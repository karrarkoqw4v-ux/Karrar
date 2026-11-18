import asyncio
import json
import re
import shutil
import uuid
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from urllib.parse import parse_qsl, urlencode, urlparse, urlunparse

import yt_dlp
from yt_dlp.utils import DownloadError

import config

URL_RE = re.compile(r"https?://[^\s<>\"']+", re.I)

# المنصات المدعومة (تقييد النطاقات يمنع استخدام البوت لمواقع/عناوين داخلية)
PLATFORMS: list[tuple[str, str, re.Pattern]] = [
    ("YouTube", "▶️", re.compile(r"(^|\.)(youtube\.com|youtu\.be|youtube-nocookie\.com)$")),
    ("Instagram", "📸", re.compile(r"(^|\.)(instagram\.com|instagr\.am)$")),
    ("TikTok", "🎵", re.compile(r"(^|\.)(tiktok\.com|tiktokv\.com)$")),
    ("Facebook", "📘", re.compile(r"(^|\.)(facebook\.com|fb\.watch|fb\.com)$")),
    ("Twitter", "🐦", re.compile(r"(^|\.)(twitter\.com|x\.com|t\.co)$")),
    ("Snapchat", "👻", re.compile(r"(^|\.)(snapchat\.com)$")),
    ("SoundCloud", "🎧", re.compile(r"(^|\.)(soundcloud\.com|snd\.sc)$")),
]
AUDIO_ONLY = {"SoundCloud"}


def find_url(text: str) -> str | None:
    m = URL_RE.search(text or "")
    return m.group(0).rstrip(").,،") if m else None


def detect(url: str) -> tuple[str, str] | None:
    host = (urlparse(url).hostname or "").lower()
    for name, icon, rx in PLATFORMS:
        if rx.search(host):
            return name, icon
    return None


_TRACK = {"utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content", "igshid", "igsh", "si", "feature", "fbclid", "ref", "_t", "_r", "is_from_webapp", "sender_device", "web_id"}


def normalize(url: str) -> str:
    """مفتاح التخزين المؤقت: الرابط بدون معاملات التتبع"""
    p = urlparse(url)
    q = [(k, v) for k, v in parse_qsl(p.query) if k not in _TRACK]
    host = (p.hostname or "").lower().removeprefix("www.").removeprefix("m.")
    return urlunparse(("https", host, p.path.rstrip("/"), "", urlencode(q), ""))


@dataclass
class Result:
    path: Path
    workdir: Path
    kind: str  # video | audio
    platform: str
    icon: str
    info: dict
    width: int = 0
    height: int = 0
    duration: int = 0
    size: int = 0
    thumb: Path | None = None
    quality: str = ""


class DownloadFailed(Exception):
    def __init__(self, user_msg: str):
        super().__init__(user_msg)
        self.user_msg = user_msg


def _friendly(err: str) -> str:
    import logging
    logging.getLogger("downloader").warning("yt-dlp error: %s", err[:400])
    e = err.lower()
    if any(k in e for k in ("private", "login", "log in", "sign in", "cookies")) or ("bot" in e and "confirm" in e):
        return "🔒 هذا المحتوى خاص أو يتطلب تسجيل دخول، ولا يمكن تحميله."
    if "not available" in e or "unavailable" in e or "removed" in e or "deleted" in e or "404" in e:
        return "❌ المحتوى غير متاح أو تم حذفه."
    if "unsupported url" in e or "no video" in e or "there is no video" in e:
        return "⚠️ هذا الرابط لا يحتوي فيديو قابلاً للتحميل."
    if "geo" in e or "country" in e:
        return "🌍 المحتوى غير متاح في منطقة الخادم."
    if any(k in e for k in ("tunnel connection", "proxyerror", "getaddrinfo", "timed out", "connection refused", "network is unreachable", "unable to download webpage")):
        return "🌐 الخادم لا يستطيع الاتصال بهذه المنصة حالياً (شبكة الخادم تحجبها). شغّل البوت على خادم بإنترنت مفتوح."
    if "429" in e or "rate" in e:
        return "⏳ ضغط كبير حالياً، حاول بعد قليل."
    return "⚠️ تعذّر التحميل من هذا الرابط. تأكد منه وحاول مرة أخرى."


def _ydl_opts(outdir: Path, fmt: str, audio: bool) -> dict:
    o: dict = {
        "outtmpl": str(outdir / "%(id).60B.%(ext)s"),
        "noplaylist": True,
        "quiet": True,
        "no_warnings": True,
        "noprogress": True,
        "retries": 3,
        "fragment_retries": 3,
        "socket_timeout": 20,
        "concurrent_fragment_downloads": 16,
        "http_chunk_size": 10 * 1024 * 1024,
        "merge_output_format": "mp4",
        "format": fmt,
        "restrictfilenames": True,
        "geo_bypass": True,
        "cachedir": False,
        "match_filter": yt_dlp.utils.match_filter_func(f"duration <=? {config.MAX_DURATION}"),
    }
    if config.COOKIES_FILE:
        o["cookiefile"] = config.COOKIES_FILE
    if config.PROXY:
        o["proxy"] = config.PROXY
    if audio:
        o["postprocessors"] = [{"key": "FFmpegExtractAudio", "preferredcodec": "mp3", "preferredquality": "192"}]
    return o


def _ladder(audio: bool) -> list[str]:
    if audio:
        return ["bestaudio[ext=m4a]/bestaudio/best"]
    # صيغة mp4/h264 تعمل داخل تيليجرام مباشرة؛ ثم نخفّض الجودة إن زاد الحجم عن حد الرفع
    return [
        "b[ext=mp4][vcodec^=avc1][height<=1080]/bv*[vcodec^=avc1][height<=1080]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b",
        "b[ext=mp4][height<=720]/bv*[height<=720]+ba/b[height<=720]/b",
        "b[ext=mp4][height<=480]/bv*[height<=480]+ba/b[height<=480]/worst",
        "worst[ext=mp4]/worst",
    ]


def _run_ydl(url: str, outdir: Path, fmt: str, audio: bool) -> tuple[dict, Path]:
    with yt_dlp.YoutubeDL(_ydl_opts(outdir, fmt, audio)) as ydl:
        info = ydl.extract_info(url, download=True)
        if info is None:
            raise DownloadFailed("⚠️ تعذّر قراءة هذا الرابط.")
        if info.get("_type") == "playlist" and info.get("entries"):
            info = next(e for e in info["entries"] if e)
        files = [p for p in outdir.iterdir() if p.is_file() and p.suffix.lower() in (".mp4", ".mkv", ".webm", ".mov", ".mp3", ".m4a", ".opus", ".ogg")]
        if not files:
            if (info.get("duration") or 0) > config.MAX_DURATION:
                raise DownloadFailed(f"⏱ الفيديو أطول من الحد المسموح ({config.MAX_DURATION // 60} دقيقة).")
            raise DownloadFailed("⚠️ لم يتم العثور على ملف فيديو في هذا الرابط.")
        files.sort(key=lambda p: p.stat().st_size, reverse=True)
        return info, files[0]


async def _probe(path: Path) -> dict:
    proc = await asyncio.create_subprocess_exec(
        "ffprobe", "-v", "error", "-print_format", "json", "-show_streams", "-show_format", str(path),
        stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL)
    out, _ = await proc.communicate()
    try:
        return json.loads(out or b"{}")
    except Exception:
        return {}


async def _thumb(path: Path, workdir: Path, dur: float) -> Path | None:
    out = workdir / "thumb.jpg"
    ss = "1" if dur > 3 else "0"
    proc = await asyncio.create_subprocess_exec(
        "ffmpeg", "-y", "-loglevel", "error", "-ss", ss, "-i", str(path), "-frames:v", "1",
        "-vf", "scale='min(320,iw)':-2", "-q:v", "6", str(out),
        stdout=asyncio.subprocess.DEVNULL, stderr=asyncio.subprocess.DEVNULL)
    await proc.wait()
    return out if out.exists() and out.stat().st_size < 200 * 1024 else None


async def _to_h264(path: Path, workdir: Path) -> Path:
    """يعيد ترميز الملف إلى h264/aac mp4 ليعمل داخل تيليجرام (عند الحاجة فقط)"""
    out = workdir / "tg.mp4"
    proc = await asyncio.create_subprocess_exec(
        "ffmpeg", "-y", "-loglevel", "error", "-i", str(path), "-c:v", "libx264", "-preset", "veryfast", "-crf", "24",
        "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "128k", "-movflags", "+faststart", str(out),
        stdout=asyncio.subprocess.DEVNULL, stderr=asyncio.subprocess.DEVNULL)
    await proc.wait()
    return out if out.exists() and out.stat().st_size > 0 else path


async def download(url: str, platform: str, icon: str) -> Result:
    audio = platform in AUDIO_ONLY
    limit = config.MAX_UPLOAD_MB * 1024 * 1024
    workdir = config.TMP_DIR / uuid.uuid4().hex[:12]
    last_err = ""
    try:
        for fmt in _ladder(audio):
            shutil.rmtree(workdir, ignore_errors=True)
            workdir.mkdir(parents=True, exist_ok=True)
            try:
                info, path = await asyncio.to_thread(_run_ydl, url, workdir, fmt, audio)
            except DownloadFailed:
                raise
            except DownloadError as e:
                last_err = str(e)
                # الصيغة المطلوبة غير موجودة → جرّب التالية، غير ذلك أعد رسالة واضحة
                if "requested format" in last_err.lower() or "format is not available" in last_err.lower():
                    continue
                raise DownloadFailed(_friendly(last_err))
            except Exception as e:  # noqa: BLE001
                raise DownloadFailed(_friendly(str(e)))

            probe = await _probe(path)
            vstreams = [s for s in probe.get("streams", []) if s.get("codec_type") == "video"]
            vcodec = (vstreams[0].get("codec_name") if vstreams else "") or ""
            need_recode = (not audio) and ((bool(vcodec) and vcodec != "h264") or path.suffix.lower() != ".mp4")
            if need_recode:
                path = await _to_h264(path, workdir)
                probe = await _probe(path)
                vstreams = [s for s in probe.get("streams", []) if s.get("codec_type") == "video"]
            size = path.stat().st_size
            if size > limit and not audio:
                last_err = "too_big"
                continue  # جودة أقل
            fmt_info = probe.get("format", {})
            dur = int(float(fmt_info.get("duration") or info.get("duration") or 0))
            w = int(vstreams[0].get("width", 0)) if vstreams else 0
            h = int(vstreams[0].get("height", 0)) if vstreams else 0
            thumb = None if audio else await _thumb(path, workdir, dur)
            return Result(path, workdir, "audio" if audio else "video", platform, icon, info, w, h, dur, size, thumb, f"{min(w, h)}p" if h else "")
        if last_err == "too_big":
            raise DownloadFailed(f"📦 حجم الفيديو أكبر من {config.MAX_UPLOAD_MB}MB حتى بأقل جودة، لا يمكن إرساله عبر تيليجرام.")
        raise DownloadFailed(_friendly(last_err))
    except BaseException:
        shutil.rmtree(workdir, ignore_errors=True)
        raise


def cleanup(res: Result):
    shutil.rmtree(res.workdir, ignore_errors=True)


def _n(v) -> str:
    if v is None:
        return "—"
    v = int(v)
    for lim, s in ((1_000_000_000, "B"), (1_000_000, "M"), (1_000, "K")):
        if v >= lim:
            return f"{v / lim:.1f}{s}".replace(".0", "")
    return str(v)


def _dur(sec: int) -> str:
    h, r = divmod(int(sec), 3600)
    m, s = divmod(r, 60)
    return f"{h}:{m:02d}:{s:02d}" if h else f"{m}:{s:02d}"


def _esc(s: str) -> str:
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def caption(res: Result, url: str) -> str:
    i = res.info
    title = _esc((i.get("title") or i.get("description") or "—").strip().replace("\n", " "))[:180]
    who = _esc(i.get("uploader") or i.get("channel") or i.get("creator") or "—")
    date = "—"
    if i.get("upload_date"):
        try:
            date = datetime.strptime(i["upload_date"], "%Y%m%d").strftime("%Y-%m-%d")
        except ValueError:
            pass
    mb = res.size / 1024 / 1024
    lines = [
        f"{'🎧' if res.kind == 'audio' else '🎬'} <b>{title}</b>",
        "",
        f"👤 <b>الناشر:</b> {who}",
        f"🕒 <b>المدة:</b> {_dur(res.duration)}",
        f"👁 <b>المشاهدات:</b> {_n(i.get('view_count'))}",
        f"❤️ <b>الإعجابات:</b> {_n(i.get('like_count'))}",
        f"💬 <b>التعليقات:</b> {_n(i.get('comment_count'))}",
        f"📅 <b>التاريخ:</b> {date}",
    ]
    if res.kind == "video":
        lines.append(f"📺 <b>الجودة:</b> {res.quality or '—'}  ({res.width}×{res.height})")
    lines += [
        f"📦 <b>الحجم:</b> {mb:.1f} MB",
        f"{res.icon} <b>المنصة:</b> {res.platform}",
        f"🔗 <a href=\"{_esc(url)}\">الرابط الأصلي</a>",
    ]
    return "\n".join(lines)[:1020]

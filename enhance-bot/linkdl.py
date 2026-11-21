"""جلب فيديو من رابط تيك توك أو إنستغرام بدون علامة مائية.
تيك توك: واجهة tikwm (رابط الفيديو الأصلي بلا علامة) ثم yt-dlp احتياطاً (مع استبعاد صيغة download ذات العلامة).
إنستغرام: yt-dlp (ريلز/منشورات عامة؛ للحسابات الخاصة ضع ملف كوكيز في IG_COOKIES)."""
import asyncio
import logging
import os
import re
from pathlib import Path
from urllib.parse import urlparse

import aiohttp

log = logging.getLogger("linkdl")
HOSTS = {"tiktok": ("tiktok.com",), "instagram": ("instagram.com", "instagr.am")}
URL_RE = re.compile(r"https?://[^\s<>\"']+", re.I)
MAX_BYTES = 800 * 1024 * 1024
UA = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"


class LinkError(Exception):
    """رسالة عربية جاهزة للمستخدم"""


def platform_of(url: str) -> str | None:
    try:
        host = (urlparse(url).hostname or "").lower()
    except ValueError:
        return None
    for name, doms in HOSTS.items():
        if any(host == d or host.endswith("." + d) for d in doms):
            return name
    return None


def extract_url(text: str) -> str | None:
    """أول رابط تيك توك/إنستغرام في النص"""
    for m in URL_RE.finditer(text or ""):
        u = m.group(0).rstrip(".,;)")
        if platform_of(u):
            return u
    return None


def has_other_url(text: str) -> bool:
    return bool(URL_RE.search(text or "")) and not extract_url(text)


async def _tikwm(url: str, dest: Path, proxy: str | None) -> dict:
    timeout = aiohttp.ClientTimeout(total=120)
    async with aiohttp.ClientSession(headers={"User-Agent": UA}, timeout=timeout) as s:
        async with s.get("https://www.tikwm.com/api/", params={"url": url, "hd": "1"}, proxy=proxy) as r:
            js = await r.json(content_type=None)
        if js.get("code") != 0 or not js.get("data"):
            raise LinkError("tikwm: " + str(js.get("msg")))
        d = js["data"]
        vid = d.get("hdplay") or d.get("play")
        if not vid:
            raise LinkError("هذا الرابط صور (سلايد) وليس فيديو.")
        if vid.startswith("/"):
            vid = "https://www.tikwm.com" + vid
        out = dest / "dl.mp4"
        got = 0
        async with s.get(vid, proxy=proxy) as r:
            if r.status != 200:
                raise LinkError(f"tikwm download {r.status}")
            with open(out, "wb") as f:
                async for chunk in r.content.iter_chunked(1 << 16):
                    got += len(chunk)
                    if got > MAX_BYTES:
                        raise LinkError("الفيديو كبير جداً.")
                    f.write(chunk)
        if got < 10_000:
            raise LinkError("ملف غير صالح")
        au = d.get("author") or {}
        return {"path": str(out), "title": d.get("title") or "", "author": au.get("nickname") or au.get("unique_id") or "", "duration": d.get("duration") or 0, "platform": "tiktok"}


def _ytdlp_sync(url: str, dest: Path, plat: str, proxy: str | None) -> dict:
    import yt_dlp
    opts = {
        "outtmpl": str(dest / "dl.%(ext)s"),
        # نتجنّب صيغة download (عليها علامة مائية في تيك توك)
        "format": "bv*[format_id!*=download][vcodec!=none]+ba/b[format_id!*=download]/b",
        "merge_output_format": "mp4",
        "noplaylist": True,
        "quiet": True,
        "no_warnings": True,
        "socket_timeout": 30,
        "retries": 3,
        "max_filesize": MAX_BYTES,
    }
    if proxy:
        opts["proxy"] = proxy
    ck = os.getenv("IG_COOKIES", "").strip()
    if ck and Path(ck).exists():
        opts["cookiefile"] = ck
    try:
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=True)
    except yt_dlp.utils.DownloadError as e:
        msg = str(e).lower()
        if plat == "instagram" and any(k in msg for k in ("login", "private", "cookies", "rate-limit", "restricted")):
            raise LinkError("إنستغرام يطلب تسجيل دخول لهذا المحتوى (حساب خاص أو مقيّد). جرّب رابط ريلز/منشور عام.") from e
        raise LinkError("تعذّر جلب الفيديو: الرابط غير صحيح أو محذوف أو خاص.") from e
    files = sorted(dest.glob("dl.*"), key=lambda p: p.stat().st_size, reverse=True)
    files = [f for f in files if f.suffix.lower() in (".mp4", ".mkv", ".webm", ".mov")]
    if not files:
        raise LinkError("لم أجد فيديو في هذا الرابط (قد يكون صوراً فقط).")
    return {"path": str(files[0]), "title": info.get("title") or info.get("description") or "", "author": info.get("uploader") or info.get("channel") or "",
            "duration": info.get("duration") or 0, "platform": plat}


async def download(url: str, dest: Path, proxy: str | None = None) -> dict:
    plat = platform_of(url)
    if not plat:
        raise LinkError("الرابط ليس من تيك توك أو إنستغرام.")
    dest.mkdir(parents=True, exist_ok=True)
    if plat == "tiktok":
        try:
            return await _tikwm(url, dest, proxy)
        except LinkError as e:
            if "صور" in str(e):
                raise
            log.warning("tikwm failed: %s", e)
        except Exception as e:  # noqa: BLE001
            log.warning("tikwm error: %s", e)
    return await asyncio.to_thread(_ytdlp_sync, url, dest, plat, proxy)

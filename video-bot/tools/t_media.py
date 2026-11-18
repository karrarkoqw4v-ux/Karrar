import asyncio
import json
import re
from pathlib import Path

from .registry import Ask, Ctx, Out, ToolError, tool


async def ff(args: list[str], timeout: int = 300) -> None:
    proc = await asyncio.create_subprocess_exec("ffmpeg", "-y", "-hide_banner", "-loglevel", "error", "-nostdin", *args, stdout=asyncio.subprocess.DEVNULL, stderr=asyncio.subprocess.PIPE)
    try:
        _, err = await asyncio.wait_for(proc.communicate(), timeout)
    except asyncio.TimeoutError:
        proc.kill()
        raise ToolError("استغرقت المعالجة وقتاً طويلاً، جرّب ملفاً أصغر.")
    if proc.returncode != 0:
        raise ToolError("تعذّرت المعالجة (ملف غير مدعوم؟): " + err.decode(errors="ignore")[-150:])


async def probe(path: str) -> dict:
    proc = await asyncio.create_subprocess_exec("ffprobe", "-v", "error", "-print_format", "json", "-show_streams", "-show_format", path, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL)
    out, _ = await proc.communicate()
    try:
        return json.loads(out or b"{}")
    except Exception:
        return {}


def _out(c: Ctx, name: str) -> str:
    return str(c.workdir / name)


def _t(s: str) -> float:
    s = s.strip().replace(",", ".")
    if ":" in s:
        parts = [float(x) for x in s.split(":")]
        v = 0.0
        for x in parts:
            v = v * 60 + x
        return v
    return float(s)


def _range(text: str) -> tuple[float, float]:
    xs = re.findall(r"\d+(?::\d+)*(?:[.,]\d+)?", text.translate(str.maketrans("٠١٢٣٤٥٦٧٨٩", "0123456789")))
    if len(xs) < 2:
        raise Ask("✍️ اكتب البداية والنهاية بالثواني أو mm:ss — مثال: 0:05 0:20")
    a, b = _t(xs[0]), _t(xs[1])
    if b <= a:
        raise ToolError("النهاية يجب أن تكون بعد البداية.")
    return a, b


@tool("vid_gif", "media", "فيديو إلى GIF", "أرسل فيديو (حتى 20MB) وسأحوّله لـ GIF متحرك.", inp="video", opts=[("480", "عادي 480"), ("320", "خفيف 320"), ("640", "كبير 640")])
async def vid_gif(c: Ctx):
    w = int(c.opt or 480)
    out = _out(c, "o.gif")
    await ff(["-t", "12", "-i", c.path, "-filter_complex", f"fps=14,scale='min({w},iw)':-2:flags=lanczos,split[a][b];[a]palettegen=max_colors=160[p];[b][p]paletteuse", out])
    return Out.anim(out)


@tool("vid_mp3", "media", "استخراج الصوت من الفيديو", "أرسل فيديو وسأستخرج صوته MP3.", inp="video", opts=[("mp3", "MP3"), ("m4a", "M4A")])
async def vid_mp3(c: Ctx):
    ext = c.opt or "mp3"
    out = _out(c, f"o.{ext}")
    codec = ["-c:a", "libmp3lame", "-q:a", "2"] if ext == "mp3" else ["-c:a", "aac", "-b:a", "192k"]
    await ff(["-i", c.path, "-vn", *codec, out])
    return Out.audio(out, f"audio.{ext}")


@tool("vid_compress", "media", "ضغط الفيديو", "أرسل فيديو وسأقلّل حجمه.", inp="video", opts=[("mid", "متوسط (480p)"), ("low", "أصغر حجم (360p)"), ("hd", "جودة أعلى (720p)")])
async def vid_compress(c: Ctx):
    h, crf = {"mid": (480, 29), "low": (360, 33), "hd": (720, 26)}[c.opt or "mid"]
    out = _out(c, "o.mp4")
    import os
    await ff(["-i", c.path, "-vf", f"scale=-2:'min({h},ih)'", "-c:v", "libx264", "-preset", "veryfast", "-crf", str(crf), "-c:a", "aac", "-b:a", "96k", "-movflags", "+faststart", out])
    return Out.video(out, f"📦 {os.path.getsize(c.path) / 1048576:.1f}MB ← {os.path.getsize(out) / 1048576:.1f}MB")


@tool("vid_trim", "media", "قص الفيديو", "أرسل الفيديو ثم اكتب البداية والنهاية.", inp="video")
async def vid_trim(c: Ctx):
    a, b = _range(c.text)
    out = _out(c, "o.mp4")
    await ff(["-ss", str(a), "-t", str(b - a), "-i", c.path, "-c:v", "libx264", "-preset", "veryfast", "-crf", "23", "-c:a", "aac", "-movflags", "+faststart", out])
    return Out.video(out)


@tool("vid_mute", "media", "كتم صوت الفيديو", "أرسل فيديو وسأحذف صوته.", inp="video")
async def vid_mute(c: Ctx):
    out = _out(c, "o.mp4")
    await ff(["-i", c.path, "-c:v", "copy", "-an", out])
    return Out.video(out)


@tool("vid_speed", "media", "تسريع أو إبطاء الفيديو", "أرسل فيديو ثم اختر السرعة.", inp="video", opts=[("0.5", "بطيء 0.5×"), ("1.5", "1.5×"), ("2", "2×"), ("3", "3×")])
async def vid_speed(c: Ctx):
    s = float(c.opt or 2)
    out = _out(c, "o.mp4")
    at = []
    v = s
    while v > 2:
        at.append("atempo=2.0")
        v /= 2
    while v < 0.5:
        at.append("atempo=0.5")
        v /= 0.5
    at.append(f"atempo={v:.3f}")
    info = await probe(c.path)
    has_a = any(x.get("codec_type") == "audio" for x in info.get("streams", []))
    args = ["-i", c.path, "-filter:v", f"setpts=PTS/{s}"]
    if has_a:
        args += ["-filter:a", ",".join(at)]
    else:
        args += ["-an"]
    await ff([*args, "-c:v", "libx264", "-preset", "veryfast", "-crf", "24", "-movflags", "+faststart", out])
    return Out.video(out)


@tool("vid_reverse", "media", "عكس الفيديو", "أرسل فيديو قصيراً (حتى 30 ثانية).", inp="video")
async def vid_reverse(c: Ctx):
    out = _out(c, "o.mp4")
    await ff(["-t", "30", "-i", c.path, "-vf", "reverse", "-af", "areverse", "-c:v", "libx264", "-preset", "veryfast", "-crf", "24", out])
    return Out.video(out)


@tool("vid_rotate", "media", "تدوير وقلب الفيديو", "أرسل الفيديو ثم اختر.", inp="video", opts=[("transpose=1", "90° يمين"), ("transpose=2", "90° يسار"), ("hflip,vflip", "180°"), ("hflip", "قلب أفقي")])
async def vid_rotate(c: Ctx):
    out = _out(c, "o.mp4")
    await ff(["-i", c.path, "-vf", c.opt or "transpose=1", "-c:v", "libx264", "-preset", "veryfast", "-crf", "23", "-c:a", "copy", out])
    return Out.video(out)


@tool("vid_note", "media", "فيديو دائري (Video Note)", "أرسل فيديو (حتى 60 ثانية) وسأحوّله لفيديو دائري كرسائل تيليجرام.", inp="video")
async def vid_note(c: Ctx):
    out = _out(c, "o.mp4")
    await ff(["-t", "59", "-i", c.path, "-vf", "crop='min(iw,ih)':'min(iw,ih)',scale=384:384", "-c:v", "libx264", "-preset", "veryfast", "-crf", "26", "-c:a", "aac", "-b:a", "96k", "-movflags", "+faststart", out])
    return Out.note(out)


@tool("vid_sticker", "media", "فيديو إلى ملصق متحرك", "أرسل فيديو قصيراً (3 ثوانٍ) وسأصنع منه ملصق WebM.", inp="video")
async def vid_sticker(c: Ctx):
    out = _out(c, "o.webm")
    for b in (450, 300, 180, 100):
        await ff(["-t", "3", "-i", c.path, "-vf", "scale=512:512:force_original_aspect_ratio=decrease,fps=30", "-an", "-c:v", "libvpx-vp9", "-b:v", f"{b}k", "-pix_fmt", "yuva420p", "-auto-alt-ref", "0", out])
        if Path(out).stat().st_size <= 256 * 1024:
            break
    return Out.doc(out, "sticker.webm", "⚠️ أرسله لـ @Stickers ليضيفه إلى حزمة ملصقاتك")


@tool("vid_frame", "media", "استخراج صورة من الفيديو", "أرسل الفيديو ثم اكتب الثانية (مثل 3 أو 0:12).", inp="video")
async def vid_frame(c: Ctx):
    if not c.text:
        raise Ask("✍️ اكتب الثانية المطلوبة (مثل 3 أو 0:12):")
    try:
        t = _t(c.text.translate(str.maketrans("٠١٢٣٤٥٦٧٨٩", "0123456789")))
    except ValueError:
        raise ToolError("اكتب رقماً أو mm:ss")
    out = _out(c, "o.jpg")
    await ff(["-ss", str(t), "-i", c.path, "-frames:v", "1", "-q:v", "2", out])
    return Out.doc(out, "frame.jpg")


@tool("vid_info", "media", "معلومات الفيديو والصوت", "أرسل أي فيديو أو ملف صوت.", inp="media")
async def vid_info(c: Ctx):
    d = await probe(c.path)
    if not d:
        raise ToolError("لا أستطيع قراءة الملف.")
    f = d.get("format", {})
    lines = [f"🎞 <b>{c.name or 'الملف'}</b>", f"الحجم: {int(f.get('size', 0)) / 1048576:.2f}MB • المدة: {float(f.get('duration', 0)):.1f}ث • معدل البت: {int(f.get('bit_rate', 0)) // 1000}kbps", f"الحاوية: {f.get('format_long_name', '')}"]
    for s in d.get("streams", []):
        if s.get("codec_type") == "video":
            fr = s.get("r_frame_rate", "0/1").split("/")
            fps = int(fr[0]) / max(1, int(fr[1])) if len(fr) == 2 else 0
            lines.append(f"📺 فيديو: {s.get('codec_name')} {s.get('width')}×{s.get('height')} @ {fps:.0f}fps")
        elif s.get("codec_type") == "audio":
            lines.append(f"🔊 صوت: {s.get('codec_name')} {s.get('sample_rate')}Hz {s.get('channels')}ch")
    return Out.text("\n".join(lines))


@tool("aud_mp3", "media", "تحويل الصوت إلى MP3", "أرسل رسالة صوتية أو ملف صوت وسأحوّله.", inp="audio", opts=[("mp3", "MP3"), ("m4a", "M4A"), ("ogg", "OGG/Opus"), ("wav", "WAV")])
async def aud_mp3(c: Ctx):
    ext = c.opt or "mp3"
    out = _out(c, f"o.{ext}")
    codec = {"mp3": ["-c:a", "libmp3lame", "-q:a", "2"], "m4a": ["-c:a", "aac", "-b:a", "192k"], "ogg": ["-c:a", "libopus", "-b:a", "96k"], "wav": ["-c:a", "pcm_s16le"]}[ext]
    await ff(["-i", c.path, "-vn", *codec, out])
    return Out.audio(out, f"audio.{ext}")


@tool("aud_speed", "media", "تغيير سرعة الصوت", "أرسل ملف صوت ثم اختر السرعة.", inp="audio", opts=[("0.75", "0.75×"), ("1.25", "1.25×"), ("1.5", "1.5×"), ("2", "2×")])
async def aud_speed(c: Ctx):
    s = float(c.opt or 1.25)
    out = _out(c, "o.mp3")
    await ff(["-i", c.path, "-vn", "-af", f"atempo={s}", "-c:a", "libmp3lame", "-q:a", "2", out])
    return Out.audio(out, "speed.mp3")


@tool("aud_voice", "media", "مغيّر الصوت", "أرسل رسالة صوتية وسأغيّر نبرتها.", inp="audio", opts=[("0.8", "صوت غليظ 🧔"), ("1.3", "صوت رفيع 👧"), ("1.6", "صوت سنجاب 🐿"), ("0.65", "وحش 👹")])
async def aud_voice(c: Ctx):
    r = float(c.opt or 1.3)
    out = _out(c, "o.ogg")
    at = f"atempo={1 / r:.4f}" if 0.5 <= 1 / r <= 2 else "atempo=0.5,atempo=" + f"{1 / r / 0.5:.4f}"
    await ff(["-i", c.path, "-vn", "-af", f"aresample=44100,asetrate={int(44100 * r)},aresample=44100,{at}", "-c:a", "libopus", "-b:a", "64k", out])
    return Out.audio(out, "voice.ogg")


@tool("aud_boost", "media", "رفع الصوت وتنقيته", "أرسل ملف صوت وسأرفع مستواه وأزيل الضجيج.", inp="audio", opts=[("boost", "رفع الصوت"), ("denoise", "إزالة الضجيج"), ("norm", "توحيد المستوى"), ("all", "الكل")])
async def aud_boost(c: Ctx):
    chains = {"boost": "volume=2.2,alimiter=limit=0.95", "denoise": "highpass=f=80,afftdn=nf=-25", "norm": "loudnorm=I=-16:TP=-1.5:LRA=11", "all": "highpass=f=80,afftdn=nf=-25,loudnorm=I=-14:TP=-1.5:LRA=11"}
    out = _out(c, "o.mp3")
    await ff(["-i", c.path, "-vn", "-af", chains[c.opt or "all"], "-c:a", "libmp3lame", "-q:a", "2", out])
    return Out.audio(out, "enhanced.mp3")


@tool("aud_trim", "media", "قص الصوت", "أرسل الملف الصوتي ثم اكتب البداية والنهاية.", inp="audio")
async def aud_trim(c: Ctx):
    a, b = _range(c.text)
    out = _out(c, "o.mp3")
    await ff(["-ss", str(a), "-t", str(b - a), "-i", c.path, "-vn", "-c:a", "libmp3lame", "-q:a", "2", out])
    return Out.audio(out, "cut.mp3")


@tool("aud_merge", "media", "دمج ملفات صوتية", "أرسل ملفات الصوت بالترتيب ثم اضغط «تم».", inp="audios")
async def aud_merge(c: Ctx):
    if len(c.paths) < 2:
        raise ToolError("أرسل ملفين على الأقل.")
    out = _out(c, "o.mp3")
    args = []
    for p in c.paths:
        args += ["-i", p]
    n = len(c.paths)
    await ff([*args, "-filter_complex", "".join(f"[{i}:a]aresample=44100,aformat=channel_layouts=stereo[a{i}];" for i in range(n)) + "".join(f"[a{i}]" for i in range(n)) + f"concat=n={n}:v=0:a=1[a]", "-map", "[a]", "-c:a", "libmp3lame", "-q:a", "2", out])
    return Out.audio(out, "merged.mp3")


@tool("img_aud_video", "media", "صوت + صورة إلى فيديو", "أرسل الصوت أولاً ثم الصورة.", inp="media2")
async def img_aud_video(c: Ctx):
    if len(c.paths) < 2:
        raise ToolError("أرسل ملف الصوت ثم الصورة.")
    aud = next((p for p in c.paths if not re.search(r"\.(jpe?g|png|webp)$", p, re.I)), None)
    img = next((p for p in c.paths if re.search(r"\.(jpe?g|png|webp)$", p, re.I)), None)
    if not aud or not img:
        raise ToolError("أحتاج ملف صوت وصورة.")
    out = _out(c, "o.mp4")
    await ff(["-loop", "1", "-framerate", "2", "-i", img, "-i", aud, "-vf", "scale=720:720:force_original_aspect_ratio=decrease,pad=720:720:(ow-iw)/2:(oh-ih)/2,format=yuv420p", "-c:v", "libx264", "-tune", "stillimage", "-c:a", "aac", "-b:a", "160k", "-shortest", "-t", "600", out])
    return Out.video(out)

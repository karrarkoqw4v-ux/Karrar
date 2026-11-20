"""معالجة الفيديو بـ FFmpeg: ألوان وتحسين / HDR / 2K — كلها برمجية (بدون ذكاء اصطناعي)."""
import asyncio
import json
import re
from pathlib import Path

SCURVE = "curves=all='0/0 0.25/0.2 0.75/0.8 1/1'"

MODES = {
    # الزر 1: ألوان وتحسين (نفس الدقة)
    "color": dict(
        title="الوان وتحسين", target=None,
        vf="hqdn3d=1.5:1.2:4:4,deband=range=20:blur=1,unsharp=5:5:0.7:5:5:0.0,unsharp=13:13:0.3:13:13:0.0,"
           f"vibrance=intensity=0.5,{SCURVE},eq=saturation=1.12:contrast=1.04"),
    # الزر 2: HDR بدقة عالية (1080 كحد أدنى)
    "hdr": dict(
        title="HDR عالية دقة", target=1080,
        vf="hqdn3d=1.3:1.1:3:3,deband=range=24:blur=1,unsharp=5:5:0.8:5:5:0.0,unsharp=13:13:0.6:13:13:0.0,cas=0.35,"
           "curves=all='0/0.03 0.2/0.27 0.5/0.55 0.85/0.88 1/0.97',vibrance=intensity=0.7,eq=saturation=1.2:contrast=1.06,noise=alls=2:allf=t"),
    # الزر 3: دقة 2K
    "2k": dict(
        title="دقة 2K", target=1440,
        vf="hqdn3d=1.2:1.0:3:3,unsharp=5:5:0.9:5:5:0.0,unsharp=13:13:0.35:13:13:0.0,cas=0.4,vibrance=intensity=0.4,eq=saturation=1.08:contrast=1.04"),
}


async def probe(path: str) -> dict:
    p = await asyncio.create_subprocess_exec("ffprobe", "-v", "error", "-print_format", "json", "-show_streams", "-show_format", path, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL)
    out, _ = await p.communicate()
    try:
        return json.loads(out or b"{}")
    except Exception:
        return {}


def info(d: dict) -> tuple[int, int, float, bool]:
    w = h = 0
    for s in d.get("streams", []):
        if s.get("codec_type") == "video":
            w, h = int(s.get("width", 0)), int(s.get("height", 0))
            rot = (s.get("tags") or {}).get("rotate") or 0
            sd = s.get("side_data_list") or []
            for x in sd:
                if "rotation" in x:
                    rot = x["rotation"]
            if abs(int(float(rot or 0))) in (90, 270):
                w, h = h, w
            break
    dur = float((d.get("format") or {}).get("duration") or 0)
    audio = any(s.get("codec_type") == "audio" for s in d.get("streams", []))
    return w, h, dur, audio


def build_vf(mode: str, w: int, h: int) -> str:
    m = MODES[mode]
    parts = []
    t = m["target"]
    short = min(w, h)
    if t and short < t:
        parts.append(f"scale=w='if(gt(iw,ih),-2,{t})':h='if(gt(iw,ih),{t},-2)':flags=lanczos")
    elif short > 1440:  # لا نكبّر ولا نحافظ على 4K: نخفّض لـ 2K حفاظاً على الحجم
        parts.append("scale=w='if(gt(iw,ih),-2,1440)':h='if(gt(iw,ih),1440,-2)':flags=lanczos")
    parts.append(m["vf"])
    parts.append("scale=trunc(iw/2)*2:trunc(ih/2)*2,format=yuv420p")
    return ",".join(parts)


async def enhance(src: str, dst: str, mode: str, on_progress, budget_mb: float = 46.0) -> dict:
    d = await probe(src)
    w, h, dur, has_a = info(d)
    if not w or not h or dur <= 0:
        raise ValueError("bad video")
    vf = build_vf(mode, w, h)
    # سقف معدل البت ليبقى الملف أقل من حد الرفع (50MB)
    total_kbit = budget_mb * 1024 * 8
    v_kbps = max(500, int(total_kbit / dur - 128))
    args = ["ffmpeg", "-y", "-hide_banner", "-loglevel", "error", "-nostdin", "-progress", "pipe:1", "-nostats", "-i", src,
            "-map", "0:v:0", "-vf", vf, "-c:v", "libx264", "-preset", "veryfast", "-crf", "17", "-maxrate", f"{v_kbps}k", "-bufsize", f"{v_kbps * 2}k",
            "-profile:v", "high", "-pix_fmt", "yuv420p", "-movflags", "+faststart"]
    if has_a:
        args += ["-map", "0:a:0", "-c:a", "aac", "-b:a", "128k"]
    args.append(dst)
    proc = await asyncio.create_subprocess_exec(*args, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE)

    async def reader():
        while True:
            line = await proc.stdout.readline()
            if not line:
                break
            m = re.match(rb"out_time_(?:us|ms)=(\d+)", line)
            if m:
                await on_progress(min(0.99, int(m.group(1)) / 1e6 / dur))
    rt = asyncio.create_task(reader())
    err = await proc.stderr.read()
    await proc.wait()
    await rt
    if proc.returncode != 0 or not Path(dst).exists():
        raise RuntimeError(err.decode(errors="ignore")[-300:])
    od = await probe(dst)
    ow, oh, _, _ = info(od)
    return {"w": ow, "h": oh, "dur": dur, "size": Path(dst).stat().st_size}

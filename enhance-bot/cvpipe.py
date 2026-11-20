"""تمرير الفيديو عبر معالجات OpenCV إطاراً بإطار بالتوازي (قراءة/كتابة FFmpeg)"""
import asyncio
import os
import time
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

import numpy as np

import cvlib
from process import info, probe

THREADS = max(1, os.cpu_count() or 2)
POOL = ThreadPoolExecutor(max_workers=THREADS)


class TooHeavy(Exception):
    pass


def _fps(d: dict) -> float:
    for s in d.get("streams", []):
        if s.get("codec_type") == "video":
            r = s.get("avg_frame_rate") or s.get("r_frame_rate") or "30/1"
            try:
                a, b = r.split("/")
                v = float(a) / max(float(b), 1)
                return v if 5 <= v <= 120 else 30.0
            except Exception:
                return 30.0
    return 30.0


def _apply(buf: bytes, w: int, h: int, funcs) -> np.ndarray:
    fr = np.frombuffer(buf, np.uint8).reshape(h, w, 3).copy()
    for fn in funcs:
        fr = fn(fr)
    hh, ww = fr.shape[:2]
    if hh % 2 or ww % 2:
        fr = fr[: hh // 2 * 2, : ww // 2 * 2]
    return np.ascontiguousarray(fr)


async def cv_pass(src: str, dst: str, func_ids: list[str], on_progress=None, budget_mb: float = 46.0, max_seconds: float = 480.0, crf: int = 17, preview_frames: int | None = None, cap: tuple = (1920, 1080)) -> dict:
    funcs = [cvlib.CV_FUNCS[i][0] for i in func_ids]
    d = await probe(src)
    w, h, dur, has_a = info(d)
    fps = _fps(d)
    total = max(1, int(dur * fps))
    if preview_frames:
        total = min(total, preview_frames)
    # حدّ أقصى للدقة الداخلية 1080p للحفاظ على السرعة
    vf = []
    if max(w, h) > cap[0] or min(w, h) > cap[1]:
        k = min(cap[0] / max(w, h), cap[1] / min(w, h))
        w, h = int(w * k) // 2 * 2, int(h * k) // 2 * 2
        vf = ["-vf", f"scale={w}:{h}:flags=lanczos"]
    rd = await asyncio.create_subprocess_exec("ffmpeg", "-nostdin", "-loglevel", "error", "-i", src, *(["-frames:v", str(total)] if preview_frames else []), "-an", *vf, "-f", "rawvideo", "-pix_fmt", "bgr24", "-",
                                              stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL)
    size = w * h * 3
    loop = asyncio.get_running_loop()
    wr = None
    done = 0
    t0 = time.time()
    batch_n = THREADS * 2
    out_w = out_h = 0
    try:
        while True:
            bufs = []
            for _ in range(batch_n):
                try:
                    bufs.append(await rd.stdout.readexactly(size))
                except asyncio.IncompleteReadError:
                    break
            if not bufs:
                break
            res = await asyncio.gather(*[loop.run_in_executor(POOL, _apply, b, w, h, funcs) for b in bufs])
            if wr is None:
                out_h, out_w = res[0].shape[:2]
                v_kbps = max(600, int(budget_mb * 1024 * 8 / max(dur, 1) - 128))
                args = ["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "bgr24", "-s", f"{out_w}x{out_h}", "-framerate", f"{fps:.3f}", "-i", "-"]
                if has_a and not preview_frames:
                    args += ["-i", src, "-map", "0:v", "-map", "1:a:0"]
                args += ["-c:v", "libx264", "-preset", "veryfast", "-crf", str(crf), "-maxrate", f"{v_kbps}k", "-bufsize", f"{v_kbps * 2}k", "-profile:v", "high", "-pix_fmt", "yuv420p", "-movflags", "+faststart"]
                if has_a and not preview_frames:
                    args += ["-c:a", "aac", "-b:a", "128k", "-shortest"]
                wr = await asyncio.create_subprocess_exec(*args, dst, stdin=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL)
            for fr in res:
                wr.stdin.write(fr.tobytes())
            await wr.stdin.drain()
            done += len(res)
            if on_progress:
                await on_progress(min(0.99, done / total))
            el = time.time() - t0
            if done >= batch_n * 2 and el / done * total > max_seconds:
                raise TooHeavy(f"المعالجة ستستغرق نحو {int(el / done * total / 60) + 1} دقائق. قصّر الفيديو أو اختر أداة أخف.")
        if wr is None:
            raise RuntimeError("no frames")
        wr.stdin.close()
        await wr.wait()
    except BaseException:
        for p in (rd, wr):
            if p and p.returncode is None:
                p.kill()
        raise
    await rd.wait()
    if not Path(dst).exists():
        raise RuntimeError("encode failed")
    return {"kind": "video", "w": out_w, "h": out_h, "dur": dur, "size": Path(dst).stat().st_size, "fps": fps}


def frame_fn(func_ids: list[str]):
    funcs = [cvlib.CV_FUNCS[i][0] for i in func_ids]

    def run(fr):
        for fn in funcs:
            fr = fn(fr)
        return fr
    return run

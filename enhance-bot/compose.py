"""تجميع التأثيرات المختارة في أمر FFmpeg واحد وتشغيله"""
import asyncio
import re
from dataclasses import dataclass
from pathlib import Path

import effects as E
from process import info, probe


class UserError(Exception):
    pass


def atempo(f: float) -> str:
    parts, v = [], f
    while v > 2.0:
        parts.append("atempo=2.0")
        v /= 2
    while v < 0.5:
        parts.append("atempo=0.5")
        v /= 0.5
    parts.append(f"atempo={v:.4f}")
    return ",".join(parts)


@dataclass
class Plan:
    pre: list
    inputs: list
    graph: list
    vlabel: str
    audio: list | None  # قائمة فلاتر الصوت أو None لو لا صوت
    music: tuple | None
    mute: bool
    out: str  # video|gif|mp3|cover
    out_dur: float
    w: int
    h: int
    stab: bool


def plan(stack: list[tuple[str, dict]], w: int, h: int, dur: float, has_a: bool, tmp: Path, preview: bool = False, beat=None, stats=None) -> Plan:
    ctx = E.Ctx(w, h, dur, has_a, tmp, beat=beat, stats=stats)
    pieces: list[E.Piece] = []
    for eid, params in stack:
        ctx.p = params
        new = E.EFFECTS[eid].build(ctx)
        for p in new:
            if p.kind == "dims":
                ctx.w, ctx.h = p.val
        pieces += new
    pre, inputs, graph, seg, af_list = [], [], [], [], []
    cur, nidx, k = "0:v", 1, 0
    music = None
    mute = False
    out = "video"
    stab = False
    speed = 1.0

    def flush():
        nonlocal cur, k, seg
        if seg:
            k += 1
            lab = f"v{k}"
            graph.append(f"[{cur}]{','.join(seg)}[{lab}]")
            cur, seg = lab, []

    for p in pieces:
        if p.kind == "vf":
            seg.append(p.val)
        elif p.kind == "cx":
            flush()
            k += 1
            lab = f"v{k}"
            g = p.val.replace("{i}", cur).replace("{o}", lab).replace("{n}", str(nidx))
            graph.append(g)
            for inp in p.inputs:
                inputs += inp
                nidx += 1
            cur = lab
        elif p.kind == "pre" and not preview:
            pre += p.val
        elif p.kind == "af":
            af_list.append(p.val)
        elif p.kind == "speed":
            af_list.append(atempo(p.val))
            speed *= p.val
        elif p.kind == "music":
            music = p.val
        elif p.kind == "mute":
            mute = True
        elif p.kind == "out" and not preview:
            out = p.val
        elif p.kind == "stab" and not preview:
            stab = True
    seg += ["scale=trunc(iw/2)*2:trunc(ih/2)*2", "format=yuv420p"]
    flush()
    return Plan(pre, inputs, graph, cur, af_list if (has_a and not mute) else None, music, mute, out, ctx.dur, ctx.w, ctx.h, stab)


async def _run_ffmpeg(args: list[str], on_progress=None, total: float = 0, timeout: int = 900) -> None:
    proc = await asyncio.create_subprocess_exec(*args, stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.PIPE)

    async def reader():
        while True:
            line = await proc.stdout.readline()
            if not line:
                break
            m = re.match(rb"out_time_(?:us|ms)=(\d+)", line)
            if m and on_progress and total > 0:
                await on_progress(min(0.99, int(m.group(1)) / 1e6 / total))
    rt = asyncio.create_task(reader())
    try:
        err = await asyncio.wait_for(proc.stderr.read(), timeout)
        await asyncio.wait_for(proc.wait(), 30)
    except asyncio.TimeoutError:
        proc.kill()
        raise UserError("استغرقت المعالجة وقتاً طويلاً وأُلغيت.")
    await rt
    if proc.returncode != 0:
        raise RuntimeError(err.decode(errors="ignore")[-400:])


async def stab_detect(src: str, pre: list, trf: str):
    await _run_ffmpeg(["ffmpeg", "-y", "-hide_banner", "-loglevel", "error", "-nostdin", *pre, "-i", src, "-vf", f"vidstabdetect=shakiness=6:accuracy=9:result={trf}", "-f", "null", "-"], timeout=600)


async def _beat_for(stack, src: str, meta: dict):
    """إيقاع الموسيقى المضافة إن وُجدت، وإلا إيقاع صوت الفيديو"""
    import beats
    if not any(E.EFFECTS[e].cat == "ed" for e, _ in stack):
        return None
    for e, p in stack:
        if e == "music" and p.get("audio"):
            b = await beats.analyze(p["audio"])
            if b:
                return b
    if meta.get("beat") is not None:
        return meta["beat"] or None
    if meta.get("audio"):
        return await beats.analyze(meta.get("beat_src") or src)
    return None


async def _render_base(src: str, dst: str, stack, meta: dict, tmp: Path, mode: str = "final", on_progress=None, budget_mb: float = 46.0, crf: int = 17) -> dict:
    """mode: final | pimg (صورة معاينة) | pclip (مقطع معاينة 2 ثانية)"""
    w, h, dur, has_a = meta["w"], meta["h"], meta["dur"], meta["audio"]
    preview = mode != "final"
    beat = await _beat_for(stack, src, meta)
    pl = plan(stack, w, h, dur, has_a, tmp, preview=preview, beat=beat, stats=meta.get('stats'))
    base = ["ffmpeg", "-y", "-hide_banner", "-loglevel", "error", "-nostdin"]
    if mode == "final":
        base += ["-progress", "pipe:1", "-nostats"]
    graph = list(pl.graph)
    pre = list(pl.pre)
    if pl.stab:
        trf = str(tmp / "s.trf")
        await stab_detect(src, pre, trf)
        graph[0] = graph[0].replace("[0:v]", "[vstab]", 1)
        graph.insert(0, f"[0:v]vidstabtransform=input={trf}:smoothing=15:zoom=1:optzoom=1[vstab]")
    vlab = pl.vlabel
    if pl.out == "gif" and mode == "final":
        graph.append(f"[{vlab}]fps=14,scale='min(480,iw)':-2:flags=lanczos,split[ga][gb];[ga]palettegen=max_colors=160[gp];[gb][gp]paletteuse[gif]")
        vlab = "gif"
    args = base + pre
    if mode == "pimg":
        args += ["-ss", "1.0"]
    args += ["-i", src] + pl.inputs
    midx = None
    if pl.music and mode == "final":
        # الموسيقى مدخل أخير
        args += ["-stream_loop", "-1", "-i", pl.music[0]]
        midx = 1 + sum(1 for t in pl.inputs if t == "-i")
    out_dur = pl.out_dur
    use_audio = mode in ("final", "pclip") and pl.audio is not None and has_a and pl.out != "gif"
    a_chain = ",".join(pl.audio) if pl.audio else ""
    if pl.out == "mp3" and mode == "final":
        if not has_a:
            raise UserError("هذا الفيديو بلا صوت.")
        await _run_ffmpeg(base + pre + ["-i", src, "-vn", *(["-af", a_chain] if a_chain else []), "-c:a", "libmp3lame", "-q:a", "2", dst], on_progress, dur)
        return {"kind": "mp3", "size": Path(dst).stat().st_size, "dur": dur, "w": 0, "h": 0}
    amap = None
    if midx is not None and mode == "final":
        vol = pl.music[1]
        mus = f"[{midx}:a]volume={vol}[mus]"
        if use_audio:
            graph.append(f"[0:a]{a_chain or 'anull'}[a0]")
            graph.append(mus)
            graph.append("[a0][mus]amix=inputs=2:duration=first:normalize=0[aout]")
        else:
            graph.append(mus)
            graph.append("[mus]anull[aout]")
        amap = "[aout]"
    elif use_audio and a_chain:
        graph.append(f"[0:a]{a_chain}[aout]")
        amap = "[aout]"
    elif use_audio:
        amap = "0:a?"
    args += ["-filter_complex", ";".join(graph), "-map", f"[{vlab}]"]
    if mode == "pimg":
        args += ["-frames:v", "1", dst]
    elif mode == "pclip":
        args += ["-t", "4", "-c:v", "libx264", "-preset", "ultrafast", "-crf", "26", "-pix_fmt", "yuv420p"]
        args += (["-map", amap, "-c:a", "aac", "-b:a", "64k"] if amap else ["-an"])
        args += ["-movflags", "+faststart", dst]
    elif pl.out == "gif":
        args += ["-t", f"{min(out_dur, 12):.2f}", "-loop", "0", dst]
    elif pl.out == "cover":
        args = base + ["-ss", f"{min(1.0, dur / 3):.2f}", "-i", src] + pl.inputs + ["-filter_complex", ";".join(graph), "-map", f"[{vlab}]", "-frames:v", "1", "-q:v", "2", dst]
    else:
        if min(pl.w, pl.h) >= 2160 and dur > 40:
            raise UserError("دقة 4K مسموحة للفيديوهات حتى 40 ثانية فقط.")
        v_kbps = max(500, int(budget_mb * 1024 * 8 / max(out_dur, 1) - 128))
        args += ["-c:v", "libx264", "-preset", "veryfast", "-crf", str(crf), "-maxrate", f"{v_kbps}k", "-bufsize", f"{v_kbps * 2}k", "-profile:v", "high", "-pix_fmt", "yuv420p", "-movflags", "+faststart"]
        if amap:
            args += ["-map", amap, "-c:a", "aac", "-b:a", "128k"]
        else:
            args += ["-an"]
        if midx is not None:
            args += ["-t", f"{out_dur:.2f}"]
        args.append(dst)
    await _run_ffmpeg(args, on_progress if mode == "final" else None, out_dur if pl.out == "video" else dur, timeout=900 if mode == "final" else 45)
    d = await probe(dst) if pl.out in ("video", "gif") and mode == "final" else {}
    ow, oh, od, _ = info(d) if d else (pl.w, pl.h, out_dur, False)
    return {"kind": pl.out, "w": ow or pl.w, "h": oh or pl.h, "dur": od or out_dur, "size": Path(dst).stat().st_size}


HEAVY_RES = {"r2k", "r4k", "r2x"}


async def render(src: str, dst: str, stack, meta: dict, tmp: Path, mode: str = "final", on_progress=None, budget_mb: float = 46.0) -> dict:
    """يشغّل المكدس: تأثيرات FFmpeg أولاً ثم (إن وُجدت) معالجات OpenCV كمرحلة أخيرة"""
    cv_ids = [e for e, _ in stack if E.EFFECTS[e].cat == "cv"]
    base_stack = [(e, p) for e, p in stack if E.EFFECTS[e].cat != "cv"]
    if not cv_ids:
        return await _render_base(src, dst, stack, meta, tmp, mode, on_progress, budget_mb)
    import cv2
    import cvpipe

    cap = (2560, 1440) if any(e in HEAVY_RES for e, _ in stack) else (1920, 1080)
    if mode == "pimg":
        mid = str(tmp / "cv_in.png")
        await _render_base(src, mid, base_stack, meta, tmp, "pimg")
        img = cv2.imread(mid)
        run = cvpipe.frame_fn(cv_ids)
        cv2.imwrite(dst, run(img))
        return {"kind": "video", "w": img.shape[1], "h": img.shape[0], "dur": 0, "size": Path(dst).stat().st_size}
    inter = str(tmp / f"cv_inter_{mode}.mp4")
    if mode == "pclip":
        await _render_base(src, inter, base_stack, meta, tmp, "pclip")
        return await cvpipe.cv_pass(inter, dst, cv_ids, None, budget_mb, 60, 24, cap=cap)

    async def p1(p):
        if on_progress:
            await on_progress(p * 0.35)

    async def p2(p):
        if on_progress:
            await on_progress(0.35 + p * 0.65)
    await _render_base(src, inter, base_stack, meta, tmp, "final", p1, 400.0, 14)
    try:
        return await cvpipe.cv_pass(inter, dst, cv_ids, p2, budget_mb, 480, 17, cap=cap)
    except cvpipe.TooHeavy as e:
        raise UserError(str(e))

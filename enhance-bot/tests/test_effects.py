import asyncio, sys, tempfile, time
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent.parent))
import effects as E, compose, process

SRC = "/tmp/claude-0/eb/c.mp4"; MUS = "/tmp/claude-0/eb/m.mp3"
OUT = Path(sys.argv[1]); OUT.mkdir(parents=True, exist_ok=True)
PARAMS = {"trim": {"range": (0.5, 3.0)}, "border": {"col": "0xFFFFFF", "b": 14}, "text": {"text": "مرحبا بك في المونتاج", "pos": "bot"},
          "watermark": {"text": "@تجربة"}, "music": {"audio": MUS, "vol": 0.8}}
only = set(sys.argv[2].split(",")) if len(sys.argv) > 2 else None

async def main():
    d = await process.probe(SRC); w, h, dur, a = process.info(d)
    meta = {"w": w, "h": h, "dur": dur, "audio": a}
    fails, ok = [], 0
    import os
    sh, shs = int(os.environ.get('SHARD', 0)), int(os.environ.get('SHARDS', 1))
    for i, (eid, e) in enumerate(E.EFFECTS.items()):
        if only and eid not in only: continue
        if i % shs != sh: continue
        tmp = Path(tempfile.mkdtemp()); st = [(eid, PARAMS.get(eid, {}))]
        t0 = time.time()
        try:
            if eid in ("r4k", "r2x"):
                pass
            ext = {"out_gif": "gif", "out_mp3": "mp3", "out_cover": "jpg"}.get(eid, "mp4")
            r = await compose.render(SRC, str(OUT / f"{eid}.{ext}"), st, meta, tmp, "final")
            if not e.noprev and e.cat != "res":
                await compose.render(SRC, str(OUT / f"{eid}_p.png"), st, meta, tmp, "pimg")
            if e.motion:
                await compose.render(SRC, str(OUT / f"{eid}_c.mp4"), st, meta, tmp, "pclip")
            ok += 1
            print(f"ok {eid} {time.time()-t0:.1f}s {r['kind']} {r['w']}x{r['h']}")
        except Exception as ex:
            fails.append(f"{eid}: {type(ex).__name__} {str(ex)[-200:]}")
    if shs > 1 and sh != 0:
        print('OK', ok, 'FAIL', len(fails)); [print('FAIL', f) for f in fails]; return
    # مكدس مركّب
    tmp = Path(tempfile.mkdtemp())
    st = [("trim", PARAMS["trim"]), ("teal_orange", {}), ("snow", {}), ("text", PARAMS["text"]), ("speed2", {}), ("music", PARAMS["music"]), ("fade_io", {}), ("r1080", {})]
    r = await compose.render(SRC, str(OUT / "combo.mp4"), st, meta, tmp, "final"); print("combo", r)
    print("OK", ok, "FAIL", len(fails)); [print("FAIL", f) for f in fails]
asyncio.run(main())

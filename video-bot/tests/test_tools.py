import asyncio
import subprocess
import sys
import tempfile
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))
import tools  # noqa: E402,F401
from tools.registry import TOOLS, Ask, Ctx, ToolError, run_tool  # noqa: E402

W = Path(tempfile.mkdtemp(prefix="tt_"))
OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else W / "out"
OUT.mkdir(parents=True, exist_ok=True)


def sh(cmd):
    subprocess.run(cmd, shell=True, check=True, capture_output=True)


sh(f"ffmpeg -y -loglevel error -f lavfi -i testsrc2=size=480x640:rate=25 -f lavfi -i sine=frequency=440 -t 6 -c:v libx264 -pix_fmt yuv420p -c:a aac {W}/v.mp4")
sh(f"ffmpeg -y -loglevel error -f lavfi -i sine=frequency=330:duration=5 -c:a libmp3lame {W}/a.mp3")
sh(f"ffmpeg -y -loglevel error -f lavfi -i gradients=size=900x1200:c0=0x7c3aed:c1=0xec4899:nb_colors=2 -frames:v 1 {W}/p.jpg")
from PIL import Image, ImageDraw, ImageFont  # noqa: E402
im = Image.new("RGB", (900, 300), "white")
d = ImageDraw.Draw(im)
d.text((30, 80), "Hello OCR Test 12345", fill="black", font=ImageFont.truetype(str(Path(__file__).parent.parent / "fonts/Cairo-b.ttf"), 70))
im.save(W / "ocr.png")
import qrcode  # noqa: E402
qrcode.make("https://example.com/qr").save(W / "qr.png")
import pymupdf  # noqa: E402
doc = pymupdf.open()
for i in range(3):
    pg = doc.new_page()
    pg.insert_text((72, 100), f"Page {i + 1} hello", fontsize=24)
doc.save(W / "d.pdf")
(W / "f.txt").write_text("hello")

FILES = {"video": [str(W / "v.mp4")], "audio": [str(W / "a.mp3")], "photo": [str(W / "p.jpg")], "pdf": [str(W / "d.pdf")], "file": [str(W / "p.jpg")]}
ASK_REPLY = {"vid_trim": "0:01 0:04", "aud_trim": "0:01 0:03", "vid_frame": "2", "img_watermark": "@test", "img_meme": "TOP\nBOTTOM", "pdf_extract": "1-2", "pdf_protect": "secret"}
SPECIAL = {"ocr": str(W / "ocr.png"), "qr_read": str(W / "qr.png")}

res = {"ok": 0, "fail": []}


async def one(t, opt, label):
    ctx = Ctx(uid=1, workdir=Path(tempfile.mkdtemp(dir=W)), opt=opt, name="x")
    inp = t.inp
    if t.id in SPECIAL:
        ctx.path = SPECIAL[t.id]
    elif inp in FILES:
        ctx.path = FILES[inp][0]
    elif inp == "pdfs":
        ctx.paths = [str(W / "d.pdf")] * 2
    elif inp == "photos":
        ctx.paths = [str(W / "p.jpg")] * 3
    elif inp == "audios":
        ctx.paths = [str(W / "a.mp3")] * 2
    elif inp == "files":
        ctx.paths = [str(W / "f.txt"), str(W / "p.jpg")]
    elif inp == "media":
        ctx.path = str(W / "v.mp4")
    elif inp == "media2":
        ctx.paths = [str(W / "a.mp3"), str(W / "p.jpg")]
    elif inp == "location":
        ctx.text = "33.31 44.36"
    else:
        ctx.text = t.sample if t.sample is not None else "اختبار test 123"
    t0 = time.time()
    try:
        try:
            outs = await run_tool(t, ctx)
        except Ask:
            ctx.text = ASK_REPLY.get(t.id, "1")
            outs = await run_tool(t, ctx)
        assert outs, "no output"
        for i, o in enumerate(outs):
            if o.kind == "text":
                assert o.data.strip()
            else:
                d = o.data
                data = d if isinstance(d, (bytes, bytearray)) else Path(d).read_bytes()
                assert len(data) > 0
                ext = {"photo": "png", "sticker": "webp", "anim": "gif", "video": "mp4", "note": "mp4", "audio": "mp3", "doc": (Path(o.name).suffix[1:] or "bin")}[o.kind]
                if o.kind == "doc" and o.name:
                    ext = Path(o.name).suffix[1:] or "bin"
                if o.kind in ("audio",) and o.name:
                    ext = Path(o.name).suffix[1:] or "mp3"
                (OUT / f"{t.id}_{label}_{i}.{ext}").write_bytes(data)
        res["ok"] += 1
        return f"ok   {t.id}/{label} {time.time() - t0:.1f}s"
    except ToolError as e:
        res["fail"].append(f"{t.id}/{label}: ToolError {e}")
    except Exception as e:  # noqa: BLE001
        res["fail"].append(f"{t.id}/{label}: {type(e).__name__} {str(e)[:160]}")


async def main():
    only = set(sys.argv[2].split(",")) if len(sys.argv) > 2 else None
    for t in TOOLS.values():
        if t.needs_net or (only and t.id not in only):
            continue
        opts = [o[0] for o in t.opts] or [""]
        for o in opts[:6] if t.inp in ("text",) else opts[:3]:
            await one(t, o, (o or "d").replace("/", "_").replace("=", "_").replace(",", "_").replace("|", "_"))
    print("OK", res["ok"], "FAIL", len(res["fail"]))
    for f in res["fail"]:
        print("FAIL", f)

asyncio.run(main())

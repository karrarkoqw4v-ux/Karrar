"""رسومات بـ Pillow: نص عربي، جسيمات، مقارنة قبل/بعد"""
import io
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont, features

FONTS = Path(__file__).parent / "fonts"
RAQM = features.check("raqm")

if not RAQM:
    import arabic_reshaper
    from bidi.algorithm import get_display


def shape(t: str) -> str:
    return t if RAQM else get_display(arabic_reshaper.reshape(t))


def _kw() -> dict:
    return {"language": "ar"} if RAQM else {}


def font(size: int, bold=True) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(FONTS / ("Cairo-b.ttf" if bold else "Cairo-r.ttf")), size)


def text_png(text: str, w: int, h: int, pos: str = "bot", out: str = "t.png", color=(255, 255, 255), size_ratio: float = 0.07, box: bool = False) -> str:
    """طبقة PNG بحجم الفيديو فيها النص (عربي أو إنجليزي) بالموضع المطلوب"""
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    size = max(18, int(min(w, h) * size_ratio))
    f = font(size)
    lines, cur = [], ""
    for wd in text.split():
        t = (cur + " " + wd).strip()
        if f.getlength(shape(t), **_kw()) > w * 0.88 and cur:
            lines.append(cur)
            cur = wd
        else:
            cur = t
    lines.append(cur)
    lh = int(size * 1.45)
    total = lh * len(lines)
    y0 = {"top": h * 0.07, "mid": (h - total) / 2, "bot": h * 0.93 - total}.get(pos, h * 0.9 - total)
    if box:
        d.rounded_rectangle((w * 0.04, y0 - size * 0.4, w * 0.96, y0 + total + size * 0.2), radius=size * 0.5, fill=(0, 0, 0, 150))
    for i, l in enumerate(lines):
        t = shape(l)
        tw = d.textlength(t, font=f, **_kw())
        d.text(((w - tw) / 2, y0 + i * lh), t, font=f, fill=color + (255,), stroke_width=max(2, size // 14), stroke_fill=(0, 0, 0, 230), **_kw())
    img.save(out)
    return out


def _heart(d, cx, cy, s, fill):
    pts = []
    for i in range(0, 360, 6):
        t = math.radians(i)
        x = 16 * math.sin(t) ** 3
        y = -(13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t))
        pts.append((cx + x * s / 16, cy + y * s / 16))
    d.polygon(pts, fill=fill)


def _star(d, cx, cy, r, fill):
    pts = []
    for i in range(8):
        a = i * math.pi / 4
        rr = r if i % 2 == 0 else r * 0.22
        pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    d.polygon(pts, fill=fill)


def particle_tile(kind: str, w: int, h: int, out: str) -> str:
    """بلاط جسيمات بارتفاع 2h (يدور بسلاسة)"""
    rnd = random.Random(kind)
    s = w / 1080.0
    n = {"snow": 70, "rain": 90, "stars": 40, "bubbles": 28, "hearts": 24, "confetti": 70, "embers": 55}.get(kind, 40)
    items = [(rnd.random() * w, rnd.random() * h, rnd.random(), rnd.random()) for _ in range(n)]
    glow = Image.new("RGBA", (w, h * 2), (0, 0, 0, 0))
    crisp = Image.new("RGBA", (w, h * 2), (0, 0, 0, 0))
    gd, cd = ImageDraw.Draw(glow), ImageDraw.Draw(crisp)
    cols_h = [(255, 77, 121), (255, 46, 99), (255, 143, 177), (233, 30, 99)]
    cols_c = [(255, 193, 7), (229, 57, 53), (67, 160, 71), (30, 136, 229), (236, 64, 122), (142, 36, 170)]
    for off in (0, h):
        for x, y, a, b in items:
            for dy in (0, h, -h):
                yy = y + dy + off
                if not -80 < yy - off < h + 80:
                    continue
                if kind == "snow":
                    r = (3 + a * 9) * s
                    gd.ellipse((x - r, yy - r, x + r, yy + r), fill=(255, 255, 255, int(150 + 90 * b)))
                elif kind == "rain":
                    ln = (40 + a * 60) * s
                    cd.line((x, yy, x - ln * 0.12, yy + ln), fill=(190, 215, 255, int(90 + 90 * b)), width=max(1, int((1.5 + b * 1.5) * s)))
                elif kind == "stars":
                    r = (8 + a * 22) * s
                    gd.ellipse((x - r * .9, yy - r * .9, x + r * .9, yy + r * .9), fill=(255, 243, 176, 50))
                    _star(cd, x, yy, r, (255, 244, 190, int(180 + 75 * b)))
                elif kind == "bubbles":
                    r = (14 + a * 40) * s
                    cd.ellipse((x - r, yy - r, x + r, yy + r), outline=(221, 243, 255, 190), width=max(2, int(2.5 * s)), fill=(191, 232, 255, 34))
                    cd.ellipse((x - r * .55, yy - r * .55, x - r * .2, yy - r * .2), fill=(255, 255, 255, 200))
                elif kind == "hearts":
                    _heart(cd, x, yy, (18 + a * 34) * s * 2, cols_h[int(b * 4) % 4] + (225,))
                elif kind == "confetti":
                    c = cols_c[int(a * 6) % 6]
                    ang = a * 6.28
                    hw, hh = (7 + b * 5) * s, (3.5 + a * 3) * s
                    pts = [(-hw, -hh), (hw, -hh), (hw, hh), (-hw, hh)]
                    cd.polygon([(x + px * math.cos(ang) - py * math.sin(ang), yy + px * math.sin(ang) + py * math.cos(ang)) for px, py in pts], fill=c + (255,))
                elif kind == "embers":
                    r = (3 + a * 7) * s
                    gd.ellipse((x - r * 2.6, yy - r * 2.6, x + r * 2.6, yy + r * 2.6), fill=(255, 122, 24, 70))
                    cd.ellipse((x - r, yy - r, x + r, yy + r), fill=(255, int(138 + 90 * b), 20, 255))
    glow = glow.filter(ImageFilter.GaussianBlur(max(1, 3 * s)))
    glow.alpha_composite(crisp)
    glow.save(out)
    return out


def before_after(before: bytes, after: bytes, label_a="قبل", label_b="بعد") -> bytes:
    a, b = Image.open(io.BytesIO(before)).convert("RGB"), Image.open(io.BytesIO(after)).convert("RGB")
    H = 640
    a = a.resize((max(1, int(a.width * H / a.height)), H), Image.LANCZOS)
    b = b.resize((max(1, int(b.width * H / b.height)), H), Image.LANCZOS)
    gap = 6
    sheet = Image.new("RGB", (a.width + b.width + gap, H), (20, 20, 20))
    sheet.paste(a, (0, 0))
    sheet.paste(b, (a.width + gap, 0))
    d = ImageDraw.Draw(sheet)
    f = font(34)
    for txt, x in ((label_a, 14), (label_b, a.width + gap + 14)):
        t = shape(txt)
        tw = d.textlength(t, font=f, **_kw())
        d.rounded_rectangle((x - 8, 12, x + tw + 14, 62), radius=14, fill=(0, 0, 0, 170))
        d.text((x + 3, 12), t, font=f, fill="white", **_kw())
    buf = io.BytesIO()
    sheet.save(buf, "JPEG", quality=88)
    return buf.getvalue()


def template_grid(images: list, titles: list, cols: int = 5) -> bytes:
    """شبكة معاينات القوالب: كل خانة صورة فيديو المستخدم بعد تطبيق القالب مع رقمها واسمها"""
    ims = [Image.open(io.BytesIO(b)).convert("RGB") for b in images]
    portrait = ims[0].height >= ims[0].width
    cw, ch = (190, 338) if portrait else (338, 190)
    pad = 8
    rows = -(-len(ims) // cols)
    sheet = Image.new("RGB", (cols * (cw + pad) + pad, rows * (ch + pad) + pad), (18, 18, 22))
    d = ImageDraw.Draw(sheet)
    f_num, f_t = font(26), font(15)
    for i, (im, t) in enumerate(zip(ims, titles)):
        x, y = pad + (i % cols) * (cw + pad), pad + (i // cols) * (ch + pad)
        sheet.paste(im.resize((cw, ch), Image.LANCZOS), (x, y))
        d.rectangle((x, y + ch - 26, x + cw, y + ch), fill=(0, 0, 0))
        ts = shape(t)
        tw = d.textlength(ts, font=f_t, **_kw())
        d.text((x + (cw - tw) / 2, y + ch - 24), ts, font=f_t, fill="white", **_kw())
        d.ellipse((x + 6, y + 6, x + 44, y + 44), fill=(245, 197, 66))
        n = str(i + 1)
        nw = d.textlength(n, font=f_num)
        d.text((x + 25 - nw / 2, y + 7), n, font=f_num, fill=(20, 20, 20))
    buf = io.BytesIO()
    sheet.save(buf, "JPEG", quality=85)
    return buf.getvalue()

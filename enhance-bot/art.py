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


def _hex(c: str, a: int = 255):
    c = (c or "#ffffff").lstrip("#")
    if len(c) == 3:
        c = "".join(x * 2 for x in c)
    try:
        return int(c[0:2], 16), int(c[2:4], 16), int(c[4:6], 16), a
    except ValueError:
        return 255, 255, 255, a


def text_png_pro(p: dict, vw: int, vh: int, out: str) -> dict:
    """طبقة نص احترافية (خط/حجم/لون/تثخين/ظل/توهج/خلفية/دوران) مقصوصة حول النص مع إحداثيات مركزه"""
    text = (p.get("text") or "").strip() or " "
    fname = p.get("font") or "Cairo-b.ttf"
    if not (FONTS / fname).exists() or "/" in fname:
        fname = "Cairo-b.ttf"
    size = max(14, int(min(vw, vh) * float(p.get("size", 8)) / 100))
    f = ImageFont.truetype(str(FONTS / fname), size)
    maxw = vw * 0.9
    lines = []
    for para in text.split("\n"):
        cur = ""
        for w in para.split(" "):
            t = (cur + " " + w).strip()
            if cur and f.getlength(shape(t), **_kw()) > maxw:
                lines.append(cur)
                cur = w
            else:
                cur = t
        lines.append(cur)
    lh = int(size * 1.35)
    widths = [f.getlength(shape(l), **_kw()) for l in lines]
    sw = int(size * float(p.get("stroke", 0)) / 100)
    pad = int(size * 0.5) + sw * 2
    W, H = int(max(widths) + pad * 2), int(lh * len(lines) + pad * 2)
    color = _hex(p.get("color", "#ffffff"))
    sc = _hex(p.get("stroke_color", "#000000"))
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw_l = ImageDraw.Draw(layer)
    if p.get("box"):
        draw_l.rounded_rectangle((pad * 0.4, pad * 0.4, W - pad * 0.4, H - pad * 0.4), radius=size * 0.35, fill=_hex(p.get("box_color", "#000000"), 170))
    align = p.get("align", "center")
    glow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    shadow = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    dg, ds = ImageDraw.Draw(glow), ImageDraw.Draw(shadow)
    for i, l in enumerate(lines):
        t = shape(l)
        tw = widths[i]
        x = {"left": pad, "right": W - pad - tw}.get(align, (W - tw) / 2)
        y = pad + i * lh
        if p.get("glow"):
            dg.text((x, y), t, font=f, fill=color[:3] + (255,), stroke_width=max(2, size // 10), stroke_fill=color[:3] + (255,), **_kw())
        if p.get("shadow"):
            ds.text((x + size * 0.05, y + size * 0.07), t, font=f, fill=(0, 0, 0, 200), stroke_width=sw, stroke_fill=(0, 0, 0, 200), **_kw())
        draw_l.text((x, y), t, font=f, fill=color, stroke_width=sw, stroke_fill=sc, **_kw())
    comp = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    if p.get("shadow"):
        comp.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(max(1, size * 0.06))))
    if p.get("glow"):
        g = glow.filter(ImageFilter.GaussianBlur(max(2, size * 0.18)))
        comp.alpha_composite(g)
        comp.alpha_composite(g)
    comp.alpha_composite(layer)
    rot = float(p.get("rot", 0) or 0)
    if rot:
        comp = comp.rotate(-rot, expand=True, resample=Image.BICUBIC)
    comp.save(out)
    return {"path": out, "w": comp.width, "h": comp.height}


def page_grid(images: list, titles: list, start_no: int, cols: int, portrait: bool) -> bytes:
    """صفحة قوالب كبيرة وواضحة: صورة كل قالب بنسبة الفيديو الأصلية (بدون تمديد) مع رقمها واسمها"""
    ims = [Image.open(io.BytesIO(b)).convert("RGB") for b in images]
    cw, ch = (330, 586) if portrait else (480, 270)
    pad = 10
    rows = -(-len(ims) // cols)
    label_h = 54
    sheet = Image.new("RGB", (cols * (cw + pad) + pad, rows * (ch + label_h + pad) + pad), (16, 17, 21))
    d = ImageDraw.Draw(sheet)
    f_num, f_t = font(30), font(17)
    for i, (im, t) in enumerate(zip(ims, titles)):
        x, y = pad + (i % cols) * (cw + pad), pad + (i // cols) * (ch + label_h + pad)
        # نملأ الخانة بالقص المركزي مع الحفاظ على النسبة (لا تمديد)
        k = max(cw / im.width, ch / im.height)
        r = im.resize((max(cw, int(im.width * k)), max(ch, int(im.height * k))), Image.LANCZOS)
        ox, oy = (r.width - cw) // 2, (r.height - ch) // 2
        sheet.paste(r.crop((ox, oy, ox + cw, oy + ch)), (x, y))
        d.rounded_rectangle((x, y + ch, x + cw, y + ch + label_h), radius=10, fill=(30, 32, 40))
        # العنوان: سطران كحد أقصى
        words, lines, cur = t.split(" "), [], ""
        for w in words:
            tt = (cur + " " + w).strip()
            if cur and f_t.getlength(shape(tt), **_kw()) > cw - 16:
                lines.append(cur)
                cur = w
            else:
                cur = tt
        lines.append(cur)
        for li, line in enumerate(lines[:2]):
            ts = shape(line)
            tw = d.textlength(ts, font=f_t, **_kw())
            d.text((x + (cw - tw) / 2, y + ch + 5 + li * 22), ts, font=f_t, fill="white", **_kw())
        d.ellipse((x + 8, y + 8, x + 56, y + 56), fill=(245, 197, 66))
        n = str(start_no + i)
        nw = d.textlength(n, font=f_num)
        d.text((x + 32 - nw / 2, y + 12), n, font=f_num, fill=(20, 20, 20))
    buf = io.BytesIO()
    sheet.save(buf, "JPEG", quality=90)
    return buf.getvalue()


# ---------------- وسوم/تاغات التعديل (EDIT • REC • LIVE ...) ----------------
def tag_layer(style: str, text: str, w: int, h: int, pos: str = "tl") -> Image.Image:
    """طبقة RGBA بحجم الصورة/الفيديو فيها وسم على شكل حبّة (pill) بالأسلوب والموضع المطلوبين"""
    from PIL import ImageFilter
    size = max(14, int(min(w, h) * 0.045))
    f = font(size)
    t = shape(text or "EDIT")
    lay = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(lay)
    bb = d.textbbox((0, 0), t, font=f, **_kw())
    tw, th = bb[2] - bb[0], bb[3] - bb[1]
    padx, pady = int(size * 0.7), int(size * 0.45)
    dot = style in ("rec", "live")
    extra = int(size * 1.0) if dot else 0
    bw, bh = tw + padx * 2 + extra, th + pady * 2
    m = int(min(w, h) * 0.04)
    x = {"tl": m, "bl": m, "tr": w - bw - m, "br": w - bw - m, "top": (w - bw) // 2, "bot": (w - bw) // 2}.get(pos, m)
    y = {"tl": m, "tr": m, "top": m, "bl": h - bh - m, "br": h - bh - m, "bot": h - bh - m}.get(pos, m)
    r = bh // 2
    box = (x, y, x + bw, y + bh)
    if style == "neon":
        glow = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        ImageDraw.Draw(glow).rounded_rectangle(box, r, outline=(0, 255, 240, 255), width=max(3, size // 6))
        lay.alpha_composite(glow.filter(ImageFilter.GaussianBlur(size * 0.35)))
        d = ImageDraw.Draw(lay)
        d.rounded_rectangle(box, r, fill=(0, 20, 30, 150), outline=(120, 255, 250, 255), width=max(2, size // 12))
        col = (170, 255, 250, 255)
    else:
        fill = {"edit": (0, 0, 0, 175), "rec": (0, 0, 0, 175), "live": (225, 20, 40, 235), "badge": (255, 255, 255, 235), "gold": (245, 197, 66, 240)}.get(style, (0, 0, 0, 175))
        d.rounded_rectangle(box, r, fill=fill)
        col = (20, 20, 20, 255) if style in ("badge", "gold") else (255, 255, 255, 255)
    tx = x + padx + extra
    if dot:
        cy = y + bh // 2
        rr = int(size * 0.28)
        d.ellipse((x + padx, cy - rr, x + padx + 2 * rr, cy + rr), fill=(255, 40, 50, 255) if style == "rec" else (255, 255, 255, 255))
    d.text((tx - bb[0], y + pady - bb[1]), t, font=f, fill=col, **_kw())
    return lay


def tag_png(style: str, text: str, w: int, h: int, pos: str, out: str) -> str:
    tag_layer(style, text, w, h, pos).save(out)
    return out

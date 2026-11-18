import io
import re
from pathlib import Path

import qrcode
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter, ImageFont, ImageOps, ImageChops
from .arfont import draw_kw, shape

from .registry import Ctx, Out, ToolError, tool

FONTS = Path(__file__).parent.parent / "fonts"


def _open(c: Ctx) -> Image.Image:
    try:
        im = Image.open(c.path)
        im = ImageOps.exif_transpose(im)
        return im
    except Exception:
        raise ToolError("لم أستطع قراءة الصورة.")


def _save(im: Image.Image, fmt="PNG", name="result", **kw) -> Out:
    buf = io.BytesIO()
    if fmt == "JPEG" and im.mode in ("RGBA", "P", "LA"):
        im = im.convert("RGB")
    im.save(buf, fmt, **kw)
    buf.seek(0)
    return Out.doc(buf.getvalue(), f"{name}.{fmt.lower().replace('jpeg', 'jpg')}")


@tool("img_compress", "image", "ضغط الصورة", "أرسل الصورة وسأصغّر حجمها مع الحفاظ على الجودة.", inp="photo", opts=[("high", "جودة عالية"), ("mid", "متوسطة"), ("low", "أصغر حجم")])
def img_compress(c: Ctx):
    im = _open(c)
    q, w = {"high": (88, 2400), "mid": (72, 1600), "low": (50, 1080)}.get(c.opt or "mid")
    if max(im.size) > w:
        im.thumbnail((w, w), Image.LANCZOS)
    import os
    before = os.path.getsize(c.path)
    out = _save(im, "JPEG", "compressed", quality=q, optimize=True)
    after = len(out.data)
    out.caption = f"📦 {before / 1024:.0f}KB ← {after / 1024:.0f}KB (وفّرت {max(0, 100 - after * 100 // max(before, 1))}%)"
    return out


@tool("img_resize", "image", "تغيير حجم الصورة", "أرسل الصورة ثم اختر الحجم.", inp="photo", opts=[("50", "50%"), ("25", "25%"), ("200", "200%"), ("1080", "عرض 1080"), ("720", "عرض 720"), ("512", "512×512 (مربع)")])
def img_resize(c: Ctx):
    im = _open(c)
    o = c.opt or "50"
    if o == "512":
        im = ImageOps.fit(im, (512, 512), Image.LANCZOS)
    elif o in ("1080", "720"):
        w = int(o)
        im = im.resize((w, max(1, int(im.height * w / im.width))), Image.LANCZOS)
    else:
        f = int(o) / 100
        im = im.resize((max(1, int(im.width * f)), max(1, int(im.height * f))), Image.LANCZOS)
    out = _save(im, "PNG" if im.mode == "RGBA" else "JPEG", "resized", **({} if im.mode == "RGBA" else {"quality": 92}))
    out.caption = f"📐 الحجم الجديد: {im.width}×{im.height}"
    return out


@tool("img_convert", "image", "تحويل صيغة الصورة", "أرسل الصورة (كملف أو صورة) ثم اختر الصيغة.", inp="photo", opts=[("PNG", "PNG"), ("JPEG", "JPG"), ("WEBP", "WebP"), ("BMP", "BMP"), ("GIF", "GIF"), ("ICO", "أيقونة ICO"), ("PDF", "PDF")])
def img_convert(c: Ctx):
    im = _open(c)
    f = c.opt or "PNG"
    if f == "ICO":
        im = im.convert("RGBA")
        buf = io.BytesIO()
        im.save(buf, "ICO", sizes=[(256, 256), (128, 128), (64, 64), (32, 32)])
        return Out.doc(buf.getvalue(), "icon.ico")
    if f == "PDF":
        buf = io.BytesIO()
        im.convert("RGB").save(buf, "PDF", resolution=150)
        return Out.doc(buf.getvalue(), "image.pdf")
    return _save(im, f, "converted")


@tool("img_sticker", "image", "صورة إلى ملصق تيليجرام", "أرسل أي صورة وسأحوّلها لملصق WebP 512 (بخلفية شفافة إن كانت الصورة PNG).", inp="photo", opts=[("plain", "ملصق عادي"), ("outline", "مع حدّ أبيض")])
def img_sticker(c: Ctx):
    im = _open(c).convert("RGBA")
    im.thumbnail((512, 512), Image.LANCZOS)
    canvas = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    canvas.paste(im, ((512 - im.width) // 2, (512 - im.height) // 2), im)
    if c.opt == "outline":
        a = canvas.split()[3]
        grown = a.filter(ImageFilter.MaxFilter(9))
        white = Image.new("RGBA", canvas.size, (255, 255, 255, 255))
        base = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
        base.paste(white, mask=grown)
        base.alpha_composite(canvas)
        canvas = base
    buf = io.BytesIO()
    canvas.save(buf, "WEBP", quality=95, lossless=False)
    return Out.sticker(buf.getvalue())


@tool("img_rotate", "image", "تدوير وقلب الصورة", "أرسل الصورة ثم اختر.", inp="photo", opts=[("90", "90° يمين"), ("-90", "90° يسار"), ("180", "180°"), ("flip_h", "قلب أفقي"), ("flip_v", "قلب عمودي")])
def img_rotate(c: Ctx):
    im = _open(c)
    o = c.opt or "90"
    if o == "flip_h":
        im = ImageOps.mirror(im)
    elif o == "flip_v":
        im = ImageOps.flip(im)
    else:
        im = im.rotate(-int(o), expand=True)
    return _save(im, "PNG" if im.mode == "RGBA" else "JPEG", "rotated", **({} if im.mode == "RGBA" else {"quality": 95}))


def _sepia(im):
    g = ImageOps.grayscale(im)
    return ImageOps.colorize(g, "#2b1a0a", "#f4e3c1", mid="#a07850")


@tool("img_filter", "image", "فلاتر الصور", "أرسل الصورة ثم اختر الفلتر.", inp="photo", opts=[("bw", "أبيض وأسود"), ("sepia", "سيبيا"), ("blur", "تغبيش"), ("sharp", "توضيح"), ("invert", "عكس الألوان"), ("vivid", "ألوان زاهية"), ("warm", "دافئ"), ("cool", "بارد"), ("sketch", "رسم بالقلم"), ("pixel", "بكسل"), ("emboss", "نقش")])
def img_filter(c: Ctx):
    im = _open(c).convert("RGB")
    o = c.opt or "bw"
    if o == "bw":
        im = ImageOps.grayscale(im).convert("RGB")
    elif o == "sepia":
        im = _sepia(im)
    elif o == "blur":
        im = im.filter(ImageFilter.GaussianBlur(max(im.size) / 120))
    elif o == "sharp":
        im = im.filter(ImageFilter.UnsharpMask(radius=2, percent=180, threshold=2))
    elif o == "invert":
        im = ImageOps.invert(im)
    elif o == "vivid":
        im = ImageEnhance.Contrast(ImageEnhance.Color(im).enhance(1.6)).enhance(1.1)
    elif o in ("warm", "cool"):
        r, g, b = im.split()
        k = 1.12 if o == "warm" else 0.9
        r = r.point(lambda v: min(255, int(v * k)))
        b = b.point(lambda v: min(255, int(v * (2 - k))))
        im = Image.merge("RGB", (r, g, b))
    elif o == "sketch":
        g = ImageOps.grayscale(im).filter(ImageFilter.GaussianBlur(1))
        e = ImageOps.invert(g.filter(ImageFilter.FIND_EDGES))
        im = ImageOps.autocontrast(e, cutoff=2).convert("RGB")
    elif o == "pixel":
        s = max(8, max(im.size) // 80)
        im = im.resize((max(1, im.width // s), max(1, im.height // s)), Image.NEAREST).resize(im.size, Image.NEAREST)
    elif o == "emboss":
        im = im.filter(ImageFilter.EMBOSS)
    return _save(im, "JPEG", f"filter_{o}", quality=93)


@tool("img_enhance", "image", "تحسين وتوضيح الصورة", "أرسل صورة ضعيفة وسأكبّرها وأوضّحها وأحسّن ألوانها (معالجة برمجية).", inp="photo", opts=[("x2", "تكبير 2× + توضيح"), ("auto", "تحسين الألوان فقط"), ("x4", "تكبير 4×")])
def img_enhance(c: Ctx):
    im = _open(c).convert("RGB")
    o = c.opt or "x2"
    if o in ("x2", "x4"):
        f = 2 if o == "x2" else 4
        w, h = im.width * f, im.height * f
        if w * h > 36_000_000:
            raise ToolError("الصورة كبيرة جداً للتكبير، جرّب صورة أصغر.")
        im = im.resize((w, h), Image.LANCZOS)
    im = ImageOps.autocontrast(im, cutoff=0.5)
    im = im.filter(ImageFilter.UnsharpMask(radius=1.6, percent=140, threshold=2))
    im = ImageEnhance.Color(im).enhance(1.12)
    out = _save(im, "PNG" if max(im.size) < 3000 else "JPEG", "enhanced", **({} if max(im.size) < 3000 else {"quality": 93}))
    out.caption = f"✨ {im.width}×{im.height}"
    return out


@tool("img_circle", "image", "قص الصورة بشكل دائري", "أرسل الصورة وسأقصها دائرة بخلفية شفافة PNG.", inp="photo")
def img_circle(c: Ctx):
    im = ImageOps.fit(_open(c).convert("RGBA"), (800, 800), Image.LANCZOS)
    mask = Image.new("L", (800, 800), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, 799, 799), fill=255)
    im.putalpha(mask)
    return _save(im, "PNG", "circle")


@tool("img_exif", "image", "معلومات الصورة + حذف البيانات الخفية", "أرسل الصورة كملف (Document) لتصلني البيانات كاملة (الكاميرا، الموقع GPS…).", inp="file", opts=[("info", "عرض المعلومات"), ("strip", "حذف كل البيانات الخفية")])
def img_exif(c: Ctx):
    im = Image.open(c.path)
    if c.opt == "strip":
        data = list(im.getdata())
        clean = Image.new(im.mode, im.size)
        clean.putdata(data)
        return _save(clean, "PNG" if im.mode == "RGBA" else "JPEG", "clean", **({} if im.mode == "RGBA" else {"quality": 95}))
    ex = im.getexif()
    from PIL.ExifTags import TAGS, GPSTAGS
    lines = [f"🖼 الصيغة: {im.format} • الحجم: {im.width}×{im.height} • النمط: {im.mode}"]
    for k, v in ex.items():
        n = TAGS.get(k, str(k))
        if n in ("Make", "Model", "Software", "DateTime", "LensModel", "ExposureTime", "FNumber", "ISOSpeedRatings", "Orientation") and v:
            lines.append(f"• {n}: {v}")
    gps = ex.get_ifd(0x8825) if hasattr(ex, "get_ifd") else {}
    if gps:
        def deg(v, ref):
            d = float(v[0]) + float(v[1]) / 60 + float(v[2]) / 3600
            return -d if ref in ("S", "W") else d
        try:
            lat, lon = deg(gps[2], gps[1]), deg(gps[4], gps[3])
            lines.append(f"📍 الموقع GPS: <a href=\"https://maps.google.com/?q={lat},{lon}\">{lat:.5f}, {lon:.5f}</a> ⚠️ الصورة تكشف مكان التصوير!")
        except Exception:
            lines.append("📍 توجد بيانات GPS")
    if len(lines) == 1:
        lines.append("لا توجد بيانات كاميرا (الصورة نظيفة أو أُزيلت منها).")
    return Out.text("\n".join(lines))


@tool("img_colors", "image", "استخراج ألوان الصورة", "أرسل صورة وسأستخرج أهم ألوانها بأكواد HEX.", inp="photo")
def img_colors(c: Ctx):
    im = _open(c).convert("RGB")
    im.thumbnail((200, 200))
    q = im.quantize(colors=6, method=Image.MEDIANCUT).convert("RGB")
    cols = sorted(q.getcolors(), reverse=True)
    total = sum(n for n, _ in cols)
    rows = [f"<code>#{r:02X}{g:02X}{b:02X}</code> — {n * 100 // total}%" for n, (r, g, b) in cols]
    sw = Image.new("RGB", (600, 120))
    x = 0
    d = ImageDraw.Draw(sw)
    for n, col in cols:
        w = max(1, round(600 * n / total))
        d.rectangle((x, 0, x + w, 120), fill=col)
        x += w
    buf = io.BytesIO()
    sw.save(buf, "PNG")
    return [Out.photo(buf.getvalue(), "🎨 الألوان الرئيسية\n" + "\n".join(rows))]


@tool("ocr", "image", "استخراج النص من الصورة (OCR)", "أرسل صورة فيها كتابة (عربي/إنجليزي) وسأستخرج النص.", inp="photo", opts=[("ara+eng", "عربي + إنجليزي"), ("ara", "عربي"), ("eng", "إنجليزي")])
def ocr(c: Ctx):
    import pytesseract
    im = _open(c).convert("L")
    if max(im.size) < 1400:
        im = im.resize((im.width * 2, im.height * 2), Image.LANCZOS)
    im = ImageOps.autocontrast(im)
    try:
        txt = pytesseract.image_to_string(im, lang=c.opt or "ara+eng", config="--psm 6").strip()
    except Exception as e:  # noqa: BLE001
        raise ToolError(f"محرك OCR غير مثبت على الخادم: {type(e).__name__}")
    if not txt:
        raise ToolError("لم أجد نصاً واضحاً في الصورة.")
    return Out.text(f"📝 <b>النص المستخرج:</b>\n\n<code>{txt[:3800]}</code>")


@tool("qr_read", "image", "قراءة QR والباركود", "أرسل صورة فيها QR أو باركود وسأقرأه.", inp="photo")
def qr_read(c: Ctx):
    import zxingcpp
    res = zxingcpp.read_barcodes(_open(c).convert("RGB"))
    if not res:
        raise ToolError("لم أجد QR أو باركود في الصورة.")
    return Out.text("\n\n".join(f"📷 <b>{r.format.name}</b>\n<code>{r.text}</code>" for r in res))


@tool("qr_make", "image", "صانع QR احترافي", "أرسل النص أو الرابط (أو واي فاي: WIFI:S:الاسم;T:WPA;P:كلمة السر;;).", opts=[("black", "أسود"), ("blue", "أزرق"), ("green", "أخضر"), ("red", "أحمر"), ("purple", "بنفسجي")], sample="https://example.com")
def qr_make(c: Ctx):
    t = c.text.strip()
    if not t or len(t) > 1000:
        raise ToolError("أرسل نصاً أو رابطاً (حتى 1000 حرف).")
    col = {"black": "#000000", "blue": "#0b3d91", "green": "#0a7d3b", "red": "#b3141b", "purple": "#6a1b9a"}.get(c.opt or "black")
    qr = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_Q, box_size=14, border=3)
    qr.add_data(t)
    img = qr.make_image(fill_color=col, back_color="white").convert("RGB")
    buf = io.BytesIO()
    img.save(buf, "PNG")
    return Out.photo(buf.getvalue(), "✅ جاهز")


@tool("img_watermark", "image", "علامة مائية نصية", "أرسل الصورة أولاً ثم اكتب نص العلامة.", inp="photo")
def img_watermark(c: Ctx):
    if not c.text:
        from .registry import Ask
        raise Ask("✍️ اكتب نص العلامة المائية (مثل @اسمك):")
    im = _open(c).convert("RGBA")
    layer = Image.new("RGBA", im.size, (0, 0, 0, 0))
    size = max(24, im.width // 14)
    font = ImageFont.truetype(str(FONTS / "Cairo-b.ttf"), size)
    txt = shape(c.text)
    d = ImageDraw.Draw(layer)
    w = d.textlength(txt, font=font, **draw_kw())
    d.text((im.width - w - size // 2, im.height - size * 2), txt, font=font, fill=(255, 255, 255, 190), stroke_width=2, stroke_fill=(0, 0, 0, 120), **draw_kw())
    return _save(Image.alpha_composite(im, layer).convert("RGB"), "JPEG", "watermarked", quality=94)


@tool("img_meme", "image", "ميمز: نص أعلى وأسفل الصورة", "أرسل الصورة ثم النص (سطر للأعلى وسطر للأسفل).", inp="photo")
def img_meme(c: Ctx):
    if not c.text:
        from .registry import Ask
        raise Ask("✍️ اكتب النص: السطر الأول للأعلى والثاني للأسفل:")
    im = _open(c).convert("RGB")
    d = ImageDraw.Draw(im)
    lines = c.text.splitlines()
    size = max(28, im.width // 9)
    font = ImageFont.truetype(str(FONTS / "Cairo-b.ttf"), size)

    def put(t, top):
        t = shape(t.strip())
        f = font
        sz = size
        while f.getlength(t, **draw_kw()) > im.width - 40 and sz > 18:
            sz -= 2
            f = ImageFont.truetype(str(FONTS / "Cairo-b.ttf"), sz)
        w = d.textlength(t, font=f, **draw_kw())
        y = 12 if top else im.height - size * 1.9
        d.text(((im.width - w) / 2, y), t, font=f, fill="white", stroke_width=max(3, size // 12), stroke_fill="black", **draw_kw())
    put(lines[0], True)
    if len(lines) > 1:
        put(lines[1], False)
    return _save(im, "JPEG", "meme", quality=93)


@tool("img_ascii", "image", "صورة إلى رسم ASCII", "أرسل صورة وسأحوّلها لرسم نصي.", inp="photo")
def img_ascii(c: Ctx):
    im = ImageOps.grayscale(_open(c))
    w = 60
    im = im.resize((w, max(1, int(im.height / im.width * w * 0.5))))
    chars = "@%#*+=-:. "
    rows = ["".join(chars[min(len(chars) - 1, p * len(chars) // 256)] for p in row) for row in (list(im.getdata())[i * w:(i + 1) * w] for i in range(im.height))]
    return Out.text("<pre>" + "\n".join(rows) + "</pre>")


@tool("photos2pdf", "image", "تحويل صور إلى PDF واحد", "أرسل الصور واحدة تلو الأخرى ثم اضغط «تم».", inp="photos")
def photos2pdf(c: Ctx):
    ims = [ImageOps.exif_transpose(Image.open(p)).convert("RGB") for p in c.paths]
    if not ims:
        raise ToolError("لم تُرسل صوراً.")
    buf = io.BytesIO()
    ims[0].save(buf, "PDF", save_all=True, append_images=ims[1:], resolution=150)
    return Out.doc(buf.getvalue(), "photos.pdf", f"📄 {len(ims)} صفحة")


@tool("img_collage", "image", "كولاج صور", "أرسل 2 إلى 9 صور ثم اضغط «تم».", inp="photos")
def img_collage(c: Ctx):
    ims = [ImageOps.exif_transpose(Image.open(p)).convert("RGB") for p in c.paths]
    n = len(ims)
    if n < 2:
        raise ToolError("أرسل صورتين على الأقل.")
    cols = n if n <= 3 else (2 if n == 4 else 3)
    rows = -(-n // cols)
    cell = 600
    sheet = Image.new("RGB", (cols * cell, rows * cell), "white")
    for i, im in enumerate(ims[:9]):
        sheet.paste(ImageOps.fit(im, (cell - 6, cell - 6), Image.LANCZOS), ((i % cols) * cell + 3, (i // cols) * cell + 3))
    return _save(sheet, "JPEG", "collage", quality=92)


@tool("remove_bg_color", "image", "إزالة خلفية بلون واحد", "أرسل صورة بخلفية بيضاء/خضراء... وسأجعلها شفافة (PNG).", inp="photo", opts=[("white", "خلفية بيضاء"), ("black", "خلفية سوداء"), ("green", "خلفية خضراء"), ("corner", "حسب لون الزاوية")])
def remove_bg_color(c: Ctx):
    im = _open(c).convert("RGBA")
    if max(im.size) > 1600:
        im.thumbnail((1600, 1600), Image.LANCZOS)
    o = c.opt or "white"
    ref = {"white": (255, 255, 255), "black": (0, 0, 0), "green": (0, 177, 64)}.get(o) or im.getpixel((2, 2))[:3]
    px = im.load()
    tol = 60
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            dist = ((r - ref[0]) ** 2 + (g - ref[1]) ** 2 + (b - ref[2]) ** 2) ** 0.5
            if dist < tol:
                px[x, y] = (r, g, b, 0)
            elif dist < tol + 40:
                px[x, y] = (r, g, b, int(255 * (dist - tol) / 40))
    return _save(im, "PNG", "transparent")

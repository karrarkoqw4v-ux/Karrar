import io
import os
import re
import shutil
import zipfile
from pathlib import Path

import pymupdf as fitz
from pypdf import PdfReader, PdfWriter

from .registry import Ask, Ctx, Out, ToolError, tool


def _pages(spec: str, n: int) -> list[int]:
    out = []
    for part in re.split(r"[,\s،]+", spec.strip()):
        if not part:
            continue
        m = re.fullmatch(r"(\d+)-(\d+)", part)
        if m:
            a, b = int(m.group(1)), int(m.group(2))
            out += list(range(a, b + 1))
        elif part.isdigit():
            out.append(int(part))
        else:
            raise ToolError("اكتب الصفحات مثل: 1-3,5,8")
    out = [p for p in out if 1 <= p <= n]
    if not out:
        raise ToolError(f"لا توجد صفحات صالحة (الملف فيه {n} صفحة).")
    return out


@tool("pdf_info", "pdf", "معلومات ملف PDF", "أرسل ملف PDF.", inp="pdf")
def pdf_info(c: Ctx):
    r = PdfReader(c.path)
    m = r.metadata or {}
    return Out.text(f"📄 <b>{c.name or 'PDF'}</b>\nالصفحات: {len(r.pages)}\nالحجم: {os.path.getsize(c.path) / 1024:.0f}KB\nمشفّر: {'نعم 🔒' if r.is_encrypted else 'لا'}\nالعنوان: {m.get('/Title') or '—'}\nالمؤلف: {m.get('/Author') or '—'}\nالبرنامج: {m.get('/Producer') or '—'}")


@tool("pdf_merge", "pdf", "دمج ملفات PDF", "أرسل ملفات PDF بالترتيب ثم اضغط «تم».", inp="pdfs")
def pdf_merge(c: Ctx):
    if len(c.paths) < 2:
        raise ToolError("أرسل ملفين على الأقل.")
    w = PdfWriter()
    for p in c.paths:
        for pg in PdfReader(p).pages:
            w.add_page(pg)
    buf = io.BytesIO()
    w.write(buf)
    return Out.doc(buf.getvalue(), "merged.pdf", f"✅ تم دمج {len(c.paths)} ملفات")


@tool("pdf_extract", "pdf", "استخراج صفحات من PDF", "أرسل الملف ثم اكتب الصفحات (مثل 1-3,5).", inp="pdf")
def pdf_extract(c: Ctx):
    r = PdfReader(c.path)
    if not c.text:
        raise Ask(f"✍️ الملف فيه {len(r.pages)} صفحة. اكتب الصفحات المطلوبة (مثل 1-3,5):")
    w = PdfWriter()
    for p in _pages(c.text, len(r.pages)):
        w.add_page(r.pages[p - 1])
    buf = io.BytesIO()
    w.write(buf)
    return Out.doc(buf.getvalue(), "pages.pdf")


@tool("pdf_split", "pdf", "تقسيم PDF لملفات", "أرسل الملف وسأقسّمه إلى ملف لكل صفحة (ZIP).", inp="pdf")
def pdf_split(c: Ctx):
    r = PdfReader(c.path)
    if len(r.pages) > 100:
        raise ToolError("الحد 100 صفحة.")
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
        for i, pg in enumerate(r.pages, 1):
            w = PdfWriter()
            w.add_page(pg)
            b = io.BytesIO()
            w.write(b)
            z.writestr(f"page_{i:03d}.pdf", b.getvalue())
    return Out.doc(buf.getvalue(), "pages.zip", f"✅ {len(r.pages)} ملف")


@tool("pdf_images", "pdf", "تحويل PDF إلى صور", "أرسل الملف وسأحوّل صفحاته إلى صور (حتى 15 صفحة).", inp="pdf", opts=[("100", "جودة عادية"), ("170", "جودة عالية")])
def pdf_images(c: Ctx):
    doc = fitz.open(c.path)
    dpi = int(c.opt or 100)
    outs = []
    for i, page in enumerate(doc):
        if i >= 15:
            break
        pix = page.get_pixmap(dpi=dpi)
        outs.append(Out.photo(pix.tobytes("jpeg"), f"صفحة {i + 1}/{len(doc)}"))
    return outs


@tool("pdf_text", "pdf", "استخراج النص من PDF", "أرسل الملف وسأستخرج نصه (للملفات الممسوحة ضوئياً استخدم OCR على صورة).", inp="pdf")
def pdf_text(c: Ctx):
    doc = fitz.open(c.path)
    txt = "\n\n".join(p.get_text() for p in doc).strip()
    if not txt:
        raise ToolError("لا يوجد نص قابل للاستخراج (ملف ممسوح ضوئياً؟ حوّله لصور واستخدم OCR).")
    if len(txt) < 3500:
        return Out.text(f"<code>{txt}</code>")
    return Out.doc(txt.encode(), "text.txt", f"📝 {len(txt):,} حرف")


@tool("pdf_compress", "pdf", "ضغط PDF", "أرسل الملف وسأقلّل حجمه.", inp="pdf")
def pdf_compress(c: Ctx):
    doc = fitz.open(c.path)
    before = os.path.getsize(c.path)
    data = doc.tobytes(garbage=4, deflate=True, clean=True)
    if len(data) >= before:
        # محاولة أقوى: إعادة ضغط الصور
        for page in doc:
            for img in page.get_images():
                try:
                    pix = fitz.Pixmap(doc, img[0])
                    if pix.width * pix.height > 1_000_000 and pix.n < 5:
                        pix = fitz.Pixmap(pix, 0) if pix.alpha else pix
                        doc.update_stream(img[0], pix.tobytes("jpeg", jpg_quality=55))
                except Exception:
                    pass
        data = doc.tobytes(garbage=4, deflate=True, clean=True)
    return Out.doc(data, "compressed.pdf", f"📦 {before / 1024:.0f}KB ← {len(data) / 1024:.0f}KB")


@tool("pdf_protect", "pdf", "حماية PDF بكلمة سر", "أرسل الملف ثم اكتب كلمة السر.", inp="pdf")
def pdf_protect(c: Ctx):
    if not c.text:
        raise Ask("🔒 اكتب كلمة السر التي تريدها:")
    w = PdfWriter(clone_from=c.path)
    w.encrypt(c.text.strip())
    buf = io.BytesIO()
    w.write(buf)
    return Out.doc(buf.getvalue(), "protected.pdf", "🔒 تم التشفير")


@tool("pdf_unlock", "pdf", "فك قفل PDF", "أرسل الملف المحمي ثم كلمة سره (لا أفك الأقفال بدون كلمة السر).", inp="pdf")
def pdf_unlock(c: Ctx):
    r = PdfReader(c.path)
    if not r.is_encrypted:
        raise ToolError("الملف غير محمي.")
    if not c.text:
        raise Ask("🔑 اكتب كلمة السر:")
    if not r.decrypt(c.text.strip()):
        raise ToolError("كلمة السر خاطئة.")
    w = PdfWriter()
    for p in r.pages:
        w.add_page(p)
    buf = io.BytesIO()
    w.write(buf)
    return Out.doc(buf.getvalue(), "unlocked.pdf")


@tool("pdf_rotate", "pdf", "تدوير صفحات PDF", "أرسل الملف ثم اختر الزاوية.", inp="pdf", opts=[("90", "90°"), ("180", "180°"), ("270", "270°")])
def pdf_rotate(c: Ctx):
    w = PdfWriter(clone_from=c.path)
    for p in w.pages:
        p.rotate(int(c.opt or 90))
    buf = io.BytesIO()
    w.write(buf)
    return Out.doc(buf.getvalue(), "rotated.pdf")


@tool("zip_make", "pdf", "ضغط ملفات في ZIP", "أرسل ملفات (Documents) ثم اضغط «تم».", inp="files")
def zip_make(c: Ctx):
    if not c.paths:
        raise ToolError("لم تُرسل ملفات.")
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
        for p in c.paths:
            z.write(p, Path(p).name.split("__", 1)[-1])
    return Out.doc(buf.getvalue(), "files.zip")


@tool("zip_open", "pdf", "فك ضغط ZIP", "أرسل ملف ZIP (حتى 20MB).", inp="file")
def zip_open(c: Ctx):
    outs = []
    try:
        z = zipfile.ZipFile(c.path)
    except zipfile.BadZipFile:
        raise ToolError("الملف ليس ZIP صالحاً.")
    infos = [i for i in z.infolist() if not i.is_dir()]
    if sum(i.file_size for i in infos) > 200 * 1024 * 1024 or len(infos) > 50:
        raise ToolError("الأرشيف كبير جداً (الحد 50 ملفاً / 200MB).")
    for i in infos[:20]:
        name = Path(i.filename).name
        if name:
            outs.append(Out.doc(z.read(i), name))
    outs.insert(0, Out.text(f"📦 فيه {len(infos)} ملف" + (" (أعرض أول 20)" if len(infos) > 20 else "")))
    return outs


@tool("txt_file", "pdf", "تحويل نص إلى ملف TXT/PDF", "أرسل النص الطويل وسأصنع منه ملفاً.", opts=[("txt", "ملف TXT"), ("pdf", "ملف PDF")], sample="سطر أول\nسطر ثان")
def txt_file(c: Ctx):
    if c.opt == "pdf":
        import arabic_reshaper
        from bidi.algorithm import get_display
        font = str(Path(__file__).parent.parent / "fonts" / "Cairo-r.ttf")
        doc = fitz.open()
        rect = fitz.Rect(50, 50, 545, 790)
        lines = [get_display(arabic_reshaper.reshape(p)) if p.strip() else " " for p in c.text.splitlines()]
        rtl = bool(re.search(r"[\u0600-\u06FF]", c.text))
        i = 0
        while i < len(lines) or i == 0:
            page = doc.new_page()
            page.insert_font(fontname="cairo", fontfile=font)
            chunk, used = [], 0
            while i < len(lines) and used < 33:
                chunk.append(lines[i])
                used += max(1, len(lines[i]) // 55 + 1)
                i += 1
            page.insert_textbox(rect, "\n".join(chunk), fontname="cairo", fontsize=13, align=fitz.TEXT_ALIGN_RIGHT if rtl else fitz.TEXT_ALIGN_LEFT)
            if not chunk:
                break
        return Out.doc(doc.tobytes(), "text.pdf")
    return Out.doc(c.text.encode(), "text.txt")

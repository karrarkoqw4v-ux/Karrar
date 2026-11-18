import io
import random
import re
import unicodedata
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont
from .arfont import draw_kw, shape

from .registry import Ctx, Out, ToolError, tool

FONTS = Path(__file__).parent.parent / "fonts"

# ---------- زخرفة الإنجليزية ----------
_A = "abcdefghijklmnopqrstuvwxyz"
_U = _A.upper()


def _map(lower: str, upper: str, digits: str | None = None):
    m = {}
    for i, c in enumerate(_A):
        m[c] = lower[i]
    for i, c in enumerate(_U):
        m[c] = upper[i]
    if digits:
        for i, c in enumerate("0123456789"):
            m[c] = digits[i]
    return m


def _seq(start: int, n: int = 26) -> str:
    return "".join(chr(start + i) for i in range(n))


STYLES = {
    "𝐁𝐨𝐥𝐝": _map(_seq(0x1D41A), _seq(0x1D400), _seq(0x1D7CE, 10)),
    "𝘐𝘵𝘢𝘭𝘪𝘤": _map(_seq(0x1D622), _seq(0x1D608)),
    "𝑩𝒐𝒍𝒅 𝑰𝒕𝒂𝒍𝒊𝒄": _map(_seq(0x1D482), _seq(0x1D468)),
    "𝓢𝓬𝓻𝓲𝓹𝓽": _map(_seq(0x1D4EA), _seq(0x1D4D0)),
    "𝔉𝔯𝔞𝔨𝔱𝔲𝔯": _map(_seq(0x1D51E), _seq(0x1D504)),
    "𝕕𝕠𝕦𝕓𝕝𝕖": _map(_seq(0x1D552), _seq(0x1D538), _seq(0x1D7D8, 10)),
    "𝙼𝚘𝚗𝚘": _map(_seq(0x1D68A), _seq(0x1D670), _seq(0x1D7F6, 10)),
    "ⓒⓘⓡⓒⓛⓔ": _map(_seq(0x24D0), _seq(0x24B6), "⓪①②③④⑤⑥⑦⑧⑨"),
    "🅂🅀🅄🄰🅁🄴": _map(_seq(0x1F130), _seq(0x1F130)),
    "ｗｉｄｅ": _map(_seq(0xFF41), _seq(0xFF21), _seq(0xFF10, 10)),
    "ꜱᴍᴀʟʟ ᴄᴀᴘꜱ": _map("ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ", _U),
}


def _style(text: str, m: dict) -> str:
    return "".join(m.get(c, c) for c in text)


@tool("zk_en", "text", "زخرفة الأسماء الإنجليزية", "أرسل الاسم بالإنجليزية وسأعطيك 15+ شكل زخرفة جاهز للنسخ.", sample="Ahmed Ali")
def zk_en(c: Ctx):
    t = c.text.strip()
    if not t:
        raise ToolError("أرسل نصاً.")
    lines = [f"<code>{_style(t, m)}</code>" for m in STYLES.values()]
    lines.append("<code>" + "".join(ch + "̶" for ch in t) + "</code>")  # مشطوب
    lines.append("<code>" + "".join(ch + "̲" for ch in t) + "</code>")  # مسطر
    lines.append("<code>" + "".join(ch + "҉" for ch in t) + "</code>")
    lines.append("<code>꧁༺ " + t + " ༻꧂</code>")
    lines.append("<code>•°•✿ " + t + " ✿•°•</code>")
    lines.append("<code>『" + t + "』</code>")
    return Out.text("✨ <b>الزخارف</b> (اضغط على أي سطر لنسخه):\n\n" + "\n".join(lines))


@tool("zk_ar", "text", "زخرفة الأسماء العربية", "أرسل الاسم بالعربية وسأعطيك أشكالاً مزخرفة (تطويل، رموز، تشكيل).", sample="محمد علي")
def zk_ar(c: Ctx):
    t = c.text.strip()
    if not t:
        raise ToolError("أرسل نصاً.")
    nojoin = set("اأإآدذرزوؤةى ")

    def tatweel(s, n=1):
        out = ""
        for i, ch in enumerate(s):
            out += ch
            if i < len(s) - 1 and ch not in nojoin and s[i + 1] != " ":
                out += "ـ" * n
        return out

    def tashkeel(s):
        marks = ["َ", "ُ", "ِ", "ّ"]
        return "".join(ch + (random.choice(marks) if ch.strip() and "ء" <= ch <= "ي" else "") for ch in s)

    items = [
        tatweel(t), tatweel(t, 2), tashkeel(t),
        f"﴿ {t} ﴾", f"✦ {t} ✦", f"༄ {t} ༄", f"【{t}】", f"❁ {t} ❁", f"『{tatweel(t)}』", f"♛ {t} ♛", f"•°• {tatweel(t)} •°•", f"⸙ {t} ⸙",
        f"꧁ {t} ꧂", f"✿ {tashkeel(t)} ✿", f"﷽ {t}", f"〖{t}〗", f"❝ {t} ❞", f"⌁︙{t}", f"𖤍 {t} 𖤍", f"☬ {tatweel(t)} ☬",
    ]
    return Out.text("✨ <b>زخارف عربية</b>\n\n" + "\n".join(f"<code>{x}</code>" for x in items))


@tool("reverse", "text", "عكس النص", "أرسل النص وسأعكسه (حروف أو كلمات).", opts=[("chars", "عكس الحروف"), ("words", "عكس ترتيب الكلمات"), ("lines", "عكس الأسطر")], sample="مرحبا بك")
def reverse(c: Ctx):
    t = c.text
    if c.opt == "words":
        r = " ".join(t.split()[::-1])
    elif c.opt == "lines":
        r = "\n".join(t.splitlines()[::-1])
    else:
        r = t[::-1]
    return Out.text(f"<code>{r}</code>")


@tool("count", "text", "عدّاد الحروف والكلمات", "أرسل نصاً لأحسب لك الحروف والكلمات والأسطر ووقت القراءة.", sample="هذا نص تجريبي. فيه جملتان!")
def count(c: Ctx):
    t = c.text
    words = re.findall(r"\w+", t, re.U)
    sents = [s for s in re.split(r"[.!?؟…\n]+", t) if s.strip()]
    uniq = len({w.lower() for w in words})
    minutes = max(1, round(len(words) / 180)) if words else 0
    return Out.text(
        "📊 <b>إحصائيات النص</b>\n"
        f"🔤 الحروف: <b>{len(t)}</b> (بدون مسافات: {len(re.sub(r'\s', '', t))})\n"
        f"📖 الكلمات: <b>{len(words)}</b> (فريدة: {uniq})\n"
        f"📄 الأسطر: <b>{len(t.splitlines()) or 0}</b>\n"
        f"✍️ الجمل: <b>{len(sents)}</b>\n"
        f"⏱ وقت القراءة: ~{minutes} دقيقة")


@tool("diacritics", "text", "إزالة التشكيل", "أرسل نصاً عربياً مشكّلاً وسأزيل الحركات منه.", opts=[("all", "إزالة التشكيل فقط"), ("tatweel", "التشكيل + التطويل")], sample="السَّلامُ عَلَيْكُمْ")
def diacritics(c: Ctx):
    r = re.sub(r"[ً-ٰٟۖ-ۭ]", "", c.text)
    if c.opt == "tatweel":
        r = r.replace("ـ", "")
    return Out.text(f"<code>{r}</code>")


@tool("digits", "text", "تحويل الأرقام (هندية ↔ عربية)", "أرسل نصاً فيه أرقام وسأحوّلها.", opts=[("to_en", "إلى 0123"), ("to_ar", "إلى ٠١٢٣")], sample="رقمي ١٢٣٤٥")
def digits(c: Ctx):
    ar, en = "٠١٢٣٤٥٦٧٨٩", "0123456789"
    t = c.text
    if c.opt == "to_ar":
        r = t.translate(str.maketrans(en, ar))
    else:
        r = t.translate(str.maketrans(ar + "۰۱۲۳۴۵۶۷۸۹", en + en))
    return Out.text(f"<code>{r}</code>")


@tool("case", "text", "تغيير حالة الأحرف", "أرسل نصاً إنجليزياً.", opts=[("upper", "UPPER"), ("lower", "lower"), ("title", "Title Case"), ("swap", "sWAP")], sample="hello World")
def case(c: Ctx):
    f = {"upper": str.upper, "lower": str.lower, "title": str.title, "swap": str.swapcase}.get(c.opt, str.upper)
    return Out.text(f"<code>{f(c.text)}</code>")


_EN = "qwertyuiop[]asdfghjkl;'zxcvbnm,./`QWERTYUIOP{}ASDFGHJKL:\"ZXCVBNM<>?~"
_AR = "ضصثقفغعهخحجدشسيبلاتنمكطئءؤرلاىةوزظ" + "ذ" + "ْ" + "ًٌٍَُِّ" 
_MAP_AR = dict(zip("qwertyuiop[]asdfghjkl;'zxcvbnm,./`", "ضصثقفغعهخحجدشسيبلاتنمكطئءؤرلاىةوزظذ"))
_MAP_AR.update({"Q": "َ", "W": "ً", "E": "ُ", "R": "ٌ", "T": "لإ", "Y": "إ", "U": "‘", "I": "÷", "O": "×", "P": "؛"})
_MAP_EN = {v: k for k, v in _MAP_AR.items() if len(v) == 1}
_MAP_EN.update({"ك": ";", "ة": "m"})


@tool("kbfix", "text", "تصحيح لغة لوحة المفاتيح", "كتبت بالإنجليزية وأنت تقصد العربية (أو العكس)؟ أرسل النص وسأصلحه.", opts=[("en2ar", "إنجليزي → عربي"), ("ar2en", "عربي → إنجليزي")], sample="hgsgl uvhr")
def kbfix(c: Ctx):
    m = _MAP_AR if c.opt != "ar2en" else {}
    if c.opt == "ar2en":
        m = {"ض": "q", "ص": "w", "ث": "e", "ق": "r", "ف": "t", "غ": "y", "ع": "u", "ه": "i", "خ": "o", "ح": "p", "ج": "[", "د": "]", "ش": "a", "س": "s", "ي": "d", "ب": "f", "ل": "g", "ا": "h", "ت": "j", "ن": "k", "م": "l", "ك": ";", "ط": "'", "ئ": "z", "ء": "x", "ؤ": "c", "ر": "v", "ى": "n", "ة": "m", "و": ",", "ز": ".", "ظ": "/"}
    return Out.text(f"<code>{''.join(m.get(ch, ch) for ch in c.text)}</code>")


@tool("clean", "text", "تنظيف النص", "أرسل نصاً وسأزيل المسافات والأسطر الزائدة.", opts=[("spaces", "المسافات الزائدة"), ("blank", "الأسطر الفارغة"), ("all", "الكل + الرموز الخفية")], sample="كلمة    كلمة\n\n\nسطر")
def clean(c: Ctx):
    t = c.text
    if c.opt in ("spaces", "all"):
        t = re.sub(r"[ \t]+", " ", t)
        t = "\n".join(l.strip() for l in t.splitlines())
    if c.opt in ("blank", "all"):
        t = "\n".join(l for l in t.splitlines() if l.strip())
    if c.opt == "all":
        t = re.sub(r"[​-‏‪-‮⁠﻿]", "", t)
    return Out.text(f"<code>{t}</code>" if t.strip() else "النتيجة فارغة")


@tool("lines", "text", "ترتيب الأسطر وحذف المكرر", "أرسل قائمة (كل عنصر في سطر).", opts=[("sort", "ترتيب أبجدي"), ("unique", "حذف المكرر"), ("shuffle", "خلط عشوائي"), ("num", "ترقيم")], sample="ب\nأ\nب\nج")
def lines(c: Ctx):
    ls = [l for l in c.text.splitlines() if l.strip()]
    if c.opt == "sort":
        ls.sort()
    elif c.opt == "unique":
        seen = set()
        ls = [l for l in ls if not (l in seen or seen.add(l))]
    elif c.opt == "shuffle":
        random.shuffle(ls)
    elif c.opt == "num":
        ls = [f"{i}. {l}" for i, l in enumerate(ls, 1)]
    return Out.text(f"<code>{chr(10).join(ls)}</code>" if ls else "لا توجد أسطر")


@tool("extract", "text", "استخراج الروابط والإيميلات والأرقام", "أرسل نصاً طويلاً وسأستخرج منه الروابط والإيميلات والأرقام والهاشتاقات.", sample="زر https://a.com أو راسل me@x.org على +9647701234567 #تقنية")
def extract(c: Ctx):
    t = c.text
    links = re.findall(r"https?://[^\s]+", t)
    mails = re.findall(r"[\w.+-]+@[\w-]+\.[\w.-]+", t)
    phones = re.findall(r"\+?\d[\d\s-]{7,}\d", t)
    tags = re.findall(r"#\w+", t, re.U)
    ments = re.findall(r"@\w{4,}", t)

    def sec(name, xs):
        return f"<b>{name}</b> ({len(xs)}):\n" + "\n".join(f"• {x}" for x in xs) + "\n\n" if xs else ""

    out = sec("🔗 الروابط", links) + sec("📧 الإيميلات", mails) + sec("📞 الأرقام", phones) + sec("#️⃣ الهاشتاقات", tags) + sec("👤 المعرّفات", ments)
    return Out.text(out or "لم أجد شيئاً.")


MORSE = {"a": ".-", "b": "-...", "c": "-.-.", "d": "-..", "e": ".", "f": "..-.", "g": "--.", "h": "....", "i": "..", "j": ".---", "k": "-.-", "l": ".-..", "m": "--", "n": "-.", "o": "---", "p": ".--.", "q": "--.-", "r": ".-.", "s": "...", "t": "-", "u": "..-", "v": "...-", "w": ".--", "x": "-..-", "y": "-.--", "z": "--..", "0": "-----", "1": ".----", "2": "..---", "3": "...--", "4": "....-", "5": ".....", "6": "-....", "7": "--...", "8": "---..", "9": "----.", ".": ".-.-.-", ",": "--..--", "?": "..--..", "!": "-.-.--", "/": "-..-.", "@": ".--.-."}
MORSE_AR = dict(zip("ابتثجحخدذرزسشصضطظعغفقكلمنهوي", [".-", "-...", "-", "-.-.", ".---", "....", "---", "-..", "--..", ".-.", "---.", "...", "----", "...-", "-...-", "..-", "-..-", "-.-", "..--", "..-.", "--.-", "-.-", ".-..", "--", "-.", "..-..", ".--", ".."]))
_REV = {v: k for k, v in MORSE.items()}
_REV_AR = {v: k for k, v in MORSE_AR.items()}


@tool("morse", "text", "شيفرة مورس", "أرسل نصاً (عربي/إنجليزي) أو شيفرة مورس (نقاط وشرطات) وسأحوّلها.", sample="sos hello")
def morse(c: Ctx):
    t = c.text.strip()
    if re.fullmatch(r"[.\-/\s]+", t):
        words = t.split(" / ") if " / " in t else re.split(r"\s{2,}|/", t)
        res = " ".join("".join(_REV.get(x, "?") for x in w.split()) for w in words)
        return Out.text(f"<code>{res}</code>")
    tab = MORSE_AR if re.search(r"[؀-ۿ]", t) else MORSE
    res = " / ".join(" ".join(tab.get(ch.lower(), "") for ch in w if tab.get(ch.lower())) for w in t.split())
    return Out.text(f"<code>{res}</code>")


_ONES = ["", "واحد", "اثنان", "ثلاثة", "أربعة", "خمسة", "ستة", "سبعة", "ثمانية", "تسعة", "عشرة", "أحد عشر", "اثنا عشر", "ثلاثة عشر", "أربعة عشر", "خمسة عشر", "ستة عشر", "سبعة عشر", "ثمانية عشر", "تسعة عشر"]
_TENS = ["", "", "عشرون", "ثلاثون", "أربعون", "خمسون", "ستون", "سبعون", "ثمانون", "تسعون"]
_HUND = ["", "مئة", "مئتان", "ثلاثمئة", "أربعمئة", "خمسمئة", "ستمئة", "سبعمئة", "ثمانمئة", "تسعمئة"]


def _below1000(n: int) -> str:
    parts = []
    h, r = divmod(n, 100)
    if h:
        parts.append(_HUND[h])
    if r:
        if r < 20:
            parts.append(_ONES[r])
        else:
            t, o = divmod(r, 10)
            parts.append((_ONES[o] + " و" if o else "") + _TENS[t])
    return " و".join(parts)


def num_words(n: int) -> str:
    if n == 0:
        return "صفر"
    neg = n < 0
    n = abs(n)
    scales = [(10**9, "مليار", "ملياران", "مليارات"), (10**6, "مليون", "مليونان", "ملايين"), (1000, "ألف", "ألفان", "آلاف")]
    parts = []
    for val, one, two, many in scales:
        q, n = divmod(n, val)
        if not q:
            continue
        if q == 1:
            parts.append(one)
        elif q == 2:
            parts.append(two)
        elif q <= 10:
            parts.append(f"{_below1000(q)} {many}")
        else:
            parts.append(f"{_below1000(q)} {one}")
    if n:
        parts.append(_below1000(n))
    return ("سالب " if neg else "") + " و".join(parts)


@tool("num_words", "text", "رقم إلى كلمات (تفقيط)", "أرسل رقماً وسأكتبه بالحروف العربية (مفيد للشيكات والعقود).", sample="1234567")
def num_words_tool(c: Ctx):
    t = c.text.strip().replace(",", "").replace("،", "").translate(str.maketrans("٠١٢٣٤٥٦٧٨٩", "0123456789"))
    if not re.fullmatch(r"-?\d{1,15}", t):
        raise ToolError("أرسل رقماً صحيحاً (حتى 15 خانة).")
    return Out.text(f"🔢 <b>{int(t):,}</b>\n\n<code>{num_words(int(t))}</code>")


_ROM = [(1000, "M"), (900, "CM"), (500, "D"), (400, "CD"), (100, "C"), (90, "XC"), (50, "L"), (40, "XL"), (10, "X"), (9, "IX"), (5, "V"), (4, "IV"), (1, "I")]


@tool("roman", "text", "الأرقام الرومانية", "أرسل رقماً (1-3999) أو رقماً رومانياً وسأحوّله.", sample="2024")
def roman(c: Ctx):
    t = c.text.strip().upper()
    if t.isdigit():
        n = int(t)
        if not 1 <= n <= 3999:
            raise ToolError("الرقم بين 1 و3999.")
        r = ""
        for v, s in _ROM:
            while n >= v:
                r += s
                n -= v
        return Out.text(f"<code>{r}</code>")
    if re.fullmatch(r"[IVXLCDM]+", t):
        vals = {"I": 1, "V": 5, "X": 10, "L": 50, "C": 100, "D": 500, "M": 1000}
        tot = 0
        for i, ch in enumerate(t):
            tot += -vals[ch] if i + 1 < len(t) and vals[ch] < vals[t[i + 1]] else vals[ch]
        return Out.text(f"<code>{tot}</code>")
    raise ToolError("أرسل رقماً أو رقماً رومانياً صحيحاً.")


def render_text_image(text: str, bg: str = "dark", size: int = 64) -> bytes:
    font = ImageFont.truetype(str(FONTS / "Cairo-b.ttf"), size)
    themes = {"dark": ("#101820", "#ffffff"), "light": ("#ffffff", "#111111"), "gold": ("#1a1405", "#f5c542"), "blue": ("#0b3d91", "#ffffff"), "pink": ("#ffdde1", "#c2185b")}
    bgc, fg = themes.get(bg, themes["dark"])
    W = 1080
    lines = []
    for para in text.splitlines() or [text]:
        cur = ""
        for w in para.split():
            test = (cur + " " + w).strip()
            if font.getlength(shape(test), **draw_kw()) > W - 160 and cur:
                lines.append(cur)
                cur = w
            else:
                cur = test
        lines.append(cur)
    lh = int(size * 1.7)
    H = max(540, lh * len(lines) + 200)
    img = Image.new("RGB", (W, H), bgc)
    d = ImageDraw.Draw(img)
    y = (H - lh * len(lines)) // 2
    for l in lines:
        d.text((W // 2, y), shape(l), font=font, fill=fg, anchor="ma", **draw_kw())
        y += lh
    buf = io.BytesIO()
    img.save(buf, "PNG")
    return buf.getvalue()


@tool("txt2img", "text", "نص إلى صورة", "أرسل النص (عربي أو إنجليزي) وسأصنع منه صورة جميلة.", opts=[("dark", "داكنة"), ("light", "فاتحة"), ("gold", "ذهبية"), ("blue", "زرقاء"), ("pink", "وردية")], sample="ما أجمل أن تبدأ يومك بابتسامة")
def txt2img(c: Ctx):
    if len(c.text) > 600:
        raise ToolError("النص طويل (الحد 600 حرف).")
    return Out.photo(render_text_image(c.text, c.opt or "dark"))

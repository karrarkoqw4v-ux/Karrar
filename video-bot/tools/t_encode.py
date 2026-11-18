import base64
import binascii
import hashlib
import json
import math
import re
import secrets
import string
import urllib.parse
import uuid
from datetime import datetime, timezone

from .registry import Ctx, Out, ToolError, tool
from .t_calc import norm


@tool("b64", "encode", "Base64 تشفير وفك", "أرسل النص. اختر ترميز أو فك.", opts=[("enc", "تشفير Base64"), ("dec", "فك Base64")], sample="مرحبا")
def b64(c: Ctx):
    if c.opt == "dec":
        try:
            s = c.text.strip()
            r = base64.b64decode(s + "=" * (-len(s) % 4), validate=False).decode("utf-8")
        except Exception:
            raise ToolError("نص Base64 غير صالح.")
        return Out.text(f"<code>{r}</code>")
    return Out.text(f"<code>{base64.b64encode(c.text.encode()).decode()}</code>")


@tool("urlenc", "encode", "ترميز الروابط URL", "أرسل نصاً أو رابطاً.", opts=[("enc", "ترميز"), ("dec", "فك الترميز")], sample="مرحبا بك/test")
def urlenc(c: Ctx):
    r = urllib.parse.unquote(c.text) if c.opt == "dec" else urllib.parse.quote(c.text, safe="")
    return Out.text(f"<code>{r}</code>")


@tool("hashes", "encode", "حساب البصمة (MD5/SHA)", "أرسل نصاً وسأعطيك MD5 وSHA1 وSHA256 وSHA512.", sample="hello")
def hashes(c: Ctx):
    b = c.text.encode()
    return Out.text("🔏 <b>البصمات</b>\n" + "\n".join(f"{n}:\n<code>{hashlib.new(a, b).hexdigest()}</code>" for n, a in (("MD5", "md5"), ("SHA1", "sha1"), ("SHA256", "sha256"), ("SHA512", "sha512"))))


@tool("filehash", "encode", "بصمة ملف (تحقق من سلامته)", "أرسل أي ملف وسأحسب MD5 وSHA256.", inp="file")
def filehash(c: Ctx):
    h = {a: hashlib.new(a) for a in ("md5", "sha1", "sha256")}
    n = 0
    with open(c.path, "rb") as f:
        while ch := f.read(1 << 20):
            n += len(ch)
            for x in h.values():
                x.update(ch)
    return Out.text(f"📁 {c.name or 'ملف'}\nالحجم: {n:,} بايت\n" + "\n".join(f"{k.upper()}:\n<code>{v.hexdigest()}</code>" for k, v in h.items()))


@tool("base", "encode", "تحويل الأنظمة العددية", "أرسل رقماً (عشري، 0b…، 0x…، 0o…) وسأعرضه بكل الأنظمة.", sample="255")
def base(c: Ctx):
    t = norm(c.text.strip().lower())
    try:
        n = int(t, 0) if t.startswith(("0b", "0x", "0o")) else int(t)
    except ValueError:
        raise ToolError("أرسل رقماً صحيحاً.")
    return Out.text(f"🔢 عشري: <code>{n}</code>\nثنائي: <code>{bin(n)}</code>\nثماني: <code>{oct(n)}</code>\nسداسي عشر: <code>{hex(n)}</code>")


@tool("bin_text", "encode", "نص ↔ ثنائي / سداسي عشر", "أرسل نصاً أو أرقاماً ثنائية.", opts=[("t2b", "نص ← ثنائي"), ("b2t", "ثنائي ← نص"), ("t2h", "نص ← Hex"), ("h2t", "Hex ← نص")], sample="Hi")
def bin_text(c: Ctx):
    o = c.opt or "t2b"
    try:
        if o == "t2b":
            r = " ".join(f"{b:08b}" for b in c.text.encode())
        elif o == "b2t":
            r = bytes(int(x, 2) for x in re.findall(r"[01]{8}", c.text)).decode()
        elif o == "t2h":
            r = c.text.encode().hex(" ")
        else:
            r = bytes.fromhex(re.sub(r"[^0-9a-fA-F]", "", c.text)).decode()
    except Exception:
        raise ToolError("مدخل غير صالح.")
    return Out.text(f"<code>{r}</code>")


@tool("json", "encode", "تنسيق JSON والتحقق منه", "أرسل JSON وسأنسّقه أو أخبرك بمكان الخطأ.", sample='{"a":1,"b":[1,2]}')
def json_fmt(c: Ctx):
    try:
        d = json.loads(c.text)
    except json.JSONDecodeError as e:
        raise ToolError(f"JSON غير صالح: {e.msg} (سطر {e.lineno}، عمود {e.colno})")
    return Out.text(f"✅ JSON صالح\n<pre>{json.dumps(d, ensure_ascii=False, indent=2)[:3500]}</pre>")


@tool("uuid", "encode", "مولّد UUID", "سأولّد 5 معرّفات UUID.", inp="none", sample="x")
def uuid_gen(c: Ctx):
    return Out.text("\n".join(f"<code>{uuid.uuid4()}</code>" for _ in range(5)))


@tool("passgen", "encode", "مولّد كلمات سر قوية", "أرسل الطول (8–64) أو اتركه.", opts=[("strong", "قوية (أحرف+أرقام+رموز)"), ("alnum", "أحرف وأرقام"), ("digits", "أرقام فقط"), ("words", "كلمات سهلة الحفظ")], sample="16")
def passgen(c: Ctx):
    m = re.search(r"\d+", norm(c.text))
    n = min(64, max(6, int(m.group()))) if m else 16
    o = c.opt or "strong"
    if o == "words":
        words = ["قمر", "نجم", "بحر", "ذهب", "نسر", "ورد", "ليل", "صقر", "نهر", "جبل", "سحاب", "برق", "رعد", "فجر", "مطر", "ريح"]
        res = ["-".join(secrets.choice(words) for _ in range(4)) + "-" + str(secrets.randbelow(900) + 100) for _ in range(5)]
    else:
        alpha = {"strong": string.ascii_letters + string.digits + "!@#$%^&*()-_=+?", "alnum": string.ascii_letters + string.digits, "digits": string.digits}[o]
        res = ["".join(secrets.choice(alpha) for _ in range(n)) for _ in range(5)]
    return Out.text("🔑 <b>كلمات سر عشوائية</b> (لا تُخزَّن)\n\n" + "\n".join(f"<code>{x}</code>" for x in res))


@tool("passcheck", "encode", "فحص قوة كلمة السر", "أرسل كلمة السر (لا تُخزَّن ولا تُرسل لأي جهة).", sample="Abc123!xyz")
def passcheck(c: Ctx):
    p = c.text.strip()
    if not p:
        raise ToolError("أرسل كلمة السر.")
    pool = 0
    pool += 26 if re.search(r"[a-z]", p) else 0
    pool += 26 if re.search(r"[A-Z]", p) else 0
    pool += 10 if re.search(r"\d", p) else 0
    pool += 33 if re.search(r"[^\w\s]", p) else 0
    pool += 40 if re.search(r"[؀-ۿ]", p) else 0
    ent = len(p) * math.log2(max(pool, 2))
    common = p.lower() in {"123456", "password", "12345678", "qwerty", "123456789", "111111", "iloveyou", "admin", "000000", "123123"} or re.fullmatch(r"(.)\1+", p) is not None
    lvl = "ضعيفة جداً 🔴" if ent < 28 or common else ("ضعيفة 🟠" if ent < 36 else ("متوسطة 🟡" if ent < 60 else ("قوية 🟢" if ent < 80 else "ممتازة 💪")))
    tips = []
    if len(p) < 12:
        tips.append("زد الطول إلى 12+")
    if not re.search(r"[^\w\s]", p):
        tips.append("أضف رموزاً")
    if not re.search(r"\d", p):
        tips.append("أضف أرقاماً")
    return Out.text(f"🔐 القوة: <b>{lvl}</b>\nالإنتروبيا: {ent:.0f} بت\n⏳ تخمين بالقوة الغاشمة (10 مليار/ثانية): ~{_crack(ent)}" + ("\n💡 " + "، ".join(tips) if tips else ""))


def _crack(ent: float) -> str:
    sec = 2 ** ent / 1e10 / 2
    for lim, name in ((31557600 * 1e9, "مليار سنة"), (31557600 * 1e6, "مليون سنة"), (31557600 * 1000, "ألف سنة"), (31557600, "سنة"), (86400, "يوم"), (3600, "ساعة"), (60, "دقيقة")):
        if sec >= lim:
            return f"{sec / lim:,.0f} {name}"
    return "أقل من دقيقة"


@tool("timestamp", "encode", "تحويل الطابع الزمني Unix", "أرسل رقم Unix (10 أو 13 خانة) أو تاريخاً YYYY-MM-DD HH:MM.", sample="1700000000")
def timestamp(c: Ctx):
    t = norm(c.text.strip())
    if re.fullmatch(r"\d{9,13}", t):
        n = int(t) / (1000 if len(t) >= 12 else 1)
        d = datetime.fromtimestamp(n, timezone.utc)
        return Out.text(f"🕒 UTC: <b>{d:%Y-%m-%d %H:%M:%S}</b>")
    for fmt in ("%Y-%m-%d %H:%M", "%Y-%m-%d"):
        try:
            d = datetime.strptime(t, fmt).replace(tzinfo=timezone.utc)
            return Out.text(f"🔢 Unix: <code>{int(d.timestamp())}</code>\nبالمللي ثانية: <code>{int(d.timestamp() * 1000)}</code>")
        except ValueError:
            pass
    raise ToolError("صيغة غير مفهومة.")


@tool("caesar", "encode", "شيفرة قيصر ROT", "أرسل نصاً إنجليزياً. اختر الإزاحة.", opts=[("3", "إزاحة 3"), ("13", "ROT13"), ("5", "إزاحة 5"), ("7", "إزاحة 7"), ("-3", "فك إزاحة 3")], sample="Hello World")
def caesar(c: Ctx):
    k = int(c.opt or 3)
    out = ""
    for ch in c.text:
        if ch.isascii() and ch.isalpha():
            b = ord("a") if ch.islower() else ord("A")
            out += chr((ord(ch) - b + k) % 26 + b)
        else:
            out += ch
    return Out.text(f"<code>{out}</code>")

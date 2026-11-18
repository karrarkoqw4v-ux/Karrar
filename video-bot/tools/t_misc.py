import colorsys
import math
import re
import unicodedata

import phonenumbers
from phonenumbers import carrier, geocoder, timezone

from .registry import Ctx, Out, ToolError, tool
from .t_calc import norm


@tool("phone", "misc", "معلومات رقم هاتف", "أرسل الرقم بصيغة دولية: +9647701234567 (لا أتصل بأحد، التحليل محلي).", sample="+9647701234567")
def phone(c: Ctx):
    t = norm(c.text.strip())
    try:
        n = phonenumbers.parse(t if t.startswith("+") else "+" + t.lstrip("0"), None)
    except Exception:
        raise ToolError("أرسل الرقم مع رمز الدولة مثل +9647701234567")
    if not phonenumbers.is_possible_number(n):
        raise ToolError("الرقم غير صالح.")
    types = {0: "ثابت", 1: "جوال", 2: "ثابت/جوال", 3: "مجاني", 4: "مدفوع", 5: "VOIP"}
    return Out.text(
        f"📞 <b>{phonenumbers.format_number(n, phonenumbers.PhoneNumberFormat.INTERNATIONAL)}</b>\n"
        f"✅ صالح: {'نعم' if phonenumbers.is_valid_number(n) else 'قد يكون غير صالح'}\n"
        f"🌍 الدولة/المنطقة: {geocoder.description_for_number(n, 'ar') or geocoder.description_for_number(n, 'en') or '—'}\n"
        f"📡 الشركة: {carrier.name_for_number(n, 'en') or '—'}\n"
        f"📱 النوع: {types.get(phonenumbers.number_type(n), 'غير معروف')}\n"
        f"🕒 المنطقة الزمنية: {', '.join(timezone.time_zones_for_number(n)[:2]) or '—'}\n"
        f"🔗 واتساب: https://wa.me/{n.country_code}{n.national_number}")


@tool("wame", "misc", "رابط واتساب مباشر", "أرسل الرقم (مع رمز الدولة) وأعطيك رابط محادثة بدون حفظ الرقم، مع رسالة جاهزة اختيارية.", sample="9647701234567 مرحبا")
def wame(c: Ctx):
    t = norm(c.text.strip())
    m = re.match(r"^\+?(\d{8,15})\s*(.*)$", t, re.S)
    if not m:
        raise ToolError("أرسل الرقم مع رمز الدولة، ثم الرسالة (اختياري).")
    import urllib.parse
    msg = f"?text={urllib.parse.quote(m.group(2))}" if m.group(2) else ""
    return Out.text(f"💬 <code>https://wa.me/{m.group(1)}{msg}</code>")


@tool("luhn", "misc", "فحص رقم بطاقة (Luhn)", "أرسل رقم البطاقة لفحص صحة الصيغة فقط (لا يتحقق من وجودها ولا رصيدها، ولا يُخزَّن).", sample="4539578763621486")
def luhn(c: Ctx):
    d = re.sub(r"\D", "", norm(c.text))
    if not 12 <= len(d) <= 19:
        raise ToolError("طول غير صحيح.")
    s = 0
    for i, ch in enumerate(reversed(d)):
        n = int(ch) * (2 if i % 2 else 1)
        s += n - 9 if n > 9 else n
    brand = "Visa" if d[0] == "4" else ("Mastercard" if d[:2] in {"51", "52", "53", "54", "55"} or 2221 <= int(d[:4]) <= 2720 else ("American Express" if d[:2] in ("34", "37") else ("Discover" if d[:2] == "65" or d[:4] == "6011" else "غير معروفة")))
    return Out.text(f"💳 الصيغة: <b>{'صحيحة ✅' if s % 10 == 0 else 'غير صحيحة ❌'}</b>\nالنوع: {brand}\nالطول: {len(d)}")


@tool("color", "misc", "محوّل الألوان", "أرسل لوناً: #FF5733 أو rgb(255,87,51) أو 255 87 51.", sample="#FF5733")
def color(c: Ctx):
    t = c.text.strip()
    m = re.fullmatch(r"#?([0-9a-fA-F]{6})", t)
    if m:
        h = m.group(1)
        r, g, b = int(h[:2], 16), int(h[2:4], 16), int(h[4:], 16)
    else:
        xs = re.findall(r"\d+", t)
        if len(xs) != 3 or any(int(x) > 255 for x in xs):
            raise ToolError("لون غير صالح.")
        r, g, b = map(int, xs)
    hh, ll, ss = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
    h2, s2, v2 = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    from PIL import Image
    import io
    buf = io.BytesIO()
    Image.new("RGB", (400, 200), (r, g, b)).save(buf, "PNG")
    return Out.photo(buf.getvalue(), f"🎨 HEX: <code>#{r:02X}{g:02X}{b:02X}</code>\nRGB: <code>{r}, {g}, {b}</code>\nHSL: <code>{hh * 360:.0f}°, {ss * 100:.0f}%, {ll * 100:.0f}%</code>\nHSV: <code>{h2 * 360:.0f}°, {s2 * 100:.0f}%, {v2 * 100:.0f}%</code>")


@tool("zakat", "misc", "حاسبة الزكاة", "أرسل: إجمالي مالك المدّخر ، سعر غرام الذهب (بنفس العملة). مثال: 12000000 95000", sample="12000000 95000")
def zakat(c: Ctx):
    xs = [float(x) for x in re.findall(r"\d+(?:\.\d+)?", norm(c.text).replace(",", ""))]
    if len(xs) < 2:
        raise ToolError("أرسل المبلغ ثم سعر غرام الذهب.")
    money, gold = xs[0], xs[1]
    nisab = 85 * gold
    if money < nisab:
        return Out.text(f"النصاب (85 غرام ذهب) = <b>{nisab:,.0f}</b>\nمالك أقل من النصاب فلا زكاة عليك ما لم تكن لديك أموال أخرى.\n\n<i>للاستئناس؛ راجع جهة الإفتاء.</i>")
    return Out.text(f"💰 النصاب: {nisab:,.0f}\nزكاة مالك (2.5%): <b>{money * 0.025:,.2f}</b>\n\n<i>للاستئناس؛ راجع جهة الإفتاء لحالتك.</i>")


@tool("qibla", "misc", "اتجاه القبلة", "أرسل موقعك (📎 ← الموقع) وأحسب لك زاوية القبلة. أو اكتب: خط_العرض خط_الطول.", inp="location", sample="33.31 44.36")
def qibla(c: Ctx):
    xs = re.findall(r"-?\d+(?:\.\d+)?", norm(c.text))
    if len(xs) < 2:
        raise ToolError("أرسل موقعك أو إحداثياتك.")
    lat, lon = map(float, xs[:2])
    k_lat, k_lon = math.radians(21.4225), math.radians(39.8262)
    p1, l1 = math.radians(lat), math.radians(lon)
    dl = k_lon - l1
    y = math.sin(dl) * math.cos(k_lat)
    x = math.cos(p1) * math.sin(k_lat) - math.sin(p1) * math.cos(k_lat) * math.cos(dl)
    bearing = (math.degrees(math.atan2(y, x)) + 360) % 360
    a = math.sin((k_lat - p1) / 2) ** 2 + math.cos(p1) * math.cos(k_lat) * math.sin(dl / 2) ** 2
    dist = 6371 * 2 * math.asin(math.sqrt(a))
    dirs = ["شمال", "شمال شرق", "شرق", "جنوب شرق", "جنوب", "جنوب غرب", "غرب", "شمال غرب"]
    return Out.text(f"🕋 اتجاه القبلة: <b>{bearing:.1f}°</b> من الشمال (باتجاه {dirs[int((bearing + 22.5) % 360 // 45)]})\n📏 المسافة إلى الكعبة: {dist:,.0f} كم\n\n<i>وجّه بوصلة هاتفك على هذه الزاوية.</i>")


@tool("unicode", "misc", "فاحص الرموز والأحرف", "أرسل نصاً وسأعرض كل حرف بكوده واسمه (يكشف الأحرف المخفية).", sample="aب​😀")
def uni(c: Ctx):
    rows = []
    for ch in c.text[:40]:
        name = unicodedata.name(ch, "غير معروف")
        rows.append(f"<code>{ch if ch.isprintable() and not ch.isspace() else '·'}</code> U+{ord(ch):04X} — {name.title()}")
    return Out.text("🔬 <b>الأحرف</b>\n" + "\n".join(rows))


@tool("link_gen", "misc", "مولّد روابط تيليجرام", "أرسل المعرّف (@user) أو الآيدي أو رقم الهاتف.", sample="@durov")
def link_gen(c: Ctx):
    t = c.text.strip()
    if t.startswith("@") or re.fullmatch(r"[A-Za-z][\w]{4,31}", t):
        u = t.lstrip("@")
        return Out.text(f"🔗 الحساب: <code>https://t.me/{u}</code>\nرابط للبدء مع بوت: <code>https://t.me/{u}?start=1</code>\nمشاركة: <code>https://t.me/share/url?url=https://t.me/{u}</code>")
    if re.fullmatch(r"\d{5,15}", norm(t)):
        return Out.text(f"🔗 <code>tg://user?id={norm(t)}</code> (يعمل في تطبيق تيليجرام فقط)")
    raise ToolError("أرسل @معرّف أو آيدي رقمياً.")

import ast
import math
import operator as op
import random
import re
from datetime import date, datetime, timedelta
from zoneinfo import ZoneInfo

from hijridate import Gregorian, Hijri

from .registry import Ctx, Out, ToolError, tool

_AR_DIG = str.maketrans("٠١٢٣٤٥٦٧٨٩۰۱۲۳۴۵۶۷۸۹", "01234567890123456789")


def norm(s: str) -> str:
    return s.translate(_AR_DIG).replace("٫", ".").replace("،", ",").replace("×", "*").replace("÷", "/").replace("^", "**")


# ---------- آلة حاسبة آمنة ----------
_OPS = {ast.Add: op.add, ast.Sub: op.sub, ast.Mult: op.mul, ast.Div: op.truediv, ast.Pow: op.pow, ast.Mod: op.mod, ast.FloorDiv: op.floordiv, ast.USub: op.neg, ast.UAdd: op.pos}
_FUN = {"sqrt": math.sqrt, "sin": lambda x: math.sin(math.radians(x)), "cos": lambda x: math.cos(math.radians(x)), "tan": lambda x: math.tan(math.radians(x)),
        "log": math.log10, "ln": math.log, "abs": abs, "round": round, "floor": math.floor, "ceil": math.ceil, "fact": math.factorial, "exp": math.exp}
_CONST = {"pi": math.pi, "e": math.e}


def safe_eval(expr: str):
    def ev(n):
        if isinstance(n, ast.Constant) and isinstance(n.value, (int, float)):
            return n.value
        if isinstance(n, ast.BinOp) and type(n.op) in _OPS:
            a, b = ev(n.left), ev(n.right)
            if isinstance(n.op, ast.Pow) and abs(b) > 1000:
                raise ToolError("الأس كبير جداً.")
            return _OPS[type(n.op)](a, b)
        if isinstance(n, ast.UnaryOp) and type(n.op) in _OPS:
            return _OPS[type(n.op)](ev(n.operand))
        if isinstance(n, ast.Call) and isinstance(n.func, ast.Name) and n.func.id in _FUN and len(n.args) == 1:
            return _FUN[n.func.id](ev(n.args[0]))
        if isinstance(n, ast.Name) and n.id in _CONST:
            return _CONST[n.id]
        raise ToolError("تعبير غير مدعوم.")
    return ev(ast.parse(expr, mode="eval").body)


@tool("calc", "calc", "آلة حاسبة متقدمة", "اكتب العملية: 2+3*4 ، sqrt(144) ، 15% ، 2^10 ، sin(30) ، fact(5)", sample="(12+8)*3/4")
def calc(c: Ctx):
    e = norm(c.text.strip())
    e = re.sub(r"(\d+(?:\.\d+)?)\s*%", r"(\1/100)", e)
    if not e or len(e) > 200:
        raise ToolError("اكتب عملية حسابية.")
    try:
        r = safe_eval(e)
    except ToolError:
        raise
    except ZeroDivisionError:
        raise ToolError("لا يمكن القسمة على صفر.")
    except Exception:
        raise ToolError("لم أفهم العملية. مثال: (5+3)*2")
    if isinstance(r, float) and r.is_integer() and abs(r) < 1e15:
        r = int(r)
    elif isinstance(r, float):
        r = round(r, 10)
    return Out.text(f"🧮 <code>{c.text.strip()}</code>\n\n= <b>{r:,}</b>" if isinstance(r, int) else f"🧮 <code>{c.text.strip()}</code>\n\n= <b>{r}</b>")


# ---------- محوّل الوحدات ----------
UNITS = {
    "length": {"mm": 0.001, "cm": 0.01, "m": 1, "km": 1000, "in": 0.0254, "inch": 0.0254, "ft": 0.3048, "yd": 0.9144, "mile": 1609.344, "mi": 1609.344, "nm": 1852},
    "mass": {"mg": 1e-6, "g": 0.001, "kg": 1, "t": 1000, "ton": 1000, "lb": 0.45359237, "oz": 0.028349523, "stone": 6.35029},
    "volume": {"ml": 0.001, "l": 1, "liter": 1, "m3": 1000, "gal": 3.78541, "cup": 0.24, "tbsp": 0.01479, "tsp": 0.00493, "floz": 0.0295735},
    "data": {"b": 1, "kb": 1024, "mb": 1024**2, "gb": 1024**3, "tb": 1024**4, "pb": 1024**5, "bit": 0.125},
    "speed": {"m/s": 1, "km/h": 1 / 3.6, "kmh": 1 / 3.6, "mph": 0.44704, "knot": 0.514444, "kn": 0.514444},
    "area": {"m2": 1, "km2": 1e6, "ha": 1e4, "acre": 4046.8564224, "ft2": 0.09290304, "mile2": 2589988.110336, "dunum": 1000, "دونم": 1000},
    "time": {"s": 1, "sec": 1, "min": 60, "h": 3600, "hr": 3600, "day": 86400, "week": 604800, "month": 2629800, "year": 31557600},
}
AR_UNITS = {"ملم": "mm", "سم": "cm", "متر": "m", "م": "m", "كم": "km", "بوصة": "in", "قدم": "ft", "ميل": "mi", "جرام": "g", "غرام": "g", "كجم": "kg", "كغ": "kg", "طن": "t", "رطل": "lb", "لتر": "l", "مل": "ml", "جالون": "gal", "ثانية": "s", "دقيقة": "min", "ساعة": "h", "يوم": "day", "اسبوع": "week", "شهر": "month", "سنة": "year", "كيلو": "kg", "دونم": "dunum", "هكتار": "ha"}


@tool("units", "calc", "محوّل الوحدات", "اكتب مثل: 10 km to mile ، 70 kg to lb ، 100 c to f ، 5 gb to mb ، 50 كم الى ميل", sample="10 km to mile")
def units(c: Ctx):
    t = norm(c.text.lower().strip())
    m = re.match(r"^(-?\d+(?:\.\d+)?)\s*([^\s\d]+(?:/[a-z]+)?)\s*(?:to|in|->|الى|إلى|ل|=)\s*([^\s\d]+(?:/[a-z]+)?)$", t)
    if not m:
        raise ToolError("الصيغة: 10 km to mile")
    v, a, b = float(m.group(1)), m.group(2), m.group(3)
    a, b = AR_UNITS.get(a, a), AR_UNITS.get(b, b)
    temps = {"c", "f", "k"}
    if a in temps and b in temps:
        k = v if a == "k" else (v + 273.15 if a == "c" else (v - 32) * 5 / 9 + 273.15)
        r = k if b == "k" else (k - 273.15 if b == "c" else (k - 273.15) * 9 / 5 + 32)
        return Out.text(f"🌡 {v:g}°{a.upper()} = <b>{r:.2f}°{b.upper()}</b>")
    for cat, tab in UNITS.items():
        if a in tab and b in tab:
            r = v * tab[a] / tab[b]
            return Out.text(f"📐 {v:g} {a} = <b>{r:,.6g}</b> {b}")
    raise ToolError("وحدات غير معروفة. الأمثلة: km, mile, kg, lb, l, gal, gb, mb, c, f, km/h, mph")


# ---------- التواريخ ----------
def parse_date(s: str) -> date:
    s = norm(s.strip())
    for fmt in ("%Y-%m-%d", "%Y/%m/%d", "%d/%m/%Y", "%d-%m-%Y", "%d.%m.%Y", "%Y.%m.%d"):
        try:
            return datetime.strptime(s, fmt).date()
        except ValueError:
            pass
    raise ToolError("صيغة التاريخ غير مفهومة. اكتب مثل: 1995-03-24 أو 24/03/1995")


ZODIAC = [(1, 20, "الجدي ♑"), (2, 19, "الدلو ♒"), (3, 20, "الحوت ♓"), (4, 20, "الحمل ♈"), (5, 21, "الثور ♉"), (6, 21, "الجوزاء ♊"), (7, 22, "السرطان ♋"), (8, 23, "الأسد ♌"), (9, 23, "العذراء ♍"), (10, 23, "الميزان ♎"), (11, 22, "العقرب ♏"), (12, 22, "القوس ♐"), (12, 31, "الجدي ♑")]
DAYS_AR = ["الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة", "السبت", "الأحد"]


def _zodiac(d: date) -> str:
    for m, dd, name in ZODIAC:
        if (d.month, d.day) <= (m, dd):
            return name
    return "الجدي ♑"


@tool("age", "calc", "حاسبة العمر الدقيقة", "أرسل تاريخ ميلادك: 1995-03-24 أو 24/03/1995", sample="1995-03-24")
def age(c: Ctx):
    b = parse_date(c.text)
    t = date.today()
    if b > t:
        raise ToolError("تاريخ الميلاد في المستقبل!")
    y, m, d = t.year - b.year, t.month - b.month, t.day - b.day
    if d < 0:
        m -= 1
        pm = t.replace(day=1) - timedelta(days=1)
        d += pm.day
    if m < 0:
        y -= 1
        m += 12
    try:
        nb = date(t.year, b.month, b.day)
    except ValueError:
        nb = date(t.year, 3, 1)
    if nb < t:
        nb = date(t.year + 1, b.month, b.day if not (b.month == 2 and b.day == 29) else 28)
    days = (t - b).days
    hij = Gregorian(b.year, b.month, b.day).to_hijri()
    return Out.text(
        f"🎂 <b>عمرك</b>: {y} سنة و{m} شهر و{d} يوم\n"
        f"📆 إجمالي الأيام: <b>{days:,}</b> • الأسابيع: {days // 7:,} • الأشهر: {y * 12 + m}\n"
        f"⏱ الساعات: {days * 24:,} • الدقائق: {days * 1440:,}\n"
        f"🎁 عيد ميلادك القادم بعد <b>{(nb - t).days}</b> يوم (يوم {DAYS_AR[nb.weekday()]})\n"
        f"🗓 وُلدت يوم <b>{DAYS_AR[b.weekday()]}</b> • هجرياً: {hij.day}/{hij.month}/{hij.year}\n"
        f"🔮 برجك: {_zodiac(b)}")


@tool("datediff", "calc", "الفرق بين تاريخين", "أرسل تاريخين في سطرين أو مفصولين بمسافة: 2024-01-01 2024-12-31", sample="2024-01-01 2024-12-31")
def datediff(c: Ctx):
    parts = re.findall(r"\d{1,4}[-/.]\d{1,2}[-/.]\d{1,4}", norm(c.text))
    if len(parts) != 2:
        raise ToolError("أرسل تاريخين.")
    a, b = parse_date(parts[0]), parse_date(parts[1])
    if a > b:
        a, b = b, a
    days = (b - a).days
    y = b.year - a.year - ((b.month, b.day) < (a.month, a.day))
    mo = (b.month - a.month - (b.day < a.day)) % 12
    return Out.text(f"📆 الفرق: <b>{days:,}</b> يوم\n≈ {y} سنة و{mo} شهر\n≈ {days // 7:,} أسبوع و{days % 7} يوم\n⏱ {days * 24:,} ساعة")


@tool("countdown", "calc", "العدّ التنازلي لتاريخ", "أرسل تاريخ الحدث وسأحسب كم تبقّى (أو مضى).", sample="2030-01-01")
def countdown(c: Ctx):
    d = parse_date(c.text)
    delta = (d - date.today()).days
    if delta == 0:
        return Out.text("🎯 هو اليوم!")
    wk = DAYS_AR[d.weekday()]
    if delta > 0:
        return Out.text(f"⏳ تبقّى <b>{delta:,}</b> يوم ({delta // 7} أسبوع و{delta % 7} يوم) — يوم {wk}")
    return Out.text(f"⌛ مضى <b>{-delta:,}</b> يوم على هذا التاريخ — كان يوم {wk}")


HIJRI_MONTHS = ["محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة", "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"]
G_MONTHS = ["يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"]


@tool("hijri", "calc", "تحويل التاريخ هجري ↔ ميلادي", "أرسل التاريخ. اختر الاتجاه أولاً.", opts=[("g2h", "ميلادي ← هجري"), ("h2g", "هجري ← ميلادي"), ("today", "تاريخ اليوم")], sample="2024-03-11", auto=("today",))
def hijri(c: Ctx):
    if c.opt == "today" or not c.text.strip():
        g = date.today()
        h = Gregorian(g.year, g.month, g.day).to_hijri()
        return Out.text(f"📅 اليوم {DAYS_AR[g.weekday()]}\nميلادي: <b>{g.day} {G_MONTHS[g.month - 1]} {g.year}</b>\nهجري: <b>{h.day} {HIJRI_MONTHS[h.month - 1]} {h.year}</b>")
    nums = re.findall(r"\d+", norm(c.text))
    if len(nums) != 3:
        raise ToolError("اكتب التاريخ بالصيغة: يوم/شهر/سنة أو سنة-شهر-يوم")
    a, b, cc = map(int, nums)
    if a > 31:
        y, m, d = a, b, cc
    else:
        d, m, y = a, b, cc
    try:
        if c.opt == "h2g":
            g = Hijri(y, m, d).to_gregorian()
            return Out.text(f"📅 {d} {HIJRI_MONTHS[m - 1]} {y} هـ\n= <b>{g.day} {G_MONTHS[g.month - 1]} {g.year} م</b> ({DAYS_AR[g.weekday()]})")
        h = Gregorian(y, m, d).to_hijri()
        return Out.text(f"📅 {d} {G_MONTHS[m - 1]} {y} م\n= <b>{h.day} {HIJRI_MONTHS[h.month - 1]} {h.year} هـ</b>")
    except Exception:
        raise ToolError("تاريخ غير صالح.")


def _nums(t: str, n: int) -> list[float]:
    xs = [float(x) for x in re.findall(r"-?\d+(?:\.\d+)?", norm(t).replace(",", ""))]
    if len(xs) < n:
        raise ToolError(f"أرسل {n} أرقام.")
    return xs


@tool("percent", "calc", "حاسبة النسبة المئوية", "أرسل رقمين: (نسبة ورقم) أو (رقمين)", opts=[("of", "كم يساوي X% من Y"), ("is", "X كم نسبته من Y"), ("chg", "نسبة التغيّر من X إلى Y"), ("disc", "السعر بعد الخصم (السعر ثم %)")], sample="20 500")
def percent(c: Ctx):
    x, y = _nums(c.text, 2)[:2]
    o = c.opt or "of"
    if o == "of":
        return Out.text(f"{x:g}% من {y:g} = <b>{x * y / 100:,.6g}</b>")
    if o == "is":
        if y == 0:
            raise ToolError("لا يمكن القسمة على صفر.")
        return Out.text(f"{x:g} من {y:g} = <b>{x / y * 100:,.4g}%</b>")
    if o == "chg":
        if x == 0:
            raise ToolError("القيمة الأولى صفر.")
        ch = (y - x) / abs(x) * 100
        return Out.text(f"التغيّر من {x:g} إلى {y:g} = <b>{ch:+,.4g}%</b> {'📈' if ch > 0 else '📉'}")
    save = x * y / 100
    return Out.text(f"🏷 السعر {x:g} بخصم {y:g}%\nالتوفير: <b>{save:,.2f}</b>\nالسعر النهائي: <b>{x - save:,.2f}</b>")


@tool("bmi", "calc", "مؤشر كتلة الجسم والوزن المثالي", "أرسل الوزن (كجم) ثم الطول (سم): 70 175", sample="70 175")
def bmi(c: Ctx):
    w, h = _nums(c.text, 2)[:2]
    if not (20 < w < 400 and 80 < h < 250):
        raise ToolError("أدخل الوزن بالكيلو والطول بالسنتيمتر.")
    v = w / (h / 100) ** 2
    cat = "نحافة" if v < 18.5 else ("وزن طبيعي ✅" if v < 25 else ("وزن زائد" if v < 30 else "سمنة"))
    lo, hi = 18.5 * (h / 100) ** 2, 24.9 * (h / 100) ** 2
    return Out.text(f"⚖️ مؤشر الكتلة: <b>{v:.1f}</b> — {cat}\n🎯 الوزن المثالي لطولك: {lo:.1f} – {hi:.1f} كجم")


@tool("loan", "calc", "حاسبة القرض والأقساط", "أرسل: المبلغ ، النسبة السنوية %، المدة بالسنوات. مثال: 20000 8 5", sample="20000 8 5")
def loan(c: Ctx):
    p, r, yrs = _nums(c.text, 3)[:3]
    n = int(yrs * 12)
    if n <= 0 or p <= 0:
        raise ToolError("قيم غير صالحة.")
    i = r / 100 / 12
    m = p / n if i == 0 else p * i * (1 + i) ** n / ((1 + i) ** n - 1)
    return Out.text(f"🏦 القسط الشهري: <b>{m:,.2f}</b>\nإجمالي المدفوع: {m * n:,.2f}\nإجمالي الفائدة: <b>{m * n - p:,.2f}</b>\nعدد الأقساط: {n}")


@tool("split", "calc", "تقسيم الفاتورة والبقشيش", "أرسل: المبلغ ، عدد الأشخاص ، البقشيش %. مثال: 120 4 10", sample="120 4 10")
def split(c: Ctx):
    xs = _nums(c.text, 2)
    total, n, tip = xs[0], max(1, int(xs[1])), xs[2] if len(xs) > 2 else 0
    t = total * (1 + tip / 100)
    return Out.text(f"🍽 المبلغ: {total:,.2f} + بقشيش {tip:g}% = <b>{t:,.2f}</b>\n👥 نصيب كل شخص ({n}): <b>{t / n:,.2f}</b>")


ZONES = [("بغداد", "Asia/Baghdad"), ("مكة / الرياض", "Asia/Riyadh"), ("دبي", "Asia/Dubai"), ("القاهرة", "Africa/Cairo"), ("إسطنبول", "Europe/Istanbul"), ("لندن", "Europe/London"), ("باريس / برلين", "Europe/Paris"), ("موسكو", "Europe/Moscow"), ("نيويورك", "America/New_York"), ("لوس أنجلوس", "America/Los_Angeles"), ("سيدني", "Australia/Sydney"), ("طوكيو", "Asia/Tokyo"), ("بكين", "Asia/Shanghai"), ("دلهي", "Asia/Kolkata")]


@tool("worldtime", "calc", "الوقت في مدن العالم", "سأعرض الوقت الآن في 14 مدينة.", inp="none", sample="")
def worldtime(c: Ctx):
    now = datetime.now(ZoneInfo("UTC"))
    t = c.text.strip()
    if t and "/" in t:
        try:
            z = now.astimezone(ZoneInfo(t))
            return Out.text(f"🕒 {t}: <b>{z:%Y-%m-%d %H:%M}</b>")
        except Exception:
            raise ToolError("منطقة غير معروفة. مثال: Asia/Tokyo")
    rows = [f"{n}: <b>{now.astimezone(ZoneInfo(z)):%H:%M}</b> ({now.astimezone(ZoneInfo(z)):%d/%m})" for n, z in ZONES]
    return Out.text("🌍 <b>الوقت الآن</b>\n" + "\n".join(rows))


@tool("random", "calc", "قرعة وأرقام عشوائية", "اختر النوع ثم أرسل المدخلات.", opts=[("num", "رقم عشوائي (من إلى)"), ("pick", "اختيار من قائمة (افصل بفاصلة)"), ("dice", "نرد 🎲"), ("coin", "عملة 🪙"), ("team", "تقسيم فرق (الأسماء ثم عدد الفرق)")], sample="1 100", auto=("dice", "coin"))
def rnd(c: Ctx):
    o = c.opt or "num"
    if o == "dice":
        return Out.text(f"🎲 {random.randint(1, 6)}")
    if o == "coin":
        return Out.text(random.choice(["🪙 صورة", "🪙 كتابة"]))
    if o == "pick":
        xs = [x.strip() for x in re.split(r"[,،\n]", c.text) if x.strip()]
        if len(xs) < 2:
            raise ToolError("أرسل عنصرين على الأقل مفصولة بفاصلة.")
        return Out.text(f"🎯 الفائز: <b>{random.choice(xs)}</b>")
    if o == "team":
        lines = [x.strip() for x in re.split(r"[,،\n]", c.text) if x.strip()]
        k = 2
        if lines and lines[-1].isdigit():
            k = max(2, int(lines.pop()))
        random.shuffle(lines)
        teams = [lines[i::k] for i in range(k)]
        return Out.text("\n\n".join(f"👥 <b>الفريق {i + 1}</b>\n" + "\n".join(f"• {x}" for x in tm) for i, tm in enumerate(teams)))
    xs = _nums(c.text, 2)
    a, b = int(min(xs[:2])), int(max(xs[:2]))
    return Out.text(f"🎲 <b>{random.randint(a, b)}</b>  (من {a} إلى {b})")

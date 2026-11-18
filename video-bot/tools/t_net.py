import asyncio
import hashlib
import ipaddress
import re
import socket
import ssl
import time
import urllib.parse
from datetime import datetime, timezone

import httpx

from .registry import Ctx, Out, ToolError, tool
from .t_calc import norm

UA = {"User-Agent": "Mozilla/5.0 (compatible; AdwatBot/1.0)"}


def client(**kw) -> httpx.AsyncClient:
    return httpx.AsyncClient(timeout=kw.pop("timeout", 12), follow_redirects=True, headers=UA, **kw)


async def jget(url: str, **params):
    try:
        async with client() as c:
            r = await c.get(url, params=params or None)
            r.raise_for_status()
            return r.json()
    except httpx.HTTPStatusError as e:
        raise ToolError(f"الخدمة ردّت بخطأ ({e.response.status_code}).")
    except Exception:
        raise ToolError("تعذّر الاتصال بالخدمة الآن. حاول لاحقاً.")


def _host_safe(host: str) -> bool:
    """يمنع الطلبات إلى عناوين داخلية (SSRF)"""
    try:
        for fam, _, _, _, sa in socket.getaddrinfo(host, None):
            ip = ipaddress.ip_address(sa[0])
            if ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_reserved or ip.is_multicast or ip.is_unspecified:
                return False
        return True
    except Exception:
        return False


def clean_url(t: str) -> str:
    t = t.strip().split()[0] if t.strip() else ""
    if not re.match(r"^https?://", t, re.I):
        t = "https://" + t
    u = urllib.parse.urlparse(t)
    if not u.hostname or "." not in u.hostname:
        raise ToolError("رابط غير صالح.")
    if not _host_safe(u.hostname):
        raise ToolError("هذا العنوان غير مسموح.")
    return t


WMO = {0: "صافٍ ☀️", 1: "غالباً صافٍ 🌤", 2: "غائم جزئياً ⛅", 3: "غائم ☁️", 45: "ضباب 🌫", 48: "ضباب جليدي 🌫", 51: "رذاذ خفيف 🌦", 53: "رذاذ 🌦", 55: "رذاذ كثيف 🌧", 61: "مطر خفيف 🌦", 63: "مطر 🌧", 65: "مطر غزير 🌧", 71: "ثلج خفيف 🌨", 73: "ثلج ❄️", 75: "ثلج كثيف ❄️", 80: "زخات مطر 🌦", 81: "زخات قوية 🌧", 82: "زخات عنيفة ⛈", 95: "عاصفة رعدية ⛈", 96: "عاصفة وبَرَد ⛈", 99: "عاصفة وبَرَد شديد ⛈"}


@tool("weather", "net", "الطقس لأي مدينة", "اكتب اسم المدينة (عربي أو إنجليزي).", net=True, sample="Baghdad")
async def weather(c: Ctx):
    name = c.text.strip()
    if not name:
        raise ToolError("اكتب اسم المدينة.")
    g = await jget("https://geocoding-api.open-meteo.com/v1/search", name=name, count=1, language="ar")
    if not g.get("results"):
        raise ToolError("لم أجد هذه المدينة.")
    p = g["results"][0]
    w = await jget("https://api.open-meteo.com/v1/forecast", latitude=p["latitude"], longitude=p["longitude"], timezone="auto", forecast_days=5,
                   current="temperature_2m,relative_humidity_2m,apparent_temperature,wind_speed_10m,weather_code",
                   daily="temperature_2m_max,temperature_2m_min,precipitation_probability_max,weather_code")
    cur, d = w["current"], w["daily"]
    lines = [f"🌍 <b>{p['name']}</b>، {p.get('country', '')}",
             f"{WMO.get(cur['weather_code'], '')}  <b>{cur['temperature_2m']:.0f}°</b> (الإحساس {cur['apparent_temperature']:.0f}°)",
             f"💧 الرطوبة {cur['relative_humidity_2m']}% • 💨 الرياح {cur['wind_speed_10m']} كم/س", "", "<b>الأيام القادمة:</b>"]
    for i in range(len(d["time"])):
        lines.append(f"• {d['time'][i][5:]}: {d['temperature_2m_min'][i]:.0f}° – {d['temperature_2m_max'][i]:.0f}° {WMO.get(d['weather_code'][i], '')} ☔{d['precipitation_probability_max'][i] or 0}%")
    return Out.text("\n".join(lines))


CUR_AR = {"دولار": "USD", "يورو": "EUR", "دينار": "IQD", "دينار عراقي": "IQD", "ريال": "SAR", "ريال سعودي": "SAR", "درهم": "AED", "جنيه": "EGP", "ليرة": "TRY", "تركي": "TRY", "استرليني": "GBP", "كويتي": "KWD", "ين": "JPY", "ذهب": "XAU"}


@tool("currency", "net", "محوّل العملات", "اكتب مثل: 100 usd to iqd أو 50 دولار الى دينار", net=True, sample="100 usd to iqd")
async def currency(c: Ctx):
    t = norm(c.text.lower().strip())
    m = re.match(r"^(\d+(?:\.\d+)?)\s*([^\d\s]+(?:\s[^\d\s]+)?)\s*(?:to|in|->|الى|إلى|=)\s*([^\d\s]+(?:\s[^\d\s]+)?)$", t)
    if not m:
        raise ToolError("الصيغة: 100 usd to iqd")
    amt = float(m.group(1))
    a = CUR_AR.get(m.group(2), m.group(2).upper())
    b = CUR_AR.get(m.group(3), m.group(3).upper())
    d = await jget(f"https://open.er-api.com/v6/latest/{a}")
    if d.get("result") != "success" or b not in d.get("rates", {}):
        raise ToolError("عملة غير معروفة. استخدم رموز مثل USD, EUR, IQD, SAR, AED, EGP, TRY")
    r = d["rates"][b]
    return Out.text(f"💱 {amt:,.2f} {a} = <b>{amt * r:,.2f} {b}</b>\nالسعر: 1 {a} = {r:,.4f} {b}\n🕒 {d.get('time_last_update_utc', '')[:22]}")


COINS = {"btc": "bitcoin", "eth": "ethereum", "bnb": "binancecoin", "xrp": "ripple", "sol": "solana", "ada": "cardano", "doge": "dogecoin", "ton": "the-open-network", "trx": "tron", "usdt": "tether", "ltc": "litecoin", "matic": "matic-network", "dot": "polkadot", "shib": "shiba-inu"}


@tool("crypto", "net", "أسعار العملات الرقمية", "اكتب رمز العملة (btc, eth, ton…) أو اتركه للعملات الرئيسية.", net=True, sample="btc eth")
async def crypto(c: Ctx):
    syms = [s for s in re.findall(r"[a-zA-Z]+", c.text.lower()) if s in COINS] or ["btc", "eth", "bnb", "sol", "ton", "xrp"]
    d = await jget("https://api.coingecko.com/api/v3/simple/price", ids=",".join(COINS[s] for s in syms[:10]), vs_currencies="usd", include_24hr_change="true")
    rows = []
    for s in syms[:10]:
        x = d.get(COINS[s])
        if x:
            ch = x.get("usd_24h_change") or 0
            rows.append(f"{'🟢' if ch >= 0 else '🔴'} <b>{s.upper()}</b>: ${x['usd']:,.4f} ({ch:+.1f}%)")
    return Out.text("₿ <b>الأسعار الآن</b>\n" + "\n".join(rows) if rows else "لا بيانات.")


@tool("prayer", "net", "أوقات الصلاة", "اكتب: المدينة ثم الدولة بالإنجليزية. مثال: Baghdad Iraq", net=True, sample="Baghdad Iraq")
async def prayer(c: Ctx):
    parts = c.text.replace(",", " ").split()
    if not parts:
        raise ToolError("اكتب المدينة والدولة.")
    addr = ",".join([parts[0], " ".join(parts[1:])] if len(parts) > 1 else [parts[0]])
    d = await jget("https://api.aladhan.com/v1/timingsByAddress", address=addr, method=3)
    if d.get("code") != 200:
        raise ToolError("لم أجد المدينة. جرّب كتابتها بالإنجليزية.")
    t = d["data"]["timings"]
    h = d["data"]["date"]["hijri"]
    names = [("الفجر", "Fajr"), ("الشروق", "Sunrise"), ("الظهر", "Dhuhr"), ("العصر", "Asr"), ("المغرب", "Maghrib"), ("العشاء", "Isha")]
    return Out.text(f"🕌 <b>{addr}</b>\n📅 {h['day']} {h['month']['ar']} {h['year']} هـ\n\n" + "\n".join(f"{ar}: <b>{t[en][:5]}</b>" for ar, en in names))


@tool("translate", "net", "المترجم", "اختر اللغة الهدف ثم أرسل النص.", net=True, opts=[("ar", "إلى العربية"), ("en", "إلى الإنجليزية"), ("tr", "إلى التركية"), ("fr", "إلى الفرنسية"), ("fa", "إلى الفارسية"), ("de", "إلى الألمانية"), ("es", "إلى الإسبانية"), ("ru", "إلى الروسية")], sample="hello world")
async def translate(c: Ctx):
    from deep_translator import GoogleTranslator
    if len(c.text) > 4000:
        raise ToolError("النص طويل (الحد 4000).")
    try:
        r = await asyncio.to_thread(lambda: GoogleTranslator(source="auto", target=c.opt or "ar").translate(c.text))
    except Exception:
        raise ToolError("تعذّرت الترجمة الآن.")
    return Out.text(f"🌐 <b>الترجمة:</b>\n\n<code>{r}</code>")


@tool("ipinfo", "net", "معلومات عنوان IP", "أرسل عنوان IP أو اسم موقع.", net=True, sample="8.8.8.8")
async def ipinfo(c: Ctx):
    q = c.text.strip().replace("https://", "").replace("http://", "").split("/")[0]
    if not q:
        raise ToolError("أرسل IP أو نطاقاً.")
    if not re.fullmatch(r"[\d.:a-fA-F]+", q):
        try:
            q = (await asyncio.to_thread(socket.gethostbyname, q))
        except Exception:
            raise ToolError("لم أستطع حلّ هذا النطاق.")
    d = await jget(f"https://ipwho.is/{q}")
    if not d.get("success"):
        raise ToolError("عنوان غير صالح.")
    conn = d.get("connection", {})
    return Out.text(f"🌐 <b>{d['ip']}</b>\n🏳️ {d.get('flag', {}).get('emoji', '')} {d.get('country')} — {d.get('region')}، {d.get('city')}\n🏢 {conn.get('isp', '')} (AS{conn.get('asn', '')})\n🕒 {d.get('timezone', {}).get('id', '')}\n📍 <a href=\"https://maps.google.com/?q={d.get('latitude')},{d.get('longitude')}\">الخريطة</a>")


@tool("sitecheck", "net", "فحص موقع (يعمل؟ سرعة، SSL)", "أرسل رابط الموقع.", net=True, sample="example.com")
async def sitecheck(c: Ctx):
    url = clean_url(c.text)
    t0 = time.time()
    try:
        async with client() as cl:
            r = await cl.get(url)
    except Exception:
        return Out.text(f"🔴 <b>{url}</b>\nالموقع لا يستجيب أو غير متاح.")
    ms = int((time.time() - t0) * 1000)
    host = urllib.parse.urlparse(str(r.url)).hostname
    ssl_line = ""
    try:
        def cert():
            ctx = ssl.create_default_context()
            with socket.create_connection((host, 443), timeout=8) as s, ctx.wrap_socket(s, server_hostname=host) as ss:
                return ss.getpeercert()
        cc = await asyncio.to_thread(cert)
        exp = datetime.strptime(cc["notAfter"], "%b %d %H:%M:%S %Y %Z")
        ssl_line = f"\n🔒 شهادة SSL تنتهي: {exp:%Y-%m-%d} (بعد {(exp - datetime.utcnow()).days} يوم)"
    except Exception:
        pass
    h = r.headers
    return Out.text(f"{'🟢' if r.status_code < 400 else '🟠'} <b>{r.status_code}</b> • {ms}ms\n🔗 {r.url}\n🖥 الخادم: {h.get('server', '—')}\n📄 النوع: {h.get('content-type', '—')}\n📦 الحجم: {len(r.content) / 1024:.0f}KB{ssl_line}")


@tool("unshort", "net", "فك الروابط المختصرة", "أرسل رابطاً مختصراً (bit.ly …) وسأكشف وجهته الحقيقية بأمان.", net=True, sample="https://bit.ly/3x")
async def unshort(c: Ctx):
    url = clean_url(c.text)
    hops = [url]
    try:
        async with httpx.AsyncClient(timeout=10, follow_redirects=False, headers=UA) as cl:
            for _ in range(8):
                r = await cl.get(hops[-1])
                loc = r.headers.get("location")
                if r.status_code in (301, 302, 303, 307, 308) and loc:
                    nxt = urllib.parse.urljoin(hops[-1], loc)
                    if not _host_safe(urllib.parse.urlparse(nxt).hostname or ""):
                        break
                    hops.append(nxt)
                else:
                    break
    except Exception:
        raise ToolError("تعذّر فتح الرابط.")
    return Out.text("🔓 <b>المسار:</b>\n" + "\n↳ ".join(hops) + f"\n\n🎯 الوجهة النهائية:\n<code>{hops[-1]}</code>")


@tool("shorten", "net", "اختصار الروابط", "أرسل الرابط الطويل.", net=True, sample="https://example.com/very/long")
async def shorten(c: Ctx):
    url = clean_url(c.text)
    try:
        async with client() as cl:
            r = await cl.get("https://is.gd/create.php", params={"format": "simple", "url": url})
            r.raise_for_status()
    except Exception:
        raise ToolError("تعذّر الاختصار الآن.")
    return Out.text(f"✂️ <code>{r.text.strip()}</code>")


@tool("screenshot", "net", "لقطة شاشة لأي موقع", "أرسل رابط الموقع وسأصوّره لك.", net=True, sample="example.com")
async def screenshot(c: Ctx):
    url = clean_url(c.text)
    for api in (f"https://image.thum.io/get/width/1280/crop/900/noanimate/{url}", f"https://s0.wp.com/mshots/v1/{urllib.parse.quote(url, safe='')}?w=1280&h=900"):
        try:
            async with client(timeout=40) as cl:
                r = await cl.get(api)
                if r.status_code == 200 and r.headers.get("content-type", "").startswith("image") and len(r.content) > 5000:
                    return Out.photo(r.content, f"📸 {url}")
        except Exception:
            continue
    raise ToolError("تعذّر تصوير الموقع الآن.")


@tool("dns", "net", "فحص DNS للنطاق", "أرسل اسم النطاق.", net=True, opts=[("A", "A"), ("AAAA", "AAAA"), ("MX", "MX"), ("TXT", "TXT"), ("NS", "NS"), ("CNAME", "CNAME")], sample="example.com")
async def dns(c: Ctx):
    dom = c.text.strip().lower().replace("https://", "").split("/")[0]
    if not re.fullmatch(r"[a-z0-9.-]+\.[a-z]{2,}", dom):
        raise ToolError("نطاق غير صالح.")
    d = await jget("https://dns.google/resolve", name=dom, type=c.opt or "A")
    ans = d.get("Answer") or []
    if not ans:
        return Out.text("لا توجد سجلات من هذا النوع.")
    return Out.text(f"🧭 <b>{dom}</b> [{c.opt or 'A'}]\n" + "\n".join(f"• <code>{a['data']}</code> (TTL {a['TTL']})" for a in ans[:15]))


@tool("whois", "net", "معلومات النطاق (WHOIS)", "أرسل النطاق (مثل google.com).", net=True, sample="google.com")
async def whois(c: Ctx):
    dom = c.text.strip().lower().replace("https://", "").split("/")[0].removeprefix("www.")
    if not re.fullmatch(r"[a-z0-9.-]+\.[a-z]{2,}", dom):
        raise ToolError("نطاق غير صالح.")
    d = await jget(f"https://rdap.org/domain/{dom}")
    ev = {e.get("eventAction"): e.get("eventDate", "")[:10] for e in d.get("events", [])}
    ns = [n.get("ldhName") for n in d.get("nameservers", [])]
    return Out.text(f"🌐 <b>{d.get('ldhName', dom)}</b>\n📅 التسجيل: {ev.get('registration', '—')}\n🔄 آخر تحديث: {ev.get('last changed', '—')}\n⏳ الانتهاء: {ev.get('expiration', '—')}\n🏷 الحالة: {', '.join(d.get('status', [])[:3]) or '—'}\n🧭 خوادم الأسماء: {', '.join(ns[:4]) or '—'}")


@tool("quran", "net", "آيات القرآن والبحث", "أرسل رقم السورة:الآية (2:255) أو كلمة للبحث.", net=True, sample="2:255")
async def quran(c: Ctx):
    t = norm(c.text.strip())
    m = re.fullmatch(r"(\d{1,3})\s*[:/\-]\s*(\d{1,3})", t)
    if m:
        d = await jget(f"https://api.alquran.cloud/v1/ayah/{m.group(1)}:{m.group(2)}/quran-uthmani")
        if d.get("code") != 200:
            raise ToolError("رقم السورة أو الآية غير صحيح.")
        a = d["data"]
        return Out.text(f"📖 <b>سورة {a['surah']['name']}</b> — الآية {a['numberInSurah']}\n\n﴿ {a['text']} ﴾")
    if len(t) < 2:
        raise ToolError("أرسل 2:255 أو كلمة.")
    d = await jget(f"https://api.alquran.cloud/v1/search/{urllib.parse.quote(t)}/all/quran-simple")
    res = (d.get("data") or {}).get("matches") or []
    if not res:
        return Out.text("لم أجد نتائج.")
    return Out.text(f"🔎 {d['data']['count']} نتيجة (أعرض 6):\n\n" + "\n\n".join(f"﴿ {x['text']} ﴾\n— {x['surah']['name']} ({x['surah']['number']}:{x['numberInSurah']})" for x in res[:6]))


@tool("wiki", "net", "ويكيبيديا: ملخص أي موضوع", "اكتب الموضوع.", net=True, opts=[("ar", "العربية"), ("en", "الإنجليزية")], sample="بغداد")
async def wiki(c: Ctx):
    lang = c.opt or "ar"
    q = c.text.strip()
    if not q:
        raise ToolError("اكتب الموضوع.")
    s = await jget(f"https://{lang}.wikipedia.org/w/api.php", action="opensearch", search=q, limit=1, format="json")
    if not s[1]:
        raise ToolError("لم أجد مقالاً.")
    d = await jget(f"https://{lang}.wikipedia.org/api/rest_v1/page/summary/{urllib.parse.quote(s[1][0])}")
    txt = f"📚 <b>{d.get('title')}</b>\n\n{(d.get('extract') or '')[:900]}\n\n🔗 {d.get('content_urls', {}).get('desktop', {}).get('page', '')}"
    img = (d.get("thumbnail") or {}).get("source")
    if img:
        try:
            async with client() as cl:
                r = await cl.get(img)
            return Out.photo(r.content, txt[:1000])
        except Exception:
            pass
    return Out.text(txt)


@tool("pwned", "net", "هل تسرّبت كلمة سرّي؟", "أرسل كلمة السر. تُحسب بصمتها محلياً ويُرسل أول 5 رموز فقط (k-anonymity) — كلمتك لا تغادر الخادم.", net=True, sample="password")
async def pwned(c: Ctx):
    h = hashlib.sha1(c.text.strip().encode()).hexdigest().upper()
    try:
        async with client() as cl:
            r = await cl.get(f"https://api.pwnedpasswords.com/range/{h[:5]}", headers={"Add-Padding": "true"})
            r.raise_for_status()
    except Exception:
        raise ToolError("تعذّر الاتصال بالخدمة.")
    cnt = 0
    for line in r.text.splitlines():
        suf, n = line.split(":")
        if suf == h[5:]:
            cnt = int(n)
    if cnt:
        return Out.text(f"🔴 هذه الكلمة ظهرت في تسريبات <b>{cnt:,}</b> مرة! غيّرها فوراً ولا تستخدمها.")
    return Out.text("🟢 لم تظهر هذه الكلمة في التسريبات المعروفة (لا يعني هذا أنها قوية).")

"""الإيموجي المميزة (Premium Custom Emoji) للبوت.

كل إيموجي عادي في نصوص البوت وأزراره يُحوَّل تلقائياً إلى إيموجي مميز:
- في النصوص والتعليقات (HTML):  <tg-emoji emoji-id="ID">X</tg-emoji>
- في الأزرار: يُزال الإيموجي العادي من نص الزر ويوضع المميز في icon_custom_emoji_id
- الإيموجي العادي الذي ليس له مقابل مميز يُحذف (لا تبقى إيموجيات عادية)
- إشعارات الضغط (answerCallbackQuery) لا تدعم المميز، فتُزال منها الإيموجيات

يعمل ذلك عبر وسيط (middleware) على جلسة البوت، فيشمل كل الرسائل تلقائياً.
تعطيله: PREMIUM_EMOJI=0 في .env
"""
import os
import re

from aiogram.client.session.middlewares.base import BaseRequestMiddleware
from aiogram.methods import (AnswerCallbackQuery, EditMessageCaption, EditMessageMedia, EditMessageReplyMarkup,
                             EditMessageText, SendMessage, SendPhoto, SendVideo, SendDocument, SendAnimation)
from aiogram.types import InlineKeyboardButton, InlineKeyboardMarkup

ENABLED = os.getenv("PREMIUM_EMOJI", "1").strip() not in ("0", "false", "no", "")

# الإيموجي العادي -> معرّف الإيموجي المميز (اخترت كل واحد حسب شكله الفعلي)
MAP: dict[str, str] = {
    # حالة
    "✅": "5021905410089550576",  # علامة صح متحركة
    "❌": "5017058788604117831",  # دائرة حمراء بعلامة ×
    "⚠️": "5017546864392668003",  # مثلث تحذير
    "⏳": "5778546461436284629",  # بطارية تشحن = جاري التنفيذ
    "⌛": "4999445114838319935",  # Zzz = في قائمة الانتظار
    "⏱": "5805429164253123352",  # ساعة
    "👋": "4963072209334567688",  # بومة تلوّح
    "🪄": "5017532575036474281",  # عصا سحرية بنجمة
    "💡": "5064709487953183440",  # نجمة لامعة = مقترح
    "💎": "5019808886228517474",  # ألماسة زرقاء = HDR
    "🔆": "5019551308449841888",  # قمر ونجوم = تحسين وأجواء
    "👇": "5899757765743615694",  # سهم تنزيل للأسفل
    "♥️": "5170618377835643873",  # وجه مبتسم
    # الاستوديو والأزرار الرئيسية
    "🎛": "5019413195186504264",  # ترس إعدادات
    "📱": "5884064642438795702",  # 1080 HD = دقة الآيفون
    "🔝": "6026261955818294779",  # 2X = آيفون برو 2K
    "🏆": "4999002445444023072",  # جوهرة وردية = قوالب المصممين
    "🎞": "5931629923478278721",  # معرض صور = قوالب الأجواء
    "🔥": "4997289591011544358",  # برق = قوالب الإيديت
    "🖋": "5839116473951328489",  # فقاعة بنجمة = محرر النصوص
    "✍️": "5886455371559604605",  # فقاعة بسهم = نص سريع / اكتب
    "📋": "5877485980901971030",  # طبقات = المختارة
    "↩️": "5875082500023258804",  # تراجع
    "↪️": "5875506366050734240",  # إعادة
    "🔄": "5017470156276761427",  # استرجاع المحذوف
    "🗑": "5019500511871632068",  # سلة حذف
    "🧹": "5775903905498010383",  # سلة زرقاء = مسح الكل
    "🚀": "5019285557348401713",  # طائرة ورقية = معالجة وتصدير
    "🔙": "5877536313623711363",  # رجوع
    "◀️": "5888484185261216745",  # الصفحة السابقة
    "▶️": "5832251986635920010",  # الصفحة التالية
    # الأقسام
    "✨": "4969829361122280042",  # راية بنجمة = تلقائي ذكي
    "🥁": "6005843436479975944",  # تكرار = إيديت على الإيقاع
    "🔬": "5017088445353296841",  # عدسة بحث = واقعية فائقة
    "🔍": "5017088445353296841",
    "🌈": "5019808886228517474",  # HDR
    "🎨": "5161221487608201804",  # دائرتان ملونتان = الألوان
    "✂️": "5915980888012755151",  # لوحة مقسومة = المونتاج
    "🎬": "5881701547137570062",  # HD = تأثيرات/سينمائي
    "🎉": "4996755833950831347",  # احتفال = جسيمات
    "🖥": "5886707481844912001",  # توسيع = الدقة
    "🎵": "5019734235401945645",  # ميكروفون = الصوت
    "🎧": "5019734235401945645",
    "🔊": "5019734235401945645",
    "🔇": "5462882007451185227",  # NO
    "📤": "5884343982816759327",  # مشاركة = تصدير خاص
    # معلومات الفيديو
    "📐": "5882002216323125435",  # SD/HD = الأبعاد
    "📦": "5877316724830768997",  # بطاقة ذاكرة = الحجم
    "📎": "5879883461711367869",  # تنزيل = أرسل كملف
    # الأدمن والمساعدة
    "📊": "5874986954180791957",  # أسهم صعود/نزول = الإحصائيات
    "👥": "5814550759961793482",  # مستخدم
    "🆔": "5801050758627462752",  # @
    "📣": "5019734235401945645",  # ميكروفون = إذاعة
    "📖": "5776121926627888870",  # معلومات
    "❓": "5776056162088652694",
    # عائلات قوالب المصممين
    "🌃": "5161221487608201804",
    "🖤": "5048863871834522790",  # بومة ليلية = دراما وغموض
    "👑": "4999002445444023072",
    "💕": "5465262274031659421",  # I❤U
    "🌿": "5019551308449841888",
    "⚡": "4997289591011544358",
    "📼": "5836749569014109509",  # شاشة = ريترو
}

# رموز مسماة لقسم الصور: ⟦name⟧ -> (معرّف الإيموجي المميز، بديل عادي)
PH: dict[str, tuple[str, str]] = {
    "photo": ("5258254475386167466", "🖼"),     # صورة
    "photos": ("5257974976094412956", "🖼"),    # صور متعددة = جلسة الصور
    "film": ("5258391252914676042", "📼"),      # شريط فيلم = الفيديو
    "home": ("4963010134172239128", "🏠"),     # بيت = القائمة
    "grid": ("4961010470413665487", "💠"),     # شبكة = إطارات وقياسات
    "layers": ("4961186405159011104", "💠"),   # طبقات = الخلفية
    "undo": ("5260687119092817530", "🔄"),     # تراجع
    "redo": ("5258420634785947640", "🔄"),     # إعادة
    "trash": ("5258130763148172425", "🗑"),    # حذف
    "clear": ("5267123797600783095", "❌"),    # مسح الكل
    "check": ("4961121396534019447", "✅"),    # تم/تطبيق
    "done": ("5357069174512303778", "✅"),     # تم
    "download": ("5258336354642697821", "⬇️"),  # تحميل/تصدير
    "share": ("5260379144167890225", "🔼"),    # مشاركة/رفع
    "brush": ("5258215635996908355", "🎨"),    # ريشة = فلاتر
    "gem": ("5359629206948976159", "💎"),      # (جوهرة) تحسين
    "star": ("5260412365739925015", "⭐️"),     # نجمة = تأثيرات فنية
    "search": ("5429571366384842791", "🔎"),   # بحث
    "plus": ("5359529383319084413", "➕"),     # إضافة
    "minus": ("5275969776668134187", "⛔️"),    # إخفاء
    "move": ("4960789253828117549", "⬆️"),      # تحويل
    "refresh": ("4960865034231088066", "🔄"),   # تدوير
    "up": ("4961097812868596792", "🆙"),       # تكبير
    "right": ("4960928080056026316", "➡️"),    # التالي
    "left": ("4960802211744449967", "⬅️"),     # السابق
    "file": ("5258477770735885832", "📄"),     # ملف
    "book": ("5258328383183396223", "📖"),     # مساعدة
    "chat": ("5258215846450305872", "💬"),     # نصوص
    "folder": ("5258514780469075716", "📂"),   # حفظ
    "star2": ("5258185631355378853", "⭐️"),
    "badge": ("5258391025281408576", "📈"),
    "stack": ("4960800343433675689", "💠"),
    "user": ("5256143829672672750", "👤"),
    "gear": ("5019413195186504264", "⚙️"),
}
_PH = re.compile(r"⟦(\w+)⟧")


# كل الإيموجيات العادية (مع محدّدات التنوع وأرقام الأزرار مثل 3️⃣)
_EMOJI = re.compile(
    "(?:[0-9#*]️?⃣)"
    "|(?:[\U0001F1E6-\U0001F1FF]{2})"
    "|(?:[\U0001F000-\U0001FAFF☀-➿⬀-⯿←-⇿⌀-⏿▪▫▶◀◻-◾⤴⤵〰〽™ℹ]"
    "️?(?:‍[\U0001F000-\U0001FAFF☀-➿]️?)*)"
)
_TAG = re.compile(r"<tg-emoji[^>]*>.*?</tg-emoji>", re.S)


def _key(e: str) -> str | None:
    if e in MAP:
        return e
    b = e.replace("️", "")
    for k in (b, b + "️"):
        if k in MAP:
            return k
    return None


def _tidy(s: str) -> str:
    s = re.sub(r"[ \t]{2,}", " ", s)
    s = re.sub(r"[ \t]+\n", "\n", s)
    s = re.sub(r"\n[ \t]+", "\n", s)
    s = re.sub(r"•\s*•", "•", s)
    return s.strip()


def strip(s: str) -> str:
    """إزالة كل الإيموجيات العادية (لنصوص لا تدعم المميز)"""
    return _tidy(_EMOJI.sub("", _PH.sub("", s or "")))


def html(s: str) -> str:
    """تحويل الإيموجي العادي إلى مميز داخل نص HTML (وحذف ما ليس له مقابل)"""
    if not s:
        return s
    parts, pos = [], 0
    for m in _TAG.finditer(s):  # لا نلمس ما تحوّل سابقاً
        parts.append(_conv(s[pos:m.start()]))
        parts.append(m.group(0))
        pos = m.end()
    parts.append(_conv(s[pos:]))
    return _tidy("".join(parts))


def _conv(s: str) -> str:
    def rep(m):
        k = _key(m.group(0))
        return f'<tg-emoji emoji-id="{MAP[k]}">{k}</tg-emoji>' if k else ""
    s = _EMOJI.sub(rep, s)
    return _PH.sub(lambda m: f'<tg-emoji emoji-id="{PH[m.group(1)][0]}">{PH[m.group(1)][1]}</tg-emoji>' if m.group(1) in PH else "", s)


def tg(e: str) -> str:
    """إيموجي مميز واحد بالاسم: tg('✅')"""
    k = _key(e)
    return f'<tg-emoji emoji-id="{MAP[k]}">{k}</tg-emoji>' if k else ""


def style_for(b: InlineKeyboardButton) -> str | None:
    """ألوان الأزرار (Bot API الأحدث): success أخضر • danger أحمر • primary أزرق"""
    d = b.callback_data or ""
    if d in ("go", "pg", "pv", "ad:bcgo") or d.startswith("ad:unban:"):
        return "success"
    if d.startswith(("ad:ban:", "ad:kill:", "ad:clean")) or d == "ad:close":
        return "danger"
    if d in ("ad:stats", "ad:users:last:0", "ad:search", "ad:bc", "ad:set"):
        return "primary"
    if d in ("clr", "stepcancel", "pz", "pcancel") or d.startswith(("del:", "pd:")):
        return "danger"
    if d.startswith("ptl:") or d in ("pe:auto", "pe:iphone", "pe:bg_remove", "pe:bg_blur", "pe:bg_bokeh", "pe:skin", "pk"):
        return "primary"
    if b.web_app or d in ("dz", "dzfam", "e:cv_iphone", "e:cv_iphone_2k", "e:text", "stk") or d.startswith(("tpl:", "dzf:")):
        return "primary"
    if d.startswith(("t:", "dzt:")):
        return "success"
    return None


def button(b: InlineKeyboardButton) -> InlineKeyboardButton:
    out = _button(b)
    if not out.style and (st := style_for(b)):
        out = out.model_copy(update={"style": st})
    return out


def _button(b: InlineKeyboardButton) -> InlineKeyboardButton:
    """أول إيموجي في نص الزر يصبح أيقونة مميزة، وتُزال الإيموجيات العادية من النص"""
    if b.icon_custom_emoji_id:
        return b.model_copy(update={"text": strip(b.text) or b.text})
    icon = None
    pm = _PH.search(b.text or "")
    if pm and pm.group(1) in PH:
        icon = PH[pm.group(1)][0]
    for m in ([] if icon else _EMOJI.finditer(b.text or "")):
        k = _key(m.group(0))
        if k:
            icon = MAP[k]
            break
    text = strip(b.text) or b.text
    upd = {"text": text}
    if icon:
        upd["icon_custom_emoji_id"] = icon
    return b.model_copy(update=upd)


def markup(mk):
    if not isinstance(mk, InlineKeyboardMarkup):
        return mk
    return InlineKeyboardMarkup(inline_keyboard=[[button(b) for b in row] for row in mk.inline_keyboard])


def _is_html(method) -> bool:
    pm = getattr(method, "parse_mode", None)
    return not isinstance(pm, str) or pm.upper() == "HTML"  # الافتراضي في البوت HTML


class PremiumEmoji(BaseRequestMiddleware):
    async def __call__(self, make_request, bot, method):
        if ENABLED:
            try:
                _apply(method)
            except Exception:  # noqa: BLE001 — لا نُفشل الإرسال بسبب التجميل
                pass
        return await make_request(bot, method)


def _apply(method):
    if isinstance(method, AnswerCallbackQuery):
        if method.text:
            method.text = strip(method.text)[:200]
        return
    if isinstance(method, (SendMessage, EditMessageText)):
        method.text = html(method.text) if _is_html(method) else strip(method.text)
    elif isinstance(method, (SendPhoto, SendVideo, SendDocument, SendAnimation, EditMessageCaption)):
        if method.caption:
            method.caption = html(method.caption) if _is_html(method) else strip(method.caption)
    elif isinstance(method, EditMessageMedia):
        med = method.media
        if getattr(med, "caption", None):
            cap = html(med.caption) if _is_html(med) else strip(med.caption)
            method.media = med.model_copy(update={"caption": cap})  # الأنواع مجمّدة
    if isinstance(method, (SendMessage, EditMessageText, SendPhoto, SendVideo, SendDocument, SendAnimation,
                           EditMessageCaption, EditMessageMedia, EditMessageReplyMarkup)):
        mk = getattr(method, "reply_markup", None)
        if mk is not None:
            method.reply_markup = markup(mk)

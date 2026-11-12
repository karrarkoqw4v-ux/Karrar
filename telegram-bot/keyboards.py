"""لوحات أزرار بوصلة: لا أوامر مكتوبة إطلاقاً. القائمة الرئيسية لوحة أزرار ثابتة (ReplyKeyboard) تظهر
مكان لوحة المفاتيح، والقوائم الفرعية (اختيار نموذج، تأكيد عدد الإيموجي...) أزرار داخل الرسالة (InlineKeyboard)."""

BTN_IMAGE = "🖼️ إنشاء صورة"
BTN_ABOUT = "ℹ️ عن بوصلة"
BTN_MODEL = "🧠 اختيار نموذج الذكاء"
BTN_ADMIN = "⚙️ لوحة التحكم"
BTN_BACK = "⬅️ رجوع"
BTN_STATS = "📊 الإحصائيات"
BTN_BROADCAST = "📢 إرسال بث"
BTN_EMOJI = "✨ إضافة إيموجيات مميزة"
BTN_LISTMOJI = "🗂️ عرض الإيموجيات المحفوظة"
BTN_ADD_API = "➕ إضافة API"
BTN_CANCEL = "❌ إلغاء"


def main_menu(is_admin: bool) -> dict:
    def b(text, cat):
        return dict({"text": text}, **_icon(cat))
    rows = [[b(BTN_IMAGE, "عام"), b(BTN_MODEL, "تفكير")], [b(BTN_ABOUT, "عام")]]
    if is_admin:
        rows.append([b(BTN_ADMIN, "نجاح")])
    return {"keyboard": rows, "resize_keyboard": True, "is_persistent": True}


def admin_menu() -> dict:
    return {"keyboard": [[BTN_STATS, BTN_BROADCAST], [BTN_EMOJI, BTN_LISTMOJI], [BTN_ADD_API], [BTN_BACK]], "resize_keyboard": True}


def cancel_menu() -> dict:
    return {"keyboard": [[BTN_CANCEL]], "resize_keyboard": True}


def _icon(category: str) -> dict:
    """يُرجع {"icon_custom_emoji_id": ".."} إن وُجد إيموجي مميز محفوظ بهذا التصنيف، وإلا {} (Bot API 9.4)."""
    import premium_emoji
    e = premium_emoji.pick_for_category(category)
    return {"icon_custom_emoji_id": e["id"]} if e else {}


def model_picker_inline(models: list, page: int = 0, per_page: int = 8) -> dict:
    start = page * per_page
    chunk = models[start:start + per_page]
    rows = [[{"text": m.split("/")[-1], "callback_data": f"model:{m}"}] for m in chunk]
    nav = []
    if page > 0:
        nav.append({"text": "◀️ السابق", "callback_data": f"modelpage:{page - 1}"})
    if start + per_page < len(models):
        nav.append({"text": "التالي ▶️", "callback_data": f"modelpage:{page + 1}"})
    if nav:
        rows.append(nav)
    return {"inline_keyboard": rows}


def emoji_count_inline() -> dict:
    return {"inline_keyboard": [[
        {"text": "150 إيموجي", "callback_data": "moji_target:150"},
        {"text": "350 إيموجي", "callback_data": "moji_target:350"},
        {"text": "1000 إيموجي", "callback_data": "moji_target:1000"},
    ]]}


def provider_picker_inline() -> dict:
    import providers
    rows = [[{"text": p[0], "callback_data": f"provider:{p[0]}"}] for p in providers.PROVIDERS]
    return {"inline_keyboard": rows}


def provider_model_inline(provider_name: str) -> dict:
    import providers
    p = providers.by_name(provider_name)
    if not p:
        return {"inline_keyboard": []}
    _, _, m1, m2 = p
    rows = [[{"text": m1, "callback_data": f"setmodel:{m1}"}]]
    if m2 != m1:
        rows.append([{"text": m2, "callback_data": f"setmodel:{m2}"}])
    return {"inline_keyboard": rows}

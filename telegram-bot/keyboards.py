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
BTN_CANCEL = "❌ إلغاء"


def main_menu(is_admin: bool) -> dict:
    rows = [[BTN_IMAGE, BTN_MODEL], [BTN_ABOUT]]
    if is_admin:
        rows.append([BTN_ADMIN])
    return {"keyboard": rows, "resize_keyboard": True, "is_persistent": True}


def admin_menu() -> dict:
    return {"keyboard": [[BTN_STATS, BTN_BROADCAST], [BTN_EMOJI, BTN_LISTMOJI], [BTN_BACK]], "resize_keyboard": True}


def cancel_menu() -> dict:
    return {"keyboard": [[BTN_CANCEL]], "resize_keyboard": True}


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

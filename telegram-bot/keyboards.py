"""لوحات أزرار بوصلة: لا أوامر مكتوبة إطلاقاً، ولا إيموجي عادي بأي نص زر — الأيقونات كلها إيموجي مميز
حقيقي عبر icon_custom_emoji_id (Bot API 9.4)، تظهر فقط إن وُجد إيموجي محفوظ بتصنيف "أزرار"."""

BTN_IMAGE = "إنشاء صورة"
BTN_VIDEO = "إنشاء فيديوهات"
BTN_GAME = "إنشاء لعبة"
BTN_APPS = "إنشاء تطبيقات"
BTN_PRIVATE = "محادثة خاصة"
BTN_SETTINGS = "الإعدادات"
BTN_ABOUT = "عن بوصلة"
BTN_MODEL = "اختيار نموذج الذكاء"
BTN_ADMIN = "لوحة التحكم"
BTN_BACK = "رجوع"
BTN_STATS = "الإحصائيات"
BTN_BROADCAST = "إرسال بث"
BTN_EMOJI = "إضافة إيموجيات مميزة"
BTN_LISTMOJI = "عرض الإيموجيات المحفوظة"
BTN_ADD_API = "إضافة API"
BTN_CANCEL = "إلغاء"
BTN_AGREE = "أوافق، تابع"

# أزرار "قيد التطوير" فعلياً (صدق: غير مبنية بعد)
UNDER_DEV_BUTTONS = {BTN_VIDEO, BTN_GAME, BTN_APPS, BTN_PRIVATE}


def _icon_for_button(name: str) -> dict:
    """أيقونة ثابتة لكل زر (نفس الإيموجي دايماً لنفس الزر، مو عشوائي يتغيّر كل مرة): موضعها داخل مجموعة
    إيموجي "أزرار" تُحسَب من اسم الزر نفسه، فتبقى واحدة طول الوقت. يُرجع {} بلا أي أيقونة لو ما فيه إيموجي محفوظ بعد."""
    import premium_emoji
    pool = premium_emoji.all_in_category("أزرار")
    if not pool:
        return {}
    idx = sum(ord(c) for c in name) % len(pool)
    return {"icon_custom_emoji_id": pool[idx]["id"]}


def _b(text: str) -> dict:
    return dict({"text": text}, **_icon_for_button(text))


def main_menu(is_admin: bool) -> dict:
    rows = [
        [_b(BTN_IMAGE), _b(BTN_VIDEO)],
        [_b(BTN_GAME), _b(BTN_APPS)],
        [_b(BTN_PRIVATE), _b(BTN_MODEL)],
        [_b(BTN_SETTINGS), _b(BTN_ABOUT)],
    ]
    if is_admin:
        rows.append([_b(BTN_ADMIN)])
    return {"keyboard": rows, "resize_keyboard": True, "is_persistent": True}


def admin_menu() -> dict:
    return {"keyboard": [[_b(BTN_STATS), _b(BTN_BROADCAST)], [_b(BTN_EMOJI), _b(BTN_LISTMOJI)],
                          [_b(BTN_ADD_API)], [_b(BTN_BACK)]], "resize_keyboard": True}


def cancel_menu() -> dict:
    return {"keyboard": [[_b(BTN_CANCEL)]], "resize_keyboard": True}


def agree_gate_inline() -> dict:
    return {"inline_keyboard": [[{"text": BTN_AGREE, "callback_data": "agree_terms"}]]}


def model_picker_inline(models: list, page: int = 0, per_page: int = 8) -> dict:
    start = page * per_page
    chunk = models[start:start + per_page]
    rows = [[{"text": m.split("/")[-1], "callback_data": f"model:{m}"}] for m in chunk]
    nav = []
    if page > 0:
        nav.append({"text": "السابق", "callback_data": f"modelpage:{page - 1}"})
    if start + per_page < len(models):
        nav.append({"text": "التالي", "callback_data": f"modelpage:{page + 1}"})
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

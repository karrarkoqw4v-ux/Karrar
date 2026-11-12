"""إيموجيات تيليجرام المميزة (Premium/Custom emoji).

صدق مهم: لا يوجد "دليل عام" لأرقام الإيموجيات المميزة (custom_emoji_id) تنشره تيليجرام، ولا طريقة لأحد
(أنا ولا أي بوت) أن "يجلبها" من فراغ. الرقم مرتبط برسالة حقيقية فيها ذاك الإيموجي بعينه، ويُستخرج فقط من
كائن الرسالة نفسه (message.entities حيث type == "custom_emoji"). الطريقة الوحيدة المضمونة والصادقة:

1) افتح تيليجرام، واكتب/الصق أي إيموجي مميز (من لوحة الإيموجي، النجمة ⭐ تدل عليها) داخل رسالة.
2) أرسلها أو حوّلها (forward) إلى هذا البوت وأنت الأدمن.
3) البوت يرد عليك فوراً بكل custom_emoji_id موجود فيها، جاهزاً لنسخه إلى premium_emoji.json.
كرّر هذا لأي عدد تريده (10، 50، 155...)، وكل رقم تحصل عليه بهذي الطريقة حقيقي ومضمون أنه يعمل.
"""
import json
import os

import config


def load() -> dict:
    if os.path.exists(config.PREMIUM_EMOJI_FILE):
        return json.load(open(config.PREMIUM_EMOJI_FILE, encoding="utf-8"))
    return {}


def save(data: dict):
    json.dump(data, open(config.PREMIUM_EMOJI_FILE, "w", encoding="utf-8"), ensure_ascii=False, indent=1)


def _utf16_slice(text: str, offset: int, length: int) -> str:
    """تيليجرام يحسب offset/length بوحدات UTF-16، لا بمحارف بايثون؛ هذا يفرق فعلياً متى ما
    سبق الإيموجي المميز حرف خارج المستوى الأساسي (BMP)، مثل إيموجي آخر."""
    u16 = text.encode("utf-16-le")
    start, end = offset * 2, (offset + length) * 2
    return u16[start:end].decode("utf-16-le", errors="ignore")


def extract_from_message(msg: dict) -> list:
    """يُرجع قائمة [{"id":.., "char":..}] لكل إيموجي مميز موجود في رسالة تيليجرام (الأصلية أو المحوَّلة)."""
    out = []
    text = msg.get("text") or msg.get("caption") or ""
    for e in (msg.get("entities") or msg.get("caption_entities") or []):
        if e.get("type") == "custom_emoji" and e.get("custom_emoji_id"):
            char = _utf16_slice(text, e["offset"], e["length"])
            out.append({"id": e["custom_emoji_id"], "char": char})
    return out


def add(name: str, custom_emoji_id: str, char: str, category: str = "عام") -> dict:
    data = load()
    data[name] = {"id": custom_emoji_id, "char": char, "category": category}
    save(data)
    return data


# كلمات بسيطة بكل تصنيف؛ يُستخدم ردّ النموذج نفسه لاختيار تصنيف إيموجي مناسب بدل العشوائي الكامل.
CATEGORY_KEYWORDS = {
    "نجاح": ["تم", "نجح", "ممتاز", "رائع", "أحسنت", "جاهز"],
    "ترحيب": ["أهلاً", "مرحبا", "هلا", "حياك"],
    "تفكير": ["أعتقد", "ربما", "دعني أفكر", "لست متأكداً"],
    "تحذير": ["تحذير", "خطأ", "تعذّر", "لا يمكن", "⚠️"],
    "حب": ["أحبك", "يسعدني", "سعيد"],
}


def category_for_text(text: str) -> str:
    for cat, words in CATEGORY_KEYWORDS.items():
        if any(w in text for w in words):
            return cat
    return "عام"


def pick_for_category(category: str) -> dict:
    """يختار إيموجي من نفس التصنيف إن وُجد، وإلا من تصنيف «عام»، وإلا أي إيموجي محفوظ."""
    data = load()
    if not data:
        return None
    import random
    same = [v for v in data.values() if v.get("category") == category]
    general = [v for v in data.values() if v.get("category", "عام") == "عام"]
    pool = same or general or list(data.values())
    return random.choice(pool)


def pick_for_reply() -> dict:
    """يختار إيموجي مميزاً عشوائياً من المخزَّن ليزيّن به رد المستخدم العادي (وليس لوحة تحكم الأدمن)."""
    data = load()
    if not data:
        return None
    import random
    return random.choice(list(data.values()))


def _utf16_len(s: str) -> int:
    return len(s.encode("utf-16-le")) // 2


def entity_for(text: str, emoji: dict, at: int = 0) -> dict:
    """يبني كائن entity صالح لإرساله ضمن sendMessage. `at` هنا هو موضع الإيموجي بوحدات UTF-16 داخل `text`
    (استخدم utf16_offset_of إن كنت تحسبه من نص بايثون عادي)."""
    return {"type": "custom_emoji", "offset": at, "length": _utf16_len(emoji["char"]), "custom_emoji_id": emoji["id"]}


def utf16_offset_of(text: str, py_index: int) -> int:
    """يحوّل فهرساً بوحدات بايثون (محارف) إلى موضع مكافئ بوحدات UTF-16، لبناء entity صحيح."""
    return _utf16_len(text[:py_index])

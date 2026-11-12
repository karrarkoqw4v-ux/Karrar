"""اختبارات حقيقية بلا شبكة (باستثناء المذكور صراحة) لمنطق البوت: استخراج الإيموجي المميز، وفصل الأدمن عن المستخدم."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import premium_emoji  # noqa: E402


def _entity_for_substring(text: str, char: str, which: int = 0) -> dict:
    """يبني entity اختبار صحيح بوحدات UTF-16 لموضع `char` رقم `which` داخل `text` (لاستعماله كبيانات اختبار واقعية)."""
    idx = -1
    for _ in range(which + 1):
        idx = text.index(char, idx + 1)
    offset = premium_emoji.utf16_offset_of(text, idx)
    length = premium_emoji._utf16_len(char)
    return {"type": "custom_emoji", "offset": offset, "length": length, "custom_emoji_id": "5368324170671202286"}


def test_extract_from_message_real_shape():
    text = "مرحباً 🔥 كيف الحال؟"
    msg = {"text": text, "entities": [_entity_for_substring(text, "🔥")]}
    out = premium_emoji.extract_from_message(msg)
    assert out == [{"id": "5368324170671202286", "char": "🔥"}]


def test_extract_with_preceding_supplementary_emoji():
    """الحالة التي تكسر الفهرسة الساذجة بمحارف بايثون: إيموجي عادي (زوج surrogate) قبل الإيموجي المميز."""
    text = "😀😀 مرحباً 🔥 أهلاً"
    msg = {"text": text, "entities": [_entity_for_substring(text, "🔥")]}
    out = premium_emoji.extract_from_message(msg)
    assert out == [{"id": "5368324170671202286", "char": "🔥"}]


def test_extract_ignores_non_custom_entities():
    msg = {"text": "Hello world", "entities": [{"type": "bold", "offset": 0, "length": 5}]}
    assert premium_emoji.extract_from_message(msg) == []


def test_extract_from_caption():
    text = "صورة ⭐ جميلة"
    msg = {"caption": text, "caption_entities": [dict(_entity_for_substring(text, "⭐"), custom_emoji_id="999")]}
    out = premium_emoji.extract_from_message(msg)
    assert out == [{"id": "999", "char": "⭐"}]


def test_add_and_pick(tmp_path, monkeypatch):
    import config
    monkeypatch.setattr(config, "PREMIUM_EMOJI_FILE", str(tmp_path / "moji.json"))
    assert premium_emoji.load() == {}
    premium_emoji.add("fire", "123", "🔥", category="نجاح")
    data = premium_emoji.load()
    assert data == {"fire": {"id": "123", "char": "🔥", "category": "نجاح"}}
    picked = premium_emoji.pick_for_category("نجاح")
    assert picked["id"] == "123" and picked["char"] == "🔥"


def test_category_for_text_and_fallback():
    assert premium_emoji.category_for_text("تم بنجاح، ممتاز") == "نجاح"
    assert premium_emoji.category_for_text("نص عادي بلا كلمات مفتاحية") == "عام"


def test_pick_for_category_falls_back_to_general(tmp_path, monkeypatch):
    import config
    monkeypatch.setattr(config, "PREMIUM_EMOJI_FILE", str(tmp_path / "moji2.json"))
    premium_emoji.add("smile", "456", "😊", category="عام")
    # لا يوجد إيموجي بتصنيف "ترحيب"، يجب أن يرجع من "عام" بدل None
    picked = premium_emoji.pick_for_category("ترحيب")
    assert picked["id"] == "456"


def test_entity_for_matches_offset_with_preceding_emoji():
    """entity_for يجب يحسب length بوحدات UTF-16 صح حتى عندما يسبق الإيموجي المميز إيموجي عادي."""
    text = "😀 هلا 🔥"
    at = premium_emoji.utf16_offset_of(text, text.index("🔥"))
    e = premium_emoji.entity_for(text, {"id": "1", "char": "🔥"}, at=at)
    # تحقّق أن القطع بموضع e يعيد فعلاً نفس الحرف
    assert premium_emoji._utf16_slice(text, e["offset"], e["length"]) == "🔥"
    assert e["custom_emoji_id"] == "1"


def test_decorate_roundtrip_via_bot():
    """يتأكد أن bot.decorate تنتج entity يُشير فعلاً لحرف الإيموجي الصحيح في النص النهائي، مهما كان الرد."""
    import bot
    import config
    import tempfile
    with tempfile.TemporaryDirectory() as d:
        config.PREMIUM_EMOJI_FILE = os.path.join(d, "moji.json")
        premium_emoji.add("star", "42", "🔥")
        full, entities = bot.decorate("😀 هذا رد تجريبي")
        assert entities and len(entities) == 1
        e = entities[0]
        assert premium_emoji._utf16_slice(full, e["offset"], e["length"]) == "🔥"


def test_admin_vs_user_routing():
    import config
    assert config.ADMIN_ID == 8010837412
    admin_chat, user_chat = config.ADMIN_ID, 123456789
    assert admin_chat != user_chat

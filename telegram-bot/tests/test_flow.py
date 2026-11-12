"""اختبار تكاملي لمنطق bot.py كاملاً (الأزرار، تغيّر الوضع، جمع الإيموجي)، بتزييف telegram_api فقط
(لا شبكة)، للتأكد أن الحالة تتحرك صح بين الأزرار بلا أي أمر مكتوب."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import config  # noqa: E402

ADMIN = config.ADMIN_ID
USER = 555


def _fresh(tmp_path, monkeypatch):
    monkeypatch.setattr(config, "STATE_FILE", str(tmp_path / "state.json"))
    monkeypatch.setattr(config, "PREMIUM_EMOJI_FILE", str(tmp_path / "moji.json"))
    import importlib
    import state as state_mod
    importlib.reload(state_mod)
    import bot
    importlib.reload(bot)
    sent = []
    monkeypatch.setattr(bot.telegram_api, "send_message", lambda *a, **k: sent.append(("msg", a, k)) or {"message_id": 1})
    monkeypatch.setattr(bot.telegram_api, "send_photo_bytes", lambda *a, **k: sent.append(("photo", a, k)))
    monkeypatch.setattr(bot, "ask_default", lambda hist: "رد تجريبي من الذكاء")
    return bot, state_mod.State(), sent


def test_admin_button_only_for_admin(tmp_path, monkeypatch):
    bot, st, sent = _fresh(tmp_path, monkeypatch)
    import keyboards as kb
    bot.handle_text(st, USER, kb.BTN_ADMIN)  # مستخدم عادي أصلاً ما يشوف هذا الزر؛ النص نفسه يذهب كسؤال عادي
    assert len(sent) == 1 and "رد تجريبي" in sent[-1][1][1]  # ردّ ذكاء عادي، وليست لوحة تحكم
    sent.clear()
    bot.handle_text(st, ADMIN, kb.BTN_ADMIN)
    assert sent and "لوحة التحكم" in sent[-1][1][1]


def test_image_flow_end_to_end(tmp_path, monkeypatch):
    bot, st, sent = _fresh(tmp_path, monkeypatch)
    import keyboards as kb
    monkeypatch.setattr(bot, "image_gen", type("X", (), {"generate": staticmethod(lambda t: b"FAKEJPEGDATA")}))
    bot.handle_text(st, USER, kb.BTN_IMAGE)
    assert st.current_mode(USER) == "awaiting_image_prompt"
    bot.handle_text(st, USER, "قطة بيضاء تلعب")
    assert st.current_mode(USER) == "idle"
    assert any(s[0] == "photo" for s in sent)


def test_normal_chat_no_commands_needed(tmp_path, monkeypatch):
    bot, st, sent = _fresh(tmp_path, monkeypatch)
    bot.handle_text(st, USER, "مرحبا كيف حالك")
    assert st.current_mode(USER) == "idle"
    last = sent[-1]
    assert "رد تجريبي" in last[1][1]  # النص المُرسَل يحتوي رد النموذج


def test_emoji_collect_flow(tmp_path, monkeypatch):
    bot, st, sent = _fresh(tmp_path, monkeypatch)
    cb_answers = []
    monkeypatch.setattr(bot.telegram_api, "answer_callback", lambda *a, **k: cb_answers.append(a))
    cq = {"id": "1", "data": "moji_target:3", "message": {"chat": {"id": ADMIN}, "message_id": 10}}
    bot.handle_callback(st, cq)
    assert st.current_mode(ADMIN) == "collecting_emoji"
    assert st.get_mode(ADMIN)["target"] == 3
    msg1 = {"text": "🔥", "entities": [{"type": "custom_emoji", "offset": 0, "length": 2, "custom_emoji_id": "111"}]}
    assert bot.handle_emoji_message(st, ADMIN, msg1) is True
    assert st.get_mode(ADMIN)["got"] == 1
    for cid in ("222", "333"):
        msg = {"text": "⭐", "entities": [{"type": "custom_emoji", "offset": 0, "length": 2, "custom_emoji_id": cid}]}
        bot.handle_emoji_message(st, ADMIN, msg)
    assert st.current_mode(ADMIN) == "idle"  # رجع idle بعد ما وصل الهدف (3)
    import premium_emoji
    assert len(premium_emoji.load()) == 3


def test_admin_replies_have_no_premium_emoji_but_user_can(tmp_path, monkeypatch):
    bot, st, sent = _fresh(tmp_path, monkeypatch)
    import premium_emoji
    premium_emoji.add("fire", "1", "🔥", category="عام")
    bot.handle_text(st, ADMIN, "مرحبا")
    admin_text = sent[-1][1][1]
    assert "🔥" not in admin_text
    sent.clear()
    bot.handle_text(st, USER, "مرحبا")
    user_text = sent[-1][1][1]
    assert "🔥" in user_text

"""اختبار تكاملي لمنطق bot.py كاملاً (الأزرار، تغيّر الوضع، جمع الإيموجي)، بتزييف telegram_api فقط
(لا شبكة)، للتأكد أن الحالة تتحرك صح بين الأزرار بلا أي أمر مكتوب."""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import config  # noqa: E402

ADMIN = config.ADMIN_ID
USER = 555


def _last_text(sent):
    """يستخرج نص آخر رسالة بصرف النظر عن كونها send_message (args=(chat,text)) أو edit_message_text (args=(chat,msg_id,text))."""
    kind, a, _ = sent[-1]
    return a[1] if kind == "msg" else a[2]


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
    monkeypatch.setattr(bot.telegram_api, "edit_message_text", lambda *a, **k: sent.append(("edit", a, k)))
    monkeypatch.setattr(bot.telegram_api, "send_chat_action", lambda *a, **k: None)
    monkeypatch.setattr(bot.telegram_api, "answer_callback", lambda *a, **k: None)
    monkeypatch.setattr(bot, "ask_default", lambda hist: "رد تجريبي من الذكاء")
    st = state_mod.State()
    # الرسالة الإلزامية (شروط الاستخدام) تُعتبر مقبولة مسبقاً بهذي الاختبارات، لاختبار بقية التدفق بمعزل عنها
    st.touch_user(ADMIN, {"first_name": "Admin"})
    st.touch_user(USER, {"first_name": "User"})
    st.set_agreed(ADMIN)
    st.set_agreed(USER)
    sent.clear()
    return bot, st, sent


def test_admin_button_only_for_admin(tmp_path, monkeypatch):
    bot, st, sent = _fresh(tmp_path, monkeypatch)
    import keyboards as kb
    bot.handle_text(st, USER, kb.BTN_ADMIN)  # مستخدم عادي أصلاً ما يشوف هذا الزر؛ النص نفسه يذهب كسؤال عادي
    assert "رد تجريبي" in _last_text(sent)  # ردّ ذكاء عادي (بعد رسالة "جارٍ التفكير")، وليست لوحة تحكم
    sent.clear()
    bot.handle_text(st, ADMIN, kb.BTN_ADMIN)
    assert sent and "لوحة التحكم" in _last_text(sent)


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
    assert "رد تجريبي" in _last_text(sent)  # النص النهائي يحتوي رد النموذج


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


def test_premium_emoji_shows_for_admin_and_user_alike(tmp_path, monkeypatch):
    """الأدمن عنده تيليجرام بريميوم فعلاً، فيشوف الإيموجي المميز متل أي مستخدم عادي؛ لوحة التحكم (أزرار/إحصائيات) تبقى نصاً عادياً لأنها رسائل نظام وليست ردود ذكاء."""
    bot, st, sent = _fresh(tmp_path, monkeypatch)
    import premium_emoji
    premium_emoji.add("fire", "1", "🔥", category="عام")
    bot.handle_text(st, ADMIN, "مرحبا")
    assert "🔥" in _last_text(sent)
    sent.clear()
    bot.handle_text(st, USER, "مرحبا")
    assert "🔥" in _last_text(sent)


def test_add_api_flow_sets_provider_and_url_automatically(tmp_path, monkeypatch):
    bot, st, sent = _fresh(tmp_path, monkeypatch)
    import keyboards as kb
    import provider_store
    monkeypatch.setattr(provider_store, "STORE_FILE", str(tmp_path / "provider.json"))

    bot.handle_text(st, ADMIN, kb.BTN_ADMIN)
    bot.handle_text(st, ADMIN, kb.BTN_ADD_API)
    assert sent[-1][1][1].startswith("اختر نوع المزوّد")

    cq = {"id": "1", "data": "provider:Groq", "message": {"chat": {"id": ADMIN}, "message_id": 5}}
    answers = []
    monkeypatch.setattr(bot.telegram_api, "answer_callback", lambda *a, **k: answers.append(a))
    bot.handle_callback(st, cq)
    assert st.current_mode(ADMIN) == "awaiting_api_key"
    assert st.get_mode(ADMIN)["provider"] == "Groq"

    bot.handle_text(st, ADMIN, "gsk_fake_test_key_123")
    assert st.current_mode(ADMIN) == "idle"
    saved = provider_store.load()
    assert saved["name"] == "Groq"
    assert saved["base_url"] == "https://api.groq.com/openai/v1"  # تم ضبطه تلقائياً من اسم المزوّد فقط
    assert saved["api_key"] == "gsk_fake_test_key_123"
    assert saved["model"] == "llama-3.3-70b-versatile"

    cq2 = {"id": "2", "data": "setmodel:openai/gpt-oss-20b", "message": {"chat": {"id": ADMIN}, "message_id": 6}}
    bot.handle_callback(st, cq2)
    assert provider_store.load()["model"] == "openai/gpt-oss-20b"
    # الـ api_key يبقى محفوظاً بعد تغيير النموذج فقط
    assert provider_store.load()["api_key"] == "gsk_fake_test_key_123"


def test_active_provider_used_by_ai_provider(tmp_path, monkeypatch):
    bot, st, sent = _fresh(tmp_path, monkeypatch)
    import provider_store
    monkeypatch.setattr(provider_store, "STORE_FILE", str(tmp_path / "provider2.json"))
    assert provider_store.load() == {}
    provider_store.save("DeepSeek", "https://api.deepseek.com", "sk-test", "deepseek-chat")
    active = provider_store.active()
    assert active["base_url"] == "https://api.deepseek.com" and active["api_key"] == "sk-test"


def test_button_icon_custom_emoji_id_bot_api_9_4(tmp_path, monkeypatch):
    """يتأكد أن أزرار القائمة تحمل icon_custom_emoji_id من تصنيف «أزرار» تحديداً عند توفّره، وبلا كسر عند غيابه."""
    import keyboards as kb
    import config
    import premium_emoji
    monkeypatch.setattr(config, "PREMIUM_EMOJI_FILE", str(tmp_path / "moji.json"))

    menu_no_emoji = kb.main_menu(True)
    admin_btn = menu_no_emoji["keyboard"][-1][0]
    assert "icon_custom_emoji_id" not in admin_btn and admin_btn["text"] == kb.BTN_ADMIN

    premium_emoji.add("check", "777", "✅", category="أزرار")
    menu_with_emoji = kb.main_menu(True)
    admin_btn2 = menu_with_emoji["keyboard"][-1][0]
    assert admin_btn2["icon_custom_emoji_id"] == "777"


def test_mandatory_gate_blocks_until_agreed(tmp_path, monkeypatch):
    """لا يصير شيء (ولا حتى محادثة عادية) قبل ما يضغط المستخدم زر الموافقة، ويظهر اسمه وآيديه وحالة بريميومه."""
    bot, st, sent = _fresh(tmp_path, monkeypatch)
    import state as state_mod
    fresh_st = state_mod.State()  # مستخدم جديد تماماً لم يوافق بعد
    NEW_USER = 777
    bot.handle_text(fresh_st, NEW_USER, "مرحبا", {"first_name": "سعد", "is_premium": True})
    assert not fresh_st.is_agreed(NEW_USER)
    text = sent[-1][1][1]
    assert "سعد" in text and str(NEW_USER) in text and "تيليجرام بريميوم الآن: نعم" in text
    assert "رد تجريبي" not in text  # ما وصل الذكاء الاصطناعي إطلاقاً

    cq = {"id": "1", "data": "agree_terms", "message": {"chat": {"id": NEW_USER}, "message_id": 9}}
    bot.handle_callback(fresh_st, cq)
    assert fresh_st.is_agreed(NEW_USER)
    sent.clear()
    bot.handle_text(fresh_st, NEW_USER, "مرحبا", {"first_name": "سعد"})
    assert "رد تجريبي" in _last_text(sent)


def test_settings_button_shows_profile_and_stats(tmp_path, monkeypatch):
    bot, st, sent = _fresh(tmp_path, monkeypatch)
    import keyboards as kb
    monkeypatch.setattr(bot, "image_gen", type("X", (), {"generate": staticmethod(lambda t: b"FAKE")}))
    bot.handle_text(st, USER, kb.BTN_IMAGE)
    bot.handle_text(st, USER, "قطة")  # ينشئ صورة واحدة فعلياً
    sent.clear()
    bot.handle_text(st, USER, kb.BTN_SETTINGS)
    text = sent[-1][1][1]
    assert "User" in text
    assert str(USER) in text
    assert "صور أنشأتها: 1" in text
    assert "فيديوهات: 0 (الميزة قيد التطوير)" in text


def test_under_development_buttons_are_honest(tmp_path, monkeypatch):
    bot, st, sent = _fresh(tmp_path, monkeypatch)
    import keyboards as kb
    for btn in (kb.BTN_VIDEO, kb.BTN_GAME, kb.BTN_APPS, kb.BTN_PRIVATE):
        sent.clear()
        bot.handle_text(st, USER, btn)
        assert "قيد التطوير" in sent[-1][1][1]


def test_thinking_message_gets_edited_to_final_reply(tmp_path, monkeypatch):
    """رسالة «جارٍ التفكير» تُرسَل أولاً، وتُستبدَل (edit) بالرد النهائي، بدل رسالتين منفصلتين."""
    bot, st, sent = _fresh(tmp_path, monkeypatch)
    bot.handle_text(st, USER, "كيف حالك")
    kinds = [s[0] for s in sent]
    assert kinds[0] == "msg" and "جارٍ التفكير" in sent[0][1][1]
    assert "edit" in kinds
    edit_call = [s for s in sent if s[0] == "edit"][-1]
    assert "رد تجريبي" in edit_call[1][2]


def test_seed_emoji_counts_match_what_admin_sent():
    import seed_emoji
    assert len(seed_emoji.SECTION_1_TEXT) == 90
    assert len(seed_emoji.SECTION_2_BUTTONS) == 60
    assert len(set(seed_emoji.SECTION_1_TEXT)) == 90  # بلا تكرار
    assert len(set(seed_emoji.SECTION_2_BUTTONS)) == 60
    assert not (set(seed_emoji.SECTION_1_TEXT) & set(seed_emoji.SECTION_2_BUTTONS))


def test_seed_emoji_import_is_idempotent(tmp_path, monkeypatch):
    import config
    import premium_emoji
    import seed_emoji
    import importlib
    monkeypatch.setattr(config, "PREMIUM_EMOJI_FILE", str(tmp_path / "moji.json"))
    importlib.reload(premium_emoji)
    n1, n2 = seed_emoji.import_all()
    assert n1 == 90 and n2 == 60
    n1b, n2b = seed_emoji.import_all()  # تشغيل ثانٍ يجب ألا يكرّر شيئاً
    assert n1b == 0 and n2b == 0
    assert len(premium_emoji.load()) == 150

"""بوت تيليجرام «بوصلة»: كل شيء أزرار، صفر أوامر مكتوبة (ما عدا /start الذي يفرضه تيليجرام نفسه كمدخل أول).
لوحة الأدمن (ID محدَّد) تظهر كأزرار إضافية فوق نفس قائمة المستخدم العادي، وليست منفصلة.

تشغيل:  python bot.py
"""
import logging
import time

import config
import image_gen
import keyboards as kb
import models_provider
import premium_emoji
import telegram_api
from ai_provider import ask as ask_default
from models_provider import ask_openrouter
from state import State

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("bawsala-bot")

ABOUT = (
    "بوصلة\nمساعد ذكاء اصطناعي، تقدر:\n"
    "تحادثني مباشرة بالكتابة، تولّد صوراً، تختار من بين نماذج ذكاء مفتوحة مجانية متعددة"
)

TERMS_TEXT = (
    "هذه رسالة إلزامية لازم توافق عليها قبل الاستخدام:\n"
    "— ردود الذكاء الاصطناعي قد تحتوي أخطاء؛ تحقّق من المهم منها بنفسك.\n"
    "— لا تستخدم البوت لأذى أحد أو لأي شيء غير قانوني.\n"
    "— رسائلك تُرسَل لمزوّد النموذج الذي يختاره الأدمن لتوليد الرد.\n"
)


def is_admin(chat_id: int) -> bool:
    return chat_id == config.ADMIN_ID


def decorate(text: str, category: str = None) -> tuple:
    """يضيف إيموجي مميزاً مناسباً (حسب التصنيف إن حُدِّد، وإلا حسب كلمات النص). يرجّع النص خالياً إن لم يوجد أي إيموجي محفوظ."""
    cat = category or premium_emoji.category_for_text(text)
    e = premium_emoji.pick_for_category(cat)
    if not e:
        return text, None
    full = text + " " + e["char"]
    at = premium_emoji.utf16_offset_of(full, len(full) - len(e["char"]))
    return full, [premium_emoji.entity_for(full, e, at=at)]


def send_decorated(chat_id: int, text: str, category: str = None, reply_markup: dict = None):
    final_text, entities = decorate(text, category)
    telegram_api.send_message(chat_id, final_text, entities=entities, reply_markup=reply_markup)


def openrouter_key() -> str:
    """يبحث عن مفتاح OpenRouter الحقيقي: أولاً المزوّد الذي ضبطه الأدمن بزر «إضافة API» (لو اختار OpenRouter
    تحديداً)، وإلا متغيّر OPENROUTER_API_KEY بملف .env. هذا الربط كان ناقصاً سابقاً وسبّب فشل زر «اختيار نموذج
    الذكاء» بصمت (كان دايماً يقرأ .env الفاضي حتى لو ضبط الأدمن مفتاحاً بالأزرار)."""
    import provider_store
    p = provider_store.load()
    if p.get("name") == "OpenRouter" and p.get("api_key"):
        return p["api_key"]
    return config.OPENROUTER_API_KEY


def answer_ai(state: State, chat_id: int, text: str) -> str:
    history = state.history(chat_id)
    history.append({"role": "user", "content": text})
    model = state.get_model(chat_id)
    if model:
        reply = ask_openrouter(openrouter_key(), model, history[-12:], config.SYSTEM_PROMPT)
    else:
        reply = ask_default(history[-12:])
    history.append({"role": "assistant", "content": reply})
    state.save_history(chat_id, history)
    return reply


def send_main(chat_id: int):
    send_decorated(chat_id, ABOUT, "عام", reply_markup=kb.main_menu(is_admin(chat_id)))


def gate_text(chat_id: int, state: State) -> str:
    p = state.profile(chat_id)
    premium_line = "نعم" if p.get("is_premium") else "لا"
    lines = [f"الاسم: {p.get('name', '')}"]
    if p.get("username"):
        lines.append(f"اسم المستخدم: @{p['username']}")
    lines.append(f"آيدي تيليجرام: {chat_id}")
    # صدق: تيليجرام لا يعطي أي بوت "مدة" اشتراك بريميوم إطلاقاً، فقط هل هو مفعَّل الآن (is_premium) أم لا. لا يوجد حقل مدة.
    lines.append(f"تيليجرام بريميوم الآن: {premium_line}")
    lines.append("")
    lines.append(TERMS_TEXT)
    return "\n".join(lines)


def handle_text(state: State, chat_id: int, text: str, from_user: dict = None):
    state.touch_user(chat_id, from_user)
    mode = state.current_mode(chat_id)

    if text == "/start":
        state.set_mode(chat_id, "idle")
        if not state.is_agreed(chat_id):
            send_decorated(chat_id, gate_text(chat_id, state), "عام", reply_markup=kb.agree_gate_inline())
            return
        send_main(chat_id)
        return

    if not state.is_agreed(chat_id):
        send_decorated(chat_id, gate_text(chat_id, state), "عام", reply_markup=kb.agree_gate_inline())
        return

    if text == kb.BTN_CANCEL:
        state.set_mode(chat_id, "idle")
        telegram_api.send_message(chat_id, "ألغيت.", reply_markup=kb.admin_menu() if is_admin(chat_id) else kb.main_menu(False))
        return

    if text == kb.BTN_IMAGE:
        state.set_mode(chat_id, "awaiting_image_prompt")
        send_decorated(chat_id, "اكتب وصف الصورة اللي تريدها.", "عام", reply_markup=kb.cancel_menu())
        return

    if text in kb.UNDER_DEV_BUTTONS:
        send_decorated(chat_id, "قيد التطوير حالياً، غير متاحة بعد.", "تحذير", reply_markup=kb.main_menu(is_admin(chat_id)))
        return

    if text == kb.BTN_SETTINGS:
        p = state.profile(chat_id)
        stats = p.get("stats", {})
        premium_line = "نعم" if p.get("is_premium") else "لا"
        lines = [
            f"الاسم: {p.get('name', '')}",
            f"اسم المستخدم: @{p['username']}" if p.get("username") else "",
            f"آيدي تيليجرام: {chat_id}",
            f"تيليجرام بريميوم: {premium_line}",
            "",
            "استخدامك:",
            f"صور أنشأتها: {stats.get('images', 0)}",
            f"فيديوهات: {stats.get('videos', 0)} (الميزة قيد التطوير)",
            f"ألعاب: {stats.get('games', 0)} (الميزة قيد التطوير)",
            f"تطبيقات: {stats.get('apps', 0)} (الميزة قيد التطوير)",
            "",
            "الملفات والأكواد اللي تُنشأ لك تُحفظ هنا بنفس حسابك (آيدي التيليجرام أعلاه)، ولا يراها أحد غيرك والأدمن.",
        ]
        send_decorated(chat_id, "\n".join(l for l in lines if l), "عام", reply_markup=kb.main_menu(is_admin(chat_id)))
        return

    if text == kb.BTN_MODEL:
        if not openrouter_key():
            send_decorated(chat_id, "هذا الزر يحتاج مفتاح OpenRouter حقيقياً أولاً (يبدأ بـ sk-or-v1-...، من openrouter.ai/keys). اطلب من الأدمن يضبطه من زر «إضافة API» ← OpenRouter.", "تحذير", reply_markup=kb.main_menu(is_admin(chat_id)))
            return
        models = models_provider.free_models()
        if not models:
            send_decorated(chat_id, "تعذّر جلب قائمة النماذج الآن، جرّب لاحقاً.", "تحذير")
            return
        send_decorated(chat_id, f"اختر نموذجاً ({len(models)} متاح الآن، القائمة حيّة وتتغيّر):", "تفكير", reply_markup=kb.model_picker_inline(models))
        return

    if text == kb.BTN_ABOUT:
        send_decorated(chat_id, ABOUT, "عام", reply_markup=kb.main_menu(is_admin(chat_id)))
        return

    if text == kb.BTN_ADMIN and is_admin(chat_id):
        send_decorated(chat_id, "لوحة التحكم:", "نجاح", reply_markup=kb.admin_menu())
        return

    if text == kb.BTN_BACK:
        state.set_mode(chat_id, "idle")
        send_main(chat_id)
        return

    if is_admin(chat_id) and text == kb.BTN_STATS:
        send_decorated(chat_id, f"المستخدمون: {state.user_count()}\nيشتغل منذ: {state.uptime()}\nإيموجيات محفوظة: {len(premium_emoji.load())}", "نجاح", reply_markup=kb.admin_menu())
        return

    if is_admin(chat_id) and text == kb.BTN_BROADCAST:
        state.set_mode(chat_id, "awaiting_broadcast")
        send_decorated(chat_id, "اكتب رسالة البث.", "عام", reply_markup=kb.cancel_menu())
        return

    if is_admin(chat_id) and text == kb.BTN_EMOJI:
        send_decorated(chat_id, "كم عدد الإيموجيات المميزة اللي تريد تضيفها؟", "عام", reply_markup=kb.emoji_count_inline())
        return

    if is_admin(chat_id) and text == kb.BTN_ADD_API:
        send_decorated(chat_id, "اختر نوع المزوّد؛ رابط API يُضبط تلقائياً حسب اختيارك:", "عام", reply_markup=kb.provider_picker_inline())
        return

    if is_admin(chat_id) and text == kb.BTN_LISTMOJI:
        data = premium_emoji.load()
        if not data:
            send_decorated(chat_id, "لا يوجد إيموجي محفوظ بعد.", "تحذير", reply_markup=kb.admin_menu())
            return
        by_cat = {}
        for v in data.values():
            by_cat.setdefault(v.get("category", "عام"), []).append(v["char"])
        lines = [f"{c}: {len(chars)} إيموجي" for c, chars in by_cat.items()]
        send_decorated(chat_id, "\n".join(lines), "نجاح", reply_markup=kb.admin_menu())
        return

    # ---- حسب الوضع الحالي ----
    if mode == "awaiting_image_prompt":
        send_decorated(chat_id, "جارٍ الرسم...", "تفكير")
        img = image_gen.generate(text)
        state.set_mode(chat_id, "idle")
        if img:
            telegram_api.send_photo_bytes(chat_id, img, caption=text[:200])
            state.bump_stat(chat_id, "images")
            send_decorated(chat_id, "تم.", "نجاح", reply_markup=kb.main_menu(is_admin(chat_id)))
        else:
            send_decorated(chat_id, "تعذّر توليد الصورة، جرّب وصفاً آخر.", "تحذير", reply_markup=kb.main_menu(is_admin(chat_id)))
        return

    if mode == "awaiting_api_key" and is_admin(chat_id):
        provider_name = mdata_get(state, chat_id, "provider")
        p = __import__("providers").by_name(provider_name)
        if not p:
            state.set_mode(chat_id, "idle")
            send_decorated(chat_id, "صار خطأ، أعد المحاولة من زر إضافة API.", "تحذير", reply_markup=kb.admin_menu())
            return
        _, base_url, default_model, _ = p
        providers_mod = __import__("providers")
        if providers_mod.key_looks_wrong(provider_name, text):
            send_decorated(chat_id, f"هذا المفتاح شكله مو مفتاح {provider_name} (المفاتيح الصحيحة تبدأ بـ «{providers_mod.KEY_PREFIXES[provider_name]}»). تأكد إنك نسخت المفتاح الصحيح من موقع {provider_name} نفسه وأرسله من جديد، أو اضغط إلغاء.", "تحذير", reply_markup=kb.cancel_menu())
            return
        import provider_store
        provider_store.save(provider_name, base_url, text.strip(), default_model)
        state.set_mode(chat_id, "idle")
        send_decorated(chat_id, f"تم ضبط {provider_name}\nالرابط: {base_url}\nالنموذج الافتراضي: {default_model}\n\nتريد تحديد نموذج آخر من نفس المزوّد؟", "نجاح", reply_markup=kb.provider_model_inline(provider_name))
        return

    if mode == "awaiting_broadcast" and is_admin(chat_id):
        sent = 0
        for uid in state.all_users():
            try:
                telegram_api.send_message(uid, text)
                sent += 1
            except Exception:
                pass
        state.set_mode(chat_id, "idle")
        send_decorated(chat_id, f"أُرسلت لـ {sent} مستخدماً.", "نجاح", reply_markup=kb.admin_menu())
        return

    # ---- محادثة عادية مع الذكاء الاصطناعي: رسالة "جاري التفكير" مؤقتة تُستبدَل بالرد النهائي ----
    try:
        telegram_api.send_chat_action(chat_id, "typing")
    except Exception:
        pass
    thinking_text, thinking_ent = decorate("جارٍ التفكير...", "تفكير")
    sent_msg = telegram_api.send_message(chat_id, thinking_text, entities=thinking_ent)
    reply = answer_ai(state, chat_id, text)
    final_text, entities = decorate(reply)
    try:
        telegram_api.edit_message_text(chat_id, sent_msg["message_id"], final_text, entities=entities)
    except Exception:
        telegram_api.send_message(chat_id, final_text, entities=entities)


def mdata_get(state, chat_id, key):
    return state.get_mode(chat_id).get(key)


def handle_emoji_message(state: State, chat_id: int, msg: dict):
    """يُستدعى بالتوازي مع handle_text عندما يكون الأدمن بوضع جمع الإيموجي، للرسائل المحوَّلة أو المرسَلة."""
    mdata = state.get_mode(chat_id)
    target = mdata.get("target", 0)
    got = mdata.get("got", 0)
    found = premium_emoji.extract_from_message(msg)
    if not found:
        return False
    for f in found:
        got += 1
        premium_emoji.add(f"emoji{got}", f["id"], f["char"], category="عام")
    state.set_mode(chat_id, "collecting_emoji", target=target, got=got)
    if got >= target:
        state.set_mode(chat_id, "idle")
        send_decorated(chat_id, f"تم! حُفظ {got} إيموجي مميزاً.", "نجاح", reply_markup=kb.admin_menu())
    else:
        send_decorated(chat_id, f"{got}/{target} حتى الآن. استمر بإرسال/تحويل المزيد، أو اضغط إلغاء.", "عام")
    return True


def handle_callback(state: State, cq: dict):
    data = cq.get("data", "")
    chat_id = cq["message"]["chat"]["id"]
    msg_id = cq["message"]["message_id"]
    if data.startswith("modelpage:"):
        page = int(data.split(":")[1])
        models = models_provider.free_models()
        telegram_api.edit_message_reply_markup(chat_id, msg_id, kb.model_picker_inline(models, page))
        telegram_api.answer_callback(cq["id"])
    elif data.startswith("model:"):
        model = data.split(":", 1)[1]
        state.set_model(chat_id, model)
        telegram_api.answer_callback(cq["id"], f"تم اختيار {model.split('/')[-1]}")
        send_decorated(chat_id, f"من الآن تحادث: {model}", "نجاح", reply_markup=kb.main_menu(is_admin(chat_id)))
    elif data.startswith("provider:") and is_admin(chat_id):
        provider_name = data.split(":", 1)[1]
        state.set_mode(chat_id, "awaiting_api_key", provider=provider_name)
        telegram_api.answer_callback(cq["id"])
        send_decorated(chat_id, f"أرسل الآن مفتاح API الخاص بـ {provider_name}.", "عام", reply_markup=kb.cancel_menu())
    elif data.startswith("setmodel:") and is_admin(chat_id):
        model = data.split(":", 1)[1]
        import provider_store
        provider_store.set_model(model)
        telegram_api.answer_callback(cq["id"], "تم")
        send_decorated(chat_id, f"النموذج الفعّال الآن: {model}", "نجاح", reply_markup=kb.admin_menu())
    elif data.startswith("moji_target:") and is_admin(chat_id):
        target = int(data.split(":")[1])
        state.set_mode(chat_id, "collecting_emoji", target=target, got=0)
        telegram_api.answer_callback(cq["id"])
        send_decorated(chat_id, f"حوّل أو أرسل {target} رسالة فيها إيموجي مميز واحد (أو أكثر بكل رسالة)، وأنا أحفظها تلقائياً.", "عام", reply_markup=kb.cancel_menu())
    elif data == "agree_terms":
        state.set_agreed(chat_id)
        telegram_api.answer_callback(cq["id"], "تم")
        send_main(chat_id)
    else:
        telegram_api.answer_callback(cq["id"])


def main():
    if not config.BOT_TOKEN:
        raise SystemExit("ضع BOT_TOKEN في ملف .env أولاً (انسخ من .env.example).")
    me = telegram_api.call("getMe")
    log.info("بدأ البوت: @%s", me.get("username"))
    telegram_api.set_my_commands([{"command": "start", "description": "بدء المحادثة"}])
    import seed_emoji
    n1, n2 = seed_emoji.import_all()
    log.info("إيموجيات مميزة: أُضيف %s للنصوص و%s للأزرار (المجموع %s)", n1, n2, len(premium_emoji.load()))
    state = State()
    offset = 0
    while True:
        try:
            updates = telegram_api.get_updates(offset)
        except Exception as e:
            log.warning("getUpdates failed: %s", e)
            time.sleep(3)
            continue
        for u in updates:
            offset = u["update_id"] + 1
            try:
                if "callback_query" in u:
                    handle_callback(state, u["callback_query"])
                    continue
                msg = u.get("message")
                if not msg:
                    continue
                chat_id = msg["chat"]["id"]
                from_user = msg.get("from") or {}
                state.touch_user(chat_id, from_user)
                if is_admin(chat_id) and state.current_mode(chat_id) == "collecting_emoji":
                    if handle_emoji_message(state, chat_id, msg):
                        continue
                text = msg.get("text", "")
                if text:
                    handle_text(state, chat_id, text, from_user)
            except Exception as e:
                log.exception("handler error")
                try:
                    cid = (u.get("message") or {}).get("chat", {}).get("id") or (u.get("callback_query") or {}).get("message", {}).get("chat", {}).get("id")
                    if cid:
                        telegram_api.send_message(cid, f"⚠️ صار خطأ داخلي: {e}")
                except Exception:
                    pass


if __name__ == "__main__":
    main()

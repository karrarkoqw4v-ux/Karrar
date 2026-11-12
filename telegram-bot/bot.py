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
    "🧭 بوصلة\nمساعد ذكاء اصطناعي، تقدر:\n"
    "• تحادثني مباشرة بالكتابة\n• تولّد صوراً\n• تختار من بين نماذج ذكاء مفتوحة مجانية متعددة"
)


def is_admin(chat_id: int) -> bool:
    return chat_id == config.ADMIN_ID


def decorate(text: str) -> tuple:
    """يضيف إيموجي مميزاً مناسباً لنوع الرد (للمستخدم العادي فقط). يرجّع النص خالياً إن لم يوجد أي إيموجي محفوظ."""
    cat = premium_emoji.category_for_text(text)
    e = premium_emoji.pick_for_category(cat)
    if not e:
        return text, None
    full = text + " " + e["char"]
    at = premium_emoji.utf16_offset_of(full, len(full) - len(e["char"]))
    return full, [premium_emoji.entity_for(full, e, at=at)]


def answer_ai(state: State, chat_id: int, text: str) -> str:
    history = state.history(chat_id)
    history.append({"role": "user", "content": text})
    model = state.get_model(chat_id)
    if model:
        reply = ask_openrouter(config.OPENROUTER_API_KEY, model, history[-12:], config.SYSTEM_PROMPT)
    else:
        reply = ask_default(history[-12:])
    history.append({"role": "assistant", "content": reply})
    state.save_history(chat_id, history)
    return reply


def send_main(chat_id: int):
    telegram_api.send_message(chat_id, ABOUT, reply_markup=kb.main_menu(is_admin(chat_id)))


def handle_text(state: State, chat_id: int, text: str):
    state.touch_user(chat_id)
    mode = state.current_mode(chat_id)

    if text == "/start":
        state.set_mode(chat_id, "idle")
        send_main(chat_id)
        return

    if text == kb.BTN_CANCEL:
        state.set_mode(chat_id, "idle")
        telegram_api.send_message(chat_id, "ألغيت.", reply_markup=kb.admin_menu() if is_admin(chat_id) else kb.main_menu(False))
        return

    if text == kb.BTN_IMAGE:
        state.set_mode(chat_id, "awaiting_image_prompt")
        telegram_api.send_message(chat_id, "اكتب وصف الصورة اللي تريدها.", reply_markup=kb.cancel_menu())
        return

    if text == kb.BTN_MODEL:
        models = models_provider.free_models()
        if not models:
            telegram_api.send_message(chat_id, "⚠️ تعذّر جلب قائمة النماذج الآن، جرّب لاحقاً.")
            return
        telegram_api.send_message(chat_id, f"اختر نموذجاً ({len(models)} متاح الآن، القائمة حيّة وتتغيّر):", reply_markup=kb.model_picker_inline(models))
        return

    if text == kb.BTN_ABOUT:
        telegram_api.send_message(chat_id, ABOUT, reply_markup=kb.main_menu(is_admin(chat_id)))
        return

    if text == kb.BTN_ADMIN and is_admin(chat_id):
        telegram_api.send_message(chat_id, "لوحة التحكم:", reply_markup=kb.admin_menu())
        return

    if text == kb.BTN_BACK:
        state.set_mode(chat_id, "idle")
        send_main(chat_id)
        return

    if is_admin(chat_id) and text == kb.BTN_STATS:
        telegram_api.send_message(chat_id, f"👥 المستخدمون: {state.user_count()}\n🕒 يشتغل منذ: {state.uptime()}\n✨ إيموجيات محفوظة: {len(premium_emoji.load())}", reply_markup=kb.admin_menu())
        return

    if is_admin(chat_id) and text == kb.BTN_BROADCAST:
        state.set_mode(chat_id, "awaiting_broadcast")
        telegram_api.send_message(chat_id, "اكتب رسالة البث.", reply_markup=kb.cancel_menu())
        return

    if is_admin(chat_id) and text == kb.BTN_EMOJI:
        telegram_api.send_message(chat_id, "كم عدد الإيموجيات المميزة اللي تريد تضيفها؟", reply_markup=kb.emoji_count_inline())
        return

    if is_admin(chat_id) and text == kb.BTN_LISTMOJI:
        data = premium_emoji.load()
        if not data:
            telegram_api.send_message(chat_id, "لا يوجد إيموجي محفوظ بعد.", reply_markup=kb.admin_menu())
            return
        by_cat = {}
        for v in data.values():
            by_cat.setdefault(v.get("category", "عام"), []).append(v["char"])
        lines = [f"{c}: {' '.join(chars[:25])} ({len(chars)})" for c, chars in by_cat.items()]
        telegram_api.send_message(chat_id, "\n".join(lines), reply_markup=kb.admin_menu())
        return

    # ---- حسب الوضع الحالي ----
    if mode == "awaiting_image_prompt":
        telegram_api.send_message(chat_id, "⏳ أرسم...")
        img = image_gen.generate(text)
        state.set_mode(chat_id, "idle")
        if img:
            telegram_api.send_photo_bytes(chat_id, img, caption=text[:200])
        else:
            telegram_api.send_message(chat_id, "⚠️ تعذّر توليد الصورة، جرّب وصفاً آخر.")
        telegram_api.send_message(chat_id, "تمام؟", reply_markup=kb.main_menu(is_admin(chat_id)))
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
        telegram_api.send_message(chat_id, f"✅ أُرسلت لـ {sent} مستخدماً.", reply_markup=kb.admin_menu())
        return

    # ---- محادثة عادية مع الذكاء الاصطناعي ----
    reply = answer_ai(state, chat_id, text)
    if is_admin(chat_id):
        telegram_api.send_message(chat_id, reply)  # بلا إيموجي مميز للوحة الأدمن
    else:
        final_text, entities = decorate(reply)
        telegram_api.send_message(chat_id, final_text, entities=entities)


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
        telegram_api.send_message(chat_id, f"✅ تم! حُفظ {got} إيموجي مميزاً.", reply_markup=kb.admin_menu())
    else:
        telegram_api.send_message(chat_id, f"📥 {got}/{target} حتى الآن. استمر بإرسال/تحويل المزيد، أو اضغط إلغاء.")
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
        telegram_api.send_message(chat_id, f"✅ من الآن تحادث: {model}", reply_markup=kb.main_menu(is_admin(chat_id)))
    elif data.startswith("moji_target:") and is_admin(chat_id):
        target = int(data.split(":")[1])
        state.set_mode(chat_id, "collecting_emoji", target=target, got=0)
        telegram_api.answer_callback(cq["id"])
        telegram_api.send_message(chat_id, f"حوّل أو أرسل {target} رسالة فيها إيموجي مميز واحد (أو أكثر بكل رسالة)، وأنا أحفظها تلقائياً.", reply_markup=kb.cancel_menu())
    else:
        telegram_api.answer_callback(cq["id"])


def main():
    if not config.BOT_TOKEN:
        raise SystemExit("ضع BOT_TOKEN في ملف .env أولاً (انسخ من .env.example).")
    me = telegram_api.call("getMe")
    log.info("بدأ البوت: @%s", me.get("username"))
    telegram_api.set_my_commands([{"command": "start", "description": "بدء المحادثة"}])
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
                state.touch_user(chat_id)
                if is_admin(chat_id) and state.current_mode(chat_id) == "collecting_emoji":
                    if handle_emoji_message(state, chat_id, msg):
                        continue
                text = msg.get("text", "")
                if text:
                    handle_text(state, chat_id, text)
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

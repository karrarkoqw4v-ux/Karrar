"""بوت تيليجرام «بوصلة»: نفس شخصية مساعد تطبيق بوصلة، بلوحة تحكم للأدمن (ID محدَّد)، وإيموجيات مميزة
للمستخدمين العاديين فقط (لوحة الأدمن نصّ عادي بلا إيموجي مميز).

تشغيل:  python bot.py
"""
import logging
import time

import config
import premium_emoji
import telegram_api
from ai_provider import ask
from state import State

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("bawsala-bot")

HELP_USER = (
    "أهلاً بك في بوصلة 🧭\nاكتب أي سؤال وسأجيبك.\n\n"
    "/start — بدء المحادثة\n/help — هذه الرسالة"
)
HELP_ADMIN = (
    "لوحة تحكم الأدمن\n"
    "/stats — عدد المستخدمين ووقت التشغيل\n"
    "/broadcast <نص> — رسالة لكل من تحدّث مع البوت\n"
    "/moji <اسم> — احفظ إيموجي مميزاً: حوّل رسالة فيها الإيموجي ثم أرسل هذا الأمر رداً عليها\n"
    "/listmoji — عرض الإيموجيات المحفوظة"
)


def decorate(text: str) -> tuple:
    """يضيف إيموجي مميزاً واحداً في نهاية الرد (للمستخدم العادي فقط)، أو يرجّع النص خالياً إن لم يوجد أي إيموجي محفوظ."""
    e = premium_emoji.pick_for_reply()
    if not e:
        return text, None
    full = text + " " + e["char"]
    at = premium_emoji.utf16_offset_of(full, len(full) - len(e["char"]))
    ent = premium_emoji.entity_for(full, e, at=at)
    return full, [ent]


def handle_admin(state: State, chat_id: int, text: str, reply_msg: dict):
    if text == "/stats":
        telegram_api.send_message(chat_id, f"👥 عدد المستخدمين: {state.user_count()}\n🕒 مشتغل منذ: {state.uptime()}")
    elif text.startswith("/broadcast "):
        msg = text[len("/broadcast "):]
        sent = 0
        for uid in state.all_users():
            try:
                telegram_api.send_message(uid, msg)
                sent += 1
            except Exception:
                pass
        telegram_api.send_message(chat_id, f"✅ أُرسلت لـ {sent} مستخدماً.")
    elif text.startswith("/moji"):
        name = text[len("/moji"):].strip() or f"emoji{len(premium_emoji.load()) + 1}"
        if not reply_msg:
            telegram_api.send_message(chat_id, "حوّل رسالة فيها إيموجي مميز ثم أرسل /moji <اسم> رداً عليها.")
            return
        found = premium_emoji.extract_from_message(reply_msg)
        if not found:
            telegram_api.send_message(chat_id, "ما لقيت إيموجي مميزاً بهذي الرسالة.")
            return
        for f in found:
            premium_emoji.add(name, f["id"], f["char"])
        telegram_api.send_message(chat_id, f"✅ حُفظ {len(found)} إيموجي مميزاً باسم «{name}». المجموع الآن: {len(premium_emoji.load())}")
    elif text == "/listmoji":
        data = premium_emoji.load()
        if not data:
            telegram_api.send_message(chat_id, "لا يوجد إيموجي محفوظ بعد.")
            return
        lines = [f"{k}: {v['char']} ({v['id']})" for k, v in data.items()]
        telegram_api.send_message(chat_id, "\n".join(lines[:50]))
    else:
        telegram_api.send_message(chat_id, HELP_ADMIN)


def handle_user(state: State, chat_id: int, text: str):
    state.touch_user(chat_id)
    if text in ("/start", "/help"):
        telegram_api.send_message(chat_id, HELP_USER)
        return
    history = state.history(chat_id)
    history.append({"role": "user", "content": text})
    reply = ask(history[-12:])
    history.append({"role": "assistant", "content": reply})
    state.save_history(chat_id, history)
    final_text, entities = decorate(reply)
    telegram_api.send_message(chat_id, final_text, entities=entities)


def main():
    if not config.BOT_TOKEN:
        raise SystemExit("ضع BOT_TOKEN في ملف .env أولاً (انسخ من .env.example).")
    me = telegram_api.call("getMe")
    log.info("بدأ البوت: @%s", me.get("username"))
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
            msg = u.get("message")
            if not msg:
                continue
            chat_id = msg["chat"]["id"]
            text = msg.get("text", "")
            try:
                if chat_id == config.ADMIN_ID:
                    handle_admin(state, chat_id, text, msg.get("reply_to_message"))
                else:
                    handle_user(state, chat_id, text)
            except Exception as e:
                log.exception("handler error")
                try:
                    telegram_api.send_message(chat_id, f"⚠️ صار خطأ داخلي: {e}")
                except Exception:
                    pass


if __name__ == "__main__":
    main()

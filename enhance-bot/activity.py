"""توجيه نشاط المستخدمين إلى المسؤول: دخول جديد/رجوع، رسائل، ملفات مرفوعة، أدوات مستخدمة، نتائج مرسلة، حسابات.
يتحكم المسؤول بكل نوع من لوحة التحكم ← التوجيهات. لا تُوجَّه أبداً رسائل إدخال الاسم/كلمة السر ولا كلمات السر التي يختارها المستخدم بنفسه."""
import asyncio
import logging
import os
import time

from aiogram import BaseMiddleware, Bot
from aiogram.client.session.middlewares.base import BaseRequestMiddleware
from aiogram.methods import SendAnimation, SendAudio, SendDocument, SendPhoto, SendVideo
from aiogram.types import CallbackQuery, Message

import db

log = logging.getLogger("activity")
BOT: Bot | None = None
ADMIN_IDS: set[int] = set()
FLUSH_SECS = float(os.getenv("ACTIVITY_FLUSH", "4"))
RETURN_GAP = 30 * 60
_buf: dict[int, dict] = {}
_last_seen: dict[int, float] = {}
_users: dict[int, dict] = {}  # آخر معلومات معروفة للمستخدم (اسم/يوزر)


def install(bot: Bot, admin_ids: set[int]):
    global BOT
    BOT = bot
    ADMIN_IDS.clear()
    ADMIN_IDS.update(admin_ids)


def skip(uid: int) -> bool:
    """المسؤول لا يُوجَّه نشاطه إلا إذا فعّل «وجّه نشاطي أنا أيضاً» (للتجربة)"""
    return uid in ADMIN_IDS and not db.flag("fwd_self")


def on(kind: str) -> bool:
    return db.flag("fwd_all") and db.flag(kind)


def esc(s) -> str:
    return str(s or "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def remember(u):
    _users[u.id] = {"name": u.full_name, "username": u.username}


async def card(uid: int) -> str:
    """بطاقة المستخدم: الاسم، اليوزر، الآيدي، الحساب"""
    info = _users.get(uid) or {}
    name, un = info.get("name"), info.get("username")
    if name is None:
        rows = await db.search_users(str(uid))
        if rows:
            name, un = rows[0][1], rows[0][2]
    acc = ""
    aid = db.account_of(uid)
    if aid:
        a = await db.account_by_id(aid)
        if a:
            acc = f"\n🔐 الحساب: <code>{esc(a[1])}</code>"
    link = f'<a href="tg://user?id={uid}">{esc(name) or uid}</a>'
    return f"👤 {link} • {('@' + un) if un else 'بدون يوزر'} • <code>{uid}</code>{acc}"


async def _send_all(text: str, silent: bool = True):
    if BOT is None:
        return
    for a in ADMIN_IDS:
        try:
            await BOT.send_message(a, text, disable_notification=silent, disable_web_page_preview=True)
        except Exception:  # noqa: BLE001
            pass


async def copy_to_admins(chat_id: int, message_id: int, silent: bool = True):
    if BOT is None:
        return
    for a in ADMIN_IDS:
        try:
            await BOT.copy_message(a, chat_id, message_id, disable_notification=silent)
        except Exception:  # noqa: BLE001
            pass


def log_event(uid: int, text: str):
    """يضيف حدثاً إلى مخزن المستخدم؛ يُرسل مجمّعاً بعد لحظات لمنع الإزعاج"""
    if skip(uid) or not db.flag("fwd_all") or not db.flag("fwd_activity"):
        return
    b = _buf.setdefault(uid, {"events": [], "task": None})
    b["events"].append(f"• {time.strftime('%H:%M:%S')} {text}")
    if b["task"] is None or b["task"].done():
        b["task"] = asyncio.get_running_loop().create_task(_flush(uid))


async def _flush(uid: int):
    await asyncio.sleep(FLUSH_SECS)
    b = _buf.get(uid)
    if not b or not b["events"]:
        return
    ev, b["events"] = b["events"][:25], b["events"][25:]
    await _send_all(f"{await card(uid)}\n" + "\n".join(ev))


async def notify_new_user(uid, name, username):
    _users[uid] = {"name": name, "username": username}
    _last_seen[uid] = time.time()
    if skip(uid) or not on("fwd_new"):
        return
    await db.add_alert("new_user", f"مستخدم جديد {name} {uid}")
    await _send_all("🆕 <b>تم دخول شخص جديد للبوت</b>\n\n" + await card(uid) + f"\n🕐 {time.strftime('%Y-%m-%d %H:%M')}\n🔐 الحساب: لم يسجّل بعد", silent=False)


async def notify_new_account(uid, username, generated_pwd=None):
    if skip(uid) or not on("fwd_account"):
        return
    pw = (f"🔑 كلمة السر (ولّدها البوت): <code>{esc(generated_pwd)}</code>" if generated_pwd
          else "🔑 كلمة السر: اختارها المستخدم بنفسه — محفوظة مشفّرة ولا يراها أحد (تقدر تعيّن له كلمة جديدة من لوحة التحكم)")
    await _send_all("🔐 <b>حساب جديد</b>\n\n" + await card(uid) + f"\n📛 اسم المستخدم: <code>{esc(username)}</code>\n{pw}", silent=False)


async def seen(u, event_text: str = "حضر للبوت"):
    """رجوع مستخدم بعد غياب (أو أول رسالة): يُبلَّغ المسؤول مرة كل 30 دقيقة"""
    remember(u)
    if skip(u.id) or not on("fwd_new"):
        return
    now = time.time()
    last = _last_seen.get(u.id)
    _last_seen[u.id] = now
    if last is None and not await db.search_users(str(u.id)):
        return  # مستخدم جديد كلياً: يخبره notify_new_user
    if last is None or now - last > RETURN_GAP:
        await _send_all(f"👋 <b>شخص حضر للبوت</b>\n\n" + await card(u.id) + f"\n🕐 {time.strftime('%Y-%m-%d %H:%M')}", silent=False)


def _kind(msg: Message) -> str | None:
    for k, lab in (("video", "فيديو"), ("photo", "صورة"), ("animation", "GIF"), ("video_note", "فيديو دائري"), ("audio", "ملف صوتي"), ("voice", "رسالة صوتية"),
                   ("document", "ملف"), ("sticker", "ملصق")):
        if getattr(msg, k, None):
            return lab
    return None


async def on_message(msg: Message, in_auth_flow: bool):
    """رسالة من مستخدم: نص يُوجَّه، وملف يُنسخ للمسؤول مع بطاقة"""
    u = msg.from_user
    if u is None or skip(u.id) or in_auth_flow:
        return
    remember(u)
    kind = _kind(msg)
    if kind and on("fwd_media"):
        extra = f" — {msg.caption[:120]}" if msg.caption else ""
        log_event(u.id, f"📎 أرسل {kind}{extra}")
        await copy_to_admins(msg.chat.id, msg.message_id)
    elif msg.text and not msg.text.startswith("/start") and on("fwd_msgs"):
        await _send_all(f"{await card(u.id)}\n💬 <b>رسالة:</b>\n{esc(msg.text[:1500])}")
    elif msg.text and msg.text.startswith("/") and on("fwd_activity"):
        log_event(u.id, f"أمر {esc(msg.text.split()[0])}")


def button_label(cb: CallbackQuery) -> str:
    try:
        for row in cb.message.reply_markup.inline_keyboard:
            for b in row:
                if b.callback_data == cb.data:
                    return b.text
    except Exception:  # noqa: BLE001
        pass
    return ""


def snapshot(uid: int, jobs: dict, pjobs: dict):
    return (len(jobs[uid]["stack"]) if uid in jobs else None, len(pjobs[uid]["stack"]) if uid in pjobs else None)


def diff_events(uid: int, before, jobs: dict, pjobs: dict, effects_titles, tools_titles):
    """مقارنة مكدس الأدوات قبل وبعد الضغط: نعرف بالضبط شنو أضاف"""
    if uid in jobs and before[0] is not None and len(jobs[uid]["stack"]) > before[0]:
        names = [effects_titles(e) for e, _ in jobs[uid]["stack"][before[0]:]]
        log_event(uid, "🎬 أضاف تأثيرات فيديو: " + "، ".join(names))
    elif uid in jobs and before[0] is None:
        log_event(uid, "🎬 بدأ جلسة مونتاج فيديو" + (" (المصممين)" if jobs[uid].get("pro") else ""))
    if uid in pjobs and before[1] is not None and len(pjobs[uid]["stack"]) > before[1]:
        names = [tools_titles(e) for e, _ in pjobs[uid]["stack"][before[1]:]]
        log_event(uid, "🖼 أضاف أدوات صور: " + "، ".join(names))
    elif uid in pjobs and before[1] is None:
        log_event(uid, "🖼 بدأ جلسة مونتاج صور" + (" (المصممين)" if pjobs[uid].get("pro") else ""))


# ---- نسخ النتائج المرسلة للمستخدم (فيديو/صورة/ملف مكتمل) إلى المسؤول
_RESULT_MARKERS = ("تم اكمال طلبك", "تم التحميل", "الصوت المستخرج", "النسخة الأصلية")


class ResultMirror(BaseRequestMiddleware):
    async def __call__(self, make_request, bot, method):
        res = await make_request(bot, method)
        try:
            if isinstance(method, (SendVideo, SendPhoto, SendDocument, SendAudio, SendAnimation)) and on("fwd_results"):
                cap = getattr(method, "caption", None) or ""
                chat = method.chat_id
                if isinstance(chat, int) and not skip(chat) and any(m in cap for m in _RESULT_MARKERS) and hasattr(res, "message_id"):
                    asyncio.get_running_loop().create_task(self._mirror(chat, res.message_id))
        except Exception:  # noqa: BLE001
            pass
        return res

    async def _mirror(self, chat: int, mid: int):
        await _send_all("📤 <b>نتيجة أُرسلت لهذا المستخدم:</b>\n" + await card(chat))
        await copy_to_admins(chat, mid)

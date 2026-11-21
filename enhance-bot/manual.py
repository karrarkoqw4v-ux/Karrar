"""طلب تعديل يدوي من المصمم: المستخدم يطلب، الملف يُحفظ ويصل المسؤول، والمسؤول (أو Claude بطلبك) يسلّم النتيجة للمستخدم."""
import asyncio
import json
import logging
import shutil
import time
from pathlib import Path

from aiogram import Bot, F, Router
from aiogram.exceptions import TelegramBadRequest
from aiogram.types import CallbackQuery, FSInputFile, InlineKeyboardButton, InlineKeyboardMarkup, Message

import activity
import db

log = logging.getLogger("manual")
router = Router()
CTX: dict = {}  # main: JOBS, photo (وحدة)، WORK
ASK: dict[int, dict] = {}  # مستخدمون يكتبون ملاحظة الطلب


def kb(rows) -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup(inline_keyboard=[[x if isinstance(x, InlineKeyboardButton) else InlineKeyboardButton(text=x[0], callback_data=x[1]) for x in r] for r in rows])


def esc(s) -> str:
    return str(s or "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def req_dir() -> Path:
    d = Path(CTX.get("WORK", "work")).parent / "requests"
    d.mkdir(parents=True, exist_ok=True)
    return d


def _session(uid: int):
    j = CTX.get("JOBS", {}).get(uid)
    if j and Path(j["src"]).exists():
        return "video", j
    pj = CTX["photo"].PJOBS.get(uid)
    if pj and Path(pj["src"]).exists():
        return "photo", pj
    return None, None


@router.callback_query(F.data == "rq:new")
async def cb_new(cb: CallbackQuery):
    kind, j = _session(cb.from_user.id)
    if not j:
        return await cb.answer("ارفع فيديو أو صورة أولاً", show_alert=True)
    await cb.answer()
    ASK[cb.from_user.id] = {"kind": kind}
    await cb.message.answer("🧑‍🎨 <b>طلب تعديل يدوي من المصمم</b>\n\nاكتب ملاحظاتك: شنو تريد بالضبط؟ (ستايل، ألوان، قص، موسيقى...).\nأو اضغط «إرسال بدون ملاحظات».",
                            reply_markup=kb([[InlineKeyboardButton(text="📤 إرسال بدون ملاحظات", callback_data="rq:go", style="success")], [("↩️ إلغاء", "rq:cancel")]]))


@router.callback_query(F.data == "rq:cancel")
async def cb_cancel(cb: CallbackQuery):
    ASK.pop(cb.from_user.id, None)
    await cb.answer("أُلغي")
    try:
        await cb.message.delete()
    except TelegramBadRequest:
        pass


def _asking(m: Message) -> bool:
    return bool(m.from_user and m.text and not m.text.startswith("/") and m.from_user.id in ASK)


@router.message(_asking)
async def on_note(msg: Message, bot: Bot):
    st = ASK.get(msg.from_user.id)
    st["note"] = msg.text.strip()[:600]
    await submit(bot, msg.from_user.id, msg.chat.id)


@router.callback_query(F.data == "rq:go")
async def cb_go(cb: CallbackQuery, bot: Bot):
    if cb.from_user.id not in ASK:
        return await cb.answer("انتهت المهلة", show_alert=True)
    await cb.answer()
    try:
        await cb.message.delete()
    except TelegramBadRequest:
        pass
    await submit(bot, cb.from_user.id, cb.message.chat.id)


async def submit(bot: Bot, uid: int, chat: int):
    st = ASK.pop(uid, None) or {}
    kind, j = _session(uid)
    if not j:
        return await bot.send_message(chat, "انتهت الجلسة، ارفع الملف من جديد.")
    note = st.get("note", "")
    stack = json.dumps([(e, p) for e, p in j["stack"]], ensure_ascii=False, default=str)
    rid = await db.add_request(uid, kind, "", note, stack)
    ext = Path(j["src"]).suffix or (".mp4" if kind == "video" else ".jpg")
    dst = req_dir() / f"{rid}_{uid}{ext}"
    await asyncio.to_thread(shutil.copyfile, j["src"], dst)
    await asyncio.to_thread(db._q, "UPDATE requests SET path=? WHERE id=?", (str(dst), rid))
    await bot.send_message(chat, f"✅ <b>وصل طلبك رقم {rid} للمصمم</b>\nراح يعدّل عليه ويرجّعه لك هنا بالبوت.")
    names = ", ".join(e for e, _ in j["stack"]) or "—"
    text = (f"🧑‍🎨 <b>طلب تعديل يدوي رقم {rid}</b>\n\n{await activity.card(uid)}\n"
            f"📂 النوع: {'فيديو' if kind == 'video' else 'صورة'}\n📝 ملاحظاته: {esc(note) or '—'}\n🧰 اختياراته الحالية: {esc(names)[:300]}")
    mk = kb([[InlineKeyboardButton(text="✅ تسليم النتيجة", callback_data=f"ad:rqd:{rid}", style="success"), ("📂 فتح الطلب", f"ad:rqv:{rid}")]])
    for a in activity.ADMIN_IDS:
        try:
            await bot.send_message(a, text, reply_markup=mk)
            if kind == "video":
                await bot.send_video(a, FSInputFile(dst), caption=f"الطلب {rid}")
            else:
                await bot.send_document(a, FSInputFile(dst), caption=f"الطلب {rid} (أصلية)")
        except Exception:  # noqa: BLE001
            log.exception("notify request")
    await db.add_alert("request", f"طلب تعديل يدوي {rid} من {uid}")


async def deliver(bot: Bot, rid: int, chat_id: int, message_id: int) -> bool:
    """يسلّم نتيجة (رسالة المسؤول) إلى صاحب الطلب ويغلق الطلب"""
    r = await db.get_request(rid)
    if not r:
        return False
    await bot.copy_message(r[1], chat_id, message_id, caption=f"✅ <b>تم تعديل طلبك رقم {rid}</b> بواسطة المصمم 🧑‍🎨\nشكراً لاستخدامك ♥️")
    await db.set_request_status(rid, "done")
    return True

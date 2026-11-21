"""لوحة «التنفيذ المباشر» (للمسؤول فقط): تكتب طلبك (أضف ميزة / عدّل زراً / أصلح شيئاً) فيُحفظ في طابور
ينفّذه Claude خلال دقيقة ثم يصلك الرد هنا. لا ينفَّذ أي طلب إلا من آيدي المسؤول المُتحقَّق منه في تيليجرام."""
import html
import time

from aiogram import F, Router
from aiogram.filters import Command, CommandObject
from aiogram.types import CallbackQuery, InlineKeyboardButton, InlineKeyboardMarkup, Message

import db

router = Router()
ADMIN_IDS: set[int] = set()
ASK: set[int] = set()  # مسؤولون ينتظرون كتابة طلبهم

ST = {"pending": "⏳ بالانتظار", "working": "🔧 قيد التنفيذ", "done": "✅ تم", "failed": "❌ تعذّر"}


def _init():
    db._q("CREATE TABLE IF NOT EXISTS devreq(id INTEGER PRIMARY KEY AUTOINCREMENT, uid INTEGER, ts INTEGER, text TEXT, status TEXT DEFAULT 'pending', result TEXT DEFAULT '', done_ts INTEGER DEFAULT 0)")


def add(uid: int, text: str) -> int:
    _init()
    db._q("INSERT INTO devreq(uid,ts,text) VALUES(?,?,?)", (uid, int(time.time()), text.strip()[:4000]))
    return db._q("SELECT max(id) FROM devreq", (), True)[0][0]


def recent(n: int = 6):
    _init()
    return db._q("SELECT id,status,substr(text,1,60),result FROM devreq ORDER BY id DESC LIMIT ?", (n,), True)


def _kb(rows):
    return InlineKeyboardMarkup(inline_keyboard=rows)


def panel() -> tuple[str, InlineKeyboardMarkup]:
    rows = recent()
    lst = "\n".join(f"{ST.get(s, s)} #{i}: {html.escape(t)}" for i, s, t, _ in rows) or "لا توجد طلبات بعد."
    txt = ("⚡ <b>لوحة التنفيذ المباشر</b>\n\n"
           "اكتب ما تريده بالعربي (مثلاً: «أضف أداة لقص الصور بنسبة 1:1» أو «غيّر نص زر ..») ويُنفَّذ خلال دقيقة تقريباً "
           "ويصلك الرد هنا بعد الانتهاء.\n\n"
           f"<b>آخر الطلبات:</b>\n{lst}")
    return txt, _kb([[InlineKeyboardButton(text="✍️ طلب جديد", callback_data="ad:devnew", style="success")],
                     [InlineKeyboardButton(text="🔄 تحديث", callback_data="ad:dev"), InlineKeyboardButton(text="↩️ اللوحة", callback_data="ad:home")]])


def _is_admin(uid: int) -> bool:
    return uid in ADMIN_IDS


@router.callback_query(F.data == "ad:dev")
async def cb_dev(cb: CallbackQuery):
    if not _is_admin(cb.from_user.id):
        return await cb.answer("للمسؤول فقط", show_alert=True)
    await cb.answer()
    txt, kb = panel()
    try:
        await cb.message.edit_text(txt, reply_markup=kb)
    except Exception:  # noqa: BLE001
        await cb.message.answer(txt, reply_markup=kb)


@router.callback_query(F.data == "ad:devnew")
async def cb_devnew(cb: CallbackQuery):
    if not _is_admin(cb.from_user.id):
        return await cb.answer("للمسؤول فقط", show_alert=True)
    await cb.answer()
    ASK.add(cb.from_user.id)
    await cb.message.answer("✍️ اكتب طلبك الآن في رسالة واحدة (أو /cancel للإلغاء).")


@router.message(Command("cancel"), F.func(lambda m: m.from_user and m.from_user.id in ASK))
async def cmd_cancel(msg: Message):
    ASK.discard(msg.from_user.id)
    await msg.answer("أُلغي.")


async def _save(msg: Message, text: str):
    rid = add(msg.from_user.id, text)
    txt, kb = panel()
    await msg.answer(f"✅ تم استلام طلبك رقم <b>#{rid}</b>\nيبدأ التنفيذ خلال دقيقة تقريباً، وسأرسل لك النتيجة هنا.\n\n" + txt, reply_markup=kb)


@router.message(Command("do"))
async def cmd_do(msg: Message, command: CommandObject):
    """/do <الطلب> — اختصار للمسؤول"""
    if not _is_admin(msg.from_user.id):
        return
    if not (command.args or "").strip():
        txt, kb = panel()
        return await msg.answer(txt, reply_markup=kb)
    await _save(msg, command.args)


@router.message(F.text, F.func(lambda m: m.from_user and m.from_user.id in ASK and not (m.text or "").startswith("/")))
async def on_text(msg: Message):
    ASK.discard(msg.from_user.id)
    await _save(msg, msg.text)

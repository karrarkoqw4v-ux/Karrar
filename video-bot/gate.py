"""الاشتراك الإجباري (مشترك بين البوت وقائمة الأدوات)"""
import asyncio
import logging
import time

from aiogram import Bot
from aiogram.enums import ChatMemberStatus
from aiogram.exceptions import TelegramBadRequest, TelegramForbiddenError
from aiogram.types import InlineKeyboardButton, InlineKeyboardMarkup

import config
import db

log = logging.getLogger("gate")
_sub_ok: dict[int, float] = {}
OK_STATUS = (ChatMemberStatus.MEMBER, ChatMemberStatus.ADMINISTRATOR, ChatMemberStatus.CREATOR, ChatMemberStatus.RESTRICTED)

SUB_TEXT = "⚠️ <b>عذراً عزيزي، يجب الاشتراك في القنوات التالية أولاً لاستخدام البوت:</b>\n\nاشترك ثم اضغط «✅ تحققت من الاشتراك»."


def is_admin(uid: int) -> bool:
    return uid in config.ADMIN_IDS


def clear_cache():
    _sub_ok.clear()


async def missing_channels(bot: Bot, uid: int) -> list[tuple[str, str, str]]:
    if is_admin(uid) or _sub_ok.get(uid, 0) > time.time():
        return []
    chans = await db.channels()
    if not chans:
        return []

    async def check(ch):
        try:
            m = await bot.get_chat_member(ch[0], uid)
            return None if m.status in OK_STATUS else ch
        except (TelegramBadRequest, TelegramForbiddenError) as e:
            log.warning("لا يمكن فحص القناة %s (هل البوت مشرف فيها؟): %s", ch[0], e)
            return None
    res = await asyncio.gather(*(check(c) for c in chans))
    miss = [c for c in res if c]
    if not miss:
        _sub_ok[uid] = time.time() + 120
    return miss


def sub_kb(miss) -> InlineKeyboardMarkup:
    rows = []
    for chat, title, link in miss:
        url = link or (f"https://t.me/{chat.lstrip('@')}" if chat.startswith("@") else None)
        if url:
            rows.append([InlineKeyboardButton(text=f"📢 {title or chat}", url=url)])
    rows.append([InlineKeyboardButton(text="✅ تحققت من الاشتراك", callback_data="chk")])
    return InlineKeyboardMarkup(inline_keyboard=rows)

import asyncio
import logging
import os
import re
import time

from aiohttp import web

from aiogram import Bot, Dispatcher, F, Router
from aiogram.client.default import DefaultBotProperties
from aiogram.client.session.aiohttp import AiohttpSession
from aiogram.client.telegram import TelegramAPIServer
from aiogram.enums import ChatMemberStatus, ParseMode
from aiogram.exceptions import TelegramBadRequest, TelegramForbiddenError
from aiogram.filters import Command, CommandStart
from aiogram.types import CallbackQuery, FSInputFile, InlineKeyboardButton, InlineKeyboardMarkup, Message

import config
import db
import downloader as dl
import menu
from gate import SUB_TEXT, clear_cache, is_admin, missing_channels, sub_kb

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("bot")
router = Router()
sem = asyncio.Semaphore(config.MAX_PARALLEL)
busy: set[int] = set()


# ---------------- الاشتراك الإجباري ----------------
async def gate(bot: Bot, msg: Message) -> bool:
    """True إذا المستخدم مشترك في كل القنوات"""
    miss = await missing_channels(bot, msg.from_user.id)
    if miss:
        await msg.answer(SUB_TEXT, reply_markup=sub_kb(miss), disable_web_page_preview=True)
        return False
    return True


@router.message(CommandStart())
async def start(msg: Message, bot: Bot):
    u = msg.from_user
    await db.add_user(u.id, u.full_name, u.username)
    menu.pending.pop(msg.from_user.id, None)
    if await gate(bot, msg):
        await msg.answer(config.WELCOME, reply_markup=menu.main_button())


@router.callback_query(F.data == "chk")
async def chk(cb: CallbackQuery, bot: Bot):
    miss = await missing_channels(bot, cb.from_user.id)
    if miss:
        await cb.answer("❌ لم تشترك في كل القنوات بعد", show_alert=True)
        try:
            await cb.message.edit_reply_markup(reply_markup=sub_kb(miss))
        except TelegramBadRequest:
            pass
        return
    await cb.answer("✅ تم التحقق")
    try:
        await cb.message.delete()
    except TelegramBadRequest:
        pass
    await cb.message.answer(config.WELCOME, reply_markup=menu.main_button())


# ---------------- البحث بالآيدي ----------------
ID_RE = re.compile(r"^\s*(\d{5,15})\s*$")
UN_RE = re.compile(r"^\s*(?:@|https?://t\.me/)([A-Za-z][A-Za-z0-9_]{4,31})/?\s*$")


def _e(s: str) -> str:
    return (s or "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


async def profile_reply(bot: Bot, msg: Message, uid: int | None = None, username: str | None = None):
    """يرسل زراً يفتح حساب الشخص مباشرة (بالآيدي أو المعرّف)"""
    name = bio = None
    ref = uid
    try:
        ch = await bot.get_chat(uid if uid else f"@{username}")
        uid = ch.id
        username = ch.username or username
        name = " ".join(x for x in (ch.first_name, ch.last_name) if x) or ch.title
        bio = ch.bio
    except (TelegramBadRequest, TelegramForbiddenError):
        pass
    lines = ["👤 <b>الحساب</b>"]
    if name:
        lines.append(f"📛 <b>الاسم:</b> {_e(name)}")
    if uid:
        lines.append(f"🆔 <b>الآيدي:</b> <code>{uid}</code>")
    if username:
        lines.append(f"🔗 <b>المعرّف:</b> @{username}")
    if bio:
        lines.append(f"📝 <b>النبذة:</b> {_e(bio)[:200]}")
    rows = []
    if username:
        rows.append([InlineKeyboardButton(text="👤 فتح الحساب", url=f"https://t.me/{username}")])
    elif uid:
        rows.append([InlineKeyboardButton(text="👤 فتح الحساب", url=f"tg://user?id={uid}")])
    if not name and not username:
        lines.append("\nℹ️ إن لم يفتح الحساب عند الضغط فصاحبه لم يتفاعل مع البوت أو يخفي حسابه.")
    await msg.answer("\n".join(lines), reply_markup=InlineKeyboardMarkup(inline_keyboard=rows) if rows else None)


@router.message(Command("id"))
async def my_id(msg: Message):
    await msg.answer(f"🆔 آيديك: <code>{msg.from_user.id}</code>")


# ---------------- التحميل ----------------
@router.message(F.text & ~F.text.startswith("/"))
async def on_text(msg: Message, bot: Bot):
    u = msg.from_user
    await db.add_user(u.id, u.full_name, u.username)
    mid = ID_RE.match(msg.text)
    mun = UN_RE.match(msg.text)
    if mid or mun:
        if not await gate(bot, msg):
            return
        await profile_reply(bot, msg, uid=int(mid.group(1)) if mid else None, username=mun.group(1) if mun else None)
        return
    url = dl.find_url(msg.text)
    if not url:
        await msg.answer("📎 أرسل الآيدي ( المعرّف ) لفتح حساب الشخص، أو رابط فيديو من اليوتيوب أو انستكرام أو تيكتوك أو فيسبوك أو تويتر أو سناب شات أو ساوند كلاود.")
        return
    if not await gate(bot, msg):
        return
    plat = dl.detect(url)
    if not plat:
        await msg.answer("⚠️ هذا الرابط غير مدعوم. المنصات المدعومة: اليوتيوب، انستكرام، تيكتوك، فيسبوك، تويتر، سناب شات، ساوند كلاود.")
        return
    name, icon = plat
    if u.id in busy:
        await msg.answer("⏳ لديك تحميل قيد التنفيذ، انتظر حتى ينتهي.")
        return
    busy.add(u.id)
    wait = await msg.answer(config.WAIT)
    t0 = time.time()
    key = dl.normalize(url)
    res = None
    try:
        # 1) تخزين مؤقت: نفس الرابط من قبل → إرسال فوري بدون تحميل
        hit = await db.cache_get(key)
        if hit:
            file_id, kind, cap = hit
            try:
                if kind == "audio":
                    await bot.send_audio(msg.chat.id, file_id, caption=cap, reply_to_message_id=msg.message_id)
                else:
                    await bot.send_video(msg.chat.id, file_id, caption=cap, supports_streaming=True, reply_to_message_id=msg.message_id)
                await db.log_download(u.id, name, True)
                log.info("cache hit %s %.2fs", key, time.time() - t0)
                return
            except TelegramBadRequest:
                pass  # file_id لم يعد صالحاً → نحمّل من جديد
        # 2) تحميل جديد
        async with sem:
            res = await dl.download(url, name, icon)
        cap = dl.caption(res, url)
        file = FSInputFile(res.path)
        if res.kind == "audio":
            sent = await bot.send_audio(
                msg.chat.id, file, caption=cap, duration=res.duration,
                title=(res.info.get("title") or "")[:64], performer=(res.info.get("uploader") or "")[:64],
                reply_to_message_id=msg.message_id)
            fid = sent.audio.file_id
        else:
            sent = await bot.send_video(
                msg.chat.id, file, caption=cap, duration=res.duration, width=res.width, height=res.height,
                thumbnail=FSInputFile(res.thumb) if res.thumb else None, supports_streaming=True,
                reply_to_message_id=msg.message_id)
            fid = sent.video.file_id
        await db.cache_put(key, fid, res.kind, cap)
        await db.log_download(u.id, name, True)
        log.info("done %s %.2fs (%.1fMB)", key, time.time() - t0, res.size / 1048576)
    except dl.DownloadFailed as e:
        await db.log_download(u.id, name, False)
        await msg.answer(e.user_msg)
    except TelegramBadRequest as e:
        await db.log_download(u.id, name, False)
        log.exception("telegram error")
        await msg.answer("⚠️ رفض تيليجرام الملف (ربما كبير جداً). جرّب رابطاً آخر.")
    except Exception:  # noqa: BLE001
        await db.log_download(u.id, name, False)
        log.exception("unexpected")
        await msg.answer("⚠️ حدث خطأ غير متوقع، حاول مرة أخرى.")
    finally:
        busy.discard(u.id)
        if res:
            dl.cleanup(res)
        try:
            await wait.delete()
        except TelegramBadRequest:
            pass


# ---------------- الأدمن ----------------
@router.message(Command("stats"))
async def stats(msg: Message):
    if not is_admin(msg.from_user.id):
        return
    s = await db.stats()
    plat = "\n".join(f"  • {p}: {c}" for p, c in s["by_platform"]) or "  —"
    await msg.answer(
        f"📊 <b>الإحصائيات</b>\n👥 المستخدمون: {s['users']} (اليوم +{s['today_users']})\n"
        f"⬇️ التحميلات الناجحة: {s['downloads']} (اليوم {s['today']})\n❌ الفاشلة: {s['failed']}\n"
        f"⚡️ مخزّنة للإرسال الفوري: {s['cached']}\n\n<b>حسب المنصة:</b>\n{plat}")


@router.message(Command("channels"))
async def channels(msg: Message):
    if not is_admin(msg.from_user.id):
        return
    ch = await db.channels()
    txt = "\n".join(f"• {c[0]} — {c[1]}" for c in ch) or "لا توجد قنوات اشتراك إجباري."
    await msg.answer(f"📢 <b>قنوات الاشتراك الإجباري</b>\n{txt}\n\nإضافة: <code>/addch @channel</code>\nقناة خاصة: <code>/addch -100123456 https://t.me/+invite</code>\nحذف: <code>/delch @channel</code>")


@router.message(Command("addch"))
async def addch(msg: Message, bot: Bot):
    if not is_admin(msg.from_user.id):
        return
    parts = msg.text.split()[1:]
    if not parts:
        await msg.answer("الاستخدام: /addch @channel  أو  /addch -100123456 رابط_الدعوة")
        return
    chat = parts[0]
    try:
        info = await bot.get_chat(chat)
        me = await bot.get_chat_member(chat, bot.id)
    except Exception as e:  # noqa: BLE001
        await msg.answer(f"❌ تعذر الوصول للقناة: {e}\nتأكد أن البوت مشرف فيها.")
        return
    link = parts[1] if len(parts) > 1 else (f"https://t.me/{info.username}" if info.username else "")
    await db.add_channel(chat, info.title or chat, link)
    clear_cache()
    warn = "" if me.status in (ChatMemberStatus.ADMINISTRATOR, ChatMemberStatus.CREATOR) else "\n⚠️ البوت ليس مشرفاً، لن يتمكن من فحص الاشتراك."
    await msg.answer(f"✅ أُضيفت القناة: {info.title}{warn}")


@router.message(Command("delch"))
async def delch(msg: Message):
    if not is_admin(msg.from_user.id):
        return
    parts = msg.text.split()[1:]
    ok = bool(parts) and await db.del_channel(parts[0])
    clear_cache()
    await msg.answer("✅ تم الحذف" if ok else "❌ القناة غير موجودة")


@router.message(Command("bcast"))
async def bcast(msg: Message, bot: Bot):
    if not is_admin(msg.from_user.id):
        return
    text = msg.text.partition(" ")[2].strip()
    if not text and not msg.reply_to_message:
        await msg.answer("الاستخدام: /bcast النص  أو بالرد على رسالة")
        return
    users = await db.all_users()
    ok = bad = 0
    for uid in users:
        try:
            if msg.reply_to_message:
                await msg.reply_to_message.copy_to(uid)
            else:
                await bot.send_message(uid, text)
            ok += 1
        except Exception:  # noqa: BLE001
            bad += 1
        await asyncio.sleep(0.04)  # ~25 رسالة/ثانية
    await msg.answer(f"📣 تم الإرسال: {ok} • فشل: {bad}")


async def health_server():
    """خادم صحة بسيط: المنصات المجانية (Render/Koyeb/HF) تطلب منفذ ويب مفتوحاً"""
    port = int(os.getenv("PORT", "0") or 0)
    if not port:
        return
    app = web.Application()
    app.router.add_get("/", lambda r: web.Response(text="ok"))
    app.router.add_get("/health", lambda r: web.Response(text="ok"))
    runner = web.AppRunner(app)
    await runner.setup()
    await web.TCPSite(runner, "0.0.0.0", port).start()
    log.info("health server on :%s", port)


async def main():
    if not config.BOT_TOKEN:
        raise SystemExit("BOT_TOKEN غير مضبوط. انسخ .env.example إلى .env وضع التوكن.")
    await db.init()
    await health_server()
    session = None
    if config.TELEGRAM_API_URL:
        session = AiohttpSession(api=TelegramAPIServer.from_base(config.TELEGRAM_API_URL), timeout=1800)
    elif config.PROXY:
        session = AiohttpSession(proxy=config.PROXY, timeout=600)
    else:
        session = AiohttpSession(timeout=600)
    bot = Bot(config.BOT_TOKEN, session=session, default=DefaultBotProperties(parse_mode=ParseMode.HTML))
    dp = Dispatcher()
    menu.setup(db, gate_fn=missing_channels, sub_kb=sub_kb, sub_text=SUB_TEXT, sem=sem)
    dp.include_router(menu.router)
    dp.include_router(router)
    me = await bot.get_me()
    log.info("البوت يعمل: @%s", me.username)
    try:
        if (await bot.get_my_name()).name != config.BOT_NAME:
            await bot.set_my_name(config.BOT_NAME)
    except Exception:  # noqa: BLE001
        log.warning('تعذر تغيير اسم البوت')
    try:
        from aiogram.types import BotCommand
        await bot.set_my_commands([BotCommand(command='start', description='البداية'), BotCommand(command='tools', description='🧰 قائمة الأدوات'), BotCommand(command='id', description='🆔 آيديك'), BotCommand(command='cancel', description='إلغاء العملية')])
    except Exception:  # noqa: BLE001
        pass
    await bot.delete_webhook(drop_pending_updates=True)
    await dp.start_polling(bot, handle_as_tasks=True, allowed_updates=["message", "callback_query"])


if __name__ == "__main__":
    try:
        import uvloop
        uvloop.install()
    except ImportError:
        pass
    asyncio.run(main())

import asyncio
import logging
import os
import shutil
import time
import uuid
from pathlib import Path

from aiogram import Bot, Dispatcher, F, Router
from aiogram.client.default import DefaultBotProperties
from aiogram.client.session.aiohttp import AiohttpSession
from aiogram.enums import ParseMode
from aiogram.exceptions import TelegramBadRequest, TelegramRetryAfter
from aiogram.filters import CommandStart
from aiogram.types import BufferedInputFile, CallbackQuery, FSInputFile, InlineKeyboardButton, InlineKeyboardMarkup, Message
from aiohttp import web
from dotenv import load_dotenv

import process

load_dotenv()
TOKEN = os.getenv("BOT_TOKEN", "").strip()
MAX_DURATION = int(os.getenv("MAX_DURATION", "180"))
MAX_PARALLEL = int(os.getenv("MAX_PARALLEL", "2"))
PROXY = os.getenv("PROXY", "").strip() or None
WORK = Path(os.getenv("WORK_DIR", str(Path(__file__).parent / "work")))
WORK.mkdir(parents=True, exist_ok=True)
MAX_DL = 20 * 1024 * 1024  # حد تنزيل الملفات للبوتات عبر Bot API

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("bot")
router = Router()
sem = asyncio.Semaphore(MAX_PARALLEL)
JOBS: dict[str, dict] = {}  # token -> {uid, path, ts}
BUSY: set[int] = set()

WELCOME = (
    "مرحبا بك\n\n"
    "💡 قم برسل الفيديو • يتم تعليه الدقه على HDR •🧬  وتحسين مع تاثيرات • 🔆\n\n"
    "🪄 ارسل الفيديو من فضلك"
)
CHOOSE = "اختار الدقه المناسبه 🪄"


def fmt(sec: float) -> str:
    s = int(sec)
    return f"{s // 60}:{s % 60:02d}"


def bar(p: float) -> str:
    n = int(p * 10)
    return "▰" * n + "▱" * (10 - n)


def wait_text(elapsed: float, p: float | None = None, queued: bool = False) -> str:
    t = f"✅ تم استلام طلبك بنجاح\n\n⏱ مدة الابداع : {fmt(elapsed)}\n\n🪄 انتظر حتى المعالجة"
    if queued:
        t += "\n\n⌛ في قائمة الانتظار..."
    elif p is not None:
        t += f"\n\n{bar(p)} {int(p * 100)}%"
    return t


def kb(token: str) -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup(inline_keyboard=[
        [InlineKeyboardButton(text="🎨 الوان وتحسين", callback_data=f"m:color:{token}")],
        [InlineKeyboardButton(text="🌈 HDR عالية دقة", callback_data=f"m:hdr:{token}")],
        [InlineKeyboardButton(text="🖥 تفعيل دقة 2k", callback_data=f"m:2k:{token}")],
    ])


@router.message(CommandStart())
async def start(msg: Message):
    await msg.answer(WELCOME)


def _video_of(m: Message):
    if m.video:
        return m.video.file_id, m.video.file_size, m.video.duration
    if m.animation:
        return m.animation.file_id, m.animation.file_size, m.animation.duration
    if m.video_note:
        return m.video_note.file_id, m.video_note.file_size, m.video_note.duration
    if m.document and (m.document.mime_type or "").startswith("video/"):
        return m.document.file_id, m.document.file_size, None
    return None


@router.message(F.video | F.animation | F.video_note | F.document)
async def on_video(msg: Message, bot: Bot):
    v = _video_of(msg)
    if not v:
        return await msg.answer("🪄 ارسل الفيديو من فضلك")
    fid, size, dur = v
    if size and size > MAX_DL:
        return await msg.answer("⚠️ حجم الفيديو أكبر من 20MB (حد تيليجرام للبوتات). أرسل فيديو أصغر أو قصّه.")
    if dur and dur > MAX_DURATION:
        return await msg.answer(f"⚠️ الفيديو أطول من {MAX_DURATION // 60} دقائق. أرسل فيديو أقصر.")
    token = uuid.uuid4().hex[:10]
    d = WORK / token
    d.mkdir(parents=True, exist_ok=True)
    path = d / "in.mp4"
    try:
        await bot.download(fid, destination=path)
    except Exception:  # noqa: BLE001
        shutil.rmtree(d, ignore_errors=True)
        log.exception("download failed")
        return await msg.answer("⚠️ تعذّر تنزيل الفيديو، أعد إرساله.")
    # ننظف طلباً قديماً لنفس المستخدم لم يُختر له شيء
    for k, j in list(JOBS.items()):
        if j["uid"] == msg.from_user.id and not j.get("running"):
            shutil.rmtree(WORK / k, ignore_errors=True)
            JOBS.pop(k, None)
    JOBS[token] = {"uid": msg.from_user.id, "path": str(path), "ts": time.time()}
    await msg.answer(CHOOSE, reply_markup=kb(token))


async def safe_edit(msg: Message, text: str):
    try:
        await msg.edit_text(text)
    except TelegramRetryAfter as e:
        await asyncio.sleep(min(e.retry_after, 3))
    except TelegramBadRequest:
        pass


@router.callback_query(F.data.startswith("m:"))
async def on_mode(cb: CallbackQuery, bot: Bot):
    _, mode, token = cb.data.split(":")
    job = JOBS.get(token)
    uid = cb.from_user.id
    if not job or job["uid"] != uid or not Path(job["path"]).exists():
        await cb.answer("انتهت صلاحية الطلب، أرسل الفيديو من جديد", show_alert=True)
        return
    if job.get("running"):
        return await cb.answer("طلبك قيد المعالجة")
    if uid in BUSY:
        return await cb.answer("لديك معالجة قيد التنفيذ، انتظر حتى تنتهي", show_alert=True)
    job["running"] = True
    BUSY.add(uid)
    await cb.answer()
    msg = cb.message
    t0 = time.time()
    state = {"p": 0.0, "queued": sem.locked(), "done": False}
    await safe_edit(msg, wait_text(0, queued=state["queued"]))

    async def ticker():
        last = ""
        while not state["done"]:
            await asyncio.sleep(1.5)
            txt = wait_text(time.time() - t0, state["p"], state["queued"])
            if txt != last:
                last = txt
                await safe_edit(msg, txt)

    tick = asyncio.create_task(ticker())
    out = Path(job["path"]).with_name("out.mp4")

    async def prog(p: float):
        state["p"] = p

    try:
        async with sem:
            state["queued"] = False
            info = await process.enhance(job["path"], str(out), mode, prog)
        state["done"] = True
        elapsed = time.time() - t0
        caption = f"تم اكمال طلبك بنجاح ✅\n\nالمده اللي انتهت : {fmt(elapsed)}\n\nشكرا على استخدامك ♥️"
        await safe_edit(msg, wait_text(elapsed, 1.0))
        await bot.send_video(msg.chat.id, FSInputFile(out), caption=caption, width=info["w"], height=info["h"], duration=int(info["dur"]), supports_streaming=True)
        try:
            await msg.delete()
        except TelegramBadRequest:
            pass
    except Exception:  # noqa: BLE001
        state["done"] = True
        log.exception("processing failed")
        await safe_edit(msg, "⚠️ تعذّرت معالجة هذا الفيديو. جرّب فيديو آخر أو صيغة مختلفة.")
    finally:
        state["done"] = True
        tick.cancel()
        BUSY.discard(uid)
        JOBS.pop(token, None)
        shutil.rmtree(WORK / token, ignore_errors=True)


@router.message()
async def other(msg: Message):
    await msg.answer("🪄 ارسل الفيديو من فضلك")


async def health():
    port = int(os.getenv("PORT", "0") or 0)
    if not port:
        return
    app = web.Application()
    app.router.add_get("/", lambda r: web.Response(text="ok"))
    app.router.add_get("/health", lambda r: web.Response(text="ok"))
    runner = web.AppRunner(app)
    await runner.setup()
    await web.TCPSite(runner, "0.0.0.0", port).start()


async def main():
    if not TOKEN:
        raise SystemExit("BOT_TOKEN غير مضبوط (.env)")
    await health()
    session = AiohttpSession(proxy=PROXY, timeout=900) if PROXY else AiohttpSession(timeout=900)
    bot = Bot(TOKEN, session=session, default=DefaultBotProperties(parse_mode=ParseMode.HTML))
    dp = Dispatcher()
    dp.include_router(router)
    me = await bot.get_me()
    log.info("running @%s", me.username)
    await bot.delete_webhook(drop_pending_updates=True)
    await dp.start_polling(bot, handle_as_tasks=True, allowed_updates=["message", "callback_query"])


if __name__ == "__main__":
    try:
        import uvloop
        uvloop.install()
    except ImportError:
        pass
    asyncio.run(main())

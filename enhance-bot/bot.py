import asyncio
import logging
import os
import re
import shutil
import time
import uuid
from pathlib import Path

from aiogram import Bot, Dispatcher, F, Router
from aiogram.client.default import DefaultBotProperties
from aiogram.client.session.aiohttp import AiohttpSession
from aiogram.enums import ParseMode
from aiogram.exceptions import TelegramBadRequest, TelegramRetryAfter
from aiogram.filters import Command, CommandStart
from aiogram.types import BufferedInputFile, CallbackQuery, FSInputFile, InlineKeyboardButton, InlineKeyboardMarkup, Message
from aiohttp import web
from dotenv import load_dotenv

import art
import compose
import effects as E
import process

load_dotenv()
TOKEN = os.getenv("BOT_TOKEN", "").strip()
MAX_DURATION = int(os.getenv("MAX_DURATION", "180"))
MAX_PARALLEL = int(os.getenv("MAX_PARALLEL", "2"))
PROXY = os.getenv("PROXY", "").strip() or None
WORK = Path(os.getenv("WORK_DIR", str(Path(__file__).parent / "work")))
WORK.mkdir(parents=True, exist_ok=True)
MAX_DL = 20 * 1024 * 1024
TTL = 1800
PAGE = 16

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("bot")
router = Router()
sem = asyncio.Semaphore(MAX_PARALLEL)
pv_sem = asyncio.Semaphore(3)
JOBS: dict[int, dict] = {}

WELCOME = (
    "مرحبا بك\n\n"
    "💡 قم برسل الفيديو • يتم تعليه الدقه على HDR •🧬  وتحسين مع تاثيرات • 🔆\n\n"
    "🪄 ارسل الفيديو من فضلك"
)
MENU_TEXT = "🎛 <b>استوديو المونتاج</b>\nاختر تأثيرات وأدوات (يمكنك اختيار أكثر من واحد) وسأريك شكلها على فيديوك قبل التطبيق 🪄"


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


def _kb(rows: list[list[tuple[str, str]]]) -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup(inline_keyboard=[[InlineKeyboardButton(text=t, callback_data=d) for t, d in r] for r in rows])


def main_kb(job: dict) -> InlineKeyboardMarkup:
    items = [(f"{E.CATS[c][0]} {E.CATS[c][1]}", f"c:{c}:0") for c in E.CATS]
    rows = [items[i:i + 2] for i in range(0, len(items), 2)]
    n = len(job["stack"])
    rows.append([(f"📋 المختارة ({n})", "lst"), ("🗑 مسح الكل", "clr")])
    rows.append([("🚀 ابدأ المعالجة", "go")])
    return _kb(rows)


def stack_text(job: dict) -> str:
    if not job["stack"]:
        return "لم تختر شيئاً بعد."
    return "\n".join(f"{i}. {E.EFFECTS[e].title}" for i, (e, _) in enumerate(job["stack"], 1))


# ---------------- الاستقبال ----------------
@router.message(CommandStart())
async def start(msg: Message):
    await msg.answer(WELCOME)


@router.message(Command("menu"))
async def menu_cmd(msg: Message):
    job = JOBS.get(msg.from_user.id)
    if not job:
        return await msg.answer(WELCOME)
    await msg.answer(MENU_TEXT, reply_markup=main_kb(job))


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


def drop_job(uid: int):
    j = JOBS.pop(uid, None)
    if j:
        shutil.rmtree(j["dir"], ignore_errors=True)


async def _sh(*args) -> bool:
    p = await asyncio.create_subprocess_exec(*args, stdout=asyncio.subprocess.DEVNULL, stderr=asyncio.subprocess.DEVNULL)
    await p.wait()
    return p.returncode == 0


@router.message(F.video | F.animation | F.video_note | (F.document & F.document.mime_type.startswith("video/")))
async def on_video(msg: Message, bot: Bot):
    uid = msg.from_user.id
    old = JOBS.get(uid)
    if old and old.get("running"):
        return await msg.answer("⏳ لديك معالجة قيد التنفيذ، انتظر حتى تنتهي.")
    v = _video_of(msg)
    fid, size, dur = v
    if size and size > MAX_DL:
        return await msg.answer("⚠️ حجم الفيديو أكبر من 20MB (حد تيليجرام للبوتات). أرسل فيديو أصغر أو قصّه.")
    if dur and dur > MAX_DURATION:
        return await msg.answer(f"⚠️ الفيديو أطول من {MAX_DURATION // 60} دقائق. أرسل فيديو أقصر.")
    drop_job(uid)
    d = WORK / f"{uid}_{uuid.uuid4().hex[:6]}"
    d.mkdir(parents=True, exist_ok=True)
    src = d / "in.mp4"
    note = await msg.answer("⏳ جارٍ تجهيز الفيديو...")
    try:
        await bot.download(fid, destination=src)
        meta_raw = await process.probe(str(src))
        w, h, dd, has_a = process.info(meta_raw)
        if not w or not h or dd <= 0:
            raise ValueError("bad")
        if dd > MAX_DURATION:
            await note.edit_text(f"⚠️ الفيديو أطول من {MAX_DURATION // 60} دقائق.")
            shutil.rmtree(d, ignore_errors=True)
            return
        # نسخة مصغّرة لمعاينة التأثيرات بسرعة (360 على الجانب القصير، 3 ثوانٍ)
        proxy = d / "proxy.mp4"
        ss = max(0.0, min(dd / 2 - 1.5, dd - 3.0)) if dd > 3 else 0
        ok = await _sh("ffmpeg", "-y", "-loglevel", "error", "-ss", f"{ss:.2f}", "-t", "3", "-i", str(src), "-an",
                       "-vf", "scale='if(gt(iw,ih),-2,360)':'if(gt(iw,ih),360,-2)',scale=trunc(iw/2)*2:trunc(ih/2)*2,fps=25", "-c:v", "libx264", "-preset", "ultrafast", "-crf", "20", "-pix_fmt", "yuv420p", str(proxy))
        pm = process.info(await process.probe(str(proxy)))
        if not ok or not pm[0]:
            raise ValueError("proxy")
        before = d / "before.jpg"
        await _sh("ffmpeg", "-y", "-loglevel", "error", "-ss", "1.0", "-i", str(proxy), "-frames:v", "1", "-q:v", "3", str(before))
    except Exception:  # noqa: BLE001
        log.exception("prepare failed")
        shutil.rmtree(d, ignore_errors=True)
        try:
            await note.edit_text("⚠️ تعذّر قراءة هذا الفيديو، جرّب فيديو آخر.")
        except TelegramBadRequest:
            pass
        return
    JOBS[uid] = {"uid": uid, "dir": d, "src": str(src), "meta": {"w": w, "h": h, "dur": dd, "audio": has_a},
                 "pmeta": {"w": pm[0], "h": pm[1], "dur": pm[2], "audio": False}, "proxy": str(proxy), "before": before.read_bytes() if before.exists() else b"",
                 "stack": [], "cand": None, "pend": None, "ts": time.time(), "running": False}
    try:
        await note.delete()
    except TelegramBadRequest:
        pass
    await msg.answer(f"✅ استلمت الفيديو ({w}×{h} • {fmt(dd)})\n\n" + MENU_TEXT, reply_markup=main_kb(JOBS[uid]))


# ---------------- القوائم ----------------
async def replace(msg: Message, text: str, kb: InlineKeyboardMarkup | None):
    try:
        await msg.edit_text(text, reply_markup=kb)
    except TelegramBadRequest:
        await msg.answer(text, reply_markup=kb)
        try:
            await msg.delete()
        except TelegramBadRequest:
            pass


def need_job(cb: CallbackQuery):
    j = JOBS.get(cb.from_user.id)
    if not j or time.time() - j["ts"] > TTL or not Path(j["src"]).exists():
        return None
    j["ts"] = time.time()
    return j


@router.callback_query(F.data == "menu")
async def cb_menu(cb: CallbackQuery):
    j = need_job(cb)
    if not j:
        return await cb.answer("انتهت الجلسة، أرسل الفيديو من جديد", show_alert=True)
    await cb.answer()
    j["pend"] = None
    await replace(cb.message, MENU_TEXT + "\n\n<b>المختارة:</b>\n" + stack_text(j), main_kb(j))


@router.callback_query(F.data.startswith("c:"))
async def cb_cat(cb: CallbackQuery):
    j = need_job(cb)
    if not j:
        return await cb.answer("انتهت الجلسة، أرسل الفيديو من جديد", show_alert=True)
    _, cat, page = cb.data.split(":")
    page = int(page)
    effs = E.by_cat(cat)
    chunk = effs[page * PAGE:(page + 1) * PAGE]
    items = [(e.title, f"e:{e.id}") for e in chunk]
    rows = [items[i:i + 2] for i in range(0, len(items), 2)]
    nav = []
    if page > 0:
        nav.append(("◀️ السابق", f"c:{cat}:{page - 1}"))
    if (page + 1) * PAGE < len(effs):
        nav.append(("التالي ▶️", f"c:{cat}:{page + 1}"))
    if nav:
        rows.append(nav)
    rows.append([("↩️ القائمة", "menu")])
    await cb.answer()
    await replace(cb.message, f"{E.CATS[cat][0]} <b>{E.CATS[cat][1]}</b> ({len(effs)})\nاضغط على أي تأثير لأريك شكله على فيديوك:", _kb(rows))


@router.callback_query(F.data == "lst")
async def cb_list(cb: CallbackQuery):
    j = need_job(cb)
    if not j:
        return await cb.answer("انتهت الجلسة", show_alert=True)
    await cb.answer("المختارة:\n" + stack_text(j), show_alert=True)


@router.callback_query(F.data == "clr")
async def cb_clear(cb: CallbackQuery):
    j = need_job(cb)
    if not j:
        return await cb.answer("انتهت الجلسة", show_alert=True)
    j["stack"] = []
    await cb.answer("تم المسح")
    await replace(cb.message, MENU_TEXT, main_kb(j))


# ---------------- اختيار تأثير ----------------
def parse_range(text: str):
    xs = re.findall(r"\d+(?::\d+)*(?:[.,]\d+)?", text.translate(str.maketrans("٠١٢٣٤٥٦٧٨٩", "0123456789")))
    if len(xs) < 2:
        return None

    def t(s):
        s = s.replace(",", ".")
        v = 0.0
        for x in s.split(":"):
            v = v * 60 + float(x)
        return v
    a, b = t(xs[0]), t(xs[1])
    return (a, b) if b > a else None


async def ask_step(msg: Message, j: dict):
    pend = j["pend"]
    e = E.EFFECTS[pend["eid"]]
    step = e.steps[pend["i"]]
    kind, prompt = step[0], step[1]
    if kind == "choice":
        rows = [[(lab, f"s:{k}")] for k, (lab, _) in enumerate(step[2])]
        rows.append([("↩️ إلغاء", "menu")])
        await msg.answer(prompt, reply_markup=_kb(rows))
    else:
        await msg.answer(prompt, reply_markup=_kb([[("↩️ إلغاء", "menu")]]))


@router.callback_query(F.data.startswith("e:"))
async def cb_effect(cb: CallbackQuery, bot: Bot):
    j = need_job(cb)
    if not j:
        return await cb.answer("انتهت الجلسة، أرسل الفيديو من جديد", show_alert=True)
    eid = cb.data[2:]
    e = E.EFFECTS.get(eid)
    if not e:
        return await cb.answer()
    await cb.answer()
    j["cand"] = None
    if e.steps:
        j["pend"] = {"eid": eid, "i": 0, "params": {}}
        return await ask_step(cb.message, j)
    await preview(bot, cb.message.chat.id, j, eid, {})


@router.callback_query(F.data.startswith("s:"))
async def cb_choice(cb: CallbackQuery, bot: Bot):
    j = need_job(cb)
    if not j or not j.get("pend"):
        return await cb.answer("انتهت الجلسة", show_alert=True)
    await cb.answer()
    pend = j["pend"]
    e = E.EFFECTS[pend["eid"]]
    step = e.steps[pend["i"]]
    val = step[2][int(cb.data[2:])][1]
    pend["params"][step[3]] = val
    await advance(bot, cb.message, j)


async def advance(bot: Bot, msg: Message, j: dict):
    pend = j["pend"]
    pend["i"] += 1
    e = E.EFFECTS[pend["eid"]]
    if pend["i"] < len(e.steps):
        return await ask_step(msg, j)
    eid, params = pend["eid"], pend["params"]
    j["pend"] = None
    await preview(bot, msg.chat.id, j, eid, params)


async def preview(bot: Bot, chat: int, j: dict, eid: str, params: dict):
    e = E.EFFECTS[eid]
    j["cand"] = (eid, params)
    kb = _kb([[("✅ إضافة هذا التأثير", "ok")], [("↩️ رجوع للقائمة", "menu")]])
    head = f"<b>{e.title}</b>\n"
    if e.noprev or e.cat == "res":
        txt = head + (e.desc or "لا توجد صورة معاينة لهذا النوع (يظهر أثره في الفيديو النهائي).") + "\n\nهل تريد إضافته؟"
        if e.cat == "res":
            txt = head + "يرفع/يضبط دقة الفيديو النهائي مع توضيح وتقليل تشويش. (الرفع يكبّر ويحدّد الحواف ولا يضيف تفاصيل غير موجودة)\n\nهل تريد إضافته؟"
        await bot.send_message(chat, txt, reply_markup=kb)
        return
    wait = await bot.send_message(chat, "🪄 جارٍ تجهيز المعاينة...")
    tmp = j["dir"] / f"pv_{uuid.uuid4().hex[:5]}"
    tmp.mkdir(exist_ok=True)
    try:
        async with pv_sem:
            if e.motion:
                out = tmp / "p.mp4"
                await compose.render(j["proxy"], str(out), [(eid, params)], j["pmeta"], tmp, "pclip")
                await bot.send_animation(chat, FSInputFile(out), caption=head + "هذه معاينة متحركة على فيديوك 👆\n\nهل يعجبك؟", reply_markup=kb)
            else:
                out = tmp / "p.png"
                await compose.render(j["proxy"], str(out), [(eid, params)], j["pmeta"], tmp, "pimg")
                sheet = await asyncio.to_thread(art.before_after, j["before"], out.read_bytes()) if j["before"] else out.read_bytes()
                await bot.send_photo(chat, BufferedInputFile(sheet, "preview.jpg"), caption=head + "هكذا سيبدو فيديوك 👆\n\nهل يعجبك؟", reply_markup=kb)
    except Exception:  # noqa: BLE001
        log.exception("preview failed for %s", eid)
        await bot.send_message(chat, head + "تعذّرت المعاينة لكن يمكنك إضافة التأثير.", reply_markup=kb)
    finally:
        shutil.rmtree(tmp, ignore_errors=True)
        try:
            await wait.delete()
        except TelegramBadRequest:
            pass


async def edit_any(msg: Message, text: str, kb: InlineKeyboardMarkup | None):
    try:
        if msg.photo or msg.animation or msg.video:
            await msg.edit_caption(caption=text, reply_markup=kb)
        else:
            await msg.edit_text(text, reply_markup=kb)
    except TelegramBadRequest:
        await msg.answer(text, reply_markup=kb)


@router.callback_query(F.data == "ok")
async def cb_ok(cb: CallbackQuery):
    j = need_job(cb)
    if not j or not j.get("cand"):
        return await cb.answer("انتهت الجلسة", show_alert=True)
    eid, params = j["cand"]
    j["cand"] = None
    j["stack"].append((eid, params))
    await cb.answer("✅ أُضيف")
    await edit_any(cb.message, f"✅ أُضيف: <b>{E.EFFECTS[eid].title}</b>\n\n<b>المختارة:</b>\n{stack_text(j)}",
                   _kb([[("➕ تأثير آخر", "menu")], [("🚀 ابدأ المعالجة", "go")], [("🗑 مسح الكل", "clr")]]))


# ---------------- مدخلات الخطوات (نص/صوت) ----------------
def _has_pend(m: Message) -> bool:
    j = JOBS.get(m.from_user.id) if m.from_user else None
    return bool(j and j.get("pend"))


@router.message(_has_pend)
async def on_step_input(msg: Message, bot: Bot):
    j = JOBS[msg.from_user.id]
    pend = j["pend"]
    e = E.EFFECTS[pend["eid"]]
    step = e.steps[pend["i"]]
    kind = step[0]
    if kind == "text":
        if not msg.text or msg.text.startswith("/"):
            return await msg.answer("✍️ أرسل نصاً.")
        if len(msg.text) > 200:
            return await msg.answer("النص طويل (الحد 200 حرف).")
        pend["params"]["text"] = msg.text.strip()
    elif kind == "range":
        if not msg.text:
            return await msg.answer("✍️ أرسل نصاً مثل: 0:05 0:20")
        r = parse_range(msg.text)
        if not r:
            return await msg.answer("⚠️ اكتب البداية ثم النهاية (النهاية بعد البداية). مثال: 0:05 0:20")
        if r[0] >= j["meta"]["dur"]:
            return await msg.answer(f"⚠️ البداية بعد نهاية الفيديو (مدته {fmt(j['meta']['dur'])}).")
        pend["params"]["range"] = (r[0], min(r[1], j["meta"]["dur"]))
    elif kind == "audio":
        a = msg.audio or msg.voice or (msg.document if msg.document and (msg.document.mime_type or "").startswith("audio/") else None)
        if not a:
            return await msg.answer("🎧 أرسل ملفاً صوتياً (MP3 أو رسالة صوتية).")
        if a.file_size and a.file_size > MAX_DL:
            return await msg.answer("⚠️ الملف أكبر من 20MB.")
        dest = j["dir"] / f"mus_{uuid.uuid4().hex[:5]}"
        await bot.download(a.file_id, destination=dest)
        pend["params"]["audio"] = str(dest)
    else:
        return
    await advance(bot, msg, j)


# ---------------- المعالجة ----------------
async def safe_edit(msg: Message, text: str):
    try:
        await msg.edit_text(text)
    except TelegramRetryAfter as e:
        await asyncio.sleep(min(e.retry_after, 3))
    except TelegramBadRequest:
        pass


@router.callback_query(F.data == "go")
async def cb_go(cb: CallbackQuery, bot: Bot):
    j = need_job(cb)
    if not j:
        return await cb.answer("انتهت الجلسة، أرسل الفيديو من جديد", show_alert=True)
    if j.get("running"):
        return await cb.answer("طلبك قيد المعالجة")
    if not j["stack"]:
        return await cb.answer("اختر تأثيراً أو أداة واحدة على الأقل", show_alert=True)
    j["running"] = True
    await cb.answer()
    msg = cb.message
    t0 = time.time()
    state = {"p": 0.0, "queued": sem.locked(), "done": False}
    wait = await bot.send_message(msg.chat.id, wait_text(0, queued=state["queued"]))
    kinds = {"out_gif": ("gif", "result.gif"), "out_mp3": ("mp3", "result.mp3"), "out_cover": ("cover", "cover.jpg")}
    out_kind = next((kinds[e][0] for e, _ in j["stack"] if e in kinds), "video")
    out = j["dir"] / {"gif": "out.gif", "mp3": "out.mp3", "cover": "out.jpg"}.get(out_kind, "out.mp4")

    async def ticker():
        last = ""
        while not state["done"]:
            await asyncio.sleep(1.5)
            txt = wait_text(time.time() - t0, state["p"], state["queued"])
            if txt != last:
                last = txt
                await safe_edit(wait, txt)

    tick = asyncio.create_task(ticker())

    async def prog(p: float):
        state["p"] = p

    try:
        async with sem:
            state["queued"] = False
            res = await compose.render(j["src"], str(out), j["stack"], j["meta"], j["dir"], "final", prog)
        state["done"] = True
        elapsed = time.time() - t0
        if res["size"] > 49 * 1024 * 1024:
            raise compose.UserError("الناتج أكبر من 50MB (حد تيليجرام). جرّب تقصير الفيديو أو دقة أقل.")
        caption = f"تم اكمال طلبك بنجاح ✅\n\nالمده اللي انتهت : {fmt(elapsed)}\n\nشكرا على استخدامك ♥️"
        await safe_edit(wait, wait_text(elapsed, 1.0))
        chat = msg.chat.id
        if out_kind == "gif":
            await bot.send_animation(chat, FSInputFile(out), caption=caption)
        elif out_kind == "mp3":
            await bot.send_audio(chat, FSInputFile(out), caption=caption)
        elif out_kind == "cover":
            await bot.send_photo(chat, FSInputFile(out), caption=caption)
        else:
            await bot.send_video(chat, FSInputFile(out), caption=caption, width=res["w"], height=res["h"], duration=int(res["dur"]), supports_streaming=True)
        try:
            await wait.delete()
        except TelegramBadRequest:
            pass
        j["stack"] = []
        await bot.send_message(chat, "🎛 تريد تعديلاً آخر على نفس الفيديو؟", reply_markup=main_kb(j))
    except compose.UserError as e:
        state["done"] = True
        await safe_edit(wait, f"⚠️ {e}")
    except Exception:  # noqa: BLE001
        state["done"] = True
        log.exception("processing failed")
        await safe_edit(wait, "⚠️ تعذّرت معالجة هذا الفيديو بهذه التأثيرات. جرّب تأثيرات أقل أو فيديو آخر.")
    finally:
        state["done"] = True
        tick.cancel()
        j["running"] = False
        if out.exists():
            out.unlink(missing_ok=True)


@router.message()
async def other(msg: Message):
    if JOBS.get(msg.from_user.id):
        return await msg.answer("اضغط /menu لعرض القائمة، أو أرسل فيديو جديداً 🪄")
    await msg.answer("🪄 ارسل الفيديو من فضلك")


async def cleaner():
    while True:
        await asyncio.sleep(300)
        now = time.time()
        for uid, j in list(JOBS.items()):
            if not j.get("running") and now - j["ts"] > TTL:
                drop_job(uid)


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
    try:
        from aiogram.types import BotCommand
        await bot.set_my_commands([BotCommand(command="start", description="البداية"), BotCommand(command="menu", description="🎛 قائمة التأثيرات")])
    except Exception:  # noqa: BLE001
        pass
    asyncio.create_task(cleaner())
    await bot.delete_webhook(drop_pending_updates=True)
    await dp.start_polling(bot, handle_as_tasks=True, allowed_updates=["message", "callback_query"])


if __name__ == "__main__":
    try:
        import uvloop
        uvloop.install()
    except ImportError:
        pass
    asyncio.run(main())

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
from aiogram.exceptions import TelegramBadRequest, TelegramNetworkError, TelegramRetryAfter
from aiogram.filters import Command, CommandStart
from aiogram.types import BufferedInputFile, CallbackQuery, FSInputFile, InlineKeyboardButton, InlineKeyboardMarkup, InputMediaVideo, Message, WebAppInfo
from aiohttp import web
from dotenv import load_dotenv

import art
import compose
import db
import effects as E
import process
import autograde
import beats
import designer
import secrets
import webapp
from templates import TEMPLATE_SETS, TEMPLATES

load_dotenv()
TOKEN = os.getenv("BOT_TOKEN", "").strip()
MAX_DURATION = int(os.getenv("MAX_DURATION", "180"))
MAX_PARALLEL = int(os.getenv("MAX_PARALLEL", "2"))
PROXY = os.getenv("PROXY", "").strip() or None
ADMIN_IDS = {int(x) for x in os.getenv("ADMIN_IDS", "").replace(" ", "").split(",") if x.strip().isdigit()}
WORK = Path(os.getenv("WORK_DIR", str(Path(__file__).parent / "work")))
WORK.mkdir(parents=True, exist_ok=True)
MAX_DL = 20 * 1024 * 1024
TTL = 1800
PAGE = 12
WEBAPP_URL = os.getenv("WEBAPP_URL", "").strip().rstrip("/")
DZ_PAGE = 6

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
HOME_CATS = ["auto", "ed", "cv", "hdr", "col", "edit", "fx", "part", "res", "aud", "out"]


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


def _kb(rows: list) -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup(inline_keyboard=[[x if isinstance(x, InlineKeyboardButton) else InlineKeyboardButton(text=x[0], callback_data=x[1]) for x in r] for r in rows])


# ---------------- لوحات الأزرار تحت الفيديو ----------------
def kb_home(j: dict) -> InlineKeyboardMarkup:
    rows = []
    rec = j.get("rec") if not j["stack"] else None
    if rec:
        rows.append([(f"💡 مقترح: {E.EFFECTS[e].title}", f"e:{e}") for e in rec[:2]])
    rows.append([("📱 دقة الآيفون", "e:cv_iphone"), ("📱 آيفون برو 2K", "e:cv_iphone_2k")])
    rows.append([("🏆 قوالب مصممين (150)", "dz")])
    rows.append([("🎞 قوالب الأجواء (25)", "tpl:l"), ("🔥 قوالب الإيديت (12)", "tpl:e")])
    cats = [(f"{E.CATS[c][0]} {E.CATS[c][1]}", f"c:{c}:0") for c in HOME_CATS]
    rows += [cats[i:i + 2] for i in range(0, len(cats), 2)]
    rows.append(text_row(j))
    n = len(j["stack"])
    rows.append([(f"📋 المختارة ({n})", "stk"), ("↩️ تراجع", "u"), ("↪️ إعادة", "redo")])
    rows.append([("🗑 مسح الكل", "clr"), ("🚀 معالجة وتصدير", "go")])
    return _kb(rows)


def text_row(j: dict):
    """زر إضافة نص: تطبيق مصغّر داخل تيليجرام إن وُجد WEBAPP_URL وإلا محرر داخل المحادثة"""
    if WEBAPP_URL and j.get("wtoken"):
        return [InlineKeyboardButton(text="🖋 محرر النصوص (تطبيق مصغّر)", web_app=WebAppInfo(url=f"{WEBAPP_URL}/app?t={j['wtoken']}")), InlineKeyboardButton(text="✍️ نص سريع", callback_data="e:text")]
    return [("🖋 إضافة نص", "e:text")]


def kb_cat(cat: str, page: int) -> InlineKeyboardMarkup:
    effs = E.by_cat(cat)
    chunk = effs[page * PAGE:(page + 1) * PAGE]
    items = [(e.title, f"e:{e.id}") for e in chunk]
    rows = [items[i:i + 2] for i in range(0, len(items), 2)]
    nav = []
    if page > 0:
        nav.append(("◀️", f"c:{cat}:{page - 1}"))
    nav.append((f"{page + 1}/{max(1, -(-len(effs) // PAGE))}", "noop"))
    if (page + 1) * PAGE < len(effs):
        nav.append(("▶️", f"c:{cat}:{page + 1}"))
    rows.append(nav)
    rows.append([("↩️ تراجع", "u"), ("🔙 القائمة", "home")])
    rows.append([("🚀 معالجة وتصدير", "go")])
    return _kb(rows)


def recommend(st: dict) -> list[str]:
    """اقتراحات ذكية من تحليل الفيديو"""
    if not st:
        return ["auto_grade"]
    rec = []
    if st["luma"] < 0.3:
        rec.append("cv_lowlight")
    if st["noise"] > 2.5:
        rec.append("cv_denoise")
    if st["sat"] < 0.28:
        rec.append("auto_vivid")
    if st["sharp"] < 120:
        rec.append("cv_sharp")
    if st["p99"] - st["p1"] < 0.55:
        rec.append("cv_hdr")
    rec.append("auto_hdr")
    seen = []
    for e in rec:
        if e not in seen:
            seen.append(e)
    return seen[:2]


def video_info(w, h, dur, has_a, size, st, beat) -> str:
    kb = f"{size / 1048576:.1f}MB" if size else "—"
    lines = [f"📐 {w}×{h} • ⏱ {fmt(dur)} • {'🔊 بصوت' if has_a else '🔇 بلا صوت'} • 📦 {kb}"]
    mbps = (size or 0) * 8 / max(dur, 0.1) / 1e6
    if min(w, h) < 720 or (size and mbps < 1.5):
        lines.append(f"⚠️ الفيديو وصلني مضغوطاً ({min(w, h)}p • {mbps:.1f} Mbps). لأعلى دقة أرسله كـ «ملف» 📎 بدل فيديو، لأن تيليجرام يضغط الفيديو العادي.")
    if st:
        lines.append(f"🔆 السطوع {int(st['luma'] * 100)}% • 🎨 التشبع {int(st['sat'] * 100)}% • 🌫 التشويش {'مرتفع' if st['noise'] > 2.5 else 'منخفض'} • 🔍 الحدة {'ضعيفة' if st['sharp'] < 120 else 'جيدة'}")
    if beat:
        lines.append(f"🥁 إيقاع الصوت: {int(beat['bpm'])} BPM")
    return "\n".join(lines)


def caption(j: dict, note: str = "") -> str:
    st = j["stack"]
    lines = "\n".join(f"{i}. {E.EFFECTS[e].title}" for i, (e, _) in enumerate(st[-8:], max(1, len(st) - 7)))
    head = "🎛 <b>استوديو المونتاج</b> — المعاينة مباشرة 👇" + (f"\n{j['info']}" if j.get("info") and not j["stack"] else "")
    body = f"\n\n<b>المختارة ({len(st)}):</b>\n{lines}" if st else "\n\nاختر تأثيراً أو قالباً من الأزرار تحت الفيديو 🪄"
    tail = f"\n\n{note}" if note else ""
    return (head + body + tail)[:1000]


# ---------------- الاستقبال ----------------
@router.message(CommandStart())
async def start(msg: Message):
    await db.touch_user(msg.from_user.id, msg.from_user.full_name, msg.from_user.username)
    await msg.answer(WELCOME)


HELP = (
    "📖 <b>طريقة الاستخدام</b>\n\n"
    "1️⃣ أرسل فيديو (حتى 20MB و3 دقائق).\n"
    "2️⃣ يظهر لك فيديو معاينة وتحته أزرار. اضغط أي تأثير فيتغيّر الفيديو **مباشرة في نفس الرسالة** (تقدر تشغّله أو تكتم صوته).\n"
    "3️⃣ ✨ <b>تلقائي ذكي</b>: يحلّل ألوان فيديوك ويضبط السطوع والتباين والتشبع والحدة تلقائياً.\n"
    "4️⃣ 🔥 <b>إيديت</b>: فلاش وزوم واهتزاز وقلب وغليتش على <b>إيقاع صوت الفيديو</b> فعلاً، أو أضف موسيقى (✂️ المونتاج ← إضافة موسيقى) وسيتبع الإيقاع موسيقاك.\n"
    "5️⃣ 🎞 <b>القوالب</b>: صورة فيها كل القوالب مطبّقة على فيديوك — اختر رقماً فيُطبَّق دفعة واحدة.\n"
    "6️⃣ ↩️ تراجع / 🗑 مسح — ثم 🚀 <b>معالجة وتصدير</b> للفيديو الكامل بأعلى جودة.\n\n"
    "💡 التأثيرات تتراكم بالترتيب الذي تختاره."
)


@router.message(Command("help"))
async def help_cmd(msg: Message):
    await msg.answer(HELP)


@router.message(Command("id"))
async def my_id(msg: Message):
    await msg.answer(f"🆔 آيديك: <code>{msg.from_user.id}</code>")


@router.message(Command("menu"))
async def menu_cmd(msg: Message, bot: Bot):
    j = JOBS.get(msg.from_user.id)
    if not j:
        return await msg.answer(WELCOME)
    await send_editor(bot, msg.chat.id, j)


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
        webapp.WTOKENS.pop(j.get("wtoken"), None)
        shutil.rmtree(j["dir"], ignore_errors=True)


async def _sh(*args) -> bool:
    p = await asyncio.create_subprocess_exec(*args, stdout=asyncio.subprocess.DEVNULL, stderr=asyncio.subprocess.DEVNULL)
    await p.wait()
    return p.returncode == 0


@router.message(F.video | F.animation | F.video_note | (F.document & F.document.mime_type.startswith("video/")))
async def on_video(msg: Message, bot: Bot):
    uid = msg.from_user.id
    await db.touch_user(uid, msg.from_user.full_name, msg.from_user.username)
    old = JOBS.get(uid)
    if old and old.get("running"):
        return await msg.answer("⏳ لديك معالجة قيد التنفيذ، انتظر حتى تنتهي.")
    fid, size, dur = _video_of(msg)
    if size and size > MAX_DL:
        return await msg.answer("⚠️ حجم الفيديو أكبر من 20MB (حد تيليجرام للبوتات). أرسل فيديو أصغر أو قصّه.")
    if dur and dur > MAX_DURATION:
        return await msg.answer(f"⚠️ الفيديو أطول من {MAX_DURATION // 60} دقائق. أرسل فيديو أقصر.")
    drop_job(uid)
    d = WORK / f"{uid}_{uuid.uuid4().hex[:6]}"
    d.mkdir(parents=True, exist_ok=True)
    src = d / "in.mp4"
    note = await msg.answer("⏳ جاري تجهيز الأدوات الاستوديو ...")
    try:
        for attempt in range(3):  # تيليجرام أحياناً يبطئ؛ نعيد المحاولة بدل الانتظار الطويل
            try:
                await asyncio.wait_for(bot.download(fid, destination=src), 60)
                break
            except (asyncio.TimeoutError, TelegramNetworkError):
                if attempt == 2:
                    raise
                await asyncio.sleep(1)
        await safe_edit(note, "⏳ تجهيز أدوات المونتاج ...")
        w, h, dd, has_a = process.info(await process.probe(str(src)))
        if not w or not h or dd <= 0:
            raise ValueError("bad video")
        if dd > MAX_DURATION:
            await note.edit_text(f"⚠️ الفيديو أطول من {MAX_DURATION // 60} دقائق.")
            shutil.rmtree(d, ignore_errors=True)
            return
        # نسخة مصغّرة (360 على الجانب القصير، 4 ثوانٍ مع الصوت) للمعاينة المباشرة
        proxy = d / "proxy.mp4"
        ss = max(0.0, min(dd / 2 - 2, dd - 4.0)) if dd > 4 else 0
        ok = await _sh("ffmpeg", "-y", "-loglevel", "error", "-ss", f"{ss:.2f}", "-t", "4", "-i", str(src),
                       "-vf", "scale='if(gt(iw,ih),-2,min(540,iw))':'if(gt(iw,ih),min(540,ih),-2)',scale=trunc(iw/2)*2:trunc(ih/2)*2,fps=25",
                       "-map", "0:v:0", *(["-map", "0:a:0", "-c:a", "aac", "-b:a", "64k"] if has_a else ["-an"]),
                       "-c:v", "libx264", "-preset", "veryfast", "-crf", "19", "-pix_fmt", "yuv420p", "-movflags", "+faststart", str(proxy))
        pm = process.info(await process.probe(str(proxy)))
        if not ok or not pm[0]:
            raise ValueError("proxy")
        await safe_edit(note, "⏳ جاري تجهيز الأدوات العامة ...")
        stats = await asyncio.to_thread(autograde.analyze, str(src), dd)
        beat = await beats.analyze(str(src)) if has_a else None
        thumb = d / "thumb.jpg"
        await _sh("ffmpeg", "-y", "-loglevel", "error", "-ss", "1.0", "-i", str(proxy), "-frames:v", "1", "-q:v", "3", str(thumb))
    except Exception:  # noqa: BLE001
        log.exception("prepare failed")
        shutil.rmtree(d, ignore_errors=True)
        try:
            await note.edit_text("⚠️ تعذّر قراءة هذا الفيديو، جرّب فيديو آخر.")
        except TelegramBadRequest:
            pass
        return
    pbeat = dict(beat, phase=(beat["phase"] - ss) % beat["period"]) if beat else False
    j = {"uid": uid, "dir": d, "src": str(src), "meta": {"w": w, "h": h, "dur": dd, "audio": has_a, "stats": stats, "beat": beat or False},
         "pmeta": {"w": pm[0], "h": pm[1], "dur": pm[2], "audio": has_a, "stats": stats, "beat": pbeat}, "proxy": str(proxy), "thumb": str(thumb),
         "stack": [], "redo": [], "pend": None, "ts": time.time(), "running": False, "busy": False, "gallery": {}, "ed": None, "rend": 0,
         "chat": msg.chat.id, "wtoken": secrets.token_urlsafe(9), "dz": {}, "rec": recommend(stats), "info": video_info(w, h, dd, has_a, size, stats, beat)}
    webapp.WTOKENS[j["wtoken"]] = uid
    JOBS[uid] = j
    try:
        await note.delete()
    except TelegramBadRequest:
        pass
    await send_editor(bot, msg.chat.id, j, first=True)


# ---------------- المحرر المباشر (فيديو + أزرار تحته) ----------------
async def make_clip(j: dict) -> str:
    """معاينة المكدس الحالي على النسخة المصغّرة"""
    if not j["stack"]:
        return j["proxy"]
    j["rend"] += 1
    tmp = j["dir"] / f"pv{j['rend']}"
    tmp.mkdir(exist_ok=True)
    out = tmp / "clip.mp4"
    async with pv_sem:
        await compose.render(j["proxy"], str(out), j["stack"], j["pmeta"], tmp, "pclip")
    return str(out)


async def send_editor(bot: Bot, chat: int, j: dict, first: bool = False, note: str = ""):
    try:
        clip = await make_clip(j)
    except Exception:  # noqa: BLE001
        log.exception("editor render failed")
        clip = j["proxy"]
    m = await bot.send_video(chat, FSInputFile(clip), caption=caption(j, note), reply_markup=kb_home(j), supports_streaming=True, width=j["pmeta"]["w"], height=j["pmeta"]["h"])
    if j.get("ed"):
        try:
            await bot.delete_message(chat, j["ed"])
        except TelegramBadRequest:
            pass
    j["ed"] = m.message_id


async def refresh(bot: Bot, chat: int, j: dict, kb: InlineKeyboardMarkup | None = None, note: str = "") -> bool:
    """يستبدل الفيديو في نفس الرسالة (تحرير مباشر) ويُبقي الأزرار"""
    if not j.get("ed"):
        await send_editor(bot, chat, j, note=note)
        return True
    try:
        clip = await make_clip(j)
    except compose.UserError as e:
        await bot.send_message(chat, f"⚠️ {e}")
        return False
    except Exception:  # noqa: BLE001
        log.exception("preview render failed")
        await bot.send_message(chat, "⚠️ تعذّرت معاينة هذا التأثير على فيديوك. جرّب غيره.")
        return False
    media = InputMediaVideo(media=FSInputFile(clip), caption=caption(j, note), width=j["pmeta"]["w"], height=j["pmeta"]["h"], supports_streaming=True)
    try:
        await bot.edit_message_media(media, chat_id=chat, message_id=j["ed"], reply_markup=kb or kb_home(j))
    except TelegramBadRequest as e:
        if "not modified" in str(e).lower():
            return True
        j["ed"] = None
        await send_editor(bot, chat, j, note=note)
    return True


def need_job(cb: CallbackQuery):
    j = JOBS.get(cb.from_user.id)
    if not j or time.time() - j["ts"] > TTL or not Path(j["src"]).exists():
        return None
    j["ts"] = time.time()
    return j


EXPIRED = "انتهت الجلسة، أرسل الفيديو من جديد"


@router.callback_query(F.data == "noop")
async def cb_noop(cb: CallbackQuery):
    await cb.answer()


@router.callback_query(F.data == "home")
async def cb_home(cb: CallbackQuery):
    j = need_job(cb)
    if not j:
        return await cb.answer(EXPIRED, show_alert=True)
    await cb.answer()
    try:
        await cb.message.edit_reply_markup(reply_markup=kb_home(j))
    except TelegramBadRequest:
        pass


@router.callback_query(F.data.startswith("c:"))
async def cb_cat(cb: CallbackQuery):
    j = need_job(cb)
    if not j:
        return await cb.answer(EXPIRED, show_alert=True)
    _, cat, page = cb.data.split(":")
    await cb.answer()
    try:
        await cb.message.edit_reply_markup(reply_markup=kb_cat(cat, int(page)))
    except TelegramBadRequest:
        pass


async def apply_live(cb: CallbackQuery, bot: Bot, j: dict, note: str = ""):
    if j["busy"]:
        return await cb.answer("⏳ لحظة، يتم تطبيق التأثير السابق", show_alert=False)
    j["busy"] = True
    try:
        ok = await refresh(bot, cb.message.chat.id, j, cb.message.reply_markup, note)
        return ok
    finally:
        j["busy"] = False


@router.callback_query(F.data.startswith("e:"))
async def cb_effect(cb: CallbackQuery, bot: Bot):
    j = need_job(cb)
    if not j:
        return await cb.answer(EXPIRED, show_alert=True)
    if j.get("running"):
        return await cb.answer("طلبك قيد المعالجة", show_alert=True)
    eid = cb.data[2:]
    e = E.EFFECTS.get(eid)
    if not e:
        return await cb.answer()
    if e.steps:
        await cb.answer()
        j["pend"] = {"eid": eid, "i": 0, "params": {}, "chat": cb.message.chat.id}
        return await ask_step(cb.message, j)
    await cb.answer(f"⏳ تطبيق: {e.title}")
    j["redo"] = []
    j["stack"].append((eid, {}))
    if not await apply_live(cb, bot, j, f"✅ أُضيف: {e.title}"):
        j["stack"].pop()


@router.callback_query(F.data == "u")
async def cb_undo(cb: CallbackQuery, bot: Bot):
    j = need_job(cb)
    if not j:
        return await cb.answer(EXPIRED, show_alert=True)
    if not j["stack"]:
        return await cb.answer("لا يوجد ما يُتراجع عنه")
    last = j["stack"].pop()
    j["redo"].append((len(j["stack"]), last))
    await cb.answer("↩️ تم التراجع")
    await apply_live(cb, bot, j, f"↩️ أُلغي: {E.EFFECTS[last[0]].title}")


@router.callback_query(F.data == "redo")
async def cb_redo(cb: CallbackQuery, bot: Bot):
    j = need_job(cb)
    if not j:
        return await cb.answer(EXPIRED, show_alert=True)
    if not j["redo"]:
        return await cb.answer("لا يوجد ما يُعاد")
    idx, item = j["redo"].pop()
    j["stack"].insert(min(idx, len(j["stack"])), item)
    await cb.answer("↪️ تمت الإعادة")
    await apply_live(cb, bot, j, f"↪️ أُعيد: {E.EFFECTS[item[0]].title}")


def kb_stack(j: dict) -> InlineKeyboardMarkup:
    rows = [[(f"🗑 {i}. {E.EFFECTS[e].title}", f"del:{i - 1}")] for i, (e, _) in enumerate(j["stack"], 1)]
    if not rows:
        rows = [[("لا توجد تأثيرات مضافة", "noop")]]
    tail = [("↪️ استرجاع المحذوف" + (f" ({len(j['redo'])})" if j["redo"] else ""), "redo")]
    rows += [tail, [("🔙 القائمة", "home")]]
    return _kb(rows)


@router.callback_query(F.data == "stk")
async def cb_stack(cb: CallbackQuery):
    j = need_job(cb)
    if not j:
        return await cb.answer(EXPIRED, show_alert=True)
    await cb.answer()
    try:
        await cb.message.edit_reply_markup(reply_markup=kb_stack(j))
    except TelegramBadRequest:
        pass


@router.callback_query(F.data.regexp(r"^del:\d+$"))
async def cb_del(cb: CallbackQuery, bot: Bot):
    j = need_job(cb)
    if not j:
        return await cb.answer(EXPIRED, show_alert=True)
    i = int(cb.data[4:])
    if not 0 <= i < len(j["stack"]):
        return await cb.answer("تم حذفه")
    item = j["stack"].pop(i)
    j["redo"].append((i, item))
    await cb.answer(f"🗑 حُذف: {E.EFFECTS[item[0]].title}")
    if j["busy"]:
        return
    j["busy"] = True
    try:
        await refresh(bot, cb.message.chat.id, j, kb_stack(j), f"🗑 حُذف: {E.EFFECTS[item[0]].title}")
    finally:
        j["busy"] = False


@router.callback_query(F.data == "clr")
async def cb_clear(cb: CallbackQuery, bot: Bot):
    j = need_job(cb)
    if not j:
        return await cb.answer(EXPIRED, show_alert=True)
    if j["stack"]:
        j["redo"].extend(reversed([(k, it) for k, it in enumerate(j["stack"])]))
    j["stack"] = []
    await cb.answer("🗑 تم المسح")
    await apply_live(cb, bot, j, "🗑 تم مسح كل التأثيرات")


# ---------------- القوالب ----------------
async def build_gallery(j: dict, key: str) -> bytes:
    j.setdefault("gallery", {})
    if key in j["gallery"]:
        return j["gallery"][key]
    title, tpls, cols = TEMPLATE_SETS[key]
    sem3 = asyncio.Semaphore(3)

    async def one(i: int, name: str, stack):
        tmp = j["dir"] / f"g{key}{i}"
        tmp.mkdir(exist_ok=True)
        out = tmp / "t.png"
        async with sem3:
            try:
                st = [(e, dict(p)) for e, p in stack]
                await compose.render(j["proxy"], str(out), st, j["pmeta"], tmp, "pimg")
                return out.read_bytes()
            except Exception:  # noqa: BLE001
                log.exception("template thumb %s", name)
                return Path(j["thumb"]).read_bytes()
    res = await asyncio.gather(*[one(i, n, s_) for i, (n, s_) in enumerate(tpls)])
    titles = [n.split(" ", 1)[1] if " " in n else n for n, _ in tpls]
    j["gallery"][key] = await asyncio.to_thread(art.template_grid, list(res), titles, cols)
    return j["gallery"][key]


def kb_templates(key: str) -> InlineKeyboardMarkup:
    _, tpls, cols = TEMPLATE_SETS[key]
    nums = [(str(i), f"t:{key}:{i}") for i in range(1, len(tpls) + 1)]
    rows = [nums[i:i + cols] for i in range(0, len(nums), cols)]
    rows.append([("🔙 رجوع للمحرر", "tpl_close")])
    return _kb(rows)


@router.callback_query(F.data.startswith("tpl:"))
async def cb_tpl(cb: CallbackQuery, bot: Bot):
    j = need_job(cb)
    if not j:
        return await cb.answer(EXPIRED, show_alert=True)
    key = cb.data[4:]
    if key not in TEMPLATE_SETS:
        return await cb.answer()
    title, tpls, _ = TEMPLATE_SETS[key]
    await cb.answer("🎞 جارٍ تجهيز القوالب على فيديوك...")
    chat = cb.message.chat.id
    wait = await bot.send_message(chat, f"🪄 أطبّق {len(tpls)} قالباً على لقطة من فيديوك، لحظات...")
    try:
        img = await build_gallery(j, key)
        names = "\n".join(f"{i}. {n}" for i, (n, _) in enumerate(tpls, 1))
        await bot.send_photo(chat, BufferedInputFile(img, "templates.jpg"), caption=f"<b>{title}</b>\nاختر رقم القالب من الأزرار 👇\n\n{names}"[:1000], reply_markup=kb_templates(key))
    except Exception:  # noqa: BLE001
        log.exception("gallery failed")
        await bot.send_message(chat, "⚠️ تعذّر تجهيز القوالب، حاول مرة أخرى.")
    finally:
        try:
            await wait.delete()
        except TelegramBadRequest:
            pass


@router.callback_query(F.data == "tpl_close")
async def cb_tpl_close(cb: CallbackQuery):
    await cb.answer()
    try:
        await cb.message.delete()
    except TelegramBadRequest:
        pass


@router.callback_query(F.data.regexp(r"^t:[a-z]:\d+$"))
async def cb_template(cb: CallbackQuery, bot: Bot):
    j = need_job(cb)
    if not j:
        return await cb.answer(EXPIRED, show_alert=True)
    if j.get("running"):
        return await cb.answer("طلبك قيد المعالجة", show_alert=True)
    _, key, num = cb.data.split(":")
    tpls = TEMPLATE_SETS[key][1]
    n = int(num)
    if not 1 <= n <= len(tpls):
        return await cb.answer()
    name, stack = tpls[n - 1]
    await cb.answer(f"⏳ تطبيق القالب: {name}")
    old = list(j["stack"])
    j["stack"] = [(e, dict(p)) for e, p in stack]
    chat = cb.message.chat.id
    j["busy"] = True
    try:
        ok = await refresh(bot, chat, j, None, f"✅ طُبّق القالب: {name}")
    finally:
        j["busy"] = False
    if not ok:
        j["stack"] = old
        return
    try:
        await cb.message.delete()
    except TelegramBadRequest:
        pass


# ---------------- قوالب مصممين (150) ----------------
def kb_dz_families() -> InlineKeyboardMarkup:
    items = [(f"{designer.FAMILY_NAMES[k]} (15)", f"dzf:{k}:0") for k in designer.DESIGNER]
    rows = [items[i:i + 2] for i in range(0, len(items), 2)]
    rows.append([("🔙 القائمة", "home")])
    return _kb(rows)


def kb_dz_page(fam: str, page: int) -> InlineKeyboardMarkup:
    n = len(designer.DESIGNER[fam])
    start = page * DZ_PAGE
    idxs = list(range(start, min(n, start + DZ_PAGE)))
    nums = [(str(i + 1), f"dzt:{fam}:{i}") for i in idxs]
    rows = [nums[i:i + 3] for i in range(0, len(nums), 3)]
    nav = []
    if page > 0:
        nav.append(("◀️ السابق", f"dzf:{fam}:{page - 1}"))
    nav.append((f"{page + 1}/{-(-n // DZ_PAGE)}", "noop"))
    if start + DZ_PAGE < n:
        nav.append(("التالي ▶️", f"dzf:{fam}:{page + 1}"))
    rows.append(nav)
    rows.append([("🏆 كل العائلات", "dzfam"), ("🔙 رجوع للمحرر", "tpl_close")])
    return _kb(rows)


async def dz_page_image(j: dict, fam: str, page: int) -> bytes:
    key = (fam, page)
    if key in j["dz"]:
        return j["dz"][key]
    items = designer.DESIGNER[fam]
    start = page * DZ_PAGE
    chunk = items[start:start + DZ_PAGE]
    sem3 = asyncio.Semaphore(3)

    async def one(k: int, stack):
        tmp = j["dir"] / f"dz_{fam}_{start + k}"
        tmp.mkdir(exist_ok=True)
        out = tmp / "t.png"
        async with sem3:
            try:
                await compose.render(j["proxy"], str(out), [(e, dict(p)) for e, p in stack], j["pmeta"], tmp, "pimg")
                return out.read_bytes()
            except Exception:  # noqa: BLE001
                log.exception("designer thumb %s %s", fam, start + k)
                return Path(j["thumb"]).read_bytes()
    imgs = await asyncio.gather(*[one(k, st) for k, (_, st) in enumerate(chunk)])
    titles = [t for t, _ in chunk]
    portrait = j["pmeta"]["h"] >= j["pmeta"]["w"]
    data = await asyncio.to_thread(art.page_grid, list(imgs), titles, start + 1, 3 if portrait else 2, portrait)
    j["dz"][key] = data
    return data


@router.callback_query(F.data == "dz")
async def cb_dz(cb: CallbackQuery):
    j = need_job(cb)
    if not j:
        return await cb.answer(EXPIRED, show_alert=True)
    await cb.answer()
    try:
        await cb.message.edit_reply_markup(reply_markup=kb_dz_families())
    except TelegramBadRequest:
        pass


@router.callback_query(F.data == "dzfam")
async def cb_dzfam(cb: CallbackQuery, bot: Bot):
    await cb.answer()
    try:
        await cb.message.delete()
    except TelegramBadRequest:
        pass
    j = JOBS.get(cb.from_user.id)
    if j and j.get("ed"):
        try:
            await bot.edit_message_reply_markup(chat_id=cb.message.chat.id, message_id=j["ed"], reply_markup=kb_dz_families())
        except TelegramBadRequest:
            pass


@router.callback_query(F.data.regexp(r"^dzf:[a-z]+:\d+$"))
async def cb_dzf(cb: CallbackQuery, bot: Bot):
    j = need_job(cb)
    if not j:
        return await cb.answer(EXPIRED, show_alert=True)
    _, fam, page = cb.data.split(":")
    page = int(page)
    if fam not in designer.DESIGNER:
        return await cb.answer()
    await cb.answer("🏆 جارٍ تجهيز القوالب على فيديوك...")
    chat = cb.message.chat.id
    caption_ = f"<b>{designer.FAMILY_NAMES[fam]}</b> — صفحة {page + 1}\nاختر رقم القالب من الأزرار 👇"
    try:
        img = await dz_page_image(j, fam, page)
        from_gallery = bool(cb.message.photo)
        if from_gallery:
            from aiogram.types import InputMediaPhoto
            await bot.edit_message_media(InputMediaPhoto(media=BufferedInputFile(img, f"dz{page}.jpg"), caption=caption_), chat_id=chat, message_id=cb.message.message_id, reply_markup=kb_dz_page(fam, page))
        else:
            await bot.send_photo(chat, BufferedInputFile(img, f"dz{page}.jpg"), caption=caption_, reply_markup=kb_dz_page(fam, page))
            if j.get("ed"):
                try:
                    await bot.edit_message_reply_markup(chat_id=chat, message_id=j["ed"], reply_markup=kb_home(j))
                except TelegramBadRequest:
                    pass
    except Exception:  # noqa: BLE001
        log.exception("designer gallery failed")
        await bot.send_message(chat, "⚠️ تعذّر تجهيز هذه الصفحة، حاول مرة أخرى.")


@router.callback_query(F.data.regexp(r"^dzt:[a-z]+:\d+$"))
async def cb_dzt(cb: CallbackQuery, bot: Bot):
    j = need_job(cb)
    if not j:
        return await cb.answer(EXPIRED, show_alert=True)
    if j.get("running"):
        return await cb.answer("طلبك قيد المعالجة", show_alert=True)
    _, fam, idx = cb.data.split(":")
    items = designer.DESIGNER.get(fam)
    i = int(idx)
    if not items or not 0 <= i < len(items):
        return await cb.answer()
    name, stack = items[i]
    await cb.answer(f"⏳ تطبيق: {name}")
    old = list(j["stack"])
    j["redo"] = []
    j["stack"] = [(e, dict(p)) for e, p in stack]
    chat = cb.message.chat.id
    j["busy"] = True
    try:
        ok = await refresh(bot, chat, j, None, f"✅ طُبّق: {designer.FAMILY_NAMES[fam]} #{i + 1} — {name}")
    finally:
        j["busy"] = False
    if not ok:
        j["stack"] = old
        return
    try:
        await cb.message.delete()
    except TelegramBadRequest:
        pass


# ---------------- خطوات التأثيرات (نص / صوت / اختيار) ----------------
def parse_range(text: str):
    xs = re.findall(r"\d+(?::\d+)*(?:[.,]\d+)?", text.translate(str.maketrans("٠١٢٣٤٥٦٧٨٩", "0123456789")))
    if len(xs) < 2:
        return None

    def t(s):
        v = 0.0
        for x in s.replace(",", ".").split(":"):
            v = v * 60 + float(x)
        return v
    a, b = t(xs[0]), t(xs[1])
    return (a, b) if b > a else None


async def ask_step(msg: Message, j: dict):
    pend = j["pend"]
    e = E.EFFECTS[pend["eid"]]
    step = e.steps[pend["i"]]
    if step[0] == "choice":
        rows = [[(lab, f"s:{k}")] for k, (lab, _) in enumerate(step[2])]
        rows.append([("↩️ إلغاء", "stepcancel")])
        await msg.answer(step[1], reply_markup=_kb(rows))
    else:
        await msg.answer(step[1], reply_markup=_kb([[("↩️ إلغاء", "stepcancel")]]))


@router.callback_query(F.data == "stepcancel")
async def cb_stepcancel(cb: CallbackQuery):
    j = JOBS.get(cb.from_user.id)
    if j:
        j["pend"] = None
    await cb.answer("أُلغي")
    try:
        await cb.message.delete()
    except TelegramBadRequest:
        pass


@router.callback_query(F.data.startswith("s:"))
async def cb_choice(cb: CallbackQuery, bot: Bot):
    j = need_job(cb)
    if not j or not j.get("pend"):
        return await cb.answer(EXPIRED, show_alert=True)
    await cb.answer()
    pend = j["pend"]
    step = E.EFFECTS[pend["eid"]].steps[pend["i"]]
    pend["params"][step[3]] = step[2][int(cb.data[2:])][1]
    await advance(bot, cb.message, j)


async def advance(bot: Bot, msg: Message, j: dict):
    pend = j["pend"]
    pend["i"] += 1
    e = E.EFFECTS[pend["eid"]]
    if pend["i"] < len(e.steps):
        return await ask_step(msg, j)
    j["pend"] = None
    j["redo"] = []
    j["stack"].append((pend["eid"], pend["params"]))
    wait = await bot.send_message(pend["chat"], f"⏳ تطبيق: {e.title}")
    j["busy"] = True
    try:
        ok = await refresh(bot, pend["chat"], j, None, f"✅ أُضيف: {e.title}")
    finally:
        j["busy"] = False
    if not ok:
        j["stack"].pop()
    try:
        await wait.delete()
    except TelegramBadRequest:
        pass


def _has_pend(m: Message) -> bool:
    j = JOBS.get(m.from_user.id) if m.from_user else None
    return bool(j and j.get("pend"))


@router.message(_has_pend)
async def on_step_input(msg: Message, bot: Bot):
    j = JOBS[msg.from_user.id]
    pend = j["pend"]
    step = E.EFFECTS[pend["eid"]].steps[pend["i"]]
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


# ---------------- المعالجة والتصدير ----------------
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
        return await cb.answer(EXPIRED, show_alert=True)
    if j.get("running"):
        return await cb.answer("طلبك قيد المعالجة")
    if not j["stack"]:
        return await cb.answer("اختر تأثيراً أو قالباً واحداً على الأقل", show_alert=True)
    j["running"] = True
    await cb.answer()
    chat = cb.message.chat.id
    t0 = time.time()
    state = {"p": 0.0, "queued": sem.locked(), "done": False}
    wait = await bot.send_message(chat, wait_text(0, queued=state["queued"]))
    kinds = {"out_gif": "gif", "out_mp3": "mp3", "out_cover": "cover"}
    out_kind = next((kinds[e] for e, _ in j["stack"] if e in kinds), "video")
    out = j["dir"] / {"gif": "out.gif", "mp3": "out.mp3", "cover": "out.jpg"}.get(out_kind, "out.mp4")
    names = [e for e, _ in j["stack"]]

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

    ok = False
    try:
        async with sem:
            state["queued"] = False
            res = await compose.render(j["src"], str(out), j["stack"], j["meta"], j["dir"], "final", prog)
        state["done"] = True
        elapsed = time.time() - t0
        if res["size"] > 49 * 1024 * 1024:
            raise compose.UserError("الناتج أكبر من 50MB (حد تيليجرام). جرّب تقصير الفيديو أو دقة أقل.")
        cap = f"تم اكمال طلبك بنجاح ✅\n\nالمده اللي انتهت : {fmt(elapsed)}\n\nشكرا على استخدامك ♥️"
        await safe_edit(wait, wait_text(elapsed, 1.0))
        if out_kind == "gif":
            await bot.send_animation(chat, FSInputFile(out), caption=cap)
        elif out_kind == "mp3":
            await bot.send_audio(chat, FSInputFile(out), caption=cap)
        elif out_kind == "cover":
            await bot.send_photo(chat, FSInputFile(out), caption=cap)
        else:
            await bot.send_video(chat, FSInputFile(out), caption=cap, width=res["w"], height=res["h"], duration=int(res["dur"]), supports_streaming=True)
        try:
            await wait.delete()
        except TelegramBadRequest:
            pass
        ok = True
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
        out.unlink(missing_ok=True)
        try:
            await db.log_job(cb.from_user.id, ok, time.time() - t0, names)
        except Exception:  # noqa: BLE001
            pass


# ---------------- الأدمن ----------------
@router.message(Command("stats"))
async def stats(msg: Message):
    if msg.from_user.id not in ADMIN_IDS:
        return
    s = await db.stats()
    top = "\n".join(f"  • {E.EFFECTS[k].title if k in E.EFFECTS else k}: {c}" for k, c in s["top"]) or "  —"
    await msg.answer(f"📊 <b>الإحصائيات</b>\n👥 المستخدمون: {s['users']} (جدد اليوم {s['new_today']} • نشطون اليوم {s['active_today']})\n"
                     f"🎬 المعالجات الناجحة: {s['jobs']} (اليوم {s['jobs_today']})\n❌ الفاشلة: {s['failed']}\n⏱ متوسط الزمن: {fmt(s['avg'])}\n\n<b>الأكثر استخداماً:</b>\n{top}")


@router.message(Command("bcast"))
async def bcast(msg: Message, bot: Bot):
    if msg.from_user.id not in ADMIN_IDS:
        return
    text = msg.text.partition(" ")[2].strip()
    if not text and not msg.reply_to_message:
        return await msg.answer("الاستخدام: /bcast النص  أو بالرد على رسالة")
    ok = bad = 0
    for uid in await db.users():
        try:
            if msg.reply_to_message:
                await msg.reply_to_message.copy_to(uid)
            else:
                await bot.send_message(uid, text)
            ok += 1
        except Exception:  # noqa: BLE001
            bad += 1
        await asyncio.sleep(0.05)
    await msg.answer(f"📣 تم الإرسال: {ok} • فشل: {bad}")


@router.message()
async def other(msg: Message):
    if JOBS.get(msg.from_user.id):
        return await msg.answer("اضغط /menu لإظهار الاستوديو، أو أرسل فيديو جديداً 🪄")
    await msg.answer("🪄 ارسل الفيديو من فضلك")


async def cleaner():
    while True:
        await asyncio.sleep(300)
        now = time.time()
        for uid, j in list(JOBS.items()):
            if not j.get("running") and now - j["ts"] > TTL:
                drop_job(uid)


BOT: Bot | None = None


async def text_submit(uid: int, params: dict):
    """يُستدعى من التطبيق المصغّر عند حفظ النص: يضيف التأثير ويحدّث المحرر مباشرة"""
    j = JOBS.get(uid)
    if not j or j.get("running") or BOT is None:
        return
    j["redo"] = []
    j["stack"].append(("text_pro", params))
    j["busy"] = True
    try:
        ok = await refresh(BOT, j["chat"], j, None, "✅ أُضيف: النص")
    finally:
        j["busy"] = False
    if not ok:
        j["stack"].pop()


async def health():
    port = int(os.getenv("PORT", "0") or 0) or (8080 if WEBAPP_URL else 0)
    if not port:
        return
    app = web.Application()
    app.router.add_get("/", lambda r: web.Response(text="ok"))
    app.router.add_get("/health", lambda r: web.Response(text="ok"))
    webapp.BOT_TOKEN = TOKEN
    webapp.get_job = lambda uid: JOBS.get(uid)
    webapp.on_text = text_submit
    webapp.add_routes(app)
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
    global BOT
    BOT = bot
    me = await bot.get_me()
    log.info("running @%s", me.username)
    try:
        from aiogram.types import BotCommand
        await bot.set_my_commands([BotCommand(command="start", description="البداية"), BotCommand(command="menu", description="🎛 استوديو المونتاج"), BotCommand(command="help", description="📖 طريقة الاستخدام"), BotCommand(command="id", description="🆔 آيديك")])
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

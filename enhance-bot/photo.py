"""استوديو الصور: جلسة تحرير مباشرة (المعاينة تتغير في نفس الرسالة) بأدوات photolib."""
import asyncio
import logging
import shutil
import time
import uuid
from pathlib import Path

from aiogram import Bot, F, Router
from aiogram.exceptions import TelegramBadRequest, TelegramRetryAfter
from aiogram.types import BufferedInputFile, CallbackQuery, FSInputFile, InlineKeyboardButton, InlineKeyboardMarkup, InputMediaPhoto, Message

import db
import photolib as P

log = logging.getLogger("photo")
router = Router()
PJOBS: dict[int, dict] = {}
WORK: Path = Path("work")  # يضبطه main
HOOK: dict = {"ask": None}  # main يضع هنا دالة سؤال (إلغاء/تكملة) عند وجود مونتاج جارٍ
PAGE = 12
TTL = 1800
MAX_DL = 20 * 1024 * 1024
sem = asyncio.Semaphore(2)
EXPIRED = "انتهت الجلسة، أرسل صورة جديدة."


def kb(rows) -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup(inline_keyboard=[[x if isinstance(x, InlineKeyboardButton) else InlineKeyboardButton(text=x[0], callback_data=x[1]) for x in r] for r in rows])


def fmt(sec: float) -> str:
    s = int(sec)
    return f"{s // 60}:{s % 60:02d}"


def drop(uid: int):
    j = PJOBS.pop(uid, None)
    if j:
        shutil.rmtree(j["dir"], ignore_errors=True)


def in_session(uid: int) -> bool:
    j = PJOBS.get(uid)
    return bool(j) and bool(j["stack"] or j.get("pend") or j.get("busy"))


# ---------------------------------------------------------------- الأزرار
def kb_home(j) -> InlineKeyboardMarkup:
    rows = [
        [("⟦gem⟧ تحسين تلقائي ذكي", "pe:auto"), ("⟦photo⟧ دقة الآيفون", "pe:iphone")],
        [("⟦layers⟧ إزالة الخلفية", "pe:bg_remove"), ("⟦layers⟧ تضبيب الخلفية", "pe:bg_blur")],
        [("⟦layers⟧ وضع البورتريه", "pe:bg_bokeh"), ("⟦brush⟧ تنعيم البشرة", "pe:skin")],
    ]
    cats = [(f"⟦{ic}⟧ {title}", f"pc:{k}:0") for k, (ic, _fb, title) in P.CATS.items()]
    rows += [cats[i:i + 2] for i in range(0, len(cats), 2)]
    n = len(j["stack"])
    rows.append([(f"⟦stack⟧ المختارة ({n})", "pk"), ("⟦undo⟧ تراجع", "pu"), ("⟦redo⟧ إعادة", "pr")])
    rows.append([("⟦clear⟧ مسح الكل", "pz"), ("⟦download⟧ معالجة وتصدير", "pg")])
    return kb(rows)


def kb_cat(cat: str, page: int) -> InlineKeyboardMarkup:
    ts = P.by_cat(cat)
    chunk = ts[page * PAGE:(page + 1) * PAGE]
    items = [(t.title, f"pe:{t.id}") for t in chunk]
    rows = [items[i:i + 2] for i in range(0, len(items), 2)]
    nav = []
    if page > 0:
        nav.append(("⟦left⟧ السابق", f"pc:{cat}:{page - 1}"))
    nav.append((f"{page + 1}/{max(1, -(-len(ts) // PAGE))}", "noop"))
    if (page + 1) * PAGE < len(ts):
        nav.append(("التالي ⟦right⟧", f"pc:{cat}:{page + 1}"))
    rows.append(nav)
    rows.append([("⟦undo⟧ تراجع", "pu"), ("⟦home⟧ القائمة", "ph")])
    rows.append([("⟦download⟧ معالجة وتصدير", "pg")])
    return kb(rows)


def kb_stack(j) -> InlineKeyboardMarkup:
    rows = [[(f"⟦trash⟧ {i}. {P.TOOLS[t].title}", f"pd:{i - 1}")] for i, (t, _) in enumerate(j["stack"], 1)]
    rows.append([("⟦redo⟧ استرجاع المحذوف" + (f" ({len(j['redo'])})" if j["redo"] else ""), "pr")])
    rows.append([("⟦home⟧ القائمة", "ph")])
    return kb(rows)


def caption(j, note="") -> str:
    st = j["stack"]
    h, w = j["cur"][0].shape[:2]
    lines = "\n".join(f"{i}. {P.TOOLS[t].title}" for i, (t, _) in enumerate(st[-8:], max(1, len(st) - 7)))
    head = f"⟦photos⟧ <b>استوديو الصور</b> — المعاينة مباشرة 👇\n📐 {w}×{h}" + (" • شفافة (PNG)" if j["cur"][1] is not None else "")
    body = f"\n\n<b>المختارة ({len(st)}):</b>\n{lines}" if st else "\n\nاختر أداة من الأزرار تحت الصورة 🪄"
    return (head + body + (f"\n\n{note}" if note else ""))[:1000]


# ---------------------------------------------------------------- الاستقبال
async def _download(bot: Bot, fid: str, dst: Path):
    for attempt in range(3):
        try:
            await asyncio.wait_for(bot.download(fid, destination=dst), 60)
            return
        except Exception:  # noqa: BLE001
            if attempt == 2:
                raise
            await asyncio.sleep(1)


async def open_session(bot: Bot, msg: Message, fid: str, size: int | None, as_file: bool):
    uid = msg.from_user.id
    if size and size > MAX_DL:
        return await msg.answer("⚠️ حجم الصورة أكبر من 20MB (حد تيليجرام للبوتات). أرسل صورة أصغر.")
    drop(uid)
    d = WORK / f"p{uid}_{uuid.uuid4().hex[:6]}"
    d.mkdir(parents=True, exist_ok=True)
    note = await msg.answer("⏳ جاري تجهيز أدوات الصور ...")
    src = d / "in.img"
    try:
        await _download(bot, fid, src)
        base = await asyncio.to_thread(P.load, str(src), 1280)
    except Exception:  # noqa: BLE001
        log.exception("photo load failed")
        shutil.rmtree(d, ignore_errors=True)
        try:
            await note.edit_text("⚠️ تعذّر قراءة هذه الصورة، جرّب صورة أخرى.")
        except TelegramBadRequest:
            pass
        return
    j = {"uid": uid, "dir": d, "src": str(src), "chat": msg.chat.id, "base": base, "stack": [], "redo": [], "cur": (base, None),
         "msg_id": None, "busy": False, "pend": None, "ts": time.time(), "pending_video": None, "as_file": as_file}
    PJOBS[uid] = j
    try:
        await note.delete()
    except TelegramBadRequest:
        pass
    h, w = base.shape[:2]
    tip = "" if as_file else "\n\n💡 لأعلى جودة أرسل الصورة كـ «ملف» 📎 بدل صورة."
    jpg = await asyncio.to_thread(P.to_jpeg, j["cur"])
    m = await bot.send_photo(msg.chat.id, BufferedInputFile(jpg, "preview.jpg"), caption=caption(j, tip.strip()), reply_markup=kb_home(j))
    j["msg_id"] = m.message_id


@router.message(F.photo)
async def on_photo(msg: Message, bot: Bot):
    await db.touch_user(msg.from_user.id, msg.from_user.full_name, msg.from_user.username)
    if HOOK["ask"] and await HOOK["ask"](msg, msg):
        return
    ph = msg.photo[-1]
    await open_session(bot, msg, ph.file_id, ph.file_size, False)


@router.message(F.document & F.document.mime_type.startswith("image/"))
async def on_photo_doc(msg: Message, bot: Bot):
    await db.touch_user(msg.from_user.id, msg.from_user.full_name, msg.from_user.username)
    if HOOK["ask"] and await HOOK["ask"](msg, msg):
        return
    d = msg.document
    await open_session(bot, msg, d.file_id, d.file_size, True)


async def resend(bot: Bot, j: dict):
    """إعادة إظهار الاستوديو كرسالة جديدة (بعد /menu أو «التكملة»)"""
    jpg = await asyncio.to_thread(P.to_jpeg, j["cur"])
    m = await bot.send_photo(j["chat"], BufferedInputFile(jpg, "preview.jpg"), caption=caption(j), reply_markup=kb_home(j))
    j["msg_id"] = m.message_id


# ---------------------------------------------------------------- التحرير المباشر
def need(cb: CallbackQuery):
    return PJOBS.get(cb.from_user.id)


async def refresh(bot: Bot, j: dict, markup, note="", recompute=False):
    """يعيد حساب الصورة (إن لزم) ويبدّلها في نفس الرسالة"""
    if recompute:
        j["cur"] = await asyncio.to_thread(P.apply_stack, j["base"], j["stack"])
    jpg = await asyncio.to_thread(P.to_jpeg, j["cur"])
    media = InputMediaPhoto(media=BufferedInputFile(jpg, "preview.jpg"), caption=caption(j, note))
    for _ in range(2):
        try:
            await bot.edit_message_media(media=media, chat_id=j["chat"], message_id=j["msg_id"], reply_markup=markup)
            return True
        except TelegramRetryAfter as e:
            await asyncio.sleep(min(e.retry_after, 3))
        except TelegramBadRequest as e:
            if "not modified" in str(e):
                return True
            break
    m = await bot.send_photo(j["chat"], BufferedInputFile(jpg, "preview.jpg"), caption=caption(j, note), reply_markup=markup)
    j["msg_id"] = m.message_id
    return True


async def add_tool(bot: Bot, j: dict, tid: str, params=None, note=None):
    t = P.TOOLS[tid]
    j["busy"] = True
    try:
        async with sem:
            new = await asyncio.to_thread(P.apply_one, j["cur"], tid, params)
        j["cur"] = new
        j["stack"].append((tid, params or {}))
        j["redo"].clear()
        j["ts"] = time.time()
        await refresh(bot, j, kb_home(j), note or f"✅ أُضيف: {t.title}")
    except Exception:  # noqa: BLE001
        log.exception("photo tool failed: %s", tid)
        await bot.send_message(j["chat"], "⚠️ تعذّر تطبيق هذه الأداة على الصورة. جرّب غيرها.")
    finally:
        j["busy"] = False


@router.callback_query(F.data.startswith("pc:"))
async def cb_cat(cb: CallbackQuery):
    j = need(cb)
    if not j:
        return await cb.answer(EXPIRED, show_alert=True)
    _, cat, page = cb.data.split(":")
    await cb.answer()
    try:
        await cb.message.edit_reply_markup(reply_markup=kb_cat(cat, int(page)))
    except TelegramBadRequest:
        pass


@router.callback_query(F.data == "ph")
async def cb_home(cb: CallbackQuery):
    j = need(cb)
    if not j:
        return await cb.answer(EXPIRED, show_alert=True)
    j["pend"] = None
    await cb.answer()
    try:
        await cb.message.edit_reply_markup(reply_markup=kb_home(j))
    except TelegramBadRequest:
        pass


@router.callback_query(F.data.startswith("pe:"))
async def cb_effect(cb: CallbackQuery, bot: Bot):
    j = need(cb)
    if not j:
        return await cb.answer(EXPIRED, show_alert=True)
    if j["busy"]:
        return await cb.answer("لحظة، يتم تطبيق الأداة السابقة")
    tid = cb.data[3:]
    t = P.TOOLS.get(tid)
    if not t:
        return await cb.answer()
    if t.steps:
        j["pend"] = {"id": tid, "i": 0, "p": {}}
        await cb.answer()
        return await ask_step(bot, j)
    await cb.answer(f"⏳ تطبيق: {t.title}")
    await add_tool(bot, j, tid)


async def ask_step(bot: Bot, j: dict):
    pend = j["pend"]
    step = P.TOOLS[pend["id"]].steps[pend["i"]]
    cancel = [("↩️ إلغاء", "pcancel")]
    if step[0] == "choice":
        rows = [[(lab, f"ps:{k}") for k, (lab, _v) in enumerate(step[2])], cancel]
        await bot.send_message(j["chat"], step[1], reply_markup=kb(rows))
    else:
        await bot.send_message(j["chat"], step[1], reply_markup=kb([cancel]))


async def finish_step(bot: Bot, j: dict):
    pend = j["pend"]
    pend["i"] += 1
    if pend["i"] < len(P.TOOLS[pend["id"]].steps):
        return await ask_step(bot, j)
    j["pend"] = None
    await add_tool(bot, j, pend["id"], pend["p"])


@router.callback_query(F.data.startswith("ps:"))
async def cb_step_choice(cb: CallbackQuery, bot: Bot):
    j = need(cb)
    if not j or not j.get("pend"):
        return await cb.answer(EXPIRED, show_alert=True)
    step = P.TOOLS[j["pend"]["id"]].steps[j["pend"]["i"]]
    if step[0] != "choice":
        return await cb.answer()
    j["pend"]["p"][step[3]] = step[2][int(cb.data[3:])][1]
    await cb.answer()
    try:
        await cb.message.delete()
    except TelegramBadRequest:
        pass
    await finish_step(bot, j)


@router.callback_query(F.data == "pcancel")
async def cb_step_cancel(cb: CallbackQuery):
    j = need(cb)
    if j:
        j["pend"] = None
    await cb.answer("أُلغيت")
    try:
        await cb.message.delete()
    except TelegramBadRequest:
        pass


def _has_pend(m: Message) -> bool:
    j = PJOBS.get(m.from_user.id) if m.from_user else None
    return bool(j and j.get("pend") and P.TOOLS[j["pend"]["id"]].steps[j["pend"]["i"]][0] == "text")


@router.message(F.text & ~F.text.startswith("/"), _has_pend)
async def on_step_text(msg: Message, bot: Bot):
    j = PJOBS[msg.from_user.id]
    step = P.TOOLS[j["pend"]["id"]].steps[j["pend"]["i"]]
    j["pend"]["p"]["text"] = msg.text.strip()[:200]
    del step
    await finish_step(bot, j)


@router.callback_query(F.data.in_({"pu", "pr"}))
async def cb_undo_redo(cb: CallbackQuery, bot: Bot):
    j = need(cb)
    if not j:
        return await cb.answer(EXPIRED, show_alert=True)
    if j["busy"]:
        return await cb.answer("لحظة...")
    if cb.data == "pu":
        if not j["stack"]:
            return await cb.answer("لا يوجد ما يُتراجع عنه")
        j["redo"].append(j["stack"].pop())
        note = f"↩️ أُلغي: {P.TOOLS[j['redo'][-1][0]].title}"
    else:
        if not j["redo"]:
            return await cb.answer("لا يوجد ما يُعاد")
        j["stack"].append(j["redo"].pop())
        note = f"↪️ أُعيد: {P.TOOLS[j['stack'][-1][0]].title}"
    await cb.answer(note[:60])
    j["busy"] = True
    try:
        await refresh(bot, j, kb_home(j), note, recompute=True)
    finally:
        j["busy"] = False


@router.callback_query(F.data == "pk")
async def cb_stack(cb: CallbackQuery):
    j = need(cb)
    if not j:
        return await cb.answer(EXPIRED, show_alert=True)
    if not j["stack"] and not j["redo"]:
        return await cb.answer("لم تختر أدوات بعد", show_alert=True)
    await cb.answer()
    try:
        await cb.message.edit_reply_markup(reply_markup=kb_stack(j))
    except TelegramBadRequest:
        pass


@router.callback_query(F.data.startswith("pd:"))
async def cb_del(cb: CallbackQuery, bot: Bot):
    j = need(cb)
    if not j:
        return await cb.answer(EXPIRED, show_alert=True)
    if j["busy"]:
        return await cb.answer("لحظة...")
    i = int(cb.data[3:])
    if i >= len(j["stack"]):
        return await cb.answer()
    item = j["stack"].pop(i)
    j["redo"].append(item)
    await cb.answer(f"🗑 حُذف: {P.TOOLS[item[0]].title}"[:60])
    j["busy"] = True
    try:
        await refresh(bot, j, kb_stack(j) if j["stack"] else kb_home(j), f"🗑 حُذف: {P.TOOLS[item[0]].title}", recompute=True)
    finally:
        j["busy"] = False


@router.callback_query(F.data == "pz")
async def cb_clear(cb: CallbackQuery, bot: Bot):
    j = need(cb)
    if not j:
        return await cb.answer(EXPIRED, show_alert=True)
    if j["busy"] or not j["stack"]:
        return await cb.answer()
    j["redo"] = []
    j["stack"] = []
    await cb.answer("🗑 تم المسح")
    j["busy"] = True
    try:
        await refresh(bot, j, kb_home(j), "🗑 تم مسح كل الأدوات", recompute=True)
    finally:
        j["busy"] = False


@router.callback_query(F.data == "pg")
async def cb_export(cb: CallbackQuery, bot: Bot):
    j = need(cb)
    if not j:
        return await cb.answer(EXPIRED, show_alert=True)
    if j["busy"]:
        return await cb.answer("لحظة...")
    if not j["stack"]:
        return await cb.answer("اختر أداة واحدة على الأقل", show_alert=True)
    await cb.answer()
    j["busy"] = True
    t0 = time.time()
    chat = j["chat"]
    wait = await bot.send_message(chat, "✅ تم استلام طلبك بنجاح\n\n🪄 جاري المعالجة بأعلى جودة...")
    names = [t for t, _ in j["stack"]]
    ok = False
    try:
        async with sem:
            def work():
                full = P.load(j["src"], 4096)
                st = P.apply_stack(full, j["stack"])
                path = P.to_file(st, str(j["dir"] / "out"))
                return st, path
            st, path = await asyncio.to_thread(work)
        if Path(path).stat().st_size > 49 * 1024 * 1024:
            raise RuntimeError("too big")
        cap = f"تم اكمال طلبك بنجاح ✅\n\nالمده اللي انتهت : {fmt(time.time() - t0)}\n\nشكرا على استخدامك ♥️"
        if st[1] is None:
            await bot.send_photo(chat, BufferedInputFile(await asyncio.to_thread(P.to_jpeg, st, 92, 2560), "photo.jpg"), caption=cap)
            await bot.send_document(chat, FSInputFile(path), caption="📎 النسخة الأصلية بدون ضغط")
        else:
            await bot.send_document(chat, FSInputFile(path), caption=cap + "\n\nPNG شفاف 📎")
        ok = True
        try:
            await wait.delete()
        except TelegramBadRequest:
            pass
    except Exception:  # noqa: BLE001
        log.exception("photo export failed")
        try:
            await wait.edit_text("⚠️ تعذّرت معالجة هذه الصورة بهذه الأدوات. جرّب أدوات أقل.")
        except TelegramBadRequest:
            pass
    finally:
        j["busy"] = False
        try:
            await db.log_job(cb.from_user.id, ok, time.time() - t0, ["photo:" + n for n in names])
        except Exception:  # noqa: BLE001
            pass


async def cleaner():
    while True:
        await asyncio.sleep(300)
        now = time.time()
        for uid, j in list(PJOBS.items()):
            if not j.get("busy") and now - j["ts"] > TTL:
                drop(uid)

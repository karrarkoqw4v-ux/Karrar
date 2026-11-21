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
HOOK: dict = {"ask": None, "quota": None, "feature": None, "pro": None, "ai": None}  # main يضع هنا دالة سؤال (إلغاء/تكملة) عند وجود مونتاج جارٍ
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
    return bool(j) and bool(j["stack"] or j.get("pend") or j.get("vpend") or j.get("busy"))


# ---------------------------------------------------------------- الأزرار
AI_PHOTO_TOOLS = ["ai_smart_fix", "ai_face_enhance", "bg_remove", "bg_remove_soft", "bg_blur", "bg_bokeh", "bg_sticker", "ai_crop_subject", "auto", "iphone",
                  "hdr", "ai_upscale3", "upscale2", "skin", "pro_even_skin", "lowlight", "dehaze", "denoise"]


def kb_ai_home(j) -> InlineKeyboardMarkup:
    """واجهة «مونتاج الذكاء الاصطناعي» للصور"""
    tools = [(P.TOOLS[t].title if P.TOOLS[t].title.startswith("🤖") else "🤖 " + P.TOOLS[t].title, f"pe:{t}") for t in AI_PHOTO_TOOLS if t in P.TOOLS]
    rows = [[("🤖 باقات ذكية جاهزة", "pg:p:0"), ("🤖 كل أدوات الذكاء الاصطناعي", "pc:ai:0")]]
    rows += [tools[i:i + 2] for i in range(0, len(tools), 2)]
    rows.append([("🎥 قوالب كاب كات", "pg:c:0"), ("⟦film⟧ تحويل لفيديو بموسيقى", "pv")])
    rows.append([("🧑‍🎨 اطلب تعديلاً يدوياً من المصمم", "rq:new")])
    n = len(j["stack"])
    rows.append([(f"⟦stack⟧ المختارة ({n})", "pk"), ("⟦undo⟧ تراجع", "pu"), ("⟦redo⟧ إعادة", "pr")])
    rows.append([("⟦clear⟧ مسح الكل", "pz"), ("⟦download⟧ معالجة وتصدير", "pg")])
    return kb(rows)


def kb_pro_home(j) -> InlineKeyboardMarkup:
    """واجهة المصممين المحترفين للصور: باقات + أدوات التصميم + كل الأقسام"""
    rows = [[("⟦gem⟧ باقات المصممين المحترفين (16)", "pg:p:0")],
            [("⟦brush⟧ أدوات التصميم الاحترافية", "pc:pro:0"), ("⟦star⟧ قوالب جاهزة بنقرة", "pg:l:0")],
            [("⟦gem⟧ تحسين تلقائي ذكي", "pe:auto"), ("⟦photo⟧ دقة الآيفون", "pe:iphone")],
            [("⟦layers⟧ إزالة الخلفية", "pe:bg_remove"), ("⟦layers⟧ وضع البورتريه", "pe:bg_bokeh")],
            [("⟦film⟧ تحويل الصورة لفيديو بموسيقى", "pv"), ("🎥 قوالب كاب كات", "pg:c:0")]]
    cats = [(f"⟦{ic}⟧ {title}", f"pc:{k}:0") for k, (ic, _fb, title) in P.CATS.items() if k != "pro"]
    rows += [cats[i:i + 2] for i in range(0, len(cats), 2)]
    n = len(j["stack"])
    rows.append([(f"⟦stack⟧ المختارة ({n})", "pk"), ("⟦undo⟧ تراجع", "pu"), ("⟦redo⟧ إعادة", "pr")])
    rows.append([("⟦clear⟧ مسح الكل", "pz"), ("⟦download⟧ معالجة وتصدير", "pg")])
    return kb(rows)


def kb_home(j) -> InlineKeyboardMarkup:
    if j.get("ai"):
        return kb_ai_home(j)
    if j.get("pro"):
        return kb_pro_home(j)
    rows = [
        [("⟦gem⟧ تحسين تلقائي ذكي", "pe:auto"), ("⟦photo⟧ دقة الآيفون", "pe:iphone")],
        [("⟦layers⟧ إزالة الخلفية", "pe:bg_remove"), ("⟦layers⟧ تضبيب الخلفية", "pe:bg_blur")],
        [("⟦layers⟧ وضع البورتريه", "pe:bg_bokeh"), ("⟦brush⟧ تنعيم البشرة", "pe:skin")],
    ]
    rows.append([(f"⟦star⟧ قوالب جاهزة بنقرة ({len(P.PHOTO_TEMPLATES)})", "pg:l:0")])
    rows.append([("⟦film⟧ تحويل الصورة لفيديو بموسيقى", "pv"), ("🎥 قوالب كاب كات", "pg:c:0")])
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
    if _tool_items(cat, page):
        rows.append([("🖼 معاينة أدوات هذه الصفحة على صورتك", f"pp:{cat}:{page}")])
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
    head = ("🤖 <b>استوديو الذكاء الاصطناعي (صور)</b>" if j.get("ai") else "⟦gem⟧ <b>استوديو المصممين المحترفين (صور)</b>" if j.get("pro") else "⟦photos⟧ <b>استوديو الصور</b>") + f" — المعاينة مباشرة 👇\n📐 {w}×{h}" + (" • شفافة (PNG)" if j["cur"][1] is not None else "")
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
         "msg_id": None, "busy": False, "pend": None, "ts": time.time(), "pending_video": None, "as_file": as_file,
         "pro": bool(HOOK["pro"] and HOOK["pro"](uid)), "ai": bool(HOOK["ai"] and HOOK["ai"](uid))}
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
    if HOOK["feature"] and (fm := HOOK["feature"]("photo", msg.from_user.id)):
        return await msg.answer(fm)
    if HOOK["ask"] and await HOOK["ask"](msg, msg):
        return
    ph = msg.photo[-1]
    await open_session(bot, msg, ph.file_id, ph.file_size, False)


@router.message(F.document & F.document.mime_type.startswith("image/"))
async def on_photo_doc(msg: Message, bot: Bot):
    await db.touch_user(msg.from_user.id, msg.from_user.full_name, msg.from_user.username)
    if HOOK["feature"] and (fm := HOOK["feature"]("photo", msg.from_user.id)):
        return await msg.answer(fm)
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


# ---------------------------------------------------------------- معارض المعاينة (قوالب وأدوات بصور حقيقية)
LISTS = {"l": ("🎁 قوالب جاهزة بنقرة", None), "p": ("🏆 باقات المصممين المحترفين", None), "c": ("🎥 قوالب بروح كاب كات", None)}
PER = 10


def _list(k):
    return {"l": P.PHOTO_TEMPLATES, "p": P.PHOTO_PRO_PACKS, "c": P.PHOTO_CAPCUT}[k]


def _thumb_base(j):
    if "tb" not in j:
        import cv2
        h, w = j["base"].shape[:2]
        s = 360.0 / max(h, w)
        j["tb"] = cv2.resize(j["base"], (max(1, int(w * s)), max(1, int(h * s))), interpolation=cv2.INTER_AREA) if s < 1 else j["base"]
    return j["tb"]


def _render_thumbs(j, jobs):
    """jobs: قائمة [(tid, params)] لكل خانة -> bytes JPEG صغيرة"""
    tb = _thumb_base(j)
    out = []
    for steps in jobs:
        try:
            st = (tb, None)
            for tid, p in steps:
                st = P.apply_one(st, tid, p or None)
            out.append(P.to_jpeg(st, 80, 360))
        except Exception:  # noqa: BLE001
            log.exception("thumb failed")
            out.append(P.to_jpeg((tb, None), 80, 360))
    return out


async def _gallery(j, jobs, titles, cols=5):
    import art
    async with sem:
        imgs = await asyncio.to_thread(_render_thumbs, j, jobs)
    return await asyncio.to_thread(art.template_grid, imgs, titles, cols)


def _short(t: str) -> str:
    import emo
    return emo.strip(t).split(" (")[0][:24]


def _kb_numbers(prefix_apply: str, ids: list, nav_prefix: str | None, k: str, page: int, total_pages: int):
    rows = [[(str(i + 1), f"{prefix_apply}{ids[i]}") for i in range(r, min(r + 5, len(ids)))] for r in range(0, len(ids), 5)]
    if nav_prefix and total_pages > 1:
        nav = []
        if page > 0:
            nav.append(("⟦left⟧ السابق", f"{nav_prefix}:{k}:{page - 1}"))
        nav.append((f"{page + 1}/{total_pages}", "noop"))
        if page + 1 < total_pages:
            nav.append(("التالي ⟦right⟧", f"{nav_prefix}:{k}:{page + 1}"))
        rows.append(nav)
    rows.append([("⟦home⟧ رجوع للمحرر", "pcl")])
    return kb(rows)


async def _tpl_page(j, k, page):
    lst = _list(k)
    lo, hi = page * PER, min(len(lst), (page + 1) * PER)
    items = lst[lo:hi]
    jobs = [[P.norm(x) for x in ids] for _, ids in items]
    img = await _gallery(j, jobs, [_short(t) for t, _ in items])
    names = "\n".join(f"{i}. {t}" for i, (t, _) in enumerate(items, lo + 1))
    cap = f"<b>{LISTS[k][0]}</b> — صفحة {page + 1}\nاختر رقم القالب 👇\n\n{names}"[:1000]
    return img, cap, _kb_numbers(f"pa:{k}:", list(range(lo, hi)), "pgn", k, page, -(-len(lst) // PER))


@router.callback_query(F.data.startswith("pg:"))
async def cb_gallery_open(cb: CallbackQuery, bot: Bot):
    j = need(cb)
    if not j:
        return await cb.answer(EXPIRED, show_alert=True)
    _, k, page = cb.data.split(":")
    await cb.answer("🖼 جارٍ تجهيز المعاينات على صورتك...")
    wait = await bot.send_message(j["chat"], "🪄 أطبّق القوالب على صورتك، لحظات...")
    try:
        img, cap, mk = await _tpl_page(j, k, int(page))
        await bot.send_photo(j["chat"], BufferedInputFile(img, "g.jpg"), caption=cap, reply_markup=mk)
    except Exception:  # noqa: BLE001
        log.exception("photo gallery failed")
        await bot.send_message(j["chat"], "⚠️ تعذّر تجهيز المعاينات، حاول مرة أخرى.")
    finally:
        try:
            await wait.delete()
        except TelegramBadRequest:
            pass


@router.callback_query(F.data.startswith("pgn:"))
async def cb_gallery_nav(cb: CallbackQuery, bot: Bot):
    j = need(cb)
    if not j:
        return await cb.answer(EXPIRED, show_alert=True)
    _, k, page = cb.data.split(":")
    await cb.answer("⏳")
    try:
        img, cap, mk = await _tpl_page(j, k, int(page))
        await cb.message.edit_media(InputMediaPhoto(media=BufferedInputFile(img, "g.jpg"), caption=cap), reply_markup=mk)
    except TelegramBadRequest:
        pass
    except Exception:  # noqa: BLE001
        log.exception("photo gallery nav failed")


@router.callback_query(F.data == "pcl")
async def cb_gallery_close(cb: CallbackQuery):
    await cb.answer()
    try:
        await cb.message.delete()
    except TelegramBadRequest:
        pass


@router.callback_query(F.data.startswith("pa:"))
async def cb_tpl_apply(cb: CallbackQuery, bot: Bot):
    j = need(cb)
    if not j:
        return await cb.answer(EXPIRED, show_alert=True)
    if j["busy"]:
        return await cb.answer("لحظة، يتم تطبيق الأداة السابقة")
    _, k, i = cb.data.split(":")
    name, ids = _list(k)[int(i)]
    steps = [P.norm(x) for x in ids]
    await cb.answer(f"⏳ تطبيق: {name}"[:60])
    j["busy"] = True
    try:
        async with sem:
            def work():
                st = j["cur"]
                for tid, prm in steps:
                    st = P.apply_one(st, tid, prm or None)
                return st
            j["cur"] = await asyncio.to_thread(work)
        j["stack"].extend((tid, prm) for tid, prm in steps)
        j["redo"].clear()
        j["ts"] = time.time()
        await refresh(bot, j, kb_home(j), f"✅ طُبّق القالب: {name}")
        try:
            await cb.message.delete()
        except TelegramBadRequest:
            pass
    except Exception:  # noqa: BLE001
        log.exception("photo template failed")
        await bot.send_message(j["chat"], "⚠️ تعذّر تطبيق هذا القالب على الصورة.")
    finally:
        j["busy"] = False


def _tool_items(cat, page):
    return [t for t in P.by_cat(cat)[page * PAGE:(page + 1) * PAGE] if not t.steps]


@router.callback_query(F.data.startswith("pp:"))
async def cb_tools_preview(cb: CallbackQuery, bot: Bot):
    j = need(cb)
    if not j:
        return await cb.answer(EXPIRED, show_alert=True)
    _, cat, page = cb.data.split(":")
    items = _tool_items(cat, int(page))
    if not items:
        return await cb.answer("لا معاينة لهذا القسم", show_alert=True)
    await cb.answer("🖼 جارٍ تجهيز المعاينات على صورتك...")
    wait = await bot.send_message(j["chat"], f"🪄 أطبّق {len(items)} أداة على صورتك، لحظات...")
    try:
        img = await _gallery(j, [[(t.id, None)] for t in items], [_short(t.title) for t in items])
        names = "\n".join(f"{i}. {t.title}" for i, t in enumerate(items, 1))
        await bot.send_photo(j["chat"], BufferedInputFile(img, "g.jpg"), caption=f"<b>معاينة الأدوات</b> — {P.CATS[cat][2]}\nاختر رقم الأداة 👇\n\n{names}"[:1000],
                             reply_markup=_kb_numbers("pce:", [t.id for t in items], None, cat, 0, 1))
    except Exception:  # noqa: BLE001
        log.exception("tools gallery failed")
        await bot.send_message(j["chat"], "⚠️ تعذّر تجهيز المعاينات، حاول مرة أخرى.")
    finally:
        try:
            await wait.delete()
        except TelegramBadRequest:
            pass


@router.callback_query(F.data.startswith("pce:"))
async def cb_tool_from_gallery(cb: CallbackQuery, bot: Bot):
    j = need(cb)
    if not j:
        return await cb.answer(EXPIRED, show_alert=True)
    if j["busy"]:
        return await cb.answer("لحظة، يتم تطبيق الأداة السابقة")
    tid = cb.data[4:]
    if tid not in P.TOOLS:
        return await cb.answer()
    await cb.answer(f"⏳ تطبيق: {P.TOOLS[tid].title}"[:60])
    await add_tool(bot, j, tid)
    try:
        await cb.message.delete()
    except TelegramBadRequest:
        pass


@router.callback_query(F.data == "ph")
async def cb_home(cb: CallbackQuery):
    j = need(cb)
    if not j:
        return await cb.answer(EXPIRED, show_alert=True)
    j["pend"] = None
    j["vpend"] = None
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
    if HOOK["quota"] and (qm := await HOOK["quota"](cb.from_user.id)):
        return await cb.answer(qm, show_alert=True)
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



# ---------------------------------------------------------------- صورة ← فيديو بموسيقى (حركة كين بيرنز)
VSTYLES = [("🔍 زوم للداخل", "zin"), ("🔎 زوم للخارج", "zout"), ("➡️ بان يمين", "pr"), ("⬅️ بان يسار", "pl"), ("💓 زوم نبضي (على الإيقاع)", "pulse")]
VDURS = [("8 ثوانٍ", 8), ("15 ثانية", 15), ("30 ثانية", 30)]


def _vq(step: int):
    if step == 0:
        return "🎬 اختر حركة الفيديو:", [(lab, f"pvs:style:{v}") for lab, v in VSTYLES]
    return "⏱ اختر مدة الفيديو:", [(lab, f"pvs:dur:{v}") for lab, v in VDURS]


@router.callback_query(F.data == "pv")
async def cb_video_start(cb: CallbackQuery, bot: Bot):
    j = need(cb)
    if not j:
        return await cb.answer(EXPIRED, show_alert=True)
    if j["busy"]:
        return await cb.answer("لحظة...")
    j["vpend"] = {"stage": "style", "p": {}}
    await cb.answer()
    t, items = _vq(0)
    rows = [[b] for b in items] + [[("↩️ إلغاء", "pcancel")]]
    await bot.send_message(j["chat"], t, reply_markup=kb(rows))


@router.callback_query(F.data.startswith("pvs:"))
async def cb_video_choice(cb: CallbackQuery, bot: Bot):
    j = need(cb)
    if not j or not j.get("vpend"):
        return await cb.answer(EXPIRED, show_alert=True)
    _, key, val = cb.data.split(":")
    vp = j["vpend"]
    vp["p"][key] = val
    await cb.answer()
    try:
        await cb.message.delete()
    except TelegramBadRequest:
        pass
    if key == "style":
        vp["stage"] = "dur"
        t, items = _vq(1)
        return await bot.send_message(j["chat"], t, reply_markup=kb([[b] for b in items] + [[("↩️ إلغاء", "pcancel")]]))
    vp["stage"] = "audio"
    await bot.send_message(j["chat"], "🎧 أرسل الآن ملف الموسيقى (MP3 أو أي ملف صوتي) أو رسالة صوتية، حتى 20MB.\nأو اضغط «بدون موسيقى».",
                           reply_markup=kb([[("🔇 بدون موسيقى", "pvskip")], [("↩️ إلغاء", "pcancel")]]))


def _wants_audio(m: Message) -> bool:
    j = PJOBS.get(m.from_user.id) if m.from_user else None
    return bool(j and j.get("vpend") and j["vpend"]["stage"] == "audio")


@router.message(F.audio | F.voice | (F.document & F.document.mime_type.startswith("audio/")), _wants_audio)
async def on_video_audio(msg: Message, bot: Bot):
    j = PJOBS[msg.from_user.id]
    a = msg.audio or msg.voice or msg.document
    if a.file_size and a.file_size > MAX_DL:
        return await msg.answer("⚠️ الملف أكبر من 20MB.")
    dest = j["dir"] / "music.bin"
    await _download(bot, a.file_id, dest)
    j["vpend"]["p"]["audio"] = str(dest)
    await render_video(bot, j)


@router.callback_query(F.data == "pvskip")
async def cb_video_skip(cb: CallbackQuery, bot: Bot):
    j = need(cb)
    if not j or not j.get("vpend"):
        return await cb.answer(EXPIRED, show_alert=True)
    await cb.answer()
    try:
        await cb.message.delete()
    except TelegramBadRequest:
        pass
    await render_video(bot, j)


async def _ffmpeg(*args) -> tuple[int, str]:
    pr = await asyncio.create_subprocess_exec("ffmpeg", "-y", "-loglevel", "error", *args, stdout=asyncio.subprocess.DEVNULL, stderr=asyncio.subprocess.PIPE)
    try:
        _, err = await asyncio.wait_for(pr.communicate(), 600)
    except asyncio.TimeoutError:
        pr.kill()
        return 1, "timeout"
    return pr.returncode, err.decode(errors="ignore")[-300:]


async def render_video(bot: Bot, j: dict):
    vp = j["vpend"]
    j["vpend"] = None
    p = vp["p"]
    style, dur = p.get("style", "zin"), int(p.get("dur", 8))
    chat = j["chat"]
    j["busy"] = True
    t0 = time.time()
    wait = await bot.send_message(chat, "✅ تم استلام طلبك بنجاح\n\n🪄 جاري تحويل الصورة إلى فيديو...")
    ok = False
    try:
        async with sem:
            def prep():
                full = P.load(j["src"], 2048)
                st = P.apply_stack(full, j["stack"])
                img = st[0]
                h, w = img.shape[:2]
                path = str(j["dir"] / "kb.png")
                import cv2
                cv2.imwrite(path, img)
                return path, w, h
            path, w, h = await asyncio.to_thread(prep)
            asp = w / h
            if asp < 0.85:
                W, H = 720, 1280
            elif asp > 1.15:
                W, H = 1280, 720
            else:
                W, H = 1080, 1080
            fps, N = 30, dur * 30
            beat = None
            if p.get("audio"):
                import beats
                beat = await beats.analyze(p["audio"])
            pulse = ""
            if style == "pulse" and beat:
                per, ph = beat["period"], beat["phase"]
                pulse = f"+0.05*exp(-7*mod(on/{fps}-{ph:.3f}+{per * 100:.2f},{per:.4f}))"
            z = {"zin": f"1+0.35*on/{N}", "zout": f"1.35-0.35*on/{N}", "pr": "1.3", "pl": "1.3",
                 "pulse": f"1.12+0.12*on/{N}"}[style] + pulse
            x = {"pr": f"(iw-iw/zoom)*on/{N}", "pl": f"(iw-iw/zoom)*(1-on/{N})"}.get(style, "iw/2-(iw/zoom/2)")
            y = "ih/2-(ih/zoom/2)"
            vf = (f"scale={2 * W}:{2 * H}:force_original_aspect_ratio=increase,crop={2 * W}:{2 * H},"
                  f"zoompan=z='{z}':x='{x}':y='{y}':d={N}:s={W}x{H}:fps={fps},format=yuv420p")
            out = str(j["dir"] / "kb.mp4")
            args = ["-loop", "1", "-framerate", str(fps), "-i", path]
            if p.get("audio"):
                args += ["-stream_loop", "-1", "-i", p["audio"], "-af", f"afade=t=in:d=0.5,afade=t=out:st={dur - 1.5}:d=1.5", "-map", "0:v", "-map", "1:a", "-c:a", "aac", "-b:a", "160k"]
            args += ["-vf", vf, "-t", str(dur), "-c:v", "libx264", "-preset", "veryfast", "-crf", "20", "-movflags", "+faststart", out]
            rc, err = await _ffmpeg(*args)
            if rc != 0:
                raise RuntimeError(err)
        cap = f"تم اكمال طلبك بنجاح ✅\n\nالمده اللي انتهت : {fmt(time.time() - t0)}\n\nشكرا على استخدامك ♥️"
        await bot.send_video(chat, FSInputFile(out), caption=cap, width=W, height=H, duration=dur, supports_streaming=True)
        ok = True
        try:
            await wait.delete()
        except TelegramBadRequest:
            pass
    except Exception:  # noqa: BLE001
        log.exception("photo->video failed")
        try:
            await wait.edit_text("⚠️ تعذّر تحويل الصورة إلى فيديو. جرّب مرة ثانية أو بدون موسيقى.")
        except TelegramBadRequest:
            pass
    finally:
        j["busy"] = False
        try:
            await db.log_job(j["uid"], ok, time.time() - t0, ["photo2video"])
        except Exception:  # noqa: BLE001
            pass


async def cleaner():
    while True:
        await asyncio.sleep(300)
        now = time.time()
        for uid, j in list(PJOBS.items()):
            if not j.get("busy") and now - j["ts"] > TTL:
                drop(uid)

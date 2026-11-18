"""قائمة الأدوات داخل البوت: أقسام ← أدوات ← مدخلات ← نتيجة"""
import asyncio
import logging
import shutil
import time
import uuid
from pathlib import Path

from aiogram import Bot, F, Router
from aiogram.exceptions import TelegramBadRequest
from aiogram.filters import Command
from aiogram.types import BufferedInputFile, CallbackQuery, FSInputFile, InlineKeyboardButton, InlineKeyboardMarkup, Message

import config
import tools  # noqa: F401  (يسجّل الأدوات)
from tools.registry import CATS, TOOLS, Ask, Ctx, Out, ToolError, by_cat, run_tool

log = logging.getLogger("menu")
router = Router()
pending: dict[int, dict] = {}
_running: set[int] = set()
_cfg: dict = {}
MAX_DL = 20 * 1024 * 1024  # حد تنزيل الملفات عبر Bot API
TTL = 1800
MULTI = {"photos", "pdfs", "audios", "files", "media2"}


def setup(db, gate_fn, sub_kb, sub_text, sem):
    _cfg.update(db=db, gate=gate_fn, sub_kb=sub_kb, sub_text=sub_text, sem=sem)


def main_button() -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup(inline_keyboard=[[InlineKeyboardButton(text="🧰 الأدوات", callback_data="m:main")]])


def _kb(rows: list[list[tuple[str, str]]]) -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup(inline_keyboard=[[InlineKeyboardButton(text=t, callback_data=d) for t, d in r] for r in rows])


def main_menu() -> tuple[str, InlineKeyboardMarkup]:
    items = [(f"{CATS[c][0]} {CATS[c][1]} ({len(by_cat(c))})", f"c:{c}") for c in CATS if by_cat(c)]
    rows = [items[i:i + 2] for i in range(0, len(items), 2)]
    rows.append([("🔎 بحث عن أداة", "m:search")])
    total = len(TOOLS)
    return f"🧰 <b>الأدوات</b> — {total} أداة جاهزة\nاختر قسماً:", _kb(rows)


def cat_menu(cat: str) -> tuple[str, InlineKeyboardMarkup]:
    tl = by_cat(cat)
    items = [(t.title, f"t:{t.id}") for t in tl]
    rows = [items[i:i + 2] for i in range(0, len(items), 2)]
    rows.append([("↩️ الأقسام", "m:main")])
    return f"{CATS[cat][0]} <b>{CATS[cat][1]}</b>\nاختر الأداة:", _kb(rows)


async def _gate_cb(cb: CallbackQuery, bot: Bot) -> bool:
    miss = await _cfg["gate"](bot, cb.from_user.id)
    if miss:
        await cb.message.answer(_cfg["sub_text"], reply_markup=_cfg["sub_kb"](miss), disable_web_page_preview=True)
        await cb.answer()
        return False
    return True


async def _edit(cb: CallbackQuery, text: str, kb: InlineKeyboardMarkup | None):
    try:
        await cb.message.edit_text(text, reply_markup=kb)
    except TelegramBadRequest:
        await cb.message.answer(text, reply_markup=kb)


@router.message(Command("tools"))
async def cmd_tools(msg: Message, bot: Bot):
    miss = await _cfg["gate"](bot, msg.from_user.id)
    if miss:
        await msg.answer(_cfg["sub_text"], reply_markup=_cfg["sub_kb"](miss), disable_web_page_preview=True)
        return
    pending.pop(msg.from_user.id, None)
    t, kb = main_menu()
    await msg.answer(t, reply_markup=kb)


@router.message(Command("cancel"))
async def cmd_cancel(msg: Message):
    _drop(msg.from_user.id)
    await msg.answer("✅ أُلغيت العملية.", reply_markup=main_button())


@router.callback_query(F.data == "m:main")
async def cb_main(cb: CallbackQuery, bot: Bot):
    if not await _gate_cb(cb, bot):
        return
    _drop(cb.from_user.id)
    t, kb = main_menu()
    await _edit(cb, t, kb)
    await cb.answer()


@router.callback_query(F.data == "m:search")
async def cb_search(cb: CallbackQuery):
    pending[cb.from_user.id] = {"mode": "search", "ts": time.time()}
    await cb.message.answer("🔎 اكتب كلمة للبحث عن أداة (مثال: pdf، صورة، عملات، qr):")
    await cb.answer()


@router.callback_query(F.data.startswith("c:"))
async def cb_cat(cb: CallbackQuery, bot: Bot):
    if not await _gate_cb(cb, bot):
        return
    cat = cb.data[2:]
    if cat not in CATS:
        return await cb.answer()
    t, kb = cat_menu(cat)
    await _edit(cb, t, kb)
    await cb.answer()


def _drop(uid: int):
    p = pending.pop(uid, None)
    if p and p.get("dir"):
        shutil.rmtree(p["dir"], ignore_errors=True)


def _need_text(inp: str) -> str:
    return {
        "text": "✍️ أرسل النص الآن.", "photo": "🖼 أرسل الصورة الآن.", "video": "🎞 أرسل الفيديو الآن (حتى 20MB).",
        "audio": "🎧 أرسل ملف الصوت أو الرسالة الصوتية (حتى 20MB).", "pdf": "📄 أرسل ملف PDF الآن.", "file": "📎 أرسل الملف الآن.",
        "media": "🎞 أرسل فيديو أو ملف صوت.", "location": "📍 أرسل موقعك (📎 ← الموقع) أو اكتب الإحداثيات.",
        "photos": "🖼 أرسل الصور واحدة تلو الأخرى ثم اضغط «✅ تم».", "pdfs": "📄 أرسل ملفات PDF بالترتيب ثم اضغط «✅ تم».",
        "audios": "🎧 أرسل الملفات الصوتية بالترتيب ثم اضغط «✅ تم».", "files": "📎 أرسل الملفات ثم اضغط «✅ تم».",
        "media2": "🎧 أرسل ملف الصوت ثم الصورة، بعدها «✅ تم».",
    }.get(inp, "✍️ أرسل المدخل.")


async def _begin(cb_or_msg, uid: int, tool_id: str, opt: str = ""):
    t = TOOLS[tool_id]
    _drop(uid)
    pending[uid] = {"tool": tool_id, "opt": opt, "step": "input", "files": [], "ts": time.time(), "dir": None}
    kb = _kb([[("❌ إلغاء", "m:main")]])
    head = f"🛠 <b>{t.title}</b>"
    if opt:
        lab = next((l for v, l in t.opts if v == opt), opt)
        head += f" — {lab}"
    txt = f"{head}\n\n{t.hint}\n\n{_need_text(t.inp)}" if t.inp != "none" else f"{head}"
    if hasattr(cb_or_msg, "message"):
        await _edit(cb_or_msg, txt, kb)
    else:
        await cb_or_msg.answer(txt, reply_markup=kb)


@router.callback_query(F.data.startswith("t:"))
async def cb_tool(cb: CallbackQuery, bot: Bot):
    if not await _gate_cb(cb, bot):
        return
    tid = cb.data[2:]
    t = TOOLS.get(tid)
    if not t:
        return await cb.answer("الأداة غير موجودة")
    uid = cb.from_user.id
    if t.opts:
        _drop(uid)
        rows = [[(l, f"o:{tid}:{i}")] for i, (v, l) in enumerate(t.opts)]
        rows = [r for r in rows]
        # خيارات في صفوف من اثنين لتوفير المساحة
        flat = [x[0] for x in rows]
        grid = [flat[i:i + 2] for i in range(0, len(flat), 2)]
        grid.append([("↩️ رجوع", f"c:{t.cat}")])
        await _edit(cb, f"🛠 <b>{t.title}</b>\n\n{t.hint}\n\nاختر:", _kb(grid))
        return await cb.answer()
    await cb.answer()
    await _begin(cb, uid, tid)
    if t.inp == "none":
        await _run(cb.message.chat.id, uid, bot)


@router.callback_query(F.data.startswith("o:"))
async def cb_opt(cb: CallbackQuery, bot: Bot):
    if not await _gate_cb(cb, bot):
        return
    _, tid, idx = cb.data.split(":")
    t = TOOLS.get(tid)
    if not t:
        return await cb.answer()
    opt = t.opts[int(idx)][0]
    uid = cb.from_user.id
    await cb.answer()
    await _begin(cb, uid, tid, opt)
    if t.inp == "none" or opt in t.auto:
        await _run(cb.message.chat.id, uid, bot)


@router.callback_query(F.data == "done")
async def cb_done(cb: CallbackQuery, bot: Bot):
    p = pending.get(cb.from_user.id)
    if not p:
        return await cb.answer("لا توجد عملية")
    await cb.answer()
    await _run(cb.message.chat.id, cb.from_user.id, bot)


# ---------------- استقبال المدخلات ----------------
def _has_pending(m: Message) -> bool:
    p = pending.get(m.from_user.id) if m.from_user else None
    if not p:
        return False
    if time.time() - p["ts"] > TTL:
        _drop(m.from_user.id)
        return False
    if m.text and m.text.startswith("/"):
        return False
    return True


async def _download(bot: Bot, file_id: str, size: int | None, workdir: Path, name: str) -> str:
    if size and size > MAX_DL:
        raise ToolError("الملف أكبر من 20MB (حد تيليجرام للبوتات). أرسل ملفاً أصغر.")
    dest = workdir / f"{uuid.uuid4().hex[:6]}__{name}"
    await bot.download(file_id, destination=dest)
    return str(dest)


def _extract(m: Message):
    """(kind, file_id, size, name)"""
    if m.photo:
        ph = m.photo[-1]
        return "photo", ph.file_id, ph.file_size, "photo.jpg"
    if m.video:
        return "video", m.video.file_id, m.video.file_size, m.video.file_name or "video.mp4"
    if m.animation:
        return "video", m.animation.file_id, m.animation.file_size, m.animation.file_name or "anim.mp4"
    if m.video_note:
        return "video", m.video_note.file_id, m.video_note.file_size, "note.mp4"
    if m.audio:
        return "audio", m.audio.file_id, m.audio.file_size, m.audio.file_name or "audio.mp3"
    if m.voice:
        return "audio", m.voice.file_id, m.voice.file_size, "voice.ogg"
    if m.sticker and not m.sticker.is_animated and not m.sticker.is_video:
        return "photo", m.sticker.file_id, m.sticker.file_size, "sticker.webp"
    if m.document:
        mime = (m.document.mime_type or "").lower()
        nm = m.document.file_name or "file"
        k = "photo" if mime.startswith("image/") else ("video" if mime.startswith("video/") else ("audio" if mime.startswith("audio/") else ("pdf" if mime == "application/pdf" or nm.lower().endswith(".pdf") else "file")))
        return k, m.document.file_id, m.document.file_size, nm
    return None


def _accepts(inp: str, kind: str) -> bool:
    return {
        "photo": kind == "photo" or kind == "file", "video": kind == "video", "audio": kind in ("audio", "video"), "pdf": kind == "pdf",
        "file": True, "media": kind in ("video", "audio"), "photos": kind == "photo", "pdfs": kind == "pdf", "audios": kind in ("audio", "video"),
        "files": True, "media2": kind in ("audio", "photo", "video"),
    }.get(inp, False)


@router.message(_has_pending)
async def on_input(msg: Message, bot: Bot):
    uid = msg.from_user.id
    p = pending[uid]
    p["ts"] = time.time()
    if p.get("mode") == "search":
        q = (msg.text or "").strip().lower()
        pending.pop(uid, None)
        res = [t for t in TOOLS.values() if q and (q in t.title.lower() or q in t.hint.lower() or q in t.id)]
        if not res:
            return await msg.answer("لم أجد أداة بهذا الاسم. جرّب كلمة أخرى أو افتح /tools")
        rows = [[(t.title, f"t:{t.id}")] for t in res[:20]]
        rows.append([("↩️ الأقسام", "m:main")])
        return await msg.answer(f"🔎 {len(res)} نتيجة:", reply_markup=_kb(rows))
    if not await _gate_msg(msg, bot):
        return
    t = TOOLS[p["tool"]]
    chat = msg.chat.id
    if p["step"] == "ask":
        if not msg.text:
            return await msg.answer("✍️ أرسل نصاً.")
        p["text"] = msg.text
        return await _run(chat, uid, bot)
    # مدخل عادي
    if t.inp in ("text", "location", "none") or (msg.text and t.inp not in MULTI and t.inp not in ("photo", "video", "audio", "pdf", "file", "media")):
        if msg.location:
            p["text"] = f"{msg.location.latitude} {msg.location.longitude}"
        elif msg.text:
            p["text"] = msg.text
        else:
            return await msg.answer(_need_text(t.inp))
        return await _run(chat, uid, bot)
    ex = _extract(msg)
    if not ex:
        if msg.text and t.inp in ("photo", "video", "audio", "pdf", "file", "media"):
            return await msg.answer("⚠️ " + _need_text(t.inp))
        return await msg.answer("⚠️ نوع غير مناسب. " + _need_text(t.inp))
    kind, fid, size, name = ex
    if not _accepts(t.inp, kind):
        return await msg.answer("⚠️ هذا النوع غير مناسب لهذه الأداة. " + _need_text(t.inp))
    if not p.get("dir"):
        p["dir"] = config.TMP_DIR / f"u{uid}_{uuid.uuid4().hex[:8]}"
        p["dir"].mkdir(parents=True, exist_ok=True)
    try:
        path = await _download(bot, fid, size, p["dir"], name)
    except ToolError as e:
        return await msg.answer(f"⚠️ {e}")
    except Exception:  # noqa: BLE001
        log.exception("download")
        return await msg.answer("⚠️ تعذّر تنزيل الملف، أعد المحاولة.")
    p["files"].append((path, name))
    if t.inp in MULTI:
        n = len(p["files"])
        return await msg.answer(f"📥 استلمت {n} ملف. أرسل المزيد أو اضغط «✅ تم».", reply_markup=_kb([[("✅ تم", "done"), ("❌ إلغاء", "m:main")]]))
    await _run(chat, uid, bot)


async def _gate_msg(msg: Message, bot: Bot) -> bool:
    miss = await _cfg["gate"](bot, msg.from_user.id)
    if miss:
        await msg.answer(_cfg["sub_text"], reply_markup=_cfg["sub_kb"](miss), disable_web_page_preview=True)
        return False
    return True


# ---------------- التنفيذ والإرسال ----------------
async def _send(bot: Bot, chat: int, o: Out, kb=None):
    def inp(data, name):
        return FSInputFile(data, filename=name or None) if isinstance(data, (str, Path)) else BufferedInputFile(data, filename=name or "file")
    cap = (o.caption or "")[:1000] or None
    if o.kind == "text":
        txt = o.data
        for i in range(0, len(txt), 4000):
            await bot.send_message(chat, txt[i:i + 4000], disable_web_page_preview=True, reply_markup=kb if i + 4000 >= len(txt) else None)
    elif o.kind == "photo":
        await bot.send_photo(chat, inp(o.data, "image.png"), caption=cap, reply_markup=kb)
    elif o.kind == "doc":
        await bot.send_document(chat, inp(o.data, o.name), caption=cap, reply_markup=kb)
    elif o.kind == "video":
        await bot.send_video(chat, inp(o.data, "video.mp4"), caption=cap, supports_streaming=True, reply_markup=kb)
    elif o.kind == "audio":
        await bot.send_audio(chat, inp(o.data, o.name or "audio.mp3"), caption=cap, reply_markup=kb)
    elif o.kind == "note":
        await bot.send_video_note(chat, inp(o.data, "note.mp4"))
    elif o.kind == "sticker":
        await bot.send_sticker(chat, inp(o.data, "sticker.webp"))
    elif o.kind == "anim":
        await bot.send_animation(chat, inp(o.data, "anim.gif"), caption=cap, reply_markup=kb)


async def _run(chat: int, uid: int, bot: Bot):
    p = pending.get(uid)
    if not p:
        return
    if uid in _running:
        return await bot.send_message(chat, "⏳ لديك عملية قيد التنفيذ، انتظر انتهاءها.")
    t = TOOLS[p["tool"]]
    if not p.get("dir"):
        p["dir"] = config.TMP_DIR / f"u{uid}_{uuid.uuid4().hex[:8]}"
        p["dir"].mkdir(parents=True, exist_ok=True)
    files = [f for f, _ in p["files"]]
    ctx = Ctx(uid=uid, workdir=p["dir"], text=p.get("text", ""), path=files[0] if files else None, paths=files, opt=p.get("opt", ""), state=p.get("state", {}), name=p["files"][0][1] if p["files"] else "")
    _running.add(uid)
    wait = await bot.send_message(chat, "لحظه من فضلك . . .")
    ok = False
    try:
        async with _cfg["sem"]:
            try:
                outs = await asyncio.wait_for(run_tool(t, ctx), 420)
            except Ask as a:
                p["step"] = "ask"
                p["state"] = a.state
                await bot.send_message(chat, a.prompt, reply_markup=_kb([[("❌ إلغاء", "m:main")]]))
                return
        kb = _kb([[("🔁 أداة أخرى", "m:main")]])
        for i, o in enumerate(outs):
            await _send(bot, chat, o, kb if i == len(outs) - 1 and o.kind in ("text", "photo", "doc", "video", "audio") else None)
        if outs and outs[-1].kind in ("sticker", "note"):
            await bot.send_message(chat, "✅ تم", reply_markup=kb)
        ok = True
    except ToolError as e:
        await bot.send_message(chat, f"⚠️ {e}", reply_markup=_kb([[("🧰 الأدوات", "m:main")]]))
    except asyncio.TimeoutError:
        await bot.send_message(chat, "⏱ استغرقت العملية وقتاً طويلاً وأُلغيت.")
    except TelegramBadRequest as e:
        log.warning("telegram rejected output: %s", e)
        await bot.send_message(chat, "⚠️ رفض تيليجرام الناتج (ربما كبير جداً).")
    except Exception:  # noqa: BLE001
        log.exception("tool %s failed", t.id)
        await bot.send_message(chat, "⚠️ حدث خطأ أثناء المعالجة. جرّب مدخلاً آخر.")
    finally:
        _running.discard(uid)
        try:
            await wait.delete()
        except TelegramBadRequest:
            pass
        if ok or p.get("step") != "ask":
            # ننظف الملفات ونُبقي الأداة جاهزة لاستقبال مدخل جديد
            shutil.rmtree(p.get("dir") or "", ignore_errors=True)
            if pending.get(uid) is p:
                if ok and t.inp not in ("none",):
                    p.update(step="input", files=[], dir=None, text="", state={}, ts=time.time())
                else:
                    pending.pop(uid, None)
        if ok:
            try:
                await _cfg["db"].log_download(uid, f"tool:{t.id}", True)
            except Exception:  # noqa: BLE001
                pass

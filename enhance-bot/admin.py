"""لوحة تحكم المسؤول (/admin): إحصائيات • مستخدمون • بحث • إذاعة • حظر • إعدادات • إشعارات • سجلات • نظام • تصدير.
كل شيء بأزرار تتبدل في نفس الرسالة. يتحكم بها فقط الآيديات في ADMIN_IDS."""
import asyncio
import csv
import io
import logging
import os
import platform
import shutil
import sys
import time
from pathlib import Path

from aiogram import BaseMiddleware, Bot, F, Router
from aiogram.exceptions import TelegramBadRequest, TelegramForbiddenError, TelegramRetryAfter
from aiogram.filters import BaseFilter, Command
from aiogram.types import BufferedInputFile, CallbackQuery, InlineKeyboardButton, InlineKeyboardMarkup, Message

import auth
import db
import photo

log = logging.getLogger("admin")
router = Router()
ADMIN_IDS: set[int] = set()
BOT: Bot | None = None
CTX: dict = {}  # main يضع: JOBS, WORK, drop_job
STARTED = time.time()
STATE: dict[int, dict] = {}  # حالة إدخال المسؤول (بحث/إذاعة/رسالة)
auth.ADMIN_STATE = STATE
USERS_PER_PAGE = 8


class AdminOnly(BaseFilter):
    async def __call__(self, ev) -> bool:
        return bool(ev.from_user and ev.from_user.id in ADMIN_IDS)


router.message.filter(AdminOnly())
router.callback_query.filter(AdminOnly())


# ---------------------------------------------------------------- أدوات
def kb(rows) -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup(inline_keyboard=[[x if isinstance(x, InlineKeyboardButton) else InlineKeyboardButton(text=x[0], callback_data=x[1]) for x in r] for r in rows])


def ago(ts) -> str:
    if not ts:
        return "—"
    s = max(0, int(time.time() - ts))
    for n, u in ((86400, "يوم"), (3600, "ساعة"), (60, "دقيقة")):
        if s >= n:
            return f"قبل {s // n} {u}"
    return "الآن"


def dt(ts) -> str:
    return time.strftime("%Y-%m-%d %H:%M", time.localtime(ts)) if ts else "—"


def fmt_dur(sec: float) -> str:
    sec = int(sec)
    d, r = divmod(sec, 86400)
    h, r = divmod(r, 3600)
    m, s = divmod(r, 60)
    return (f"{d}ي " if d else "") + (f"{h}س " if h else "") + (f"{m}د " if m else "") + (f"{s}ث" if not d else "")


def bar(v: int, mx: int, n: int = 10) -> str:
    k = 0 if mx <= 0 else max(1 if v else 0, round(v / mx * n))
    return "█" * k + "░" * (n - k)


def esc(s) -> str:
    return str(s or "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def uname(u) -> str:
    return f"@{u}" if u else "—"


async def show(ev, text: str, markup: InlineKeyboardMarkup):
    """يعرض الشاشة: تحرير رسالة اللوحة إن كان ضغط زر، وإلا رسالة جديدة"""
    text = text[:4000]
    if isinstance(ev, CallbackQuery):
        try:
            await ev.message.edit_text(text, reply_markup=markup)
            return
        except TelegramBadRequest as e:
            if "not modified" in str(e):
                return
        await ev.message.answer(text, reply_markup=markup)
    else:
        await ev.answer(text, reply_markup=markup)


async def notify_admins(text: str, kind: str = "info"):
    await db.add_alert(kind, text.replace("\n", " ")[:300])
    if BOT is None:
        return
    for a in ADMIN_IDS:
        try:
            await BOT.send_message(a, text, reply_markup=kb([[("⟦gear⟧ لوحة التحكم", "ad:home")]]))
        except Exception:  # noqa: BLE001
            pass


# ---------------------------------------------------------------- الحماية العامة: حظر + صيانة
class Gate(BaseMiddleware):
    async def __call__(self, handler, event, data):
        u = getattr(event, "from_user", None)
        if u:
            if not auth.gate_allows(event):  # تسجيل الدخول إجباري
                try:
                    if isinstance(event, CallbackQuery):
                        await event.answer("سجّل دخولك أولاً", show_alert=True)
                        t, k = auth.welcome_screen()
                        await event.message.answer(t, reply_markup=k)
                    else:
                        await auth.send_welcome(event)
                except Exception:  # noqa: BLE001
                    pass
                return None
            if u.id not in ADMIN_IDS:
                text = None
                if db.banned(u.id):
                    text = "🚫 تم حظرك من استخدام هذا البوت."
                elif db.flag("maintenance"):
                    text = "🛠 البوت تحت الصيانة حالياً، عاود المحاولة بعد قليل."
                if text:
                    try:
                        if isinstance(event, CallbackQuery):
                            await event.answer(text, show_alert=True)
                        else:
                            await event.answer(text)
                    except Exception:  # noqa: BLE001
                        pass
                    return None
        return await handler(event, data)


async def quota_msg(uid: int) -> str | None:
    """رسالة منع إن تجاوز المستخدم الحد اليومي للمعالجات"""
    if uid in ADMIN_IDS:
        return None
    lim = int(db.setting("daily_limit") or 0)
    if lim and await db.jobs_today(uid) >= lim:
        return f"وصلت الحد اليومي ({lim} معالجات). عاود غداً."
    return None


def feature_msg(kind: str, uid: int) -> str | None:
    if uid in ADMIN_IDS:
        return None
    if kind == "video" and not db.flag("allow_video"):
        return "🛠 قسم الفيديو متوقف مؤقتاً."
    if kind == "photo" and not db.flag("allow_photo"):
        return "🛠 قسم الصور متوقف مؤقتاً."
    return None


def max_seconds() -> int:
    return int(float(db.setting("max_minutes") or 120) * 60)


async def _on_new_user(uid, name, username):
    if uid in ADMIN_IDS or not db.flag("alert_new"):
        return
    await notify_admins(f"👤 <b>مستخدم جديد</b>\n{esc(name)} • {uname(username)} • <code>{uid}</code>", "new_user")


async def _on_job(uid, ok, secs, effects):
    if ok and db.flag("alert_jobs"):
        await notify_admins(f"✅ عملية ناجحة • <code>{uid}</code> • {secs:.0f}ث\n{esc(', '.join(effects)[:150])}", "job")
    elif not ok and db.flag("alert_fail"):
        await notify_admins(f"⚠️ <b>فشلت عملية</b> • <code>{uid}</code> • {secs:.0f}ث\n{esc(', '.join(effects)[:200])}", "fail")


async def _on_new_account(uid, username):
    if uid in ADMIN_IDS or not db.flag("alert_new"):
        return
    await notify_admins(f"🔐 <b>حساب جديد</b>\n<code>{esc(username)}</code> • تيليجرام <code>{uid}</code>", "account")


async def _on_lockout(uid, username):
    if db.flag("alert_fail"):
        await notify_admins(f"🔒 <b>محاولات دخول فاشلة</b>\nتم قفل <code>{esc(username)}</code> 5 دقائق • تيليجرام <code>{uid}</code>", "security")


def install(bot: Bot, admin_ids: set[int], ctx: dict):
    global BOT
    BOT = bot
    ADMIN_IDS.clear()
    ADMIN_IDS.update(admin_ids)
    CTX.update(ctx)
    db.HOOKS["new_user"] = _on_new_user
    db.HOOKS["new_account"] = _on_new_account
    db.HOOKS["lockout"] = _on_lockout
    db.HOOKS["job_done"] = _on_job
    db.load_cache()


# ---------------------------------------------------------------- الشاشة الرئيسية
async def screen_home():
    t = await db.totals()
    at = await db.account_totals()
    jobs, pj = CTX.get("JOBS", {}), photo.PJOBS
    running = sum(1 for j in jobs.values() if j.get("running"))
    rate = (t["jobs_all"] / max(1, t["jobs_all"] + t["failed_all"])) * 100
    flags = []
    if db.flag("maintenance"):
        flags.append("🛠 وضع الصيانة مفعّل")
    if not db.flag("allow_video"):
        flags.append("⛔ الفيديو متوقف")
    if not db.flag("allow_photo"):
        flags.append("⛔ الصور متوقفة")
    text = (
        "⟦gear⟧ <b>لوحة تحكم المسؤول</b>\n"
        f"⏱ يعمل منذ {fmt_dur(time.time() - STARTED)}\n\n"
        f"⟦user⟧ <b>المستخدمون:</b> {t['users']}  (جدد اليوم {t['d1']['new']} • نشطون اليوم {t['d1']['active']})\n"
        f"⟦badge⟧ <b>العمليات الناجحة:</b> {t['jobs_all']}  (اليوم {t['d1']['jobs']} • الأسبوع {t['d7']['jobs']})\n"
        f"⚠️ <b>الفاشلة:</b> {t['failed_all']}  •  نسبة النجاح {rate:.0f}%\n"
        f"⏳ <b>جلسات الآن:</b> فيديو {len(jobs)} (تصدير {running}) • صور {len(pj)}\n"
        f"⟦verified⟧ <b>الحسابات:</b> {at['total']} (جدد اليوم {at['new_today']} • متصلون الآن {at['online']})\n"
        f"🚫 <b>محظورون:</b> {t['banned']}" + ("\n\n" + "\n".join(flags) if flags else "")
    )
    rows = [
        [("⟦badge⟧ الإحصائيات", "ad:stats"), ("⟦user⟧ المستخدمون", "ad:users:last:0")],
        [("⟦search⟧ بحث عن مستخدم", "ad:search"), ("⟦share⟧ إذاعة", "ad:bc")],
        [("⟦verified⟧ الحسابات", "ad:acc:last_login:0"), ("⟦search⟧ بحث عن حساب", "ad:asearch")],
        [("⟦star⟧ الأكثر استخداماً", "ad:top"), ("⟦file⟧ آخر العمليات", "ad:logs")],
        [("⟦layers⟧ الجلسات النشطة", "ad:sess"), ("🔔 الإشعارات", "ad:alerts")],
        [("⟦minus⟧ المحظورون", "ad:bans"), ("⟦gear⟧ الإعدادات", "ad:set")],
        [("⟦grid⟧ حالة النظام", "ad:sys"), ("⟦download⟧ تصدير البيانات", "ad:exp")],
        [("📋 تقرير اليوم مقابل أمس", "ad:rep")],
        [("⟦trash⟧ تنظيف الملفات المؤقتة", "ad:clean"), ("⟦refresh⟧ تحديث", "ad:home")],
        [("❌ إغلاق اللوحة", "ad:close")],
    ]
    return text, kb(rows)


BACK = [("⟦home⟧ اللوحة الرئيسية", "ad:home")]


@router.message(Command("admin"))
async def cmd_admin(msg: Message):
    STATE.pop(msg.from_user.id, None)
    await show(msg, *await screen_home())


@router.message(F.text.in_({"ادمن", "الادمن", "لوحة التحكم", "لوحة"}))
async def txt_admin(msg: Message):
    await cmd_admin(msg)


@router.callback_query(F.data == "ad:home")
async def cb_home(cb: CallbackQuery):
    STATE.pop(cb.from_user.id, None)
    await cb.answer()
    await show(cb, *await screen_home())


@router.callback_query(F.data == "ad:close")
async def cb_close(cb: CallbackQuery):
    STATE.pop(cb.from_user.id, None)
    await cb.answer("أُغلقت")
    try:
        await cb.message.delete()
    except TelegramBadRequest:
        pass


# ---------------------------------------------------------------- الإحصائيات
@router.callback_query(F.data == "ad:stats")
async def cb_stats(cb: CallbackQuery):
    await cb.answer()
    t = await db.totals()
    days = await db.per_day(7)
    mx = max([d[2] + d[3] for d in days] + [1])
    mxu = max([d[1] for d in days] + [1])
    lines = "\n".join(f"<code>{d[0]}</code> {bar(d[2], mx)} {d[2]}✓ {d[3]}✗ • 👤+{d[1]}" for d in days)
    peak = " • ".join(f"{h:02d}:00 ({n})" for h, n in t["peak"]) or "—"
    text = (
        "⟦badge⟧ <b>الإحصائيات التفصيلية</b>\n\n"
        f"<b>آخر 24 ساعة:</b> {t['d1']['jobs']} ناجحة • {t['d1']['failed']} فاشلة • {t['d1']['active']} نشط • {t['d1']['new']} جديد\n"
        f"<b>آخر 7 أيام:</b> {t['d7']['jobs']} ناجحة • {t['d7']['failed']} فاشلة • {t['d7']['active']} نشط • {t['d7']['new']} جديد\n"
        f"<b>آخر 30 يوماً:</b> {t['d30']['jobs']} ناجحة • {t['d30']['failed']} فاشلة • {t['d30']['active']} نشط • {t['d30']['new']} جديد\n\n"
        f"⏱ متوسط زمن المعالجة: {t['avg']:.0f}ث • أطولها: {fmt_dur(t['max'])}\n"
        f"🕐 ساعات الذروة (آخر أسبوع): {peak}\n"
        f"🚫 حظر تيليجرام للبوت: {t['blocked']} مستخدم\n\n"
        f"<b>آخر 7 أيام (يومياً):</b>\n{lines}"
    )
    await show(cb, text, kb([[("⟦star⟧ الأكثر استخداماً", "ad:top"), ("⟦file⟧ آخر العمليات", "ad:logs")], BACK]))


@router.callback_query(F.data == "ad:top")
async def cb_top(cb: CallbackQuery):
    await cb.answer()
    import effects as E
    import photolib as P
    vid = await db.top_tools(False, 10)
    pho = await db.top_tools(True, 10)

    def name(tid, photo_):
        if photo_:
            t = P.TOOLS.get(tid)
            return t.title if t else tid
        e = E.EFFECTS.get(tid)
        return e.title if e else tid
    mv = max([n for _, n in vid] + [1])
    mp = max([n for _, n in pho] + [1])
    lv = "\n".join(f"{i}. {esc(name(t, False))} — {bar(n, mv, 6)} {n}" for i, (t, n) in enumerate(vid, 1)) or "لا بيانات بعد"
    lp = "\n".join(f"{i}. {esc(name(t, True))} — {bar(n, mp, 6)} {n}" for i, (t, n) in enumerate(pho, 1)) or "لا بيانات بعد"
    await show(cb, f"⟦star⟧ <b>الأكثر استخداماً</b>\n\n⟦film⟧ <b>الفيديو:</b>\n{lv}\n\n⟦photos⟧ <b>الصور:</b>\n{lp}", kb([[("⟦badge⟧ الإحصائيات", "ad:stats")], BACK]))


@router.callback_query(F.data == "ad:logs")
async def cb_logs(cb: CallbackQuery):
    await cb.answer()
    rows = await db.recent_jobs(15)
    lines = []
    for ts, uid, name, ok, secs, eff in rows:
        kind = "🖼" if db.is_photo(eff) else "🎬"
        lines.append(f"{'✅' if ok else '⚠️'}{kind} <code>{time.strftime('%H:%M', time.localtime(ts))}</code> {esc(name)[:14]} (<code>{uid}</code>) {secs:.0f}ث\n    {esc((eff or '')[:60])}")
    await show(cb, "⟦file⟧ <b>آخر العمليات</b>\n\n" + ("\n".join(lines) or "لا عمليات بعد"), kb([[("⟦refresh⟧ تحديث", "ad:logs")], BACK]))


# ---------------------------------------------------------------- المستخدمون
SORTS = {"last": "الأحدث نشاطاً", "new": "الأحدث انضماماً", "jobs": "الأكثر عمليات"}


@router.callback_query(F.data.startswith("ad:users:"))
async def cb_users(cb: CallbackQuery):
    await cb.answer()
    _, _, sort, page = cb.data.split(":")
    page = int(page)
    total = await db.count_users()
    rows = await db.list_users(sort, page * USERS_PER_PAGE, USERS_PER_PAGE)
    lines = [f"{page * USERS_PER_PAGE + i}. {esc(r[1])[:18]} {uname(r[2])} • {r[5]} عملية • {ago(r[4])}" for i, r in enumerate(rows, 1)]
    btns = [[(f"⟦user⟧ {r[1][:16] or r[0]}", f"ad:u:{r[0]}")] for r in rows]
    nav = []
    if page > 0:
        nav.append(("⟦left⟧ السابق", f"ad:users:{sort}:{page - 1}"))
    nav.append((f"{page + 1}/{max(1, -(-total // USERS_PER_PAGE))}", "noop"))
    if (page + 1) * USERS_PER_PAGE < total:
        nav.append(("التالي ⟦right⟧", f"ad:users:{sort}:{page + 1}"))
    sorts = [(("• " if s == sort else "") + lab, f"ad:users:{s}:0") for s, lab in SORTS.items()]
    await show(cb, f"⟦user⟧ <b>المستخدمون ({total})</b> — {SORTS[sort]}\n\n" + ("\n".join(lines) or "لا مستخدمين"), kb(btns + [nav, sorts, [("⟦search⟧ بحث", "ad:search")], BACK]))


async def screen_user(uid: int):
    d = await db.user_detail(uid)
    if not d:
        return "لم أجد هذا المستخدم.", kb([BACK])
    recent = "\n".join(f"  {'✅' if r[1] else '⚠️'} {ago(r[0])} • {r[2]:.0f}ث • {esc((r[3] or '')[:40])}" for r in d["recent"]) or "  —"
    sess = []
    if uid in CTX.get("JOBS", {}):
        sess.append("جلسة فيديو")
    if uid in photo.PJOBS:
        sess.append("جلسة صور")
    status = "🚫 محظور" + (f" ({esc(d['ban'][0])})" if d["ban"] and d["ban"][0] else "") if d["ban"] else ("⛔ حظر البوت" if d["blocked"] else "✅ عادي")
    link = f'<a href="tg://user?id={uid}">{esc(d["name"]) or uid}</a>'
    text = (
        f"⟦user⟧ <b>تفاصيل المستخدم</b>\n\n"
        f"الاسم: {link}\nاليوزر: {uname(d['username'])}\nالآيدي: <code>{uid}</code>\n"
        f"الحالة: {status}\n"
        f"انضم: {dt(d['first'])} ({ago(d['first'])})\nآخر نشاط: {ago(d['last'])}\n\n"
        f"العمليات: {d['jobs']} (ناجحة {d['ok']}) • اليوم {d['today']} • متوسط {d['avg']:.0f}ث\n"
        f"جلسات مفتوحة: {'، '.join(sess) or 'لا يوجد'}\n\n<b>آخر عملياته:</b>\n{recent}"
    )
    rows = [[("⟦chat⟧ إرسال رسالة له", f"ad:dm:{uid}")]]
    rows.append([("✅ رفع الحظر", f"ad:unban:{uid}")] if d["ban"] else [("🚫 حظر المستخدم", f"ad:ban:{uid}")])
    if sess:
        rows.append([("⟦clear⟧ إنهاء جلساته", f"ad:kill:{uid}")])
    rows.append([("⟦left⟧ المستخدمون", "ad:users:last:0")])
    rows.append(BACK)
    return text, kb(rows)


@router.callback_query(F.data.startswith("ad:u:"))
async def cb_user(cb: CallbackQuery):
    await cb.answer()
    await show(cb, *await screen_user(int(cb.data.split(":")[2])))


@router.callback_query(F.data.startswith("ad:ban:"))
async def cb_ban(cb: CallbackQuery):
    uid = int(cb.data.split(":")[2])
    if uid in ADMIN_IDS:
        return await cb.answer("لا يمكن حظر مسؤول", show_alert=True)
    await db.ban(uid, "بقرار المسؤول")
    CTX["drop_job"](uid)
    photo.drop(uid)
    await cb.answer("تم الحظر")
    await show(cb, *await screen_user(uid))


@router.callback_query(F.data.startswith("ad:unban:"))
async def cb_unban(cb: CallbackQuery):
    uid = int(cb.data.split(":")[2])
    await db.unban(uid)
    await cb.answer("تم رفع الحظر")
    await show(cb, *await screen_user(uid))


@router.callback_query(F.data.startswith("ad:kill:"))
async def cb_kill(cb: CallbackQuery):
    uid = int(cb.data.split(":")[2])
    j = CTX.get("JOBS", {}).get(uid)
    if j and j.get("running") and j.get("task") and not j["task"].done():
        j["task"].cancel()
    CTX["drop_job"](uid)
    photo.drop(uid)
    await cb.answer("أُنهيت جلساته")
    await show(cb, *await screen_user(uid))


@router.callback_query(F.data == "ad:bans")
async def cb_bans(cb: CallbackQuery):
    await cb.answer()
    rows = await db.banned_list()
    lines = [f"• {esc(n)[:16]} <code>{u}</code> — {esc(r)[:20]} ({ago(ts)})" for u, n, r, ts in rows]
    btns = [[(f"✅ رفع الحظر عن {n[:12] or u}", f"ad:unban:{u}")] for u, n, r, ts in rows[:10]]
    await show(cb, f"⟦minus⟧ <b>المحظورون ({len(rows)})</b>\n\n" + ("\n".join(lines) or "لا يوجد محظورون") + "\n\nلحظر شخص: افتح تفاصيله من «بحث» أو «المستخدمون».", kb(btns + [[("⟦search⟧ بحث", "ad:search")], BACK]))


# ---------------------------------------------------------------- البحث / رسالة لمستخدم / إذاعة
@router.callback_query(F.data == "ad:search")
async def cb_search(cb: CallbackQuery):
    await cb.answer()
    STATE[cb.from_user.id] = {"mode": "search"}
    await show(cb, "⟦search⟧ <b>بحث عن مستخدم</b>\n\nأرسل الآن الآيدي أو @اليوزر أو جزءاً من الاسم.", kb([[("↩️ إلغاء", "ad:home")]]))


@router.callback_query(F.data.startswith("ad:dm:"))
async def cb_dm(cb: CallbackQuery):
    await cb.answer()
    uid = int(cb.data.split(":")[2])
    STATE[cb.from_user.id] = {"mode": "dm", "uid": uid}
    await show(cb, f"⟦chat⟧ <b>رسالة إلى <code>{uid}</code></b>\n\nأرسل الآن الرسالة (نص أو صورة أو فيديو...) وسأوصلها له من البوت.", kb([[("↩️ إلغاء", f"ad:u:{uid}")]]))


AUD = {"all": "كل المستخدمين", "today": "نشطون آخر 24 ساعة", "week": "نشطون آخر 7 أيام", "new": "المنضمون اليوم", "workers": "من جرّبوا المعالجة"}


@router.callback_query(F.data == "ad:bc")
async def cb_bc(cb: CallbackQuery):
    await cb.answer()
    STATE.pop(cb.from_user.id, None)
    rows = []
    for k, lab in AUD.items():
        n = len(await db.audience(k))
        rows.append([(f"{lab} ({n})", f"ad:bca:{k}")])
    await show(cb, "⟦share⟧ <b>إذاعة رسالة</b>\n\nاختر الفئة المستهدفة:", kb(rows + [BACK]))


@router.callback_query(F.data.startswith("ad:bca:"))
async def cb_bca(cb: CallbackQuery):
    await cb.answer()
    k = cb.data.split(":")[2]
    STATE[cb.from_user.id] = {"mode": "bc", "aud": k}
    await show(cb, f"⟦share⟧ <b>إذاعة إلى: {AUD[k]}</b>\n\nأرسل الآن الرسالة التي تريد إذاعتها (نص، صورة، فيديو، ملف... كما هي).", kb([[("↩️ إلغاء", "ad:bc")]]))


@router.callback_query(F.data == "ad:bcgo")
async def cb_bcgo(cb: CallbackQuery, bot: Bot):
    st = STATE.pop(cb.from_user.id, None)
    if not st or st.get("mode") != "bc_ready":
        return await cb.answer("انتهت المسودة", show_alert=True)
    await cb.answer("جاري الإرسال...")
    ids = await db.audience(st["aud"])
    ok = bad = blocked = 0
    status = await bot.send_message(cb.from_user.id, f"📣 جاري الإرسال... 0/{len(ids)}")
    for i, uid in enumerate(ids, 1):
        try:
            await bot.copy_message(uid, st["chat"], st["msg"])
            ok += 1
        except TelegramForbiddenError:
            blocked += 1
            await db.mark_blocked(uid)
        except TelegramRetryAfter as e:
            await asyncio.sleep(min(e.retry_after, 30))
            try:
                await bot.copy_message(uid, st["chat"], st["msg"])
                ok += 1
            except Exception:  # noqa: BLE001
                bad += 1
        except Exception:  # noqa: BLE001
            bad += 1
        if i % 20 == 0:
            try:
                await status.edit_text(f"📣 جاري الإرسال... {i}/{len(ids)}")
            except TelegramBadRequest:
                pass
        await asyncio.sleep(0.05)
    await db.add_alert("broadcast", f"إذاعة {AUD[st['aud']]}: نجح {ok} • محظور {blocked} • فشل {bad}")
    await status.edit_text(f"✅ <b>انتهت الإذاعة</b>\nالفئة: {AUD[st['aud']]}\nوصلت: {ok}\nحاظرو البوت: {blocked}\nفشلت: {bad}", reply_markup=kb([BACK]))


def _wait_mode(m: Message) -> bool:
    return bool(m.from_user and m.from_user.id in STATE and not (m.text and m.text.startswith("/")))


@router.message(_wait_mode)
async def on_admin_input(msg: Message, bot: Bot):
    st = STATE.get(msg.from_user.id)
    mode = st["mode"]
    if mode == "search":
        if not msg.text:
            return await msg.answer("أرسل نصاً للبحث.")
        STATE.pop(msg.from_user.id, None)
        rows = await db.search_users(msg.text)
        if not rows:
            return await msg.answer("لم أجد نتائج.", reply_markup=kb([[("⟦search⟧ بحث جديد", "ad:search")], BACK]))
        if len(rows) == 1:
            return await show(msg, *await screen_user(rows[0][0]))
        btns = [[(f"⟦user⟧ {r[1][:14]} {uname(r[2])} • {r[0]}", f"ad:u:{r[0]}")] for r in rows]
        return await msg.answer(f"⟦search⟧ نتائج البحث ({len(rows)}):", reply_markup=kb(btns + [BACK]))
    if mode == "asearch":
        STATE.pop(msg.from_user.id, None)
        rows = await db.search_accounts(msg.text or "")
        if not rows:
            return await msg.answer("لم أجد حسابات.", reply_markup=kb([[("⟦search⟧ بحث جديد", "ad:asearch")], BACK]))
        if len(rows) == 1:
            return await show(msg, *await screen_account(rows[0][0]))
        return await msg.answer(f"⟦search⟧ نتائج ({len(rows)}):", reply_markup=kb([[(f"⟦account⟧ {r[1]}", f"ad:a:{r[0]}")] for r in rows] + [BACK]))
    if mode == "dm":
        uid = st["uid"]
        STATE.pop(msg.from_user.id, None)
        try:
            await bot.copy_message(uid, msg.chat.id, msg.message_id)
            await msg.answer("✅ وصلت الرسالة.", reply_markup=kb([[("⟦user⟧ المستخدم", f"ad:u:{uid}")], BACK]))
        except TelegramForbiddenError:
            await db.mark_blocked(uid)
            await msg.answer("⛔ المستخدم حاظر البوت.", reply_markup=kb([BACK]))
        except Exception as e:  # noqa: BLE001
            await msg.answer(f"⚠️ فشل الإرسال: {esc(str(e))[:150]}", reply_markup=kb([BACK]))
        return
    if mode == "bc":
        n = len(await db.audience(st["aud"]))
        STATE[msg.from_user.id] = {"mode": "bc_ready", "aud": st["aud"], "chat": msg.chat.id, "msg": msg.message_id}
        await bot.copy_message(msg.chat.id, msg.chat.id, msg.message_id)  # معاينة
        return await msg.answer(f"👆 هذه معاينة رسالتك.\nستصل إلى <b>{n}</b> مستخدم ({AUD[st['aud']]}).\nتأكيد الإرسال؟",
                                reply_markup=kb([[("✅ إرسال الآن", "ad:bcgo")], [("↩️ إلغاء", "ad:home")]]))


# ---------------------------------------------------------------- الجلسات
@router.callback_query(F.data == "ad:sess")
async def cb_sess(cb: CallbackQuery):
    await cb.answer()
    jobs = CTX.get("JOBS", {})
    lines, btns = [], []
    for uid, j in jobs.items():
        lines.append(f"🎬 <code>{uid}</code> • {len(j['stack'])} تأثير • {'⚙️ تصدير' if j.get('running') else 'تحرير'} • {ago(j['ts'])}")
        btns.append([(f"⟦clear⟧ إنهاء {uid}", f"ad:kill:{uid}")])
    for uid, j in photo.PJOBS.items():
        lines.append(f"🖼 <code>{uid}</code> • {len(j['stack'])} أداة • {ago(j['ts'])}")
        if uid not in jobs:
            btns.append([(f"⟦clear⟧ إنهاء {uid}", f"ad:kill:{uid}")])
    await show(cb, "⟦layers⟧ <b>الجلسات النشطة</b>\n\n" + ("\n".join(lines) or "لا جلسات مفتوحة الآن"), kb(btns[:12] + [[("⟦refresh⟧ تحديث", "ad:sess")], BACK]))


# ---------------------------------------------------------------- الإشعارات
@router.callback_query(F.data == "ad:alerts")
async def cb_alerts(cb: CallbackQuery):
    await cb.answer()
    rows = await db.recent_alerts(10)
    icons = {"new_user": "👤", "fail": "⚠️", "job": "✅", "broadcast": "📣", "info": "ℹ️", "security": "🔒", "account": "🔐"}
    lines = [f"{icons.get(k, '•')} {ago(ts)}: {esc(t)[:110]}" for ts, k, t in rows]

    def tg(k, lab):
        return (f"{'🟢' if db.flag(k) else '⚪'} {lab}", f"ad:tg:{k}:alerts")
    await show(cb, "🔔 <b>الإشعارات</b>\nتصلك فوراً على التليجرام عند التفعيل.\n\n<b>آخر الإشعارات:</b>\n" + ("\n".join(lines) or "لا إشعارات بعد"),
               kb([[tg("alert_new", "مستخدم جديد")], [tg("alert_fail", "فشل عملية")], [tg("alert_jobs", "كل عملية ناجحة")], BACK]))


# ---------------------------------------------------------------- الإعدادات
CHOICES = {"daily_limit": ("الحد اليومي لكل مستخدم", [0, 3, 5, 10, 20, 50], "غير محدود"), "max_minutes": ("أقصى مدة للفيديو (دقيقة)", [10, 30, 60, 120, 240], "")}


@router.callback_query(F.data == "ad:set")
async def cb_set(cb: CallbackQuery):
    await cb.answer()
    await show_settings(cb)


async def show_settings(cb):
    def tg(k, lab):
        return [(f"{'🟢' if db.flag(k) else '⚪'} {lab}", f"ad:tg:{k}:set")]
    dl = int(db.setting("daily_limit") or 0)
    mm = int(float(db.setting("max_minutes") or 120))
    text = ("⟦gear⟧ <b>الإعدادات</b>\n\n"
            "🟢 = مفعّل • ⚪ = متوقف\n"
            f"الحد اليومي لكل مستخدم: <b>{dl or 'غير محدود'}</b>\nأقصى مدة للفيديو: <b>{mm} دقيقة</b> (الفيديو يُعدَّل كاملاً بدون قص)")
    rows = [tg("require_login", "إلزام تسجيل الدخول (حساب لكل مستخدم)"), tg("subscriptions", "نظام الاشتراك (جاهز • متوقف = مجاني)"), tg("maintenance", "وضع الصيانة (يوقف البوت للجميع عدا المسؤول)"), tg("allow_video", "قسم الفيديو"), tg("allow_photo", "قسم الصور"),
            [("⟦user⟧ الحد اليومي", "ad:ch:daily_limit"), ("⏱ أقصى مدة", "ad:ch:max_minutes")],
            [("🔔 الإشعارات", "ad:alerts")], BACK]
    await show(cb, text, kb(rows))


@router.callback_query(F.data.startswith("ad:tg:"))
async def cb_toggle(cb: CallbackQuery):
    _, _, k, back = cb.data.split(":")
    if k not in db.DEFAULTS:
        return await cb.answer()
    await db.set_setting(k, "0" if db.flag(k) else "1")
    await cb.answer("تم التبديل")
    if back == "alerts":
        await cb_alerts(cb)
    else:
        await show_settings(cb)


@router.callback_query(F.data.startswith("ad:ch:"))
async def cb_choice(cb: CallbackQuery):
    await cb.answer()
    k = cb.data.split(":")[2]
    lab, vals, zero = CHOICES[k]
    cur = int(float(db.setting(k) or 0))
    btns = [[(("✔ " if v == cur else "") + (zero if v == 0 and zero else str(v)), f"ad:cs:{k}:{v}") for v in vals[i:i + 3]] for i in range(0, len(vals), 3)]
    await show(cb, f"⟦gear⟧ <b>{lab}</b>\nالحالي: {cur or zero}\nاختر القيمة:", kb(btns + [[("⟦left⟧ الإعدادات", "ad:set")]]))


@router.callback_query(F.data.startswith("ad:cs:"))
async def cb_choice_set(cb: CallbackQuery):
    _, _, k, v = cb.data.split(":")
    if k in CHOICES:
        await db.set_setting(k, v)
    await cb.answer("تم الحفظ")
    await show_settings(cb)


# ---------------------------------------------------------------- النظام
def _meminfo():
    try:
        d = {}
        for ln in Path("/proc/meminfo").read_text().splitlines():
            k, v = ln.split(":")
            d[k] = int(v.split()[0]) * 1024
        return d.get("MemTotal", 0), d.get("MemAvailable", 0)
    except Exception:  # noqa: BLE001
        return 0, 0


def _dirsize(p: Path) -> int:
    t = 0
    for f in p.rglob("*"):
        try:
            if f.is_file():
                t += f.stat().st_size
        except OSError:
            pass
    return t


@router.callback_query(F.data == "ad:sys")
async def cb_sys(cb: CallbackQuery):
    await cb.answer()
    import aiogram
    work = Path(CTX.get("WORK", "work"))
    try:
        load = os.getloadavg()
        la = f"{load[0]:.2f} / {load[1]:.2f} / {load[2]:.2f}"
    except OSError:
        la = "—"
    mt, ma = _meminfo()
    du = shutil.disk_usage(str(work if work.exists() else "."))
    ff = "—"
    try:
        pr = await asyncio.create_subprocess_exec("ffmpeg", "-version", stdout=asyncio.subprocess.PIPE, stderr=asyncio.subprocess.DEVNULL)
        out, _ = await asyncio.wait_for(pr.communicate(), 5)
        ff = out.decode().splitlines()[0].split(" ")[2]
    except Exception:  # noqa: BLE001
        pass
    wsize = await asyncio.to_thread(_dirsize, work) if work.exists() else 0
    text = (
        "⟦grid⟧ <b>حالة النظام</b>\n\n"
        f"⏱ التشغيل: {fmt_dur(time.time() - STARTED)} • PID <code>{os.getpid()}</code>\n"
        f"🐍 Python {platform.python_version()} • aiogram {aiogram.__version__} • FFmpeg {ff}\n"
        f"🖥 المعالج: {os.cpu_count()} نواة • الحِمل (1/5/15د): {la}\n"
        f"🧠 الذاكرة: {(mt - ma) / 1e9:.1f} / {mt / 1e9:.1f} GB\n"
        f"💽 القرص: {(du.total - du.free) / 1e9:.1f} / {du.total / 1e9:.1f} GB (حرّ {du.free / 1e9:.1f})\n"
        f"📁 الملفات المؤقتة: {wsize / 1e6:.0f} MB\n"
        f"⚙️ الجلسات: فيديو {len(CTX.get('JOBS', {}))} • صور {len(photo.PJOBS)}\n"
        f"⚙️ الحد المتوازي: {CTX.get('MAX_PARALLEL', '—')} • المنفذ {CTX.get('PORT', '—')}\n"
        f"🌐 الواجهة المصغّرة: {'مفعّلة' if CTX.get('WEBAPP') else 'غير مفعّلة'}"
    )
    await show(cb, text, kb([[("⟦refresh⟧ تحديث", "ad:sys"), ("⟦trash⟧ تنظيف", "ad:clean")], BACK]))


@router.callback_query(F.data == "ad:clean")
async def cb_clean(cb: CallbackQuery):
    work = Path(CTX.get("WORK", "work"))
    keep = [str(j["dir"]) for j in CTX.get("JOBS", {}).values()] + [str(j["dir"]) for j in photo.PJOBS.values()]
    freed, n = 0, 0
    if work.exists():
        for d in work.iterdir():
            if d.is_dir() and str(d) not in keep:
                freed += _dirsize(d)
                shutil.rmtree(d, ignore_errors=True)
                n += 1
    await cb.answer(f"حُذف {n} مجلد • {freed / 1e6:.0f}MB", show_alert=True)
    await show(cb, *await screen_home())


# ---------------------------------------------------------------- التصدير
@router.callback_query(F.data == "ad:exp")
async def cb_exp(cb: CallbackQuery):
    await cb.answer()
    await show(cb, "⟦download⟧ <b>تصدير البيانات</b>\nملفات CSV تفتح بإكسل (UTF-8).", kb([[("⟦user⟧ المستخدمون", "ad:ex:users"), ("⟦file⟧ العمليات", "ad:ex:jobs")], [("⟦verified⟧ الحسابات (بدون كلمات السر)", "ad:ex:accounts")], BACK]))


@router.callback_query(F.data.startswith("ad:ex:"))
async def cb_export(cb: CallbackQuery, bot: Bot):
    table = cb.data.split(":")[2]
    head, rows = await db.export_rows(table)
    buf = io.StringIO()
    w = csv.writer(buf)
    w.writerow(head)
    for r in rows:
        w.writerow(r)
    data = ("﻿" + buf.getvalue()).encode("utf-8")
    await cb.answer("جاري التصدير")
    await bot.send_document(cb.from_user.id, BufferedInputFile(data, f"{table}_{time.strftime('%Y%m%d_%H%M')}.csv"), caption=f"📄 {len(rows)} صف")


# ---------------------------------------------------------------- الحسابات
ACC_ORDERS = {"last_login": "آخر دخول", "created": "الأحدث إنشاءً", "logins": "الأكثر دخولاً"}


@router.callback_query(F.data.startswith("ad:acc:"))
async def cb_accounts(cb: CallbackQuery):
    await cb.answer()
    _, _, order, page = cb.data.split(":")
    page = int(page)
    at = await db.account_totals()
    rows = await db.list_accounts(page * USERS_PER_PAGE, USERS_PER_PAGE, order)
    lines = [f"{'🟢' if r[6] else '⚪'}{'🚫' if r[5] else ''} <code>{esc(r[1])}</code> • {r[7]} عملية • {ago(r[3])}" for r in rows]
    btns = [[(f"⟦account⟧ {r[1]}", f"ad:a:{r[0]}")] for r in rows]
    nav = []
    if page > 0:
        nav.append(("⟦left⟧ السابق", f"ad:acc:{order}:{page - 1}"))
    nav.append((f"{page + 1}/{max(1, -(-at['total'] // USERS_PER_PAGE))}", "noop"))
    if (page + 1) * USERS_PER_PAGE < at["total"]:
        nav.append(("التالي ⟦right⟧", f"ad:acc:{order}:{page + 1}"))
    sorts = [(("• " if o == order else "") + lab, f"ad:acc:{o}:0") for o, lab in ACC_ORDERS.items()]
    head = (f"⟦verified⟧ <b>الحسابات ({at['total']})</b>\n"
            f"جدد اليوم {at['new_today']} • دخلوا اليوم {at['active_today']} • متصلون الآن {at['online']} • معطّلة {at['disabled']} • مدفوعة {at['paid']}\n\n")
    await show(cb, head + ("\n".join(lines) or "لا حسابات بعد"), kb(btns + [nav, sorts, [("⟦search⟧ بحث عن حساب", "ad:asearch")], BACK]))


async def screen_account(aid: int):
    acc = await db.account_by_id(aid)
    if not acc:
        return "الحساب غير موجود.", kb([BACK])
    (n, ok), recent = await db.account_jobs(aid)
    tgs = await db.tg_of_account(aid)
    rec = "\n".join(f"  {'✅' if r[1] else '⚠️'} {ago(r[0])} • {esc((r[3] or '')[:36])}" for r in recent) or "  —"
    plan = "مجانية" if acc[6] == "free" else f"{esc(acc[6])}" + (f" حتى {dt(acc[7])}" if acc[7] else "")
    text = (f"⟦account⟧ <b>الحساب <code>{esc(acc[1])}</code></b>\n\n"
            f"المعرّف: <code>{acc[0]}</code>\nالحالة: {'🚫 معطّل' if acc[8] else '✅ مفعّل'}\nالخطة: {plan}\n"
            f"أُنشئ: {dt(acc[4])} • آخر دخول: {ago(acc[5])} • مرات الدخول: {acc[9] or 0}\n"
            f"مربوط بتيليجرام: {', '.join(f'<code>{t}</code>' for t in tgs) or 'لا أحد (خرج)'}\n"
            f"الأعمال: {n} (ناجحة {ok})\n\n<b>آخر أعماله:</b>\n{rec}")
    rows = [[("⟦key⟧ إعادة تعيين كلمة السر", f"ad:ar:{aid}"), ("⟦logout⟧ إخراجه", f"ad:ao:{aid}")],
            [("✅ تفعيل" if acc[8] else "🚫 تعطيل", f"ad:ad:{aid}"), ("⟦star⟧ تغيير الخطة", f"ad:ap:{aid}")],
            [InlineKeyboardButton(text="⟦trash⟧ حذف الحساب", callback_data=f"ad:ax:{aid}", style="danger")]]
    for t in tgs[:2]:
        rows.append([(f"⟦user⟧ مستخدم تيليجرام {t}", f"ad:u:{t}")])
    rows += [[("⟦left⟧ الحسابات", "ad:acc:last_login:0")], BACK]
    return text, kb(rows)


@router.callback_query(F.data.startswith("ad:a:"))
async def cb_account(cb: CallbackQuery):
    await cb.answer()
    await show(cb, *await screen_account(int(cb.data.split(":")[2])))


@router.callback_query(F.data.startswith("ad:ar:"))
async def cb_acc_reset(cb: CallbackQuery):
    aid = int(cb.data.split(":")[2])
    pwd = auth.gen_password()
    h, salt = await asyncio.to_thread(auth.hash_pw, pwd)
    await db.set_password(aid, h, salt)
    await db.drop_account_sessions(aid)
    await cb.answer("تم")
    acc = await db.account_by_id(aid)
    await show(cb, f"⟦key⟧ <b>كلمة سر جديدة للحساب <code>{esc(acc[1])}</code></b>\n\n<code>{esc(pwd)}</code>\n\nأُخرج من كل جلساته. أعطه كلمة السر هذه (لن تظهر مرة أخرى).", kb([[("⟦account⟧ الحساب", f"ad:a:{aid}")], BACK]))


@router.callback_query(F.data.startswith("ad:ao:"))
async def cb_acc_out(cb: CallbackQuery):
    aid = int(cb.data.split(":")[2])
    await db.drop_account_sessions(aid)
    await cb.answer("أُخرج من كل أجهزته")
    await show(cb, *await screen_account(aid))


@router.callback_query(F.data.startswith("ad:ad:"))
async def cb_acc_toggle(cb: CallbackQuery):
    aid = int(cb.data.split(":")[2])
    acc = await db.account_by_id(aid)
    await db.set_account_flag(aid, not acc[8])
    if not acc[8]:
        await db.drop_account_sessions(aid)
    await cb.answer("تم")
    await show(cb, *await screen_account(aid))


@router.callback_query(F.data.startswith("ad:ap:"))
async def cb_acc_plan(cb: CallbackQuery):
    parts = cb.data.split(":")
    aid = int(parts[2])
    if len(parts) == 3:
        await cb.answer()
        return await show(cb, "⟦star⟧ <b>اختر الخطة</b> (النظام الآن مجاني للجميع، هذا للتحضير للاشتراك لاحقاً):",
                          kb([[("مجانية", f"ad:ap:{aid}:free:0"), ("Pro شهر", f"ad:ap:{aid}:pro:30")], [("Pro سنة", f"ad:ap:{aid}:pro:365"), ("VIP دائمة", f"ad:ap:{aid}:vip:0")], [("↩️ رجوع", f"ad:a:{aid}")]]))
    plan, days = parts[3], int(parts[4])
    await db.set_plan(aid, plan, int(time.time()) + days * 86400 if days else None)
    await cb.answer("تم تغيير الخطة")
    await show(cb, *await screen_account(aid))


@router.callback_query(F.data.startswith("ad:ax:"))
async def cb_acc_delete(cb: CallbackQuery):
    parts = cb.data.split(":")
    aid = int(parts[2])
    if len(parts) == 3:
        await cb.answer()
        return await show(cb, "⚠️ <b>حذف الحساب نهائياً؟</b>\nتُحذف بيانات تسجيله ويُخرج من أجهزته. لا يمكن التراجع.",
                          kb([[InlineKeyboardButton(text="⟦trash⟧ نعم احذف", callback_data=f"ad:ax:{aid}:y", style="danger"), ("↩️ إلغاء", f"ad:a:{aid}")]]))
    await db.delete_account(aid)
    await cb.answer("حُذف الحساب")
    await show(cb, "✅ تم حذف الحساب.", kb([[("⟦left⟧ الحسابات", "ad:acc:last_login:0")], BACK]))


@router.callback_query(F.data == "ad:asearch")
async def cb_acc_search(cb: CallbackQuery):
    await cb.answer()
    STATE[cb.from_user.id] = {"mode": "asearch"}
    await show(cb, "⟦search⟧ <b>بحث عن حساب</b>\n\nأرسل اسم المستخدم أو جزءاً منه.", kb([[("↩️ إلغاء", "ad:home")]]))


@router.callback_query(F.data == "ad:rep")
async def cb_report(cb: CallbackQuery):
    await cb.answer()
    days = await db.per_day(2)
    (dy, nu_y, ok_y, bad_y), (dt_, nu_t, ok_t, bad_t) = days[0], days[1]
    at = await db.account_totals()

    def arrow(a, b):
        return "⬆️" if b > a else ("⬇️" if b < a else "➖")
    text = (
        "📋 <b>تقرير اليوم مقابل أمس</b>\n\n"
        f"👤 مستخدمون جدد: {nu_t} {arrow(nu_y, nu_t)} (أمس {nu_y})\n"
        f"✅ عمليات ناجحة: {ok_t} {arrow(ok_y, ok_t)} (أمس {ok_y})\n"
        f"⚠️ عمليات فاشلة: {bad_t} {arrow(bad_t, bad_y)} (أمس {bad_y})\n"
        f"⟦verified⟧ حسابات جديدة اليوم: {at['new_today']} • دخلوا اليوم: {at['active_today']} • متصلون الآن: {at['online']}\n"
        f"⟦star⟧ حسابات مدفوعة: {at['paid']} • معطّلة: {at['disabled']}"
    )
    await show(cb, text, kb([[("⟦badge⟧ الإحصائيات التفصيلية", "ad:stats")], BACK]))

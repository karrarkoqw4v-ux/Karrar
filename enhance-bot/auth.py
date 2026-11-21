"""نظام الحسابات: إنشاء حساب / تسجيل دخول (مرة واحدة تُحفظ على المستخدم) + حسابي + تغيير كلمة السر.
كلمات السر تُخزَّن مشفّرة (scrypt + salt) ولا تُحفظ نصاً أبداً. بيانات كل حساب (سجل أعماله) مربوطة بحسابه."""
import asyncio
import hashlib
import hmac
import logging
import os
import random
import re
import secrets
import time

from aiogram import Bot, F, Router
from aiogram.exceptions import TelegramBadRequest
from aiogram.filters import Command
from aiogram.types import CallbackQuery, InlineKeyboardButton, InlineKeyboardMarkup, Message

import db

log = logging.getLogger("auth")
router = Router()
STATE: dict[int, dict] = {}
FAILS: dict[str, tuple[int, float]] = {}  # اسم المستخدم -> (محاولات فاشلة، حتى متى مقفل)
ADMIN_IDS: set[int] = set()
START: dict = {"fn": None}  # main يضع دالة عرض قائمة الأقسام (فيديو/صور)
USER_RE = re.compile(r"^[A-Za-z0-9_]{3,20}$")
RESERVED = {"admin", "administrator", "root", "support", "bot", "system", "moderator", "owner"}
FAST = os.getenv("AUTH_FAST") == "1"
MAX_TRIES = 5
LOCK_SECS = 300


def kb(rows) -> InlineKeyboardMarkup:
    return InlineKeyboardMarkup(inline_keyboard=[[x if isinstance(x, InlineKeyboardButton) else InlineKeyboardButton(text=x[0], callback_data=x[1]) for x in r] for r in rows])


def esc(s) -> str:
    return str(s or "").replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


# ---------------------------------------------------------------- التشفير
def hash_pw(pwd: str, salt: str | None = None) -> tuple[str, str]:
    salt = salt or secrets.token_hex(16)
    h = hashlib.scrypt(pwd.encode(), salt=bytes.fromhex(salt), n=2 ** 14, r=8, p=1, dklen=32).hex()
    return h, salt


def check_pw(pwd: str, h: str, salt: str) -> bool:
    return hmac.compare_digest(hash_pw(pwd, salt)[0], h)


def gen_password() -> str:
    """كلمة سر قوية سهلة النسخ: 3 مجموعات من 4 رموز بلا حروف ملتبسة"""
    al = "abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return "-".join("".join(secrets.choice(al) for _ in range(4)) for _ in range(3))


# ---------------------------------------------------------------- فحص الدخول (تستعمله البوابة)
def is_logged(uid: int) -> bool:
    return db.account_of(uid) is not None


def needs_login(uid: int) -> bool:
    """تسجيل الدخول إجباري للجميع (حتى المسؤول لاستخدام المونتاج)؛ المسؤول يفتح /admin بدونه"""
    return db.flag("require_login") and not is_logged(uid)


ADMIN_STATE: dict = {}  # حالة إدخال المسؤول (تضعها admin.py)


def gate_allows(event) -> bool:
    """غير المسجَّل يُسمح له فقط: /start وأزرار الحساب وكتابة بيانات التسجيل (والمسؤول: لوحة /admin)"""
    uid = event.from_user.id
    if not needs_login(uid):
        return True
    admin = uid in ADMIN_IDS
    if isinstance(event, CallbackQuery):
        d = event.data or ""
        return d.startswith("au:") or (admin and d.startswith("ad:"))
    txt = event.text or ""
    if txt.startswith("/start") or uid in STATE:
        return True
    return admin and (txt.startswith("/admin") or txt in ("ادمن", "الادمن", "لوحة التحكم", "لوحة") or uid in ADMIN_STATE)


# ---------------------------------------------------------------- الشاشات
def welcome_screen():
    text = ("⟦lock⟧ <b>أهلاً بك في بوت المونتاج</b>\n\n"
            "⟦key⟧ سجّل دخولك أو أنشئ حساباً <b>مجانياً</b> ليحفظ البوت بياناتك وسجل أعمالك بحسابك الخاص.\n"
            "⟦verified⟧ تسجّل <b>مرة واحدة فقط</b> ويبقى حسابك محفوظاً على تيليجرامك.")
    return text, kb([[InlineKeyboardButton(text="⟦login⟧ تسجيل دخول", callback_data="au:login", style="primary"),
                      InlineKeyboardButton(text="⟦account⟧ إنشاء حساب", callback_data="au:reg", style="success")]])


async def send_welcome(msg: Message):
    t, k = welcome_screen()
    await msg.answer(t, reply_markup=k)


async def maybe_welcome(msg: Message) -> bool:
    """عند /start: غير المسجَّل يرى شاشة الدخول/الإنشاء"""
    if needs_login(msg.from_user.id):
        STATE.pop(msg.from_user.id, None)
        await send_welcome(msg)
        return True
    return False


async def edit(bot: Bot, st: dict, text: str, markup=None):
    """يعدّل رسالة اللوحة الحالية للمستخدم (وإلا يرسل جديدة)"""
    try:
        await bot.edit_message_text(text, chat_id=st["chat"], message_id=st["msg"], reply_markup=markup)
    except TelegramBadRequest as e:
        if "not modified" in str(e):
            return
        m = await bot.send_message(st["chat"], text, reply_markup=markup)
        st["msg"] = m.message_id


def pbar(p: float, n: int = 10) -> str:
    k = int(p * n)
    return "▰" * k + "▱" * (n - k)


async def animate(bot: Bot, st: dict, title: str, stages: list[str], work, total: float | None = None):
    """يعرض مراحل المعالجة 5–10 ثوانٍ بشريط تقدم، وينفّذ العمل الفعلي بالتوازي"""
    total = total if total is not None else (0.2 if FAST else random.uniform(5, 10))
    task = asyncio.ensure_future(work)
    t0 = time.time()
    last = ""
    while True:
        el = time.time() - t0
        p = min(el / total, 0.99)
        i = min(int(p * len(stages)), len(stages) - 1)
        txt = f"{title}\n\n{stages[i]}\n\n{pbar(p)} {int(p * 100)}%"
        if txt != last:
            last = txt
            await edit(bot, st, txt)
        if el >= total and task.done():
            break
        await asyncio.sleep(0.2 if FAST else 1.0)
    return await task


def start_kb():
    return kb([[InlineKeyboardButton(text="⟦film⟧ ابدأ المونتاج", callback_data="au:start", style="success"), ("⟦account⟧ حسابي", "au:me")]])


# ---------------------------------------------------------------- إنشاء حساب
@router.callback_query(F.data == "au:reg")
async def cb_reg(cb: CallbackQuery):
    await cb.answer()
    if is_logged(cb.from_user.id):
        return await cb_me(cb)
    if not db.flag("allow_register"):
        return await cb.message.edit_text("⏸ إنشاء الحسابات الجديدة متوقف حالياً من الإدارة. إن كان لديك حساب سجّل دخولك.", reply_markup=kb([[("⟦login⟧ تسجيل دخول", "au:login")]]))
    STATE[cb.from_user.id] = {"mode": "reg_user", "chat": cb.message.chat.id, "msg": cb.message.message_id}
    await cb.message.edit_text("⟦account⟧ <b>إنشاء حساب جديد</b>\n\nأرسل <b>اسم المستخدم</b> الذي تريده:\n"
                               "• من 3 إلى 20 حرفاً\n• أحرف إنجليزية أو أرقام أو _ فقط",
                               reply_markup=kb([[("↩️ رجوع", "au:back")]]))


@router.callback_query(F.data == "au:back")
async def cb_back(cb: CallbackQuery):
    await cb.answer()
    STATE.pop(cb.from_user.id, None)
    t, k = welcome_screen()
    await cb.message.edit_text(t, reply_markup=k)


@router.callback_query(F.data == "au:gen")
async def cb_gen(cb: CallbackQuery, bot: Bot):
    st = STATE.get(cb.from_user.id)
    if not st or st["mode"] not in ("reg_pass", "pw_new"):
        return await cb.answer("ابدأ من جديد", show_alert=True)
    await cb.answer("تم توليد كلمة سر")
    st["gen"] = True
    await submit_password(bot, cb.from_user.id, gen_password())


async def submit_password(bot: Bot, uid: int, pwd: str):
    st = STATE.get(uid)
    if not st:
        return
    mode = st["mode"]
    if mode == "pw_new":
        aid = db.account_of(uid)
        h, salt = await asyncio.to_thread(hash_pw, pwd)
        await db.set_password(aid, h, salt)
        STATE.pop(uid, None)
        shown = f"\n⟦key⟧ كلمة السر الجديدة: <code>{esc(pwd)}</code>\n⚠️ احفظها الآن، لن تظهر مرة أخرى." if st.get("gen") else ""
        return await edit(bot, st, "⟦verified⟧ <b>تم تغيير كلمة السر</b>" + shown, kb([[("⟦account⟧ حسابي", "au:me")]]))
    username = st["user"]
    generated = bool(st.get("gen"))

    async def work():
        h, salt = await asyncio.to_thread(hash_pw, pwd)
        return await db.create_account(username, h, salt, uid)
    aid = await animate(bot, st, "⟦account⟧ <b>جاري إنشاء حسابك</b>",
                        ["⟦search⟧ التحقق من توفر الاسم...", "⟦key⟧ تشفير كلمة السر بأمان...", "⟦layers⟧ إنشاء الحساب وحفظ البيانات...", "⟦lock⟧ ربط الحساب بتيليجرامك..."], work())
    STATE.pop(uid, None)
    if not aid:
        return await edit(bot, st, "⚠️ سبقك أحد لهذا الاسم. جرّب اسماً آخر.", kb([[("⟦account⟧ إنشاء حساب", "au:reg")]]))
    await db.bind_session(uid, aid)
    await db.add_alert("account", f"حساب جديد: {username}")
    if db.HOOKS.get("new_account"):
        asyncio.get_running_loop().create_task(db.HOOKS["new_account"](uid, username))
    pw_line = f"\n⟦key⟧ كلمة السر: <code>{esc(pwd)}</code>\n⚠️ احفظها الآن، لن تظهر مرة أخرى." if generated else "\n⟦key⟧ كلمة السر: التي أدخلتها (محفوظة مشفّرة)"
    text = ("⟦verified⟧ <b>تم إنشاء حسابك بنجاح</b>\n\n"
            f"⟦account⟧ اسم المستخدم: <code>{esc(username)}</code> ⟦ok⟧" + pw_line + "\n"
            "⟦dot_green⟧ الحالة: مفعّل • الخطة: مجانية\n\n"
            "⟦lock⟧ حسابك محفوظ على تيليجرامك، لا تحتاج تسجيل الدخول مرة ثانية.")
    await edit(bot, st, text, start_kb())


# ---------------------------------------------------------------- تسجيل الدخول
@router.callback_query(F.data == "au:login")
async def cb_login(cb: CallbackQuery):
    await cb.answer()
    if is_logged(cb.from_user.id):
        return await cb_me(cb)
    STATE[cb.from_user.id] = {"mode": "login_user", "chat": cb.message.chat.id, "msg": cb.message.message_id}
    await cb.message.edit_text("⟦login⟧ <b>تسجيل الدخول</b>\n\nأرسل <b>اسم المستخدم</b>:", reply_markup=kb([[("↩️ رجوع", "au:back")]]))


def _wait_auth(m: Message) -> bool:
    return bool(m.from_user and m.text and m.from_user.id in STATE and not m.text.startswith("/"))


@router.message(_wait_auth)
async def on_auth_text(msg: Message, bot: Bot):
    uid = msg.from_user.id
    st = STATE[uid]
    text = msg.text.strip()
    try:
        await msg.delete()  # لا نترك اسم/كلمة السر ظاهرة بالمحادثة
    except TelegramBadRequest:
        pass
    mode = st["mode"]
    if mode == "reg_user":
        if not USER_RE.match(text):
            return await edit(bot, st, "⚠️ اسم غير صالح.\nمن 3 إلى 20 حرفاً: إنجليزي أو أرقام أو _ فقط.\n\nأرسل اسماً آخر:", kb([[("↩️ رجوع", "au:back")]]))
        if text.lower() in RESERVED or await db.username_taken(text):
            return await edit(bot, st, f"⚠️ الاسم <code>{esc(text)}</code> غير متاح.\n\nأرسل اسماً آخر:", kb([[("↩️ رجوع", "au:back")]]))
        st.update(mode="reg_pass", user=text)
        return await edit(bot, st, f"⟦ok⟧ الاسم <code>{esc(text)}</code> متاح\n\n⟦key⟧ <b>الآن كلمة السر</b>\nأرسلها بنفسك (6 أحرف فأكثر)، أو اضغط «توليد» ليختار لك البوت كلمة قوية.",
                          kb([[InlineKeyboardButton(text="🎲 توليد كلمة سر قوية", callback_data="au:gen", style="primary")], [("↩️ رجوع", "au:back")]]))
    if mode in ("reg_pass", "pw_new"):
        if len(text) < 6 or len(text) > 64:
            return await edit(bot, st, "⚠️ كلمة السر يجب أن تكون من 6 إلى 64 رمزاً.\n\nأرسل كلمة سر أخرى أو اضغط «توليد»:",
                              kb([[InlineKeyboardButton(text="🎲 توليد كلمة سر قوية", callback_data="au:gen", style="primary")], [("↩️ رجوع", "au:back")]]))
        return await submit_password(bot, uid, text)
    if mode == "login_user":
        acc = await db.account_by_name(text) if USER_RE.match(text) else None
        if not acc:
            STATE.pop(uid, None)
            return await edit(bot, st, f"❌ لا يوجد حساب باسم <code>{esc(text)}</code> في قائمة الحسابات.",
                              kb([[("⟦login⟧ إعادة المحاولة", "au:login"), ("⟦account⟧ إنشاء حساب", "au:reg")]]))
        if acc[8]:
            STATE.pop(uid, None)
            return await edit(bot, st, "🚫 هذا الحساب معطّل من الإدارة.", kb([[("↩️ رجوع", "au:back")]]))
        st.update(mode="login_pass", user=acc[1])
        return await edit(bot, st, f"⟦ok⟧ الحساب <code>{esc(acc[1])}</code> موجود\n\n⟦key⟧ أرسل <b>كلمة السر</b>:", kb([[("↩️ رجوع", "au:back")]]))
    if mode == "login_pass":
        uname = st["user"]
        n, until = FAILS.get(uname.lower(), (0, 0))
        if until > time.time():
            STATE.pop(uid, None)
            return await edit(bot, st, f"⏳ تم قفل المحاولات مؤقتاً. حاول بعد {int((until - time.time()) // 60) + 1} دقيقة.", kb([[("↩️ رجوع", "au:back")]]))

        async def work():
            acc = await db.account_by_name(uname)
            if not acc or acc[8]:
                return None
            ok = await asyncio.to_thread(check_pw, text, acc[2], acc[3])
            return acc[0] if ok else False
        res = await animate(bot, st, "⟦login⟧ <b>جاري تسجيل الدخول</b>", ["⟦search⟧ البحث عن حسابك في قائمة البيانات...", "⟦key⟧ التحقق من كلمة السر...", "⟦lock⟧ فتح حسابك..."], work(), total=0.2 if FAST else random.uniform(3, 5))
        if res:
            FAILS.pop(uname.lower(), None)
            STATE.pop(uid, None)
            await db.bind_session(uid, res)
            return await edit(bot, st, f"⟦verified⟧ <b>تم تسجيل الدخول</b>\n\n⟦account⟧ أهلاً <code>{esc(uname)}</code> ⟦ok⟧\n⟦lock⟧ حسابك محفوظ على هذا التليجرام، لن تحتاج الدخول مرة ثانية.", start_kb())
        n += 1
        if n >= MAX_TRIES:
            FAILS[uname.lower()] = (0, time.time() + LOCK_SECS)
            STATE.pop(uid, None)
            if db.HOOKS.get("lockout"):
                asyncio.get_running_loop().create_task(db.HOOKS["lockout"](uid, uname))
            return await edit(bot, st, "⛔ تجاوزت عدد المحاولات. تم قفل الحساب 5 دقائق.", kb([[("↩️ رجوع", "au:back")]]))
        FAILS[uname.lower()] = (n, 0)
        st["mode"] = "login_pass"
        return await edit(bot, st, f"❌ كلمة السر غير صحيحة (تبقى {MAX_TRIES - n} محاولات).\n\n⟦key⟧ أرسل كلمة السر مرة ثانية:", kb([[("↩️ رجوع", "au:back")]]))


# ---------------------------------------------------------------- حسابي
async def account_card(uid: int):
    aid = db.account_of(uid)
    acc = await db.account_by_id(aid) if aid else None
    if not acc:
        return None
    (n, ok), recent = await db.account_jobs(aid)
    plan = "مجانية" if acc[6] == "free" else f"{esc(acc[6])}" + (f" (حتى {time.strftime('%Y-%m-%d', time.localtime(acc[7]))})" if acc[7] else "")
    rec = "\n".join(f"  {'✅' if r[1] else '⚠️'} {esc((r[3] or '')[:34])}" for r in recent) or "  —"
    sub = "مجاني حالياً لجميع المستخدمين" if not db.flag("subscriptions") else "نظام الاشتراك مفعّل"
    text = ("⟦account⟧ <b>حسابي</b>\n\n"
            f"⟦verified⟧ اسم المستخدم: <code>{esc(acc[1])}</code>\n"
            f"⟦dot_green⟧ الحالة: مفعّل\n"
            f"⟦star⟧ الخطة: {plan} ({sub})\n"
            f"📅 أُنشئ: {time.strftime('%Y-%m-%d', time.localtime(acc[4]))} • آخر دخول: {time.strftime('%Y-%m-%d %H:%M', time.localtime(acc[5] or acc[4]))}\n"
            f"⟦badge⟧ أعمالي: {n} (ناجحة {ok})\n\n<b>آخر أعمالي:</b>\n{rec}")
    return text, kb([[("⟦key⟧ تغيير كلمة السر", "au:pw"), ("⟦logout⟧ تسجيل الخروج", "au:out")], [("⟦film⟧ القائمة", "au:start"), ("🗑 حذف حسابي", "au:del")]])


@router.message(Command("account"))
async def cmd_account(msg: Message):
    c = await account_card(msg.from_user.id)
    if not c:
        return await send_welcome(msg)
    await msg.answer(c[0], reply_markup=c[1])


@router.callback_query(F.data == "au:me")
async def cb_me(cb: CallbackQuery):
    await cb.answer()
    c = await account_card(cb.from_user.id)
    if not c:
        t, k = welcome_screen()
        return await cb.message.edit_text(t, reply_markup=k)
    try:
        await cb.message.edit_text(*c[:1], reply_markup=c[1])
    except TelegramBadRequest:
        await cb.message.answer(c[0], reply_markup=c[1])


@router.callback_query(F.data == "au:pw")
async def cb_pw(cb: CallbackQuery):
    if not is_logged(cb.from_user.id):
        return await cb.answer("سجّل الدخول أولاً", show_alert=True)
    await cb.answer()
    STATE[cb.from_user.id] = {"mode": "pw_new", "chat": cb.message.chat.id, "msg": cb.message.message_id}
    await cb.message.edit_text("⟦key⟧ <b>تغيير كلمة السر</b>\n\nأرسل كلمة السر الجديدة (6 أحرف فأكثر) أو اضغط توليد:",
                               reply_markup=kb([[InlineKeyboardButton(text="🎲 توليد كلمة سر قوية", callback_data="au:gen", style="primary")], [("↩️ رجوع", "au:me")]]))


@router.callback_query(F.data == "au:out")
async def cb_out(cb: CallbackQuery):
    await cb.answer()
    await cb.message.edit_text("⟦logout⟧ <b>تسجيل الخروج؟</b>\nحسابك وبياناتك تبقى محفوظة، وتدخل مرة ثانية بالاسم وكلمة السر.",
                               reply_markup=kb([[InlineKeyboardButton(text="⟦logout⟧ نعم، خروج", callback_data="au:outy", style="danger"), ("↩️ إلغاء", "au:me")]]))


@router.callback_query(F.data == "au:outy")
async def cb_outy(cb: CallbackQuery):
    await cb.answer("تم تسجيل الخروج")
    await db.drop_session(cb.from_user.id)
    STATE.pop(cb.from_user.id, None)
    t, k = welcome_screen()
    await cb.message.edit_text(t, reply_markup=k)


@router.callback_query(F.data == "au:start")
async def cb_start(cb: CallbackQuery):
    await cb.answer()
    if START["fn"]:
        await START["fn"](cb.message)


@router.callback_query(F.data == "au:del")
async def cb_del(cb: CallbackQuery):
    await cb.answer()
    await cb.message.edit_text("⚠️ <b>حذف حسابك نهائياً؟</b>\nيُحذف الحساب وكلمة السر وتُفصل بيانات أعمالك عنه. لا يمكن التراجع.",
                               reply_markup=kb([[InlineKeyboardButton(text="🗑 نعم، احذف حسابي", callback_data="au:delyes", style="danger"), ("↩️ إلغاء", "au:me")]]))


@router.callback_query(F.data == "au:delyes")
async def cb_del_yes(cb: CallbackQuery):
    aid = db.account_of(cb.from_user.id)
    if aid:
        await db.delete_account(aid)
        await db.add_alert("account", f"حذف حساب #{aid} بطلب صاحبه")
    await cb.answer("تم حذف حسابك")
    STATE.pop(cb.from_user.id, None)
    t, k = welcome_screen()
    await cb.message.edit_text("✅ تم حذف حسابك.\n\n" + t, reply_markup=k)

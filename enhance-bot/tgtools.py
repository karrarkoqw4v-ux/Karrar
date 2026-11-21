"""قسم «أدوات تيليجرام»: القسم الأول سحب بروكسي (دولة ← سيرفر ← فتح واتصال)"""
import asyncio

from aiogram import F, Router
from aiogram.filters import Command
from aiogram.types import CallbackQuery, InlineKeyboardButton, InlineKeyboardMarkup, Message

import db
import proxies
from flagmap import FLAGS, flag_html

router = Router()
HOOK: dict = {}  # {"home": send_menu}
ADMIN_IDS: set[int] = set()
PAGE = 16


def _kb(rows):
    return InlineKeyboardMarkup(inline_keyboard=rows)


def kb_tools() -> InlineKeyboardMarkup:
    return _kb([[InlineKeyboardButton(text="🌐 سحب بروكسي", callback_data="tt:px", style="success")],
                [InlineKeyboardButton(text="↩️ القائمة الرئيسية", callback_data="tt:home")]])


TOOLS_TXT = "🛠 <b>أدوات تيليجرام</b>\n\nاختر القسم 👇"


async def _show(cb: CallbackQuery, text: str, kb: InlineKeyboardMarkup):
    try:
        await cb.message.edit_text(text, reply_markup=kb)
    except Exception:  # noqa: BLE001
        await cb.message.answer(text, reply_markup=kb)


@router.callback_query(F.data == "tt:menu")
async def cb_menu(cb: CallbackQuery):
    await cb.answer()
    await cb.message.answer(TOOLS_TXT, reply_markup=kb_tools())


@router.callback_query(F.data == "tt:tools")
async def cb_tools(cb: CallbackQuery):
    await cb.answer()
    await _show(cb, TOOLS_TXT, kb_tools())


@router.callback_query(F.data == "tt:home")
async def cb_home(cb: CallbackQuery):
    await cb.answer()
    fn = HOOK.get("home")
    if fn:
        await fn(cb.message)


def _countries_kb(page: int, admin: bool = False) -> tuple[str, InlineKeyboardMarkup]:
    cs = proxies.countries()
    pages = max(1, (len(cs) + PAGE - 1) // PAGE)
    page = max(0, min(page, pages - 1))
    chunk = cs[page * PAGE:(page + 1) * PAGE]
    rows, row = [], []
    for cc, n in chunk:
        row.append(InlineKeyboardButton(text=FLAGS[cc][1] + (f" • {n}" if n else ""), callback_data=f"tt:pc:{cc}", icon_custom_emoji_id=FLAGS[cc][0]))
        if len(row) == 2:
            rows.append(row)
            row = []
    if row:
        rows.append(row)
    nav = []
    if page > 0:
        nav.append(InlineKeyboardButton(text="◀️ السابق", callback_data=f"tt:px:{page - 1}"))
    if page < pages - 1:
        nav.append(InlineKeyboardButton(text="التالي ▶️", callback_data=f"tt:px:{page + 1}"))
    if nav:
        rows.append(nav)
    rows.append([InlineKeyboardButton(text="🔄 تحديث", callback_data="tt:pxr"), InlineKeyboardButton(text="↩️ رجوع", callback_data="tt:tools")])
    st = proxies.STATE
    withs = sum(1 for _, n in cs if n)
    txt = (f"🌐 <b>سحب بروكسي</b>\n\nاختر الدولة 👇\n🗂 {len(cs)} دولة • فيها سيرفرات متصلة الآن: {withs} • صفحة {page + 1}/{pages}\n"
           f"🟢 = قوي (ping ≤ {proxies.max_ping():.0f}ms)")
    if admin and st["total"] and st["alive"] < 0.1 * st["total"]:
        txt += (f"\n\n⚠️ <b>للمسؤول:</b> اتصل الفحص بـ {st['alive']} فقط من {st['total']} سيرفراً، فجهاز تشغيل البوت لا يملك إنترنتاً مباشراً كافياً. "
                "شغّل البوت على استضافة بإنترنت مباشر ليظهر الفحص الحقيقي.")
    return txt, _kb(rows)


async def _countries(cb: CallbackQuery, page: int = 0, force: bool = False):
    admin = cb.from_user.id in ADMIN_IDS
    if force or not proxies.STATE["ts"]:
        try:
            await cb.message.edit_text("⏳ جاري فحص السيرفرات واختيار الأقوى •••")
        except Exception:  # noqa: BLE001
            pass
        if force:
            await proxies.refresh()
        else:
            await proxies.ensure_fresh()
    txt, kb = _countries_kb(page, admin)
    await _show(cb, txt, kb)


@router.callback_query(F.data == "tt:px")
async def cb_px(cb: CallbackQuery):
    await cb.answer()
    await _countries(cb, 0)


@router.callback_query(F.data.startswith("tt:px:"))
async def cb_px_page(cb: CallbackQuery):
    await cb.answer()
    await _countries(cb, int(cb.data.split(":")[2]))


@router.callback_query(F.data == "tt:pxr")
async def cb_px_refresh(cb: CallbackQuery):
    await cb.answer("جاري التحديث…")
    await _countries(cb, 0, force=True)


@router.callback_query(F.data.startswith("tt:pc:"))
async def cb_country(cb: CallbackQuery):
    await cb.answer()
    cc = cb.data.split(":")[2]
    if cc not in FLAGS:
        return
    lst = proxies.servers(cc)
    if not lst:
        await _show(cb, f"{flag_html(cc)} <b>{FLAGS[cc][1]}</b>\n\nلا توجد سيرفرات متصلة لهذه الدولة الآن. جرّب دولة ثانية أو «تحديث».", _kb([[InlineKeyboardButton(text="↩️ الدول", callback_data="tt:px")]]))
        return
    lim = proxies.max_ping()
    rows = [[InlineKeyboardButton(text=f"{'🟢' if e['ms'] <= lim else '🟡'} {e['ms']:.0f}ms • {e['ip']}", callback_data=f"tt:ps:{e['key']}", style="primary" if e["ms"] <= lim else None)] for e in lst]
    rows.append([InlineKeyboardButton(text="↩️ الدول", callback_data="tt:px")])
    await _show(cb, f"{flag_html(cc)} <b>{FLAGS[cc][1]}</b>\n\n🟢 قوي (≤ {lim:.0f}ms) • 🟡 متصل أبطأ — الأسرع أولاً\nاختر السيرفر المناسب 👇", _kb(rows))


@router.callback_query(F.data.startswith("tt:ps:"))
async def cb_server(cb: CallbackQuery):
    await cb.answer()
    e = proxies.STATE["all"].get(cb.data.split(":")[2])
    if not e:
        await _show(cb, "⚠️ هذا السيرفر لم يعد متاحاً، اختر غيره.", _kb([[InlineKeyboardButton(text="↩️ الدول", callback_data="tt:px")]]))
        return
    try:
        await cb.message.edit_text("⏳ جاري تجهيز السيرفر •••")
    except Exception:  # noqa: BLE001
        pass
    await asyncio.sleep(1.2)
    cc = e["cc"]
    txt = (f"✅ <b>السيرفر جاهز</b>\n\n"
           f"{flag_html(cc)} الدولة: <b>{FLAGS[cc][1]}</b>\n"
           f"🖥 السيرفر: <code>{e['host']}</code>\n"
           f"🔌 المنفذ: <code>{e['port']}</code>\n"
           f"🌐 IP: <code>{e['ip']}</code>\n"
           f"⚡ ping: <b>{e['ms']:.0f}ms</b>\n\n"
           f"اضغط «فتح واتصال» ثم «تفعيل» داخل تيليجرام 👇")
    await _show(cb, txt, _kb([[InlineKeyboardButton(text="🔌 فتح واتصال", url=proxies.link(e), style="success")],
                              [InlineKeyboardButton(text="↩️ سيرفرات الدولة", callback_data=f"tt:pc:{cc}"), InlineKeyboardButton(text="🌐 الدول", callback_data="tt:px")]]))


@router.message(Command("px"))
async def cmd_px(msg: Message):
    """للمسؤول: /px = حالة الفحص • /px 30 = تغيير حد البنق"""
    if msg.from_user.id not in ADMIN_IDS:
        return
    parts = (msg.text or "").split()
    if len(parts) > 1:
        try:
            v = float(parts[1])
        except ValueError:
            return await msg.answer("اكتب رقماً: /px 20")
        await db.set_setting("px_max_ping", str(v))
    s = proxies.STATE
    await msg.answer(f"🌐 <b>حالة البروكسيات</b>\nمتصل: {s['alive']}/{s['total']}\nفي دول الأعلام: {len(s['all'])} • دول: {len(s['by_cc'])}\n"
                     f"حد البنق: ≤ {proxies.max_ping():.0f}ms • دول متاحة الآن: {len(proxies.countries())}\n"
                     f"آخر فحص: {'—' if not s['ts'] else int((__import__('time').time() - s['ts']) // 60)} د\n\nتغيير الحد: /px 30")

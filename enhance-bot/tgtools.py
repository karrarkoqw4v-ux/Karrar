"""قسم «أدوات تيليجرام»: القسم الأول سحب بروكسي (دولة ← سيرفر ← فتح واتصال).
الأزرار والنصوص هنا بلا إيموجيات مميزة؛ المميز فقط أعلام الدول."""
import asyncio
import time

from aiogram import F, Router
from aiogram.filters import Command
from aiogram.types import CallbackQuery, InlineKeyboardButton, InlineKeyboardMarkup, Message

import proxies
from flagmap import FLAGS, flag_html

router = Router()
HOOK: dict = {}  # {"home": send_menu}
ADMIN_IDS: set[int] = set()
PAGE = 16


def _kb(rows):
    return InlineKeyboardMarkup(inline_keyboard=rows)


def B(text: str, data: str | None = None, style: str | None = None, url: str | None = None, icon: str | None = None) -> InlineKeyboardButton:
    return InlineKeyboardButton(text=text, callback_data=data, url=url, style=style, icon_custom_emoji_id=icon)


TOOLS_TXT = "<b>أدوات تيليجرام</b>\n\nاختر القسم:"


def kb_tools() -> InlineKeyboardMarkup:
    return _kb([[B("سحب بروكسي", "tt:px", "success")], [B("القائمة الرئيسية", "tt:home")]])


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
    rows, row = [], []
    for cc, n in cs[page * PAGE:(page + 1) * PAGE]:
        row.append(B(FLAGS[cc][1] + (f" • {n}" if n else ""), f"tt:pc:{cc}", icon=FLAGS[cc][0]))
        if len(row) == 2:
            rows.append(row)
            row = []
    if row:
        rows.append(row)
    nav = []
    if page > 0:
        nav.append(B("السابق", f"tt:px:{page - 1}"))
    if page < pages - 1:
        nav.append(B("التالي", f"tt:px:{page + 1}"))
    if nav:
        rows.append(nav)
    rows.append([B("تحديث", "tt:pxr"), B("رجوع", "tt:tools")])
    st = proxies.STATE
    withs = sum(1 for _, n in cs if n)
    txt = (f"<b>سحب بروكسي</b>\n\nاختر الدولة:\n{len(cs)} دولة • فيها سيرفرات مستقرة الآن: {withs} • صفحة {page + 1}/{pages}\n"
           "الرقم بجانب العلم = عدد السيرفرات المتصلة المستقرة")
    if admin and st["total"] and st["alive"] < 0.1 * st["total"]:
        txt += (f"\n\n<b>للمسؤول:</b> اتصل الفحص بـ {st['alive']} فقط من {st['total']} سيرفراً، فجهاز تشغيل البوت لا يملك إنترنتاً مباشراً كافياً. "
                "شغّل البوت على استضافة بإنترنت مباشر ليظهر الفحص الحقيقي.")
    return txt, _kb(rows)


async def _countries(cb: CallbackQuery, page: int = 0, force: bool = False):
    admin = cb.from_user.id in ADMIN_IDS
    if force or not proxies.STATE["ts"]:
        try:
            await cb.message.edit_text("جاري فحص السيرفرات واختيار المستقر منها •••")
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
    head = f"{flag_html(cc)} <b>{FLAGS[cc][1]}</b>"
    if not lst:
        await _show(cb, f"{head}\n\nلا توجد سيرفرات مستقرة لهذه الدولة الآن. جرّب دولة ثانية أو «تحديث».", _kb([[B("الدول", "tt:px")]]))
        return
    rows = [[B(f"{i}) {e['ms']:.0f}ms • {e['ip']}", f"tt:ps:{e['key']}", "primary" if i == 1 else None)] for i, e in enumerate(lst, 1)]
    rows.append([B("الدول", "tt:px")])
    await _show(cb, f"{head}\n\nسيرفرات متصلة ومستقرة — الأسرع أولاً ({len(lst)})\nاختر السيرفر المناسب:", _kb(rows))


@router.callback_query(F.data.startswith("tt:ps:"))
async def cb_server(cb: CallbackQuery):
    await cb.answer()
    e = proxies.STATE["all"].get(cb.data.split(":")[2])
    if not e:
        await _show(cb, "هذا السيرفر لم يعد متاحاً، اختر غيره.", _kb([[B("الدول", "tt:px")]]))
        return
    try:
        await cb.message.edit_text("جاري تجهيز السيرفر •••")
    except Exception:  # noqa: BLE001
        pass
    await asyncio.sleep(1.2)
    cc = e["cc"]
    txt = (f"<b>السيرفر جاهز</b>\n\n"
           f"الدولة: {flag_html(cc)} <b>{FLAGS[cc][1]}</b>\n"
           f"السيرفر: <code>{e['host']}</code>\n"
           f"المنفذ: <code>{e['port']}</code>\n"
           f"IP: <code>{e['ip']}</code>\n"
           f"ping: <b>{e['ms']:.0f}ms</b>\n\n"
           f"اضغط «فتح واتصال» ثم «تفعيل» داخل تيليجرام")
    await _show(cb, txt, _kb([[B("فتح واتصال", url=proxies.link(e), style="success")], [B("سيرفرات الدولة", f"tt:pc:{cc}"), B("الدول", "tt:px")]]))


@router.message(Command("px"))
async def cmd_px(msg: Message):
    """للمسؤول: حالة فحص البروكسيات"""
    if msg.from_user.id not in ADMIN_IDS:
        return
    s = proxies.STATE
    await msg.answer(f"<b>حالة البروكسيات</b>\nمتصل: {s['alive']}/{s['total']}\nفي دول الأعلام: {len(s['all'])} • دول: {len(s['by_cc'])}\n"
                     f"آخر فحص: {'—' if not s['ts'] else int((time.time() - s['ts']) // 60)} د")

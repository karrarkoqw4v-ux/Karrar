"""اختبار منطق «أدوات تيليجرام» (بدون شبكة): قوائم الدول والسيرفرات وبطاقة الاتصال"""
import asyncio
import sys
import time
from pathlib import Path
from types import SimpleNamespace

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import emo  # noqa: E402
import proxies  # noqa: E402
import tgtools  # noqa: E402
from flagmap import FLAGS  # noqa: E402


class Msg:
    def __init__(self):
        self.out = []

    async def edit_text(self, text, reply_markup=None):
        self.out.append((text, reply_markup))

    async def answer(self, text, reply_markup=None):
        self.out.append((text, reply_markup))


class CB:
    def __init__(self, data):
        self.data = data
        self.message = Msg()
        self.from_user = SimpleNamespace(id=1)

    async def answer(self, *a, **k):
        pass


def mk(cc, host, ms, n=0):
    return {"key": proxies.key_of(host, 443 + n, "s"), "host": host, "port": 443 + n, "secret": "s", "ip": f"1.2.3.{n + 4}", "cc": cc, "ms": ms}


async def main():
    ents = [mk("IR", "a.ir", 5, 1), mk("IR", "b.ir", 12, 2), mk("IR", "slow.ir", 90, 3), mk("EG", "c.eg", 18, 4), mk("JP", "d.jp", 40, 5)]
    by = {}
    for e in ents:
        by.setdefault(e["cc"], []).append(e)
    proxies.STATE.update({"ts": time.time(), "by_cc": by, "all": {e["key"]: e for e in ents}, "alive": 5, "total": 9})
    # كل الدول تظهر (68)، والتي فيها سيرفرات أولاً
    cs = proxies.countries()
    assert len(cs) == len(FLAGS) == 68, len(cs)
    assert [c for c, n in cs[:3]] == ["IR", "JP", "EG"] or cs[0][0] == "IR", cs[:3]
    assert dict(cs)["IR"] == 3 and dict(cs)["SA"] == 0
    cb = CB("tt:px")
    await tgtools.cb_px(cb)
    txt, kb = cb.message.out[-1]
    assert "68 دولة" in txt and "مستقرة الآن: 3" in txt
    btns = [b for r in kb.inline_keyboard for b in r]
    assert any(b.icon_custom_emoji_id == FLAGS["IR"][0] for b in btns)
    emo.markup(kb)  # لا يكسر الأزرار
    # سيرفرات دولة
    cb = CB("tt:pc:IR")
    await tgtools.cb_country(cb)
    txt, kb = cb.message.out[-1]
    assert "tg-emoji" in txt and FLAGS["IR"][0] in txt
    srv = [b for r in kb.inline_keyboard for b in r if b.callback_data.startswith("tt:ps:")]
    assert len(srv) == 3 and "5ms" in srv[0].text and "90ms" in srv[2].text  # حتى البنق العالي يظهر
    # بطاقة السيرفر
    real_sleep = asyncio.sleep
    asyncio.sleep = lambda *_a, **_k: real_sleep(0)
    cb = CB(srv[0].callback_data)
    await tgtools.cb_server(cb)
    asyncio.sleep = real_sleep
    assert cb.message.out[0][0].startswith("جاري تجهيز السيرفر")
    txt, kb = cb.message.out[-1]
    assert "a.ir" in txt and "444" in txt and "ping" in txt
    url = [b for r in kb.inline_keyboard for b in r if b.url][0].url
    assert url.startswith("https://t.me/proxy?server=a.ir&port=444&secret=s"), url
    # دولة بلا سيرفرات
    cb = CB("tt:pc:SA")
    await tgtools.cb_country(cb)
    assert "لا توجد سيرفرات مستقرة" in cb.message.out[-1][0]
    # سيرفر منتهٍ
    cb = CB("tt:ps:deadbeef")
    await tgtools.cb_server(cb)
    assert "لم يعد متاحاً" in cb.message.out[-1][0]
    # القائمة الرئيسية فيها الزر
    import main as M
    assert any(b.callback_data == "tt:menu" for r in M.kb_mode().inline_keyboard for b in r)
    print("OK tgtools")


asyncio.run(main())

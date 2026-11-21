"""لوحة التنفيذ المباشر: المسؤول فقط يحفظ طلباً في الطابور (بقاعدة مؤقتة)"""
import asyncio
import os
import sys
import tempfile
from pathlib import Path
from types import SimpleNamespace

os.environ["DB_PATH"] = str(Path(tempfile.mkdtemp()) / "t.db")
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import devq  # noqa: E402


class Msg:
    def __init__(self, uid, text=""):
        self.out, self.text, self.from_user = [], text, SimpleNamespace(id=uid)

    async def answer(self, t, reply_markup=None):
        self.out.append(t)

    async def edit_text(self, t, reply_markup=None):
        self.out.append(t)


class CB:
    def __init__(self, uid, data):
        self.data, self.from_user, self.message, self.alerts = data, SimpleNamespace(id=uid), Msg(uid), []

    async def answer(self, t="", show_alert=False):
        if show_alert:
            self.alerts.append(t)


async def main():
    devq.ADMIN_IDS = {7}
    # غير المسؤول مرفوض
    cb = CB(99, "ad:dev")
    await devq.cb_dev(cb)
    assert cb.alerts == ["للمسؤول فقط"]
    cb = CB(99, "ad:devnew")
    await devq.cb_devnew(cb)
    assert 99 not in devq.ASK
    # المسؤول: طلب جديد ← كتابة ← حفظ
    cb = CB(7, "ad:devnew")
    await devq.cb_devnew(cb)
    assert 7 in devq.ASK
    m = Msg(7, "أضف أداة قص مربع للصور")
    await devq.on_text(m)
    assert 7 not in devq.ASK and "#1" in m.out[0], m.out
    # /do
    m2 = Msg(7)
    await devq.cmd_do(m2, SimpleNamespace(args="غيّر نص زر البداية"))
    assert "#2" in m2.out[0]
    m3 = Msg(99)
    await devq.cmd_do(m3, SimpleNamespace(args="اختراق"))
    assert not m3.out and len(devq.recent(10)) == 2
    cb = CB(7, "ad:dev")
    await devq.cb_dev(cb)
    assert "⏳ بالانتظار #2" in cb.message.out[0]
    print("OK devq")


asyncio.run(main())

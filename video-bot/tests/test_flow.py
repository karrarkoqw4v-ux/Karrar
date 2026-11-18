"""اختبار تدفق البوت كاملاً بدون الاتصال بتيليجرام (جلسة وهمية)"""
import faulthandler; faulthandler.dump_traceback_later(30, exit=True)
import asyncio
import datetime
import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))
import os

os.environ.setdefault("BOT_TOKEN", "1:test")
os.environ["DB_PATH"] = tempfile.mktemp(suffix=".db")
os.environ["TMP_DIR"] = tempfile.mkdtemp()

from aiogram import Bot, Dispatcher  # noqa: E402
from aiogram.client.session.base import BaseSession  # noqa: E402
from aiogram.methods import TelegramMethod  # noqa: E402
from aiogram.types import Chat, ChatMemberMember, File, Message, Update, User  # noqa: E402

import bot as botmod  # noqa: E402
import db  # noqa: E402
import menu  # noqa: E402
from gate import SUB_TEXT, missing_channels, sub_kb  # noqa: E402

CALLS: list[tuple[str, dict]] = []
FILES: dict[str, bytes] = {}
N = [100]


class Fake(BaseSession):
    async def close(self): ...

    async def stream_content(self, url, headers=None, timeout=30, chunk_size=65536, raise_for_status=True):
        yield FILES[url.rsplit("/", 1)[-1]]

    async def make_request(self, bot, method: TelegramMethod, timeout=None):
        import re as _re
        name = _re.sub(r'(?<!^)(?=[A-Z])', '_', method.__api_method__).lower()
        data = method.model_dump(exclude_none=True)
        CALLS.append((name, data))
        N[0] += 1
        if name.startswith("send_") or name.startswith("edit_message"):
            return Message(message_id=N[0], date=datetime.datetime.now(), chat=Chat(id=data.get("chat_id", 1), type="private"), text="x").as_(bot)
        if name == "get_file":
            fp = data["file_id"]
            return File(file_id=data["file_id"], file_unique_id="u", file_path=fp, file_size=len(FILES.get(data["file_id"], b"")))
        if name == "get_chat_member":
            return ChatMemberMember(status="member", user=User(id=data["user_id"], is_bot=False, first_name="x"))
        if name == "get_chat":
            from aiogram.exceptions import TelegramBadRequest
            raise TelegramBadRequest(method=method, message="chat not found")
        if name == "get_me":
            return User(id=1, is_bot=True, first_name="B", username="b")
        return True


async def main():
    await db.init()
    bot = Bot("1:test", session=Fake())
    dp = Dispatcher()
    botmod.menu.setup(db, gate_fn=missing_channels, sub_kb=sub_kb, sub_text=SUB_TEXT, sem=asyncio.Semaphore(4))
    dp.include_router(menu.router)
    dp.include_router(botmod.router)
    uid = 777
    user = {"id": uid, "is_bot": False, "first_name": "T"}
    chat = {"id": uid, "type": "private"}
    uidn = [1000]

    async def say(text=None, **extra):
        uidn[0] += 1
        m = {"message_id": uidn[0], "date": 1700000000, "chat": chat, "from": user}
        if text is not None:
            m["text"] = text
            if text.startswith("/"):
                m["entities"] = [{"type": "bot_command", "offset": 0, "length": len(text.split()[0])}]
        m.update(extra)
        await dp.feed_update(bot, Update.model_validate({"update_id": uidn[0], "message": m}, context={"bot": bot}))

    async def click(data):
        uidn[0] += 1
        await dp.feed_update(bot, Update.model_validate({"update_id": uidn[0], "callback_query": {"id": str(uidn[0]), "from": user, "chat_instance": "c", "data": data, "message": {"message_id": 5, "date": 1700000000, "chat": chat, "text": "m"}}}, context={"bot": bot}))

    def last(names):
        return [c for c in CALLS if c[0] in names][-1]

    await say("/start")
    assert "اهلا بك في بوت ادوات" in last(["send_message"])[1]["text"]
    await click("m:main")
    assert "أداة جاهزة" in last(["edit_message_text", "send_message"])[1]["text"]
    await click("c:calc")
    await click("t:calc")
    await say("(2+3)*4")
    assert "20" in last(["send_message"])[1]["text"], CALLS[-3:]
    # أداة بخيارات
    await click("t:case")
    await click("o:case:0")
    await say("hello")
    assert "HELLO" in last(["send_message"])[1]["text"]
    # صورة
    FILES["PH"] = Path(__file__).parent.joinpath("p.jpg").read_bytes() if Path(__file__).parent.joinpath("p.jpg").exists() else None
    import io
    from PIL import Image
    b = io.BytesIO()
    Image.new("RGB", (400, 300), "red").save(b, "JPEG")
    FILES["PH"] = b.getvalue()
    await click("t:img_filter")
    await click("o:img_filter:0")
    await say(photo=[{"file_id": "PH", "file_unique_id": "u", "width": 400, "height": 300, "file_size": len(FILES["PH"])}])
    assert last(["send_document"])[0] == "send_document"
    # أداة بخطوتين (علامة مائية)
    await click("t:img_watermark")
    await say(photo=[{"file_id": "PH", "file_unique_id": "u", "width": 400, "height": 300, "file_size": 10}])
    assert "اكتب نص العلامة" in last(["send_message"])[1]["text"]
    n_doc = len([c for c in CALLS if c[0] == "send_document"])
    await say("@wm")
    assert len([c for c in CALLS if c[0] == "send_document"]) == n_doc + 1
    # أداة متعددة الملفات
    await click("t:photos2pdf")
    await say(photo=[{"file_id": "PH", "file_unique_id": "u", "width": 400, "height": 300, "file_size": 10}])
    await say(photo=[{"file_id": "PH", "file_unique_id": "u", "width": 400, "height": 300, "file_size": 10}])
    n_doc = len([c for c in CALLS if c[0] == "send_document"])
    await click("done")
    assert len([c for c in CALLS if c[0] == "send_document"]) == n_doc + 1
    # آيدي مستخدم
    await say("/cancel")
    await say("123456789")
    assert "الآيدي" in last(["send_message"])[1]["text"]
    # بحث
    await click("m:search")
    await say("qr")
    assert "نتيجة" in last(["send_message"])[1]["text"]
    # فوري بدون مدخلات
    await click("t:uuid")
    assert "<code>" in last(["send_message"])[1]["text"]
    await click("t:hijri")
    await click("o:hijri:2")
    assert "هجري" in last(["send_message"])[1]["text"]
    print("FLOW OK, calls:", len(CALLS))
    await db._db.close()


asyncio.run(main())

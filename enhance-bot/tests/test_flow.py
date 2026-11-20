import asyncio, datetime, os, re, sys, tempfile
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent.parent))
os.environ["BOT_TOKEN"] = "1:test"
os.environ["WORK_DIR"] = tempfile.mkdtemp()
from aiogram import Bot, Dispatcher
from aiogram.client.session.base import BaseSession
from aiogram.types import Chat, File, Message, Update, User
import bot as B

CALLS = []
FILES = {"V": Path(sys.argv[1]).read_bytes()}

class Fake(BaseSession):
    async def close(self): ...
    async def stream_content(self, url, headers=None, timeout=30, chunk_size=65536, raise_for_status=True):
        yield FILES[url.rsplit("/", 1)[-1]]
    async def make_request(self, bot, method, timeout=None):
        name = re.sub(r'(?<!^)(?=[A-Z])', '_', method.__api_method__).lower()
        data = method.model_dump(exclude_none=True)
        CALLS.append((name, data))
        if name.startswith("send_") or name.startswith("edit_message"):
            return Message(message_id=len(CALLS) + 50, date=datetime.datetime.now(), chat=Chat(id=data.get("chat_id", 1), type="private"), text="x").as_(bot)
        if name == "get_file":
            return File(file_id=data["file_id"], file_unique_id="u", file_path=data["file_id"], file_size=len(FILES[data["file_id"]]))
        return True

async def main():
    bot = Bot("1:test", session=Fake())
    dp = Dispatcher(); dp.include_router(B.router)
    user = {"id": 7, "is_bot": False, "first_name": "T"}; chat = {"id": 7, "type": "private"}
    n = [10]
    async def feed(upd):
        n[0] += 1; upd["update_id"] = n[0]
        await dp.feed_update(bot, Update.model_validate(upd, context={"bot": bot}))
    await feed({"message": {"message_id": 1, "date": 1, "chat": chat, "from": user, "text": "/start", "entities": [{"type": "bot_command", "offset": 0, "length": 6}]}})
    assert CALLS[-1][1]["text"].startswith("مرحبا بك"), CALLS[-1]
    await feed({"message": {"message_id": 2, "date": 1, "chat": chat, "from": user, "video": {"file_id": "V", "file_unique_id": "u", "width": 540, "height": 960, "duration": 8, "file_size": len(FILES["V"])}}})
    sent = CALLS[-1]
    assert sent[1]["text"] == "اختار الدقه المناسبه 🪄"
    btns = [b["text"] for r in sent[1]["reply_markup"]["inline_keyboard"] for b in r]
    print("BUTTONS", btns)
    cbdata = sent[1]["reply_markup"]["inline_keyboard"][0][0]["callback_data"]
    await feed({"callback_query": {"id": "1", "from": user, "chat_instance": "c", "data": cbdata, "message": {"message_id": 99, "date": 1, "chat": chat, "text": "m"}}})
    edits = [c[1]["text"] for c in CALLS if c[0] == "edit_message_text"]
    print("EDITS", len(edits)); print(edits[0]); print("---"); print(edits[len(edits)//2]); print("---")
    vid = [c for c in CALLS if c[0] == "send_video"][-1][1]
    print("CAPTION:", vid["caption"])
    print("FLOW OK")
asyncio.run(main())

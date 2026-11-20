import asyncio, datetime, os, re, sys, tempfile
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent.parent))
os.environ["BOT_TOKEN"] = "1:test"; os.environ["WORK_DIR"] = tempfile.mkdtemp()
from aiogram import Bot, Dispatcher
from aiogram.client.session.base import BaseSession
from aiogram.types import Chat, File, Message, Update
import bot as B

CALLS = []; FILES = {"V": Path(sys.argv[1]).read_bytes(), "A": Path(sys.argv[2]).read_bytes()}

class Fake(BaseSession):
    async def close(self): ...
    async def stream_content(self, url, headers=None, timeout=30, chunk_size=65536, raise_for_status=True):
        yield FILES[url.rsplit("/", 1)[-1]]
    async def make_request(self, bot, method, timeout=None):
        name = re.sub(r'(?<!^)(?=[A-Z])', '_', method.__api_method__).lower()
        data = method.model_dump(exclude_none=True); CALLS.append((name, data))
        if name.startswith("send_") or name.startswith("edit_message"):
            return Message(message_id=len(CALLS) + 50, date=datetime.datetime.now(), chat=Chat(id=data.get("chat_id", 1), type="private"), text="x").as_(bot)
        if name == "get_file":
            return File(file_id=data["file_id"], file_unique_id="u", file_path=data["file_id"], file_size=len(FILES[data["file_id"]]))
        return True

async def main():
    bot = Bot("1:test", session=Fake()); dp = Dispatcher(); dp.include_router(B.router)
    user = {"id": 7, "is_bot": False, "first_name": "T"}; chat = {"id": 7, "type": "private"}; n = [10]
    async def feed(upd):
        n[0] += 1; upd["update_id"] = n[0]
        await dp.feed_update(bot, Update.model_validate(upd, context={"bot": bot}))
    async def say(text=None, **extra):
        m = {"message_id": n[0] + 1, "date": 1, "chat": chat, "from": user, **extra}
        if text is not None:
            m["text"] = text
            if text.startswith("/"): m["entities"] = [{"type": "bot_command", "offset": 0, "length": len(text.split()[0])}]
        await feed({"message": m})
    async def click(data):
        await feed({"callback_query": {"id": str(n[0]), "from": user, "chat_instance": "c", "data": data, "message": {"message_id": 5, "date": 1, "chat": chat, "text": "m"}}})
    def last(*names): return [c for c in CALLS if c[0] in names][-1]
    await say("/start"); assert CALLS[-1][1]["text"].startswith("مرحبا بك")
    await say(video={"file_id": "V", "file_unique_id": "u", "width": 360, "height": 640, "duration": 5, "file_size": len(FILES["V"])})
    t = last("send_message")[1]["text"]; assert "استوديو المونتاج" in t, t
    await click("c:hdr:0"); assert "HDR والتحسين" in last("edit_message_text", "send_message")[1]["text"]
    await click("e:hdr_strong")
    pv = last("send_photo"); assert "هل يعجبك" in pv[1]["caption"]; print("preview photo sent (before/after)")
    await click("ok"); assert "أُضيف" in last("edit_message_caption", "edit_message_text", "send_message")[1].get("caption", last("edit_message_text", "send_message")[1].get("text", ""))
    await click("e:glitch"); assert [c for c in CALLS if c[0] == "send_animation"], "motion preview"; print("motion preview sent")
    await click("ok")
    await click("e:text")  # خطوات: نص ثم مكان
    assert "اكتب النص" in last("send_message")[1]["text"]
    await say("مرحبا بك")
    assert "مكان النص" in last("send_message")[1]["text"]
    await click("s:2"); assert "هل يعجبك" in last("send_photo")[1]["caption"]; await click("ok")
    await click("e:music"); await say(audio={"file_id": "A", "file_unique_id": "u", "duration": 3, "file_size": len(FILES["A"])})
    await click("s:1"); await click("ok")
    await click("e:trim"); await say("0:01 0:04"); await click("ok")
    await click("lst"); print("STACK:", last("answer_callback_query")[1].get("text", "").replace("\n", " | "))
    await click("go")
    edits = [c[1]["text"] for c in CALLS if c[0] == "edit_message_text" and "مدة الابداع" in c[1].get("text", "")]
    print("timer edits:", len(edits)); print(edits[-1] if edits else "")
    vid = last("send_video"); print("CAPTION:", vid[1]["caption"].replace("\n", " "))
    assert "تريد تعديلاً آخر" in last("send_message")[1]["text"]
    print("FLOW OK")
asyncio.run(main())

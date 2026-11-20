import asyncio, datetime, os, re, sys, tempfile
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent.parent))
os.environ["BOT_TOKEN"] = "1:test"; os.environ["WORK_DIR"] = tempfile.mkdtemp(); os.environ["DB_PATH"] = tempfile.mktemp(suffix=".db"); os.environ["ADMIN_IDS"] = "7"
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
    ses = Fake(); import emo; ses.middleware(emo.PremiumEmoji()); bot = Bot("1:test", session=ses); dp = Dispatcher(); dp.include_router(B.router)
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
        await feed({"callback_query": {"id": str(n[0]), "from": user, "chat_instance": "c", "data": data, "message": {"message_id": 5, "date": 1, "chat": chat, "video": {"file_id": "x", "file_unique_id": "u", "width": 1, "height": 1, "duration": 1}, "reply_markup": {"inline_keyboard": [[{"text": "a", "callback_data": "noop"}]]}}}})
    def last(*names): return [c for c in CALLS if c[0] in names][-1]
    def cnt(name): return len([c for c in CALLS if c[0] == name])
    await say("/start"); assert "مرحبا بك" in CALLS[-1][1]["text"] and "tg-emoji" in CALLS[-1][1]["text"]
    await say(video={"file_id": "V", "file_unique_id": "u", "width": 360, "height": 640, "duration": 5, "file_size": len(FILES["V"])})
    ed = last("send_video"); assert "استوديو المونتاج" in ed[1]["caption"]
    kb = [b["text"] for r in ed[1]["reply_markup"]["inline_keyboard"] for b in r]; print("HOME BUTTONS:", len(kb), kb[:4]); assert "جاري تجهيز" in [c[1].get("text","") for c in CALLS if c[0] in ("send_message","edit_message_text")][1]
    await click("c:cv:0"); assert "edit_message_reply_markup" in [c[0] for c in CALLS][-1:], CALLS[-1][0]
    await click("e:cv_hdr_max"); assert cnt("edit_message_media") == 1; print("live edit #1 (cv_hdr_max) OK")
    await click("e:teal_orange"); assert cnt("edit_message_media") == 2
    await click("e:snow"); assert cnt("edit_message_media") == 3
    await click("u"); assert cnt("edit_message_media") == 4; print("undo OK")
    await click("tpl:k"); assert "تيك توك" in last("send_photo")[1]["caption"]; await click("tpl:l"); ph = last("send_photo"); assert "قوالب" in ph[1]["caption"]
    nb = [b["text"] for r in ph[1]["reply_markup"]["inline_keyboard"] for b in r]; print("TEMPLATE BUTTONS:", len(nb)); await click("tpl:e"); assert "الإيديت" in last("send_photo")[1]["caption"]; await click("t:e:2"); print("edit template OK")
    await click("t:l:7"); assert cnt("edit_message_media") >= 5; print("template applied live OK")
    await click("e:text"); assert "اكتب النص" in last("send_message")[1]["text"]
    await say("مرحبا بك"); await click("s:2"); assert cnt("edit_message_media") >= 6; print("text step OK")
    # قوالب المصممين (150): عائلات ← صفحة صور كبيرة ← تطبيق
    n0 = cnt("edit_message_media"); await click("dz"); await click("dzf:cin:0")
    ph = last("send_photo"); assert "سينمائي" in ph[1]["caption"]; nb = [b["text"] for r in ph[1]["reply_markup"]["inline_keyboard"] for b in r]; print("DZ PAGE BUTTONS:", nb)
    await click("dzt:cin:2"); assert cnt("edit_message_media") == n0 + 1; print("designer template applied OK")
    # لوحة المختارة: حذف عنصر محدد ثم استرجاعه
    await click("stk"); kb = last("edit_message_reply_markup")[1]["reply_markup"]["inline_keyboard"]; print("STACK PANEL:", [b["text"] for r in kb for b in r][:4])
    n1 = cnt("edit_message_media"); await click("del:1"); assert cnt("edit_message_media") == n1 + 1; print("delete specific OK")
    await click("redo"); assert cnt("edit_message_media") == n1 + 2; print("redo OK")
    await click("go")
    vid = last("send_video"); print("CAPTION:", vid[1]["caption"].replace("\n", " "))
    # مونتاج جارٍ + /start أو فيديو جديد => زران: إلغاء / تكملة
    await say("/start"); q = last("send_message")[1]; qb = [b["callback_data"] for r in q["reply_markup"]["inline_keyboard"] for b in r]; assert qb == ["cx_cancel", "cx_cont"], qb; print("CONFLICT /start OK", [b["text"] for r in q["reply_markup"]["inline_keyboard"] for b in r])
    nv = cnt("send_video"); await click("cx_cont"); assert cnt("send_video") == nv + 1; print("continue OK")
    await say(video={"file_id": "V", "file_unique_id": "u", "width": 360, "height": 640, "duration": 5, "file_size": len(FILES["V"])})
    qb = [b["callback_data"] for r in last("send_message")[1]["reply_markup"]["inline_keyboard"] for b in r]; assert qb == ["cx_cancel", "cx_cont"]
    nv = cnt("send_video"); await click("cx_cancel"); assert cnt("send_video") == nv + 1 and not B.JOBS.get(7, {}).get("stack"); print("cancel + new video OK")
    await say("/start"); assert "مرحبا بك" in last("send_message")[1]["text"]; print("no conflict on fresh job OK")
    await say("/stats"); print(last("send_message")[1]["text"].replace("\n", " | ")[:200])
    import emo as _e
    leak = [(n, d) for n, d in CALLS if _e._EMOJI.search(_e._TAG.sub("", str(d.get("text", "")) + str(d.get("caption", "")) + str((d.get("media") or {}).get("caption", ""))))]
    assert not leak, leak[:2]
    btns = [b for n, d in CALLS for r in ((d.get("reply_markup") or {}).get("inline_keyboard") or []) for b in r]
    assert btns and not [b for b in btns if _e._EMOJI.search(b["text"])], [b for b in btns if _e._EMOJI.search(b["text"])][:3]
    print("premium icons on", sum(1 for b in btns if b.get("icon_custom_emoji_id")), "of", len(btns), "buttons")
    print("FLOW OK")
asyncio.run(main())

import asyncio, datetime, os, re, sys, tempfile
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent.parent))
os.environ["BOT_TOKEN"] = "1:test"; os.environ["WORK_DIR"] = tempfile.mkdtemp(); os.environ["DB_PATH"] = tempfile.mktemp(suffix=".db"); os.environ["ADMIN_IDS"] = "7"
from aiogram import Bot, Dispatcher
from aiogram.client.session.base import BaseSession
from aiogram.types import Chat, File, Message, Update
import bot as B

CALLS = []; FILES = {"V": Path(sys.argv[1]).read_bytes(), "A": Path(sys.argv[2]).read_bytes(), "P": Path(sys.argv[3]).read_bytes()}

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
    ses = Fake(); import emo; ses.middleware(emo.PremiumEmoji()); bot = Bot("1:test", session=ses); dp = Dispatcher(); B.auth.ADMIN_IDS = {7}; dp.include_router(B.auth.router); B.admin.install(bot, {7}, {"JOBS": B.JOBS, "WORK": B.WORK, "drop_job": B.drop_job}); dp.message.outer_middleware(B.admin.Gate()); dp.callback_query.outer_middleware(B.admin.Gate()); dp.include_router(B.admin.router); dp.include_router(B.photo.router); dp.include_router(B.router); B.photo.HOOK["ask"] = B.ask_conflict; B.photo.HOOK["quota"] = B.admin.quota_msg; B.photo.HOOK["feature"] = B.admin.feature_msg
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
    _a = await B.db.create_account("admintester", "x" * 64, "00" * 16, 7); await B.db.bind_session(7, _a)
    await say("/start"); assert "مرحبا بك" in CALLS[-1][1]["text"] and "tg-emoji" in CALLS[-1][1]["text"]
    # ---- لوحة تحكم المسؤول
    await say("/admin"); assert "لوحة تحكم المسؤول" in last("send_message")[1]["text"]; hb = [b["callback_data"] for r in last("send_message")[1]["reply_markup"]["inline_keyboard"] for b in r]; print("ADMIN HOME buttons:", len(hb))
    for cbk in ["ad:stats", "ad:users:last:0", "ad:users:jobs:0", "ad:u:7", "ad:top", "ad:logs", "ad:sess", "ad:alerts", "ad:set", "ad:ch:daily_limit", "ad:cs:daily_limit:5", "ad:sys", "ad:exp", "ad:rep", "ad:idle", "ad:bans", "ad:bc", "ad:clean", "ad:home"]:
        n0 = len(CALLS); await click(cbk); assert len(CALLS) > n0, cbk
    await click("ad:ex:users"); assert last("send_document")[0] == "send_document"
    await click("ad:tg:maintenance:set"); assert B.db.flag("maintenance"); await click("ad:tg:maintenance:set"); assert not B.db.flag("maintenance")
    await click("ad:cs:daily_limit:0")
    await B.db.touch_user(99, "تجربة", "tester"); await click("ad:ban:99"); assert B.db.banned(99); await click("ad:unban:99"); assert not B.db.banned(99)
    await click("ad:search"); await say("tester"); assert "ad:u:99" in str(last("send_message")[1]) or "تفاصيل المستخدم" in last("send_message")[1]["text"]
    await click("ad:bc"); await click("ad:bca:all"); await say("رسالة إذاعة تجريبية"); assert "ad:bcgo" in str(last("send_message")[1]); await click("ad:bcgo"); assert cnt("copy_message") >= 2 and "انتهت الإذاعة" in last("send_message", "edit_message_text")[1]["text"]; print("ADMIN PANEL OK")
    B.admin.STATE.clear()
    # ---- وضع الصور
    await say("/start"); q = last("send_message")[1]; qb = [b["callback_data"] for r in q["reply_markup"]["inline_keyboard"] for b in r]; assert qb == ["mode:video", "mode:photo", "au:me", "mode:pro", "mode:link"], qb; print("MODE CHOOSER OK")
    await click("mode:photo"); assert "جلسة صور" in last("send_message")[1]["text"]
    await click("mode:pro"); assert [b["callback_data"] for r in last("send_message")[1]["reply_markup"]["inline_keyboard"] for b in r] == ["pro:video", "pro:photo"]
    await click("pro:photo"); await say(photo=[{"file_id": "P", "file_unique_id": "p", "width": 10, "height": 10, "file_size": len(FILES["P"])}])
    ed = last("send_photo"); assert "المصممين المحترفين" in ed[1]["caption"]; assert "pg:p:0" in str(ed[1]["reply_markup"])
    await click("pg:p:0"); assert "باقات المصممين" in last("send_photo")[1]["caption"]; await click("pa:p:0"); assert "طُبّق القالب" in last("edit_message_media")[1]["media"]["caption"]; await click("pc:pro:0"); await click("pe:pro_dodge_burn"); print("PRO PHOTO OK")
    B.photo.drop(7); await click("mode:pro"); await click("pro:video"); await say(video={"file_id": "V", "file_unique_id": "u", "width": 360, "height": 640, "duration": 5, "file_size": len(FILES["V"])})
    ed = last("send_video"); assert "المصممين المحترفين" in ed[1]["caption"] and "tpl:p" in str(ed[1]["reply_markup"]); await click("tpl:p"); await click("t:p:1"); await click("c:pro:0"); await click("c:ed:0"); await click("cp:ed:0"); assert "معاينة التأثيرات" in last("send_photo")[1]["caption"]; await click("ce:flash_beat"); assert "flash_beat" in str(B.JOBS[7]["stack"]); print("video effects preview gallery OK"); print("PRO VIDEO OK")
    # ---- مونتاج من الرابط (تحميل مُحاكى)
    async def fake_dl(url, dest, proxy=None):
        dest.mkdir(parents=True, exist_ok=True); p = dest / "dl.mp4"; p.write_bytes(FILES["V"])
        return {"path": str(p), "title": "فيديو تجريبي", "author": "tester", "duration": 8, "platform": "tiktok" if "tiktok" in url else "instagram"}
    B.linkdl.download = fake_dl
    B.drop_job(7)
    await click("mode:link"); assert "تيك توك" in last("send_message")[1]["text"]
    await say("مرحبا هذا مو رابط"); assert "فقط" in last("send_message")[1]["text"]
    await say("https://vm.tiktok.com/ZSabc123/"); card = last("send_message", "edit_message_text")[1]; cb_ = [b["callback_data"] for r in card["reply_markup"]["inline_keyboard"] for b in r]; assert {"lk:edit", "lk:pro", "lk:dl"} <= set(cb_), cb_
    await click("lk:dl"); assert "بدون علامة مائية" in last("send_video")[1]["caption"]; print("LINK download OK")
    await click("lk:mp3"); assert last("send_audio")[0] == "send_audio"; print("LINK mp3 OK")
    await say("شوف هذا https://www.instagram.com/reel/Cabc/?igsh=1"); assert "lk:pro" in str(last("send_message", "edit_message_text")[1])
    await click("lk:pro"); ed = last("send_video"); assert "المصممين المحترفين" in ed[1]["caption"]; print("LINK edit (pro) OK")
    B.photo.drop(7); B.drop_job(7); PROC = list(CALLS); CALLS.clear()
    await say(photo=[{"file_id": "P", "file_unique_id": "p", "width": 10, "height": 10, "file_size": len(FILES["P"])}])
    ed = last("send_photo"); assert "استوديو الصور" in ed[1]["caption"]; pk = [b["text"] for r in ed[1]["reply_markup"]["inline_keyboard"] for b in r]; print("PHOTO HOME:", len(pk), pk[:3])
    await click("pc:bg:0"); await click("pe:bg_blur"); assert cnt("edit_message_media") == 1; print("photo live edit OK")
    await click("pe:text"); assert "اكتب النص" in last("send_message")[1]["text"]
    await say("مرحبا"); await click("ps:2"); assert cnt("edit_message_media") == 2; print("photo text step OK")
    await click("pe:bg_remove"); assert cnt("edit_message_media") == 3 and "شفافة" in last("edit_message_media")[1]["media"]["caption"]; print("bg_remove alpha OK")
    n0 = cnt("edit_message_media"); await click("pg:l:0"); assert "قوالب جاهزة" in last("send_photo")[1]["caption"]; await click("pa:l:1"); assert cnt("edit_message_media") == n0 + 1 and "طُبّق القالب" in last("edit_message_media")[1]["media"]["caption"]; print("photo template OK")
    await click("pg:c:0"); assert "كاب كات" in last("send_photo")[1]["caption"]; await click("pgn:c:0"); await click("pa:c:6"); assert "tag" in str(B.photo.PJOBS[7]["stack"]); print("photo capcut OK")
    await click("pc:bg:0"); await click("pp:bg:0"); assert "معاينة الأدوات" in last("send_photo")[1]["caption"]; await click("pce:bg_blur"); print("photo tool preview gallery OK")
    await click("pu"); await click("pr"); await click("pk"); await click("pd:0"); assert cnt("edit_message_media") >= 7; print("photo undo/redo/delete OK")
    await click("pv"); await click("pvs:style:pulse"); await click("pvs:dur:8"); await say(audio={"file_id": "A", "file_unique_id": "a", "duration": 5, "file_size": len(FILES["A"])}); assert last("send_video")[1]["duration"] == 8; print("photo->video with music OK")
    await click("pg"); assert last("send_document")[1]["caption"]; print("photo export OK")
    await say("/start"); assert "لديك جلسة صور" in last("send_message")[1]["text"]; await click("cx_cancel"); assert not B.photo.PJOBS; print("photo conflict cancel OK")
    PC = list(CALLS); CALLS.clear()
    await say(video={"file_id": "V", "file_unique_id": "u", "width": 360, "height": 640, "duration": 5, "file_size": len(FILES["V"])})
    ed = last("send_video"); assert "استوديو المونتاج" in ed[1]["caption"]
    kb = [b["text"] for r in ed[1]["reply_markup"]["inline_keyboard"] for b in r]; print("HOME BUTTONS:", len(kb), kb[:4]); assert any("جاري تجهيز" in c[1].get("text","") for c in CALLS if c[0] in ("send_message","edit_message_text"))
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
    leak = [(n, d) for n, d in PROC + PC + CALLS if _e._EMOJI.search(_e._TAG.sub("", str(d.get("text", "")) + str(d.get("caption", "")) + str((d.get("media") or {}).get("caption", ""))))]
    assert not leak, leak[:2]
    btns = [b for n, d in PROC + PC + CALLS for r in ((d.get("reply_markup") or {}).get("inline_keyboard") or []) for b in r]
    assert btns and not [b for b in btns if _e._EMOJI.search(b["text"])], [b for b in btns if _e._EMOJI.search(b["text"])][:3]
    print("premium icons on", sum(1 for b in btns if b.get("icon_custom_emoji_id")), "of", len(btns), "buttons")
    print("FLOW OK")
asyncio.run(main())

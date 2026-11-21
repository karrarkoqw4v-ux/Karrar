import asyncio, datetime, os, re, sys, tempfile
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent.parent))
os.environ.update(BOT_TOKEN="1:test", WORK_DIR=tempfile.mkdtemp(), DB_PATH=tempfile.mktemp(suffix=".db"), ADMIN_IDS="7", AUTH_FAST="1")
from aiogram import Bot, Dispatcher
from aiogram.client.session.base import BaseSession
from aiogram.types import Chat, Message, Update
import bot as B, auth, db, emo

CALLS = []

class Fake(BaseSession):
    async def close(self): ...
    async def stream_content(self, *a, **k):
        yield b""
    async def make_request(self, bot, method, timeout=None):
        name = re.sub(r'(?<!^)(?=[A-Z])', '_', method.__api_method__).lower()
        data = method.model_dump(exclude_none=True); CALLS.append((name, data))
        if name.startswith("send_") or name.startswith("edit_message"):
            return Message(message_id=len(CALLS) + 50, date=datetime.datetime.now(), chat=Chat(id=data.get("chat_id", 1), type="private"), text="x").as_(bot)
        return True

def last_text():
    for n, d in reversed(CALLS):
        if "text" in d and n in ("send_message", "edit_message_text"):
            return d
    return {}

async def main():
    ses = Fake(); ses.middleware(emo.PremiumEmoji()); bot = Bot("1:test", session=ses); dp = Dispatcher()
    B.admin.install(bot, {7}, {"JOBS": B.JOBS, "WORK": B.WORK, "drop_job": B.drop_job}); auth.ADMIN_IDS = {7}
    dp.message.outer_middleware(B.admin.Gate()); dp.callback_query.outer_middleware(B.admin.Gate())
    for r in (auth.router, B.admin.router, B.photo.router, B.router): dp.include_router(r)
    n = [100]
    async def feed(upd):
        n[0] += 1; upd["update_id"] = n[0]; await dp.feed_update(bot, Update.model_validate(upd, context={"bot": bot}))
    def user(uid): return {"id": uid, "is_bot": False, "first_name": "T"}
    async def say(uid, text):
        m = {"message_id": n[0] + 1, "date": 1, "chat": {"id": uid, "type": "private"}, "from": user(uid), "text": text}
        if text.startswith("/"): m["entities"] = [{"type": "bot_command", "offset": 0, "length": len(text.split()[0])}]
        await feed({"message": m})
    async def click(uid, data):
        await feed({"callback_query": {"id": str(n[0]), "from": user(uid), "chat_instance": "c", "data": data, "message": {"message_id": 5, "date": 1, "chat": {"id": uid, "type": "private"}, "text": "x"}}})
    def btns(d): return [b["callback_data"] for r in d["reply_markup"]["inline_keyboard"] for b in r]

    # المسؤول بلا تسجيل: /admin يعمل، لكن المونتاج يطلب الدخول
    await say(7, "/admin"); assert "لوحة تحكم المسؤول" in last_text()["text"]
    await say(7, "/start"); assert btns(last_text()) == ["au:login", "au:reg"]; print("admin also must login for editing OK")
    await say(7, "/menu"); assert btns(last_text()) == ["au:login", "au:reg"]
    U = 55
    await say(U, "/start"); assert btns(last_text()) == ["au:login", "au:reg"]; print("welcome 2 buttons OK")
    await say(U, "hello"); assert btns(last_text()) == ["au:login", "au:reg"]; print("gate blocks unauthenticated OK")
    await click(U, "au:reg"); assert "اسم المستخدم" in last_text()["text"]
    await say(U, "a!"); assert "غير صالح" in last_text()["text"]
    await say(U, "admin"); assert "غير متاح" in last_text()["text"]
    await say(U, "Ali_99"); assert "متاح" in last_text()["text"] and "au:gen" in btns(last_text())
    await say(U, "123"); assert "من 6" in last_text()["text"]
    await click(U, "au:gen"); t = last_text()["text"]; assert "تم إنشاء حسابك" in t and "<code>Ali_99</code>" in t, t
    pwd = re.findall(r"كلمة السر: <code>(.*?)</code>", t)[0].split(">")[-1] if "tg-emoji" in t else re.findall(r"كلمة السر: <code>(.*?)</code>", t)[0]
    assert re.fullmatch(r"[A-Za-z0-9]{4}-[A-Za-z0-9]{4}-[A-Za-z0-9]{4}", pwd), pwd
    assert db.account_of(U) is not None; print("register+generated password OK", pwd)
    row = await db.account_by_name("ali_99"); assert row and pwd not in str(row) and len(row[2]) == 64; print("password stored hashed OK")
    await say(U, "/start"); assert "مونتاج استوديو" in str(last_text()["reply_markup"]); print("logged-in start -> menu OK")
    await say(99 if False else U, "/account"); assert "حسابي" in last_text()["text"]
    await click(U, "au:out"); await click(U, "au:outy"); assert db.account_of(U) is None; print("logout OK")
    await click(U, "au:login"); await say(U, "nobody"); assert "لا يوجد حساب" in last_text()["text"]
    await click(U, "au:login"); await say(U, "ali_99"); assert "موجود" in last_text()["text"]
    await say(U, "wrong-pass"); assert "غير صحيحة" in last_text()["text"] and db.account_of(U) is None
    await say(U, pwd); assert "تم تسجيل الدخول" in last_text()["text"] and db.account_of(U); print("login wrong/right OK")
    # حساب ثانٍ بنفس الاسم مرفوض
    V = 56
    await click(V, "au:reg"); await say(V, "ALI_99"); assert "غير متاح" in last_text()["text"]; print("duplicate username rejected OK")
    await say(V, "Bob_1"); await say(V, "mypassword"); await asyncio.sleep(0.3)
    assert db.account_of(V) and "<code>mypassword</code>" not in last_text()["text"]; print("manual password OK")
    # قفل بعد محاولات فاشلة
    await click(V, "au:outy")
    await click(V, "au:login"); await say(V, "Bob_1")
    for _ in range(5): await say(V, "bad-pass-x")
    assert "قفل" in last_text()["text"]; print("lockout OK")
    # تغيير كلمة السر
    await click(U, "au:pw"); await say(U, "newpass123"); assert "تم تغيير" in last_text()["text"]
    assert (await db.account_by_name("ali_99"))[2] != row[2]; print("change password OK")
    # لوحة الأدمن: الحسابات
    await click(7, "ad:acc:last_login:0"); assert "الحسابات" in last_text()["text"]
    aid = (await db.account_by_name("ali_99"))[0]
    await click(7, f"ad:a:{aid}"); assert "Ali_99" in last_text()["text"]
    await click(7, f"ad:ar:{aid}"); assert "كلمة سر جديدة" in last_text()["text"] and db.account_of(U) is None
    await click(7, f"ad:ad:{aid}"); assert (await db.account_by_id(aid))[8] == 1
    await click(7, f"ad:ap:{aid}:pro:30"); assert (await db.account_by_id(aid))[6] == "pro"
    await click(7, f"ad:ax:{aid}:y"); assert await db.account_by_id(aid) is None; print("ADMIN ACCOUNTS OK")
    X = 58
    await click(X, "au:reg"); await say(X, "Temp_1"); await click(X, "au:gen"); assert db.account_of(X)
    await click(X, "au:del"); await click(X, "au:delyes"); assert db.account_of(X) is None and await db.account_by_name("Temp_1") is None; print("self delete OK")
    await db.set_setting("allow_register", "0"); W = 57
    await click(W, "au:reg"); assert "متوقف" in last_text()["text"]; await db.set_setting("allow_register", "1"); print("registration toggle OK")
    print("AUTH OK")
asyncio.run(main())

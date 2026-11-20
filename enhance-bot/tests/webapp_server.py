import asyncio, hashlib, hmac, json, sys, time, urllib.parse
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent.parent))
from aiohttp import web
import webapp
TOKEN = "9999:TESTTOKEN"
webapp.BOT_TOKEN = TOKEN
webapp.WTOKENS["wt123"] = 42
JOB = {"proxy": "/tmp/claude-0/eb/c.mp4", "pmeta": {"w": 360, "h": 640, "dur": 5.0}}
webapp.get_job = lambda uid: JOB if uid == 42 else None
RESULT = Path("/tmp/claude-0/eb/webapp_result.json")
async def on_text(uid, p): RESULT.write_text(json.dumps({"uid": uid, "params": p}, ensure_ascii=False))
webapp.on_text = on_text
def init_data():
    user = json.dumps({"id": 42, "first_name": "T"})
    pairs = {"auth_date": str(int(time.time())), "user": user, "query_id": "AAA"}
    data = "\n".join(f"{k}={v}" for k, v in sorted(pairs.items()))
    secret = hmac.new(b"WebAppData", TOKEN.encode(), hashlib.sha256).digest()
    pairs["hash"] = hmac.new(secret, data.encode(), hashlib.sha256).hexdigest()
    return urllib.parse.urlencode(pairs)
Path("/tmp/claude-0/eb/initdata.txt").write_text(init_data())
app = web.Application(); webapp.add_routes(app)
web.run_app(app, host="127.0.0.1", port=18765, print=None)

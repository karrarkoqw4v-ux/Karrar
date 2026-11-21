import asyncio
import os
import sqlite3
import threading
import time
from pathlib import Path

PATH = os.getenv("DB_PATH", str(Path(__file__).parent / "stats.db"))
_lock = threading.Lock()
_c: sqlite3.Connection | None = None


def _conn():
    global _c
    if _c is None:
        _c = sqlite3.connect(PATH, check_same_thread=False)
        _c.executescript(
            "CREATE TABLE IF NOT EXISTS users(id INTEGER PRIMARY KEY,name TEXT,username TEXT,first INTEGER,last INTEGER);"
            "CREATE TABLE IF NOT EXISTS jobs(id INTEGER PRIMARY KEY AUTOINCREMENT,uid INTEGER,ts INTEGER,ok INTEGER,secs REAL,effects TEXT);"
            "CREATE TABLE IF NOT EXISTS bans(uid INTEGER PRIMARY KEY,reason TEXT,ts INTEGER);"
            "CREATE TABLE IF NOT EXISTS settings(k TEXT PRIMARY KEY,v TEXT);"
            "CREATE TABLE IF NOT EXISTS alerts(id INTEGER PRIMARY KEY AUTOINCREMENT,ts INTEGER,kind TEXT,text TEXT);")
        try:
            _c.execute("ALTER TABLE users ADD COLUMN blocked INTEGER DEFAULT 0")
        except sqlite3.OperationalError:
            pass
        _c.commit()
    return _c


def _q(sql, args=(), fetch=False):
    with _lock:
        c = _conn()
        cur = c.execute(sql, args)
        c.commit()
        return cur.fetchall() if fetch else None


HOOKS: dict = {}  # admin.py يضع هنا دوال التنبيه: new_user / job_failed
_BANNED: set[int] | None = None
SETTINGS: dict[str, str] = {}
DEFAULTS = {"maintenance": "0", "allow_video": "1", "allow_photo": "1", "alert_new": "1", "alert_fail": "1", "alert_jobs": "0", "daily_limit": "0", "max_minutes": "120"}


def load_cache():
    """يحمّل الحظر والإعدادات في الذاكرة (قراءة سريعة بدون انتظار)"""
    global _BANNED
    _BANNED = {r[0] for r in _q("SELECT uid FROM bans", (), True)}
    SETTINGS.clear()
    SETTINGS.update(DEFAULTS)
    SETTINGS.update({k: v for k, v in _q("SELECT k,v FROM settings", (), True)})


def banned(uid: int) -> bool:
    if _BANNED is None:
        load_cache()
    return uid in _BANNED


def setting(k: str) -> str:
    if _BANNED is None:
        load_cache()
    return SETTINGS.get(k, DEFAULTS.get(k, ""))


def flag(k: str) -> bool:
    return setting(k) == "1"


async def set_setting(k: str, v: str):
    await asyncio.to_thread(_q, "INSERT INTO settings(k,v) VALUES(?,?) ON CONFLICT(k) DO UPDATE SET v=excluded.v", (k, str(v)))
    load_cache()


async def ban(uid: int, reason: str = ""):
    await asyncio.to_thread(_q, "INSERT OR REPLACE INTO bans(uid,reason,ts) VALUES(?,?,?)", (uid, reason, int(time.time())))
    load_cache()


async def unban(uid: int):
    await asyncio.to_thread(_q, "DELETE FROM bans WHERE uid=?", (uid,))
    load_cache()


async def touch_user(uid: int, name: str, username: str | None):
    now = int(time.time())
    existed = (await asyncio.to_thread(_q, "SELECT 1 FROM users WHERE id=?", (uid,), True))
    if not existed and HOOKS.get("new_user"):
        try:
            asyncio.get_running_loop().create_task(HOOKS["new_user"](uid, name, username))
        except Exception:  # noqa: BLE001
            pass
    await asyncio.to_thread(_q, "INSERT INTO users(id,name,username,first,last) VALUES(?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET name=excluded.name,username=excluded.username,last=excluded.last", (uid, name, username, now, now))


async def log_job(uid: int, ok: bool, secs: float, effects: list[str]):
    await asyncio.to_thread(_q, "INSERT INTO jobs(uid,ts,ok,secs,effects) VALUES(?,?,?,?,?)", (uid, int(time.time()), int(ok), secs, ",".join(effects)))
    h = HOOKS.get("job_done")
    if h:
        try:
            asyncio.get_running_loop().create_task(h(uid, ok, secs, effects))
        except Exception:  # noqa: BLE001
            pass


async def users() -> list[int]:
    return [r[0] for r in await asyncio.to_thread(_q, "SELECT id FROM users", (), True)]


async def stats() -> dict:
    day = int(time.time()) - 86400
    g = lambda sql, a=(): asyncio.to_thread(_q, sql, a, True)  # noqa: E731
    out = {
        "users": (await g("SELECT COUNT(*) FROM users"))[0][0],
        "new_today": (await g("SELECT COUNT(*) FROM users WHERE first>?", (day,)))[0][0],
        "active_today": (await g("SELECT COUNT(*) FROM users WHERE last>?", (day,)))[0][0],
        "jobs": (await g("SELECT COUNT(*) FROM jobs WHERE ok=1"))[0][0],
        "jobs_today": (await g("SELECT COUNT(*) FROM jobs WHERE ok=1 AND ts>?", (day,)))[0][0],
        "failed": (await g("SELECT COUNT(*) FROM jobs WHERE ok=0"))[0][0],
        "avg": (await g("SELECT AVG(secs) FROM jobs WHERE ok=1"))[0][0] or 0,
    }
    cnt: dict[str, int] = {}
    for (e,) in await g("SELECT effects FROM jobs WHERE ok=1 ORDER BY id DESC LIMIT 500"):
        for x in (e or "").split(","):
            if x:
                cnt[x] = cnt.get(x, 0) + 1
    out["top"] = sorted(cnt.items(), key=lambda kv: -kv[1])[:8]
    return out


# ---------------- استعلامات لوحة التحكم ----------------
def _g(sql, a=()):
    return asyncio.to_thread(_q, sql, a, True)


def is_photo(effects: str) -> bool:
    return (effects or "").startswith("photo")


async def mark_blocked(uid: int, blocked: bool = True):
    await asyncio.to_thread(_q, "UPDATE users SET blocked=? WHERE id=?", (int(blocked), uid))


async def audience(kind: str = "all") -> list[int]:
    now = int(time.time())
    sql = {"all": "SELECT id FROM users WHERE COALESCE(blocked,0)=0",
           "today": f"SELECT id FROM users WHERE COALESCE(blocked,0)=0 AND last>{now - 86400}",
           "week": f"SELECT id FROM users WHERE COALESCE(blocked,0)=0 AND last>{now - 7 * 86400}",
           "new": f"SELECT id FROM users WHERE COALESCE(blocked,0)=0 AND first>{now - 86400}",
           "workers": "SELECT DISTINCT uid FROM jobs WHERE ok=1"}[kind]
    return [r[0] for r in await _g(sql)]


async def count_users() -> int:
    return (await _g("SELECT COUNT(*) FROM users"))[0][0]


async def list_users(sort: str = "last", offset: int = 0, limit: int = 8):
    order = {"last": "u.last DESC", "new": "u.first DESC", "jobs": "n DESC, u.last DESC"}[sort]
    return await _g(f"SELECT u.id,u.name,u.username,u.first,u.last,COALESCE((SELECT COUNT(*) FROM jobs j WHERE j.uid=u.id),0) AS n FROM users u ORDER BY {order} LIMIT ? OFFSET ?", (limit, offset))


async def search_users(q: str, limit: int = 10):
    q = q.strip().lstrip("@")
    if q.isdigit():
        r = await _g("SELECT id,name,username,first,last FROM users WHERE id=?", (int(q),))
        if r:
            return r
    like = f"%{q}%"
    return await _g("SELECT id,name,username,first,last FROM users WHERE name LIKE ? OR username LIKE ? ORDER BY last DESC LIMIT ?", (like, like, limit))


async def user_detail(uid: int) -> dict | None:
    r = await _g("SELECT id,name,username,first,last,COALESCE(blocked,0) FROM users WHERE id=?", (uid,))
    if not r:
        return None
    u = r[0]
    jobs = await _g("SELECT COUNT(*),SUM(ok),AVG(secs) FROM jobs WHERE uid=?", (uid,))
    day = int(time.time()) - 86400
    today = (await _g("SELECT COUNT(*) FROM jobs WHERE uid=? AND ok=1 AND ts>?", (uid, day)))[0][0]
    last = await _g("SELECT ts,ok,secs,effects FROM jobs WHERE uid=? ORDER BY id DESC LIMIT 5", (uid,))
    ban_r = await _g("SELECT reason,ts FROM bans WHERE uid=?", (uid,))
    return {"id": u[0], "name": u[1], "username": u[2], "first": u[3], "last": u[4], "blocked": u[5], "jobs": jobs[0][0] or 0, "ok": int(jobs[0][1] or 0),
            "avg": jobs[0][2] or 0, "today": today, "recent": last, "ban": ban_r[0] if ban_r else None}


async def jobs_today(uid: int) -> int:
    return (await _g("SELECT COUNT(*) FROM jobs WHERE uid=? AND ok=1 AND ts>?", (uid, int(time.time()) - 86400)))[0][0]


async def recent_jobs(n: int = 15):
    return await _g("SELECT j.ts,j.uid,COALESCE(u.name,''),j.ok,j.secs,j.effects FROM jobs j LEFT JOIN users u ON u.id=j.uid ORDER BY j.id DESC LIMIT ?", (n,))


async def banned_list():
    return await _g("SELECT b.uid,COALESCE(u.name,''),b.reason,b.ts FROM bans b LEFT JOIN users u ON u.id=b.uid ORDER BY b.ts DESC LIMIT 30")


async def per_day(n: int = 7):
    """(تاريخ، مستخدمون جدد، عمليات ناجحة، فاشلة) لآخر n يوم"""
    out = []
    now = int(time.time())
    for i in range(n - 1, -1, -1):
        a, b = now - (i + 1) * 86400, now - i * 86400
        nu = (await _g("SELECT COUNT(*) FROM users WHERE first>? AND first<=?", (a, b)))[0][0]
        ok = (await _g("SELECT COUNT(*) FROM jobs WHERE ok=1 AND ts>? AND ts<=?", (a, b)))[0][0]
        bad = (await _g("SELECT COUNT(*) FROM jobs WHERE ok=0 AND ts>? AND ts<=?", (a, b)))[0][0]
        out.append((time.strftime("%m/%d", time.localtime(b)), nu, ok, bad))
    return out


async def totals() -> dict:
    now = int(time.time())
    g = lambda sql, a=(): _g(sql, a)  # noqa: E731
    r = {}
    for name, secs in (("d1", 86400), ("d7", 7 * 86400), ("d30", 30 * 86400)):
        r[name] = {"jobs": (await g("SELECT COUNT(*) FROM jobs WHERE ok=1 AND ts>?", (now - secs,)))[0][0],
                   "failed": (await g("SELECT COUNT(*) FROM jobs WHERE ok=0 AND ts>?", (now - secs,)))[0][0],
                   "active": (await g("SELECT COUNT(*) FROM users WHERE last>?", (now - secs,)))[0][0],
                   "new": (await g("SELECT COUNT(*) FROM users WHERE first>?", (now - secs,)))[0][0]}
    r["users"] = (await g("SELECT COUNT(*) FROM users"))[0][0]
    r["blocked"] = (await g("SELECT COUNT(*) FROM users WHERE COALESCE(blocked,0)=1"))[0][0]
    r["banned"] = (await g("SELECT COUNT(*) FROM bans"))[0][0]
    r["jobs_all"] = (await g("SELECT COUNT(*) FROM jobs WHERE ok=1"))[0][0]
    r["failed_all"] = (await g("SELECT COUNT(*) FROM jobs WHERE ok=0"))[0][0]
    r["avg"] = (await g("SELECT AVG(secs) FROM jobs WHERE ok=1"))[0][0] or 0
    r["max"] = (await g("SELECT MAX(secs) FROM jobs WHERE ok=1"))[0][0] or 0
    hours = {}
    for (ts,) in await g("SELECT ts FROM jobs WHERE ts>?", (now - 7 * 86400,)):
        h = time.localtime(ts).tm_hour
        hours[h] = hours.get(h, 0) + 1
    r["peak"] = sorted(hours.items(), key=lambda kv: -kv[1])[:3]
    return r


async def top_tools(photo: bool, limit: int = 8):
    cnt: dict[str, int] = {}
    for (e,) in await _g("SELECT effects FROM jobs WHERE ok=1 ORDER BY id DESC LIMIT 1500"):
        if is_photo(e) != photo:
            continue
        for x in (e or "").split(","):
            x = x.replace("photo:", "")
            if x:
                cnt[x] = cnt.get(x, 0) + 1
    return sorted(cnt.items(), key=lambda kv: -kv[1])[:limit]


async def add_alert(kind: str, text: str):
    await asyncio.to_thread(_q, "INSERT INTO alerts(ts,kind,text) VALUES(?,?,?)", (int(time.time()), kind, text))


async def recent_alerts(n: int = 12):
    return await _g("SELECT ts,kind,text FROM alerts ORDER BY id DESC LIMIT ?", (n,))


async def export_rows(table: str):
    if table == "users":
        return ["id", "name", "username", "first_seen", "last_seen", "blocked"], await _g("SELECT id,name,username,first,last,COALESCE(blocked,0) FROM users ORDER BY id")
    return ["id", "uid", "ts", "ok", "secs", "effects"], await _g("SELECT id,uid,ts,ok,secs,effects FROM jobs ORDER BY id")

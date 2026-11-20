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
            "CREATE TABLE IF NOT EXISTS jobs(id INTEGER PRIMARY KEY AUTOINCREMENT,uid INTEGER,ts INTEGER,ok INTEGER,secs REAL,effects TEXT);")
    return _c


def _q(sql, args=(), fetch=False):
    with _lock:
        c = _conn()
        cur = c.execute(sql, args)
        c.commit()
        return cur.fetchall() if fetch else None


async def touch_user(uid: int, name: str, username: str | None):
    now = int(time.time())
    await asyncio.to_thread(_q, "INSERT INTO users(id,name,username,first,last) VALUES(?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET name=excluded.name,username=excluded.username,last=excluded.last", (uid, name, username, now, now))


async def log_job(uid: int, ok: bool, secs: float, effects: list[str]):
    await asyncio.to_thread(_q, "INSERT INTO jobs(uid,ts,ok,secs,effects) VALUES(?,?,?,?,?)", (uid, int(time.time()), int(ok), secs, ",".join(effects)))


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

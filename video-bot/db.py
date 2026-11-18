import time

import aiosqlite

import config

_db: aiosqlite.Connection | None = None


async def init():
    global _db
    _db = await aiosqlite.connect(config.DB_PATH)
    await _db.executescript(
        """
        PRAGMA journal_mode=WAL;
        CREATE TABLE IF NOT EXISTS users(id INTEGER PRIMARY KEY, name TEXT, username TEXT, joined INTEGER);
        CREATE TABLE IF NOT EXISTS channels(chat TEXT PRIMARY KEY, title TEXT, link TEXT);
        CREATE TABLE IF NOT EXISTS cache(key TEXT PRIMARY KEY, file_id TEXT, kind TEXT, caption TEXT, ts INTEGER);
        CREATE TABLE IF NOT EXISTS downloads(id INTEGER PRIMARY KEY AUTOINCREMENT, user INTEGER, platform TEXT, ts INTEGER, ok INTEGER);
        """
    )
    for ch in config.FORCE_CHANNELS:
        link = f"https://t.me/{ch.lstrip('@')}" if ch.startswith("@") else ""
        await _db.execute("INSERT OR IGNORE INTO channels(chat,title,link) VALUES(?,?,?)", (ch, ch, link))
    await _db.commit()


async def add_user(uid: int, name: str, username: str | None) -> bool:
    cur = await _db.execute("SELECT 1 FROM users WHERE id=?", (uid,))
    if await cur.fetchone():
        return False
    await _db.execute("INSERT INTO users VALUES(?,?,?,?)", (uid, name, username, int(time.time())))
    await _db.commit()
    return True


async def all_users() -> list[int]:
    cur = await _db.execute("SELECT id FROM users")
    return [r[0] for r in await cur.fetchall()]


async def channels() -> list[tuple[str, str, str]]:
    cur = await _db.execute("SELECT chat,title,link FROM channels")
    return list(await cur.fetchall())


async def add_channel(chat: str, title: str, link: str):
    await _db.execute("INSERT OR REPLACE INTO channels VALUES(?,?,?)", (chat, title, link))
    await _db.commit()


async def del_channel(chat: str) -> bool:
    cur = await _db.execute("DELETE FROM channels WHERE chat=?", (chat,))
    await _db.commit()
    return cur.rowcount > 0


async def cache_get(key: str):
    cur = await _db.execute("SELECT file_id,kind,caption FROM cache WHERE key=?", (key,))
    return await cur.fetchone()


async def cache_put(key: str, file_id: str, kind: str, caption: str):
    await _db.execute("INSERT OR REPLACE INTO cache VALUES(?,?,?,?,?)", (key, file_id, kind, caption, int(time.time())))
    await _db.commit()


async def log_download(uid: int, platform: str, ok: bool):
    await _db.execute("INSERT INTO downloads(user,platform,ts,ok) VALUES(?,?,?,?)", (uid, platform, int(time.time()), int(ok)))
    await _db.commit()


async def stats() -> dict:
    out = {}
    for k, q in {
        "users": "SELECT COUNT(*) FROM users",
        "today_users": f"SELECT COUNT(*) FROM users WHERE joined>{int(time.time()) - 86400}",
        "downloads": "SELECT COUNT(*) FROM downloads WHERE ok=1",
        "today": f"SELECT COUNT(*) FROM downloads WHERE ok=1 AND ts>{int(time.time()) - 86400}",
        "failed": "SELECT COUNT(*) FROM downloads WHERE ok=0",
        "cached": "SELECT COUNT(*) FROM cache",
    }.items():
        cur = await _db.execute(q)
        out[k] = (await cur.fetchone())[0]
    cur = await _db.execute("SELECT platform,COUNT(*) c FROM downloads WHERE ok=1 GROUP BY platform ORDER BY c DESC")
    out["by_platform"] = list(await cur.fetchall())
    return out

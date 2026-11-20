"""خادم التطبيق المصغّر (Telegram Mini App) لمحرر النصوص: صفحة + فيديو المعاينة + الخطوط + استقبال النص"""
import hashlib
import hmac
import json
import time
import urllib.parse
from pathlib import Path

from aiohttp import web

BASE = Path(__file__).parent
FONTS = BASE / "fonts"
CATALOG = json.loads((FONTS / "catalog.json").read_text(encoding="utf-8"))
FONT_FILES = {c["file"] for c in CATALOG}

# يضبطها bot.py
WTOKENS: dict[str, int] = {}  # رمز التطبيق المصغّر -> user id
get_job = lambda uid: None  # noqa: E731
on_text = None  # async (uid, params) -> None
BOT_TOKEN = ""


def check_init_data(init_data: str, token: str, max_age: int = 86400) -> dict | None:
    """التحقق من توقيع Telegram WebApp initData (HMAC-SHA256) وإرجاع المستخدم"""
    try:
        pairs = dict(urllib.parse.parse_qsl(init_data, keep_blank_values=True))
        got = pairs.pop("hash", "")
        data = "\n".join(f"{k}={v}" for k, v in sorted(pairs.items()))
        secret = hmac.new(b"WebAppData", token.encode(), hashlib.sha256).digest()
        calc = hmac.new(secret, data.encode(), hashlib.sha256).hexdigest()
        if not hmac.compare_digest(calc, got):
            return None
        if time.time() - int(pairs.get("auth_date", "0")) > max_age:
            return None
        return json.loads(pairs.get("user", "{}"))
    except Exception:
        return None


def _auth(request: web.Request, wtoken: str):
    uid = WTOKENS.get(wtoken)
    if uid is None:
        raise web.HTTPNotFound()
    init = request.headers.get("X-Init-Data", "")
    user = check_init_data(init, BOT_TOKEN)
    if not user or int(user.get("id", 0)) != uid:
        raise web.HTTPForbidden(text="auth")
    return uid


async def page(request: web.Request):
    return web.FileResponse(BASE / "webapp" / "index.html", headers={"Cache-Control": "no-store"})


async def api_init(request: web.Request):
    t = request.query.get("t", "")
    uid = _auth(request, t)
    j = get_job(uid)
    if not j:
        raise web.HTTPNotFound(text="no job")
    return web.json_response({"video": f"/media/{t}.mp4", "w": j["pmeta"]["w"], "h": j["pmeta"]["h"], "dur": j["pmeta"]["dur"], "fonts": CATALOG})


async def media(request: web.Request):
    t = request.match_info["t"].removesuffix(".mp4")
    uid = WTOKENS.get(t)
    j = get_job(uid) if uid is not None else None
    if not j:
        raise web.HTTPNotFound()
    return web.FileResponse(j["proxy"], headers={"Cache-Control": "no-store"})


async def font(request: web.Request):
    name = request.match_info["name"]
    if name not in FONT_FILES:
        raise web.HTTPNotFound()
    return web.FileResponse(FONTS / name, headers={"Cache-Control": "public, max-age=86400"})


def _num(v, lo, hi, d):
    try:
        return max(lo, min(hi, float(v)))
    except (TypeError, ValueError):
        return d


def sanitize(p: dict) -> dict:
    color = lambda c, d: c if isinstance(c, str) and len(c) in (4, 7) and c.startswith("#") else d  # noqa: E731
    return {
        "text": str(p.get("text", ""))[:200],
        "font": p.get("font") if p.get("font") in FONT_FILES else "Cairo-b.ttf",
        "size": _num(p.get("size"), 2, 30, 8),
        "color": color(p.get("color"), "#ffffff"),
        "stroke": _num(p.get("stroke"), 0, 40, 10),
        "stroke_color": color(p.get("stroke_color"), "#000000"),
        "shadow": bool(p.get("shadow")), "glow": bool(p.get("glow")), "box": bool(p.get("box")),
        "box_color": color(p.get("box_color"), "#000000"),
        "align": p.get("align") if p.get("align") in ("left", "center", "right") else "center",
        "x": _num(p.get("x"), 0, 100, 50), "y": _num(p.get("y"), 0, 100, 80),
        "rot": _num(p.get("rot"), -45, 45, 0),
        "anim": p.get("anim") if p.get("anim") in ("none", "fade", "pop", "slide", "bounce") else "none",
        "start": _num(p.get("start"), 0, 3600, 0), "end": _num(p.get("end"), 0, 3600, 0),
    }


async def api_text(request: web.Request):
    body = await request.json()
    t = str(body.get("t", ""))
    uid = _auth(request, t)
    p = sanitize(body.get("params") or {})
    if not p["text"].strip():
        return web.json_response({"ok": False, "error": "empty"}, status=400)
    if on_text:
        await on_text(uid, p)
    return web.json_response({"ok": True})


def add_routes(app: web.Application):
    app.router.add_get("/app", page)
    app.router.add_get("/api/init", api_init)
    app.router.add_post("/api/text", api_text)
    app.router.add_get("/media/{t}", media)
    app.router.add_get("/fonts/{name}", font)

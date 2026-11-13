#!/usr/bin/env python3
"""
خادم تطبيق Velox المستقل.
- مستقل تماماً عن البوت: قاعدة بياناته الخاصة (velox_app.db) وحساباته ورصيده الخاص.
- ملف واحد بدون أي مكتبات خارجية (Python 3.8+).

تشغيل:   python3 velox_app_server.py
متغيرات البيئة (اختيارية):
  PORT             المنفذ (الافتراضي 8080)
  VELOX_APP_DB     مسار قاعدة البيانات (الافتراضي: بجانب هذا الملف)
  ADMIN_PASSWORD   كلمة سر المطور (الافتراضي 1005 — غيّرها)
  ADMIN_EMAIL      بريد المطور (الافتراضي admin@velox.app)
  ALLOWED_ORIGINS  دومينات مسموحة مفصولة بفاصلة (الافتراضي *)
"""
import base64
import hashlib
import hmac
import io
import json
import os
import random
import re
import socket
import sqlite3
import threading
import time
import zipfile
from datetime import datetime, timedelta
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

HERE = os.path.dirname(os.path.abspath(__file__))
DB_PATH = os.environ.get("VELOX_APP_DB", os.path.join(HERE, "velox_app.db"))
PORT = int(os.environ.get("PORT", "8080"))
ADMIN_UID = "8010837412"
ADMIN_USERNAME = "admin"
ADMIN_PASSWORD = os.environ.get("ADMIN_PASSWORD", "1005")
ADMIN_EMAIL = os.environ.get("ADMIN_EMAIL", "admin@velox.app")
ALLOWED_ORIGINS = os.environ.get("ALLOWED_ORIGINS", "*")

PLATFORMS = ["تيك توك", "انستغرام", "فيسبوك", "يوتيوب", "كواي", "تليجرام", "ثريدز", "واتساب", "خدمات مميزة", "تفاعلات تليجرام"]
GAMES = ["شدات ببجي موبايل", "نجوم تليجرام", "حسابات نتفلكس", "جواهر فري فاير", "ارقام تليجرام", "تمويل اشتراك اجباري",
         "تفعيل تليجرام مميز", "رصيد اسياسيل", "جواهر لودو", "رصيد اثير", "ذهب لودو"]
STATUSES = ["قيد الانتظار", "قيد التنفيذ", "مكتمل", "مرفوض"]
DEFAULT_SETTINGS = {
    "app_name": "Velox", "currency": "نقطة", "daily_points": "50", "registration_open": "1",
    "support_link": "", "channel_link": "", "contact_text": "للشحن تواصل مع الدعم.",
}

_lock = threading.RLock()


# ───────────────────────── قاعدة البيانات ─────────────────────────
def db():
    conn = sqlite3.connect(DB_PATH, timeout=15)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA busy_timeout=15000")
    conn.execute("PRAGMA journal_mode=WAL")
    return conn


def rows(conn, sql, args=()):
    return [dict(r) for r in conn.execute(sql, args).fetchall()]


def now():
    return datetime.now().strftime("%Y-%m-%d %H:%M:%S")


def hash_pw(pw):
    salt = os.urandom(8).hex()
    return f"vx$120000${salt}${hashlib.pbkdf2_hmac('sha256', pw.encode(), salt.encode(), 120000).hex()}"


def check_pw(pw, stored):
    try:
        _, it, salt, h = stored.split("$")
        c = hashlib.pbkdf2_hmac("sha256", pw.encode(), salt.encode(), int(it)).hex()
        return hmac.compare_digest(c, h)
    except Exception:
        return False


def init_db():
    conn = db()
    conn.executescript("""
    CREATE TABLE IF NOT EXISTS users (
        id INTEGER PRIMARY KEY AUTOINCREMENT, uid TEXT UNIQUE NOT NULL, name TEXT NOT NULL,
        username TEXT UNIQUE NOT NULL, email TEXT, pass_hash TEXT NOT NULL,
        balance INTEGER NOT NULL DEFAULT 0, spent INTEGER NOT NULL DEFAULT 0,
        is_admin INTEGER NOT NULL DEFAULT 0, banned INTEGER NOT NULL DEFAULT 0,
        last_daily TEXT, created_at TEXT NOT NULL);
    CREATE TABLE IF NOT EXISTS services (
        id INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL, category TEXT NOT NULL, name TEXT NOT NULL,
        price INTEGER NOT NULL DEFAULT 0, min_qty INTEGER NOT NULL DEFAULT 0, max_qty INTEGER NOT NULL DEFAULT 0,
        quantity TEXT DEFAULT '', description TEXT DEFAULT '', active INTEGER NOT NULL DEFAULT 1, created_at TEXT NOT NULL);
    CREATE TABLE IF NOT EXISTS orders (
        id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, kind TEXT NOT NULL, category TEXT NOT NULL,
        service_id INTEGER, service_name TEXT NOT NULL, link TEXT DEFAULT '', quantity INTEGER DEFAULT 0,
        price INTEGER NOT NULL, status TEXT NOT NULL, note TEXT DEFAULT '', created_at TEXT NOT NULL, updated_at TEXT);
    CREATE TABLE IF NOT EXISTS codes (
        code TEXT PRIMARY KEY, points INTEGER NOT NULL, max_uses INTEGER NOT NULL DEFAULT 0,
        used_count INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL);
    CREATE TABLE IF NOT EXISTS code_uses (user_id INTEGER, code TEXT, PRIMARY KEY (user_id, code));
    CREATE TABLE IF NOT EXISTS ledger (
        id INTEGER PRIMARY KEY AUTOINCREMENT, user_id INTEGER NOT NULL, amount INTEGER NOT NULL,
        kind TEXT NOT NULL, note TEXT DEFAULT '', created_at TEXT NOT NULL);
    CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT);
    """)
    for k, v in DEFAULT_SETTINGS.items():
        conn.execute("INSERT OR IGNORE INTO settings (key, value) VALUES (?,?)", (k, v))
    row = conn.execute("SELECT id, pass_hash FROM users WHERE uid=?", (ADMIN_UID,)).fetchone()
    if not row:
        conn.execute("INSERT INTO users (uid,name,username,email,pass_hash,is_admin,created_at) VALUES (?,?,?,?,?,1,?)",
                     (ADMIN_UID, "المسؤول", ADMIN_USERNAME, ADMIN_EMAIL, hash_pw(ADMIN_PASSWORD), now()))
    elif os.environ.get("ADMIN_PASSWORD"):
        conn.execute("UPDATE users SET pass_hash=? WHERE uid=?", (hash_pw(ADMIN_PASSWORD), ADMIN_UID))
    conn.commit()
    conn.close()


def setting(conn, key):
    r = conn.execute("SELECT value FROM settings WHERE key=?", (key,)).fetchone()
    return r[0] if r else DEFAULT_SETTINGS.get(key, "")


def ledger(conn, uid, amount, kind, note=""):
    conn.execute("INSERT INTO ledger (user_id,amount,kind,note,created_at) VALUES (?,?,?,?,?)", (uid, amount, kind, note, now()))


# ───────────────────────── الجلسات ─────────────────────────
def secret():
    path = os.path.join(os.path.dirname(os.path.abspath(DB_PATH)), "velox_app_secret.txt")
    try:
        with open(path) as f:
            v = f.read().strip()
            if len(v) >= 32:
                return v.encode()
    except Exception:
        pass
    v = os.urandom(32).hex()
    try:
        with open(path, "w") as f:
            f.write(v)
    except Exception:
        pass
    return v.encode()


SECRET = secret()


def make_token(uid):
    payload = base64.urlsafe_b64encode(json.dumps({"u": int(uid), "e": int(time.time()) + 60 * 86400}).encode()).decode()
    return payload + "." + hmac.new(SECRET, payload.encode(), hashlib.sha256).hexdigest()


def token_uid(tok):
    try:
        payload, sig = tok.split(".")
        if not hmac.compare_digest(sig, hmac.new(SECRET, payload.encode(), hashlib.sha256).hexdigest()):
            return None
        d = json.loads(base64.urlsafe_b64decode(payload.encode()))
        return int(d["u"]) if d["e"] > time.time() else None
    except Exception:
        return None


_attempts = {}


def public_user(u):
    return {"id": u["id"], "uid": u["uid"], "name": u["name"], "username": u["username"], "email": u["email"] or "",
            "balance": u["balance"], "spent": u["spent"], "admin": bool(u["is_admin"]), "created_at": u["created_at"]}


def calc_price(svc, qty):
    if svc["kind"] == "game":
        return int(svc["price"])
    return max(1, int(round(float(svc["price"]) * qty / 1000.0)))


# ───────────────────────── الخادم ─────────────────────────
class Api(BaseHTTPRequestHandler):
    server_version = "VeloxApp"

    def log_message(self, *a):
        pass

    def cors(self):
        o = self.headers.get("Origin")
        if o and (ALLOWED_ORIGINS == "*" or o in [x.strip() for x in ALLOWED_ORIGINS.split(",")]):
            self.send_header("Access-Control-Allow-Origin", o)
            self.send_header("Vary", "Origin")
            self.send_header("Access-Control-Allow-Headers", "Authorization, Content-Type")
            self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
            self.send_header("Access-Control-Expose-Headers", "Content-Disposition")

    def out(self, status, obj):
        data = json.dumps(obj, ensure_ascii=False).encode()
        self.send_response(status)
        self.cors()
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def file(self, data, name, mime):
        self.send_response(200)
        self.cors()
        self.send_header("Content-Type", mime)
        self.send_header("Content-Disposition", f'attachment; filename="{name}"')
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def limited(self, limit=12):
        ip = (self.headers.get("X-Forwarded-For") or self.client_address[0]).split(",")[0].strip()
        t = time.time()
        hits = [x for x in _attempts.get(ip, []) if t - x < 60] + [t]
        _attempts[ip] = hits
        return len(hits) > limit

    def do_OPTIONS(self):
        self.send_response(204)
        self.cors()
        self.end_headers()

    def do_GET(self):
        self.route("GET")

    def do_POST(self):
        self.route("POST")

    def route(self, method):
        u = urlparse(self.path)
        q = {k: v[0] for k, v in parse_qs(u.query).items()}
        body = {}
        if method == "POST":
            try:
                n = int(self.headers.get("Content-Length") or 0)
                body = json.loads(self.rfile.read(min(n, 65536)) or b"{}")
            except Exception:
                body = {}
        try:
            with _lock:
                self.handle_api(method, u.path.rstrip("/"), q, body)
        except Exception as e:
            self.out(500, {"message": f"خطأ في الخادم: {e}"})

    # ───── التوثيق ─────
    def user(self, conn, q, admin=False):
        h = self.headers.get("Authorization", "")
        tok = h[7:] if h.startswith("Bearer ") else q.get("token", "")
        uid = token_uid(tok) if tok else None
        u = conn.execute("SELECT * FROM users WHERE id=?", (uid,)).fetchone() if uid else None
        if not u:
            self.out(401, {"message": "غير مسجّل"})
            return None
        if u["banned"]:
            self.out(403, {"message": "هذا الحساب محظور"})
            return None
        if admin and not u["is_admin"]:
            self.out(403, {"message": "صلاحية المسؤول مطلوبة"})
            return None
        return u

    def handle_api(self, m, p, q, b):
        if not p.startswith("/api/"):
            return self.out(200, {"velox_app": True})
        r = p[5:]
        conn = db()
        try:
            return self.dispatch(conn, m, r, q, b)
        finally:
            conn.commit()
            conn.close()

    def dispatch(self, conn, m, r, q, b):
        S = lambda k: setting(conn, k)

        def to_int(v, d=0):
            try:
                return int(float(str(v).strip()))
            except Exception:
                return d

        # ───── عام ─────
        if r == "app/info" and m == "GET":
            return self.out(200, {"name": S("app_name"), "currency": S("currency"), "support_link": S("support_link"),
                                  "channel_link": S("channel_link"), "contact_text": S("contact_text"),
                                  "daily_points": to_int(S("daily_points")), "registration_open": S("registration_open") == "1",
                                  "platforms": PLATFORMS, "games": GAMES})
        if r == "register" and m == "POST":
            if self.limited():
                return self.out(429, {"message": "محاولات كثيرة، انتظر دقيقة"})
            if S("registration_open") != "1":
                return self.out(403, {"message": "التسجيل مغلق حالياً"})
            name = str(b.get("name", "")).strip()[:40]
            un = str(b.get("username", "")).strip().lower()
            pw = str(b.get("password", ""))
            em = str(b.get("email", "")).strip().lower()[:120]
            if len(name) < 2:
                return self.out(400, {"message": "اكتب اسمك"})
            if not re.fullmatch(r"[a-z0-9_.]{3,20}", un):
                return self.out(400, {"message": "اسم المستخدم 3-20 حرفاً إنجليزياً أو أرقام أو _"})
            if len(pw) < 6:
                return self.out(400, {"message": "كلمة السر 6 أحرف على الأقل"})
            if em and not re.fullmatch(r"[^@\s]+@[^@\s]+\.[^@\s]+", em):
                return self.out(400, {"message": "البريد الإلكتروني غير صحيح"})
            if conn.execute("SELECT 1 FROM users WHERE username=?", (un,)).fetchone():
                return self.out(409, {"message": "اسم المستخدم مستخدم، اختر غيره"})
            if em and conn.execute("SELECT 1 FROM users WHERE lower(email)=?", (em,)).fetchone():
                return self.out(409, {"message": "البريد مسجّل بالفعل"})
            uid = None
            for _ in range(50):
                c = str(random.randint(10000000, 99999999))
                if not conn.execute("SELECT 1 FROM users WHERE uid=?", (c,)).fetchone():
                    uid = c
                    break
            cur = conn.execute("INSERT INTO users (uid,name,username,email,pass_hash,created_at) VALUES (?,?,?,?,?,?)",
                               (uid, name, un, em or None, hash_pw(pw), now()))
            u = conn.execute("SELECT * FROM users WHERE id=?", (cur.lastrowid,)).fetchone()
            return self.out(200, {"ok": True, "token": make_token(u["id"]), "user": public_user(u)})
        if r == "login" and m == "POST":
            if self.limited():
                return self.out(429, {"message": "محاولات كثيرة، انتظر دقيقة"})
            lg = str(b.get("login", "")).strip().lower()
            pw = str(b.get("password", ""))
            u = conn.execute("SELECT * FROM users WHERE lower(username)=? OR lower(email)=? OR uid=?", (lg, lg, lg)).fetchone()
            if not u or not check_pw(pw, u["pass_hash"]):
                return self.out(401, {"message": "بيانات الدخول غير صحيحة"})
            if u["banned"]:
                return self.out(403, {"message": "هذا الحساب محظور"})
            return self.out(200, {"ok": True, "token": make_token(u["id"]), "user": public_user(u)})

        # ───── المستخدم ─────
        if not r.startswith("admin/"):
            u = self.user(conn, q)
            if not u:
                return
            uid = u["id"]
            if r == "me" and m == "GET":
                d = public_user(u)
                d["orders"] = conn.execute("SELECT COUNT(*) FROM orders WHERE user_id=?", (uid,)).fetchone()[0]
                d["can_daily"] = (not u["last_daily"]) or (datetime.now() - datetime.strptime(u["last_daily"], "%Y-%m-%d %H:%M:%S") >= timedelta(hours=24))
                d["daily_points"] = to_int(S("daily_points"))
                return self.out(200, {"user": d})
            if r == "me/password" and m == "POST":
                if not check_pw(str(b.get("old", "")), u["pass_hash"]):
                    return self.out(400, {"message": "كلمة السر الحالية غير صحيحة"})
                if len(str(b.get("new", ""))) < 6:
                    return self.out(400, {"message": "كلمة السر الجديدة 6 أحرف على الأقل"})
                conn.execute("UPDATE users SET pass_hash=? WHERE id=?", (hash_pw(str(b["new"])), uid))
                return self.out(200, {"ok": True})
            if r == "catalog" and m == "GET":
                kind = "game" if q.get("kind") == "game" else "rashq"
                cnt = {x["category"]: x["c"] for x in rows(conn, "SELECT category, COUNT(*) c FROM services WHERE kind=? AND active=1 GROUP BY category", (kind,))}
                names = GAMES if kind == "game" else PLATFORMS
                return self.out(200, {"categories": {n: cnt.get(n, 0) for n in names}})
            if r == "services" and m == "GET":
                kind = "game" if q.get("kind") == "game" else "rashq"
                return self.out(200, {"services": rows(conn, "SELECT id,kind,category,name,price,min_qty,max_qty,quantity,description FROM services WHERE kind=? AND category=? AND active=1 ORDER BY id", (kind, q.get("category", "")))})
            if r == "services/one" and m == "GET":
                s = conn.execute("SELECT id,kind,category,name,price,min_qty,max_qty,quantity,description FROM services WHERE id=? AND active=1", (to_int(q.get("id")),)).fetchone()
                return self.out(200, {"service": dict(s)}) if s else self.out(404, {"message": "الخدمة غير موجودة"})
            if r == "orders" and m == "POST":
                svc = conn.execute("SELECT * FROM services WHERE id=? AND active=1", (to_int(b.get("service_id")),)).fetchone()
                if not svc:
                    return self.out(404, {"message": "الخدمة غير موجودة"})
                link = str(b.get("link", "")).strip()[:300]
                if not link:
                    return self.out(400, {"message": "اكتب الرابط أو معرف اللاعب" if svc["kind"] == "game" else "اكتب الرابط"})
                qty = to_int(b.get("quantity"))
                if svc["kind"] == "rashq":
                    if qty < svc["min_qty"] or (svc["max_qty"] and qty > svc["max_qty"]):
                        return self.out(400, {"message": f"الكمية يجب أن تكون بين {svc['min_qty']} و {svc['max_qty']}"})
                else:
                    qty = 0
                price = calc_price(svc, qty)
                cur_bal = conn.execute("SELECT balance FROM users WHERE id=?", (uid,)).fetchone()[0]
                if cur_bal < price:
                    return self.out(402, {"message": f"رصيدك غير كافٍ. المطلوب {price} ورصيدك {cur_bal}"})
                conn.execute("UPDATE users SET balance=balance-?, spent=spent+? WHERE id=?", (price, price, uid))
                cur = conn.execute("INSERT INTO orders (user_id,kind,category,service_id,service_name,link,quantity,price,status,created_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                                   (uid, svc["kind"], svc["category"], svc["id"], svc["name"], link, qty, price, STATUSES[0], now()))
                ledger(conn, uid, -price, "order", f"طلب #{cur.lastrowid} {svc['name']}")
                bal = conn.execute("SELECT balance FROM users WHERE id=?", (uid,)).fetchone()[0]
                return self.out(200, {"ok": True, "order_id": cur.lastrowid, "balance": bal})
            if r == "orders" and m == "GET":
                return self.out(200, {"orders": rows(conn, "SELECT id,kind,category,service_name,link,quantity,price,status,note,created_at FROM orders WHERE user_id=? ORDER BY id DESC LIMIT 200", (uid,))})
            if r == "track" and m == "GET":
                o = conn.execute("SELECT id,kind,category,service_name,link,quantity,price,status,note,created_at,updated_at FROM orders WHERE id=? AND (user_id=? OR ?=1)", (to_int(str(q.get("id", "")).lstrip("#")), uid, u["is_admin"])).fetchone()
                return self.out(200, {"order": dict(o)}) if o else self.out(404, {"message": "لم يتم العثور على الطلب"})
            if r == "ledger" and m == "GET":
                return self.out(200, {"items": rows(conn, "SELECT amount,kind,note,created_at FROM ledger WHERE user_id=? ORDER BY id DESC LIMIT 100", (uid,))})
            if r == "redeem" and m == "POST":
                code = str(b.get("code", "")).strip().upper()
                c = conn.execute("SELECT * FROM codes WHERE code=?", (code,)).fetchone()
                if not c:
                    return self.out(404, {"message": "الكود غير صحيح"})
                if c["max_uses"] and c["used_count"] >= c["max_uses"]:
                    return self.out(400, {"message": "انتهى عدد استخدامات الكود"})
                if conn.execute("SELECT 1 FROM code_uses WHERE user_id=? AND code=?", (uid, code)).fetchone():
                    return self.out(400, {"message": "استخدمت هذا الكود من قبل"})
                conn.execute("INSERT INTO code_uses (user_id, code) VALUES (?,?)", (uid, code))
                conn.execute("UPDATE codes SET used_count=used_count+1 WHERE code=?", (code,))
                conn.execute("UPDATE users SET balance=balance+? WHERE id=?", (c["points"], uid))
                ledger(conn, uid, c["points"], "code", f"كود {code}")
                return self.out(200, {"ok": True, "points": c["points"], "balance": conn.execute("SELECT balance FROM users WHERE id=?", (uid,)).fetchone()[0]})
            if r == "daily" and m == "POST":
                pts = to_int(S("daily_points"))
                if u["last_daily"] and datetime.now() - datetime.strptime(u["last_daily"], "%Y-%m-%d %H:%M:%S") < timedelta(hours=24):
                    left = timedelta(hours=24) - (datetime.now() - datetime.strptime(u["last_daily"], "%Y-%m-%d %H:%M:%S"))
                    return self.out(400, {"message": f"الهدية متاحة بعد {int(left.total_seconds() // 3600)} ساعة و {int(left.total_seconds() % 3600 // 60)} دقيقة"})
                if pts <= 0:
                    return self.out(400, {"message": "الهدية اليومية غير مفعّلة"})
                conn.execute("UPDATE users SET balance=balance+?, last_daily=? WHERE id=?", (pts, now(), uid))
                ledger(conn, uid, pts, "daily", "الهدية اليومية")
                return self.out(200, {"ok": True, "points": pts, "balance": conn.execute("SELECT balance FROM users WHERE id=?", (uid,)).fetchone()[0]})
            if r == "transfer" and m == "POST":
                to = str(b.get("to", "")).strip().lower().lstrip("@")
                amt = to_int(b.get("amount"))
                if amt <= 0:
                    return self.out(400, {"message": "اكتب عدد النقاط"})
                t = conn.execute("SELECT * FROM users WHERE lower(username)=? OR uid=?", (to, to)).fetchone()
                if not t or t["banned"]:
                    return self.out(404, {"message": "المستخدم غير موجود"})
                if t["id"] == uid:
                    return self.out(400, {"message": "لا يمكنك التحويل لنفسك"})
                if u["balance"] < amt:
                    return self.out(402, {"message": "رصيدك غير كافٍ"})
                conn.execute("UPDATE users SET balance=balance-? WHERE id=?", (amt, uid))
                conn.execute("UPDATE users SET balance=balance+? WHERE id=?", (amt, t["id"]))
                ledger(conn, uid, -amt, "transfer", f"تحويل إلى @{t['username']}")
                ledger(conn, t["id"], amt, "transfer", f"تحويل من @{u['username']}")
                return self.out(200, {"ok": True, "balance": u["balance"] - amt})
            return self.out(404, {"message": "غير موجود"})

        # ───── المسؤول ─────
        u = self.user(conn, q, admin=True)
        if not u:
            return
        a = r[6:]
        if a == "stats" and m == "GET":
            c1 = lambda sql: conn.execute(sql).fetchone()[0]
            return self.out(200, {"users": c1("SELECT COUNT(*) FROM users"), "orders": c1("SELECT COUNT(*) FROM orders"),
                                  "pending": c1("SELECT COUNT(*) FROM orders WHERE status='قيد الانتظار'"),
                                  "completed": c1("SELECT COUNT(*) FROM orders WHERE status='مكتمل'"),
                                  "services": c1("SELECT COUNT(*) FROM services"), "balance_total": c1("SELECT COALESCE(SUM(balance),0) FROM users"),
                                  "spent_total": c1("SELECT COALESCE(SUM(spent),0) FROM users")})
        if a == "users" and m == "GET":
            s = str(q.get("q", "")).strip().lower()
            like = f"%{s}%"
            data = rows(conn, "SELECT * FROM users WHERE ?='' OR lower(name) LIKE ? OR lower(username) LIKE ? OR uid LIKE ? OR lower(COALESCE(email,'')) LIKE ? ORDER BY id DESC LIMIT 100", (s, like, like, like, like))
            return self.out(200, {"users": [dict(public_user(x), banned=bool(x["banned"])) for x in data], "total": conn.execute("SELECT COUNT(*) FROM users").fetchone()[0]})
        if a == "user/balance" and m == "POST":
            t = conn.execute("SELECT * FROM users WHERE id=?", (to_int(b.get("id")),)).fetchone()
            if not t:
                return self.out(404, {"message": "المستخدم غير موجود"})
            amt = abs(to_int(b.get("amount")))
            mode = b.get("mode")
            if mode == "add":
                delta = amt
            elif mode == "deduct":
                delta = -min(amt, t["balance"])
            elif mode == "reset":
                delta = -t["balance"]
            else:
                return self.out(400, {"message": "عملية غير معروفة"})
            conn.execute("UPDATE users SET balance=balance+? WHERE id=?", (delta, t["id"]))
            ledger(conn, t["id"], delta, "admin", "من المسؤول")
            return self.out(200, {"ok": True, "balance": t["balance"] + delta})
        if a == "user/ban" and m == "POST":
            t = conn.execute("SELECT * FROM users WHERE id=?", (to_int(b.get("id")),)).fetchone()
            if not t or t["is_admin"]:
                return self.out(400, {"message": "لا يمكن تنفيذ العملية"})
            conn.execute("UPDATE users SET banned=? WHERE id=?", (1 if b.get("banned") else 0, t["id"]))
            return self.out(200, {"ok": True})
        if a == "orders" and m == "GET":
            st = q.get("status", "")
            data = rows(conn, "SELECT o.*, u.username, u.name AS user_name FROM orders o JOIN users u ON u.id=o.user_id WHERE ?='' OR o.status=? ORDER BY o.id DESC LIMIT 200", (st, st))
            return self.out(200, {"orders": data, "statuses": STATUSES})
        if a == "order/status" and m == "POST":
            o = conn.execute("SELECT * FROM orders WHERE id=?", (to_int(b.get("id")),)).fetchone()
            st = b.get("status")
            if not o or st not in STATUSES:
                return self.out(400, {"message": "بيانات غير صحيحة"})
            if st == "مرفوض" and o["status"] != "مرفوض":
                conn.execute("UPDATE users SET balance=balance+?, spent=MAX(0,spent-?) WHERE id=?", (o["price"], o["price"], o["user_id"]))
                ledger(conn, o["user_id"], o["price"], "refund", f"استرداد طلب #{o['id']}")
            elif o["status"] == "مرفوض" and st != "مرفوض":
                return self.out(400, {"message": "الطلب المرفوض تم استرداد نقاطه، لا يمكن إعادة فتحه"})
            conn.execute("UPDATE orders SET status=?, note=?, updated_at=? WHERE id=?", (st, str(b.get("note", ""))[:200], now(), o["id"]))
            return self.out(200, {"ok": True})
        if a == "services" and m == "GET":
            kind = "game" if q.get("kind") == "game" else "rashq"
            return self.out(200, {"services": rows(conn, "SELECT * FROM services WHERE kind=? AND category=? ORDER BY id", (kind, q.get("category", ""))),
                                  "categories": GAMES if kind == "game" else PLATFORMS})
        if a == "service/save" and m == "POST":
            kind = "game" if b.get("kind") == "game" else "rashq"
            cat = str(b.get("category", ""))
            if cat not in (GAMES if kind == "game" else PLATFORMS):
                return self.out(400, {"message": "القسم غير موجود"})
            name = str(b.get("name", "")).strip()[:120]
            if not name:
                return self.out(400, {"message": "اكتب اسم الخدمة"})
            vals = (name, to_int(b.get("price")), to_int(b.get("min_qty")), to_int(b.get("max_qty")), str(b.get("quantity", ""))[:60], str(b.get("description", ""))[:300], 1 if b.get("active", True) else 0)
            if b.get("id"):
                conn.execute("UPDATE services SET name=?, price=?, min_qty=?, max_qty=?, quantity=?, description=?, active=? WHERE id=?", vals + (to_int(b["id"]),))
            else:
                conn.execute("INSERT INTO services (kind,category,name,price,min_qty,max_qty,quantity,description,active,created_at) VALUES (?,?,?,?,?,?,?,?,?,?)", (kind, cat) + vals + (now(),))
            return self.out(200, {"ok": True})
        if a == "service/delete" and m == "POST":
            conn.execute("DELETE FROM services WHERE id=?", (to_int(b.get("id")),))
            return self.out(200, {"ok": True})
        if a == "codes" and m == "GET":
            return self.out(200, {"codes": rows(conn, "SELECT * FROM codes ORDER BY created_at DESC")})
        if a == "codes/add" and m == "POST":
            code = (str(b.get("code", "")).strip().upper() or ("VX" + os.urandom(3).hex().upper()))
            if not re.fullmatch(r"[A-Z0-9_-]{3,30}", code):
                return self.out(400, {"message": "الكود حروف إنجليزية وأرقام فقط"})
            if to_int(b.get("points")) <= 0:
                return self.out(400, {"message": "اكتب عدد النقاط"})
            if conn.execute("SELECT 1 FROM codes WHERE code=?", (code,)).fetchone():
                return self.out(409, {"message": "الكود موجود مسبقاً"})
            conn.execute("INSERT INTO codes (code,points,max_uses,created_at) VALUES (?,?,?,?)", (code, to_int(b.get("points")), to_int(b.get("max_uses")), now()))
            return self.out(200, {"ok": True, "code": code})
        if a == "codes/delete" and m == "POST":
            conn.execute("DELETE FROM codes WHERE code=?", (str(b.get("code", "")).upper(),))
            return self.out(200, {"ok": True})
        if a == "settings" and m == "GET":
            return self.out(200, {"settings": {k: S(k) for k in DEFAULT_SETTINGS}})
        if a == "settings/save" and m == "POST":
            for k, v in (b.get("settings") or {}).items():
                if k in DEFAULT_SETTINGS:
                    conn.execute("INSERT OR REPLACE INTO settings (key,value) VALUES (?,?)", (k, str(v)[:500]))
            return self.out(200, {"ok": True})
        if a == "backup" and m == "GET":
            conn.commit()
            buf = io.BytesIO()
            with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
                z.write(DB_PATH, "velox_app.db")
            return self.file(buf.getvalue(), f"velox_app_backup_{datetime.now().strftime('%Y%m%d_%H%M%S')}.zip", "application/zip")
        if a == "server" and m == "GET":
            import platform
            ip = ""
            try:
                s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
                s.connect(("8.8.8.8", 80))
                ip = s.getsockname()[0]
                s.close()
            except Exception:
                pass
            return self.out(200, {"server": {"hostname": socket.gethostname(), "local_ip": ip, "port": PORT, "os": f"{platform.system()} {platform.release()}",
                                             "python": platform.python_version(), "db": os.path.abspath(DB_PATH), "db_size": os.path.getsize(DB_PATH)}})
        return self.out(404, {"message": "غير موجود"})


def main():
    init_db()
    srv = ThreadingHTTPServer(("0.0.0.0", PORT), Api)
    srv.daemon_threads = True
    print(f"Velox App server running on port {PORT}  |  db: {DB_PATH}")
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()

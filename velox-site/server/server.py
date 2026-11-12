"""
Velox site — خادم الموقع/التطبيق (Flask).
يقرأ ويكتب في نفس قاعدة بيانات البوت (velox.db) وملفات الخدمات (json)، فتكون كل البيانات حقيقية وواحدة في البوت والتطبيق.
يجب تشغيله على نفس الاستضافة التي فيها البوت (حيث velox.db).

تشغيل:  pip install -r requirements.txt  ثم  python server.py
متغيرات البيئة:
  VELOX_DB         مسار velox.db (الافتراضي: ../../velox.db أي بجانب مجلد الموقع)
  SECRET_KEY       مفتاح توقيع الجلسات (إلزامي في الإنتاج)
  ADMIN_IDS        أيديات المسؤولين مفصولة بفاصلة (الافتراضي 8010837412)
  ADMIN_PASSWORD   كلمة سر حساب المسؤول الأول (الافتراضي 1005 — غيّرها)
  ALLOWED_ORIGINS  دومينات الواجهة المسموحة مفصولة بفاصلة (الافتراضي *)
  GOOGLE_CLIENT_ID لتفعيل الدخول بـ Google
  PORT             المنفذ (الافتراضي 8080)
"""
import io
import json
import os
import random
import re
import sqlite3
import time
import zipfile
from datetime import datetime, timedelta
from functools import wraps

from flask import Flask, jsonify, request, send_file, send_from_directory
from itsdangerous import BadSignature, SignatureExpired, URLSafeTimedSerializer
from werkzeug.security import check_password_hash, generate_password_hash

HERE = os.path.dirname(os.path.abspath(__file__))
SITE = os.path.dirname(HERE)
DB_PATH = os.path.abspath(os.environ.get("VELOX_DB", os.path.join(SITE, "..", "velox.db")))
BOT_DIR = os.path.dirname(DB_PATH)
ADMIN_IDS = [int(x) for x in os.environ.get("ADMIN_IDS", "8010837412").split(",") if x.strip().isdigit()]
ADMIN_PASSWORD = os.environ.get("ADMIN_PASSWORD", "1005")
GOOGLE_CLIENT_ID = os.environ.get("GOOGLE_CLIENT_ID", "")
ALLOWED_ORIGINS = [o.strip() for o in os.environ.get("ALLOWED_ORIGINS", "*").split(",") if o.strip()]
SECRET = os.environ.get("SECRET_KEY") or "change-me-in-production"
CODE_TTL_MIN = 10
TOKEN_TTL = 30 * 24 * 3600

app = Flask(__name__, static_folder=SITE, static_url_path="")
signer = URLSafeTimedSerializer(SECRET, salt="velox-auth")


# ───────────────────────── قاعدة البيانات ─────────────────────────
def db():
    conn = sqlite3.connect(DB_PATH, timeout=15)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA busy_timeout=15000")
    return conn


def migrate():
    conn = db()
    cols = {r["name"] for r in conn.execute("PRAGMA table_info(users)")}
    if cols:
        for name, ddl in [("web_password_hash", "TEXT"), ("web_email", "TEXT"), ("web_google_id", "TEXT"),
                          ("web_name", "TEXT"), ("web_linked", "INTEGER DEFAULT 0")]:
            if name not in cols:
                conn.execute(f"ALTER TABLE users ADD COLUMN {name} {ddl}")
    conn.execute("""CREATE TABLE IF NOT EXISTS web_link_codes (
        code TEXT PRIMARY KEY, user_id INTEGER NOT NULL, created_at TEXT NOT NULL,
        confirmed INTEGER DEFAULT 0, confirmed_at TEXT)""")
    # حساب المسؤول: ينشأ ومربوط تلقائياً بكلمة السر المحددة
    if cols and ADMIN_IDS:
        aid = ADMIN_IDS[0]
        row = conn.execute("SELECT user_id, web_password_hash FROM users WHERE user_id=?", (aid,)).fetchone()
        if not row:
            conn.execute("INSERT INTO users (user_id, first_name, joined_at) VALUES (?,?,?)",
                         (aid, "Admin", datetime.now().strftime("%Y-%m-%d %H:%M:%S")))
            row = {"web_password_hash": None}
        if not row["web_password_hash"] or os.environ.get("ADMIN_PASSWORD"):
            conn.execute("UPDATE users SET web_password_hash=?, web_linked=1, web_name=COALESCE(web_name,'المسؤول') WHERE user_id=?",
                         (generate_password_hash(ADMIN_PASSWORD), aid))
    conn.commit()
    conn.close()


def read_json(name, default):
    try:
        with open(os.path.join(BOT_DIR, name), "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return default


# ───────────────────────── الجلسات (Bearer token) ─────────────────────────
_attempts = {}


def too_many(limit=10):
    ip = request.headers.get("X-Forwarded-For", request.remote_addr or "?").split(",")[0].strip()
    now = time.time()
    hits = [t for t in _attempts.get(ip, []) if now - t < 60]
    hits.append(now)
    _attempts[ip] = hits
    return len(hits) > limit


def make_token(uid):
    return signer.dumps({"uid": int(uid)})


def current_uid():
    h = request.headers.get("Authorization", "")
    tok = h[7:] if h.startswith("Bearer ") else request.args.get("token", "")
    if not tok:
        return None
    try:
        return int(signer.loads(tok, max_age=TOKEN_TTL)["uid"])
    except (BadSignature, SignatureExpired, KeyError, ValueError):
        return None


def err(msg, status=400, **extra):
    return jsonify(dict(message=msg, **extra)), status


def auth(admin=False):
    def deco(fn):
        @wraps(fn)
        def wrapper(*a, **kw):
            uid = current_uid()
            if not uid:
                return err("غير مسجّل", 401)
            conn = db()
            u = conn.execute("SELECT * FROM users WHERE user_id=?", (uid,)).fetchone()
            conn.close()
            if not u:
                return err("غير مسجّل", 401)
            if admin and uid not in ADMIN_IDS:
                return err("صلاحية المسؤول مطلوبة", 403)
            request.user = u
            return fn(*a, **kw)
        return wrapper
    return deco


@app.after_request
def cors(resp):
    origin = request.headers.get("Origin")
    if origin and ("*" in ALLOWED_ORIGINS or origin in ALLOWED_ORIGINS):
        resp.headers["Access-Control-Allow-Origin"] = origin
        resp.headers["Vary"] = "Origin"
        resp.headers["Access-Control-Allow-Headers"] = "Authorization, Content-Type"
        resp.headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS"
    return resp


@app.route("/api/<path:_>", methods=["OPTIONS"])
def preflight(_):
    return ("", 204)


# ───────────────────────── مساعدات ─────────────────────────
def public_user(u, full=False):
    uid = int(u["user_id"])
    d = {
        "uid": str(uid),
        "name": u["web_name"] or u["first_name"] or u["username"] or "مستخدم",
        "username": ("@" + u["username"]) if u["username"] else "",
        "points": int(u["points"] or 0),
        "balance": float(u["balance"] or 0),
        "orders": int(u["orders"] or 0),
        "admin": uid in ADMIN_IDS,
    }
    if full:
        d.update(free_points=int(u["free_points"] or 0), paid_points=int(u["paid_points"] or 0),
                 spent_points=int(u["spent_points"] or 0), referrals=int(u["referrals"] or 0),
                 joined_at=u["joined_at"] or "", email=u["web_email"] or "", google=bool(u["web_google_id"]))
    return d


def total_orders(conn):
    r = conn.execute("SELECT value FROM stats WHERE key='total_orders'").fetchone()
    return int(r[0]) if r else 0


def new_code(conn, uid):
    for _ in range(30):
        code = f"VX-{random.randint(10000, 99999)}"
        if not conn.execute("SELECT 1 FROM web_link_codes WHERE code=?", (code,)).fetchone():
            conn.execute("INSERT INTO web_link_codes (code,user_id,created_at) VALUES (?,?,?)",
                         (code, uid, datetime.now().isoformat(timespec="seconds")))
            conn.commit()
            return code
    raise RuntimeError("code generation failed")


def user_orders(conn, uid=None, limit=200):
    where, args = ("WHERE user_id=?", (uid,)) if uid else ("", ())
    out = []
    q = [
        ("orders", f"SELECT order_id, service AS title, price, status, created_at, quantity, link FROM orders {where}", "خدمة"),
        ("game", f"SELECT order_id, (category || ' - ' || service_name) AS title, price, status, created_at, quantity, '' AS link FROM game_orders {where}", "ألعاب"),
        ("rashq", f"SELECT order_id, (platform || ' - ' || service_name) AS title, price, status, created_at, quantity, link FROM rashq_orders {where}", "رشق"),
        ("tamweel", f"SELECT order_id, ('تمويل قناة ' || COALESCE(channel_name,'') || ' (' || members || ' عضو)') AS title, price, status, created_at, members AS quantity, channel_link AS link FROM tamweel_orders {where}", "تمويل"),
    ]
    for key, sql, label in q:
        try:
            for r in conn.execute(sql, args):
                out.append({"id": r["order_id"], "kind": label, "table": key, "title": r["title"], "price": r["price"],
                            "status": r["status"], "created_at": r["created_at"], "quantity": r["quantity"], "link": r["link"]})
        except sqlite3.Error:
            pass
    out.sort(key=lambda o: str(o["created_at"] or ""), reverse=True)
    return out[:limit]


# ───────────────────────── الحساب ─────────────────────────
@app.post("/api/register")
def register():
    if too_many():
        return err("محاولات كثيرة، انتظر دقيقة", 429)
    d = request.get_json(silent=True) or {}
    name = str(d.get("name", "")).strip()[:40]
    uid = str(d.get("id", "")).strip()
    pw = str(d.get("password", ""))
    email = str(d.get("email", "")).strip().lower()[:120]
    if len(name) < 2:
        return err("اكتب اسمك")
    if not re.fullmatch(r"\d{5,15}", uid):
        return err("الأيدي أرقام فقط")
    if len(pw) < 6:
        return err("كلمة السر 6 أحرف على الأقل")
    if email and not re.fullmatch(r"[^@\s]+@[^@\s]+\.[^@\s]+", email):
        return err("البريد الإلكتروني غير صحيح")
    uid = int(uid)
    conn = db()
    try:
        row = conn.execute("SELECT * FROM users WHERE user_id=?", (uid,)).fetchone()
        if row and row["web_password_hash"]:
            return err("هذا الأيدي مسجّل بالفعل، سجّل الدخول", 409)
        if not row:
            conn.execute("INSERT INTO users (user_id, first_name, joined_at) VALUES (?,?,?)",
                         (uid, name, datetime.now().strftime("%Y-%m-%d %H:%M:%S")))
        conn.execute("UPDATE users SET web_password_hash=?, web_email=?, web_name=?, web_linked=0 WHERE user_id=?",
                     (generate_password_hash(pw), email or None, name, uid))
        conn.commit()
        return jsonify(ok=True, link_code=new_code(conn, uid))
    finally:
        conn.close()


@app.post("/api/link/check")
def link_check():
    if too_many(30):
        return err("محاولات كثيرة، انتظر دقيقة", 429)
    code = str((request.get_json(silent=True) or {}).get("code", "")).strip().upper()
    conn = db()
    try:
        row = conn.execute("SELECT * FROM web_link_codes WHERE code=?", (code,)).fetchone()
        if not row:
            return err("كود غير صحيح", 404)
        age = datetime.now() - datetime.fromisoformat(row["created_at"])
        if not row["confirmed"]:
            if age > timedelta(minutes=CODE_TTL_MIN):
                return err("انتهت صلاحية الكود، سجّل الدخول لإنشاء كود جديد", 410)
            return jsonify(linked=False)
        if age > timedelta(minutes=2 * CODE_TTL_MIN):
            return err("انتهت صلاحية الكود، سجّل الدخول", 410)
        u = conn.execute("SELECT * FROM users WHERE user_id=?", (row["user_id"],)).fetchone()
        return jsonify(linked=True, token=make_token(u["user_id"]), user=public_user(u))
    finally:
        conn.close()


@app.post("/api/login")
def login():
    if too_many():
        return err("محاولات كثيرة، انتظر دقيقة", 429)
    d = request.get_json(silent=True) or {}
    uid = str(d.get("id", "")).strip()
    pw = str(d.get("password", ""))
    if not uid.isdigit():
        return err("الأيدي أو كلمة السر غير صحيحة", 401)
    conn = db()
    try:
        u = conn.execute("SELECT * FROM users WHERE user_id=?", (int(uid),)).fetchone()
        if not u or not u["web_password_hash"] or not check_password_hash(u["web_password_hash"], pw):
            return err("الأيدي أو كلمة السر غير صحيحة", 401)
        if not u["web_linked"]:
            return err("الحساب غير مربوط بالبوت بعد", 403, link_code=new_code(conn, int(u["user_id"])))
        return jsonify(ok=True, token=make_token(u["user_id"]), user=public_user(u))
    finally:
        conn.close()


@app.post("/api/google")
def google_login():
    if not GOOGLE_CLIENT_ID:
        return err("Google غير مفعّل على الخادم", 501)
    try:
        from google.auth.transport import requests as g_req
        from google.oauth2 import id_token
        info = id_token.verify_oauth2_token((request.get_json(silent=True) or {}).get("credential", ""),
                                            g_req.Request(), GOOGLE_CLIENT_ID)
    except Exception:
        return err("تعذّر التحقق من حساب Google", 401)
    sub, mail = info.get("sub"), (info.get("email") or "").lower()
    conn = db()
    try:
        u = conn.execute("SELECT * FROM users WHERE web_google_id=?", (sub,)).fetchone()
        if not u and mail and info.get("email_verified"):
            u = conn.execute("SELECT * FROM users WHERE lower(web_email)=? AND web_linked=1", (mail,)).fetchone()
            if u:
                conn.execute("UPDATE users SET web_google_id=? WHERE user_id=?", (sub, u["user_id"]))
                conn.commit()
        if not u:
            return err("لا يوجد حساب مرتبط بهذا الـ Google. أنشئ حساباً أولاً ثم اربطه.", 404)
        return jsonify(ok=True, token=make_token(u["user_id"]), user=public_user(u))
    finally:
        conn.close()


@app.get("/api/me")
@auth()
def me():
    conn = db()
    try:
        d = public_user(request.user, full=True)
        d["total_orders"] = total_orders(conn)
        return jsonify(user=d)
    finally:
        conn.close()


@app.get("/api/stats")
def stats_public():
    conn = db()
    try:
        return jsonify(total_orders=total_orders(conn))
    except sqlite3.Error:
        return jsonify(total_orders=0)
    finally:
        conn.close()


# ───────────────────────── بيانات المستخدم ─────────────────────────
@app.get("/api/orders")
@auth()
def my_orders():
    conn = db()
    try:
        return jsonify(orders=user_orders(conn, int(request.user["user_id"])))
    finally:
        conn.close()


@app.get("/api/track")
@auth()
def track():
    oid = str(request.args.get("id", "")).strip().lstrip("#")
    if not oid.isdigit():
        return err("اكتب رقم الطلب")
    conn = db()
    try:
        uid = int(request.user["user_id"])
        rows = user_orders(conn, None if uid in ADMIN_IDS else uid, limit=100000)
        for o in rows:
            if str(o["id"]) == oid:
                return jsonify(order=o)
        return err("لم يتم العثور على الطلب", 404)
    finally:
        conn.close()


@app.get("/api/recharges")
@auth()
def my_recharges():
    conn = db()
    try:
        rows = conn.execute("SELECT points, method, note, created_at FROM recharge_log WHERE user_id=? ORDER BY id DESC LIMIT 50",
                            (int(request.user["user_id"]),)).fetchall()
        return jsonify(recharges=[dict(r) for r in rows])
    finally:
        conn.close()


@app.get("/api/tamweel")
@auth()
def my_tamweel():
    conn = db()
    try:
        rows = conn.execute("SELECT order_id, members, price, channel_link, channel_name, channel_members, status, created_at FROM tamweel_orders WHERE user_id=? ORDER BY order_id DESC",
                            (int(request.user["user_id"]),)).fetchall()
        return jsonify(items=[dict(r) for r in rows])
    finally:
        conn.close()


@app.get("/api/services/rashq")
@auth()
def services_rashq():
    data = read_json("velox_rashq_services.json", {})
    p = request.args.get("platform")
    if p:
        return jsonify(services=[dict(s, index=i) for i, s in enumerate(data.get(p, []))])
    return jsonify(platforms={k: len(v) for k, v in data.items()})


@app.get("/api/services/games")
@auth()
def services_games():
    data = read_json("velox_services.json", {})
    c = request.args.get("category")
    if c:
        return jsonify(services=[dict(s, index=i) for i, s in enumerate(data.get(c, []))])
    return jsonify(categories={k: len(v) for k, v in data.items()})


# ───────────────────────── المسؤول ─────────────────────────
@app.get("/api/admin/stats")
@auth(admin=True)
def admin_stats():
    conn = db()
    try:
        def c(sql):
            try:
                return conn.execute(sql).fetchone()[0]
            except sqlite3.Error:
                return 0
        return jsonify(
            users=c("SELECT COUNT(*) FROM users"),
            web_users=c("SELECT COUNT(*) FROM users WHERE web_password_hash IS NOT NULL"),
            total_orders=total_orders(conn),
            rashq_orders=c("SELECT COUNT(*) FROM rashq_orders"),
            game_orders=c("SELECT COUNT(*) FROM game_orders"),
            tamweel_orders=c("SELECT COUNT(*) FROM tamweel_orders"),
            recharges=c("SELECT COUNT(*) FROM recharge_log"),
            recharge_points=c("SELECT COALESCE(SUM(points),0) FROM recharge_log"),
        )
    finally:
        conn.close()


@app.get("/api/admin/users")
@auth(admin=True)
def admin_users():
    q = str(request.args.get("q", "")).strip()
    conn = db()
    try:
        if q:
            like = f"%{q}%"
            rows = conn.execute("SELECT * FROM users WHERE CAST(user_id AS TEXT) LIKE ? OR username LIKE ? OR first_name LIKE ? ORDER BY joined_at DESC LIMIT 100", (like, like, like)).fetchall()
        else:
            rows = conn.execute("SELECT * FROM users ORDER BY joined_at DESC LIMIT 100").fetchall()
        users = []
        for r in rows:
            d = public_user(r)
            d["web"] = bool(r["web_password_hash"])
            users.append(d)
        return jsonify(users=users, total=conn.execute("SELECT COUNT(*) FROM users").fetchone()[0])
    finally:
        conn.close()


@app.get("/api/admin/recharges")
@auth(admin=True)
def admin_recharges():
    conn = db()
    try:
        rows = conn.execute("SELECT user_id, points, method, note, created_at FROM recharge_log ORDER BY id DESC LIMIT 100").fetchall()
        return jsonify(recharges=[dict(r) for r in rows])
    finally:
        conn.close()


@app.get("/api/admin/orders")
@auth(admin=True)
def admin_orders():
    conn = db()
    try:
        return jsonify(orders=user_orders(conn, None, limit=100))
    finally:
        conn.close()


@app.get("/api/admin/db")
@auth(admin=True)
def admin_db():
    conn = db()
    try:
        tables = []
        for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name"):
            n = conn.execute(f'SELECT COUNT(*) FROM "{r[0]}"').fetchone()[0]
            tables.append({"name": r[0], "rows": n})
        return jsonify(path=DB_PATH, size=os.path.getsize(DB_PATH), tables=tables)
    finally:
        conn.close()


@app.get("/api/admin/backups")
@auth(admin=True)
def admin_backups():
    out = []
    for f in sorted(os.listdir(BOT_DIR)):
        if f.startswith("velox_backup_") and f.endswith(".db"):
            p = os.path.join(BOT_DIR, f)
            out.append({"name": f, "size": os.path.getsize(p), "mtime": datetime.fromtimestamp(os.path.getmtime(p)).strftime("%Y-%m-%d %H:%M")})
    return jsonify(backups=out[::-1])


@app.get("/api/admin/backups/download")
@auth(admin=True)
def admin_backup_download():
    name = os.path.basename(request.args.get("name", ""))
    p = os.path.join(BOT_DIR, name)
    if not (name.startswith("velox_backup_") and name.endswith(".db") and os.path.isfile(p)):
        return err("الملف غير موجود", 404)
    return send_file(p, as_attachment=True, download_name=name)


SKIP_DIRS = {"__pycache__", ".git", "node_modules", ".cache", "venv", ".venv", ".local", ".npm"}


def hosting_files(mode):
    out = []
    if mode == "all":
        for dp, dns, fns in os.walk(BOT_DIR):
            dns[:] = [d for d in dns if d not in SKIP_DIRS]
            for fn in fns:
                p = os.path.join(dp, fn)
                if fn.startswith("velox_hosting_") or os.path.islink(p) or not os.path.isfile(p):
                    continue
                out.append((p, os.path.relpath(p, BOT_DIR)))
    else:
        for fn in sorted(os.listdir(BOT_DIR)):
            p = os.path.join(BOT_DIR, fn)
            if os.path.isfile(p) and not os.path.islink(p) and fn.lower().endswith((".py", ".db", ".json", ".txt", ".env", ".sqlite", ".sqlite3")):
                out.append((p, fn))
    return out


@app.get("/api/admin/hosting")
@auth(admin=True)
def admin_hosting():
    res = {}
    for mode in ("all", "main"):
        files = hosting_files(mode)
        res[mode] = {"files": len(files), "size": sum(os.path.getsize(p) for p, _ in files)}
    return jsonify(res)


@app.get("/api/admin/hosting/zip")
@auth(admin=True)
def admin_hosting_zip():
    mode = "all" if request.args.get("mode") == "all" else "main"
    buf = io.BytesIO()
    with zipfile.ZipFile(buf, "w", zipfile.ZIP_DEFLATED) as z:
        for p, arc in hosting_files(mode):
            try:
                z.write(p, arc)
            except OSError:
                pass
    buf.seek(0)
    return send_file(buf, mimetype="application/zip", as_attachment=True,
                     download_name=f"velox_hosting_{mode}_{datetime.now().strftime('%Y%m%d_%H%M%S')}.zip")


@app.get("/api/admin/config")
@auth(admin=True)
def admin_config():
    cfg = read_json("velox_config.json", {})
    safe = {k: v for k, v in cfg.items() if isinstance(v, (str, int, float, bool)) and not re.search(r"token|key|secret|pass", k, re.I)}
    return jsonify(config=safe)


@app.get("/")
def index():
    return send_from_directory(SITE, "index.html")


migrate()

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", "8080")))

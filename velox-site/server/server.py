"""
Velox site — خادم الموقع (Flask).
يقرأ ويكتب في نفس قاعدة بيانات البوت (velox.db)، فيكون رصيدك ونقاطك وطلباتك واحدة في الموقع والبوت.
يشغَّل على نفس الاستضافة التي فيها velox.db.

تشغيل:  pip install -r requirements.txt  ثم  python server.py
متغيرات البيئة:  VELOX_DB, SECRET_KEY, ADMIN_IDS, GOOGLE_CLIENT_ID, PORT
"""
import os
import re
import random
import sqlite3
import time
from datetime import datetime, timedelta

from flask import Flask, jsonify, request, session, send_from_directory
from werkzeug.security import generate_password_hash, check_password_hash

HERE = os.path.dirname(os.path.abspath(__file__))
SITE = os.path.dirname(HERE)
DB_PATH = os.environ.get("VELOX_DB", os.path.join(SITE, "..", "velox.db"))
ADMIN_IDS = {int(x) for x in os.environ.get("ADMIN_IDS", "8010837412").split(",") if x.strip().isdigit()}
GOOGLE_CLIENT_ID = os.environ.get("GOOGLE_CLIENT_ID", "")
CODE_TTL_MIN = 10

app = Flask(__name__, static_folder=SITE, static_url_path="")
app.secret_key = os.environ.get("SECRET_KEY") or "change-me-in-production"
app.config.update(SESSION_COOKIE_HTTPONLY=True, SESSION_COOKIE_SAMESITE="Lax",
                  PERMANENT_SESSION_LIFETIME=timedelta(days=30))


def db():
    conn = sqlite3.connect(DB_PATH, timeout=15)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA busy_timeout=15000")
    return conn


def migrate():
    """يضيف أعمدة الموقع وجدول أكواد الربط دون المساس ببيانات البوت."""
    conn = db()
    cols = {r["name"] for r in conn.execute("PRAGMA table_info(users)")}
    for name, ddl in [("web_password_hash", "TEXT"), ("web_email", "TEXT"), ("web_google_id", "TEXT"),
                      ("web_name", "TEXT"), ("web_linked", "INTEGER DEFAULT 0")]:
        if cols and name not in cols:
            conn.execute(f"ALTER TABLE users ADD COLUMN {name} {ddl}")
    conn.execute("""CREATE TABLE IF NOT EXISTS web_link_codes (
        code TEXT PRIMARY KEY, user_id INTEGER NOT NULL, created_at TEXT NOT NULL,
        confirmed INTEGER DEFAULT 0, confirmed_at TEXT)""")
    conn.commit()
    conn.close()


# محاولات الدخول: 8 كل دقيقة لكل IP
_attempts = {}


def too_many():
    ip = request.headers.get("X-Forwarded-For", request.remote_addr or "?").split(",")[0].strip()
    now = time.time()
    hits = [t for t in _attempts.get(ip, []) if now - t < 60]
    hits.append(now)
    _attempts[ip] = hits
    return len(hits) > 8


def public_user(row):
    uid = int(row["user_id"])
    return {
        "uid": str(uid),
        "name": row["web_name"] or row["first_name"] or row["username"] or "مستخدم",
        "points": int(row["points"] or 0),
        "balance": float(row["balance"] or 0),
        "orders": int(row["orders"] or 0),
        "admin": uid in ADMIN_IDS,
    }


def new_code(conn, uid):
    for _ in range(20):
        code = f"VX-{random.randint(10000, 99999)}"
        if not conn.execute("SELECT 1 FROM web_link_codes WHERE code=?", (code,)).fetchone():
            conn.execute("INSERT INTO web_link_codes (code,user_id,created_at) VALUES (?,?,?)",
                         (code, uid, datetime.now().isoformat(timespec="seconds")))
            conn.commit()
            return code
    raise RuntimeError("code generation failed")


def err(msg, status=400, **extra):
    return jsonify(dict(message=msg, **extra)), status


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
    if too_many():
        return err("محاولات كثيرة، انتظر دقيقة", 429)
    d = request.get_json(silent=True) or {}
    code = str(d.get("code", "")).strip().upper()
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
        session.permanent = True
        session["uid"] = int(u["user_id"])
        return jsonify(linked=True, user=public_user(u))
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
        session.permanent = True
        session["uid"] = int(u["user_id"])
        return jsonify(ok=True, user=public_user(u))
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
        session.permanent = True
        session["uid"] = int(u["user_id"])
        return jsonify(ok=True, user=public_user(u))
    finally:
        conn.close()


@app.get("/api/me")
def me():
    uid = session.get("uid")
    if not uid:
        return err("غير مسجّل", 401)
    conn = db()
    try:
        u = conn.execute("SELECT * FROM users WHERE user_id=?", (uid,)).fetchone()
        if not u:
            session.clear()
            return err("غير مسجّل", 401)
        return jsonify(user=public_user(u))
    finally:
        conn.close()


@app.post("/api/logout")
def logout():
    session.clear()
    return jsonify(ok=True)


@app.get("/")
def index():
    return send_from_directory(SITE, "index.html")


migrate()

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", "8080")))

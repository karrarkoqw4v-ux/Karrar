"""
هذا هو الكود الذي أُضيف إلى main.py (الملف الجاهز main.py مرفق وفيه كل شيء).
استخدم هذا الملف فقط إذا أردت إضافته يدوياً إلى نسخة أخرى من البوت:
  1) الصق الكود *قبل* دالة cmd_start (قبل @bot.message_handler(commands=["start"])).
  2) داخل cmd_start، بعد سطر  user = get_user(uid, ...)  أضف:

    if len(args) > 1 and args[1] == "weblink":
        _web_ensure_schema()
        user_states[uid] = "web_wait_code"
        bot.send_message(uid, "🔐 <b>ربط التطبيق</b>\n\nأرسل لي الرمز الذي ظهر لك في التطبيق\nمثال: <code>VX-12345</code>", parse_mode="HTML")
        return
"""
# ─────────────────────────────────────────────────────────────
# 🔗  ربط التطبيق/الموقع بالبوت (التحقق بالرمز)
# ─────────────────────────────────────────────────────────────
def _web_ensure_schema():
    conn = get_conn()
    try:
        cols = {r[1] for r in conn.execute("PRAGMA table_info(users)")}
        for name, ddl in (("web_password_hash", "TEXT"), ("web_email", "TEXT"), ("web_google_id", "TEXT"),
                          ("web_name", "TEXT"), ("web_linked", "INTEGER DEFAULT 0")):
            if name not in cols:
                try: conn.execute(f"ALTER TABLE users ADD COLUMN {name} {ddl}")
                except Exception: pass
        conn.execute("""CREATE TABLE IF NOT EXISTS web_link_codes (
            code TEXT PRIMARY KEY, user_id INTEGER NOT NULL, created_at TEXT NOT NULL,
            confirmed INTEGER DEFAULT 0, confirmed_at TEXT)""")
        conn.commit()
    finally:
        conn.close()

def _web_code_row(code):
    conn = get_conn()
    try:
        return conn.execute("SELECT user_id, created_at, confirmed FROM web_link_codes WHERE code=?", (code,)).fetchone()
    finally:
        conn.close()

def _web_code_valid(row, uid):
    try:
        return bool(row) and int(row[0]) == int(uid) and not row[2] \
            and datetime.now() - datetime.fromisoformat(row[1]) < timedelta(minutes=10)
    except Exception:
        return False

@bot.message_handler(content_types=["text"], func=lambda m: bool(m.text) and (user_states.get(m.from_user.id) == "web_wait_code" or re.fullmatch(r"VX-\d{5}", m.text.strip().upper()) is not None))
def web_link_code_msg(message):
    uid = message.from_user.id
    code = (message.text or "").strip().upper()
    if not re.fullmatch(r"VX-\d{5}", code):
        bot.send_message(uid, "❌ الرمز غير صحيح.\nأرسل الرمز بهذا الشكل: VX-12345")
        return
    _web_ensure_schema()
    row = _web_code_row(code)
    if not row:
        bot.send_message(uid, "❌ الرمز غير موجود في التطبيق. تأكد منه أو أنشئ رمزاً جديداً.")
        return
    if int(row[0]) != int(uid):
        bot.send_message(uid, "❌ هذا الرمز لا يخص حسابك.")
        return
    if not _web_code_valid(row, uid):
        bot.send_message(uid, "⌛ انتهت صلاحية الرمز. أنشئ رمزاً جديداً من التطبيق.")
        return
    user_states.pop(uid, None)
    kb = InlineKeyboardMarkup()
    kb.row(InlineKeyboardButton(text="✅ نعم، تحقق", style=STYLE_GREEN, callback_data=f"web_ok_{code}"),
           InlineKeyboardButton(text="❌ لا", style=STYLE_RED, callback_data=f"web_no_{code}"))
    bot.send_message(uid, f"🔐 <b>تأكيد ربط التطبيق</b>\n\nالرمز: <code>{code}</code>\n\nهل أنت من طلب ربط حسابك بالتطبيق؟\nاضغط <b>نعم، تحقق</b> لإتمام التحقق.", parse_mode="HTML", reply_markup=kb)

@bot.callback_query_handler(func=lambda c: (c.data or "").startswith("web_ok_") or (c.data or "").startswith("web_no_"))
def web_link_confirm_cb(call):
    uid = call.from_user.id
    ok = call.data.startswith("web_ok_")
    code = call.data.split("_", 2)[2].upper()
    row = _web_code_row(code)
    if not _web_code_valid(row, uid):
        bot.answer_callback_query(call.id, "الرمز غير صالح أو انتهت صلاحيته", show_alert=True)
        return
    conn = get_conn()
    try:
        if ok:
            conn.execute("UPDATE web_link_codes SET confirmed=1, confirmed_at=? WHERE code=?", (datetime.now().isoformat(timespec="seconds"), code))
            conn.execute("UPDATE users SET web_linked=1 WHERE user_id=?", (uid,))
        else:
            conn.execute("DELETE FROM web_link_codes WHERE code=?", (code,))
        conn.commit()
    finally:
        conn.close()
    try: bot.edit_message_reply_markup(call.message.chat.id, call.message.message_id, reply_markup=None)
    except Exception: pass
    if ok:
        bot.answer_callback_query(call.id, "تم التحقق")
        bot.send_message(uid, "✅ <b>تم التحقق</b>\n\nتمت عملية التحقق من أنك موجود في التطبيق.\nعد إلى التطبيق وسيظهر لك رصيدك وطلباتك وكل تفاصيل حسابك.", parse_mode="HTML")
    else:
        bot.answer_callback_query(call.id, "تم الإلغاء")
        bot.send_message(uid, "تم إلغاء طلب الربط.")


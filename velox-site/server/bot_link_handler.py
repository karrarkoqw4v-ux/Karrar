"""
أضف هذا المعالج إلى main.py (ملف البوت) *قبل* أي معالج نصوص عام.
عندما يرسل المستخدم الكود VX-12345 للبوت، يتم ربط حسابه بالموقع.
يستخدم get_conn و bot الموجودين أصلاً في main.py.
"""
import re
from datetime import datetime, timedelta


@bot.message_handler(content_types=['text'],
                     func=lambda m: bool(m.text) and re.fullmatch(r'VX-\d{5}', m.text.strip().upper()))
def web_link_confirm(m):
    code = m.text.strip().upper()
    conn = get_conn()
    try:
        row = conn.execute("SELECT user_id, created_at, confirmed FROM web_link_codes WHERE code=?", (code,)).fetchone()
        ok = bool(row) and int(row[0]) == m.from_user.id and not row[2] \
            and datetime.now() - datetime.fromisoformat(row[1]) < timedelta(minutes=10)
        if ok:
            conn.execute("UPDATE web_link_codes SET confirmed=1, confirmed_at=? WHERE code=?",
                         (datetime.now().isoformat(timespec='seconds'), code))
            conn.execute("UPDATE users SET web_linked=1 WHERE user_id=?", (m.from_user.id,))
            conn.commit()
            bot.reply_to(m, "✅ تم ربط حسابك بالموقع بنجاح.")
        else:
            bot.reply_to(m, "❌ الكود غير صحيح أو منتهي، أنشئ كوداً جديداً من الموقع.")
    finally:
        conn.close()

package com.turbo.rashq

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class User(val id: Long, val uid: String, val name: String, val username: String, val email: String,
                val phone: String, val balance: Long, val banned: Boolean, val admin: Boolean)
data class Service(val id: Long, val cat: String, val name: String, val price: Long, val kind: String,
                   val minQty: Long, val hidden: Boolean)
data class Order(val id: Long, val uid: String, val title: String, val target: String, val qty: Long,
                 val price: Long, val status: String, val ts: Long, val userName: String)
data class Notif(val id: Long, val title: String, val body: String, val ts: Long, val read: Boolean, val type: String = "sys")
data class CodeRow(val code: String, val amount: Long, val maxUses: Int, val used: Int)
data class Ledger(val delta: Long, val note: String, val ts: Long)

class Db(ctx: Context) : SQLiteOpenHelper(ctx, "turbo.db", null, 2) {
    override fun onCreate(d: SQLiteDatabase) {
        d.execSQL("CREATE TABLE users(id INTEGER PRIMARY KEY AUTOINCREMENT, uid TEXT UNIQUE, name TEXT, username TEXT UNIQUE, email TEXT, phone TEXT, pass TEXT, balance INTEGER DEFAULT 0, banned INTEGER DEFAULT 0, admin INTEGER DEFAULT 0, created INTEGER)")
        d.execSQL("CREATE TABLE services(id INTEGER PRIMARY KEY AUTOINCREMENT, cat TEXT, name TEXT, price INTEGER, kind TEXT, minq INTEGER DEFAULT 1, hidden INTEGER DEFAULT 0)")
        d.execSQL("CREATE TABLE orders(id INTEGER PRIMARY KEY AUTOINCREMENT, uid TEXT, title TEXT, target TEXT, qty INTEGER, price INTEGER, status TEXT, ts INTEGER)")
        d.execSQL("CREATE TABLE codes(code TEXT PRIMARY KEY, amount INTEGER, maxuses INTEGER, used INTEGER DEFAULT 0)")
        d.execSQL("CREATE TABLE code_uses(code TEXT, uid TEXT)")
        d.execSQL("CREATE TABLE ledger(id INTEGER PRIMARY KEY AUTOINCREMENT, uid TEXT, delta INTEGER, note TEXT, ts INTEGER)")
        d.execSQL("CREATE TABLE settings(k TEXT PRIMARY KEY, v TEXT)")
        d.execSQL("CREATE TABLE notifs(id INTEGER PRIMARY KEY AUTOINCREMENT, uid TEXT, title TEXT, body TEXT, ts INTEGER, read INTEGER DEFAULT 0, shown INTEGER DEFAULT 0, type TEXT DEFAULT 'sys')")
        d.execSQL("CREATE TABLE emoji(id TEXT PRIMARY KEY, path TEXT, kind TEXT, setname TEXT)")
        // developer account
        val cv = ContentValues().apply {
            put("uid", "8010837412"); put("name", "المطور"); put("username", "admin"); put("email", "admin@velox.app")
            put("phone", ""); put("pass", Security.hash("1005")); put("balance", 0); put("admin", 1)
            put("created", System.currentTimeMillis())
        }
        d.insert("users", null, cv)
    }
    override fun onUpgrade(d: SQLiteDatabase, o: Int, n: Int) { if (o < 2) d.execSQL("ALTER TABLE notifs ADD COLUMN type TEXT DEFAULT 'sys'") }
}

object Security {
    private val rnd = SecureRandom()
    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun unhex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    private fun derive(p: String, salt: ByteArray): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(PBEKeySpec(p.toCharArray(), salt, 10000, 256)).encoded
    fun hash(p: String): String { val s = ByteArray(16); rnd.nextBytes(s); return hex(s) + ":" + hex(derive(p, s)) }
    fun verify(p: String, stored: String): Boolean {
        val parts = stored.split(":"); if (parts.size != 2) return false
        return MessageDigest.isEqual(derive(p, unhex(parts[0])), unhex(parts[1]))
    }
}

object Repo {
    lateinit var ctx: Context
    lateinit var db: Db
    lateinit var sp: android.content.SharedPreferences
    fun init(c: Context) { ctx = c.applicationContext; db = Db(ctx); sp = ctx.getSharedPreferences("turbo", 0) }
    private val w get() = db.writableDatabase

    // ---- settings
    fun get(k: String, def: String = ""): String =
        w.rawQuery("SELECT v FROM settings WHERE k=?", arrayOf(k)).use { if (it.moveToFirst()) it.getString(0) else def }
    fun put(k: String, v: String) { w.execSQL("INSERT OR REPLACE INTO settings(k,v) VALUES(?,?)", arrayOf(k, v)) }
    fun del(k: String) { w.execSQL("DELETE FROM settings WHERE k=?", arrayOf(k)) }
    fun getLong(k: String, def: Long) = get(k, def.toString()).toLongOrNull() ?: def

    fun accent(uid: String) = androidx.compose.ui.graphics.Color(prefStr(uid, "accent", 0xFF2563EBL.toString()).toLong())
    fun bubble(uid: String) = androidx.compose.ui.graphics.Color(prefStr(uid, "bubble", 0xFF2563EBL.toString()).toLong())
    fun cur() = get("currency", "نقطة")
    fun pref(uid: String, k: String, def: Boolean = true) = get("u_${uid}_$k", if (def) "1" else "0") == "1"
    fun setPref(uid: String, k: String, v: Boolean) = put("u_${uid}_$k", if (v) "1" else "0")
    fun prefStr(uid: String, k: String, def: String) = get("u_${uid}_$k", def)

    // ---- users
    private fun user(c: Cursor) = User(c.getLong(0), c.getString(1), c.getString(2) ?: "", c.getString(3) ?: "",
        c.getString(4) ?: "", c.getString(5) ?: "", c.getLong(6), c.getInt(7) == 1, c.getInt(8) == 1)
    private const val UCOLS = "id,uid,name,username,email,phone,balance,banned,admin"
    fun userByUid(uid: String): User? = w.rawQuery("SELECT $UCOLS FROM users WHERE uid=?", arrayOf(uid)).use { if (it.moveToFirst()) user(it) else null }
    fun userByUsername(u: String): User? = w.rawQuery("SELECT $UCOLS FROM users WHERE lower(username)=lower(?)", arrayOf(u.trim().removePrefix("@"))).use { if (it.moveToFirst()) user(it) else null }
    fun users(q: String = ""): List<User> {
        val like = "%$q%"
        return w.rawQuery("SELECT $UCOLS FROM users WHERE name LIKE ? OR username LIKE ? OR uid LIKE ? ORDER BY id DESC LIMIT 200", arrayOf(like, like, like)).use { c -> buildList { while (c.moveToNext()) add(user(c)) } }
    }
    fun userCount() = w.rawQuery("SELECT COUNT(*) FROM users", null).use { it.moveToFirst(); it.getLong(0) }

    fun register(name: String, username: String, phone: String, email: String, pass: String, ref: String = ""): String? {
        if (name.isBlank() || username.isBlank() || pass.length < 4) return "املأ الاسم واسم المستخدم وكلمة سر (4 أحرف على الأقل)"
        val un = username.trim().removePrefix("@")
        if (!Regex("[A-Za-z0-9_]{3,20}").matches(un)) return "اسم المستخدم: حروف إنجليزية وأرقام و _ (3-20)"
        if (userByUsername(un) != null) return "اسم المستخدم مستخدم"
        var uid: String
        do { uid = (100000000L + java.util.Random().nextInt(899999999)).toString() } while (userByUid(uid) != null)
        val cv = ContentValues().apply {
            put("uid", uid); put("name", name.trim()); put("username", un); put("email", email.trim()); put("phone", phone.trim())
            put("pass", Security.hash(pass)); put("balance", 0); put("created", System.currentTimeMillis())
        }
        w.insert("users", null, cv)
        setCurrent(uid)
        if (ref.isNotBlank()) userByUid(ref.trim())?.let { inv ->
            val pts = getLong("referral_points", 200)
            addBalance(inv.uid, pts, "دعوة ${name.trim()}")
            pushNotif(inv.uid, "🎉 دعوة جديدة", "سجّل ${name.trim()} برمز دعوتك وأضفنا لك $pts ${cur()}", "gift")
        }
        return null
    }
    fun login(id: String, pass: String): String? {
        val q = id.trim().removePrefix("@")
        val c = w.rawQuery("SELECT uid,pass,banned FROM users WHERE uid=? OR lower(username)=lower(?) OR lower(email)=lower(?)", arrayOf(q, q, q))
        c.use {
            if (!it.moveToFirst()) return "الحساب غير موجود"
            if (it.getInt(2) == 1) return "هذا الحساب محظور"
            if (!Security.verify(pass, it.getString(1))) return "كلمة السر غير صحيحة"
            setCurrent(it.getString(0)); return null
        }
    }
    fun setCurrent(uid: String?) { sp.edit().apply { if (uid == null) remove("uid") else putString("uid", uid) }.apply() }
    fun currentUid(): String? = sp.getString("uid", null)
    fun current(): User? = currentUid()?.let { userByUid(it) }
    fun changePass(uid: String, old: String, new: String): String? {
        val st = w.rawQuery("SELECT pass FROM users WHERE uid=?", arrayOf(uid)).use { if (it.moveToFirst()) it.getString(0) else "" }
        if (!Security.verify(old, st)) return "كلمة السر الحالية خاطئة"
        if (new.length < 4) return "كلمة السر قصيرة"
        w.execSQL("UPDATE users SET pass=? WHERE uid=?", arrayOf(Security.hash(new), uid)); return null
    }
    fun changeUsername(uid: String, nu: String): String? {
        val un = nu.trim().removePrefix("@")
        if (!Regex("[A-Za-z0-9_]{3,20}").matches(un)) return "اسم المستخدم: حروف إنجليزية وأرقام و _ (3-20)"
        val ex = userByUsername(un); if (ex != null && ex.uid != uid) return "اسم المستخدم مستخدم"
        w.execSQL("UPDATE users SET username=? WHERE uid=?", arrayOf(un, uid)); return null
    }
    fun changeEmail(uid: String, e: String): String? { w.execSQL("UPDATE users SET email=? WHERE uid=?", arrayOf(e.trim(), uid)); return null }
    fun changeName(uid: String, n: String): String? { if (n.isBlank()) return "اكتب الاسم"; w.execSQL("UPDATE users SET name=? WHERE uid=?", arrayOf(n.trim(), uid)); return null }
    fun changePhone(uid: String, p: String): String? { w.execSQL("UPDATE users SET phone=? WHERE uid=?", arrayOf(p.trim(), uid)); return null }
    fun setBalance(uid: String, v: Long) { val cur = userByUid(uid)?.balance ?: 0; addBalance(uid, v - cur, "تصفير/ضبط من المطور") }
    fun resetAllBalances() { w.execSQL("UPDATE users SET balance=0") }
    fun sendMessage(from: User, toUsername: String, text: String): String? {
        val to = userByUsername(toUsername) ?: return "المستخدم غير موجود"
        if (text.isBlank()) return "اكتب الرسالة"
        pushNotif(to.uid, "💬 رسالة من ${from.name}", text.trim(), "msg"); return null
    }
    fun backupDir() = java.io.File(ctx.filesDir, "backups").also { it.mkdirs() }
    fun createBackup(): String { val f = java.io.File(backupDir(), "turbo-${System.currentTimeMillis()}.db"); db.close(); dbFile().copyTo(f, true); return f.name }
    fun backups(): List<java.io.File> = backupDir().listFiles()?.sortedByDescending { it.name } ?: emptyList()
    fun restoreBackup(f: java.io.File) { db.close(); f.copyTo(dbFile(), true) }
    fun cleanBackups(keep: Int = 3) { backups().drop(keep).forEach { it.delete() } }
    fun setBanned(uid: String, b: Boolean) { w.execSQL("UPDATE users SET banned=? WHERE uid=?", arrayOf(if (b) 1 else 0, uid)) }

    // ---- balance
    fun addBalance(uid: String, delta: Long, note: String) {
        w.execSQL("UPDATE users SET balance=balance+? WHERE uid=?", arrayOf(delta, uid))
        w.execSQL("INSERT INTO ledger(uid,delta,note,ts) VALUES(?,?,?,?)", arrayOf(uid, delta, note, System.currentTimeMillis()))
    }
    fun ledger(uid: String): List<Ledger> = w.rawQuery("SELECT delta,note,ts FROM ledger WHERE uid=? ORDER BY id DESC LIMIT 100", arrayOf(uid)).use { c -> buildList { while (c.moveToNext()) add(Ledger(c.getLong(0), c.getString(1), c.getLong(2))) } }

    fun transfer(from: User, toUsername: String, byId: Boolean, amount: Long): String? {
        if (amount <= 0) return "أدخل مبلغاً صحيحاً"
        val to = (if (byId) userByUid(toUsername.trim()) else userByUsername(toUsername)) ?: return "المستخدم غير موجود"
        if (to.uid == from.uid) return "لا يمكنك التحويل لنفسك"
        if (get("transfer_enabled", "1") != "1") return "التحويل متوقف حالياً"
        if (!pref(to.uid, "allow_in", true)) return "هذا المستخدم أوقف استقبال التحويلات"
        val fresh = userByUid(from.uid) ?: return "خطأ"
        if (fresh.balance < amount) return "رصيدك غير كافٍ"
        w.beginTransaction()
        try {
            addBalance(from.uid, -amount, "تحويل إلى @${to.username}")
            addBalance(to.uid, amount, "تحويل من @${from.username}")
            w.setTransactionSuccessful()
        } finally { w.endTransaction() }
        val anon = pref(from.uid, "anon_send", false)
        pushNotif(to.uid, "وصلك تحويل 💰", "استلمت $amount ${cur()} من " + (if (anon) "مستخدم مجهول" else "${from.name} (@${from.username})"), "transfer")
        return null
    }

    // ---- services & orders
    private fun svc(c: Cursor) = Service(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getString(4), c.getLong(5), c.getInt(6) == 1)
    fun services(cat: String, includeHidden: Boolean = false): List<Service> =
        w.rawQuery("SELECT id,cat,name,price,kind,minq,hidden FROM services WHERE cat=? ${if (includeHidden) "" else "AND hidden=0"} ORDER BY id", arrayOf(cat)).use { c -> buildList { while (c.moveToNext()) add(svc(c)) } }
    fun allServices(): List<Service> = w.rawQuery("SELECT id,cat,name,price,kind,minq,hidden FROM services ORDER BY cat,id", null).use { c -> buildList { while (c.moveToNext()) add(svc(c)) } }
    fun saveService(s: Service) {
        val cv = ContentValues().apply { put("cat", s.cat); put("name", s.name); put("price", s.price); put("kind", s.kind); put("minq", s.minQty); put("hidden", if (s.hidden) 1 else 0) }
        if (s.id == 0L) w.insert("services", null, cv) else w.update("services", cv, "id=?", arrayOf(s.id.toString()))
    }
    fun deleteService(id: Long) { w.delete("services", "id=?", arrayOf(id.toString())) }

    fun placeOrder(u: User, s: Service, target: String, qty: Long): String? {
        if (target.isBlank()) return "أدخل الرابط أو الأيدي"
        val q = if (s.kind == "per1000") qty else 1L
        if (s.kind == "per1000" && q < s.minQty) return "أقل كمية ${s.minQty}"
        val cost = if (s.kind == "per1000") (s.price * q + 999) / 1000 else s.price
        val fresh = userByUid(u.uid) ?: return "خطأ"
        if (fresh.balance < cost) return "رصيدك غير كافٍ (المطلوب $cost)"
        w.beginTransaction()
        try {
            addBalance(u.uid, -cost, "طلب: ${s.name}")
            w.execSQL("INSERT INTO orders(uid,title,target,qty,price,status,ts) VALUES(?,?,?,?,?,?,?)", arrayOf(u.uid, s.name, target.trim(), q, cost, "قيد التنفيذ", System.currentTimeMillis()))
            w.setTransactionSuccessful()
        } finally { w.endTransaction() }
        return null
    }
    private fun order(c: Cursor) = Order(c.getLong(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4), c.getLong(5), c.getString(6), c.getLong(7), c.getString(8) ?: "")
    fun orders(uid: String?): List<Order> = w.rawQuery(
        "SELECT o.id,o.uid,o.title,o.target,o.qty,o.price,o.status,o.ts,u.name FROM orders o LEFT JOIN users u ON u.uid=o.uid ${if (uid != null) "WHERE o.uid=?" else ""} ORDER BY o.id DESC LIMIT 300",
        if (uid != null) arrayOf(uid) else null).use { c -> buildList { while (c.moveToNext()) add(order(c)) } }
    fun orderById(id: Long): Order? = w.rawQuery("SELECT o.id,o.uid,o.title,o.target,o.qty,o.price,o.status,o.ts,u.name FROM orders o LEFT JOIN users u ON u.uid=o.uid WHERE o.id=?", arrayOf(id.toString())).use { if (it.moveToFirst()) order(it) else null }
    fun setOrderStatus(o: Order, st: String) {
        w.execSQL("UPDATE orders SET status=? WHERE id=?", arrayOf(st, o.id))
        if (st == "مرفوض" && o.status != "مرفوض") addBalance(o.uid, o.price, "استرجاع طلب #${o.id}")
        pushNotif(o.uid, "تحديث الطلب #${o.id}", "${o.title}: $st", "order")
    }
    fun doneCount() = w.rawQuery("SELECT COUNT(*) FROM orders WHERE status='مكتمل'", null).use { it.moveToFirst(); it.getLong(0) }

    // ---- codes
    fun codes(): List<CodeRow> = w.rawQuery("SELECT code,amount,maxuses,used FROM codes ORDER BY rowid DESC", null).use { c -> buildList { while (c.moveToNext()) add(CodeRow(c.getString(0), c.getLong(1), c.getInt(2), c.getInt(3))) } }
    fun addCode(code: String, amount: Long, max: Int) { w.execSQL("INSERT OR REPLACE INTO codes(code,amount,maxuses,used) VALUES(?,?,?,0)", arrayOf(code.trim(), amount, max)) }
    fun delCode(code: String) { w.execSQL("DELETE FROM codes WHERE code=?", arrayOf(code)) }
    fun useCode(u: User, code: String): String? {
        if (get("codes_enabled", "1") != "1") return "الأكواد متوقفة حالياً"
        val c = code.trim()
        val row = codes().firstOrNull { it.code == c } ?: return "الكود غير صحيح"
        if (row.used >= row.maxUses) return "انتهت صلاحية الكود"
        val used = w.rawQuery("SELECT 1 FROM code_uses WHERE code=? AND uid=?", arrayOf(c, u.uid)).use { it.moveToFirst() }
        if (used) return "استخدمت هذا الكود من قبل"
        w.execSQL("INSERT INTO code_uses(code,uid) VALUES(?,?)", arrayOf(c, u.uid))
        w.execSQL("UPDATE codes SET used=used+1 WHERE code=?", arrayOf(c))
        addBalance(u.uid, row.amount, "كود $c")
        return null
    }

    // ---- gifts / wheel
    fun claim(u: User, kind: String, hours: Long, amount: Long): String? {
        val k = "claim_${kind}_${u.uid}"
        val last = getLong(k, 0)
        val now = System.currentTimeMillis()
        if (now - last < hours * 3600_000) {
            val left = (hours * 3600_000 - (now - last)) / 60000
            return "متاح بعد ${left / 60} س و${left % 60} د"
        }
        put(k, now.toString()); addBalance(u.uid, amount, "هدية $kind"); return null
    }

    // ---- notifications
    fun pushNotif(uid: String, title: String, body: String, type: String = "sys") {
        w.execSQL("INSERT INTO notifs(uid,title,body,ts,type) VALUES(?,?,?,?,?)", arrayOf(uid, title, body, System.currentTimeMillis(), type))
    }
    fun broadcast(title: String, body: String, type: String = "broadcast"): Int {
        val ids = users().map { it.uid }
        w.beginTransaction()
        try { ids.forEach { pushNotif(it, title, body, type) }; w.setTransactionSuccessful() } finally { w.endTransaction() }
        return ids.size
    }
    fun notifs(uid: String): List<Notif> = w.rawQuery("SELECT id,title,body,ts,read,type FROM notifs WHERE uid=? ORDER BY id DESC LIMIT 200", arrayOf(uid)).use { c -> buildList { while (c.moveToNext()) add(Notif(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), c.getInt(4) == 1, c.getString(5) ?: "sys")) } }
    fun unreadCount(uid: String) = w.rawQuery("SELECT COUNT(*) FROM notifs WHERE uid=? AND read=0", arrayOf(uid)).use { it.moveToFirst(); it.getInt(0) }
    fun markRead(uid: String) { w.execSQL("UPDATE notifs SET read=1 WHERE uid=?", arrayOf(uid)) }
    /** returns notifications not yet shown as system notifications and marks them shown */
    fun takeUnshown(uid: String): List<Notif> {
        val l = w.rawQuery("SELECT id,title,body,ts,read,type FROM notifs WHERE uid=? AND shown=0 ORDER BY id", arrayOf(uid)).use { c -> buildList { while (c.moveToNext()) add(Notif(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3), false, c.getString(5) ?: "sys")) } }
        if (l.isNotEmpty()) w.execSQL("UPDATE notifs SET shown=1 WHERE uid=?", arrayOf(uid))
        return l.filter { pref(uid, "n_" + it.type, true) && pref(uid, "n_all", true) }
    }

    // ---- emoji cache
    fun emojiPath(id: String): Pair<String, String>? = w.rawQuery("SELECT path,kind FROM emoji WHERE id=?", arrayOf(id)).use { if (it.moveToFirst()) it.getString(0) to it.getString(1) else null }
    fun emojiSave(id: String, path: String, kind: String, set: String) { w.execSQL("INSERT OR REPLACE INTO emoji(id,path,kind,setname) VALUES(?,?,?,?)", arrayOf(id, path, kind, set)) }
    fun emojiAll(): List<Triple<String, String, String>> = w.rawQuery("SELECT id,path,kind FROM emoji ORDER BY rowid DESC", null).use { c -> buildList { while (c.moveToNext()) add(Triple(c.getString(0), c.getString(1), c.getString(2))) } }

    fun seedShots() {
        if (allServices().isNotEmpty()) return
        saveService(Service(0, "تيك توك", "متابعين تيك توك", 2500, "per1000", 100, false))
        saveService(Service(0, "تيك توك", "لايكات تيك توك", 600, "per1000", 50, false))
        saveService(Service(0, "شدات ببجي موبايل", "60 شدة", 1200, "fixed", 1, false))
        saveService(Service(0, "شدات ببجي موبايل", "325 شدة", 6000, "fixed", 1, false))
        val me = userByUsername("weswod"); if (me != null && me.balance == 0L) addBalance(me.uid, 32, "شاشة اختبار")
    }
    fun recentCharges(): List<Triple<String, Long, Long>> = w.rawQuery(
        "SELECT u.name,l.delta,l.ts FROM ledger l LEFT JOIN users u ON u.uid=l.uid WHERE l.delta>0 AND (l.note LIKE 'كود%' OR l.note LIKE 'شحن%' OR l.note LIKE 'إضافة%') ORDER BY l.id DESC LIMIT 100", null)
        .use { c -> buildList { while (c.moveToNext()) add(Triple(c.getString(0) ?: "?", c.getLong(1), c.getLong(2))) } }
    fun dbFile() = ctx.getDatabasePath("turbo.db")
}

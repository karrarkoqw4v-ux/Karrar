package com.vio.studio

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class User(val name: String, val username: String, val phone: String, val joined: Long)
data class Work(val kind: String, val title: String, val time: Long)

object Session {
    var user by mutableStateOf<User?>(null)
    var guest by mutableStateOf(false)
    val works = mutableStateListOf<Work>()
}

object Nav {
    val stack = mutableStateListOf("home")
    val cur: String get() = stack.last()
    fun go(r: String) { stack.add(r) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.lastIndex) }
    fun tab(r: String) { stack.clear(); stack.add(r) }
    fun reset(r: String) { stack.clear(); stack.add(r) }
}

object Debug { var regStage by mutableStateOf(0); var still by mutableStateOf(false) }

/** تخزين محلي: حسابات (PBKDF2) + جلسة + أعمالي + رابط الخادم */
object Repo {
    private lateinit var sp: SharedPreferences

    fun init(c: Context) {
        sp = c.getSharedPreferences("vio", Context.MODE_PRIVATE)
        Session.guest = sp.getBoolean("guest", false)
        val cur = sp.getString("current", null)
        Session.user = if (cur != null) find(cur)?.let { toUser(it) } else null
        loadWorks()
    }

    private fun users() = JSONArray(sp.getString("users", "[]"))
    private fun find(username: String): JSONObject? {
        val a = users()
        for (i in 0 until a.length()) if (a.getJSONObject(i).getString("u").equals(username, true)) return a.getJSONObject(i)
        return null
    }
    private fun toUser(o: JSONObject) = User(o.getString("n"), o.getString("u"), o.optString("p"), o.optLong("t"))

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }
    private fun hash(pass: String, salt: String): String {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(pass.toCharArray(), salt.toByteArray(), 20000, 256)
        return hex(f.generateSecret(spec).encoded)
    }

    /** يرجع رسالة خطأ أو null عند النجاح */
    fun register(name: String, user: String, phone: String, pass: String): String? {
        if (name.trim().length < 2) return "اكتب اسمك (حرفان على الأقل)"
        if (!Regex("^[A-Za-z0-9_]{3,20}$").matches(user)) return "اسم المستخدم 3-20 حرفاً إنجليزياً أو أرقاماً"
        if (pass.length < 4) return "كلمة المرور 4 أحرف على الأقل"
        if (find(user) != null) return "اسم المستخدم مستخدم مسبقاً"
        val salt = hex(ByteArray(8).also { SecureRandom().nextBytes(it) })
        val o = JSONObject().put("n", name.trim()).put("u", user).put("p", phone).put("s", salt).put("h", hash(pass, salt)).put("t", System.currentTimeMillis())
        sp.edit().putString("users", users().put(o).toString()).apply()
        setCurrent(user)
        return null
    }

    fun login(user: String, pass: String): String? {
        val o = find(user.trim()) ?: return "الحساب غير موجود"
        val ok = MessageDigest.isEqual(hash(pass, o.getString("s")).toByteArray(), o.getString("h").toByteArray())
        if (!ok) return "كلمة المرور غير صحيحة"
        setCurrent(o.getString("u"))
        return null
    }

    private fun setCurrent(u: String?) {
        sp.edit().apply { if (u == null) remove("current") else putString("current", u) }.apply()
        Session.user = if (u != null) find(u)?.let { toUser(it) } else null
        loadWorks()
    }

    fun logout() { setCurrent(null) }
    fun setGuest(g: Boolean) { Session.guest = g; sp.edit().putBoolean("guest", g).apply() }

    private fun key() = "works_" + (Session.user?.username ?: "_guest")
    private fun loadWorks() {
        Session.works.clear()
        val a = JSONArray(sp.getString(key(), "[]"))
        for (i in 0 until a.length()) a.getJSONObject(i).let { Session.works.add(Work(it.getString("k"), it.getString("t"), it.getLong("d"))) }
    }
    fun addWork(kind: String, title: String) {
        Session.works.add(0, Work(kind, title, System.currentTimeMillis()))
        val a = JSONArray()
        Session.works.take(50).forEach { a.put(JSONObject().put("k", it.kind).put("t", it.title).put("d", it.time)) }
        sp.edit().putString(key(), a.toString()).apply()
    }

    fun seedShots() {
        val now = System.currentTimeMillis()
        Session.works.clear()
        Session.works.addAll(listOf(Work("photo", "فلتر سينمائي", now - 3600_000), Work("video", "مونتاج ريلز", now - 86400_000), Work("ai", "إزالة الخلفية", now - 2 * 86400_000), Work("link", "تيك توك بلا علامة", now - 3 * 86400_000)))
    }
}

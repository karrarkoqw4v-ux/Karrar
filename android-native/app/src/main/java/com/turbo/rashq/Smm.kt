package com.turbo.rashq

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Standard SMM-panel API v2 client (same protocol the bot uses): action=balance | services | add | status */
object Smm {
    data class Remote(val id: String, val name: String, val category: String, val rate: Double, val min: Long, val max: Long)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun request(url: String, post: String?): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 30000; c.setRequestProperty("User-Agent", "Mozilla/5.0")
        if (post != null) { c.requestMethod = "POST"; c.doOutput = true; c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded"); c.outputStream.use { it.write(post.toByteArray()) } }
        val code = c.responseCode
        val body = (if (code in 200..399) c.inputStream else c.errorStream)?.readBytes()?.decodeToString() ?: ""
        return code to body
    }

    /** Tries /api/v2, /api and the root, POST then GET. Returns JSONObject or JSONArray. */
    fun call(base: String, key: String, action: String, extra: Map<String, String> = emptyMap()): Any {
        val b = base.trim().trimEnd('/')
        val q = (listOf("key" to key, "action" to action) + extra.toList()).joinToString("&") { "${it.first}=${enc(it.second)}" }
        var last = "فشل الاتصال بالموقع"
        for (path in listOf("/api/v2", "/api", "")) {
            for (post in listOf(true, false)) {
                try {
                    val (code, body) = if (post) request(b + path, q) else request("$b$path?$q", null)
                    val t = body.trim()
                    if (t.startsWith("[")) return JSONArray(t)
                    if (t.startsWith("{")) return JSONObject(t)
                    last = if (code == 404) "المسار غير صحيح" else "رد غير معروف من الموقع (HTTP $code)"
                } catch (e: Exception) { last = e.message ?: last }
            }
        }
        throw Exception(last)
    }

    /** Returns "balance currency" or throws with the reason. */
    fun verify(base: String, key: String): String {
        for (a in listOf("balance", "get_balance")) {
            val r = call(base, key, a)
            if (r is JSONObject) {
                if (r.has("error")) { if (a == "balance") continue else throw Exception(r.optString("error")) }
                val bal = r.opt("balance") ?: r.opt("result") ?: "OK"
                val cur = r.optString("currency", "")
                return "$bal $cur".trim()
            }
        }
        throw Exception("المفتاح غير صحيح")
    }

    fun services(base: String, key: String): List<Remote> {
        val r = call(base, key, "services")
        val arr = when (r) { is JSONArray -> r; is JSONObject -> { if (r.has("error")) throw Exception(r.optString("error")); r.optJSONArray("services") ?: r.optJSONArray("data") ?: JSONArray() }; else -> JSONArray() }
        return (0 until arr.length()).map { arr.getJSONObject(it) }.map { s ->
            Remote(s.opt("service")?.toString() ?: s.opt("id")?.toString() ?: "", s.optString("name", "خدمة"), s.optString("category", ""),
                s.optString("rate", s.optString("price", "0")).toDoubleOrNull() ?: 0.0, s.optString("min", "1").toDoubleOrNull()?.toLong() ?: 1, s.optString("max", "10000").toDoubleOrNull()?.toLong() ?: 10000)
        }
    }

    fun add(base: String, key: String, service: String, link: String, qty: Long): String {
        val r = call(base, key, "add", mapOf("service" to service, "link" to link, "quantity" to qty.toString()))
        if (r is JSONObject) { if (r.has("order")) return r.get("order").toString(); throw Exception(r.optString("error", "رد غير معروف من الموقع")) }
        throw Exception("رد غير معروف من الموقع")
    }

    fun status(base: String, key: String, order: String): String {
        val r = call(base, key, "status", mapOf("order" to order))
        if (r is JSONObject) { if (r.has("error")) throw Exception(r.optString("error")); return r.optString("status", "") }
        return ""
    }

    val keywords = mapOf(
        "تيك توك" to listOf("tiktok", "tt", "تيك توك", "تيكتوك"), "انستغرام" to listOf("instagram", "ig", "insta", "انستغرام", "انستا"),
        "فيسبوك" to listOf("facebook", "fb", "فيسبوك", "فيس"), "يوتيوب" to listOf("youtube", "yt", "يوتيوب"), "كواي" to listOf("kwai", "كوافي", "كواي"),
        "تليجرام" to listOf("telegram", "tg", "تليجرام", "تيليجرام"), "ثريدز" to listOf("threads", "ثريدز"), "واتساب" to listOf("whatsapp", "wa", "واتساب", "واتس"),
        "خدمات مميزة" to listOf("premium", "vip", "مميز"), "تفاعلات تليجرام" to listOf("reaction", "تفاعل", "تفاعلات"), "خدمات مجانية" to listOf("free", "مجاني", "مجانية"))
    fun filterFor(platform: String, all: List<Remote>): List<Remote> {
        val kws = keywords[platform] ?: listOf(platform.lowercase())
        val f = all.filter { s -> kws.any { k -> s.name.lowercase().contains(k.lowercase()) || s.category.lowercase().contains(k.lowercase()) } }
        return f.ifEmpty { all }
    }
    fun mapStatus(s: String): String = when (s.lowercase()) {
        "completed" -> "مكتمل"; "canceled", "cancelled", "refunded", "fail", "failed" -> "مرفوض"; "partial" -> "مكتمل جزئياً"
        "in progress", "processing" -> "قيد التنفيذ"; "pending" -> "قيد الانتظار"; else -> "قيد التنفيذ" }
}

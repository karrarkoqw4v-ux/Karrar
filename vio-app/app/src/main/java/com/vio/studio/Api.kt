package com.vio.studio

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** اتصال اختياري بخادم Vio (للفيديو/الذكاء الاصطناعي/الروابط). يعمل في خيط خلفي. */
object Api {
    private fun call(path: String, body: JSONObject?): Pair<Int, String> {
        val u = URL(Session.server.trimEnd('/') + path)
        val c = u.openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 60000
        if (body != null) { c.requestMethod = "POST"; c.doOutput = true; c.setRequestProperty("Content-Type", "application/json"); c.outputStream.use { it.write(body.toString().toByteArray()) } }
        val code = c.responseCode
        val txt = (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        return code to txt
    }

    fun async(block: () -> String, done: (String) -> Unit) {
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        Thread { val r = try { block() } catch (e: Exception) { "تعذّر الاتصال بالخادم" }; h.post { done(r) } }.start()
    }

    fun ping(): String {
        if (Session.server.isBlank()) return "لم تُضبط عنوان الخادم بعد"
        val (c, _) = call("/api/ping", null)
        return if (c == 200) "متصل بالخادم" else "الخادم ردّ برمز $c"
    }

    fun link(url: String): String {
        if (Session.server.isBlank()) return "اضبط عنوان الخادم من الإعدادات أولاً"
        val (c, t) = call("/api/link", JSONObject().put("url", url))
        return if (c == 200) "تم: " + (JSONObject(t).optString("title").ifBlank { "الفيديو جاهز" }) else "فشل الجلب ($c)"
    }
}

package com.vio.studio

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** جلب فيديو تيك توك بدون علامة مائية مباشرة من الجهاز (نفس مصدر البوت: tikwm) */
object Api {
    fun async(block: () -> String, done: (String) -> Unit) {
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        Thread { val r = try { block() } catch (e: Exception) { "تعذّر الجلب: تأكد من الاتصال بالإنترنت والرابط" }; h.post { done(r) } }.start()
    }

    private fun open(u: String): HttpURLConnection {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 60000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
        return c
    }

    fun tiktok(ctx: Context, link: String): String {
        if (!(link.contains("tiktok.com"))) return "الرابط ليس من تيك توك"
        val api = "https://www.tikwm.com/api/?hd=1&url=" + java.net.URLEncoder.encode(link, "UTF-8")
        val js = JSONObject(open(api).inputStream.bufferedReader().use { it.readText() })
        if (js.optInt("code") != 0) return "تعذّر جلب الفيديو: الرابط غير صحيح أو محذوف أو خاص"
        val d = js.getJSONObject("data")
        var v = d.optString("hdplay").ifBlank { d.optString("play") }
        if (v.isBlank()) return "هذا الرابط صور (سلايد) وليس فيديو"
        if (v.startsWith("/")) v = "https://www.tikwm.com$v"
        val vals = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "Vio_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Vio")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, vals) ?: return "تعذّر الحفظ في المعرض"
        ctx.contentResolver.openOutputStream(uri)?.use { out -> open(v).inputStream.use { it.copyTo(out) } }
        return "تم: حُفظ الفيديو في Movies/Vio"
    }
}

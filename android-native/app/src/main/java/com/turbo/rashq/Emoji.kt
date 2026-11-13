package com.turbo.rashq

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Telegram Bot API client used only to pull premium (custom) emoji by ID or by pack. */
object TgApi {
    private fun token() = Repo.get("tg_token")
    private fun http(url: String): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 30000
        val code = c.responseCode
        val s = if (code in 200..299) c.inputStream else c.errorStream
        val b = s.readBytes(); if (code !in 200..299) throw Exception(String(b).take(160)); return b
    }
    private fun api(method: String, q: String = ""): JSONObject {
        val t = token(); if (t.isBlank()) throw Exception("أدخل توكن البوت أولاً")
        val j = JSONObject(String(http("https://api.telegram.org/bot$t/$method$q")))
        if (!j.optBoolean("ok")) throw Exception(j.optString("description", "فشل"))
        return j
    }
    private fun download(ctx: Context, fileId: String, name: String): File {
        val path = api("getFile", "?file_id=${URLEncoder.encode(fileId, "UTF-8")}").getJSONObject("result").getString("file_path")
        val f = File(ctx.filesDir, "emoji").also { it.mkdirs() }.let { File(it, name) }
        f.writeBytes(http("https://api.telegram.org/file/bot${token()}/$path")); return f
    }
    private fun store(ctx: Context, st: JSONObject, set: String): String? {
        val id = st.optString("custom_emoji_id").ifBlank { return null }
        if (Repo.emojiPath(id) != null) return id
        return try {
            when {
                st.optBoolean("is_animated") -> { val f = download(ctx, st.getString("file_id"), "$id.tgs"); Repo.emojiSave(id, f.absolutePath, "tgs", set) }
                else -> {
                    // static webp, or the preview thumbnail for video (webm) emoji
                    val fid = if (st.optBoolean("is_video")) st.optJSONObject("thumbnail")?.optString("file_id") ?: st.getString("file_id") else st.getString("file_id")
                    val f = download(ctx, fid, "$id.webp"); Repo.emojiSave(id, f.absolutePath, "img", set)
                }
            }; id
        } catch (e: Exception) { null }
    }
    /** Fetch IDs; also returns number saved. Pack of each emoji is recorded. */
    fun fetchIds(ctx: Context, ids: List<String>): Int {
        var n = 0
        ids.distinct().chunked(50).forEach { chunk ->
            val arr = JSONArray(chunk)
            val res = api("getCustomEmojiStickers", "?custom_emoji_ids=" + URLEncoder.encode(arr.toString(), "UTF-8")).getJSONArray("result")
            for (i in 0 until res.length()) { val st = res.getJSONObject(i); if (store(ctx, st, st.optString("set_name")) != null) n++ }
        }
        return n
    }
    /** Pull the whole pack that contains the given emoji id. */
    fun pullPackOf(ctx: Context, id: String): Pair<String, Int> {
        val res = api("getCustomEmojiStickers", "?custom_emoji_ids=" + URLEncoder.encode(JSONArray(listOf(id)).toString(), "UTF-8")).getJSONArray("result")
        if (res.length() == 0) throw Exception("المعرّف غير موجود")
        val set = res.getJSONObject(0).optString("set_name"); if (set.isBlank()) throw Exception("لا توجد حزمة")
        return set to pullPack(ctx, set)
    }
    fun pullPack(ctx: Context, set: String): Int {
        val st = api("getStickerSet", "?name=" + URLEncoder.encode(set, "UTF-8")).getJSONObject("result").getJSONArray("stickers")
        var n = 0
        for (i in 0 until st.length()) if (store(ctx, st.getJSONObject(i), set) != null) n++
        return n
    }
    fun packIds(set: String): List<String> {
        val st = api("getStickerSet", "?name=" + URLEncoder.encode(set, "UTF-8")).getJSONObject("result").getJSONArray("stickers")
        return (0 until st.length()).mapNotNull { st.getJSONObject(it).optString("custom_emoji_id").ifBlank { null } }
    }
}

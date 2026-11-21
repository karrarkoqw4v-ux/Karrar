package com.vio.studio

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File

object Jobs {
    var active by mutableStateOf(false)
    var title by mutableStateOf("")
    var progress by mutableStateOf(0f)
    var msg by mutableStateOf("")
    var finished by mutableStateOf(false)
    var ok by mutableStateOf(false)
}

/** محرك المعالجة على الجهاز (Media3 Transformer) */
object Engine {
    private val main = Handler(Looper.getMainLooper())

    private fun fail(m: String) { Jobs.finished = true; Jobs.ok = false; Jobs.msg = m }

    fun run(ctx: Context, uri: Uri, ids: List<String>, title: String) {
        Jobs.active = true; Jobs.finished = false; Jobs.ok = false; Jobs.progress = 0f; Jobs.title = title; Jobs.msg = "جارٍ التحضير…"
        val durUs = try {
            val r = MediaMetadataRetriever(); r.setDataSource(ctx, uri)
            val d = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000L
            if (ids == listOf("out_cover")) {
                val f = r.getFrameAtTime(minOf(1_000_000L, d / 2), MediaMetadataRetriever.OPTION_CLOSEST_SYNC); r.release()
                if (f != null && Imaging.save(ctx, f, "cover")) { Jobs.finished = true; Jobs.ok = true; Jobs.msg = "تم حفظ صورة الغلاف في Pictures/Vio"; Repo.addWork("photo", title) } else fail("تعذّر استخراج الصورة")
                return
            }
            r.release(); d
        } catch (e: Exception) { fail("تعذّر فتح الفيديو"); return }
        val specs = ids.mapNotNull { Fxs.build(it, durUs) }
        if (specs.isEmpty()) { fail("هذا التأثير غير متاح"); return }
        val video = specs.flatMap { it.video } + specs.flatMap { it.last }
        val audio = specs.flatMap { it.audio }
        val item = EditedMediaItem.Builder(MediaItem.fromUri(uri)).setEffects(Effects(audio, video)).setRemoveAudio(specs.any { it.mute }).build()
        val out = File(ctx.cacheDir, "vio_${System.currentTimeMillis()}.mp4")
        lateinit var tr: Transformer
        tr = Transformer.Builder(ctx).addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                Jobs.msg = "جارٍ الحفظ في المعرض…"
                val ok = saveVideo(ctx, out)
                out.delete()
                Jobs.progress = 1f; Jobs.finished = true; Jobs.ok = ok
                Jobs.msg = if (ok) "تم! حُفظ الفيديو في Movies/Vio" else "تعذّر الحفظ في المعرض"
                if (ok) Repo.addWork("video", title)
            }
            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                out.delete(); fail("تعذّرت المعالجة على هذا الجهاز/الفيديو")
            }
        }).build()
        Jobs.msg = "جارٍ المعالجة…"
        tr.start(item, out.absolutePath)
        val holder = ProgressHolder()
        val poll = object : Runnable {
            override fun run() {
                if (Jobs.finished) return
                if (tr.getProgress(holder) != Transformer.PROGRESS_STATE_NOT_STARTED) Jobs.progress = holder.progress / 100f
                main.postDelayed(this, 400)
            }
        }
        main.postDelayed(poll, 400)
    }

    private fun saveVideo(ctx: Context, f: File): Boolean = try {
        val v = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "Vio_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Vio")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v)
        if (uri == null) false else { ctx.contentResolver.openOutputStream(uri)?.use { o -> f.inputStream().use { it.copyTo(o) } }; true }
    } catch (e: Exception) { false }
}

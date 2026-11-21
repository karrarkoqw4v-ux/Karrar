package com.vio.studio

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.graphics.Brush

class PFilter(val id: String, val name: String, val brush: Brush, val matrix: ColorMatrix?, val vignette: Boolean = false, val pixel: Int = 0, val flip: Boolean = false)

/** معالجة الصور على الجهاز (بدون خادم) */
object Imaging {
    private fun m(vararg v: Float) = ColorMatrix(floatArrayOf(*v))
    private fun sat(s: Float) = ColorMatrix().apply { setSaturation(s) }
    private fun con(c: Float, b: Float = 0f): ColorMatrix { val t = (1f - c) * 128f + b; return m(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f) }
    private fun mul(a: ColorMatrix, b: ColorMatrix) = ColorMatrix(a).apply { postConcat(b) }

    val filters = listOf(
        PFilter("orig", "أصلي", C.brand, null),
        PFilter("bw", "أبيض وأسود", C.cool, sat(0f)),
        PFilter("warm", "دافئ", C.warm, m(1.15f, 0f, 0f, 0f, 8f, 0f, 1.02f, 0f, 0f, 0f, 0f, 0f, 0.85f, 0f, -8f, 0f, 0f, 0f, 1f, 0f)),
        PFilter("cold", "بارد", C.cool, m(0.88f, 0f, 0f, 0f, -6f, 0f, 1.0f, 0f, 0f, 0f, 0f, 0f, 1.18f, 0f, 10f, 0f, 0f, 0f, 1f, 0f)),
        PFilter("vint", "كلاسيك", C.warm, m(0.393f, 0.769f, 0.189f, 0f, 0f, 0.349f, 0.686f, 0.168f, 0f, 0f, 0.272f, 0.534f, 0.131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)),
        PFilter("drama", "دراما", C.brand, mul(sat(1.25f), con(1.4f, -10f)), true),
        PFilter("neon", "نيون", C.mint, mul(sat(2.0f), con(1.25f))),
        PFilter("cine", "سينمائي", C.cool, mul(m(0.9f, 0.05f, 0.05f, 0f, 0f, 0f, 1.0f, 0.05f, 0f, 6f, 0.1f, 0.2f, 1.1f, 0f, 14f, 0f, 0f, 0f, 1f, 0f), con(1.15f)), true),
        PFilter("pixel", "بكسل", C.brand, null, false, 14),
        PFilter("mirror", "مرآة", C.mint, null, false, 0, true),
    )

    fun sample(w: Int = 640, h: Int = 440): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0xFF1E3A8A.toInt(), 0xFFEC4899.toInt(), 0xFFFBBF24.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = null; p.color = 0xFFFFF3C4.toInt(); c.drawCircle(w * 0.7f, h * 0.45f, h * 0.13f, p)
        p.color = 0xFF3B1D6E.toInt(); val a = Path().apply { moveTo(0f, h * 0.8f); lineTo(w * 0.25f, h * 0.55f); lineTo(w * 0.5f, h * 0.78f); lineTo(w * 0.78f, h * 0.5f); lineTo(w.toFloat(), h * 0.75f); lineTo(w.toFloat(), h.toFloat()); lineTo(0f, h.toFloat()); close() }
        c.drawPath(a, p)
        p.color = 0xFF14082E.toInt(); c.drawRect(0f, h * 0.88f, w.toFloat(), h.toFloat(), p)
        return b
    }

    fun apply(src: Bitmap, f: PFilter): Bitmap {
        var base = src
        if (f.pixel > 0) {
            val sw = (src.width / f.pixel).coerceAtLeast(2); val sh = (src.height / f.pixel).coerceAtLeast(2)
            base = Bitmap.createScaledBitmap(Bitmap.createScaledBitmap(src, sw, sh, false), src.width, src.height, false)
        }
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if (f.matrix != null) p.colorFilter = ColorMatrixColorFilter(f.matrix)
        if (f.flip) { c.translate(src.width.toFloat(), 0f); c.scale(-1f, 1f) }
        c.drawBitmap(base, 0f, 0f, p)
        if (f.flip) c.setMatrix(null)
        if (f.vignette) {
            val vp = Paint()
            vp.shader = RadialGradient(src.width / 2f, src.height / 2f, maxOf(src.width, src.height) * 0.75f, intArrayOf(0x00000000, 0x00000000, 0xB0000000.toInt()), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, src.width.toFloat(), src.height.toFloat(), vp)
        }
        return out
    }

    fun thumb(f: PFilter): Bitmap = apply(sample(160, 120), f)

    /** يحفظ في معرض الصور (Pictures/Vio) */
    fun save(ctx: Context, bmp: Bitmap, name: String): Boolean = try {
        val v = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Vio_${name}_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Vio")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v)
        if (uri == null) false else { ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) }; true }
    } catch (e: Exception) { false }
}

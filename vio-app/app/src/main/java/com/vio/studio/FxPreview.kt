package com.vio.studio

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private val Scene: ImageBitmap by lazy { Imaging.sample(320, 320).asImageBitmap() }

private fun kindOf(f: Fx): String {
    val s = (f.id + " " + f.title).lowercase()
    fun has(vararg k: String) = k.any { it in s }
    return when {
        f.cat == "part" -> "part"; f.cat == "aud" -> "aud"; f.cat == "res" -> "res"; f.cat == "out" -> "out"
        has("glitch", "rgb", "غليتش", "انزياح", "vhs") -> "glitch"
        has("shake", "اهتزاز", "رجفة") -> "shake"
        has("flash", "فلاش", "strobe", "ستروب", "وميض") -> "flash"
        has("zoom", "زوم", "pulse", "نبض", "تنفّس", "breath") -> "zoom"
        has("spin", "دوران", "دوّار") -> "spin"
        has("flip", "قلب", "مرآة", "mirror") -> "flip"
        has("blur", "تضبيب", "تمويه", "bokeh") -> "blur"
        has("trail", "أثر", "drift", "انجراف") -> "drift"
        f.cat == "edit" -> "edit"
        else -> "color"
    }
}

private fun sat(s: Float) = ColorMatrix().apply { setToSaturation(s) }
private fun grade(f: Fx, t: Float): ColorFilter? {
    val s = (f.id + " " + f.title).lowercase()
    val m = when {
        "أبيض" in s || "noir" in s || "bw" in s || "رمادي" in s -> sat(0f)
        "دافئ" in s || "warm" in s || "golden" in s || "ذهبي" in s || "sunset" in s || "غروب" in s -> ColorMatrix(floatArrayOf(1.18f, 0f, 0f, 0f, 10f, 0f, 1.02f, 0f, 0f, 0f, 0f, 0f, 0.8f, 0f, -10f, 0f, 0f, 0f, 1f, 0f))
        "بارد" in s || "cool" in s || "ice" in s || "ثلج" in s || "أزرق" in s -> ColorMatrix(floatArrayOf(0.85f, 0f, 0f, 0f, -8f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1.2f, 0f, 14f, 0f, 0f, 0f, 1f, 0f))
        "نيون" in s || "neon" in s || "cyber" in s || "vivid" in s || "غنية" in s -> sat(1.9f)
        "hdr" in s || "وضوح" in s || "تباين" in s || "clarity" in s -> ColorMatrix(floatArrayOf(1.3f, 0f, 0f, 0f, -30f, 0f, 1.3f, 0f, 0f, -30f, 0f, 0f, 1.3f, 0f, -30f, 0f, 0f, 0f, 1f, 0f))
        "قديم" in s || "vintage" in s || "kodak" in s || "كلاسيك" in s -> ColorMatrix(floatArrayOf(0.393f, 0.769f, 0.189f, 0f, 0f, 0.349f, 0.686f, 0.168f, 0f, 0f, 0.272f, 0.534f, 0.131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f))
        else -> {
            val h = abs(f.id.hashCode()) % 5
            when (h) { 0 -> sat(1.5f); 1 -> sat(0.6f); 2 -> ColorMatrix(floatArrayOf(1.1f, 0f, 0f, 0f, 0f, 0f, 0.95f, 0.1f, 0f, 0f, 0f, 0.1f, 1.1f, 0f, 6f, 0f, 0f, 0f, 1f, 0f))
                3 -> ColorMatrix(floatArrayOf(1.2f, 0f, 0f, 0f, -16f, 0f, 1.2f, 0f, 0f, -16f, 0f, 0f, 1.2f, 0f, -16f, 0f, 0f, 0f, 1f, 0f)); else -> sat(1.25f) }
        }
    }
    return ColorFilter.colorMatrix(m)
}

/** معاينة متحركة للتأثير (إجرائية، بلا خادم) */
@Composable
fun FxThumb(f: Fx, size: Dp = 76.dp, radius: Dp = 16.dp) {
    val k = kindOf(f)
    val t: Float = if (Debug.still) 0.3f else {
        val tr = rememberInfiniteTransition(label = "fx")
        tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "t").value
    }
    val w = sin(t * 2 * PI.toFloat())
    Box(Modifier.size(size).clip(RoundedCornerShape(radius)), contentAlignment = Alignment.Center) {
        when (k) {
            "part" -> {
                Image(Scene, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = ColorFilter.colorMatrix(sat(0.5f)))
                Canvas(Modifier.fillMaxSize()) {
                    val ice = f.id.contains("snow") || f.title.contains("ثلج")
                    for (i in 0 until 16) {
                        val sx = ((i * 37) % 100) / 100f; val sy = ((i * 53) % 100) / 100f
                        val y = (sy + t * (0.6f + (i % 3) * 0.25f)) % 1f
                        val c = if (ice) Color.White else listOf(C.pink, C.gold, C.cyan, Color.White)[i % 4]
                        drawCircle(c.copy(alpha = 0.9f), radius = size.toPx() * (0.025f + (i % 3) * 0.01f), center = Offset(this.size.width * sx, this.size.height * y))
                    }
                }
            }
            "aud" -> Box(Modifier.fillMaxSize().background(C.brandV), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize(0.7f)) {
                    val n = 7; val bw = this.size.width / (n * 2f)
                    for (i in 0 until n) {
                        val h = this.size.height * (0.25f + 0.7f * abs(sin(t * 2 * PI.toFloat() + i * 0.9f)))
                        drawRoundRect(Color.White, Offset(i * 2 * bw, (this.size.height - h) / 2), androidx.compose.ui.geometry.Size(bw, h), androidx.compose.ui.geometry.CornerRadius(bw / 2))
                    }
                }
            }
            "res" -> Box(Modifier.fillMaxSize().background(C.cool), contentAlignment = Alignment.Center) {
                val s = f.title
                val label = when { "4K" in s -> "4K"; "2K" in s || "2k" in s -> "2K"; "1080" in s -> "FHD"; "720" in s -> "HD"; "2×" in s -> "2×"; else -> "HQ" }
                T(label, 20, Color.White, FontWeight.ExtraBold, Modifier.graphicsLayer { val sc = 1f + 0.08f * w; scaleX = sc; scaleY = sc })
            }
            "out", "edit" -> Box(Modifier.fillMaxSize().background(C.warm), contentAlignment = Alignment.Center) {
                Icon(if (k == "out") Icons.Rounded.IosShare else Icons.Rounded.ContentCut, null, tint = Color.White,
                    modifier = Modifier.size(size * 0.45f).graphicsLayer { rotationZ = if (k == "edit") 14f * w else 0f; translationY = if (k == "out") -6f * abs(w) else 0f })
            }
            else -> {
                val mod = Modifier.fillMaxSize().graphicsLayer {
                    when (k) {
                        "zoom" -> { val s = 1f + 0.16f * abs(w); scaleX = s; scaleY = s }
                        "shake" -> { translationX = 7f * sin(t * 40f); translationY = 5f * sin(t * 33f) }
                        "spin" -> { rotationZ = 360f * t; scaleX = 1.25f; scaleY = 1.25f }
                        "flip" -> { scaleX = if (t < 0.5f) 1f else -1f }
                        "drift" -> { translationX = 14f * w; scaleX = 1.15f; scaleY = 1.15f }
                    }
                }
                val cf = if (k == "color" || k == "blur") grade(f, t) else null
                Image(Scene, null, mod, contentScale = ContentScale.Crop, colorFilter = cf)
                if (k == "glitch") {
                    val o = 6f * sin(t * 30f)
                    Image(Scene, null, Modifier.fillMaxSize().graphicsLayer { translationX = o; alpha = 0.5f }, contentScale = ContentScale.Crop, colorFilter = ColorFilter.tint(Color.Red, BlendMode.Modulate))
                    Image(Scene, null, Modifier.fillMaxSize().graphicsLayer { translationX = -o; alpha = 0.5f }, contentScale = ContentScale.Crop, colorFilter = ColorFilter.tint(Color.Cyan, BlendMode.Modulate))
                }
                if (k == "flash") Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = (0.75f * (1f - t)).coerceIn(0f, 1f))))
                if (k == "blur") Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))
            }
        }
    }
}

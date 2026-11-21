package com.vio.studio

import android.graphics.Matrix
import androidx.media3.common.Effect
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.Crop
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbAdjustment
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SpeedChangeEffect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** ما يُطبَّق فعلياً على الفيديو: تأثيرات Media3 (GPU) تُبنى من معرّف تأثير البوت */
class Spec(
    val video: List<Effect> = emptyList(),
    val audio: List<AudioProcessor> = emptyList(),
    val mute: Boolean = false,
    val last: List<Effect> = emptyList(),
    val cover: Boolean = false,
)

object Fxs {
    private class Grade(val r: Float = 1f, val g: Float = 1f, val b: Float = 1f, val c: Float = 0f, val s: Float = 1f, val br: Float = 0f)
    private fun gd(r: Float = 1f, g: Float = 1f, b: Float = 1f, c: Float = 0f, s: Float = 1f, br: Float = 0f) = Grade(r, g, b, c, s, br)

    private val grades: Map<String, Grade> = mapOf(
        "cin_warm" to gd(1.1f, 1f, 0.9f, 0.15f), "cin_cool" to gd(0.9f, 1f, 1.12f, 0.15f), "teal_orange" to gd(1.12f, 1f, 0.88f, 0.2f, 1.2f),
        "vintage" to gd(1.1f, 1f, 0.8f, -0.1f, 0.7f), "bw" to gd(c = 0.1f, s = 0f), "noir" to gd(c = 0.35f, s = 0f, br = -0.05f), "sepia" to gd(1.2f, 1f, 0.75f, s = 0.5f),
        "faded" to gd(c = -0.2f, s = 0.75f, br = 0.06f), "vivid" to gd(c = 0.1f, s = 1.6f), "golden" to gd(1.15f, 1.05f, 0.8f, s = 1.2f), "sunset" to gd(1.2f, 0.95f, 0.85f, s = 1.3f),
        "night" to gd(0.8f, 0.9f, 1.2f, 0.1f, br = -0.1f), "cyber" to gd(0.9f, 1f, 1.25f, 0.2f, 1.8f), "moody" to gd(0.95f, 1f, 1.05f, 0.2f, 0.8f, -0.08f),
        "highkey" to gd(c = -0.1f, s = 0.9f, br = 0.18f), "kodak" to gd(1.1f, 1.03f, 0.85f, 0.1f, 1.15f), "fuji" to gd(0.95f, 1.05f, 1f, 0.05f, 1.2f),
        "matte" to gd(c = -0.25f, s = 0.85f, br = 0.05f), "pastel" to gd(c = -0.2f, s = 0.7f, br = 0.12f), "underwater" to gd(0.75f, 1.05f, 1.2f, s = 1.1f),
        "desert" to gd(1.2f, 1.05f, 0.8f, s = 1.1f), "pinkish" to gd(1.15f, 0.95f, 1.05f, s = 1.1f), "violet_night" to gd(1f, 0.85f, 1.2f, 0f, 1.2f, -0.08f),
        "teal_dark" to gd(0.85f, 1f, 1.05f, 0.2f, 1f, -0.1f), "golden_hour" to gd(1.18f, 1.05f, 0.8f, 0f, 1.2f, 0.04f), "moonlight" to gd(0.85f, 0.95f, 1.15f, br = -0.08f),
        "dark_academia" to gd(1.05f, 0.98f, 0.85f, 0.2f, 0.7f, -0.05f), "tokyo" to gd(1.05f, 0.9f, 1.15f, 0.15f, 1.5f), "clean_bright" to gd(c = 0.05f, s = 1.1f, br = 0.1f),
        "autumn" to gd(1.2f, 1f, 0.75f, s = 1.2f), "ice" to gd(0.85f, 1f, 1.2f, 0f, 0.8f, 0.05f), "forest" to gd(0.9f, 1.1f, 0.85f, s = 1.2f), "colorpop" to gd(c = 0.15f, s = 1.9f),
        "dreamy" to gd(c = -0.15f, s = 1.1f, br = 0.1f), "glow" to gd(c = -0.05f, s = 1.1f, br = 0.12f), "neon" to gd(c = 0.25f, s = 1.9f, br = -0.04f),
        "auto_grade" to gd(c = 0.1f, s = 1.15f, br = 0.03f), "auto_natural" to gd(c = 0.05f, s = 1.05f), "auto_vivid" to gd(c = 0.12f, s = 1.4f), "auto_hdr" to gd(c = 0.25f, s = 1.3f, br = 0.02f),
        "hdr_nat" to gd(c = 0.12f, s = 1.15f), "hdr_strong" to gd(c = 0.3f, s = 1.35f), "hdr_cine" to gd(0.95f, 1f, 1.05f, 0.22f, 1.1f), "hdr_gold" to gd(1.12f, 1.04f, 0.85f, 0.2f, 1.25f),
        "enh_all" to gd(c = 0.12f, s = 1.2f, br = 0.04f), "enh_colors" to gd(s = 1.4f), "enh_light" to gd(br = 0.12f), "enh_shadows" to gd(c = -0.1f, br = 0.08f),
        "pro_split_tone" to gd(1.05f, 1f, 0.95f, 0.15f), "pro_scurve" to gd(c = 0.3f), "pro_skin_glow" to gd(1.08f, 1.02f, 0.97f, -0.05f, 1f, 0.06f), "pro_clarity" to gd(c = 0.2f),
        "pro_local_contrast" to gd(c = 0.25f, s = 1.1f), "pro_bleach" to gd(c = 0.3f, s = 0.5f), "pro_punch" to gd(c = 0.25f, s = 1.3f), "pro_hdr_look" to gd(c = 0.3f, s = 1.3f),
        "pro_soft_light" to gd(c = -0.15f, s = 0.95f, br = 0.08f), "pro_portra" to gd(1.06f, 1f, 0.92f, 0.05f, 0.9f), "pro_airy" to gd(c = -0.2f, s = 0.9f, br = 0.15f),
    )

    private fun gradeEffects(g: Grade): List<Effect> {
        val l = ArrayList<Effect>()
        if (g.r != 1f || g.g != 1f || g.b != 1f) l.add(RgbAdjustment.Builder().setRedScale(g.r).setGreenScale(g.g).setBlueScale(g.b).build())
        if (g.c != 0f) l.add(Contrast(g.c))
        if (g.s != 1f) l.add(HslAdjustment.Builder().adjustSaturation((g.s - 1f) * 100f).build())
        if (g.br != 0f) l.add(Brightness(g.br))
        return l
    }

    // ---- أدوات رياضية للحركة/الإيقاع (120 نبضة بالدقيقة = نصف ثانية للنبضة) ----
    private fun ph(t: Long, period: Double = 0.5): Float = (((t / 1e6) % period) / period).toFloat()
    private fun sec(t: Long): Double = t / 1e6
    private fun mt(f: (Long) -> Matrix): Effect = MatrixTransformation { t -> f(t) }
    private fun rgb(f: (Long) -> FloatArray): Effect = RgbMatrix { t, _ -> f(t) }
    private fun gain(r: Float, g: Float, b: Float) = floatArrayOf(r, 0f, 0f, 0f, 0f, g, 0f, 0f, 0f, 0f, b, 0f, 0f, 0f, 0f, 1f)
    private fun gainAll(x: Float) = gain(x, x, x)
    private fun satMat(s: Float): FloatArray {
        val lr = 0.2126f; val lg = 0.7152f; val lb = 0.0722f
        val a = (1 - s)
        // مصفوفة صفّية 3x3 → عمودية 4x4
        val m = arrayOf(floatArrayOf(a * lr + s, a * lg, a * lb), floatArrayOf(a * lr, a * lg + s, a * lb), floatArrayOf(a * lr, a * lg, a * lb + s))
        return col(m)
    }
    private fun hueMat(deg: Float): FloatArray {
        val c = cos(deg * PI / 180.0).toFloat(); val s = sin(deg * PI / 180.0).toFloat()
        val m = arrayOf(
            floatArrayOf(0.213f + c * 0.787f - s * 0.213f, 0.715f - c * 0.715f - s * 0.715f, 0.072f - c * 0.072f + s * 0.928f),
            floatArrayOf(0.213f - c * 0.213f + s * 0.143f, 0.715f + c * 0.285f + s * 0.140f, 0.072f - c * 0.072f - s * 0.283f),
            floatArrayOf(0.213f - c * 0.213f - s * 0.787f, 0.715f - c * 0.715f + s * 0.715f, 0.072f + c * 0.928f + s * 0.072f),
        )
        return col(m)
    }
    private fun col(m: Array<FloatArray>): FloatArray = floatArrayOf(
        m[0][0], m[1][0], m[2][0], 0f, m[0][1], m[1][1], m[2][1], 0f, m[0][2], m[1][2], m[2][2], 0f, 0f, 0f, 0f, 1f)

    private fun scale(s: Float) = Matrix().apply { setScale(s, s) }
    private fun shake(t: Long, amp: Float, hz: Double, s: Float = 1.06f) = Matrix().apply { setScale(s, s); postTranslate((amp * sin(sec(t) * hz * 2 * PI)).toFloat(), (amp * 0.7f * sin(sec(t) * hz * 1.7 * 2 * PI)).toFloat()) }

    private fun single(vararg e: Effect) = Spec(video = e.toList())
    private fun ratio(r: Float) = Spec(last = listOf(Presentation.createForAspectRatio(r, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP)))
    private fun height(h: Int) = Spec(last = listOf(Presentation.createForHeight(h)))
    private fun speed(s: Float): Spec { val p = SonicAudioProcessor(); p.setSpeed(s); p.setPitch(1f); return Spec(video = listOf(SpeedChangeEffect(s)), audio = listOf(p)) }

    fun build(id: String, durUs: Long): Spec? {
        grades[id]?.let { return Spec(video = gradeEffects(it)) }
        return when (id) {
            // إيديت مصممين (ريتم)
            "flash_beat" -> single(rgb { t -> gainAll(1f + 1.6f * (1f - ph(t)).let { it * it * it }) })
            "zoom_beat" -> single(mt { t -> scale(1f + 0.12f * (1f - ph(t))) })
            "shake_beat" -> single(mt { t -> val k = (1f - ph(t)); shake(t, 0.05f * k, 45.0) })
            "hue_pulse" -> single(rgb { t -> hueMat(60f * (1f - ph(t))) })
            "strobe" -> single(rgb { t -> gainAll(if ((sec(t) * 8).toInt() % 2 == 0) 1f else 0.2f) })
            "flip_alt" -> single(mt { t -> Matrix().apply { setScale(if ((sec(t) / 0.5).toInt() % 2 == 0) 1f else -1f, 1f) } })
            "zoom_cut" -> single(mt { t -> scale(if ((sec(t) / 1.0).toInt() % 2 == 0) 1f else 1.5f) })
            "glitch_burst", "glitch" -> Spec(video = listOf(mt { t -> if (ph(t, 1.0) < 0.2f) shake(t, 0.07f, 60.0, 1.05f) else Matrix() }, rgb { t -> if (ph(t, 1.0) < 0.2f) hueMat(90f) else gainAll(1f) }))
            "tilt" -> single(mt { t -> Matrix().apply { setScale(1.12f, 1.12f); postRotate((4f * sin(sec(t) * 2 * PI)).toFloat()) } })
            "rgb_beat" -> single(rgb { t -> val k = 1f - ph(t); gain(1f + 0.6f * k, 1f, 1f + 0.6f * (1f - k) * 0.5f) })
            "bw_flash" -> single(rgb { t -> satMat(if (ph(t) < 0.2f) 0f else 1f) })
            "bloom_beat" -> single(rgb { t -> gainAll(1f + 0.5f * (1f - ph(t))) })
            "contrast_pulse" -> single(rgb { t -> gainAll(1f + 0.35f * (1f - ph(t))) })
            "zoom_out_beat" -> single(mt { t -> scale(1.15f - 0.15f * (1f - ph(t))) })
            "spin_beat" -> single(mt { t -> Matrix().apply { setScale(1.4f, 1.4f); postRotate(360f * ph(t, 2.0)) } })
            "whip_zoom" -> single(mt { t -> scale(1f + 0.6f * (1f - ph(t, 1.0)).let { it * it }) })
            "vert_shake" -> single(mt { t -> Matrix().apply { setScale(1.06f, 1.06f); postTranslate(0f, (0.05f * (1f - ph(t)) * sin(sec(t) * 50)).toFloat()) } })
            "sat_drop" -> single(rgb { t -> satMat(0.3f + 0.7f * abs(sin(sec(t) * PI)).toFloat()) })
            "red_flash" -> single(rgb { t -> val k = 1f - ph(t); gain(1f + 0.9f * k, 1f - 0.4f * k, 1f - 0.4f * k) })
            "color_flicker" -> single(rgb { t -> hueMat((25f * sin(sec(t) * 14)).toFloat()) })
            // حركة
            "shake" -> single(mt { t -> shake(t, 0.03f, 18.0) })
            "pulse", "punch" -> single(mt { t -> scale(1f + 0.08f * abs(sin(sec(t) * PI * 2)).toFloat()) })
            "spin" -> single(mt { t -> Matrix().apply { setScale(1.4f, 1.4f); postRotate((360f * ph(t, 6.0))) } })
            "hue_cycle" -> single(rgb { t -> hueMat((sec(t) * 90.0 % 360.0).toFloat()) })
            "zoom_in" -> single(mt { t -> scale(min(1.6f, 1f + 0.05f * sec(t).toFloat())) })
            "zoom_out" -> single(mt { t -> scale(max(1f, 1.5f - 0.05f * sec(t).toFloat())) })
            "breath_zoom" -> single(mt { t -> scale(1.06f + 0.06f * sin(sec(t) * PI * 0.8).toFloat()) })
            "drift_pan" -> single(mt { t -> Matrix().apply { setScale(1.15f, 1.15f); postTranslate((0.06f * sin(sec(t) * 0.7)).toFloat(), (0.03f * sin(sec(t) * 0.5)).toFloat()) } })
            "film_flicker" -> single(rgb { t -> gainAll(0.92f + 0.08f * sin(sec(t) * 37).toFloat()) })
            "mirror", "flip_h" -> single(mt { Matrix().apply { setScale(-1f, 1f) } })
            "flip_v" -> single(mt { Matrix().apply { setScale(1f, -1f) } })
            // مونتاج
            "speed2" -> speed(2f); "speed3" -> speed(3f); "slow05" -> speed(0.5f); "slow025" -> speed(0.25f)
            "rot_r" -> single(ScaleAndRotateTransformation.Builder().setRotationDegrees(-90f).build())
            "rot_l" -> single(ScaleAndRotateTransformation.Builder().setRotationDegrees(90f).build())
            "ratio916" -> ratio(9f / 16f); "ratio11", "crop_sq" -> ratio(1f); "ratio45" -> ratio(4f / 5f); "ratio169" -> ratio(16f / 9f)
            "zoom15" -> single(ScaleAndRotateTransformation.Builder().setScale(1.5f, 1.5f).build())
            "letterbox" -> single(Crop(-1f, 1f, -0.72f, 0.72f))
            "fade_io" -> if (durUs > 2_000_000) single(rgb { t -> gainAll(min(1f, min(t / 1_000_000f, (durUs - t) / 1_000_000f)).coerceIn(0f, 1f)) }) else null
            // الدقة
            "r720" -> height(720); "r1080" -> height(1080); "r2k" -> height(1440); "r4k" -> height(2160)
            // صوت وتصدير
            "mute" -> Spec(mute = true)
            "out_cover" -> Spec(cover = true)
            else -> null
        }
    }

    fun has(id: String): Boolean = grades.containsKey(id) || build(id, 10_000_000L) != null
}

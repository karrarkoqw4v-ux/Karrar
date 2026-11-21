package com.vio.studio

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*

data class Tool(val id: String, val name: String, val icon: ImageVector, val brush: Brush)
data class Cat(val name: String, val tools: List<Tool>)
data class Section(val id: String, val title: String, val sub: String, val icon: ImageVector, val brush: Brush, val cats: List<Cat>, val needsServer: Boolean)

private fun t(id: String, n: String, i: ImageVector, b: Brush = C.brand) = Tool(id, n, i, b)

object Catalog {
    val video = Section("video", "مونتاج الفيديو", "تأثيرات وألوان وقوالب وموسيقى", Icons.Rounded.Movie, C.brand, listOf(
        Cat("تأثيرات", listOf(t("glitch", "غلتش", Icons.Rounded.Bolt), t("zoom", "زوم نبض", Icons.Rounded.ZoomIn, C.cool), t("shake", "اهتزاز", Icons.Rounded.Vibration, C.warm), t("slow", "حركة بطيئة", Icons.Rounded.Speed, C.mint), t("rgb", "انزياح RGB", Icons.Rounded.Layers), t("flash", "وميض", Icons.Rounded.FlashOn, C.warm))),
        Cat("ألوان", listOf(t("teal", "تيل وبرتقالي", Icons.Rounded.Palette, C.cool), t("neon", "نيون", Icons.Rounded.Lightbulb), t("vint", "كلاسيك", Icons.Rounded.CameraRoll, C.warm), t("bw", "أبيض وأسود", Icons.Rounded.Contrast, C.mint), t("warm", "ذهبي دافئ", Icons.Rounded.WbSunny, C.warm), t("cold", "أزرق بارد", Icons.Rounded.AcUnit, C.cool))),
        Cat("قوالب", listOf(t("reels", "ريلز عمودي", Icons.Rounded.Smartphone), t("story", "ستوري", Icons.Rounded.AutoStories, C.warm), t("intro", "مقدمة سينمائية", Icons.Rounded.Theaters, C.cool), t("beat", "على الإيقاع", Icons.Rounded.GraphicEq, C.mint))),
        Cat("صوت", listOf(t("music", "إضافة موسيقى", Icons.Rounded.MusicNote), t("vol", "ضبط الصوت", Icons.Rounded.VolumeUp, C.cool), t("fade", "تدرّج الصوت", Icons.Rounded.Tune, C.mint))),
    ), true)

    val photo = Section("photo", "تعديل الصور", "فلاتر وأدوات تعمل على جهازك مباشرة", Icons.Rounded.PhotoCamera, C.warm, listOf(
        Cat("فلاتر", Imaging.filters.map { t(it.id, it.name, Icons.Rounded.Filter, it.brush) }),
    ), false)

    val pro = Section("pro", "الاحترافي", "جودة 4K وتحسين وتصحيح ألوان", Icons.Rounded.WorkspacePremium, C.cool, listOf(
        Cat("جودة", listOf(t("up4k", "رفع الدقة 4K", Icons.Rounded.HighQuality), t("den", "إزالة التشويش", Icons.Rounded.BlurOn, C.cool), t("sharp", "حدّة ووضوح", Icons.Rounded.Details, C.mint), t("stab", "تثبيت الصورة", Icons.Rounded.Videocam, C.warm))),
        Cat("ألوان", listOf(t("grade", "تصحيح الألوان", Icons.Rounded.ColorLens), t("hdr", "HDR", Icons.Rounded.Hdr, C.warm), t("lut", "LUT سينمائي", Icons.Rounded.Gradient, C.cool))),
    ), true)

    val ai = Section("ai", "مونتاج الذكاء الاصطناعي", "إزالة الخلفية وتأثيرات ذكية", Icons.Rounded.AutoAwesome, C.brand, listOf(
        Cat("خلفية", listOf(t("rmbg", "إزالة الخلفية", Icons.Rounded.ContentCut), t("blurbg", "تمويه الخلفية", Icons.Rounded.BlurCircular, C.cool), t("repbg", "استبدال الخلفية", Icons.Rounded.Wallpaper, C.warm), t("neonbg", "هالة نيون", Icons.Rounded.Lightbulb, C.mint))),
        Cat("ذكي", listOf(t("autocut", "قص تلقائي", Icons.Rounded.ContentCut, C.warm), t("subs", "ترجمة وتعليق", Icons.Rounded.ClosedCaption, C.cool), t("face", "تحسين الوجه", Icons.Rounded.Face), t("track", "تتبع الحركة", Icons.Rounded.CenterFocusStrong, C.mint))),
    ), true)

    fun get(id: String) = when (id) { "video" -> video; "photo" -> photo; "pro" -> pro; else -> ai }
}

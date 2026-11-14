package com.turbo.rashq

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.SvgDecoder
import com.airbnb.lottie.compose.*
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import java.io.File
import java.util.zip.GZIPInputStream

object C {
    val bg = Color(0xFF0B1020); val card = Color(0xFF141B34); val card2 = Color(0xFF1B2444)
    val text = Color(0xFFF1F5FF); val sub = Color(0xFF93A0C8); val line = Color(0xFF273157)
    val blue = Color(0xFF3B82F6); val blue2 = Color(0xFF2563EB); val green = Color(0xFF22C55E); val green2 = Color(0xFF16A34A)
    val red = Color(0xFFEF4444); val red2 = Color(0xFFDC2626); val plain = Color(0xFF2A3354); val plain2 = Color(0xFF202845)
    val gold = Color(0xFFFBBF24)
}

val Tajawal = FontFamily(Font(R.font.tajawal_regular, FontWeight.Normal), Font(R.font.tajawal_medium, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.Bold), Font(R.font.tajawal_extrabold, FontWeight.ExtraBold))

object Cat { val map: Map<String, String> by lazy { runCatching { val a = org.json.JSONArray(Repo.ctx.assets.open("tge_catalog.json").bufferedReader().readText())
    (0 until a.length()).associate { i -> a.getJSONObject(i).let { o -> o.getString("e").replace("\uFE0F", "") to o.getString("id") } } }.getOrDefault(emptyMap()) }
    fun id(ch: String): String? = map[ch.replace("\uFE0F", "")] }

@Composable
fun RichText(text: String, size: Int = 14, color: Color = C.text, weight: FontWeight = FontWeight.Normal, modifier: Modifier = Modifier, align: TextAlign? = null) {
    val raw = remember(text) { Regex("\\{c:([^}]+)\\}").replace(text) { m -> Cat.id(m.groupValues[1])?.let { "{e:$it}" } ?: m.groupValues[1] } }
    val re = Regex("\\{e:(\\d+)\\}")
    val ids = re.findAll(raw).map { it.groupValues[1] }.toSet()
    val s = buildAnnotatedString {
        var last = 0
        re.findAll(raw).forEach { m -> append(raw.substring(last, m.range.first)); appendInlineContent(m.groupValues[1], "\u25A1"); last = m.range.last + 1 }
        append(raw.substring(last))
    }
    val ph = (size + 6).sp
    val inline = ids.associateWith { id -> InlineTextContent(Placeholder(ph, ph, PlaceholderVerticalAlign.TextCenter)) { PremiumEmoji(id, (size + 6).dp) } }
    Text(s, inlineContent = inline, fontSize = size.sp, color = color, fontWeight = weight, fontFamily = Tajawal, modifier = modifier, textAlign = align, lineHeight = (size + 11).sp)
}

lateinit var Img: ImageLoader
object Bundled { val set: Set<String> by lazy { Repo.ctx.assets.list("tge")?.toSet() ?: emptySet() } }
fun toast(m: String) = Toast.makeText(Repo.ctx, m, Toast.LENGTH_SHORT).show()
fun buildLoader(c: android.content.Context) = ImageLoader.Builder(c).components { add(SvgDecoder.Factory()); if (android.os.Build.VERSION.SDK_INT >= 28) add(coil.decode.ImageDecoderDecoder.Factory()) else add(GifDecoder.Factory()) }.build()

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val cs = darkColorScheme(primary = C.blue, background = C.bg, surface = C.card, onSurface = C.text, onBackground = C.text, onPrimary = Color.White)
    val base = TextStyle(fontFamily = Tajawal)
    val ty = Typography(bodyLarge = base, bodyMedium = base, bodySmall = base, titleLarge = base, titleMedium = base, titleSmall = base,
        labelLarge = base, labelMedium = base, labelSmall = base, headlineSmall = base, headlineMedium = base)
    MaterialTheme(colorScheme = cs, typography = ty, content = content)
}

@Composable
fun T(s: String, size: Int = 14, weight: FontWeight = FontWeight.Normal, color: Color = C.text, modifier: Modifier = Modifier,
      align: TextAlign? = null, maxLines: Int = Int.MAX_VALUE) =
    Text(s, fontSize = size.sp, fontWeight = weight, color = color, modifier = modifier, textAlign = align, maxLines = maxLines,
        overflow = TextOverflow.Ellipsis, fontFamily = Tajawal)

@Composable
fun PremiumEmoji(spec: String, size: Dp = 26.dp) {
    val custom = spec.length >= 10 && spec.all { it.isDigit() }
    if (!custom) {
        AsyncImage("file:///android_asset/emoji/e_$spec.webp", null, modifier = Modifier.size(size), imageLoader = Img)
        return
    }
    val row = remember(spec) { Repo.emojiPath(spec) }
    when {
        "$spec.json" in Bundled.set -> {
            val comp by rememberLottieComposition(LottieCompositionSpec.Asset("tge/$spec.json"))
            LottieAnimation(comp, Modifier.size(size), iterations = LottieConstants.IterateForever)
        }
        "$spec.webp" in Bundled.set -> AsyncImage("file:///android_asset/tge/$spec.webp", null, modifier = Modifier.size(size), imageLoader = Img)
        row == null -> Box(Modifier.size(size))
        row.second == "tgs" -> {
            val json = remember(row.first) { runCatching { GZIPInputStream(File(row.first).inputStream()).readBytes().decodeToString() }.getOrDefault("{}") }
            val comp by rememberLottieComposition(LottieCompositionSpec.JsonString(json))
            LottieAnimation(comp, Modifier.size(size), iterations = LottieConstants.IterateForever)
        }
        else -> AsyncImage(File(row.first), null, modifier = Modifier.size(size), imageLoader = Img)
    }
}

fun styleColors(s: String): Pair<Color, Color> = when (s) {
    "primary" -> C.blue to C.blue2; "success" -> C.green to C.green2; "danger" -> C.red to C.red2
    else -> C.plain to C.plain2
}

/** Glass button: translucent surface, light border, colored accent edge, same Telegram colors. */
@Composable
fun TgButton(text: String, style: String, emoji: String?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val (a, _) = styleColors(style)
    val fs = when { text.length <= 8 -> 14; text.length <= 12 -> 13; text.length <= 16 -> 12; text.length <= 20 -> 11; else -> 10 }
    val shape = RoundedCornerShape(15.dp)
    Box(modifier.height(46.dp).clip(shape).background(Brush.horizontalGradient(listOf(Color.White.copy(alpha = .07f), a.copy(alpha = .20f))))
        .border(1.dp, Color.White.copy(alpha = .14f), shape).clickable(onClick = onClick).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(4.dp).background(a))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (emoji != null) { PremiumEmoji(emoji, 20.dp); Spacer(Modifier.width(6.dp)) }
            Text(text, fontSize = fs.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = Tajawal, maxLines = 1, softWrap = false, overflow = TextOverflow.Clip, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun ButtonRows(cat: String, onRoute: (String) -> Unit) {
    val tick = Session.tick.intValue
    val rows = remember(tick, cat) { Defs.layout(cat) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { k ->
                    var label = Defs.name(k)
                    if (k == "btn_stats") label = "$label : ${Repo.doneCount()}"
                    TgButton(label, Defs.style(k), Defs.emoji(k), Modifier.weight(1f)) { onRoute(Defs.byKey[k]!!.route) }
                }
            }
        }
    }
}

@Composable
fun Page(title: String, onBack: (() -> Unit)? = Nav::back, scroll: Boolean = true, actions: @Composable RowScope.() -> Unit = {}, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().background(C.bg).drawBehind {
        drawRect(Brush.radialGradient(listOf(Color(0x551E3A8A), Color.Transparent), Offset(size.width * .9f, 0f), size.width * .9f))
        drawRect(Brush.radialGradient(listOf(Color(0x447C3AED), Color.Transparent), Offset(0f, size.height * .6f), size.width * .8f))
        drawRect(Brush.radialGradient(listOf(Color(0x4406B6D4), Color.Transparent), Offset(size.width, size.height), size.width * .8f))
    }.statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = C.text) } else Spacer(Modifier.width(12.dp))
            T(title, 18, FontWeight.ExtraBold, modifier = Modifier.weight(1f), maxLines = 1)
            actions()
        }
        val m = if (scroll) Modifier.weight(1f).verticalScroll(rememberScrollState()) else Modifier.weight(1f)
        Column(m.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White.copy(alpha = .06f)).border(1.dp, Color.White.copy(alpha = .12f), RoundedCornerShape(18.dp)).let { if (onClick != null) it.clickable(onClick = onClick) else it }
    Column(m.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

@Composable
fun Field(v: String, onChange: (String) -> Unit, label: String, password: Boolean = false, number: Boolean = false, modifier: Modifier = Modifier, single: Boolean = true) {
    OutlinedTextField(v, onChange, modifier.fillMaxWidth(), label = { T(label, 13, color = C.sub) }, singleLine = single,
        textStyle = TextStyle(fontFamily = Tajawal, fontSize = 15.sp, color = C.text),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (number) KeyboardType.Number else if (password) KeyboardType.Password else KeyboardType.Text),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = C.blue, unfocusedBorderColor = C.line, focusedContainerColor = C.card, unfocusedContainerColor = C.card))
}

@Composable
fun Btn(text: String, style: String = "primary", modifier: Modifier = Modifier, emoji: String? = null, onClick: () -> Unit) =
    TgButton(text, style, emoji, modifier.fillMaxWidth(), onClick)

@Composable
fun Chip(text: String, color: Color = C.blue) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = .18f)).padding(horizontal = 10.dp, vertical = 3.dp)) { T(text, 12, FontWeight.Bold, color) }
}

@Composable
fun Logo(cat: String, size: Dp = 52.dp) {
    val custom = Repo.get("logo_$cat")
    val brand = Defs.logos[cat]
    val col = Defs.logoColors[brand] ?: 0xFF3B82F6
    Box(Modifier.size(size).clip(RoundedCornerShape(size / 3.2f)).background(Color(col)), contentAlignment = Alignment.Center) {
        when {
            custom.isNotBlank() && File(custom).exists() -> AsyncImage(File(custom), null, modifier = Modifier.fillMaxSize(), imageLoader = Img, contentScale = ContentScale.Crop)
            brand != null -> AsyncImage("file:///android_asset/brands/$brand.svg", null, modifier = Modifier.size(size * 0.58f), imageLoader = Img, colorFilter = ColorFilter.tint(Color.White))
            else -> T(cat.take(1), (size.value * .45f).toInt(), FontWeight.ExtraBold, Color.White)
        }
    }
}

@Composable
fun ConfirmDialog(title: String, body: String, onOk: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = C.card,
        title = { T(title, 17, FontWeight.Bold) }, text = { T(body, 14, color = C.sub) },
        confirmButton = { TextButton({ onOk(); onDismiss() }) { T("تأكيد", 14, FontWeight.Bold, C.blue) } },
        dismissButton = { TextButton(onDismiss) { T("إلغاء", 14, color = C.sub) } })
}

fun fmtTime(ms: Long): String = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.US).format(java.util.Date(ms))

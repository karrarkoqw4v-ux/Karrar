package com.vio.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon

object C {
    val bg = Color(0xFF0B0818)
    val surface = Color(0xFF15102A)
    val surface2 = Color(0xFF1D1738)
    val stroke = Color(0xFF2B2450)
    val violet = Color(0xFF8B5CF6)
    val pink = Color(0xFFEC4899)
    val cyan = Color(0xFF22D3EE)
    val gold = Color(0xFFFBBF24)
    val green = Color(0xFF22C55E)
    val red = Color(0xFFEF4444)
    val hi = Color(0xFFF5F3FF)
    val lo = Color(0xFFA79FCB)
    val brand = Brush.horizontalGradient(listOf(violet, pink))
    val brandV = Brush.verticalGradient(listOf(violet, pink))
    val cool = Brush.horizontalGradient(listOf(Color(0xFF0EA5E9), violet))
    val warm = Brush.horizontalGradient(listOf(Color(0xFFF59E0B), pink))
    val mint = Brush.horizontalGradient(listOf(Color(0xFF10B981), cyan))
}

val Tajawal = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_medium, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.Bold),
    Font(R.font.tajawal_extrabold, FontWeight.ExtraBold),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val base = TextStyle(fontFamily = Tajawal, color = C.hi)
    MaterialTheme(
        colorScheme = darkColorScheme(primary = C.violet, secondary = C.pink, background = C.bg, surface = C.surface, onSurface = C.hi, onBackground = C.hi),
        typography = Typography(bodyLarge = base, bodyMedium = base, bodySmall = base, titleLarge = base, titleMedium = base, labelLarge = base),
        content = content,
    )
}

@Composable
fun T(text: String, size: Int = 14, color: Color = C.hi, weight: FontWeight = FontWeight.Medium, modifier: Modifier = Modifier, center: Boolean = false, maxLines: Int = Int.MAX_VALUE) {
    Text(
        text, modifier = modifier, color = color, fontSize = size.sp, fontWeight = weight, fontFamily = Tajawal, maxLines = maxLines,
        textAlign = if (center) androidx.compose.ui.text.style.TextAlign.Center else null, lineHeight = (size * 1.45f).sp,
    )
}

@Composable
fun Card(modifier: Modifier = Modifier, radius: Dp = 20.dp, fill: Color = C.surface, border: Color = C.stroke, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val m = modifier.clip(RoundedCornerShape(radius)).background(fill).border(1.dp, border, RoundedCornerShape(radius))
    Column(if (onClick != null) m.clickable { onClick() } else m, content = content)
}

@Composable
fun GradButton(text: String, modifier: Modifier = Modifier, brush: Brush = C.brand, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier.height(54.dp).clip(RoundedCornerShape(16.dp)).background(if (enabled) brush else Brush.horizontalGradient(listOf(C.stroke, C.stroke)))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            T(text, 16, Color.White, FontWeight.Bold)
        }
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, color: Color = C.hi, onClick: () -> Unit) {
    Box(
        modifier.height(54.dp).clip(RoundedCornerShape(16.dp)).border(1.5.dp, C.stroke, RoundedCornerShape(16.dp)).background(C.surface).clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            T(text, 15, color, FontWeight.Bold)
        }
    }
}

@Composable
fun IconBadge(icon: ImageVector, brush: Brush, size: Dp = 46.dp, iconSize: Dp = 24.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(14.dp)).background(brush), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(iconSize))
    }
}

/** شعار Vio: حلقة نيون متدرجة + زر تشغيل (يُرسم برمجياً بلا صور) */
@Composable
fun VioLogo(size: Dp = 120.dp) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension
        val c = Offset(s / 2, s / 2)
        drawCircle(Brush.verticalGradient(listOf(Color(0xFF1E1650), Color(0xFF080618))), radius = s * 0.47f, center = c)
        val sw = s * 0.07f
        drawCircle(
            Brush.sweepGradient(listOf(Color(0xFF22D3EE), C.violet, C.pink, C.gold, Color(0xFF22D3EE)), c),
            radius = s * 0.47f - sw / 2, center = c, style = Stroke(width = sw, cap = StrokeCap.Round),
        )
        val p = Path().apply {
            moveTo(s * 0.42f, s * 0.33f); lineTo(s * 0.42f, s * 0.67f); lineTo(s * 0.70f, s * 0.50f); close()
        }
        drawPath(p, Brush.verticalGradient(listOf(Color.White, Color(0xFFC4B5FD))))
    }
}

@Composable
fun ScreenBg(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier.fillMaxSize().background(C.bg).drawBehind {
            drawCircle(Brush.radialGradient(listOf(C.violet.copy(alpha = 0.35f), Color.Transparent), Offset(size.width * 0.15f, 0f), size.width * 0.9f), size.width, Offset(size.width * 0.15f, 0f))
            drawCircle(Brush.radialGradient(listOf(C.pink.copy(alpha = 0.22f), Color.Transparent), Offset(size.width, size.height), size.width), size.width, Offset(size.width, size.height))
        },
        content = content,
    )
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (selected) C.brand else Brush.horizontalGradient(listOf(C.surface2, C.surface2)))
            .border(1.dp, if (selected) Color.Transparent else C.stroke, RoundedCornerShape(50)).clickable { onClick() }.padding(horizontal = 16.dp, vertical = 9.dp),
    ) { T(text, 13, if (selected) Color.White else C.lo, if (selected) FontWeight.Bold else FontWeight.Medium) }
}

@Composable
fun Tag(text: String, color: Color) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.18f)).padding(horizontal = 8.dp, vertical = 3.dp)) { T(text, 10, color, FontWeight.Bold) }
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) { Box(Modifier.size(size).clip(CircleShape).background(color)) }

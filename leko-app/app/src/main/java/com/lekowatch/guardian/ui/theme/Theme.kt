package com.lekowatch.guardian.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val LekoColorScheme = lightColorScheme(
    primary = LekoBlue,
    onPrimary = LekoWhite,
    secondary = LekoTeal,
    onSecondary = LekoWhite,
    background = LekoWhite,
    onBackground = LekoInk,
    surface = LekoWhite,
    onSurface = LekoInk,
    error = LekoDanger,
)

private val LekoTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = LekoInk),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp, color = LekoInk),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.5.sp, color = LekoInk),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, color = LekoSubtext),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = LekoWhite),
)

@Composable
fun LekoGuardianTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LekoColorScheme,
        typography = LekoTypography,
        content = content,
    )
}

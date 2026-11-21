package com.vio.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Field(value: String, onChange: (String) -> Unit, label: String, icon: ImageVector, password: Boolean = false, keyboard: KeyboardType = KeyboardType.Text, modifier: Modifier = Modifier) {
    var show by remember { mutableStateOf(false) }
    Column(modifier) {
        T(label, Sz.Small, Color.White.copy(alpha = 0.75f), FontWeight.Medium, Modifier.padding(bottom = 6.dp, start = 4.dp, end = 4.dp))
        Row(
            Modifier.fillMaxWidth().height(56.dp).glass(16.dp, Color.White, 0.07f).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(22.dp))
            BasicTextField(
                value, onChange, singleLine = true, modifier = Modifier.weight(1f),
                textStyle = TextStyle(color = C.hi, fontSize = 15.sp, fontFamily = Tajawal),
                cursorBrush = SolidColor(C.pink),
                visualTransformation = if (password && !show) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboard),
            )
            if (password) Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, null, tint = C.lo, modifier = Modifier.size(22.dp).clickable { show = !show })
        }
    }
}

@Composable
fun TopBar(title: String, back: Boolean = true) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (back) Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).glass(14.dp).clickable { Nav.back() }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ArrowForward, null, tint = C.hi, modifier = Modifier.size(22.dp))
        }
        T(title, Sz.H2, C.hi, FontWeight.ExtraBold, Modifier.weight(1f))
    }
}

@Composable
fun BottomBar() {
    val items = listOf(Triple("home", "الرئيسية", Icons.Rounded.Home), Triple("fx/ed", "التأثيرات", Icons.Rounded.AutoFixHigh), Triple("templates", "القوالب", Icons.Rounded.Dashboard), Triple("account", "حسابي", Icons.Rounded.Person))
    Row(Modifier.fillMaxWidth().background(Color(0xFF0B0818)).padding(horizontal = 14.dp, vertical = 8.dp).glass(22.dp, Color.White, 0.08f).padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceAround) {
        items.forEach { (r, n, ic) ->
            val on = Nav.cur == r || (r == "fx/ed" && Nav.cur.startsWith("fx/"))
            Column(Modifier.clip(RoundedCornerShape(14.dp)).clickable { Nav.tab(r) }.padding(horizontal = 14.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.clip(RoundedCornerShape(50)).background(if (on) C.brand else androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))).padding(horizontal = 16.dp, vertical = 3.dp)) {
                    Icon(ic, null, tint = if (on) Color.White else Color.White.copy(alpha = 0.6f), modifier = Modifier.size(22.dp))
                }
                T(n, Sz.Tiny, if (on) Color.White else Color.White.copy(alpha = 0.6f), if (on) FontWeight.Bold else FontWeight.Medium)
            }
        }
    }
}

fun ago(t: Long): String {
    val m = (System.currentTimeMillis() - t) / 60000
    return when { m < 1 -> "الآن"; m < 60 -> "قبل $m دقيقة"; m < 1440 -> "قبل ${m / 60} ساعة"; else -> "قبل ${m / 1440} يوم" }
}

package com.vio.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier

@Composable
fun Router(r: String) {
    when {
        r == "welcome" -> WelcomeScreen()
        r == "login" -> LoginScreen()
        r == "register" -> RegisterScreen()
        r == "home" -> ScreenBg { HomeScreen() }
        r.startsWith("fx/") -> ScreenBg { FxScreen(r.removePrefix("fx/")) }
        r == "templates" -> ScreenBg { TemplatesScreen() }
        r == "photo" -> ScreenBg { PhotoScreen() }
        r == "link" -> ScreenBg { LinkScreen() }
        r == "account" -> ScreenBg { AccountScreen() }
        else -> ScreenBg { HomeScreen() }
    }
}

private val tabs = setOf("home", "templates", "account")

@Composable
fun AppRoot() {
    val r = Nav.cur
    val showBar = r in tabs || r.startsWith("fx/")
    Column(Modifier.fillMaxSize().background(C.bg)) {
        Box(Modifier.weight(1f)) { Router(r) }
        if (showBar) BottomBar()
    }
    if (Jobs.active) JobOverlay()
}

@Composable
fun JobOverlay() {
    Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xCC0B0818)), contentAlignment = androidx.compose.ui.Alignment.Center) {
        GlassCard(Modifier.padding(32.dp).fillMaxWidth(), 24.dp, 0.1f) {
            Column(Modifier.padding(24.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                T(Jobs.title, Sz.H3, C.hi, androidx.compose.ui.text.font.FontWeight.ExtraBold, center = true)
                T(Jobs.msg, Sz.Body, if (Jobs.finished && !Jobs.ok) C.gold else C.hi, center = true)
                if (!Jobs.finished) androidx.compose.material3.LinearProgressIndicator(progress = { Jobs.progress }, modifier = Modifier.fillMaxWidth().height(8.dp), color = C.pink, trackColor = C.stroke)
                else GlassButton("حسناً", Modifier.fillMaxWidth(), null, C.violet, 0.5f) { Jobs.active = false }
            }
        }
    }
}

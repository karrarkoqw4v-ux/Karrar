package com.vio.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun Router(r: String) {
    when {
        r == "welcome" -> WelcomeScreen()
        r == "login" -> LoginScreen()
        r == "register" -> RegisterScreen()
        r == "home" -> ScreenBg { HomeScreen() }
        r.startsWith("studio/") -> ScreenBg { StudioScreen(r.removePrefix("studio/")) }
        r == "link" -> ScreenBg { LinkScreen() }
        r == "account" -> ScreenBg { AccountScreen() }
        r == "settings" -> ScreenBg { SettingsScreen() }
        else -> ScreenBg { HomeScreen() }
    }
}

private val tabs = setOf("home", "link", "account")

@Composable
fun AppRoot() {
    val r = Nav.cur
    val showBar = r in tabs || r.startsWith("studio/")
    Column(Modifier.fillMaxSize().background(C.bg)) {
        Box(Modifier.weight(1f)) { Router(r) }
        if (showBar) BottomBar()
    }
}

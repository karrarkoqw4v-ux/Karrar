package com.turbo.rashq

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.asAndroidBitmap
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h851dp-xxhdpi")
class ShotsTest {
    @get:Rule val rule = createComposeRule()

    private fun shoot(name: String, route: String) {
        Nav.reset(); if (route != "home") Nav.go(route)
        Session.refresh()
        rule.waitForIdle(); Thread.sleep(1500); rule.waitForIdle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val f = File("build/shots-jvm/$name.png"); f.parentFile?.mkdirs()
        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun allScreens() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        Repo.init(ctx); Img = buildLoader(ctx); Flags.still = true
        Repo.register("كروري", "weswod", "", "", "1234"); Repo.seedShots(); Session.refresh()
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                AppTheme {
                    Column(Modifier.fillMaxSize().background(C.bg)) {
                        Box(Modifier.weight(1f)) { if (Session.user == null) AuthScreen() else Router(Nav.stack.last()) }
                        if (Session.user != null) BottomBar()
                    }
                }
            }
        }
        listOf("01_home" to "home", "02_rashq" to "menu/rashq", "03_games" to "menu/games", "04_tiktok_services" to "cat_tiktok", "05_pubg_store" to "cat_pubg",
            "06_points" to "menu/points", "07_recharge" to "menu/recharge", "08_account" to "menu/account", "09_settings" to "set", "10_colors" to "set/colors",
            "11_privacy" to "set/privacy", "12_notifications" to "notifs", "13_transfer" to "menu/transfer", "14_terms" to "terms", "15_orders" to "orders",
            "16_support" to "support", "17_messages" to "msg").forEach { (n, r) ->
            val route = when (r) { "cat_tiktok" -> "cat/تيك توك"; "cat_pubg" -> "cat/شدات ببجي موبايل"; else -> r }
            shoot(n, route)
        }
        Repo.login("admin", "1005"); Session.refresh()
        listOf("20_admin_home" to "admin", "21_admin_settings" to "admin/settings", "22_admin_gifts" to "admin/gifts", "23_admin_toggle" to "admin/toggle",
            "24_button_editor" to "admin/buttons", "25_admin_services" to "admin/services", "26_emoji_manager" to "admin/emoji", "27_admin_recharge" to "admin/recharge",
            "28_admin_texts" to "admin/texts", "29_admin_sites" to "admin/sites", "30_admin_fetch" to "admin/fetch", "31_admin_notify" to "admin/notify",
            "32_admin_accounts" to "admin/accounts").forEach { (n, r) -> shoot(n, r) }
        Repo.setCurrent(null); Session.refresh(); shoot("40_login", "home")
    }
}

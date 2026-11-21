package com.vio.studio

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private fun shoot(name: String, route: String) {
        Nav.reset(route)
        rule.waitForIdle(); Thread.sleep(1200); rule.waitForIdle()
        val v = rule.activity.window.decorView
        val w = if (v.width > 0) v.width else 1179; val h = if (v.height > 0) v.height else 2552
        v.measure(android.view.View.MeasureSpec.makeMeasureSpec(w, android.view.View.MeasureSpec.EXACTLY), android.view.View.MeasureSpec.makeMeasureSpec(h, android.view.View.MeasureSpec.EXACTLY))
        v.layout(0, 0, w, h)
        val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
        v.draw(android.graphics.Canvas(bmp))
        val f = File("build/shots-jvm/$name.png"); f.parentFile?.mkdirs()
        f.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun allScreens() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        Repo.init(ctx)
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) { AppTheme { AppRoot() } }
        }
        shoot("01_welcome", "welcome")
        shoot("02_login", "login")
        shoot("03_register", "register")
        Debug.regStage = 1; shoot("04_register_processing", "register")
        Debug.regStage = 2; shoot("05_register_success", "register")
        Debug.regStage = 0
        Repo.register("كرار", "karrar", "", "1234"); Repo.seedShots()
        shoot("06_home", "home")
        shoot("07_studio_video", "studio/video")
        shoot("08_studio_photo", "studio/photo")
        shoot("09_studio_pro", "studio/pro")
        shoot("10_studio_ai", "studio/ai")
        shoot("11_link", "link")
        shoot("12_account", "account")
        shoot("13_settings", "settings")
    }
}

package com.turbo.rashq

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Repo.init(this); Img = buildLoader(this); Notify.channel(this); Session.refresh()
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                AppTheme {
                    val ctx = this@MainActivity
                    // on-device delivery loop: shows system notifications for newly received items
                    LaunchedEffect(Session.user?.uid) {
                        while (true) {
                            val u = Session.user
                            if (u != null) {
                                val l = Repo.takeUnshown(u.uid)
                                if (l.isNotEmpty()) { l.forEach { Notify.show(ctx, it) }; Session.refresh() }
                            }
                            delay(2500)
                        }
                    }
                    BackHandler(Nav.stack.size > 1) { Nav.back() }
                    Box(Modifier.fillMaxSize()) {
                        if (Session.user == null) AuthScreen() else Router(Nav.stack.last())
                    }
                }
            }
        }
    }
}

@Composable
fun Router(r: String) {
    val u = Session.user
    when {
        r == "home" -> HomeScreen()
        r == "menu/account" -> AccountMenu()
        r.startsWith("menu/") -> MenuScreen(r.removePrefix("menu/"))
        r.startsWith("cat/") -> CatScreen(r.removePrefix("cat/"))
        r == "orders" -> OrdersScreen()
        r == "track" -> TrackScreen()
        r == "code" -> CodeScreen()
        r == "support" -> SupportScreen()
        r == "terms" -> TermsScreen()
        r == "wheel" -> WheelScreen()
        r == "subscribe" -> SubscribeScreen()
        r == "referral" -> ReferralScreen()
        r == "transfer:username" -> TransferScreen(false)
        r == "transfer:id" -> TransferScreen(true)
        r == "accinfo" -> AccountInfoScreen()
        r == "notifs" -> NotifsScreen()
        r.startsWith("admin") && u?.admin == true -> when (r) {
            "admin/stats" -> AdminStats(); "admin/services" -> AdminServices(); "admin/orders" -> AdminOrders(); "admin/users" -> AdminUsers()
            "admin/codes" -> AdminCodes(); "admin/notify" -> AdminNotify(); "admin/buttons" -> AdminButtons(); "admin/emoji" -> AdminEmoji()
            "admin/settings" -> AdminSettings(); "admin/backup" -> AdminBackup(); else -> AdminHome()
        }
        else -> HomeScreen()
    }
}

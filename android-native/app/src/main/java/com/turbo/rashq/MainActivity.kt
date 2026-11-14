package com.turbo.rashq

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.delay
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Repo.init(this); Img = buildLoader(this); Notify.channel(this)
        if (BuildConfig.DEBUG) intent.getStringExtra("shot_login")?.let { l ->
            if (l == "admin") Repo.login("admin", "1005")
            else if (Repo.userByUsername("weswod") == null) Repo.register("كروري", "weswod", "", "", "1234") else Repo.login("weswod", "1234")
            Repo.seedShots()
            Session.refresh()
            var r = intent.getStringExtra("shot_route") ?: "home"
            if (r == "cat_tiktok") r = "cat/تيك توك"; if (r == "cat_pubg") r = "cat/شدات ببجي موبايل"
            Nav.reset(); if (r != "home") Nav.go(r)
        }
        Session.refresh()
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
                    Column(Modifier.fillMaxSize().background(C.bg)) {
                        Box(Modifier.weight(1f)) { if (Session.user == null) AuthScreen() else Router(Nav.stack.last()) }
                        if (Session.user != null) BottomBar()
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

@Composable
fun BottomBar() {
    val ime = androidx.compose.foundation.layout.WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
    if (ime) return
    val cur = Nav.stack.last()
    val u = Session.user
    val unread = if (u != null) remember(Session.tick.intValue) { Repo.unreadCount(u.uid) } else 0
    val tabs = listOf(Triple("home", "الرئيسية", "5050874036788134809"), Triple("orders", "طلباتي", "5375338737028841420"),
        Triple("menu/recharge", "شحن", "5866369239740320716"), Triple("menu/account", "حسابي", "4956418939920843885"), Triple("notifs", "الإشعارات", "5251511475515712980"))
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().background(C.card).navigationBarsPadding().padding(vertical = 6.dp)) {
        tabs.forEach { (r, l, e) ->
            val sel = cur == r
            Column(Modifier.weight(1f).clickable { Nav.reset(); if (r != "home") Nav.go(r) }.padding(vertical = 2.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Box(Modifier.size(width = 52.dp, height = 28.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(if (sel) C.blue.copy(alpha = .25f) else Color.Transparent), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    PremiumEmoji(e, 22.dp)
                    if (r == "notifs" && unread > 0) Box(Modifier.align(androidx.compose.ui.Alignment.TopEnd).size(14.dp).clip(androidx.compose.foundation.shape.CircleShape).background(C.red), contentAlignment = androidx.compose.ui.Alignment.Center) { T("$unread", 8, FontWeight.Bold, Color.White) }
                }
                T(l, 10, if (sel) FontWeight.Bold else FontWeight.Normal, if (sel) C.blue else C.sub)
            }
        }
    }
}

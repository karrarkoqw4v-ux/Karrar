package com.turbo.rashq

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

object Session {
    val tick = mutableIntStateOf(0)
    var user by mutableStateOf<User?>(null)
    fun refresh() { user = Repo.current(); tick.intValue++ }
}

object Nav {
    val stack = mutableStateListOf("home")
    fun go(r: String) { stack.add(r) }
    fun back() { if (stack.size > 1) stack.removeAt(stack.size - 1) }
    fun reset() { stack.clear(); stack.add("home") }
}

fun openUrl(u: String) {
    if (u.isBlank()) { toast("لم يحدد المطور الرابط بعد"); return }
    runCatching { Repo.ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(if (u.startsWith("http")) u else "https://$u")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

fun doRoute(r: String) {
    val u = Session.user ?: return
    when {
        r.startsWith("menu:") -> Nav.go("menu/" + r.removePrefix("menu:"))
        r.startsWith("cat:") -> Nav.go("cat/" + r.removePrefix("cat:"))
        r == "tamweel" -> Nav.go("cat/تمويل قناتك")
        r == "channels" -> Nav.go("orders")
        r.startsWith("url:") -> openUrl(Repo.get(r.removePrefix("url:")))
        r == "stats" -> toast("طلبات أُنجزت: ${Repo.doneCount()}")
        r.startsWith("gift:") -> {
            val k = r.removePrefix("gift:")
            val err = if (k == "daily") Repo.claim(u, "يومية", 24, Repo.getLong("gift_daily", 5)) else Repo.claim(u, "أسبوعية", 168, Repo.getLong("gift_weekly", 25))
            if (err == null) { toast("تمت إضافة الهدية إلى رصيدك"); Repo.pushNotif(u.uid, "هدية 🎁", "تمت إضافة هديتك"); Session.refresh() } else toast(err)
        }
        else -> Nav.go(r)
    }
}

@Composable
fun AuthScreen() {
    var mode by remember { mutableStateOf("choose") }
    var name by remember { mutableStateOf("") }; var user by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }; var err by remember { mutableStateOf("") }
    Page("Turbo Rashq", onBack = if (mode == "choose") null else ({ mode = "choose"; err = "" })) {
        Spacer(Modifier.height(8.dp))
        Box(Modifier.align(Alignment.CenterHorizontally).size(96.dp).clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF06B6D4)))), contentAlignment = Alignment.Center) { PremiumEmoji("1f680", 56.dp) }
        T("تيربو رشق", 26, FontWeight.ExtraBold, modifier = Modifier.align(Alignment.CenterHorizontally))
        T("خدمات الرشق وشحن الألعاب في مكان واحد", 13, color = C.sub, modifier = Modifier.align(Alignment.CenterHorizontally))
        when (mode) {
            "choose" -> { Spacer(Modifier.height(16.dp)); Btn("إنشاء حساب", "primary", emoji = "2728") { mode = "register" }; Btn("لدي حساب بالفعل", "success", emoji = "1f464") { mode = "login" } }
            "register" -> {
                Field(name, { name = it }, "الاسم"); Field(user, { user = it }, "اسم المستخدم (إنجليزي)")
                Field(phone, { phone = it }, "رقم الهاتف", number = true); Field(email, { email = it }, "البريد (اختياري)")
                Field(pass, { pass = it }, "كلمة السر", password = true)
                if (err.isNotBlank()) T(err, 13, color = C.red)
                Btn("إنشاء الحساب", "primary") { val e = Repo.register(name, user, phone, email, pass); if (e == null) { Session.refresh() } else err = e }
            }
            else -> {
                Field(user, { user = it }, "اسم المستخدم أو البريد أو الأيدي"); Field(pass, { pass = it }, "كلمة السر", password = true)
                if (err.isNotBlank()) T(err, 13, color = C.red)
                Btn("دخول", "success") { val e = Repo.login(user, pass); if (e == null) Session.refresh() else err = e }
            }
        }
    }
}

@Composable
fun HomeScreen() {
    val u = Session.user ?: return
    val tick = Session.tick.intValue
    val unread = remember(tick) { Repo.unreadCount(u.uid) }
    Page("تيربو رشق", onBack = null, actions = {
        Box { IconButton({ Nav.go("notifs") }) { Icon(Icons.Filled.Notifications, null, tint = C.text) }
            if (unread > 0) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(18.dp).clip(RoundedCornerShape(9.dp)).background(C.red), contentAlignment = Alignment.Center) { T("$unread", 10, FontWeight.Bold, Color.White) } }
    }) {
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(Color(0xFF2563EB), Color(0xFF0EA5E9)))).padding(16.dp)) {
            T("أهلاً ${u.name}", 14, color = Color.White.copy(alpha = .85f))
            T("${u.balance} نقطة", 28, FontWeight.ExtraBold, Color.White)
            T("ID: ${u.uid}  •  @${u.username}", 12, color = Color.White.copy(alpha = .8f))
        }
        ButtonRows("main") { doRoute(it) }
        if (u.admin) Btn("لوحة المطور", "danger", emoji = "1f6e0") { Nav.go("admin") }
    }
}

@Composable
fun MenuScreen(cat: String) {
    Page(Defs.titles[cat] ?: cat) { ButtonRows(cat) { doRoute(it) } }
}

@Composable
fun AccountMenu() {
    Page("حسابي") { Btn("بياناتي وسجل الرصيد", "primary", emoji = "1f464") { Nav.go("accinfo") }; ButtonRows("account") { doRoute(it) } }
}

@Composable
fun CatScreen(cat: String) {
    val u = Session.user ?: return
    val tick = Session.tick.intValue
    val list = remember(tick, cat) { Repo.services(cat) }
    var pick by remember { mutableStateOf<Service?>(null) }
    Page(cat) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Logo(cat, 56.dp); Column { T(cat, 18, FontWeight.ExtraBold); T("${list.size} خدمة متاحة", 12, color = C.sub) }
        }
        if (list.isEmpty()) Card { T("لا توجد خدمات هنا بعد. يضيفها المطور من لوحة التحكم.", 13, color = C.sub, align = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        list.forEach { s ->
            Card(onClick = { pick = s }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Logo(cat, 40.dp); Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) { T(s.name, 15, FontWeight.Bold); T(if (s.kind == "per1000") "لكل 1000 • الحد الأدنى ${s.minQty}" else "سعر ثابت", 12, color = C.sub) }
                    Chip("${s.price} نقطة", C.green)
                }
            }
        }
    }
    pick?.let { s ->
        var target by remember { mutableStateOf("") }; var qty by remember { mutableStateOf(s.minQty.toString()) }
        val q = qty.toLongOrNull() ?: 0
        val cost = if (s.kind == "per1000") (s.price * q + 999) / 1000 else s.price
        AlertDialog(onDismissRequest = { pick = null }, containerColor = C.card,
            title = { T(s.name, 17, FontWeight.Bold) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Field(target, { target = it }, if (s.kind == "per1000") "الرابط" else "الأيدي / الرقم / المعلومات")
                if (s.kind == "per1000") Field(qty, { qty = it.filter(Char::isDigit) }, "الكمية", number = true)
                T("التكلفة: $cost نقطة  •  رصيدك: ${u.balance}", 13, color = C.sub) } },
            confirmButton = { TextButton({ val e = Repo.placeOrder(u, s, target, q); if (e == null) { toast("تم إرسال الطلب"); pick = null; Session.refresh() } else toast(e) }) { T("اطلب", 14, FontWeight.Bold, C.blue) } },
            dismissButton = { TextButton({ pick = null }) { T("إلغاء", 14, color = C.sub) } })
    }
}

fun statusColor(s: String) = when (s) { "مكتمل" -> C.green; "مرفوض" -> C.red; else -> C.gold }

@Composable
fun OrdersScreen() {
    val u = Session.user ?: return
    val list = remember(Session.tick.intValue) { Repo.orders(u.uid) }
    Page("طلباتي") {
        if (list.isEmpty()) Card { T("لا توجد طلبات بعد", 13, color = C.sub, align = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        list.forEach { o -> Card { Row { T("#${o.id}  ${o.title}", 15, FontWeight.Bold, modifier = Modifier.weight(1f)); Chip(o.status, statusColor(o.status)) }
            T("الكمية ${o.qty}  •  ${o.price} نقطة", 12, color = C.sub); T(o.target, 12, color = C.sub, maxLines = 1); T(fmtTime(o.ts), 11, color = C.sub) } }
    }
}

@Composable
fun TrackScreen() {
    val u = Session.user ?: return
    var id by remember { mutableStateOf("") }; var res by remember { mutableStateOf<Order?>(null) }; var msg by remember { mutableStateOf("") }
    Page("تتبع طلب") {
        Field(id, { id = it.filter(Char::isDigit) }, "رقم الطلب", number = true)
        Btn("تتبع", "primary", emoji = "1f50d") { val o = id.toLongOrNull()?.let { Repo.orderById(it) }; if (o != null && (o.uid == u.uid || u.admin)) { res = o; msg = "" } else { res = null; msg = "لم يتم العثور على الطلب" } }
        if (msg.isNotBlank()) T(msg, 13, color = C.red)
        res?.let { o -> Card { T("#${o.id}  ${o.title}", 16, FontWeight.Bold); Chip(o.status, statusColor(o.status)); T("الكمية ${o.qty} • ${o.price} نقطة", 13, color = C.sub); T(fmtTime(o.ts), 12, color = C.sub) } }
    }
}

@Composable
fun CodeScreen() {
    val u = Session.user ?: return
    var code by remember { mutableStateOf("") }
    Page("استخدام الكود") {
        Field(code, { code = it }, "أدخل الكود")
        Btn("شحن", "success", emoji = "1f39f") { val e = Repo.useCode(u, code); if (e == null) { toast("تم شحن رصيدك"); code = ""; Session.refresh() } else toast(e) }
        T("الأكواد يصدرها المطور من لوحة التحكم.", 12, color = C.sub)
    }
}

@Composable
fun SupportScreen() {
    Page("الدعم") {
        Card { T("للشراء أو الاستفسار تواصل مع المطور.", 14); T(Repo.get("support_link").ifBlank { "لم يتم تحديد رابط الدعم بعد" }, 13, color = C.sub) }
        Btn("فتح الدعم", "primary", emoji = "1f4ac") { openUrl(Repo.get("support_link")) }
    }
}

@Composable
fun TermsScreen() { Page("الشروط والأحكام") { Card { T(Repo.get("terms").ifBlank { "1) النقاط غير قابلة للاسترجاع بعد التنفيذ.\n2) تأكد من الرابط قبل الطلب.\n3) الطلبات المرفوضة تُرجع نقاطها." }, 14) } } }

@Composable
fun WheelScreen() {
    val u = Session.user ?: return
    var out by remember { mutableStateOf("") }
    Page("عجلة الحظ") {
        Card { Row(verticalAlignment = Alignment.CenterVertically) { PremiumEmoji("1f3a1", 48.dp); Spacer(Modifier.width(10.dp)); T("لفّة كل 24 ساعة. الجوائز من 1 إلى ${Repo.getLong("wheel_max", 20)} نقطة.", 13) } }
        Btn("لف العجلة", "success", emoji = "1f3a1") {
            val prize = (1..Repo.getLong("wheel_max", 20).toInt().coerceAtLeast(1)).random().toLong()
            val e = Repo.claim(u, "عجلة الحظ", 24, prize); if (e == null) { out = "ربحت $prize نقطة 🎉"; Session.refresh() } else out = e }
        if (out.isNotBlank()) T(out, 16, FontWeight.Bold, C.gold, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun SubscribeScreen() { Page("الاشتراك بالقنوات") { Card { T(Repo.get("sub_channels").ifBlank { "لا توجد مهام اشتراك حالياً." }, 14) }; Btn("فتح قناتنا", "primary") { openUrl(Repo.get("channel_link")) } } }

@Composable
fun ReferralScreen() {
    val u = Session.user ?: return
    val ctx = androidx.compose.ui.platform.LocalContext.current
    Page("رابط الدعوة") {
        Card { T("رمز دعوتك", 13, color = C.sub); T(u.uid, 24, FontWeight.ExtraBold) }
        Btn("مشاركة", "primary", emoji = "1f517") { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "حمّل تطبيق Turbo Rashq وسجّل برمز الدعوة ${u.uid}"), null)) }
    }
}

@Composable
fun TransferScreen(byId: Boolean) {
    val u = Session.user ?: return
    var to by remember { mutableStateOf("") }; var amt by remember { mutableStateOf("") }
    Page(if (byId) "تحويل بالأيدي" else "تحويل باليوزر نيم") {
        Card { T("رصيدك: ${u.balance} نقطة", 15, FontWeight.Bold) }
        Field(to, { to = it }, if (byId) "الأيدي الرقمي للمستلم" else "اسم المستخدم (@username)")
        Field(amt, { amt = it.filter(Char::isDigit) }, "عدد النقاط", number = true)
        Btn("تحويل", "success", emoji = "1f504") { val e = Repo.transfer(u, to, byId, amt.toLongOrNull() ?: 0); if (e == null) { toast("تم التحويل"); to = ""; amt = ""; Session.refresh() } else toast(e) }
        T("يصل للمستلم إشعار فيه اسمك والمبلغ.", 12, color = C.sub)
    }
}

@Composable
fun AccountInfoScreen() {
    val u = Session.user ?: return
    var show by remember { mutableStateOf(false) }
    val led = remember(Session.tick.intValue) { Repo.ledger(u.uid) }
    Page("حسابي") {
        Card { T(u.name, 18, FontWeight.ExtraBold); T("@${u.username}", 13, color = C.sub); T("ID: ${u.uid}", 13, color = C.sub); T("الرصيد: ${u.balance} نقطة", 15, FontWeight.Bold, C.green) }
        Btn("تغيير كلمة السر", "primary") { show = true }
        Btn("تسجيل الخروج", "danger") { Repo.setCurrent(null); Nav.reset(); Session.refresh() }
        T("سجل الرصيد", 15, FontWeight.Bold)
        led.forEach { l -> Card { Row { T(l.note, 13, modifier = Modifier.weight(1f)); T((if (l.delta > 0) "+" else "") + l.delta, 14, FontWeight.Bold, if (l.delta > 0) C.green else C.red) }; T(fmtTime(l.ts), 11, color = C.sub) } }
    }
    if (show) {
        var o by remember { mutableStateOf("") }; var n by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { show = false }, containerColor = C.card, title = { T("تغيير كلمة السر", 17, FontWeight.Bold) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Field(o, { o = it }, "الحالية", password = true); Field(n, { n = it }, "الجديدة", password = true) } },
            confirmButton = { TextButton({ val e = Repo.changePass(u.uid, o, n); if (e == null) { toast("تم التغيير"); show = false } else toast(e) }) { T("حفظ", 14, FontWeight.Bold, C.blue) } },
            dismissButton = { TextButton({ show = false }) { T("إلغاء", 14, color = C.sub) } })
    }
}

@Composable
fun NotifsScreen() {
    val u = Session.user ?: return
    val list = remember(Session.tick.intValue) { Repo.notifs(u.uid) }
    LaunchedEffect(Unit) { Repo.markRead(u.uid) }
    Page("الإشعارات") {
        if (list.isEmpty()) Card { T("لا توجد إشعارات", 13, color = C.sub, align = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        list.forEach { n -> Card { T(n.title, 15, FontWeight.Bold); T(n.body, 13, color = C.sub); T(fmtTime(n.ts), 11, color = C.sub) } }
    }
}

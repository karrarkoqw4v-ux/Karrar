package com.turbo.rashq

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class SItem(val key: String, val label: String, val def: String = "", val multi: Boolean = false)

@Composable
fun SettingsScreen(title: String, items: List<SItem>, note: String = "") {
    val vals = remember { items.map { mutableStateOf(Repo.get(it.key, it.def)) } }
    Page(title) {
        if (note.isNotBlank()) Card { T(note, 12, color = C.sub) }
        items.forEachIndexed { i, s -> Field(vals[i].value, { v -> vals[i].value = v }, s.label, single = !s.multi) }
        Btn("حفظ", "success", emoji = "5251595639694848975") { items.forEachIndexed { i, s -> Repo.put(s.key, vals[i].value) }; toast("تم الحفظ"); Session.refresh() }
    }
}

@Composable
fun ToggleRow(label: String, key: String, def: Boolean = true) {
    var on by remember { mutableStateOf(Repo.get(key, if (def) "1" else "0") == "1") }
    Card { Row(verticalAlignment = Alignment.CenterVertically) {
        T(label, 14, FontWeight.Bold, modifier = Modifier.weight(1f)); Chip(if (on) "مفعّل" else "معطّل", if (on) C.green else C.red); Spacer(Modifier.width(8.dp))
        Switch(on, { on = it; Repo.put(key, if (it) "1" else "0"); Session.refresh() }) } }
}

data class HubItem(val route: String, val label: String, val emoji: String, val style: String = "success")

@Composable
fun Hub(title: String, items: List<HubItem>, extra: @Composable ColumnScope.() -> Unit = {}) {
    Page(title) {
        extra()
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { h -> TgButton(h.label, h.style, h.emoji, Modifier.weight(1f)) { Nav.go(h.route) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

object E { const val games = "5359617799515823946"; const val rashq = "5247134066322447995"; const val free = "5251227707026470504"; const val sites = "5845740017896394081"
    const val toggle = "5850323366476519158"; const val settings = "5251511475515712980"; const val exchange = "5958768713720140845"; const val gift = "5823689256366771156"
    const val money = "5431416823637622117"; const val user = "5190558695515272787"; const val recharge = "5866369239740320716"; const val box = "5028746137645876535"
    const val brain = "6006107551198874621"; const val lock = "5310253176287414073"; const val edit = "5251595639694848975"; const val check = "5339148771362097161"
    const val mega = "5249148968625002018"; const val orders = "5935832686416366431"; const val files = "4967518033061872209" }

@Composable
fun AdminHome() {
    Hub("لوحة التحكم", listOf(
        HubItem("admin/games", "إدارة خدمات الألعاب", E.games), HubItem("admin/rashq", "إدارة خدمات الرشق", E.rashq),
        HubItem("admin/free", "إدارة الخدمات المجانية", E.free), HubItem("admin/sites", "ربط الموقع الرسمي", E.sites),
        HubItem("admin/toggle", "قسم التفعيل والتعطيل", E.toggle), HubItem("admin/settings", "قسم الإعدادات", E.settings),
        HubItem("admin/exchange", "قسم الاستبدال", E.exchange), HubItem("admin/funding", "قسم التمويل", E.sites),
        HubItem("admin/gifts", "قسم الهدايا", E.gift), HubItem("admin/pts", "قسم النقاط", E.money),
        HubItem("admin/accounts", "قسم الحسابات", E.user), HubItem("admin/recharge", "قسم الشحن", E.recharge),
        HubItem("admin/backup", "النسخ الاحتياطية", E.box), HubItem("admin/stats", "الإحصائيات", E.box),
        HubItem("admin/texts", "تعديل النصوص", E.edit), HubItem("admin/codes", "إدارة الأكواد", E.lock),
        HubItem("admin/support", "الدعم الذكي", E.brain), HubItem("admin/mandatory", "الاشتراك الإجباري", E.lock),
        HubItem("admin/orders", "الطلبات", E.orders), HubItem("admin/notify", "إذاعة • هدايا • منشورات", E.mega),
        HubItem("admin/buttons", "تعديل الأزرار", E.settings, "primary"), HubItem("admin/emoji", "الإيموجي المميز", E.free, "primary"),
        HubItem("admin/appinfo", "تفاصيل التطبيق", E.files, "primary"),
    ))
}

fun normUrl(u: String): String { val t = u.trim().trimEnd('/'); return if (t.startsWith("http")) t else "https://$t" }

@Composable
fun AdminSites() {
    val tick = Session.tick.intValue
    val sites = remember(tick) { Repo.sites() }
    var url by remember { mutableStateOf("") }; var key by remember { mutableStateOf("") }; var msg by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Page("ربط الموقع الرسمي") {
        if (sites.isEmpty()) Card { T("⚠️ لا يوجد مواقع مربوطة", 13, color = C.red) }
        sites.forEach { s -> Card(onClick = { Nav.go("admin/site/" + s.optString("name")) }) {
            Row(verticalAlignment = Alignment.CenterVertically) { T((if (s.optBoolean("active", true)) "🟢 " else "🔴 ") + s.optString("name"), 15, FontWeight.Bold, modifier = Modifier.weight(1f)); Chip("💰 " + s.optString("balance"), C.green) }
            T(s.optString("url"), 11, color = C.sub) } }
        Card {
            T("➕ إضافة موقع جديد", 15, FontWeight.Bold)
            Field(url, { url = it }, "رابط الموقع"); Field(key, { key = it }, "مفتاح API", password = true)
            Btn(if (busy) "جاري التحقق..." else "🔗 ربط الموقع", "success", emoji = E.check) {
                if (url.isBlank() || key.isBlank()) { toast("أدخل الرابط والمفتاح"); return@Btn }
                if (busy) return@Btn
                busy = true; msg = "⏳ جاري التحقق من الموقع..."
                val u = normUrl(url); val k = key.trim()
                scope.launch {
                    val r = withContext(Dispatchers.IO) { runCatching { Smm.verify(u, k) } }
                    busy = false
                    r.onSuccess { bal ->
                        val name = runCatching { java.net.URI(u).host }.getOrNull() ?: u
                        val l = Repo.sites().filter { it.optString("name") != name }.toMutableList()
                        l.add(org.json.JSONObject().put("name", name).put("url", u).put("key", k).put("balance", bal).put("active", true))
                        Repo.saveSites(l); msg = "✅ تم ربط الموقع بنجاح!\n🌐 الاسم: $name\n🔗 الرابط: $u\n🪙 الرصيد: $bal"; url = ""; key = ""; Session.refresh() }
                    r.onFailure { msg = "❌ فشل التحقق!\n⚠️ السبب: ${it.message}" }
                }
            }
            if (msg.isNotBlank()) T(msg, 13)
        }
    }
}

@Composable
fun AdminSiteDetail(name: String) {
    val tick = Session.tick.intValue
    val site = remember(tick) { Repo.sites().firstOrNull { it.optString("name") == name } }
    val scope = rememberCoroutineScope(); var msg by remember { mutableStateOf("") }; var del by remember { mutableStateOf(false) }
    if (site == null) { Page("الموقع") { T("الموقع غير موجود", 13, color = C.red) }; return }
    fun update(f: (org.json.JSONObject) -> Unit) { Repo.saveSites(Repo.sites().map { if (it.optString("name") == name) it.also(f) else it }); Session.refresh() }
    Page(name) {
        Card { T("🌐 $name", 16, FontWeight.ExtraBold); T("🔗 ${site.optString("url")}", 12, color = C.sub); T("🪙 الرصيد: ${site.optString("balance")}", 14, FontWeight.Bold, C.green)
            T(if (site.optBoolean("active", true)) "🟢 الموقع مفعّل" else "🔴 الموقع متوقف", 13) }
        Btn("📦 جلب الخدمات", "success", emoji = E.rashq) { Nav.go("admin/fetch") }
        Btn("🔍 فحص الاتصال وتحديث الرصيد", "primary") {
            msg = "⏳ جاري الفحص..."
            scope.launch { val r = withContext(Dispatchers.IO) { runCatching { Smm.verify(site.optString("url"), site.optString("key")) } }
                r.onSuccess { b -> update { it.put("balance", b) }; msg = "✅ الاتصال سليم • الرصيد: $b" }; r.onFailure { msg = "❌ فشل الاتصال: ${it.message}" } } }
        if (msg.isNotBlank()) Card { T(msg, 13) }
        if (site.optBoolean("active", true)) Btn("⏹️ توقيف الموقع", "danger") { update { it.put("active", false) } } else Btn("▶️ تشغيل الموقع", "success") { update { it.put("active", true) } }
        Btn("🗑️ حذف الموقع", "danger") { del = true }
    }
    if (del) ConfirmDialog("حذف الموقع", name, { Repo.saveSites(Repo.sites().filter { it.optString("name") != name }); Nav.back(); Session.refresh() }) { del = false }
}

@Composable
fun AdminFetch() {
    val site = Repo.currentSite()
    val scope = rememberCoroutineScope()
    var platform by remember { mutableStateOf(Defs.name("btn_plat_tiktok")) }
    var remote by remember { mutableStateOf<List<Smm.Remote>?>(null) }; var err by remember { mutableStateOf("") }; var loading by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }; var shown by remember { mutableStateOf(40) }
    val plats = Defs.ofCat("rashq").map { Defs.name(it.key) }
    Page("جلب الخدمات من الموقع") {
        if (site == null) { Card { T("⚠️ اربط موقعاً أولاً من «ربط الموقع الرسمي»", 13, color = C.red) }; return@Page }
        Card { T("🌐 ${site.optString("name")}  •  🪙 ${site.optString("balance")}", 13, FontWeight.Bold); T("المنصات فقط (بدون الألعاب والتطبيقات)", 11, color = C.sub) }
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(plats) { p -> FilterChip(platform == p, { platform = p; selected = emptySet(); shown = 40 }, label = { T(p, 12) }) } }
        Btn(if (loading) "جاري الجلب..." else "⬇️ جلب خدمات $platform", "success") {
            if (loading) return@Btn
            loading = true; err = ""
            scope.launch { val r = withContext(Dispatchers.IO) { runCatching { Smm.services(site.optString("url"), site.optString("key")) } }
                loading = false; r.onSuccess { remote = it; selected = emptySet(); shown = 40 }; r.onFailure { err = "❌ فشل جلب الخدمات: ${it.message}" } } }
        if (err.isNotBlank()) T(err, 13, color = C.red)
        val list = remember(remote, platform) { remote?.let { Smm.filterFor(platform, it) } ?: emptyList() }
        if (remote != null) {
            Card { T("📦 خدمات $platform المتاحة للاسترداد", 14, FontWeight.Bold); T("📊 الإجمالي: ${list.size}  •  ✅ المحدد: ${selected.size}", 12, color = C.sub) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TgButton("تحديد الكل", "primary", null, Modifier.weight(1f)) { selected = list.map { it.id }.toSet() }
                TgButton("إلغاء التحديد", "plain", null, Modifier.weight(1f)) { selected = emptySet() } }
            list.take(shown).forEach { s -> Card(onClick = { selected = if (s.id in selected) selected - s.id else selected + s.id }) {
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(s.id in selected, { selected = if (it) selected + s.id else selected - s.id })
                    Column(Modifier.weight(1f)) { T(s.name, 13, FontWeight.Bold, maxLines = 2); T("#${s.id} • ${(s.rate * 1000).toLong()} نقطة/1000 • ${s.min}-${s.max}", 11, color = C.sub) } } } }
            if (list.size > shown) Btn("عرض المزيد (${list.size - shown})", "primary") { shown += 40 }
            Btn("✅ استرداد المحدد (${selected.size})", "success", emoji = E.check) {
                val chosen = list.filter { it.id in selected }
                if (chosen.isEmpty()) { toast("لم تحدد أي خدمة"); return@Btn }
                Repo.allServices().filter { it.cat == platform && it.apiId.isNotBlank() }.forEach { Repo.deleteService(it.id) }
                chosen.forEach { Repo.saveService(Service(0, platform, it.name, (it.rate * 1000).toLong(), "per1000", it.min, false, it.max, it.id, site.optString("name"))) }
                toast("✅ تم استرداد ${chosen.size} خدمة لـ $platform"); selected = emptySet(); Session.refresh() }
        }
    }
}

@Composable
fun AdminExchange() { Page("قسم الاستبدال") { Card { T("قسم الاستبدال قيد التطوير في البوت أيضاً. سيُفعَّل هنا عند تفعيله.", 13, color = C.sub) } } }

@Composable
fun AdminFunding() {
    SettingsScreen("قسم التمويل", listOf(SItem("tamweel_price", "💰 سعر العضو", "16"), SItem("tamweel_min", "🔽 الحد الأدنى", "50"), SItem("tamweel_max", "🔼 الحد الأقصى", "100000"),
        SItem("tamweel_txt", "🚀 كليشة التمويل", "", true)), "قسم التمويل يظهر للمستخدمين من زر «تمويل قناتك».")
}

@Composable
fun AdminToggle() {
    Page("قسم التفعيل والتعطيل") {
        ToggleRow("قسم التمويل", "funding_enabled"); ToggleRow("تحويل النقاط", "transfer_enabled"); ToggleRow("أكواد الخصم", "codes_enabled")
    }
}

@Composable
fun AdminSettingsHub() {
    Hub("قسم الإعدادات", listOf(
        HubItem("admin/set/main", "الكلايش والمتغيرات", E.edit), HubItem("admin/set/channels", "قنوات الإشعارات", E.mega),
        HubItem("admin/set/complete", "كلاشات الإشعارات", E.check), HubItem("admin/set/verify", "قسم التحقق", E.lock),
        HubItem("admin/funding", "إعدادات التمويل", E.sites)))
}

@Composable
fun AdminSet(g: String) = when (g) {
    "main" -> SettingsScreen("الكلايش والمتغيرات", listOf(SItem("bot_name", "📝 اسم التطبيق", "تيربو رشق"), SItem("currency", "💱 العملة", "نقطة"), SItem("transfer_min", "📊 أقل تحويل", "60000"), SItem("transfer_fee", "٪ عمولة التحويل", "1"),
        SItem("recharge_txt", "💰 كليشة الشحن", "", true), SItem("terms", "📋 كليشة الشروط", "", true), SItem("collect_txt", "📊 كليشة التجميع", "", true),
        SItem("tamweel_txt", "🚀 كليشة التمويل", "", true), SItem("stars_txt", "⭐ كليشة النجوم", "", true), SItem("welcome", "👋 رسالة الترحيب (#first_name #balance #id)", "", true)))
    "channels" -> SettingsScreen("قنوات الإشعارات", listOf(SItem("orders_channel", "📢 قناة إكمال الطلب"), SItem("game_bot_link", "🔗 رابط بوت الألعاب"), SItem("rashq_bot_link", "🔗 رابط بوت الرشق"), SItem("channel_link", "📣 رابط القناة")))
    "complete" -> SettingsScreen("كلاشات الإشعارات", listOf(SItem("rashq_complete", "📝 كلاشة إكمال الرشق", "تم إكمال طلبك ✅", true), SItem("game_complete", "📝 كلاشة إكمال الألعاب", "تم شحن طلبك ✅", true)))
    else -> AdminVerify()
}

@Composable
fun AdminVerify() {
    var m by remember { mutableStateOf(Repo.get("verify_mode", "off")) }
    Page("قسم التحقق") {
        listOf("code" to "🔢 التحقق عبر 6 أرقام", "phone" to "📱 التحقق عبر الرقم", "off" to "❌ تعطيل التحقق").forEach { (k, l) ->
            Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(m == k, { m = k; Repo.put("verify_mode", k) }); T(l, 14) } }
        Card { T("الوضع الحالي: $m", 12, color = C.sub) }
    }
}

@Composable
fun AdminGiftsHub() {
    Page("قسم الهدايا") {
        val keys = listOf("gift_points" to "🎁 نقاط الهدية اليومية" to "50", "wheel_min" to "🎪 أقل جائزة للعجلة" to "30", "wheel_max" to "🎪 أعلى جائزة للعجلة" to "1000", "weekly_gift_points" to "🎁 نقاط الهدية الأسبوعية" to "100", "referral_points" to "🔗 نقاط رابط الدعوة" to "200")
        val vals = remember { keys.map { mutableStateOf(Repo.get(it.first.first, it.second)) } }
        keys.forEachIndexed { i, k -> Field(vals[i].value, { v -> vals[i].value = v.filter(Char::isDigit) }, k.first.second, number = true) }
        Btn("حفظ", "success", emoji = E.check) { keys.forEachIndexed { i, k -> Repo.put(k.first.first, vals[i].value) }; toast("تم الحفظ") }
        Btn("🔗 صنع رابط هدية", "primary", emoji = E.gift) { Nav.go("admin/giftlink") }
    }
}

@Composable
fun AdminGiftsOld() {
    SettingsScreen("قسم الهدايا", listOf(SItem("gift_points", "🎁 نقاط الهدية اليومية", "50"), SItem("wheel_min", "🎪 أقل جائزة للعجلة", "30"), SItem("wheel_max", "🎪 أعلى جائزة للعجلة", "1000"),
        SItem("weekly_gift_points", "🎁 نقاط الهدية الأسبوعية", "100"), SItem("referral_points", "🔗 نقاط رابط الدعوة", "200")))
}

@Composable
fun AdminGiftLink() {
    var pts by remember { mutableStateOf("") }; var made by remember { mutableStateOf("") }; val ctx = LocalContext.current
    Page("صنع رابط هدية") {
        Field(pts, { pts = it.filter(Char::isDigit) }, "عدد النقاط", number = true)
        Btn("إنشاء كود هدية", "success", emoji = E.gift) {
            val a = pts.toLongOrNull() ?: 0; if (a <= 0) { toast("أدخل النقاط"); return@Btn }
            val c = "GIFT" + (1..8).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString(""); Repo.addCode(c, a, 1); made = c }
        if (made.isNotBlank()) { Card { T("الكود: $made", 18, FontWeight.ExtraBold) }
            Btn("مشاركة الكود", "primary") { ctx.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, "هدية ${pts} نقطة في تيربو رشق 🎁 استخدم الكود: $made"), null)) } }
    }
}

fun findUser(s: String): User? = Repo.userByUid(s.trim()) ?: Repo.userByUsername(s)

@Composable
fun AdminPts() {
    var who by remember { mutableStateOf("") }; var amt by remember { mutableStateOf("") }
    val tick = Session.tick.intValue
    val users = remember(tick) { Repo.users() }
    Page("قسم النقاط") {
        Field(who, { who = it }, "اليوزر أو الأيدي"); Field(amt, { amt = it.filter(Char::isDigit) }, "النقاط", number = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TgButton("➕ إضافة", "success", null, Modifier.weight(1f)) { val u = findUser(who); val a = amt.toLongOrNull() ?: 0
                if (u == null || a <= 0) toast("تحقق من البيانات") else { Repo.addBalance(u.uid, a, "إضافة من المطور"); Repo.pushNotif(u.uid, "تمت إضافة نقاط 💰", "أضاف لك المطور $a ${Repo.cur()}", "gift"); toast("تمت الإضافة"); Session.refresh() } }
            TgButton("➖ خصم", "danger", null, Modifier.weight(1f)) { val u = findUser(who); val a = amt.toLongOrNull() ?: 0
                if (u == null || a <= 0) toast("تحقق من البيانات") else { Repo.addBalance(u.uid, -a, "خصم من المطور"); toast("تم الخصم"); Session.refresh() } }
        }
        Btn("🔄 تصفير نقاط مستخدم", "primary") { val u = findUser(who); if (u == null) toast("المستخدم غير موجود") else { Repo.setBalance(u.uid, 0); toast("تم التصفير"); Session.refresh() } }
        Card { T("📊 إحصائيات النقاط", 15, FontWeight.Bold); T("المستخدمون: ${users.size}  •  الإجمالي: ${users.sumOf { it.balance }}", 13, color = C.sub)
            users.sortedByDescending { it.balance }.take(5).forEach { T("${it.name} (@${it.username}): ${it.balance}", 12) } }
    }
}

@Composable
fun AdminAccounts() {
    var confirm by remember { mutableStateOf(false) }
    Hub("قسم الحسابات", listOf(HubItem("admin/users", "🔍 بحث عن مستخدم", E.user), HubItem("admin/notify", "📢 رسالة جماعية", E.mega)), extra = {
        Card { T("👥 عدد المستخدمين: ${Repo.userCount()}", 15, FontWeight.Bold) }
        Btn("🗑️ حذف جميع نقاط المستخدمين", "danger") { confirm = true } })
    if (confirm) ConfirmDialog("تصفير كل الأرصدة", "سيتم تصفير نقاط جميع المستخدمين. متأكد؟", { Repo.resetAllBalances(); Session.refresh(); toast("تم") }) { confirm = false }
}

@Composable
fun AdminRecharge() {
    var who by remember { mutableStateOf("") }; var amt by remember { mutableStateOf("") }
    val led = remember(Session.tick.intValue) { Repo.users().take(0) }
    Page("قسم الشحن") {
        ToggleRow("📱 شحن آسياسيل", "asiacell_enabled"); ToggleRow("⭐ الشحن عبر النجوم", "stars_enabled")
        Card { T("➕ شحن يدوي لمستخدم", 15, FontWeight.Bold); Field(who, { who = it }, "اليوزر أو الأيدي"); Field(amt, { amt = it.filter(Char::isDigit) }, "النقاط", number = true)
            Btn("شحن", "success", emoji = E.recharge) { val u = findUser(who); val a = amt.toLongOrNull() ?: 0
                if (u == null || a <= 0) toast("تحقق من البيانات") else { Repo.addBalance(u.uid, a, "شحن يدوي"); Repo.pushNotif(u.uid, "تم شحن رصيدك 💳", "أُضيف $a ${Repo.cur()} إلى رصيدك", "gift"); toast("تم الشحن"); who = ""; amt = ""; Session.refresh() } } }
        Btn("نصوص الشحن والنجوم", "primary") { Nav.go("admin/set/rtexts") }
        val all = remember(Session.tick.intValue) { Repo.recentCharges() }
        Card { T("🧾 آخر عمليات الشحن", 15, FontWeight.Bold); T("مجموع الشحن: ${all.sumOf { it.second }}", 12, color = C.sub)
            all.take(15).forEach { T("${it.first}: +${it.second}  (${fmtTime(it.third)})", 12) } }
    }
}

@Composable
fun AdminBackup() {
    val ctx = LocalContext.current; var tick by remember { mutableStateOf(0) }
    val exp = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if (uri != null) { Repo.db.close(); ctx.contentResolver.openOutputStream(uri)?.use { o -> Repo.dbFile().inputStream().use { it.copyTo(o) } }; toast("تم حفظ النسخة") } }
    val imp = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) { Repo.db.close(); ctx.contentResolver.openInputStream(uri)?.use { i -> Repo.dbFile().outputStream().use { i.copyTo(it) } }; Session.refresh(); toast("تمت الاستعادة") } }
    val list = remember(tick) { Repo.backups() }
    Page("النسخ الاحتياطية") {
        Btn("💾 إنشاء نسخة احتياطية", "success", emoji = E.box) { Repo.createBackup(); tick++; toast("تم إنشاء النسخة") }
        Btn("🗑️ حذف النسخ القديمة (يبقى آخر 3)", "danger") { Repo.cleanBackups(); tick++; toast("تم") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TgButton("تصدير ملف", "primary", null, Modifier.weight(1f)) { exp.launch("turbo-backup.db") }
            TgButton("استيراد ملف", "primary", null, Modifier.weight(1f)) { imp.launch(arrayOf("*/*")) } }
        list.forEach { f -> Card { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { T(f.name, 12); T("${f.length() / 1024} KB", 11, color = C.sub) }
            TextButton({ Repo.restoreBackup(f); Session.refresh(); toast("تمت الاستعادة") }) { T("♻️ استعادة", 13, color = C.blue) } } } }
    }
}

@Composable
fun AdminTexts() {
    Hub("تعديل النصوص", listOf(HubItem("admin/set/notiftexts", "نصوص الإشعارات", E.mega), HubItem("admin/set/main", "كل كلايش التطبيق", E.edit),
        HubItem("admin/set/rashqdetail", "تفاصيل طلب الرشق", E.rashq), HubItem("admin/set/texts2", "الألعاب • النقاط • الشحن • النجوم", E.games),
        HubItem("admin/set/complete", "كلاشات إكمال الطلب", E.check), HubItem("admin/set/mandtext", "نص الاشتراك الإجباري", E.lock)),
        extra = { Card { T("الهاشتاقات المدعومة: #id #first_name #username #points #balance", 12, color = C.sub) } })
}

@Composable
fun AdminSet2(g: String) = when (g) {
    "notiftexts" -> SettingsScreen("نصوص الإشعارات", listOf(SItem("nt_transfer", "عنوان إشعار التحويل", "وصلك تحويل 💰"), SItem("nt_gift", "عنوان إشعار الهدية", "🎁 هدية"), SItem("nt_order", "عنوان إشعار الطلب", "تحديث الطلب"), SItem("nt_broadcast", "عنوان الإذاعة", "📢 إعلان")))
    "rashqdetail" -> SettingsScreen("تفاصيل طلب الرشق", listOf(SItem("rashq_detail", "نص التفاصيل", "اختر الخدمة ثم أرسل الرابط والكمية.", true)))
    "texts2" -> SettingsScreen("نصوص الأقسام", listOf(SItem("games_txt", "🎮 نص الألعاب", "", true), SItem("points_txt", "💎 نص النقاط", "", true), SItem("recharge_txt", "💰 نص الشحن", "", true), SItem("stars_txt", "⭐ نص النجوم", "", true), SItem("terms", "📋 الشروط", "", true)))
    "mandtext" -> SettingsScreen("نص الاشتراك الإجباري", listOf(SItem("mand_text", "نص الاشتراك", "اشترك بالقنوات التالية أولاً", true)))
    "rtexts" -> SettingsScreen("نصوص الشحن", listOf(SItem("recharge_txt", "💰 نص الشحن", "", true), SItem("stars_txt", "⭐ نص النجوم", "", true), SItem("asi_prompt", "📱 نص طلب رقم آسياسيل", "أرسل رقم خطك آسياسيل", true), SItem("asi_success", "✅ نص نجاح الشحن", "تم الشحن بنجاح", true)))
    else -> AdminSet(g)
}

@Composable
fun AdminSupport() {
    SettingsScreen("الدعم الذكي", listOf(SItem("support_welcome", "📝 نص ترحيب الدعم", "اهلاً بك في الدعم الذكي. اكتب مشكلتك للحصول على الحل.", true),
        SItem("support_faq", "❓ الأسئلة الشائعة (سؤال|جواب بكل سطر)", "", true), SItem("support_link", "رابط التواصل المباشر")), "يطابق التطبيق كلمات سؤال المستخدم مع الأسئلة المحفوظة.")
}

@Composable
fun AdminMandatory() {
    SettingsScreen("الاشتراك الإجباري", listOf(SItem("mand_channels", "القنوات والكروبات والبوتات (رابط بكل سطر)", "", true), SItem("mand_text", "📝 نص الاشتراك", "اشترك بالقنوات التالية أولاً", true)),
        "تظهر القنوات للمستخدم على الصفحة الرئيسية كأزرار اشتراك.")
}

@Composable
fun AdminAppInfo() {
    val ctx = LocalContext.current
    Page("تفاصيل التطبيق") {
        Card { T("اسم التطبيق: Turbo Rashq / تيربو رشق", 14); T("الإصدار: 1.0", 13, color = C.sub); T("قاعدة البيانات: SQLite داخل الهاتف", 13, color = C.sub); T("حجمها: ${Repo.dbFile().length() / 1024} KB", 13, color = C.sub)
            T("المستخدمون: ${Repo.userCount()}  •  الخدمات: ${Repo.allServices().size}  •  الطلبات: ${Repo.orders(null).size}", 13, color = C.sub)
            T("الإيموجي المحفوظ: ${Repo.emojiAll().size}  •  المضمّن: ${Bundled.set.size}", 13, color = C.sub); T("أندرويد: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})", 13, color = C.sub) }
    }
}

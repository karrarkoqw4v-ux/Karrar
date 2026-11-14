package com.turbo.rashq

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun AdminStats() {
    val tick = Session.tick.intValue
    Page("الإحصائيات") {
        val all = remember(tick) { Repo.orders(null) }
        val sum = remember(tick) { Repo.users().sumOf { it.balance } }
        Card { T("المستخدمون: ${Repo.userCount()}", 16, FontWeight.Bold); T("إجمالي الأرصدة: $sum نقطة", 14, color = C.sub) }
        Card { T("الطلبات: ${all.size}", 16, FontWeight.Bold); T("قيد التنفيذ: ${all.count { it.status == "قيد التنفيذ" }}  •  مكتمل: ${all.count { it.status == "مكتمل" }}  •  مرفوض: ${all.count { it.status == "مرفوض" }}", 13, color = C.sub) }
        Card { T("الخدمات: ${Repo.allServices().size}  •  الأكواد: ${Repo.codes().size}", 14) }
    }
}

@Composable
fun AdminServices(group: String = "all") {
    val tick = Session.tick.intValue
    val gcats = when (group) { "rashq" -> Defs.ofCat("rashq").map { Defs.name(it.key) }; "games" -> Defs.ofCat("games").map { Defs.name(it.key) }; "free" -> listOf("خدمات مجانية"); else -> null }
    val list = remember(tick) { Repo.allServices().filter { gcats == null || it.cat in gcats } }
    var edit by remember { mutableStateOf<Service?>(null) }
    var delete by remember { mutableStateOf<Service?>(null) }
    var logoFor by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null && logoFor.isNotBlank()) {
            val f = File(ctx.filesDir, "logo_${logoFor.hashCode()}.img")
            ctx.contentResolver.openInputStream(uri)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            Repo.put("logo_$logoFor", f.absolutePath); Session.refresh(); toast("تم حفظ الصورة")
        }
    }
    val cats = gcats ?: ((Defs.ofCat("rashq") + Defs.ofCat("games")).map { Defs.name(it.key) } + "تمويل قناتك").distinct()
    Page(when (group) { "rashq" -> "إدارة خدمات الرشق"; "games" -> "إدارة خدمات الألعاب"; "free" -> "إدارة الخدمات المجانية"; else -> "الخدمات والمتجر" }) {
        Btn("إضافة خدمة", "success", emoji = "2705") { edit = Service(0, cats.first(), "", 0, "fixed", 1, false) }
        if (group == "rashq" || group == "all") Btn("⬇️ جلب الخدمات من الموقع", "primary", emoji = E.rashq) { Nav.go("admin/fetch") }
        T("صور التصنيفات (اضغط لاختيار صورة رسمية)", 13, color = C.sub)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) { items(cats) { c -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp).clip(RoundedCornerShape(12.dp)).let { it }) {
            Box(Modifier.clip(RoundedCornerShape(16.dp))) { Logo(c, 56.dp) }
            TextButton({ logoFor = c; picker.launch(arrayOf("image/*")) }) { T(c, 10, color = C.blue, maxLines = 2, align = TextAlign.Center) } } } }
        list.forEach { s -> Card { Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { T(s.name, 15, FontWeight.Bold); T("${s.cat} • ${s.price} • ${if (s.kind == "per1000") "لكل 1000" else "ثابت"}${if (s.apiId.isNotBlank()) " • 🔗 موقع" else ""}", 12, color = C.sub) }
            if (s.hidden) Chip("مخفي", C.gold) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton({ edit = s }) { T("تعديل", 13, color = C.blue) }
                TextButton({ Repo.saveService(s.copy(hidden = !s.hidden)); Session.refresh() }) { T(if (s.hidden) "إظهار" else "إخفاء", 13, color = C.gold) }
                TextButton({ delete = s }) { T("حذف", 13, color = C.red) } } } }
    }
    edit?.let { s ->
        var cat by remember { mutableStateOf(s.cat) }; var name by remember { mutableStateOf(s.name) }; var price by remember { mutableStateOf(s.price.toString()) }
        var kind by remember { mutableStateOf(s.kind) }; var minq by remember { mutableStateOf(s.minQty.toString()) }; var maxq by remember { mutableStateOf(s.maxQty.toString()) }
        AlertDialog(onDismissRequest = { edit = null }, containerColor = C.card, title = { T(if (s.id == 0L) "خدمة جديدة" else "تعديل الخدمة", 17, FontWeight.Bold) },
            text = { Column(Modifier.let { it }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(cats) { c -> FilterChip(cat == c, { cat = c }, label = { T(c, 12) }) } }
                Field(cat, { cat = it }, "التصنيف"); Field(name, { name = it }, "اسم الخدمة"); Field(price, { price = it.filter(Char::isDigit) }, "السعر (نقاط)", number = true)
                Row(verticalAlignment = Alignment.CenterVertically) { Switch(kind == "per1000", { kind = if (it) "per1000" else "fixed" }); Spacer(Modifier.width(8.dp)); T("سعر لكل 1000 (رشق)", 13) }
                if (kind == "per1000") { Field(minq, { minq = it.filter(Char::isDigit) }, "أقل كمية", number = true); Field(maxq, { maxq = it.filter(Char::isDigit) }, "أكبر كمية", number = true) }
                if (s.apiId.isNotBlank()) T("مربوطة بالموقع ${s.site} • API #${s.apiId}", 11, color = C.sub) } },
            confirmButton = { TextButton({ if (name.isBlank()) toast("اكتب الاسم") else { Repo.saveService(s.copy(cat = cat.trim(), name = name.trim(), price = price.toLongOrNull() ?: 0, kind = kind, minQty = minq.toLongOrNull() ?: 1, maxQty = maxq.toLongOrNull() ?: 1000000)); edit = null; Session.refresh() } }) { T("حفظ", 14, FontWeight.Bold, C.blue) } },
            dismissButton = { TextButton({ edit = null }) { T("إلغاء", 14, color = C.sub) } })
    }
    delete?.let { s -> ConfirmDialog("حذف الخدمة", s.name, { Repo.deleteService(s.id); Session.refresh() }) { delete = null } }
}

@Composable
fun AdminOrders() {
    val list = remember(Session.tick.intValue) { Repo.orders(null) }
    Page("الطلبات") {
        if (list.isEmpty()) Card { T("لا توجد طلبات", 13, color = C.sub) }
        list.forEach { o -> Card {
            Row { T("#${o.id} ${o.title}", 15, FontWeight.Bold, modifier = Modifier.weight(1f)); Chip(o.status, statusColor(o.status)) }
            T("${o.userName} (${o.uid}) • ${o.qty} • ${o.price}", 12, color = C.sub); T(o.target, 12, color = C.sub)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton({ Repo.setOrderStatus(o, "مكتمل"); Session.refresh() }) { T("إكمال", 12, color = C.green) }
                TextButton({ Repo.setOrderStatus(o, "قيد التنفيذ"); Session.refresh() }) { T("قيد التنفيذ", 12, color = C.gold) }
                TextButton({ Repo.setOrderStatus(o, "مرفوض"); Session.refresh() }) { T("رفض + استرجاع", 12, color = C.red) } } } }
    }
}

@Composable
fun AdminUsers() {
    var q by remember { mutableStateOf("") }; var sel by remember { mutableStateOf<User?>(null) }
    val list = remember(Session.tick.intValue, q) { Repo.users(q) }
    Page("المستخدمون") {
        Field(q, { q = it }, "بحث بالاسم أو اليوزر أو الأيدي")
        list.forEach { u -> Card(onClick = { sel = u }) { Row { T("${u.name} (@${u.username})", 14, FontWeight.Bold, modifier = Modifier.weight(1f)); if (u.banned) Chip("محظور", C.red) }; T("ID ${u.uid} • ${u.balance} نقطة", 12, color = C.sub) } }
    }
    sel?.let { u0 ->
        val u = Repo.userByUid(u0.uid) ?: u0
        var amt by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { sel = null }, containerColor = C.card, title = { T(u.name, 17, FontWeight.Bold) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { T("@${u.username} • ID ${u.uid}\nالرصيد ${u.balance}", 13, color = C.sub); Field(amt, { amt = it.filter(Char::isDigit) }, "المبلغ", number = true) } },
            confirmButton = { Column { Row {
                TextButton({ val a = amt.toLongOrNull() ?: 0; if (a > 0) { Repo.addBalance(u.uid, a, "إضافة من المطور"); Repo.pushNotif(u.uid, "تمت إضافة رصيد 💰", "أضاف لك المطور $a ${Repo.cur()}", "gift"); sel = null; Session.refresh() } }) { T("إضافة", 13, color = C.green) }
                TextButton({ val a = amt.toLongOrNull() ?: 0; if (a > 0) { Repo.addBalance(u.uid, -a, "خصم من المطور"); sel = null; Session.refresh() } }) { T("خصم", 13, color = C.red) } }
                TextButton({ Repo.setBanned(u.uid, !u.banned); sel = null; Session.refresh() }) { T(if (u.banned) "فك الحظر" else "حظر", 13, color = C.gold) } } },
            dismissButton = { TextButton({ sel = null }) { T("إغلاق", 14, color = C.sub) } })
    }
}

@Composable
fun AdminCodes() {
    var code by remember { mutableStateOf("") }; var amt by remember { mutableStateOf("") }; var max by remember { mutableStateOf("1") }
    val list = remember(Session.tick.intValue) { Repo.codes() }
    Page("الأكواد") {
        Field(code, { code = it }, "الكود"); Field(amt, { amt = it.filter(Char::isDigit) }, "النقاط", number = true); Field(max, { max = it.filter(Char::isDigit) }, "عدد الاستخدامات", number = true)
        Btn("إنشاء كود", "success") { if (code.isNotBlank() && (amt.toLongOrNull() ?: 0) > 0) { Repo.addCode(code, amt.toLong(), max.toIntOrNull() ?: 1); code = ""; amt = ""; Session.refresh() } else toast("أكمل الحقول") }
        Btn("توليد كود عشوائي", "primary") { code = (1..10).map { "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".random() }.joinToString("") }
        list.forEach { c -> Card { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { T(c.code, 15, FontWeight.Bold); T("${c.amount} نقطة • ${c.used}/${c.maxUses}", 12, color = C.sub) }
            TextButton({ Repo.delCode(c.code); Session.refresh() }) { T("حذف", 13, color = C.red) } } } }
    }
}

@Composable
fun AdminNotify() {
    var tab by remember { mutableStateOf(0) }
    var title by remember { mutableStateOf("") }; var body by remember { mutableStateOf("") }; var to by remember { mutableStateOf("") }; var amt by remember { mutableStateOf("") }
    val ctx = LocalContext.current
    Page("إذاعة • هدايا • منشورات") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("إذاعة", "هدية", "منشور").forEachIndexed { i, s -> FilterChip(tab == i, { tab = i }, label = { T(s, 13) }) } }
        Field(title, { title = it }, "العنوان"); Field(body, { body = it }, "النص", single = false)
        if (tab == 1) { Field(to, { to = it }, "اسم المستخدم المستلم (فارغ = الجميع)"); Field(amt, { amt = it.filter(Char::isDigit) }, "نقاط الهدية", number = true) }
        Btn("إرسال", "success", emoji = "1f4e2") {
            if (title.isBlank()) { toast("اكتب العنوان"); return@Btn }
            when (tab) {
                0 -> toast("أُرسلت إلى ${Repo.broadcast("📢 $title", body)} مستخدم")
                2 -> toast("نُشر إلى ${Repo.broadcast("📰 $title", body, "post")} مستخدم")
                else -> { val a = amt.toLongOrNull() ?: 0
                    val targets = if (to.isBlank()) Repo.users().filter { !it.admin } else listOfNotNull(Repo.userByUsername(to))
                    if (targets.isEmpty()) { toast("المستخدم غير موجود"); return@Btn }
                    targets.forEach { if (a > 0) Repo.addBalance(it.uid, a, "هدية من المطور"); Repo.pushNotif(it.uid, "🎁 $title", if (a > 0) "$body\n+$a ${Repo.cur()}" else body, "gift") }
                    toast("أُرسلت الهدية إلى ${targets.size}") } }
            Session.refresh(); title = ""; body = ""
        }
        Card { T("تصل الإشعارات إلى كل الحسابات المسجلة على هذا الهاتف وتظهر في شريط الإشعارات. لإيصالها لهواتف أخرى يلزم خادم.", 12, color = C.sub) }
    }
}

// ---------------- Button editor ----------------
@Composable
fun AdminButtons() {
    var cat by remember { mutableStateOf("main") }
    val tick = Session.tick.intValue
    val rows = remember(tick, cat) { Defs.layout(cat) }
    var rename by remember { mutableStateOf<String?>(null) }; var emojiFor by remember { mutableStateOf<String?>(null) }
    Page("تعديل الأزرار") {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { items(Defs.cats) { c -> FilterChip(cat == c, { cat = c }, label = { T(Defs.titles[c] ?: c, 12) }) } }
        T("اضغط على أي زر لتعديل اسمه ولونه وإيموجيه وموضعه.", 12, color = C.sub)
        rows.forEachIndexed { ri, row -> Card { T("الصف ${ri + 1}", 11, color = C.sub)
            row.forEach { k -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TgButton(Defs.name(k), Defs.style(k), Defs.emoji(k), Modifier.fillMaxWidth()) { rename = k }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf("primary", "success", "danger", "plain").forEach { s -> val (a, _) = styleColors(s); Box(Modifier.size(26.dp).clip(RoundedCornerShape(8.dp)).let { it }) { Surface(color = a, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxSize(), onClick = { Repo.put("bs_$k", s); Session.refresh() }) {} }; Spacer(Modifier.width(4.dp)) }
                    Spacer(Modifier.weight(1f))
                    TextButton({ Defs.move(cat, k, "up"); Session.refresh() }, contentPadding = PaddingValues(4.dp)) { T("⬆", 14) }
                    TextButton({ Defs.move(cat, k, "down"); Session.refresh() }, contentPadding = PaddingValues(4.dp)) { T("⬇", 14) }
                    TextButton({ Defs.move(cat, k, "right"); Session.refresh() }, contentPadding = PaddingValues(4.dp)) { T("➡", 14) }
                    TextButton({ Defs.move(cat, k, "left"); Session.refresh() }, contentPadding = PaddingValues(4.dp)) { T("⬅", 14) }
                    TextButton({ Defs.move(cat, k, "join"); Session.refresh() }, contentPadding = PaddingValues(4.dp)) { T("دمج", 11, color = C.blue) }
                    TextButton({ Defs.move(cat, k, "split"); Session.refresh() }, contentPadding = PaddingValues(4.dp)) { T("فصل", 11, color = C.blue) } } } } } }
        Btn("إعادة ضبط هذا القسم", "danger") { Defs.resetLayout(cat); Session.refresh(); toast("تمت إعادة الضبط") }
    }
    rename?.let { k ->
        var n by remember { mutableStateOf(Defs.name(k)) }
        AlertDialog(onDismissRequest = { rename = null }, containerColor = C.card, title = { T("تعديل الزر", 17, FontWeight.Bold) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Field(n, { n = it }, "اسم الزر"); TextButton({ emojiFor = k; rename = null }) { T("تغيير الإيموجي المميز", 14, color = C.blue) } } },
            confirmButton = { TextButton({ Repo.put("bn_$k", n.trim()); rename = null; Session.refresh() }) { T("حفظ", 14, FontWeight.Bold, C.blue) } },
            dismissButton = { TextButton({ rename = null }) { T("إلغاء", 14, color = C.sub) } })
    }
    emojiFor?.let { k -> EmojiPickDialog(onPick = { Repo.put("be_$k", it); emojiFor = null; Session.refresh() }) { emojiFor = null } }
}

@Composable
fun EmojiPickDialog(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var id by remember { mutableStateOf("") }; var busy by remember { mutableStateOf(false) }; var msg by remember { mutableStateOf("") }
    val gallery = remember(Session.tick.intValue, msg) { Repo.emojiAll() }
    AlertDialog(onDismissRequest = onDismiss, containerColor = C.card, title = { T("إيموجي مميز", 17, FontWeight.Bold) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Field(id, { id = it.filter(Char::isDigit) }, "الصق معرّف الإيموجي (ID)", number = true)
            if (msg.isNotBlank()) T(msg, 12, color = C.sub)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(gallery.take(60)) { g -> Surface(onClick = { onPick(g.first) }, color = C.card2, shape = RoundedCornerShape(10.dp)) { Box(Modifier.padding(6.dp)) { PremiumEmoji(g.first, 30.dp) } } } } } },
        confirmButton = { TextButton({
            if (id.length < 10) { toast("معرّف غير صحيح"); return@TextButton }
            busy = true; msg = "جاري الجلب وسحب الحزمة..."
            scope.launch { val r = withContext(Dispatchers.IO) { runCatching { TgApi.fetchIds(ctx, listOf(id)); TgApi.pullPackOf(ctx, id) } }
                busy = false
                r.onSuccess { msg = "تم سحب الحزمة ${it.first} (${it.second})" ; if (Repo.emojiPath(id) != null) onPick(id) }
                r.onFailure { msg = "فشل: ${it.message}"; if (Repo.emojiPath(id) != null) onPick(id) } } }) { T(if (busy) "..." else "جلب واستخدام", 14, FontWeight.Bold, C.blue) } },
        dismissButton = { TextButton(onDismiss) { T("إغلاق", 14, color = C.sub) } })
}

@Composable
fun AdminEmoji() {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    var tok by remember { mutableStateOf(Repo.get("tg_token")) }; var set by remember { mutableStateOf("") }; var ids by remember { mutableStateOf("") }; var msg by remember { mutableStateOf("") }
    val gallery = remember(Session.tick.intValue, msg) { Repo.emojiAll() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) { val txt = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText().orEmpty()
            val found = Regex("\\d{10,}").findAll(txt).map { it.value }.toList().distinct(); ids = found.joinToString("\n"); msg = "وُجد ${found.size} معرّف" }
    }
    fun run(label: String, f: () -> String) { msg = "$label..."; scope.launch { msg = withContext(Dispatchers.IO) { runCatching(f).getOrElse { "فشل: ${it.message}" } }; Session.refresh() } }
    Page("الإيموجي المميز") {
        Card { T("توكن البوت يُخزَّن على هذا الهاتف فقط ويُستخدم لجلب الإيموجي من تيليجرام.", 12, color = C.sub) }
        Field(tok, { tok = it }, "توكن البوت", password = true)
        Btn("حفظ التوكن", "primary") { Repo.put("tg_token", tok.trim()); toast("تم الحفظ") }
        Field(ids, { ids = it }, "معرّفات الإيموجي (واحد بكل سطر)", single = false)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TgButton("استيراد ملف", "plain", "1f4c4", Modifier.weight(1f)) { picker.launch(arrayOf("*/*")) }
            TgButton("جلب المعرّفات", "success", "1f504", Modifier.weight(1f)) { val l = Regex("\\d{10,}").findAll(ids).map { it.value }.toList(); run("جاري الجلب") { "تم حفظ ${TgApi.fetchIds(ctx, l)} إيموجي" } }
        }
        Btn("سحب كل حزمة المعرّفات", "primary", emoji = "1f4e6") { val l = Regex("\\d{10,}").findAll(ids).map { it.value }.toList().take(5); run("جاري سحب الحزم") { l.joinToString("\n") { val p = TgApi.pullPackOf(ctx, it); "${p.first}: ${p.second}" } } }
        Field(set, { set = it }, "اسم حزمة إيموجي (set name)")
        Btn("سحب الحزمة", "primary") { run("جاري السحب") { "تم حفظ ${TgApi.pullPack(ctx, set.trim())} إيموجي" } }
        if (msg.isNotBlank()) Card { T(msg, 13) }
        T("المحفوظ (${gallery.size})", 14, FontWeight.Bold)
        gallery.chunked(6).forEach { r -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { r.forEach { g -> Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { PremiumEmoji(g.first, 36.dp) } } } }
    }
}

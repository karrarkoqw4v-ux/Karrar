package com.vio.studio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private fun kindIcon(k: String) = when (k) { "video" -> Icons.Rounded.Movie; "photo" -> Icons.Rounded.PhotoCamera; "ai" -> Icons.Rounded.AutoAwesome; "pro" -> Icons.Rounded.WorkspacePremium; else -> Icons.Rounded.Link }
private fun kindBrush(k: String) = when (k) { "video" -> C.brand; "photo" -> C.warm; "ai" -> C.mint; "pro" -> C.cool; else -> C.cool }

@Composable
fun HomeScreen() {
    val u = Session.user
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VioLogo(52.dp)
            Column(Modifier.weight(1f)) {
                T(if (u != null) "أهلاً ${u.name}" else "أهلاً بك في Vio", 20, C.hi, FontWeight.ExtraBold)
                T("ماذا تريد أن تصنع اليوم؟", 13, C.lo)
            }
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(C.surface).border(1.dp, C.stroke, RoundedCornerShape(14.dp)).clickable { Nav.go("settings") }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Settings, null, tint = C.hi, modifier = Modifier.size(22.dp))
            }
        }
        // بطاقة رئيسية
        Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.brand).clickable { Nav.go("studio/video") }.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    T("ابدأ مونتاج جديد", 22, Color.White, FontWeight.ExtraBold)
                    T("اختر فيديو وأضف تأثيرات وألوان وموسيقى", 13, Color.White.copy(alpha = 0.85f))
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 18.dp, vertical = 8.dp)) { T("ابدأ الآن", 14, C.violet, FontWeight.ExtraBold) }
                }
                Icon(Icons.Rounded.Movie, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(72.dp))
            }
        }
        Spacer(Modifier.height(22.dp))
        T("الأدوات", 18, C.hi, FontWeight.ExtraBold, Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(12.dp))
        val secs = listOf(Catalog.video, Catalog.photo, Catalog.pro, Catalog.ai)
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            secs.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { s ->
                        Card(Modifier.weight(1f), onClick = { Nav.go("studio/${s.id}") }) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                IconBadge(s.icon, s.brush, 48.dp, 26.dp)
                                T(s.title, 15, C.hi, FontWeight.Bold, maxLines = 1)
                                T(s.sub, 11, C.lo, maxLines = 2)
                            }
                        }
                    }
                }
            }
            Card(Modifier.fillMaxWidth(), onClick = { Nav.go("link") }) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconBadge(Icons.Rounded.Link, C.cool, 48.dp, 26.dp)
                    Column(Modifier.weight(1f)) { T("مونتاج من رابط", 15, C.hi, FontWeight.Bold); T("تيك توك وإنستغرام بدون علامة مائية", 12, C.lo) }
                    Icon(Icons.Rounded.ChevronLeft, null, tint = C.lo)
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            T("آخر أعمالي", 18, C.hi, FontWeight.ExtraBold, Modifier.weight(1f))
            T("عرض الكل", 13, C.pink, FontWeight.Bold, Modifier.clickable { Nav.tab("account") })
        }
        Spacer(Modifier.height(12.dp))
        if (Session.works.isEmpty()) {
            Card(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { T("لا توجد أعمال بعد، ابدأ أول مونتاج", 13, C.lo) } }
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Session.works.take(8).forEach { w ->
                    Card(Modifier.width(150.dp)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconBadge(kindIcon(w.kind), kindBrush(w.kind), 40.dp, 22.dp)
                            T(w.title, 13, C.hi, FontWeight.Bold, maxLines = 1); T(ago(w.time), 11, C.lo)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StudioScreen(id: String) {
    val s = Catalog.get(id)
    var cat by remember(id) { mutableStateOf(0) }
    var tool by remember(id) { mutableStateOf(0) }
    var note by remember(id) { mutableStateOf("") }
    val ctx = LocalContext.current
    var picked by remember(id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) try { ctx.contentResolver.openInputStream(uri)?.use { picked = android.graphics.BitmapFactory.decodeStream(it) } } catch (e: Exception) { note = "تعذّر فتح الصورة" }
    }
    val base = remember(id) { Imaging.sample() }
    val src = picked ?: base
    val tools = s.cats[cat].tools
    val cur = tools.getOrNull(tool) ?: tools.first()
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            TopBar(s.title, back = false)
            // نوافذ التبويب بين الأقسام
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Catalog.video, Catalog.photo, Catalog.pro, Catalog.ai).forEach { sec -> Chip(sec.title, sec.id == id) { if (sec.id != id) Nav.tab("studio/${sec.id}") } }
            }
            Spacer(Modifier.height(14.dp))
            // المعاينة
            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(210.dp).clip(RoundedCornerShape(22.dp)).border(1.dp, C.stroke, RoundedCornerShape(22.dp)).background(C.surface), contentAlignment = Alignment.Center) {
                if (id == "photo") {
                    val f = Imaging.filters.firstOrNull { it.id == cur.id } ?: Imaging.filters.first()
                    val shown = remember(f.id, picked) { Imaging.apply(src, f) }
                    Image(shown.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Box(Modifier.fillMaxSize().background(C.brandV.let { androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF1B1440), Color(0xFF0B0818))) }), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            IconBadge(cur.icon, cur.brush, 64.dp, 34.dp)
                            T(cur.name, 18, C.hi, FontWeight.ExtraBold)
                            T("اختر فيديو لتطبيق هذا التأثير", 12, C.lo)
                        }
                    }
                }
                Box(Modifier.align(Alignment.TopStart).padding(10.dp)) { Tag(cur.name, C.cyan) }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.cats.forEachIndexed { i, c -> Chip(c.name, i == cat) { cat = i; tool = 0 } }
            }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tools.chunked(3).forEachIndexed { ri, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEachIndexed { ci, t ->
                            val idx = ri * 3 + ci; val on = idx == tool
                            Card(Modifier.weight(1f), radius = 16.dp, border = if (on) C.pink else C.stroke, fill = if (on) C.surface2 else C.surface, onClick = { tool = idx }) {
                                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (id == "photo") {
                                        val f = Imaging.filters.first { it.id == t.id }
                                        val th = remember(f.id) { Imaging.thumb(f) }
                                        Image(th.asImageBitmap(), null, Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                                    } else IconBadge(t.icon, t.brush, 42.dp, 22.dp)
                                    T(t.name, 12, if (on) C.hi else C.lo, if (on) FontWeight.Bold else FontWeight.Medium, center = true, maxLines = 1)
                                }
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            if (note.isNotEmpty()) { Spacer(Modifier.height(10.dp)); T(note, 13, C.gold, FontWeight.Bold, Modifier.padding(horizontal = 20.dp)) }
            Spacer(Modifier.height(16.dp))
        }
        Row(Modifier.fillMaxWidth().background(C.bg).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GhostButton(if (id == "photo") "اختيار صورة" else "اختيار فيديو", Modifier.weight(1f), Icons.Rounded.AddPhotoAlternate) {
                if (id == "photo") launcher.launch("image/*") else note = if (Session.server.isBlank()) "المعالجة تحتاج خادم Vio: اضبطه من الإعدادات" else "اختر الفيديو (قيد التطوير)"
            }
            GradButton(if (id == "photo") "حفظ في المعرض" else "ابدأ المعالجة", Modifier.weight(1f), s.brush, if (id == "photo") Icons.Rounded.Download else Icons.Rounded.PlayArrow) {
                if (id == "photo") {
                    val f = Imaging.filters.firstOrNull { it.id == cur.id } ?: Imaging.filters.first()
                    val ok = Imaging.save(ctx, Imaging.apply(src, f), f.id)
                    if (ok) { Repo.addWork("photo", "فلتر ${f.name}"); note = "تم الحفظ في Pictures/Vio" } else note = "تعذّر الحفظ"
                } else if (Session.server.isBlank()) note = "المعالجة تحتاج خادم Vio: اضبطه من الإعدادات"
                else { note = "تم الإرسال للمعالجة…"; Repo.addWork(id, cur.name) }
            }
        }
    }
}

@Composable
fun LinkScreen() {
    var url by remember { mutableStateOf("") }
    var plat by remember { mutableStateOf(0) }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar("مونتاج من رابط", back = false)
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconBadge(Icons.Rounded.Link, C.cool, 52.dp, 28.dp)
                    Column { T("الصق رابط الفيديو", 16, C.hi, FontWeight.ExtraBold); T("نجلبه بأعلى جودة وبدون علامة مائية", 12, C.lo) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Chip("تيك توك", plat == 0) { plat = 0 }; Chip("إنستغرام", plat == 1) { plat = 1 } }
            Field(url, { url = it }, if (plat == 0) "رابط تيك توك" else "رابط إنستغرام (ريلز / منشور)", Icons.Rounded.Link, modifier = Modifier.fillMaxWidth())
            GradButton(if (busy) "جارٍ الجلب…" else "جلب الفيديو", Modifier.fillMaxWidth(), C.cool, Icons.Rounded.CloudDownload, enabled = !busy && url.isNotBlank()) {
                busy = true; msg = ""
                Api.async({ Api.link(url) }) { r -> busy = false; msg = r; if (r.startsWith("تم")) Repo.addWork("link", if (plat == 0) "تيك توك بلا علامة" else "إنستغرام ريلز") }
            }
            if (msg.isNotEmpty()) T(msg, 13, if (msg.startsWith("تم")) C.green else C.gold, FontWeight.Bold)
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    T("كيف يعمل؟", 15, C.hi, FontWeight.ExtraBold)
                    listOf("1  انسخ رابط الفيديو من التطبيق", "2  الصقه هنا واضغط جلب", "3  أكمل المونتاج بالتأثيرات والموسيقى").forEach { T(it, 13, C.lo) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun AccountScreen() {
    val u = Session.user
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar("حسابي", back = false)
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(32.dp)).background(C.brandV), contentAlignment = Alignment.Center) { T((u?.name ?: "؟").take(1), 26, Color.White, FontWeight.ExtraBold) }
                    Column(Modifier.weight(1f)) {
                        T(u?.name ?: "زائر", 18, C.hi, FontWeight.ExtraBold)
                        T(if (u != null) "@${u.username}" else "أنت تتصفح بدون حساب", 13, C.lo)
                    }
                    if (u != null) Tag("حساب مفعّل", C.green)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("الأعمال" to Session.works.size, "الفيديو" to Session.works.count { it.kind == "video" || it.kind == "link" }, "الصور" to Session.works.count { it.kind == "photo" }).forEach { (n, v) ->
                    Card(Modifier.weight(1f)) { Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) { T("$v", 24, C.hi, FontWeight.ExtraBold); T(n, 12, C.lo) } }
                }
            }
            T("أعمالي", 17, C.hi, FontWeight.ExtraBold)
            if (Session.works.isEmpty()) T("لا توجد أعمال بعد", 13, C.lo)
            Session.works.take(10).forEach { w ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBadge(kindIcon(w.kind), kindBrush(w.kind), 42.dp, 22.dp)
                        Column(Modifier.weight(1f)) { T(w.title, 14, C.hi, FontWeight.Bold); T(ago(w.time), 11, C.lo) }
                    }
                }
            }
            GhostButton("الإعدادات", Modifier.fillMaxWidth(), Icons.Rounded.Settings) { Nav.go("settings") }
            if (u != null) GhostButton("تسجيل الخروج", Modifier.fillMaxWidth(), Icons.Rounded.PowerSettingsNew, C.red) { Repo.logout(); Nav.reset("welcome") }
            else GradButton("تسجيل الدخول", Modifier.fillMaxWidth(), icon = Icons.Rounded.Lock) { Repo.setGuest(false); Nav.reset("login") }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun SettingsScreen() {
    var srv by remember { mutableStateOf(Session.server) }
    var status by remember { mutableStateOf("") }
    var notif by remember { mutableStateOf(true) }; var hq by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar("الإعدادات")
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { IconBadge(Icons.Rounded.Dns, C.cool, 38.dp, 20.dp); T("خادم Vio", 16, C.hi, FontWeight.ExtraBold) }
                    T("الفيديو والذكاء الاصطناعي والروابط تُعالج على خادم البوت. ضع عنوانه هنا.", 12, C.lo)
                    Field(srv, { srv = it }, "عنوان الخادم", Icons.Rounded.Language, keyboard = androidx.compose.ui.text.input.KeyboardType.Uri, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GradButton("حفظ", Modifier.weight(1f), icon = Icons.Rounded.Check) { Repo.setServer(srv); status = "تم الحفظ" }
                        GhostButton("اختبار الاتصال", Modifier.weight(1f), Icons.Rounded.Wifi) { Repo.setServer(srv); status = "جارٍ الاختبار…"; Api.async({ Api.ping() }) { status = it } }
                    }
                    if (status.isNotEmpty()) T(status, 13, C.gold, FontWeight.Bold)
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    SwitchRow("الإشعارات", Icons.Rounded.Notifications, notif) { notif = it }
                    SwitchRow("حفظ بأعلى جودة", Icons.Rounded.HighQuality, hq) { hq = it }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    VioLogo(44.dp)
                    Column(Modifier.weight(1f)) { T("Vio Studio", 15, C.hi, FontWeight.ExtraBold); T("الإصدار 1.0 • تطبيق أندرويد أصلي (Kotlin)", 12, C.lo) }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, on: Boolean, change: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, null, tint = C.violet, modifier = Modifier.size(22.dp))
        T(label, 14, C.hi, modifier = Modifier.weight(1f))
        Switch(on, change, colors = SwitchDefaults.colors(checkedTrackColor = C.violet, checkedThumbColor = Color.White, uncheckedTrackColor = C.stroke, uncheckedThumbColor = C.lo, uncheckedBorderColor = C.stroke))
    }
}

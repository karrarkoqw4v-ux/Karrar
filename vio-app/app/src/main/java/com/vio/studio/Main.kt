package com.vio.studio

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private fun kindIcon(k: String) = when (k) { "video" -> Icons.Rounded.Movie; "photo" -> Icons.Rounded.PhotoCamera; "link" -> Icons.Rounded.Link; else -> Icons.Rounded.AutoFixHigh }

private data class Tile(val title: String, val sub: String, val icon: ImageVector, val brush: androidx.compose.ui.graphics.Brush, val route: String)

@Composable
fun HomeScreen() {
    val u = Session.user
    val tiles = listOf(
        Tile("مونتاج الفيديو", "${BotCatalog.fx.size} تأثيراً", Icons.Rounded.Movie, C.brand, "fx/ed"),
        Tile("قوالب جاهزة", "${BotCatalog.templates.size} قالباً", Icons.Rounded.Dashboard, C.warm, "templates"),
        Tile("تعديل الصور", "فلاتر على جهازك", Icons.Rounded.PhotoCamera, C.mint, "photo"),
        Tile("مونتاج من رابط", "تيك توك بلا علامة", Icons.Rounded.Link, C.cool, "link"),
        Tile("الذكاء الاصطناعي", "عزل وتضبيب الخلفية", Icons.Rounded.AutoAwesome, C.brand, "fx/ai"),
        Tile("الاحترافي", "تدريج وتباين وصوت", Icons.Rounded.WorkspacePremium, C.cool, "fx/pro"),
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VioLogo(48.dp)
            Column(Modifier.weight(1f)) {
                T(if (u != null) "أهلاً ${u.name}" else "أهلاً بك في Vio", Sz.H3 + 1, C.hi, FontWeight.ExtraBold)
                T("ماذا تريد أن تصنع اليوم؟", Sz.Small, Color.White.copy(alpha = 0.7f))
            }
        }
        Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(C.brand).clickable { Nav.go("fx/ed") }.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    T("ابدأ مونتاج جديد", Sz.H2, Color.White, FontWeight.ExtraBold)
                    T("اختر فيديو وأضف التأثيرات والألوان", Sz.Small, Color.White.copy(alpha = 0.9f))
                    Spacer(Modifier.height(6.dp))
                    Box(Modifier.clip(RoundedCornerShape(50)).background(Color.White).padding(horizontal = 18.dp, vertical = 8.dp)) { T("ابدأ الآن", Sz.Small + 1, C.violet, FontWeight.ExtraBold) }
                }
                Icon(Icons.Rounded.Movie, null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(68.dp))
            }
        }
        Spacer(Modifier.height(22.dp))
        T("الأدوات", Sz.H3, C.hi, FontWeight.ExtraBold, Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(12.dp))
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            tiles.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { s ->
                        GlassCard(Modifier.weight(1f), onClick = { Nav.go(s.route) }) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                IconBadge(s.icon, s.brush, 46.dp, 24.dp)
                                T(s.title, Sz.Body, C.hi, FontWeight.Bold, maxLines = 1)
                                T(s.sub, Sz.Tiny + 1, Color.White.copy(alpha = 0.65f), maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        T("آخر أعمالي", Sz.H3, C.hi, FontWeight.ExtraBold, Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(12.dp))
        if (Session.works.isEmpty()) {
            GlassCard(Modifier.padding(horizontal = 20.dp).fillMaxWidth()) { Box(Modifier.fillMaxWidth().padding(22.dp), contentAlignment = Alignment.Center) { T("لا توجد أعمال بعد", Sz.Small, Color.White.copy(alpha = 0.7f)) } }
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Session.works.take(8).forEach { w ->
                    GlassCard(Modifier.width(150.dp)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconBadge(kindIcon(w.kind), C.brand, 38.dp, 20.dp)
                            T(w.title, Sz.Small, C.hi, FontWeight.Bold, maxLines = 1); T(ago(w.time), Sz.Tiny, Color.White.copy(alpha = 0.6f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FxScreen(initial: String) {
    var cat by remember(initial) { mutableStateOf(initial) }
    var sel by remember(cat) { mutableStateOf<String?>(null) }
    val list = Cats.of(cat)
    val ctx = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { }
    Column(Modifier.fillMaxSize()) {
        TopBar("تأثيرات الفيديو", back = false)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotCatalog.cats.forEach { (id, _) -> Chip(Cats.short[id] ?: id, id == cat) { cat = id } }
        }
        Spacer(Modifier.height(6.dp))
        T(BotCatalog.cats.first { it.first == cat }.second + " • ${list.size}", Sz.Small, Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(list, key = { it.id }) { f ->
                val on = sel == f.id
                Row(
                    Modifier.fillMaxWidth().glass(18.dp, if (on) C.violet else Color.White, if (on) 0.28f else 0.07f).clickable { sel = f.id }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    FxThumb(f, 64.dp, 14.dp)
                    Column(Modifier.weight(1f)) { T(f.title, Sz.Body - 1, C.hi, FontWeight.Bold, maxLines = 2) }
                    if (on) Icon(Icons.Rounded.CheckCircle, null, tint = C.pink, modifier = Modifier.size(24.dp))
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            GlassButton(if (sel == null) "اختر تأثيراً أولاً" else "اختيار فيديو وتطبيق التأثير", Modifier.fillMaxWidth(), Icons.Rounded.VideoLibrary, C.violet, if (sel == null) 0.12f else 0.5f) {
                if (sel != null) picker.launch("video/*")
            }
        }
    }
}

@Composable
fun TemplatesScreen() {
    var sel by remember { mutableStateOf<Int?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { }
    Column(Modifier.fillMaxSize()) {
        TopBar("القوالب الجاهزة", back = false)
        T("كل قالب = مجموعة تأثيرات تُطبَّق دفعة واحدة", Sz.Small, Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(BotCatalog.templates.size) { i ->
                val t = BotCatalog.templates[i]; val on = sel == i
                val first = BotCatalog.fx.firstOrNull { it.id == t.fx.first() } ?: BotCatalog.fx.first()
                Row(Modifier.fillMaxWidth().glass(18.dp, if (on) C.violet else Color.White, if (on) 0.28f else 0.07f).clickable { sel = i }.padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FxThumb(first, 64.dp, 14.dp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        T(t.name, Sz.Body, C.hi, FontWeight.Bold, maxLines = 1)
                        T("${t.fx.size} تأثيرات في قالب واحد", Sz.Tiny + 1, Color.White.copy(alpha = 0.65f))
                    }
                    if (on) Icon(Icons.Rounded.CheckCircle, null, tint = C.pink, modifier = Modifier.size(24.dp))
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            GlassButton(if (sel == null) "اختر قالباً أولاً" else "اختيار فيديو وتطبيق القالب", Modifier.fillMaxWidth(), Icons.Rounded.VideoLibrary, C.violet, if (sel == null) 0.12f else 0.5f) { if (sel != null) picker.launch("video/*") }
        }
    }
}

@Composable
fun PhotoScreen() {
    val ctx = LocalContext.current
    var tool by remember { mutableStateOf(0) }
    var note by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) try { ctx.contentResolver.openInputStream(uri)?.use { picked = android.graphics.BitmapFactory.decodeStream(it) } } catch (e: Exception) { note = "تعذّر فتح الصورة" }
    }
    val src = picked ?: remember { Imaging.sample() }
    val f = Imaging.filters[tool]
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            TopBar("تعديل الصور")
            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(250.dp).glass(22.dp, Color.White, 0.05f)) {
                val shown = remember(f.id, picked) { Imaging.apply(src, f) }
                Image(shown.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Spacer(Modifier.height(14.dp))
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Imaging.filters.chunked(3).forEachIndexed { ri, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEachIndexed { ci, ft ->
                            val idx = ri * 3 + ci; val on = idx == tool
                            GlassCard(Modifier.weight(1f), 16.dp, if (on) 0.2f else 0.07f, onClick = { tool = idx }) {
                                Column(Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val th = remember(ft.id) { Imaging.thumb(ft) }
                                    Image(th.asImageBitmap(), null, Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                                    T(ft.name, Sz.Small - 1, if (on) C.hi else Color.White.copy(alpha = 0.7f), if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                                }
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
            if (note.isNotEmpty()) T(note, Sz.Small, C.gold, FontWeight.Bold, Modifier.padding(20.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton("اختيار صورة", Modifier.weight(1f), Icons.Rounded.AddPhotoAlternate) { launcher.launch("image/*") }
            GlassButton("حفظ في المعرض", Modifier.weight(1f), Icons.Rounded.Download, C.violet, 0.5f) {
                val ok = Imaging.save(ctx, Imaging.apply(src, f), f.id)
                if (ok) { Repo.addWork("photo", "فلتر ${f.name}"); note = "تم الحفظ في Pictures/Vio" } else note = "تعذّر الحفظ"
            }
        }
    }
}

@Composable
fun LinkScreen() {
    val ctx = LocalContext.current
    var url by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar("مونتاج من رابط")
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconBadge(Icons.Rounded.Link, C.cool, 50.dp, 26.dp)
                    Column { T("الصق رابط تيك توك", Sz.H3, C.hi, FontWeight.ExtraBold); T("بأعلى جودة وبدون علامة مائية", Sz.Small, Color.White.copy(alpha = 0.7f)) }
                }
            }
            Field(url, { url = it }, "رابط الفيديو", Icons.Rounded.Link, modifier = Modifier.fillMaxWidth())
            GlassButton(if (busy) "جارٍ الجلب…" else "جلب الفيديو", Modifier.fillMaxWidth(), Icons.Rounded.CloudDownload, C.violet, if (url.isBlank() || busy) 0.12f else 0.5f) {
                if (url.isNotBlank() && !busy) {
                    busy = true; msg = ""
                    Api.async({ Api.tiktok(ctx, url.trim()) }) { r -> busy = false; msg = r; if (r.startsWith("تم")) Repo.addWork("link", "تيك توك بلا علامة") }
                }
            }
            if (msg.isNotEmpty()) T(msg, Sz.Small, if (msg.startsWith("تم")) C.green else C.gold, FontWeight.Bold)
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    T("كيف يعمل؟", Sz.Body, C.hi, FontWeight.ExtraBold)
                    listOf("1  انسخ رابط الفيديو من تيك توك", "2  الصقه هنا واضغط جلب الفيديو", "3  يُحفظ في معرض هاتفك جاهزاً للمونتاج").forEach { T(it, Sz.Small, Color.White.copy(alpha = 0.75f)) }
                }
            }
        }
    }
}

@Composable
fun AccountScreen() {
    val u = Session.user
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar("حسابي", back = false)
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(Modifier.size(60.dp).clip(RoundedCornerShape(30.dp)).background(C.brandV), contentAlignment = Alignment.Center) { T((u?.name ?: "؟").take(1), Sz.H1, Color.White, FontWeight.ExtraBold) }
                    Column(Modifier.weight(1f)) {
                        T(u?.name ?: "زائر", Sz.H3 + 1, C.hi, FontWeight.ExtraBold)
                        T(if (u != null) "@${u.username}" else "تتصفح بدون حساب", Sz.Small, Color.White.copy(alpha = 0.7f))
                    }
                    if (u != null) Tag("حساب مفعّل", C.green)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("الأعمال" to Session.works.size, "الفيديو" to Session.works.count { it.kind == "video" || it.kind == "link" }, "الصور" to Session.works.count { it.kind == "photo" }).forEach { (n, v) ->
                    GlassCard(Modifier.weight(1f)) { Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) { T("$v", Sz.H2, C.hi, FontWeight.ExtraBold); T(n, Sz.Small, Color.White.copy(alpha = 0.7f)) } }
                }
            }
            T("أعمالي", Sz.H3, C.hi, FontWeight.ExtraBold)
            if (Session.works.isEmpty()) T("لا توجد أعمال بعد", Sz.Small, Color.White.copy(alpha = 0.7f))
            Session.works.take(10).forEach { w ->
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconBadge(kindIcon(w.kind), C.brand, 40.dp, 22.dp)
                        Column(Modifier.weight(1f)) { T(w.title, Sz.Body - 1, C.hi, FontWeight.Bold); T(ago(w.time), Sz.Tiny, Color.White.copy(alpha = 0.6f)) }
                    }
                }
            }
            if (u != null) GlassButton("تسجيل الخروج", Modifier.fillMaxWidth(), Icons.Rounded.PowerSettingsNew, C.red, 0.25f) { Repo.logout(); Nav.reset("welcome") }
            else GlassButton("تسجيل الدخول", Modifier.fillMaxWidth(), Icons.Rounded.Lock, C.violet, 0.5f) { Repo.setGuest(false); Nav.reset("login") }
            Spacer(Modifier.height(8.dp))
        }
    }
}

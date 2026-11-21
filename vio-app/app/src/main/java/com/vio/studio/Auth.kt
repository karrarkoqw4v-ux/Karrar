package com.vio.studio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun WelcomeScreen() {
    ScreenBg {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            VioLogo(150.dp)
            Spacer(Modifier.height(22.dp))
            T("Vio", 36, C.hi, FontWeight.ExtraBold)
            T("استوديو المونتاج الاحترافي في جيبك", Sz.Body, C.lo, center = true)
            Spacer(Modifier.height(28.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf(Icons.Rounded.Movie to "مونتاج فيديو بتأثيرات وألوان وقوالب", Icons.Rounded.PhotoCamera to "فلاتر صور تعمل على جهازك", Icons.Rounded.AutoAwesome to "مونتاج بالذكاء الاصطناعي", Icons.Rounded.Link to "جلب فيديو تيك توك وإنستغرام بلا علامة").forEach { (ic, tx) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IconBadge(ic, C.brand, 38.dp, 20.dp); T(tx, Sz.Body - 1, C.hi, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            GlassButton("تسجيل الدخول", Modifier.fillMaxWidth(), Icons.Rounded.Lock, C.violet, 0.45f) { Nav.go("login") }
            Spacer(Modifier.height(12.dp))
            GlassButton("إنشاء حساب جديد", Modifier.fillMaxWidth(), Icons.Rounded.PersonAdd) { Nav.go("register") }
            Spacer(Modifier.height(14.dp))
            T("تخطّي والمتابعة بدون حساب", Sz.Small + 1, Color.White.copy(alpha = 0.7f), modifier = Modifier.clickable { Repo.setGuest(true); Nav.reset("home") }.padding(8.dp))
        }
    }
}

@Composable
fun LoginScreen() {
    var u by remember { mutableStateOf("") }; var p by remember { mutableStateOf("") }; var err by remember { mutableStateOf("") }
    ScreenBg {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            TopBar("")
            Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                VioLogo(96.dp); Spacer(Modifier.height(14.dp))
                T("أهلاً بعودتك", Sz.H1, C.hi, FontWeight.ExtraBold)
                T("سجّل الدخول لتتابع أعمالك", Sz.Body, C.lo)
                Spacer(Modifier.height(26.dp))
                Field(u, { u = it; err = "" }, "اسم المستخدم", Icons.Rounded.Person, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(14.dp))
                Field(p, { p = it; err = "" }, "كلمة المرور", Icons.Rounded.Lock, password = true, modifier = Modifier.fillMaxWidth())
                if (err.isNotEmpty()) { Spacer(Modifier.height(10.dp)); T(err, 13, C.red, FontWeight.Bold) }
                Spacer(Modifier.height(22.dp))
                GlassButton("دخول", Modifier.fillMaxWidth(), Icons.Rounded.Login, C.violet, 0.5f) {
                    val e = Repo.login(u, p); if (e == null) Nav.reset("home") else err = e
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.clickable { Nav.stack.removeAt(Nav.stack.lastIndex); Nav.go("register") }.padding(8.dp)) {
                    T("ليس لديك حساب؟", Sz.Small + 1, Color.White.copy(alpha = 0.7f)); T("أنشئ حساباً", Sz.Small + 1, C.pink, FontWeight.Bold)
                }
            }
        }
    }
}

/** stage: 0 نموذج، 1 جارٍ الإنشاء، 2 تم */
@Composable
fun RegisterScreen() {
    var name by remember { mutableStateOf("") }; var u by remember { mutableStateOf("") }; var ph by remember { mutableStateOf("") }; var p by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }; var stage by remember { mutableStateOf(0) }
    val st = if (Debug.regStage != 0) Debug.regStage else stage
    ScreenBg {
        when (st) {
            1 -> Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                VioLogo(120.dp); Spacer(Modifier.height(26.dp))
                T("جارٍ إنشاء حسابك…", Sz.H2, C.hi, FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp)); T("نجهّز استوديو Vio الخاص بك", Sz.Small + 1, Color.White.copy(alpha = 0.7f))
                Spacer(Modifier.height(24.dp))
                androidx.compose.material3.LinearProgressIndicator(progress = { 0.62f }, modifier = Modifier.fillMaxWidth(0.7f).height(8.dp), color = C.pink, trackColor = C.stroke)
            }
            2 -> Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                IconBadge(Icons.Rounded.Check, C.mint, 96.dp, 56.dp); Spacer(Modifier.height(24.dp))
                T("تم إنشاء حسابك بنجاح", Sz.H2 + 2, C.hi, FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp)); T("مرحباً بك في Vio، ابدأ أول مونتاج الآن", 14, C.lo, center = true)
                Spacer(Modifier.height(28.dp))
                GlassButton("ابدأ الآن", Modifier.fillMaxWidth(), Icons.Rounded.PlayArrow, C.violet, 0.5f) { Debug.regStage = 0; Nav.reset("home") }
            }
            else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                TopBar("")
                Column(Modifier.padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    T("إنشاء حساب", Sz.H1, C.hi, FontWeight.ExtraBold)
                    T("خطوة واحدة وتبدأ المونتاج", Sz.Body, C.lo)
                    Spacer(Modifier.height(22.dp))
                    Field(name, { name = it; err = "" }, "الاسم", Icons.Rounded.Badge, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(12.dp))
                    Field(u, { u = it; err = "" }, "اسم المستخدم (إنجليزي)", Icons.Rounded.AlternateEmail, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(12.dp))
                    Field(ph, { ph = it }, "رقم الهاتف (اختياري)", Icons.Rounded.Phone, keyboard = KeyboardType.Phone, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(12.dp))
                    Field(p, { p = it; err = "" }, "كلمة المرور", Icons.Rounded.Lock, password = true, modifier = Modifier.fillMaxWidth())
                    if (err.isNotEmpty()) { Spacer(Modifier.height(10.dp)); T(err, 13, C.red, FontWeight.Bold) }
                    Spacer(Modifier.height(22.dp))
                    GlassButton("إنشاء الحساب", Modifier.fillMaxWidth(), Icons.Rounded.PersonAdd, C.violet, 0.5f) {
                        val e = Repo.register(name, u, ph, p); if (e == null) stage = 2 else err = e
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.clickable { Nav.stack.removeAt(Nav.stack.lastIndex); Nav.go("login") }.padding(8.dp)) {
                        T("لديك حساب؟", Sz.Small + 1, Color.White.copy(alpha = 0.7f)); T("سجّل الدخول", Sz.Small + 1, C.pink, FontWeight.Bold)
                    }
                }
            }
        }
    }
}

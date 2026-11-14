package com.turbo.rashq

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun UserSettings() {
    val u = Session.user ?: return
    Hub("الإعدادات", listOf(
        HubItem("set/info", "معلومات الحساب", E.user, "primary"), HubItem("set/username", "تغيير اسم المستخدم", E.edit, "primary"),
        HubItem("set/email", "تغيير البريد الإلكتروني", E.edit, "primary"), HubItem("set/pass", "تغيير كلمة السر", E.lock, "primary"),
        HubItem("set/privacy", "الخصوصية", E.lock, "success"), HubItem("set/notif", "الإشعارات", E.mega, "success"),
        HubItem("set/colors", "الألوان", E.free, "success"), HubItem("msg", "الرسائل", E.mega, "success")))
}

@Composable
fun EditOne(title: String, label: String, initial: String, password: Boolean = false, save: (String) -> String?) {
    var v by remember { mutableStateOf(initial) }
    Page(title) {
        Field(v, { v = it }, label, password = password)
        Btn("حفظ", "success", emoji = E.check) { val e = save(v); if (e == null) { toast("تم الحفظ"); Session.refresh(); Nav.back() } else toast(e) }
    }
}

@Composable
fun SetInfo() {
    val u = Session.user ?: return
    var n by remember { mutableStateOf(u.name) }; var p by remember { mutableStateOf(u.phone) }
    Page("معلومات الحساب") {
        Card { T("ID: ${u.uid}", 14, FontWeight.Bold); T("@${u.username}", 13, color = C.sub); T("البريد: ${u.email.ifBlank { "غير مضاف" }}", 13, color = C.sub) }
        Field(n, { n = it }, "الاسم"); Field(p, { p = it }, "رقم الهاتف", number = true)
        Btn("حفظ", "success", emoji = E.check) { val e = Repo.changeName(u.uid, n) ?: Repo.changePhone(u.uid, p); if (e == null) { toast("تم الحفظ"); Session.refresh() } else toast(e) }
    }
}

@Composable
fun SetPass() {
    val u = Session.user ?: return
    var o by remember { mutableStateOf("") }; var n by remember { mutableStateOf("") }; var n2 by remember { mutableStateOf("") }
    Page("تغيير كلمة السر") {
        Field(o, { o = it }, "كلمة السر الحالية", password = true); Field(n, { n = it }, "الجديدة", password = true); Field(n2, { n2 = it }, "تأكيد الجديدة", password = true)
        Btn("تغيير", "success", emoji = E.lock) { if (n != n2) toast("غير متطابقتين") else { val e = Repo.changePass(u.uid, o, n); if (e == null) { toast("تم التغيير"); Nav.back() } else toast(e) } }
    }
}

@Composable
fun PrefToggle(uid: String, label: String, key: String, def: Boolean, note: String = "") {
    var on by remember { mutableStateOf(Repo.pref(uid, key, def)) }
    Card { Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { T(label, 14, FontWeight.Bold); if (note.isNotBlank()) T(note, 11, color = C.sub) }
        Switch(on, { on = it; Repo.setPref(uid, key, it); Session.refresh() }) } }
}

@Composable
fun SetPrivacy() {
    val u = Session.user ?: return
    Page("الخصوصية") {
        PrefToggle(u.uid, "إخفاء الرصيد في الرئيسية", "hide_bal", false)
        PrefToggle(u.uid, "إرسال التحويلات كمجهول", "anon_send", false, "لن يظهر اسمك في إشعار المستلم")
        PrefToggle(u.uid, "السماح باستقبال التحويلات", "allow_in", true)
        PrefToggle(u.uid, "السماح بالرسائل من المستخدمين", "allow_msg", true)
    }
}

@Composable
fun SetNotif() {
    val u = Session.user ?: return
    Page("الإشعارات") {
        PrefToggle(u.uid, "تفعيل إشعارات التطبيق", "n_all", true, "الإشعارات تبقى محفوظة داخل التطبيق دائماً")
        PrefToggle(u.uid, "💰 التحويلات", "n_transfer", true); PrefToggle(u.uid, "🎁 الهدايا", "n_gift", true)
        PrefToggle(u.uid, "💬 الرسائل", "n_msg", true); PrefToggle(u.uid, "📢 الإذاعات", "n_broadcast", true)
        PrefToggle(u.uid, "📰 المنشورات", "n_post", true); PrefToggle(u.uid, "📦 تحديثات الطلبات", "n_order", true)
    }
}

val swatches = listOf("أزرق" to 0xFF2563EBL, "أخضر" to 0xFF16A34AL, "بنفسجي" to 0xFF7C3AEDL, "برتقالي" to 0xFFEA580CL, "وردي" to 0xFFDB2777L, "رمادي" to 0xFF475569L)

@Composable
fun SwatchRow(uid: String, key: String, def: Long) {
    var cur by remember { mutableStateOf(Repo.prefStr(uid, key, def.toString()).toLong()) }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        swatches.forEach { (n, c) ->
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(Color(c)).border(if (cur == c) 3.dp else 0.dp, Color.White, RoundedCornerShape(14.dp))
                .clickable { cur = c; Repo.put("u_${uid}_$key", c.toString()); Session.refresh() })
        }
    }
}

@Composable
fun SetColors() {
    val u = Session.user ?: return
    Page("الألوان") {
        T("لون بطاقة الرصيد", 14, FontWeight.Bold); SwatchRow(u.uid, "accent", 0xFF2563EBL)
        T("لون فقاعات الرسائل والإشعارات", 14, FontWeight.Bold); SwatchRow(u.uid, "bubble", 0xFF2563EBL)
        T("معاينة", 13, color = C.sub)
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PremiumEmoji("5431416823637622117", 34.dp)
            Column(Modifier.clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)).background(Repo.bubble(u.uid)).padding(12.dp)) { T("وصلك تحويل 💰", 14, FontWeight.Bold, Color.White); T("استلمت 100 نقطة من أحمد", 13, color = Color.White.copy(alpha = .9f)) }
        }
    }
}

fun typeEmoji(t: String) = when (t) { "transfer" -> "5431416823637622117"; "gift" -> "5823689256366771156"; "msg" -> "5190558695515272787"; "order" -> "5935832686416366431"; "post", "broadcast" -> "5249148968625002018"; else -> "5247134066322447995" }

@Composable
fun NotifBubble(n: Notif, color: Color) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        PremiumEmoji(typeEmoji(n.type), 36.dp)
        Column(Modifier.weight(1f, false).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp)).background(color).padding(12.dp)) {
            T(n.title, 14, FontWeight.Bold, Color.White); T(n.body, 13, color = Color.White.copy(alpha = .92f)); T(fmtTime(n.ts), 10, color = Color.White.copy(alpha = .65f))
        }
    }
}

@Composable
fun MessagesScreen() {
    val u = Session.user ?: return
    var to by remember { mutableStateOf("") }; var text by remember { mutableStateOf("") }
    val list = remember(Session.tick.intValue) { Repo.notifs(u.uid).filter { it.type == "msg" } }
    Page("الرسائل") {
        Field(to, { to = it }, "اسم المستخدم المستلم"); Field(text, { text = it }, "الرسالة", single = false)
        Btn("إرسال", "success", emoji = E.mega) {
            val t = Repo.userByUsername(to)
            if (t != null && !Repo.pref(t.uid, "allow_msg", true)) toast("هذا المستخدم أوقف الرسائل")
            else { val e = Repo.sendMessage(u, to, text); if (e == null) { toast("تم الإرسال"); text = "" } else toast(e) } }
        T("الرسائل الواردة", 14, FontWeight.Bold)
        if (list.isEmpty()) T("لا توجد رسائل", 12, color = C.sub)
        list.forEach { NotifBubble(it, Repo.bubble(u.uid)) }
    }
}

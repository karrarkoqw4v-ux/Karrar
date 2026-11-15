package com.lekowatch.guardian.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lekowatch.guardian.navigation.Routes
import com.lekowatch.guardian.ui.theme.LekoCard
import com.lekowatch.guardian.ui.theme.LekoInk
import com.lekowatch.guardian.ui.theme.LekoSubtext

private val points = listOf(
    "هذا التطبيق مخصَّص لرقابة الوالدين على أجهزة أبنائهم القاصرين فقط، بعلم الطفل ورضاه.",
    "تطبيق الطفل يظهر بأيقونة واضحة دائمًا في قائمة التطبيقات، ولا يختفي بعد التثبيت.",
    "لا نقرأ محتوى الرسائل أو المكالمات، ولا نسجّل الشاشة أو الصوت دون إشعار واضح للطفل في كل مرة.",
    "يقدر الطفل يفتح التطبيق بنفسه بأي وقت ليشوف بالضبط شنو يقدر والده يشوفه.",
)

@Composable
fun ConsentScreen(onNavigate: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp)) {
        Text(
            "قبل ما نكمل، تعرّف على التزامنا",
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold,
            color = LekoInk,
        )
        Spacer(Modifier.padding(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LekoCard, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            points.forEach { p -> Text("•  $p", fontSize = 13.sp, color = Color4453) }
        }

        Spacer(Modifier.padding(12.dp))
        Text(
            "بالمتابعة، أنت تقر بأنك الوصي القانوني على الجهاز الذي سيُستخدم عليه هذا التطبيق، وتوافق على شروط الخدمة وسياسة الخصوصية.",
            fontSize = 12.sp,
            color = LekoSubtext,
        )

        Spacer(Modifier.weight(1f))
        LekoPrimaryButton("أوافق، تابع") { onNavigate(Routes.ADD_DEVICE) }
    }
}

private val Color4453 = androidx.compose.ui.graphics.Color(0xFF3A4453)

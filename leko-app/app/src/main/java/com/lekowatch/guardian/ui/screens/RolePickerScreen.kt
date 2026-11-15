package com.lekowatch.guardian.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

@Composable
fun RolePickerScreen(onNavigate: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("لمن هذا الجهاز؟", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = LekoInk)
        Text(
            "اختر الدور المناسب لإعداد التطبيق على هذا الجهاز بالشكل الصحيح",
            fontSize = 13.5.sp,
            color = LekoSubtext,
        )
        Spacer(Modifier.padding(4.dp))

        RoleCard(
            title = "جهاز الوالدين",
            subtitle = "هذا جهازي، وسأستخدمه لمتابعة طفلي",
            onClick = { onNavigate(Routes.DASHBOARD) },
        )
        RoleCard(
            title = "جهاز الطفل",
            subtitle = "هذا جهاز سأثبّت عليه تطبيق المتابعة لطفلي",
            onClick = { onNavigate(Routes.CONSENT) },
        )
    }
}

@Composable
private fun RoleCard(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = LekoCard),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = LekoInk)
            Text(subtitle, fontSize = 12.5.sp, color = LekoSubtext)
        }
    }
}

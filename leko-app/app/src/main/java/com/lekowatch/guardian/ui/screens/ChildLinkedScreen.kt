package com.lekowatch.guardian.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lekowatch.guardian.ui.theme.LekoInk
import com.lekowatch.guardian.ui.theme.LekoSubtext
import com.lekowatch.guardian.ui.theme.LekoTeal

@Composable
fun ChildLinkedScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("✓", fontSize = 40.sp, color = LekoTeal, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.padding(10.dp))
        Text("تم ربط هذا الجهاز بنجاح", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = LekoInk)
        Spacer(Modifier.padding(8.dp))
        Text(
            "جهازك مرتبط الآن بحساب والدك. أيقونة هذا التطبيق تضل ظاهرة دائمًا في قائمة تطبيقاتك، "
                + "وتقدر تفتحه بأي وقت لتشوف بالضبط شنو يقدر والدك يشوفه.",
            fontSize = 13.sp,
            color = LekoSubtext,
        )
    }
}

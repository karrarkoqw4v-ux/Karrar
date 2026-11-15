package com.lekowatch.guardian.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.lekowatch.guardian.ui.theme.LekoBlue
import com.lekowatch.guardian.ui.theme.LekoInk
import com.lekowatch.guardian.ui.theme.LekoSubtext
import com.lekowatch.guardian.viewmodel.DeviceViewModel

@Composable
fun AddDeviceScreen(vm: DeviceViewModel) {
    val parentUid = FirebaseAuth.getInstance().currentUser?.uid

    LaunchedEffect(parentUid) {
        parentUid?.let { vm.generateCode(it) }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("ربط جهاز طفل", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = LekoInk)
        Spacer(Modifier.padding(6.dp))
        Text(
            "افتح تطبيق الطفل بجهازه وأدخل هذا الرمز خلال 15 دقيقة",
            fontSize = 13.sp,
            color = LekoSubtext,
        )
        Spacer(Modifier.padding(18.dp))

        val code = vm.pairingCode
        if (code == null) {
            CircularProgressIndicator(color = LekoBlue)
        } else {
            Text(
                code.chunked(3).joinToString("  "),
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = LekoInk,
            )
        }

        vm.errorMessage?.let {
            Spacer(Modifier.padding(10.dp))
            ErrorBanner(it)
        }
    }
}

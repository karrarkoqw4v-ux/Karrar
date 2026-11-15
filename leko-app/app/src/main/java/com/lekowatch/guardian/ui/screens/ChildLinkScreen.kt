package com.lekowatch.guardian.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lekowatch.guardian.navigation.Routes
import com.lekowatch.guardian.ui.theme.LekoInk
import com.lekowatch.guardian.ui.theme.LekoSubtext
import com.lekowatch.guardian.viewmodel.DeviceViewModel

@Composable
fun ChildLinkScreen(vm: DeviceViewModel, deviceLabel: String, onNavigate: (String) -> Unit) {
    var code by remember { mutableStateOf("") }

    LaunchedEffect(vm.linkedDevice) {
        if (vm.linkedDevice != null) onNavigate(Routes.CHILD_LINKED)
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("أدخل رمز الربط", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = LekoInk)
        Spacer(Modifier.padding(8.dp))
        Text(
            "اطلب من والدك رمز الربط الظاهر في تطبيقه، وأدخله هنا",
            fontSize = 13.sp,
            color = LekoSubtext,
        )
        Spacer(Modifier.padding(18.dp))

        LekoTextField(code, { code = it.filter(Char::isDigit).take(9) }, "رمز الربط (9 أرقام)", keyboardType = KeyboardType.Number)
        Spacer(Modifier.padding(10.dp))

        vm.errorMessage?.let {
            ErrorBanner(it)
            Spacer(Modifier.padding(8.dp))
        }

        LekoPrimaryButton("ربط هذا الجهاز", enabled = code.length == 9) {
            vm.redeemCode(code, deviceLabel)
        }
    }
}

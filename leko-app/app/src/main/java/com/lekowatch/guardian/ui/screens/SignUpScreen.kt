package com.lekowatch.guardian.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lekowatch.guardian.navigation.Routes
import com.lekowatch.guardian.ui.theme.LekoInk
import com.lekowatch.guardian.ui.theme.LekoSubtext
import com.lekowatch.guardian.viewmodel.AuthViewModel
import com.lekowatch.guardian.viewmodel.UiState

@Composable
fun SignUpScreen(vm: AuthViewModel, onNavigate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }

    LaunchedEffect(vm.state) {
        if (vm.state is UiState.Success) {
            vm.resetState()
            onNavigate(Routes.VERIFY)
        }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 24.dp)) {
        Spacer(Modifier.padding(top = 20.dp))
        Text("إنشاء حساب جديد", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = LekoInk)
        Text(
            "لإدارة رقابة أبوية شفافة على أجهزة أطفالك",
            fontSize = 13.sp,
            color = LekoSubtext,
            modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
        )

        LekoTextField(name, { name = it }, "الاسم")
        Spacer(Modifier.padding(6.dp))
        LekoTextField(email, { email = it }, "البريد الإلكتروني", keyboardType = KeyboardType.Email)
        Spacer(Modifier.padding(6.dp))
        LekoTextField(password, { password = it }, "كلمة المرور", isPassword = true)
        Spacer(Modifier.padding(6.dp))
        LekoTextField(confirm, { confirm = it }, "تأكيد كلمة المرور", isPassword = true)
        Spacer(Modifier.padding(10.dp))

        if (vm.state is UiState.Error) {
            ErrorBanner((vm.state as UiState.Error).message)
            Spacer(Modifier.padding(6.dp))
        }

        LekoPrimaryButton(
            text = if (vm.state is UiState.Loading) "جارٍ الإنشاء..." else "إنشاء الحساب",
            enabled = vm.state !is UiState.Loading,
        ) {
            vm.signUp(name, email, password, confirm)
        }
    }
}

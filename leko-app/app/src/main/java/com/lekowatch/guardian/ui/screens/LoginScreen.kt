package com.lekowatch.guardian.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.lekowatch.guardian.viewmodel.AuthViewModel
import com.lekowatch.guardian.viewmodel.UiState

@Composable
fun LoginScreen(vm: AuthViewModel, onNavigate: (String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(vm.state) {
        if (vm.state is UiState.Success) {
            vm.resetState()
            onNavigate(Routes.ROLE_PICKER)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("ليكو مراقبكم", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = LekoInk)
        Text(
            "حساب الوالد — رقابة أبوية شفّافة",
            fontSize = 14.sp,
            color = LekoSubtext,
            modifier = Modifier.padding(top = 4.dp, bottom = 28.dp),
        )

        LekoTextField(email, { email = it }, "البريد الإلكتروني", keyboardType = KeyboardType.Email)
        androidx.compose.foundation.layout.Spacer(Modifier.padding(6.dp))
        LekoTextField(password, { password = it }, "كلمة المرور", isPassword = true)

        androidx.compose.foundation.layout.Spacer(Modifier.padding(10.dp))

        if (vm.state is UiState.Error) {
            ErrorBanner((vm.state as UiState.Error).message)
            androidx.compose.foundation.layout.Spacer(Modifier.padding(6.dp))
        }

        LekoPrimaryButton(
            text = if (vm.state is UiState.Loading) "جارٍ تسجيل الدخول..." else "تسجيل الدخول",
            enabled = vm.state !is UiState.Loading,
        ) {
            vm.signIn(email, password)
        }

        TextButton(onClick = { onNavigate(Routes.SIGNUP) }) {
            Text("ليس لديك حساب؟ إنشاء حساب جديد")
        }
    }
}

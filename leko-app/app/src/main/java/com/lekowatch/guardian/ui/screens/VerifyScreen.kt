package com.lekowatch.guardian.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lekowatch.guardian.data.AuthRepository
import com.lekowatch.guardian.navigation.Routes
import com.lekowatch.guardian.ui.theme.LekoInk
import com.lekowatch.guardian.ui.theme.LekoSubtext
import kotlinx.coroutines.launch

@Composable
fun VerifyScreen(onNavigate: (String) -> Unit) {
    val repo = remember { AuthRepository() }
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("التحقق من بريدك الإلكتروني", fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = LekoInk)
        Spacer(Modifier.padding(8.dp))
        Text(
            "أرسلنا رابط تفعيل إلى بريدك. افتحه، ثم ارجع هنا واضغط \"تحققت\".",
            fontSize = 13.5.sp,
            color = LekoSubtext,
        )
        Spacer(Modifier.padding(16.dp))

        message?.let { Text(it, fontSize = 12.5.sp, color = LekoSubtext) }
        Spacer(Modifier.padding(10.dp))

        LekoPrimaryButton("تحققت، متابعة") {
            scope.launch {
                repo.reloadUser().fold(
                    onSuccess = { verified ->
                        if (verified) {
                            onNavigate(Routes.ROLE_PICKER)
                        } else {
                            message = "لم يتم تفعيل بريدك بعد — افتح الرابط من إيميلك أولاً"
                        }
                    },
                    onFailure = { message = it.message },
                )
            }
        }

        TextButton(onClick = { scope.launch { repo.sendVerificationEmail() } }) {
            Text("إعادة إرسال رابط التفعيل")
        }
    }
}

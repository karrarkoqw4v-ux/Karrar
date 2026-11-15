package com.lekowatch.guardian.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lekowatch.guardian.ui.theme.LekoBlue
import com.lekowatch.guardian.ui.theme.LekoBorder
import com.lekowatch.guardian.ui.theme.LekoDanger
import com.lekowatch.guardian.ui.theme.LekoInk
import com.lekowatch.guardian.ui.theme.LekoSurface

@Composable
fun LekoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (isPassword) {
            androidx.compose.ui.text.input.PasswordVisualTransformation()
        } else VisualTransformation.None,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = LekoSurface,
            unfocusedContainerColor = LekoSurface,
            focusedBorderColor = LekoBlue,
            unfocusedBorderColor = LekoBorder,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun LekoPrimaryButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = LekoBlue, contentColor = Color.White),
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp),
    ) {
        Text(text, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}

@Composable
fun ErrorBanner(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFDECEC), RoundedCornerShape(12.dp))
            .border(1.dp, LekoDanger.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text(message, color = LekoDanger, fontSize = 13.sp)
    }
}

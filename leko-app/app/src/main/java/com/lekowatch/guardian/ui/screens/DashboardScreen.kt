package com.lekowatch.guardian.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.lekowatch.guardian.data.model.ChildDevice
import com.lekowatch.guardian.navigation.Routes
import com.lekowatch.guardian.ui.theme.LekoCard
import com.lekowatch.guardian.ui.theme.LekoInk
import com.lekowatch.guardian.ui.theme.LekoSubtext
import com.lekowatch.guardian.ui.theme.LekoTeal
import com.lekowatch.guardian.viewmodel.DeviceViewModel

@Composable
fun DashboardScreen(vm: DeviceViewModel, onNavigate: (String) -> Unit) {
    val parentUid = FirebaseAuth.getInstance().currentUser?.uid

    LaunchedEffect(parentUid) {
        parentUid?.let { vm.loadDevices(it) }
    }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("أجهزة أطفالي", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = LekoInk)
            Text(
                "+ إضافة جهاز",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = com.lekowatch.guardian.ui.theme.LekoBlue,
                modifier = Modifier.clickableAdd(onNavigate),
            )
        }
        Spacer(Modifier.padding(10.dp))

        if (vm.devices.isEmpty()) {
            Text("ما عندك أجهزة مربوطة بعد. اضغط \"إضافة جهاز\" للبدء.", fontSize = 13.sp, color = LekoSubtext)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(vm.devices) { device -> DeviceCard(device) }
            }
        }
    }
}

private fun Modifier.clickableAdd(onNavigate: (String) -> Unit): Modifier =
    this.then(androidx.compose.foundation.clickable { onNavigate(Routes.ADD_DEVICE) })

@Composable
private fun DeviceCard(device: ChildDevice) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(LekoCard, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Text(device.label.ifBlank { "جهاز بدون اسم" }, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = LekoInk)
        Spacer(Modifier.padding(2.dp))
        Text(
            "وقت الشاشة اليوم: ${device.screenTimeTodayMinutes} / ${device.screenTimeLimitMinutes} دقيقة",
            fontSize = 12.sp,
            color = LekoSubtext,
        )
        if (device.lastLat != null && device.lastLng != null) {
            Text(
                "آخر موقع: ${"%.4f".format(device.lastLat)}, ${"%.4f".format(device.lastLng)}",
                fontSize = 12.sp,
                color = LekoTeal,
            )
        }
    }
}

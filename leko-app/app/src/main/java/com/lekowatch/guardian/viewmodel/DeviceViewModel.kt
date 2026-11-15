package com.lekowatch.guardian.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lekowatch.guardian.data.DeviceRepository
import com.lekowatch.guardian.data.model.ChildDevice
import kotlinx.coroutines.launch

class DeviceViewModel(private val repo: DeviceRepository = DeviceRepository()) : ViewModel() {
    var pairingCode by mutableStateOf<String?>(null)
        private set

    var devices by mutableStateOf<List<ChildDevice>>(emptyList())
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var linkedDevice by mutableStateOf<ChildDevice?>(null)
        private set

    fun generateCode(parentUid: String) {
        viewModelScope.launch {
            repo.createPairingCode(parentUid).fold(
                onSuccess = { pairingCode = it },
                onFailure = { errorMessage = it.message },
            )
        }
    }

    fun loadDevices(parentUid: String) {
        viewModelScope.launch {
            repo.listDevicesForParent(parentUid).onSuccess { devices = it }
        }
    }

    fun redeemCode(code: String, deviceLabel: String) {
        viewModelScope.launch {
            repo.redeemPairingCode(code, deviceLabel).fold(
                onSuccess = { linkedDevice = it },
                onFailure = { errorMessage = it.message },
            )
        }
    }
}

package com.lekowatch.guardian.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lekowatch.guardian.data.AuthRepository
import kotlinx.coroutines.launch

sealed class UiState {
    object Idle : UiState()
    object Loading : UiState()
    data class Error(val message: String) : UiState()
    object Success : UiState()
}

class AuthViewModel(private val repo: AuthRepository = AuthRepository()) : ViewModel() {
    var state by mutableStateOf<UiState>(UiState.Idle)
        private set

    fun signUp(name: String, email: String, password: String, confirm: String) {
        if (name.isBlank() || email.isBlank() || password.isBlank()) {
            state = UiState.Error("رجاءً عبّي كل الحقول")
            return
        }
        if (password != confirm) {
            state = UiState.Error("كلمتا المرور غير متطابقتين")
            return
        }
        state = UiState.Loading
        viewModelScope.launch {
            val result = repo.signUp(name, email, password)
            state = result.fold(
                onSuccess = { UiState.Success },
                onFailure = { UiState.Error(it.message ?: "تعذّر إنشاء الحساب") },
            )
        }
    }

    fun signIn(email: String, password: String) {
        if (email.isBlank() || password.isBlank()) {
            state = UiState.Error("رجاءً عبّي كل الحقول")
            return
        }
        state = UiState.Loading
        viewModelScope.launch {
            val result = repo.signIn(email, password)
            state = result.fold(
                onSuccess = { UiState.Success },
                onFailure = { UiState.Error(it.message ?: "تعذّر تسجيل الدخول") },
            )
        }
    }

    fun resetState() {
        state = UiState.Idle
    }
}

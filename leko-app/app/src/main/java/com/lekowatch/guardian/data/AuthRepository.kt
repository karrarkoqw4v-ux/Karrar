package com.lekowatch.guardian.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.lekowatch.guardian.data.model.UserProfile
import kotlinx.coroutines.tasks.await

/**
 * Real authentication backed by Firebase Auth. No mocked/demo accounts:
 * every call hits Firebase and persists the profile in Firestore.
 */
class AuthRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    val currentUid: String? get() = auth.currentUser?.uid

    suspend fun signUp(name: String, email: String, password: String): Result<String> = runCatching {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        val uid = result.user?.uid ?: error("فشل إنشاء الحساب")
        result.user?.sendEmailVerification()?.await()

        val profile = UserProfile(
            uid = uid,
            name = name,
            email = email,
            createdAtMillis = System.currentTimeMillis(),
        )
        db.collection("users").document(uid).set(profile).await()
        uid
    }

    suspend fun signIn(email: String, password: String): Result<String> = runCatching {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        result.user?.uid ?: error("فشل تسجيل الدخول")
    }

    suspend fun sendVerificationEmail(): Result<Unit> = runCatching {
        auth.currentUser?.sendEmailVerification()?.await()
        Unit
    }

    suspend fun reloadUser(): Result<Boolean> = runCatching {
        val user = auth.currentUser ?: error("لا يوجد مستخدم مسجَّل")
        user.reload().await()
        user.isEmailVerified
    }

    fun signOut() = auth.signOut()
}

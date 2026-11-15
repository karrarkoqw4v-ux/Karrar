package com.lekowatch.guardian.data.model

enum class DeviceRole { PARENT, CHILD }

data class UserProfile(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val createdAtMillis: Long = 0L,
)

data class PairingCode(
    val code: String = "",
    val parentUid: String = "",
    val createdAtMillis: Long = 0L,
    val expiresAtMillis: Long = 0L,
    val consumed: Boolean = false,
)

data class ChildDevice(
    val deviceId: String = "",
    val parentUid: String = "",
    val label: String = "",
    val linkedAtMillis: Long = 0L,
    val lastSeenMillis: Long = 0L,
    val lastLat: Double? = null,
    val lastLng: Double? = null,
    val screenTimeTodayMinutes: Int = 0,
    val screenTimeLimitMinutes: Int = 240,
)

package com.lekowatch.guardian.data

import com.google.firebase.firestore.FirebaseFirestore
import com.lekowatch.guardian.data.model.ChildDevice
import com.lekowatch.guardian.data.model.PairingCode
import kotlinx.coroutines.tasks.await
import kotlin.random.Random

private const val PAIRING_TTL_MILLIS = 15 * 60 * 1000L // 15 دقيقة

/**
 * ربط جهاز الطفل بحساب الوالد عبر رمز حقيقي مخزَّن في Firestore.
 * لا يوجد أي منطق لإخفاء الأيقونة أو طلب صلاحيات مشرف الجهاز هنا أو في أي مكان آخر بالتطبيق.
 */
class DeviceRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    /** يولّد رمز ربط من 9 أرقام صالح لمدة 15 دقيقة، ويخزّنه تحت حساب الوالد. */
    suspend fun createPairingCode(parentUid: String): Result<String> = runCatching {
        val code = (1..9).joinToString("") { Random.nextInt(0, 10).toString() }
        val now = System.currentTimeMillis()
        val pairing = PairingCode(
            code = code,
            parentUid = parentUid,
            createdAtMillis = now,
            expiresAtMillis = now + PAIRING_TTL_MILLIS,
            consumed = false,
        )
        db.collection("pairingCodes").document(code).set(pairing).await()
        code
    }

    /** يُستدعى من تطبيق الطفل بعد إدخال الرمز: يتحقق من صلاحيته ويربط الجهاز بحساب الوالد. */
    suspend fun redeemPairingCode(code: String, deviceLabel: String): Result<ChildDevice> = runCatching {
        val doc = db.collection("pairingCodes").document(code).get().await()
        val pairing = doc.toObject(PairingCode::class.java) ?: error("رمز غير صحيح")

        if (pairing.consumed) error("هذا الرمز مستخدَم سابقًا")
        if (System.currentTimeMillis() > pairing.expiresAtMillis) error("انتهت صلاحية هذا الرمز، اطلب رمزًا جديدًا")

        val deviceId = db.collection("devices").document().id
        val device = ChildDevice(
            deviceId = deviceId,
            parentUid = pairing.parentUid,
            label = deviceLabel,
            linkedAtMillis = System.currentTimeMillis(),
            lastSeenMillis = System.currentTimeMillis(),
        )

        db.collection("devices").document(deviceId).set(device).await()
        db.collection("pairingCodes").document(code).update("consumed", true).await()
        device
    }

    suspend fun listDevicesForParent(parentUid: String): Result<List<ChildDevice>> = runCatching {
        db.collection("devices")
            .whereEqualTo("parentUid", parentUid)
            .get()
            .await()
            .toObjects(ChildDevice::class.java)
    }

    /** يُستدعى دوريًا من تطبيق الطفل فقط أثناء استخدامه الفعلي — لا تشغيل خلفي صامت دائم. */
    suspend fun reportLocation(deviceId: String, lat: Double, lng: Double): Result<Unit> = runCatching {
        db.collection("devices").document(deviceId).update(
            mapOf(
                "lastLat" to lat,
                "lastLng" to lng,
                "lastSeenMillis" to System.currentTimeMillis(),
            )
        ).await()
        Unit
    }
}

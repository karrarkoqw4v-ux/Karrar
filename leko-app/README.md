# ليكو مراقبكم (Leko Guardian)

تطبيق رقابة أبوية شفافة — Kotlin + Jetpack Compose + Firebase (حساب حقيقي، بدون بيانات تجريبية).

## المبادئ الثابتة في هذا المشروع (لا تُنتهك مهما طُلب لاحقًا)
- **لا إخفاء لأيقونة التطبيق** على جهاز الطفل تحت أي ظرف.
- **لا صلاحية "مشرف الجهاز" (Device Admin)** ولا أي تحكم خفي كامل بالجهاز.
- كل الصلاحيات (الموقع، الإشعارات، تقرير الاستخدام) تُطلب عبر حوارات أندرويد القياسية الظاهرة للمستخدم.

## الحالة الحالية
- مشروع Android حقيقي (Gradle + Kotlin + Jetpack Compose).
- تسجيل دخول/إنشاء حساب حقيقي عبر **Firebase Authentication** (بريد إلكتروني + كلمة مرور + تفعيل بريد).
- ربط جهاز الطفل بحساب الوالد عبر **رمز ربط حقيقي من 9 أرقام مخزَّن في Firestore**، صالح 15 دقيقة.
- لوحة تحكم الوالد تقرأ الأجهزة المرتبطة فعليًا من Firestore (مش بيانات وهمية).

## لتشغيل المشروع تحتاج:
1. إنشاء مشروع مجاني على [console.firebase.google.com](https://console.firebase.google.com).
2. تفعيل **Authentication → Email/Password** و **Firestore Database**.
3. تنزيل ملف `google-services.json` من إعدادات المشروع (Android app, applicationId = `com.lekowatch.guardian`) ووضعه في `app/google-services.json`.
4. فتح المجلد بـ Android Studio (يحتاج JDK 17 و Android SDK) والبناء بشكل عادي.

## الخطوات التالية (لم تُبنَ بعد)
- شاشة وقت الشاشة الفعلي (تحتاج `UsageStatsManager` على جهاز الطفل + رفعها لـ Firestore).
- خدمة خلفية على جهاز الطفل ترسل الموقع دوريًا (باستخدام `FusedLocationProviderClient`)، تعمل فقط بإذن ظاهر ومُفعَّل من المستخدم.
- شاشة الاشتراك (ربط مع Google Play Billing).
- شاشة حظر التطبيقات (تحتاج صلاحية Accessibility أو DeviceOwner لأجهزة مُدارة فقط — تحتاج قرارًا منفصلًا بما يحترم نفس المبادئ أعلاه).

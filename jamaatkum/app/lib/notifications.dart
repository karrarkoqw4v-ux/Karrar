import 'package:flutter_local_notifications/flutter_local_notifications.dart';

/// إشعارات محلية: تطلع من داخل الجهاز لما التطبيق يكتشف رسالة جديدة
/// أثناء اشتغاله (عن طريق الفحص الدوري). هذا النوع يعمل بدون سيرفر
/// إشعارات خارجي، لكنه ما يوصل والتطبيق مغلق تماماً — ذاك يحتاج
/// Firebase Cloud Messaging (يحتاج مشروع Firebase وملف google-services.json).
class Notifications {
  static final _plugin = FlutterLocalNotificationsPlugin();
  static bool _ready = false;

  static Future<void> init() async {
    if (_ready) return;
    const android = AndroidInitializationSettings('@mipmap/ic_launcher');
    await _plugin.initialize(const InitializationSettings(android: android));
    // على أندرويد 13 وما فوق لازم تطلب الإذن صراحة
    await _plugin.resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>()?.requestNotificationsPermission();
    _ready = true;
  }

  static Future<void> show({required int id, required String title, required String body}) async {
    if (!_ready) await init();
    const details = NotificationDetails(
      android: AndroidNotificationDetails(
        'jamaatkum_messages',
        'رسائل جماعتكم',
        channelDescription: 'إشعارات الرسائل الجديدة',
        importance: Importance.high,
        priority: Priority.high,
      ),
    );
    await _plugin.show(id, title, body, details);
  }
}

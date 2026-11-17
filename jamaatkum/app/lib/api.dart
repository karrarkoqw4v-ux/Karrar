import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

// عنوان السيرفر: يتغيّر وقت البناء  --dart-define=API_HOST=http://IP:8080
// الافتراضي 10.0.2.2 = localhost من محاكي أندرويد فقط.
const host = String.fromEnvironment('API_HOST', defaultValue: 'http://10.0.2.2:8080');
const apiBase = '$host/api';

class ApiException implements Exception {
  final String? field;
  final String message;
  ApiException(this.message, [this.field]);
}

class Api {
  static Future<Map<String, dynamic>> _handle(http.Response r) async {
    final body = jsonDecode(utf8.decode(r.bodyBytes)) as Map<String, dynamic>;
    if (r.statusCode >= 400) {
      throw ApiException(body['error'] ?? 'صار خطأ', body['field'] == '' ? null : body['field']);
    }
    return body;
  }

  static Future<void> _saveSession(Map<String, dynamic> b) async {
    final p = await SharedPreferences.getInstance();
    await p.setString('token', b['token']);
    await p.setString('user', jsonEncode(b['user']));
  }

  static Future<bool> hasSession() async =>
      (await SharedPreferences.getInstance()).getString('token') != null;

  static Future<Map<String, dynamic>?> currentUser() async {
    final s = (await SharedPreferences.getInstance()).getString('user');
    return s == null ? null : jsonDecode(s) as Map<String, dynamic>;
  }

  static Future<void> logout() async {
    final p = await SharedPreferences.getInstance();
    await p.remove('token');
    await p.remove('user');
  }

  static Future<void> login(String email, String password) async {
    final r = await http.post(Uri.parse('$apiBase/auth/login'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({'email': email, 'password': password}));
    await _saveSession(await _handle(r));
  }

  static Future<void> forgot(String email) async {
    await _handle(await http.post(Uri.parse('$apiBase/auth/forgot'),
        headers: {'Content-Type': 'application/json'}, body: jsonEncode({'email': email})));
  }

  /// null = متاح، غير ذلك سبب الرفض
  static Future<String?> usernameCheck(String u) async {
    final r = await http.get(Uri.parse('$apiBase/auth/username-available').replace(queryParameters: {'username': u}));
    final b = await _handle(r);
    return b['available'] == true ? null : (b['reason'] == 'taken' ? 'اسم المستخدم مستخدم' : 'اسم المستخدم غير صالح');
  }

  static Future<void> register({
    required String name,
    required String username,
    required String email,
    required String password,
    File? avatar,
  }) async {
    final req = http.MultipartRequest('POST', Uri.parse('$apiBase/auth/register'))
      ..fields.addAll({'name': name, 'username': username, 'email': email, 'password': password});
    if (avatar != null) req.files.add(await http.MultipartFile.fromPath('avatar', avatar.path));
    await _saveSession(await _handle(await http.Response.fromStream(await req.send())));
  }

  // ---- المحادثات: كل طلب يحتاج رمز الدخول ----

  static Future<String> _token() async {
    final t = (await SharedPreferences.getInstance()).getString('token');
    if (t == null) throw ApiException('سجّل الدخول أولاً');
    return t;
  }

  static Future<Map<String, String>> _authHeaders() async => {'Authorization': 'Bearer ${await _token()}', 'Content-Type': 'application/json'};

  static dynamic _handleAny(http.Response r) {
    final body = jsonDecode(utf8.decode(r.bodyBytes));
    if (r.statusCode >= 400) {
      final m = body as Map<String, dynamic>;
      throw ApiException(m['error'] ?? 'صار خطأ', (m['field'] as String?)?.isEmpty == true ? null : m['field']);
    }
    return body;
  }

  /// قائمة المحادثات، كل عنصر فيه user, lastMessage, lastAt, unreadCount, id
  static Future<List<dynamic>> conversations() async {
    final r = await http.get(Uri.parse('$apiBase/conversations'), headers: await _authHeaders());
    return _handleAny(r) as List<dynamic>;
  }

  /// يبدأ محادثة مع يوزر، أو يرجّع الموجودة
  static Future<Map<String, dynamic>> startConversation(String username) async {
    final r = await http.post(Uri.parse('$apiBase/conversations/start'), headers: await _authHeaders(), body: jsonEncode({'username': username}));
    return _handleAny(r) as Map<String, dynamic>;
  }

  static Future<List<dynamic>> messages(String convId) async {
    final r = await http.get(Uri.parse('$apiBase/conversations/$convId/messages'), headers: await _authHeaders());
    return _handleAny(r) as List<dynamic>;
  }

  static Future<Map<String, dynamic>> sendMessage(String convId, String text) async {
    final r = await http.post(Uri.parse('$apiBase/conversations/$convId/messages'), headers: await _authHeaders(), body: jsonEncode({'text': text}));
    return _handleAny(r) as Map<String, dynamic>;
  }
}

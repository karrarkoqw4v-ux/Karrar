import 'dart:convert';
import 'dart:io';
import 'package:crypto/crypto.dart';
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'models.dart';

class UserInfo {
  final String name;
  final String email;
  final bool guest;
  UserInfo(this.name, this.email, this.guest);
}

class Store {
  static late SharedPreferences sp;
  static late Directory docs;
  static late Directory projDir;
  static late Directory exportDir;
  static late Directory cacheDir;

  static Future<void> init() async {
    sp = await SharedPreferences.getInstance();
    docs = await getApplicationDocumentsDirectory();
    projDir = Directory('${docs.path}/projects')..createSync(recursive: true);
    exportDir = Directory('${docs.path}/exports')..createSync(recursive: true);
    cacheDir = Directory('${(await getTemporaryDirectory()).path}/montage')..createSync(recursive: true);
  }

  // ---- onboarding / auth
  static bool get onboarded => sp.getBool('onboarded') ?? false;
  static Future<void> setOnboarded() => sp.setBool('onboarded', true);

  static String _hash(String s) => sha256.convert(utf8.encode('montage-murtaah:$s')).toString();
  static Map<String, dynamic> get _users => jsonDecode(sp.getString('users') ?? '{}') as Map<String, dynamic>;

  static UserInfo? get user {
    final e = sp.getString('cur_email');
    if (e == null) return null;
    if (e == 'guest') return UserInfo('زائر', '', true);
    final u = _users[e];
    if (u == null) return null;
    return UserInfo(u['name'] ?? e, e, false);
  }

  static Future<String?> register(String name, String email, String pass) async {
    final e = email.trim().toLowerCase();
    if (!RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$').hasMatch(e)) return 'البريد غير صحيح';
    if (pass.length < 6) return 'كلمة السر 6 أحرف على الأقل';
    final u = _users;
    if (u.containsKey(e)) return 'هذا البريد مسجّل مسبقاً';
    u[e] = {'name': name.trim().isEmpty ? e.split('@').first : name.trim(), 'pass': _hash(pass)};
    await sp.setString('users', jsonEncode(u));
    await sp.setString('cur_email', e);
    return null;
  }

  static Future<String?> login(String email, String pass) async {
    final e = email.trim().toLowerCase();
    final u = _users[e];
    if (u == null) return 'الحساب غير موجود';
    if (u['pass'] != _hash(pass)) return 'كلمة السر غير صحيحة';
    await sp.setString('cur_email', e);
    return null;
  }

  static Future<void> guest() => sp.setString('cur_email', 'guest');
  static Future<void> logout() => sp.remove('cur_email');

  // ---- settings
  static int get quality => sp.getInt('quality') ?? 1080;
  static Future<void> setQuality(int q) => sp.setInt('quality', q);

  // ---- projects
  static Future<void> saveProject(Project p) async {
    p.updatedAt = DateTime.now();
    await File('${projDir.path}/${p.id}.json').writeAsString(p.encode());
  }

  static List<Project> projects() {
    final out = <Project>[];
    for (final f in projDir.listSync().whereType<File>()) {
      if (!f.path.endsWith('.json')) continue;
      try {
        out.add(Project.fromJson(jsonDecode(f.readAsStringSync()) as Map<String, dynamic>));
      } catch (_) {}
    }
    out.sort((a, b) => b.updatedAt.compareTo(a.updatedAt));
    return out;
  }

  static Future<void> deleteProject(String id) async {
    final f = File('${projDir.path}/$id.json');
    if (await f.exists()) await f.delete();
  }

  // ---- exports
  static List<ExportRec> exports() {
    final l = (jsonDecode(sp.getString('exports') ?? '[]') as List).map((e) => ExportRec.fromJson(Map<String, dynamic>.from(e))).toList();
    l.removeWhere((e) => !File(e.path).existsSync());
    l.sort((a, b) => b.at.compareTo(a.at));
    return l;
  }

  static Future<void> addExport(ExportRec r) async {
    final l = exports()..insert(0, r);
    await sp.setString('exports', jsonEncode(l.map((e) => e.toJson()).toList()));
  }

  static Future<void> removeExport(String path) async {
    final l = exports()..removeWhere((e) => e.path == path);
    await sp.setString('exports', jsonEncode(l.map((e) => e.toJson()).toList()));
    final f = File(path);
    if (await f.exists()) await f.delete();
  }

  static Future<void> clearCache() async {
    if (await cacheDir.exists()) {
      await cacheDir.delete(recursive: true);
      await cacheDir.create(recursive: true);
    }
  }
}

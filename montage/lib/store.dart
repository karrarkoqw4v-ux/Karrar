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
  static late Directory trashDir;
  static late Directory exportDir;
  static late Directory cacheDir;

  static Future<void> init({Directory? docsOverride, Directory? cacheOverride}) async {
    sp = await SharedPreferences.getInstance();
    docs = docsOverride ?? await getApplicationDocumentsDirectory();
    projDir = Directory('${docs.path}/projects')..createSync(recursive: true);
    trashDir = Directory('${docs.path}/trash')..createSync(recursive: true);
    exportDir = Directory('${docs.path}/exports')..createSync(recursive: true);
    cacheDir = cacheOverride ?? Directory('${(await getTemporaryDirectory()).path}/montage');
    cacheDir.createSync(recursive: true);
  }

  // ---- onboarding / auth
  static bool get onboarded => sp.getBool('onboarded') ?? false;
  static Future<void> setOnboarded() => sp.setBool('onboarded', true);

  static String _hash(String s) => sha256.convert(utf8.encode('montagi:$s')).toString();
  static Map<String, dynamic> get _users => jsonDecode(sp.getString('users') ?? '{}') as Map<String, dynamic>;
  static final _emailRe = RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$');
  static bool validEmail(String e) => _emailRe.hasMatch(e.trim());

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
    if (!validEmail(e)) return 'البريد غير صحيح';
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

  /// إعادة تعيين محلية (الحسابات على هذا الجهاز فقط ولا يوجد خادم لإرسال رمز)
  static Future<String?> resetPassword(String email, String newPass) async {
    final e = email.trim().toLowerCase();
    final u = _users;
    if (!u.containsKey(e)) return 'لا يوجد حساب بهذا البريد على هذا الجهاز';
    if (newPass.length < 6) return 'كلمة السر 6 أحرف على الأقل';
    u[e]['pass'] = _hash(newPass);
    await sp.setString('users', jsonEncode(u));
    return null;
  }

  static Future<void> guest() => sp.setString('cur_email', 'guest');
  static Future<void> logout() => sp.remove('cur_email');

  // ---- إعدادات التصدير الافتراضية
  static ExportOptions get exportOptions => ExportOptions(res: sp.getInt('x_res') ?? 1080, fps: sp.getInt('x_fps') ?? 30, quality: sp.getInt('x_q') ?? 1);
  static Future<void> saveExportOptions(ExportOptions o) async {
    await sp.setInt('x_res', o.res);
    await sp.setInt('x_fps', o.fps);
    await sp.setInt('x_q', o.quality);
  }

  // ---- المشاريع
  static String nextName() {
    final now = DateTime.now();
    final key = '${now.month.toString().padLeft(2, '0')}${now.day.toString().padLeft(2, '0')}';
    var n = 1;
    final names = {...projects().map((p) => p.name), ...trashed().map((p) => p.name)};
    while (names.contains('$key-${n.toString().padLeft(2, '0')}')) {
      n++;
    }
    return '$key-${n.toString().padLeft(2, '0')}';
  }

  static Future<void> saveProject(Project p) async {
    p.updatedAt = DateTime.now();
    await File('${projDir.path}/${p.id}.json').writeAsString(p.encode());
  }

  static List<Project> _read(Directory d) {
    final out = <Project>[];
    for (final f in d.listSync().whereType<File>()) {
      if (!f.path.endsWith('.json')) continue;
      try {
        out.add(Project.fromJson(jsonDecode(f.readAsStringSync()) as Map<String, dynamic>));
      } catch (_) {}
    }
    out.sort((a, b) => b.updatedAt.compareTo(a.updatedAt));
    return out;
  }

  static List<Project> projects() => _read(projDir);
  static List<Project> trashed() => _read(trashDir);

  static Future<void> trashProject(String id) async {
    final f = File('${projDir.path}/$id.json');
    if (await f.exists()) await f.rename('${trashDir.path}/$id.json');
  }

  static Future<void> restoreProject(String id) async {
    final f = File('${trashDir.path}/$id.json');
    if (await f.exists()) await f.rename('${projDir.path}/$id.json');
  }

  static Future<void> deleteForever(String id) async {
    final f = File('${trashDir.path}/$id.json');
    if (await f.exists()) await f.delete();
  }

  static int projectSize(Project p) {
    var s = 0;
    for (final c in p.clips) {
      try {
        s += File(c.path).lengthSync();
      } catch (_) {}
    }
    return s;
  }

  // ---- المُصدَّرات
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

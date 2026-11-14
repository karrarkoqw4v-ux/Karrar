import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:montage_murtaah/media.dart';
import 'package:montage_murtaah/screens/welcome.dart';
import 'package:montage_murtaah/store.dart';
import 'harness.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('welcome video', (t) async {
    phone(t);
    await t.runAsync(() async {
      await loadFonts();
      await initEnv();
    });
    final files = await t.runAsync(makeSamples) as Map<String, String>;
    MediaBackend.instance = FakeBackend([
      AssetInfo('v1', true, 6), AssetInfo('v2', true, 5), AssetInfo('v3', true, 4),
      AssetInfo('p1', false, 0), AssetInfo('p2', false, 0), AssetInfo('p3', false, 0), AssetInfo('p4', false, 0),
    ], files);
    final cam = Cam();
    final dir = Directory('${outDir.path}/frames')..createSync(recursive: true);
    for (final f in dir.listSync()) {
      f.deleteSync();
    }
    const dt = Duration(milliseconds: 42); // ~24 إطار/ثانية

    Future<void> hold(double secs) async {
      final n = (secs * 1000 / 42).round();
      for (var i = 0; i < n; i++) {
        await t.pump(dt);
        await cam.frame(t, dir, pr: 1.2);
      }
    }

    Future<void> real(int ms) => t.runAsync(() => Future.delayed(Duration(milliseconds: ms)));

    Future<void> type(Finder f, String text) async {
      for (var i = 1; i <= text.length; i++) {
        await t.enterText(f, text.substring(0, i));
        await hold(0.09);
      }
    }

    await t.pumpWidget(appFor(cam, const Splash(hold: Duration(hours: 1))));
    await hold(2.7);
    // ترحيب: 3 صفحات
    await t.pumpWidget(KeyedSubtree(key: UniqueKey(), child: appFor(cam, const Onboarding())));
    await hold(1.8);
    await t.tap(find.text('التالي'));
    await hold(2.4);
    await t.tap(find.text('التالي'));
    await hold(2.4);
    await t.tap(find.text('ابدأ الآن'));
    await hold(1.4);
    // الدخول
    await hold(0.8);
    await t.tap(find.text('تسجيل بالبريد الإلكتروني'));
    await hold(0.9);
    await t.tap(find.textContaining('سجّل الاشتراك', findRichText: true));
    await hold(0.9);
    final fields = find.byType(TextField);
    await t.tap(fields.at(0));
    await type(fields.at(0), 'كرار');
    await t.tap(fields.at(1));
    await type(fields.at(1), 'karrar@example.com');
    await t.tap(fields.at(2));
    await type(fields.at(2), '123456');
    await hold(0.5);
    await t.tap(find.text('إنشاء الحساب'));
    await real(300);
    await hold(1.6);
    // الرئيسية ثم فيديو جديد
    await t.tap(find.text('فيديو جديد'));
    await real(500);
    await hold(1.0);
    await t.tap(find.text('الصور'));
    await real(500);
    await hold(1.0);
    // اختيار صورتين/ثلاث
    final grid = find.byType(GridView);
    expect(grid, findsWidgets);
    final tiles = find.descendant(of: grid.first, matching: find.byType(GestureDetector));
    for (var i = 0; i < 3; i++) {
      await t.tap(tiles.at(i), warnIfMissed: false);
      await hold(0.5);
    }
    await hold(0.6);
    await t.tap(find.text('إضافة'));
    await real(2500);
    await hold(2.0);
    // المحرر: فلتر
    await t.tap(find.text('الفلاتر'));
    await hold(1.4);
    await t.tap(find.text('سيبيا'));
    await hold(1.4);
    await t.tap(find.text('الانتقالات'));
    await hold(1.4);
    await t.tap(find.text('النص'));
    await hold(1.5);
    await t.tap(find.text('حفظ'));
    await hold(1.8);
    await Process.run('bash', ['-c', 'cd ${dir.path} && ffmpeg -y -loglevel error -framerate 24 -i f_%05d.png -c:v libx264 -pix_fmt yuv420p -vf "pad=ceil(iw/2)*2:ceil(ih/2)*2" ${outDir.path}/welcome_and_login.mp4']);
  }, timeout: const Timeout(Duration(minutes: 20)));
}

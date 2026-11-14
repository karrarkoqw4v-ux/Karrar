import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:montage_murtaah/media.dart';
import 'package:montage_murtaah/models.dart';
import 'package:montage_murtaah/store.dart';
import 'package:montage_murtaah/screens/auth.dart';
import 'package:montage_murtaah/screens/custom_page.dart';
import 'package:montage_murtaah/custom.dart';
import 'package:montage_murtaah/screens/editor.dart';
import 'package:montage_murtaah/screens/export_sheet.dart';
import 'package:montage_murtaah/screens/picker.dart';
import 'package:montage_murtaah/screens/shell.dart';
import 'package:montage_murtaah/screens/welcome.dart';
import 'harness.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('screens', (t) async {
    phone(t);
    await t.runAsync(() async {
      await loadFonts();
      await initEnv();
    });
    final files = await t.runAsync(makeSamples) as Map<String, String>;
    final cam = Cam();
    final list = <AssetInfo>[
      AssetInfo('v1', true, 6), AssetInfo('v2', true, 5), AssetInfo('v3', true, 4),
      AssetInfo('p1', false, 0), AssetInfo('p2', false, 0), AssetInfo('p3', false, 0), AssetInfo('p4', false, 0),
    ];
    MediaBackend.instance = FakeBackend(list, files);

    Future<void> go(Widget w, String name, {int ms = 600}) async {
      await t.pumpWidget(KeyedSubtree(key: UniqueKey(), child: appFor(cam, w)));
      await t.pump(const Duration(milliseconds: 100));
      for (var k = 0; k < 4; k++) {
        await t.runAsync(() => Future.delayed(const Duration(milliseconds: 350)));
        await t.pump(Duration(milliseconds: k == 0 ? ms : 60));
      }
      await cam.shot(t, name);
    }

    await go(const Splash(hold: Duration(hours: 1)), '01_splash', ms: 2500);
    await go(const Onboarding(), '02_welcome_1', ms: 1200);
    await go(const AuthLanding(), '03_auth_landing');
    await go(const EmailLogin(), '04_email_login');
    await go(const RegisterPage(), '05_register');
    await go(const ResetPage(), '06_reset');

    // مشروع فعلي من وسائط حقيقية
    final clips = <MClip>[];
    await t.runAsync(() async {
      for (final id in ['v1', 'p1', 'v2', 'p2', 'p4']) {
        final c = await MediaImport.fromPath(files[id]!, knownDuration: id.startsWith('v') ? (id == 'v1' ? 6.0 : 5.0) : null);
        if (c != null) clips.add(c);
      }
    });
    final p = Project(id: 'demo1', name: Store.nextName(), clips: clips);
    p.clips[0].end = 3.2;
    p.clips[2].end = 3.0;
    p.overlays.add(OverlayItem(id: 'o1', text: 'مرحباً بك في مونتاجي', font: 'Cairo', size: 96, anim: 'fade', position: 1, start: 0.3, end: 3));
    p.overlays.add(OverlayItem(id: 'o2', text: 'Summer 2026', font: 'Pacifico', size: 90, anim: 'pop', position: 2, start: 3.4, end: 6.5, color: 0xFFFACC15));
    p.musicName = 'music.m4a';
    p.musicPath = files['music'];
    await t.runAsync(() => Store.saveProject(p));
    final p2 = Project(id: 'demo2', name: Store.nextName(), clips: [clips[1].copy()]);
    await t.runAsync(() => Store.saveProject(p2));

    await go(const Shell(), '07_home');
    await go(const Shell(initial: 1), '10_profile');
    await go(const MediaPickerPage(), '11_picker', ms: 900);

    await go(EditorPage(project: p), '12_editor', ms: 900);
    await go(EditorPage(project: p, initialTool: Tool.filters), '13_editor_filters', ms: 900);
    await go(EditorPage(project: p, initialTool: Tool.transitions), '14_editor_transitions', ms: 900);
    await go(EditorPage(project: p, initialTool: Tool.trim, solo: true), '15_editor_trim_solo', ms: 900);
    await go(EditorPage(project: p, initialTool: Tool.filters, solo: true), '15b_editor_filters_solo', ms: 900);
    await go(const CustomHub(), '21_custom_hub');
    await go(JobPage(job: kJobs.firstWhere((j) => j.id == 'sticker')), '22_job_sticker');
    await go(JobPage(job: kJobs.firstWhere((j) => j.id == 'resize')), '23_job_resize');
    await go(JobPage(job: kJobs.firstWhere((j) => j.id == 'music_freq')), '24_job_music');
    await go(EditorPage(project: p, initialTool: Tool.adjust), '16_editor_adjust', ms: 900);

    // محرر النصوص (قائمة الخطوط)
    await t.pumpWidget(KeyedSubtree(key: UniqueKey(), child: appFor(cam, EditorPage(project: p, initialTool: Tool.text))));
    await t.pump(const Duration(milliseconds: 1200));
    await cam.shot(t, '17_text_editor_arabic_fonts');
    final listFinder = find.text('خطوط إنجليزية');
    if (listFinder.evaluate().isNotEmpty) {
      await t.tap(listFinder);
      await t.pump(const Duration(milliseconds: 500));
      await cam.shot(t, '18_text_editor_english_fonts');
    }

    await go(ExportPage(project: p), '19_export_settings', ms: 600);
    final ph = Project(id: 'photo1', name: 'photo', clips: [clips[1].copy()], photoMode: true)..overlays.add(OverlayItem(id: 'o3', text: 'مونتاجي', font: 'Lalezar', size: 150, position: 1));
    await go(EditorPage(project: ph), '20_photo_editor', ms: 900);
  });
}

import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import 'package:montage_murtaah/export.dart';
import 'package:montage_murtaah/media.dart';
import 'package:montage_murtaah/models.dart';
import 'package:montage_murtaah/store.dart';
import 'harness.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  testWidgets('export with real app code path', (t) async {
    await t.runAsync(() async {
      await loadFonts();
      await initEnv();
      final files = await makeSamples();
      final clips = <MClip>[];
      for (final id in ['v1', 'p1', 'v2', 'p3', 'v3']) {
        final c = await MediaImport.fromPath(files[id]!, knownDuration: id == 'v1' ? 6.0 : (id == 'v2' ? 5.0 : (id == 'v3' ? 4.0 : null)));
        clips.add(c!);
      }
      clips[0].end = 3.5;
      clips[0].filter = 'vivid';
      clips[0].transition = 'zoom';
      clips[1].imageDur = 2.5;
      clips[1].transition = 'slide';
      clips[2].end = 3.0;
      clips[2].speed = 1.5;
      clips[2].filter = 'sepia';
      clips[2].transition = 'circle';
      clips[3].imageDur = 2.0;
      clips[3].filter = 'dramatic';
      clips[3].transition = 'dissolve';
      clips[4].end = 3.0;
      clips[4].adjS = 1.4;
      final p = Project(id: 'x1', name: '1114-01', clips: clips, fitBlur: true);
      p.overlays.addAll([
        OverlayItem(id: 'a', text: 'مرحباً بك في مونتاجي', font: 'Cairo', size: 100, anim: 'type', position: 1, start: 0.4, end: 3.4),
        OverlayItem(id: 'b', text: 'Summer Vibes', font: 'Pacifico', size: 110, anim: 'pop', position: 0, start: 4.0, end: 7.0, color: 0xFFFACC15),
        OverlayItem(id: 'c', text: 'الخط المائل', font: 'Amiri', size: 90, italic: true, anim: 'slide', position: 2, start: 7.5, end: 10.5, box: true),
        OverlayItem(id: 'd', text: '🔥', sticker: true, size: 180, anim: 'pop', position: 1, start: 8.0, end: 11),
      ]);
      p.musicPath = files['music'];
      final total = p.total;
      print('TOTAL=$total');
      final res = await Exporter.run(p, opt: ExportOptions(res: 720, fps: 30, quality: 1), onProgress: (_) {});
      expect(res.error, isNull);
      final out = File(res.path!);
      expect(out.existsSync(), true);
      File(res.path!).copySync('${outDir.path}/export_demo.mp4');
      // صورة (وضع الصورة)
      final ph = Project(id: 'x2', name: 'photo', clips: [clips[1].copy()], photoMode: true);
      ph.overlays.add(OverlayItem(id: 'e', text: 'مونتاجي', font: 'Lalezar', size: 160, position: 1));
      final r2 = await Exporter.run(ph, opt: ExportOptions(res: 1080), onProgress: (_) {});
      print('R2ERR=${r2.error}'); expect(r2.error, isNull);
      File(r2.path!).copySync('${outDir.path}/export_photo.jpg');
      // GIF
      final g = Project(id: 'x3', name: 'gif', clips: [clips[2].copy(), clips[0].copy()]);
      final r3 = await Exporter.run(g, opt: ExportOptions(res: 480, gif: true), onProgress: (_) {});
      expect(r3.error, isNull);
      File(r3.path!).copySync('${outDir.path}/export_demo.gif');
    });
  }, timeout: const Timeout(Duration(minutes: 10)));
}

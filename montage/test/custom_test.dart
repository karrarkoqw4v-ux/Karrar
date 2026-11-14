import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import 'package:montage_murtaah/custom.dart';
import 'harness.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  testWidgets('custom jobs', (t) async {
    await t.runAsync(() async {
      await loadFonts();
      await initEnv();
      final files = await makeSamples();
      final dir = Directory('${outDir.path}/custom')..createSync(recursive: true);
      final sizes = <String>[];
      for (final j in kJobs) {
        for (final isVideo in [true, false]) {
          if (j.id == 'sticker') {
            // كل المنصات
            final plats = j.opts.first.choices.map((e) => e.$1);
            for (final pl in plats) {
              final p = {for (final o in j.opts) o.key: o.def};
              p['plat'] = pl;
              if (pl.contains('anim')) p['rm'] = 'none';
              final r = await JobRunner.run(j, isVideo ? files['v1']! : files['p1']!, isVideo, 6, p, onLog: (_) {});
              expect(r.error, isNull, reason: '${j.id}/$pl/$isVideo');
              for (final f in r.files) {
                final n = '${dir.path}/${j.id}_${pl}_${isVideo ? 'v' : 'p'}.${f.split('.').last}';
                File(f).copySync(n);
                sizes.add('$n ${File(n).lengthSync()}');
              }
            }
            continue;
          }
          final p = {for (final o in j.opts) o.key: o.def};
          if (j.id == 'resize') p['sizes'] = '1080x1080,1080x1920';
          if (j.id.startsWith('q_') && p['res'] == '2160') p['res'] = '1080';
          final r = await JobRunner.run(j, isVideo ? files['v1']! : files['p1']!, isVideo, 6, p, music: j.needsMusic ? files['music'] : null, onLog: (_) {});
          expect(r.error, isNull, reason: '${j.id}/$isVideo');
          var k = 0;
          for (final f in r.files) {
            final n = '${dir.path}/${j.id}_${isVideo ? 'v' : 'p'}${k++}.${f.split('.').last}';
            File(f).copySync(n);
            sizes.add('$n ${File(n).lengthSync()}');
          }
        }
      }
      File('${dir.path}/sizes.txt').writeAsStringSync(sizes.join('\n'));
    });
  }, timeout: const Timeout(Duration(minutes: 15)));
}

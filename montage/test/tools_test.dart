import 'dart:io';
import 'package:flutter_test/flutter_test.dart';
import 'package:montage_murtaah/custom.dart';
import 'package:montage_murtaah/toolrun.dart';
import 'package:montage_murtaah/tools.dart';
import 'package:montage_murtaah/tools_real.dart';
import 'harness.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  testWidgets('all new tools', (t) async {
    await t.runAsync(() async {
      await loadFonts();
      await initEnv();
      final files = await makeSamples();
      final gif = '${sampleDir.path}/s.gif';
      if (!File(gif).existsSync()) {
        await Process.run('ffmpeg', ['-y', '-loglevel', 'error', '-f', 'lavfi', '-i', 'testsrc2=size=320x240:rate=12', '-t', '2', gif]);
      }
      final dir = Directory('${outDir.path}/tools')..createSync(recursive: true);
      final only = Platform.environment['ONLY']?.split(',');
      final shard = int.tryParse(Platform.environment['SHARD'] ?? '');
      final shards = int.tryParse(Platform.environment['SHARDS'] ?? '1') ?? 1;
      final rep = File('${dir.path}/report_${shard ?? 0}.txt')..writeAsStringSync('');
      var idx = -1;
      final fails = <String>[];
      final ok = <String>[];
      Future<void> go(JobDef j, ToolInput inp, String tag) async {
        final sw = Stopwatch()..start();
        final r = await ToolRunner.run(j, inp);
        if (r.files.isEmpty) {
          final e = (r.error ?? '').replaceAll('\n', ' | ');
          fails.add('${j.id}/$tag: $e');
          rep.writeAsStringSync('FAIL ${j.id}/$tag ${sw.elapsed.inSeconds}s: ${e.length > 300 ? e.substring(e.length - 300) : e}\n', mode: FileMode.append);
        } else {
          var k = 0;
          for (final f in r.files.take(3)) {
            final n = '${dir.path}/${j.id}_${tag}_${k++}.${f.split('.').last}';
            File(f).copySync(n);
          }
          ok.add('${j.id}/$tag ${r.files.length}');
          rep.writeAsStringSync('OK ${j.id}/$tag ${sw.elapsed.inSeconds}s\n', mode: FileMode.append);
        }
      }
      for (final j in (Platform.environment['REAL'] == '1' ? kRealTools : kNewTools)) {
        idx++;
        if (only != null && !only.contains(j.id)) continue;
        if (shard != null && idx % shards != shard) continue;
        final p = {for (final o in j.opts) o.key: o.def};
        if (Platform.environment['RES'] != null && p.containsKey('res')) p['res'] = Platform.environment['RES']!;
        String? extra;
        if (j.extra == Extra.audio) extra = files['music'];
        if (j.extra == Extra.image || j.extra == Extra.imageOpt) extra = files['p2'];
        ToolInput mk(String? src, {bool img = false, List<String> multi = const []}) => ToolInput(src: src, srcImage: img, files: multi, extra: extra, p: Map.of(p));
        switch (j.inp) {
          case In.video:
            await go(j, mk(files['v1']), 'v');
            break;
          case In.image:
            await go(j, mk(files['p1'], img: true), 'p');
            break;
          case In.both:
            await go(j, mk(files['v1']), 'v');
            await go(j, mk(files['p1'], img: true), 'p');
            break;
          case In.audio:
            await go(j, mk(files['music']), 'a');
            break;
          case In.av:
            await go(j, mk(files['music']), 'a');
            await go(j, mk(files['v1']), 'v');
            break;
          case In.gif:
            await go(j, mk(gif), 'g');
            break;
          case In.multiAudio:
            await go(j, mk(null, multi: [files['music']!, files['music']!]), 'm');
            break;
          case In.multi:
            final List<String> m = j.id == 'pip' ? [files['v1']!, files['v2']!] : (j.id == 'merge' ? [files['v1']!, files['p1']!, files['v2']!] : (j.id == 'split_screen' ? [files['v1']!, files['v2']!, files['p1']!] : [files['p1']!, files['p2']!, files['p3']!, files['p4']!]));
            await go(j, mk(null, multi: m), 'm');
            break;
        }
      }
      print('OK=${ok.length} FAILS=${fails.length}');
      for (final f in fails) {
        print('FAIL $f');
      }
    });
  }, timeout: const Timeout(Duration(minutes: 40)));
}

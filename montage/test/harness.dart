import 'dart:async';
import 'dart:io';
import 'dart:typed_data';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:montage_murtaah/export.dart';
import 'package:montage_murtaah/media.dart';
import 'package:montage_murtaah/store.dart';
import 'package:montage_murtaah/theme.dart';
import 'package:shared_preferences/shared_preferences.dart';

final outDir = Directory(Platform.environment['SHOTS_OUT'] ?? '/tmp/claude-0/montagi_out')..createSync(recursive: true);
final sampleDir = Directory('${outDir.path}/sample')..createSync(recursive: true);

/// تشغيل ffmpeg النظام بدل ffmpeg_kit (نفس الأوامر تماماً)
class ProcessRunner implements FfRunner {
  final List<String> history = [];
  @override
  Future<bool> run(List<String> args, {void Function(double seconds)? onTime, void Function(String line)? onLog}) async {
    history.add(args.join(' '));
    final r = await Process.run('ffmpeg', ['-nostdin', ...args]);
    if (r.exitCode != 0) onLog?.call(r.stderr.toString());
    return r.exitCode == 0;
  }

  @override
  Future<bool> hasAudio(String path) async {
    final r = await Process.run('ffprobe', ['-v', 'error', '-select_streams', 'a', '-show_entries', 'stream=codec_type', '-of', 'csv=p=0', path]);
    return r.stdout.toString().trim().isNotEmpty;
  }
}

class FakeBackend implements MediaBackend {
  final List<AssetInfo> list;
  final Map<String, String> files;
  FakeBackend(this.list, this.files);
  @override
  Future<bool> requestAccess() async => true;
  @override
  Future<void> openSettings() async {}
  @override
  Future<List<AlbumInfo>> albums(PickKind kind) async => [AlbumInfo('all', 'الأحدث', list.length), AlbumInfo('cam', 'الكاميرا', 12), AlbumInfo('dl', 'التنزيلات', 8)];
  @override
  Future<List<AssetInfo>> assets(AlbumInfo album, PickKind kind, int page, int size) async {
    if (page > 0) return [];
    return list.where((a) => kind == PickKind.both || (kind == PickKind.video ? a.video : !a.video)).toList();
  }

  @override
  Future<Uint8List?> thumb(AssetInfo a) async {
    final f = File(files[a.id]!.replaceAll(RegExp(r'\.(mp4|jpg)$'), '.thumb.png'));
    return f.existsSync() ? f.readAsBytes() : null;
  }

  @override
  Future<String?> filePath(AssetInfo a) async => files[a.id];
}

Future<void> sh(String cmd) async {
  final r = await Process.run('bash', ['-c', cmd]);
  if (r.exitCode != 0) throw Exception('cmd failed: $cmd\n${r.stderr}');
}

/// يولّد وسائط تجريبية للاختبار (فيديوهات بصوت + صور)
Future<Map<String, String>> makeSamples() async {
  final d = sampleDir.path;
  final files = <String, String>{};
  Future<void> vid(String id, String src, int secs, String audio) async {
    final f = '$d/$id.mp4';
    if (!File(f).existsSync()) {
      await sh('ffmpeg -y -loglevel error -f lavfi -i "$src" ${audio.isEmpty ? '' : '-f lavfi -i "$audio"'} -t $secs -c:v libx264 -pix_fmt yuv420p -preset ultrafast ${audio.isEmpty ? '' : '-c:a aac'} $f');
      await sh('ffmpeg -y -loglevel error -ss 1 -i $f -frames:v 1 -vf scale=240:240:force_original_aspect_ratio=increase,crop=240:240 $d/$id.thumb.png');
    }
    files[id] = f;
  }

  Future<void> img(String id, String src) async {
    final f = '$d/$id.jpg';
    if (!File(f).existsSync()) {
      await sh('ffmpeg -y -loglevel error -f lavfi -i "$src" -frames:v 1 -q:v 2 $f');
      await sh('ffmpeg -y -loglevel error -i $f -vf scale=240:240:force_original_aspect_ratio=increase,crop=240:240 $d/$id.thumb.png');
    }
    files[id] = f;
  }

  await vid('v1', 'testsrc2=size=720x1280:rate=30', 6, 'sine=frequency=440');
  await vid('v2', 'mandelbrot=size=720x1280:rate=30', 5, 'sine=frequency=330');
  await vid('v3', 'rgbtestsrc=size=1280x720:rate=30', 4, '');
  await img('p1', 'gradients=size=1080x1350:c0=0x7c3aed:c1=0xec4899:nb_colors=2');
  await img('p2', 'gradients=size=1080x1350:c0=0x06b6d4:c1=0x1d4ed8:nb_colors=2');
  await img('p3', 'gradients=size=1350x1080:c0=0xf59e0b:c1=0xef4444:nb_colors=2');
  await img('p4', 'gradients=size=1080x1350:c0=0x22c55e:c1=0xfacc15:nb_colors=2:x0=0:y0=0:x1=1080:y1=1350');
  await sh('ffmpeg -y -loglevel error -f lavfi -i "sine=frequency=262:duration=12" -c:a aac $d/music.m4a');
  files['music'] = '$d/music.m4a';
  return files;
}

Future<void> loadFonts() async {
  final dir = Directory('assets/fonts');
  final byFamily = <String, List<File>>{};
  for (final f in dir.listSync(recursive: true).whereType<File>().where((f) => f.path.endsWith('.ttf'))) {
    final base = f.uri.pathSegments.last.replaceAll('.ttf', '');
    String fam;
    if (f.path.contains('/ttf/')) {
      fam = base.split('-').first;
    } else {
      fam = 'Tajawal';
    }
    byFamily.putIfAbsent(fam, () => []).add(f);
  }
  for (final e in byFamily.entries) {
    final l = FontLoader(e.key);
    for (final f in e.value) {
      l.addFont(Future.value(ByteData.view(Uint8List.fromList(f.readAsBytesSync()).buffer)));
    }
    await l.load();
  }
  // أيقونات ماتيريال
  final mi = FontLoader('MaterialIcons');
  final p = Platform.environment['FLUTTER_ROOT'] ?? '/opt/fl/flutter';
  final f = File('$p/bin/cache/artifacts/material_fonts/MaterialIcons-Regular.otf');
  if (f.existsSync()) {
    mi.addFont(Future.value(ByteData.view(Uint8List.fromList(f.readAsBytesSync()).buffer)));
    await mi.load();
  }
}

Future<void> initEnv() async {
  SharedPreferences.setMockInitialValues({});
  final root = Directory('${outDir.path}/env')..createSync(recursive: true);
  for (final n in ['docs', 'cache']) {
    final d = Directory('${root.path}/$n');
    if (d.existsSync()) d.deleteSync(recursive: true);
    d.createSync(recursive: true);
  }
  await Store.init(docsOverride: Directory('${root.path}/docs'), cacheOverride: Directory('${root.path}/cache'));
  final runner = ProcessRunner();
  Exporter.runner = runner;
  Exporter.tempDir = Store.cacheDir;
  Exporter.outDir = Store.exportDir;
}

/// يلتقط RepaintBoundary إلى PNG
class Cam {
  final GlobalKey key = GlobalKey();
  int n = 0;
  Future<Uint8List> grab(WidgetTester t, {double pr = 2}) async {
    late Uint8List out;
    await t.runAsync(() async {
      final b = key.currentContext!.findRenderObject() as RenderRepaintBoundary;
      final img = await b.toImage(pixelRatio: pr);
      final bd = await img.toByteData(format: ui.ImageByteFormat.png);
      out = bd!.buffer.asUint8List();
    });
    return out;
  }

  Future<void> shot(WidgetTester t, String name, {double pr = 2}) async {
    final bytes = await grab(t, pr: pr);
    File('${outDir.path}/$name.png').writeAsBytesSync(bytes);
  }

  Future<void> frame(WidgetTester t, Directory dir, {double pr = 1.5}) async {
    final bytes = await grab(t, pr: pr);
    File('${dir.path}/f_${(n++).toString().padLeft(5, '0')}.png').writeAsBytesSync(bytes);
  }
}

Widget appFor(Cam cam, Widget home) {
  return MaterialApp(
    debugShowCheckedModeBanner: false,
    theme: buildTheme(),
    locale: const Locale('ar'),
    supportedLocales: const [Locale('ar')],
    localizationsDelegates: const [GlobalMaterialLocalizations.delegate, GlobalWidgetsLocalizations.delegate, GlobalCupertinoLocalizations.delegate],
    builder: (c, child) => RepaintBoundary(key: cam.key, child: child!),
    home: home,
  );
}

void phone(WidgetTester t) {
  t.view.physicalSize = const Size(1080, 2340);
  t.view.devicePixelRatio = 3.0;
  addTearDown(t.view.reset);
}

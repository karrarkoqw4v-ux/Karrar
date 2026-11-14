import 'dart:io';
import 'dart:math' as math;
import 'custom.dart';
import 'export.dart';
import 'fx.dart';
import 'models.dart';

enum In { video, image, both, audio, av, gif, multi, multiAudio }

enum Extra { none, audio, image, imageOpt }

enum Out { auto, video, image, audio, gif }

class ToolError implements Exception {
  final String msg;
  ToolError(this.msg);
}

class Spec {
  final String? vf; // فلتر بسيط على [0:v]
  final String? fc; // فلتر مركّب (مخارجه [v] و/أو [a])
  final String? af; // فلتر صوت بسيط
  final Out out;
  final String? ext;
  final List<String> pre; // خيارات قبل الإدخال الرئيسي
  final List<String> inputs; // مدخلات إضافية كاملة
  final List<String>? allInputs; // استبدال كل المدخلات (أدوات متعددة)
  final List<String> post; // خيارات إخراج إضافية
  final List<String>? enc; // استبدال إعدادات الترميز
  final double? dur; // مدة المخرج
  final bool anim; // صورة ← فيديو
  final bool noAudio;
  final bool copyV;
  final bool av; // يعدّل الصوت فقط: فيديو→فيديو بنسخ الصورة، صوت→صوت
  final bool shortest;
  final String? pattern; // لمخرجات متعددة مثل frame_%03d.jpg
  final int gifFps, gifW, gifColors;
  const Spec({
    this.vf,
    this.fc,
    this.af,
    this.out = Out.auto,
    this.ext,
    this.pre = const [],
    this.inputs = const [],
    this.allInputs,
    this.post = const [],
    this.enc,
    this.dur,
    this.anim = false,
    this.noAudio = false,
    this.copyV = false,
    this.av = false,
    this.shortest = false,
    this.pattern,
    this.gifFps = 15,
    this.gifW = 480,
    this.gifColors = 128,
  });
}

/// سياق بناء الأداة: المدخلات + الخيارات + مولّدات الطبقات
class Ctx {
  final Map<String, String> p;
  final String src;
  final bool srcImage;
  final MInfo info;
  final List<String> files;
  final List<MInfo> infos;
  final String? extra;
  final String tmp;
  final FfRunner run;
  late final int w, h;
  Ctx(this.p, this.src, this.srcImage, this.info, this.files, this.infos, this.extra, this.tmp, this.run) {
    var iw = info.w, ih = info.h;
    if (iw <= 0 || ih <= 0) {
      iw = 1080;
      ih = 1920;
    }
    final sc = math.min(1.0, math.min(1920.0 / math.max(iw, ih), 1080.0 / math.min(iw, ih)));
    w = math.max(2, (iw * sc / 2).floor() * 2);
    h = math.max(2, (ih * sc / 2).floor() * 2);
  }

  bool get isVideo => info.hasVideo && !srcImage;
  bool get hasA => info.hasAudio;
  double get dur => info.dur > 0 && !srcImage ? info.dur : (p['dur'] != null ? (double.tryParse(p['dur']!) ?? 6) : 6);
  String o(String k) => p[k] ?? '';
  double n(String k, [double d = 0]) => double.tryParse(p[k] ?? '') ?? d;
  int get seq => _seq++;
  int _seq = 0;

  String f(double v) => v.toStringAsFixed(3);

  Future<String> text(String t, {double size = 90, int color = 0xFFFFFFFF, bool box = false, double maxW = 1000, String font = 'Tajawal', bool bold = true}) async {
    final out = '$tmp/txt${seq}.png';
    final item = OverlayItem(id: 't$seq', text: t, size: size, color: color, box: box, font: font, bold: bold);
    await renderOverlayPng(item, t, 1.0, maxW, out);
    return out;
  }

  Future<String> layer(String kind, {int? lw, int? lh, int c1 = 0xFF000000, int c2 = 0xFFFFFFFF}) async {
    final out = '$tmp/lay${seq}_$kind.png';
    await staticLayer(kind, lw ?? w, lh ?? h, out, c1: c1, c2: c2);
    return out;
  }

  Future<String> tile(String kind) async {
    final out = '$tmp/tile${seq}_$kind.png';
    await particleTile(kind, w, h, out);
    return out;
  }
}

String atempoChain(double s) {
  final parts = <String>[];
  var v = s;
  while (v > 2.0) {
    parts.add('atempo=2.0');
    v /= 2.0;
  }
  while (v < 0.5) {
    parts.add('atempo=0.5');
    v /= 0.5;
  }
  parts.add('atempo=${v.toStringAsFixed(4)}');
  return parts.join(',');
}

class ToolInput {
  final String? src;
  final bool srcImage;
  final List<String> files;
  final String? extra;
  final Map<String, String> p;
  ToolInput({this.src, this.srcImage = false, this.files = const [], this.extra, required this.p});
}

final _imgExt = RegExp(r'\.(jpe?g|png|webp|bmp|heic)$', caseSensitive: false);
bool isImagePath(String p) => _imgExt.hasMatch(p);

class ToolRunner {
  static const _vEnc = ['-c:v', 'libx264', '-preset', 'veryfast', '-crf', '20', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '192k', '-movflags', '+faststart'];

  static Future<JobResult> run(JobDef j, ToolInput inp, {void Function(double)? onProgress, void Function(String)? onLog}) async {
    final runner = Exporter.runner;
    final ts = DateTime.now().millisecondsSinceEpoch;
    final tmp = Directory('${Exporter.tempDir.path}/tool_$ts')..createSync(recursive: true);
    final log = StringBuffer();
    void lg(String l) {
      log.writeln(l);
      onLog?.call(l);
    }

    try {
      final src = inp.src ?? (inp.files.isNotEmpty ? inp.files.first : '');
      final info = src.isEmpty ? const MInfo(0, 0, 0, false, false) : await runner.probe(src);
      final infos = <MInfo>[];
      for (final f in inp.files) {
        infos.add(await runner.probe(f));
      }
      final ctx = Ctx(inp.p, src, inp.srcImage, info, inp.files, infos, inp.extra, tmp.path, runner);
      final Spec s;
      try {
        s = await j.build!(ctx);
      } on ToolError catch (e) {
        return JobResult([], e.msg);
      }

      var out = s.out;
      if (out == Out.auto) out = (ctx.isVideo || s.anim) ? Out.video : Out.image;
      var vidSrcAv = false;
      if (s.av) {
        if (ctx.isVideo) {
          out = Out.video;
          vidSrcAv = true;
        } else {
          out = Out.audio;
        }
      }
      final ext = s.ext ?? {Out.video: 'mp4', Out.image: 'jpg', Out.audio: 'm4a', Out.gif: 'gif', Out.auto: 'mp4'}[out]!;
      final dir = Exporter.outDir.path;
      final outFile = s.pattern != null ? '$dir/montagi_${ts}_${s.pattern}' : '$dir/montagi_${j.id}_$ts.$ext';

      final args = <String>['-y', '-hide_banner'];
      final total = s.dur ?? ctx.dur;
      if (s.allInputs != null) {
        args.addAll(s.allInputs!);
      } else {
        if (ctx.srcImage && (out == Out.video || out == Out.gif)) {
          args.addAll(['-loop', '1', '-framerate', '30', '-t', ctx.f(total), '-i', src]);
        } else {
          args.addAll([...s.pre, '-i', src]);
        }
        args.addAll(s.inputs);
      }

      var fc = s.fc;
      var vf = s.vf;
      if (out == Out.gif) {
        final tail = 'fps=${s.gifFps},scale=${s.gifW}:-2:flags=lanczos,split[a][b];[a]palettegen=max_colors=${s.gifColors}[pp];[b][pp]paletteuse';
        if (fc != null) {
          fc = '${fc.replaceFirst(RegExp(r'\[v\]\s*$'), '[g]')};[g]$tail[v]';
        } else {
          fc = '[0:v]${vf != null ? '$vf,' : ''}$tail[v]';
          vf = null;
        }
      }
      final audioSource = out == Out.audio;
      if (fc != null) {
        args.addAll(['-filter_complex', fc]);
        if (out == Out.video || out == Out.gif || out == Out.image) {
          args.addAll(['-map', fc.contains('[v]') ? '[v]' : '0:v']);
          if (out == Out.video || vidSrcAv) {
            if (fc.contains('[a]')) {
              args.addAll(['-map', '[a]']);
            } else if (!s.noAudio && ctx.hasA && s.allInputs == null) {
              args.addAll(['-map', '0:a?']);
            }
          }
        } else if (out == Out.audio) {
          args.addAll(['-map', '[a]']);
        }
      } else if (vf != null && !audioSource) {
        args.addAll(['-vf', vf]);
      }
      if (s.af != null && fc == null && (ctx.hasA || audioSource) && !s.noAudio) args.addAll(['-af', s.af!]);

      switch (out) {
        case Out.video:
        case Out.auto:
          if (s.enc != null) {
            args.addAll(s.enc!);
          } else if (s.copyV || vidSrcAv) {
            args.addAll(['-c:v', 'copy', '-c:a', 'aac', '-b:a', '192k']);
          } else {
            args.addAll(_vEnc);
          }
          if (s.noAudio) args.add('-an');
          if (s.dur != null) args.addAll(['-t', ctx.f(s.dur!)]);
          break;
        case Out.image:
          if (s.pattern == null) args.addAll(['-frames:v', '1']);
          if (ext == 'jpg' && s.pattern == null) args.addAll(['-q:v', '2']);
          break;
        case Out.audio:
          args.add('-vn');
          if (ext == 'mp3') {
            args.addAll(['-c:a', 'libmp3lame', '-q:a', '2']);
          } else if (ext == 'wav') {
            args.addAll(['-c:a', 'pcm_s16le']);
          } else {
            args.addAll(['-c:a', 'aac', '-b:a', '192k']);
          }
          break;
        case Out.gif:
          args.addAll(['-loop', '0']);
          if (s.dur != null) args.addAll(['-t', ctx.f(s.dur!)]);
          break;
      }
      if (s.shortest) args.add('-shortest');
      args.addAll(s.post);
      args.add(outFile);

      final ok = await runner.run(args, onTime: (sec) {
        if (total > 0) onProgress?.call((sec / total).clamp(0.0, 0.98));
      }, onLog: lg);
      if (!ok) {
        final t = log.toString();
        return JobResult([], t.length > 700 ? t.substring(t.length - 700) : t);
      }
      if (s.pattern != null) {
        final prefix = 'montagi_${ts}_';
        final files = Directory(dir).listSync().whereType<File>().where((f) => f.path.split('/').last.startsWith(prefix)).map((f) => f.path).toList()..sort();
        if (files.isEmpty) return JobResult([], 'لم يُنتج أي ملف');
        return JobResult(files);
      }
      if (!File(outFile).existsSync()) return JobResult([], 'لم يُنتج الملف');
      return JobResult([outFile]);
    } catch (e) {
      return JobResult([], '$e');
    } finally {
      try {
        tmp.deleteSync(recursive: true);
      } catch (_) {}
    }
  }
}

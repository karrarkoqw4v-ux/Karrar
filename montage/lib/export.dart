import 'dart:async';
import 'dart:io';
import 'dart:ui' as ui;
import 'package:flutter/painting.dart';
import 'filters.dart';
import 'models.dart';

/// واجهة تنفيذ FFmpeg (على الجهاز: ffmpeg_kit، وفي الاختبار: ffmpeg النظام).
class MInfo {
  final int w, h;
  final double dur;
  final bool hasVideo, hasAudio;
  const MInfo(this.w, this.h, this.dur, this.hasVideo, this.hasAudio);
}

abstract class FfRunner {
  Future<bool> run(List<String> args, {void Function(double seconds)? onTime, void Function(String line)? onLog});
  Future<bool> hasAudio(String path);
  Future<MInfo> probe(String path);
}

class ExportResult {
  final String? path;
  final String? error;
  ExportResult.ok(this.path) : error = null;
  ExportResult.fail(this.error) : path = null;
}

bool _isRtl(String s) => RegExp(r'[֐-ࣿיִ-﷿ﹰ-﻿]').hasMatch(s);
String _f(double v) => v.toStringAsFixed(3);

TextStyle overlayStyle(OverlayItem o, double scale) => TextStyle(
      fontFamily: o.font,
      fontFamilyFallback: const ['Tajawal', 'Noto Color Emoji', 'sans-serif'],
      fontSize: o.size * scale,
      color: Color(o.color),
      fontWeight: o.bold ? FontWeight.w700 : FontWeight.w400,
      fontStyle: o.italic ? FontStyle.italic : FontStyle.normal,
      height: 1.25,
      shadows: o.box || o.sticker ? null : [Shadow(color: const Color(0xCC000000), blurRadius: 8 * scale, offset: Offset(0, 2 * scale))],
    );

/// يرسم نصاً/ملصقاً إلى PNG شفاف (حتى يظهر العربي متصلاً صحيحاً داخل الفيديو).
Future<void> renderOverlayPng(OverlayItem o, String text, double scale, double maxW, String out, {double? fixedW}) async {
  final rtl = _isRtl(o.text);
  final style = overlayStyle(o, scale);
  final dir = rtl ? TextDirection.rtl : TextDirection.ltr;
  final full = TextPainter(text: TextSpan(text: o.text, style: style), textDirection: dir, textAlign: TextAlign.center)..layout(maxWidth: maxW);
  final pad = (o.box ? 30 : 16) * scale;
  final w = (fixedW ?? full.width);
  final cw = (w + pad * 2).ceil();
  final ch = (full.height + pad * 2).ceil();
  final tp = TextPainter(text: TextSpan(text: text, style: style), textDirection: dir, textAlign: fixedW != null ? TextAlign.start : TextAlign.center)..layout(minWidth: w, maxWidth: w);
  final rec = ui.PictureRecorder();
  final canvas = Canvas(rec);
  if (o.box && !o.sticker) {
    canvas.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(0, 0, cw.toDouble(), ch.toDouble()), Radius.circular(22 * scale)), Paint()..color = const Color(0xAA000000));
  }
  tp.paint(canvas, Offset(pad, pad));
  final img = await rec.endRecording().toImage(cw, ch);
  final data = await img.toByteData(format: ui.ImageByteFormat.png);
  await File(out).writeAsBytes(data!.buffer.asUint8List());
}

Future<double> measureOverlayWidth(OverlayItem o, double scale, double maxW) async {
  final tp = TextPainter(text: TextSpan(text: o.text, style: overlayStyle(o, scale)), textDirection: _isRtl(o.text) ? TextDirection.rtl : TextDirection.ltr)..layout(maxWidth: maxW);
  return tp.width;
}

class Exporter {
  static late FfRunner runner;
  static late Directory tempDir;
  static late Directory outDir;

  static String _atempo(double speed) {
    final parts = <String>[];
    var s = speed;
    while (s > 2.0) {
      parts.add('atempo=2.0');
      s /= 2.0;
    }
    while (s < 0.5) {
      parts.add('atempo=0.5');
      s /= 0.5;
    }
    if ((s - 1.0).abs() > 0.001) parts.add('atempo=${_f(s)}');
    return parts.isEmpty ? '' : '${parts.join(',')},';
  }

  /// يبني أوامر FFmpeg ويشغّلها. يرجع مسار الملف النهائي.
  static Future<ExportResult> run(Project p, {required ExportOptions opt, required void Function(double) onProgress, void Function(String)? onLog}) async {
    if (p.clips.isEmpty) return ExportResult.fail('لا توجد مقاطع');
    final dm = dimsFor(p.ratio, opt.res);
    final W = dm.w, H = dm.h;
    final scale = W / (p.ratio == '16:9' ? 1920.0 : 1080.0);
    final fps = opt.fps;
    final ts = DateTime.now().millisecondsSinceEpoch;
    final tmp = Directory('${tempDir.path}/exp_$ts')..createSync(recursive: true);
    final photo = p.photoMode;
    final ext = photo ? 'jpg' : (opt.gif ? 'mp4' : 'mp4');
    final stem = '${outDir.path}/montage_$ts';
    final out = '$stem.$ext';
    final total = p.total;

    final args = <String>['-y', '-hide_banner'];
    final fc = StringBuffer();
    var idx = 0;
    final vLab = <String>[];
    final aLab = <String>[];

    for (var i = 0; i < p.clips.length; i++) {
      final c = p.clips[i];
      final eff = c.effective;
      final vi = idx;
      String aSrc;
      if (c.isVideo) {
        args.addAll(['-ss', _f(c.start), '-t', _f(c.end - c.start), '-i', c.path]);
        idx++;
        final has = await runner.hasAudio(c.path);
        if (has) {
          aSrc = '$vi:a';
        } else {
          args.addAll(['-f', 'lavfi', '-t', _f(eff), '-i', 'anullsrc=channel_layout=stereo:sample_rate=44100']);
          aSrc = '$idx:a';
          idx++;
        }
      } else {
        args.addAll(['-loop', '1', '-framerate', '$fps', '-t', _f(eff), '-i', c.path]);
        idx++;
        args.addAll(['-f', 'lavfi', '-t', _f(eff), '-i', 'anullsrc=channel_layout=stereo:sample_rate=44100']);
        aSrc = '$idx:a';
        idx++;
      }
      final vf = clipVf(c);
      final sp = c.isVideo && (c.speed - 1.0).abs() > 0.001 ? '(PTS-STARTPTS)/${_f(c.speed)}' : 'PTS-STARTPTS';
      final tail = 'setsar=1,fps=$fps,format=yuv420p${vf != null ? ',$vf' : ''},setpts=$sp[v$i];';
      if (p.fitBlur) {
        fc.write('[$vi:v]split[s${i}a][s${i}b];'
            '[s${i}a]scale=$W:$H:force_original_aspect_ratio=increase,crop=$W:$H,boxblur=24:3[bg$i];'
            '[s${i}b]scale=$W:$H:force_original_aspect_ratio=decrease[fg$i];'
            '[bg$i][fg$i]overlay=(W-w)/2:(H-h)/2,$tail');
      } else {
        fc.write('[$vi:v]scale=$W:$H:force_original_aspect_ratio=increase,crop=$W:$H,$tail');
      }
      final tempo = c.isVideo ? _atempo(c.speed) : '';
      fc.write('[$aSrc]aresample=44100,aformat=sample_fmts=fltp:channel_layouts=stereo,${tempo}volume=${_f(c.volume)},asetpts=PTS-STARTPTS[a$i];');
      vLab.add('v$i');
      aLab.add('a$i');
    }

    var curV = vLab[0];
    var curA = aLab[0];
    double acc = p.clips[0].effective;
    for (var i = 1; i < p.clips.length; i++) {
      final td = p.joinDur(i - 1);
      final tr = transitionById(p.clips[i - 1].transition);
      final off = acc - td;
      fc.write('[$curV][${vLab[i]}]xfade=transition=${tr.xfade}:duration=${_f(td)}:offset=${_f(off < 0 ? 0 : off)}[xv$i];');
      fc.write('[$curA][${aLab[i]}]acrossfade=d=${_f(td)}:c1=tri:c2=tri[xa$i];');
      curV = 'xv$i';
      curA = 'xa$i';
      acc = acc + p.clips[i].effective - td;
    }

    var ovN = 0;
    for (final o0 in p.overlays) {
      final o = photo ? (OverlayItem.fromJson(o0.toJson())..anim = 'none'..start = 0..end = total) : o0;
      final s = o.start.clamp(0.0, total).toDouble();
      var e = o.end.clamp(0.0, total).toDouble();
      if (e - s < 0.2) e = (s + 0.2).clamp(0.0, total + 0.2).toDouble();
      final cyFrac = o.position == 0 ? 0.14 : (o.position == 1 ? 0.5 : 0.82);
      final cy = '${_f(cyFrac)}*H';
      final maxW = W * 0.88;
      final slide = o.anim == 'slide' ? '+max(0,(${_f(s + 0.4)}-t))*${_f(350 * scale)}' : '';
      final yExpr = "'$cy-h/2$slide'";

      if (o.anim == 'type' && o.text.runes.length > 1) {
        final chars = o.text.runes.toList();
        final steps = chars.length < 14 ? chars.length : 14;
        final fullW = await measureOverlayWidth(o, scale, maxW);
        final span = (e - s) * 0.6;
        final step = span / steps;
        for (var k = 0; k < steps; k++) {
          final cut = ((k + 1) * chars.length / steps).ceil();
          final part = String.fromCharCodes(chars.take(cut));
          final png = '${tmp.path}/ov${ovN}_$k.png';
          await renderOverlayPng(o, part, scale, maxW, png, fixedW: fullW);
          final ks = s + k * step;
          final ke = k == steps - 1 ? e : s + (k + 1) * step;
          args.addAll(['-loop', '1', '-framerate', '$fps', '-t', _f(ke - ks + 0.1), '-i', png]);
          final ii = idx++;
          fc.write('[$ii:v]format=rgba,setpts=PTS+${_f(ks)}/TB[ot${ovN}_$k];');
          fc.write("[$curV][ot${ovN}_$k]overlay=x='(W-w)/2':y=$yExpr:enable='between(t,${_f(ks)},${_f(ke)})':eof_action=pass[ovv${ovN}_$k];");
          curV = 'ovv${ovN}_$k';
        }
      } else {
        final png = '${tmp.path}/ov$ovN.png';
        await renderOverlayPng(o, o.text, scale, maxW, png);
        args.addAll(['-loop', '1', '-framerate', '$fps', '-t', _f(e - s + 0.1), '-i', png]);
        final ii = idx++;
        final d = e - s;
        var chain = 'format=rgba';
        if (o.anim == 'fade') {
          final fd = d / 2 < 0.35 ? d / 2 : 0.35;
          chain += ',fade=t=in:st=0:d=${_f(fd)}:alpha=1,fade=t=out:st=${_f(d - fd)}:d=${_f(fd)}:alpha=1';
        } else if (o.anim == 'pop') {
          chain += ",scale=w='iw*(0.55+0.45*min(1,t/0.25))':h=-1:eval=frame";
        }
        chain += ',setpts=PTS+${_f(s)}/TB';
        fc.write('[$ii:v]$chain[ot$ovN];');
        fc.write("[$curV][ot$ovN]overlay=x='(W-w)/2':y=$yExpr:enable='between(t,${_f(s)},${_f(e)})':eof_action=pass[ovv$ovN];");
        curV = 'ovv$ovN';
      }
      ovN++;
    }

    var aOut = curA;
    if (!photo && p.musicPath != null && File(p.musicPath!).existsSync()) {
      args.addAll(['-stream_loop', '-1', '-i', p.musicPath!]);
      final mi = idx++;
      fc.write('[$mi:a]aresample=44100,aformat=sample_fmts=fltp:channel_layouts=stereo,volume=${_f(p.musicVolume)},atrim=0:${_f(total + 0.5)},asetpts=PTS-STARTPTS[mus];');
      fc.write('[$curA][mus]amix=inputs=2:duration=first:dropout_transition=0,volume=1.8[amixed];');
      aOut = 'amixed';
    }

    var graph = fc.toString();
    if (graph.endsWith(';')) graph = graph.substring(0, graph.length - 1);
    if (photo) graph = '$graph;[$aOut]anullsink';

    Future<ExportResult> attempt(List<String> codec) async {
      final List<String> full;
      if (photo) {
        full = [...args, '-filter_complex', graph, '-map', '[$curV]', '-frames:v', '1', '-q:v', '2', out];
      } else {
        full = [
          ...args,
          '-filter_complex', graph,
          '-map', '[$curV]', '-map', '[$aOut]',
          ...codec,
          '-pix_fmt', 'yuv420p', '-r', '$fps',
          '-c:a', 'aac', '-b:a', '160k',
          '-movflags', '+faststart',
          '-t', _f(total),
          out,
        ];
      }
      final log = StringBuffer();
      final ok = await runner.run(full, onTime: (sec) {
        if (total > 0) onProgress((sec / total).clamp(0.0, 0.97) * (opt.gif ? 0.8 : 1.0));
      }, onLog: (l) {
        log.writeln(l);
        onLog?.call(l);
      });
      if (ok) return ExportResult.ok(out);
      final tail = log.toString();
      return ExportResult.fail(tail.length > 700 ? tail.substring(tail.length - 700) : tail);
    }

    final crf = opt.quality == 0 ? '28' : (opt.quality == 1 ? '23' : '18');
    var res = await attempt(['-c:v', 'libx264', '-preset', 'veryfast', '-crf', crf]);
    if (res.path == null && (res.error ?? '').contains('libx264')) {
      final mbps = (opt.res >= 1080 ? 10 : 5) * (opt.quality == 0 ? 0.6 : (opt.quality == 1 ? 1 : 1.6));
      res = await attempt(['-c:v', 'h264_mediacodec', '-b:v', '${mbps.round()}M']);
    }
    try {
      tmp.deleteSync(recursive: true);
    } catch (_) {}

    if (res.path != null && opt.gif && !photo) {
      final gifOut = '$stem.gif';
      final ok = await runner.run(['-y', '-hide_banner', '-i', res.path!, '-filter_complex', 'fps=15,scale=480:-1:flags=lanczos,split[a][b];[a]palettegen[p];[b][p]paletteuse', gifOut], onLog: onLog);
      try {
        File(res.path!).deleteSync();
      } catch (_) {}
      res = ok ? ExportResult.ok(gifOut) : ExportResult.fail('تعذر إنشاء GIF');
    }
    if (res.path != null) onProgress(1.0);
    return res;
  }

  /// صورة مصغّرة بالمحرك الحالي
  static Future<String?> thumb(String src, {double at = 0.3, bool isVideo = true}) async {
    final out = '${tempDir.path}/th_${src.hashCode}_${at.toStringAsFixed(1)}.jpg';
    if (File(out).existsSync()) return out;
    final a = <String>['-y'];
    if (isVideo) a.addAll(['-ss', _f(at)]);
    a.addAll(['-i', src, '-frames:v', '1', '-vf', 'scale=320:-2', '-q:v', '5', out]);
    final ok = await runner.run(a);
    return ok && File(out).existsSync() ? out : null;
  }
}

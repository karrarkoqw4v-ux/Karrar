import 'dart:async';
import 'dart:io';
import 'dart:ui' as ui;
import 'package:ffmpeg_kit_flutter_new/ffmpeg_kit.dart';
import 'package:ffmpeg_kit_flutter_new/ffprobe_kit.dart';
import 'package:ffmpeg_kit_flutter_new/return_code.dart';
import 'package:flutter/painting.dart';
import 'filters.dart';
import 'models.dart';
import 'store.dart';

class ExportResult {
  final String? path;
  final String? error;
  ExportResult.ok(this.path) : error = null;
  ExportResult.fail(this.error) : path = null;
}

bool _isRtl(String s) => RegExp(r'[֐-ࣿיִ-﷿ﹰ-﻿]').hasMatch(s);
String _f(double v) => v.toStringAsFixed(3);

/// يرسم نصاً/ملصقاً إلى PNG شفاف (حتى يظهر العربي متصلاً صحيحاً داخل الفيديو).
Future<void> renderOverlayPng(OverlayItem o, String text, double scale, double maxW, String out, {double? fixedW}) async {
  final rtl = _isRtl(text);
  final style = TextStyle(
    fontFamily: o.font == 'Tajawal' ? 'Tajawal' : null,
    fontFamilyFallback: const ['Tajawal', 'Noto Color Emoji', 'sans-serif'],
    fontSize: o.size * scale,
    color: Color(o.color),
    fontWeight: o.bold ? FontWeight.w800 : FontWeight.w500,
    height: 1.25,
    shadows: o.box || o.sticker ? null : [Shadow(color: const Color(0xCC000000), blurRadius: 8 * scale, offset: Offset(0, 2 * scale))],
  );
  final dir = rtl ? TextDirection.rtl : TextDirection.ltr;
  final full = TextPainter(text: TextSpan(text: o.text, style: style), textDirection: dir, textAlign: TextAlign.center)..layout(maxWidth: maxW);
  final pad = (o.box ? 30 : 16) * scale;
  final w = (fixedW ?? full.width);
  final cw = (w + pad * 2).ceil();
  final ch = (full.height + pad * 2).ceil();
  final tp = TextPainter(text: TextSpan(text: text, style: style), textDirection: dir, textAlign: fixedW != null ? TextAlign.start : TextAlign.center)
    ..layout(minWidth: w, maxWidth: w);
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
  final rtl = _isRtl(o.text);
  final tp = TextPainter(
    text: TextSpan(text: o.text, style: TextStyle(fontFamily: o.font == 'Tajawal' ? 'Tajawal' : null, fontSize: o.size * scale, fontWeight: o.bold ? FontWeight.w800 : FontWeight.w500, height: 1.25)),
    textDirection: rtl ? TextDirection.rtl : TextDirection.ltr,
  )..layout(maxWidth: maxW);
  return tp.width;
}

class Exporter {
  static Future<bool> _hasAudio(String path) async {
    try {
      final s = await FFprobeKit.getMediaInformation(path);
      final info = s.getMediaInformation();
      if (info == null) return false;
      for (final st in info.getStreams()) {
        if (st.getType() == 'audio') return true;
      }
    } catch (_) {}
    return false;
  }

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
  static Future<ExportResult> run(Project p, {required int quality, required void Function(double) onProgress, void Function(String)? onLog}) async {
    if (p.clips.isEmpty) return ExportResult.fail('لا توجد مقاطع');
    final H = quality >= 1080 ? 1920 : 1280;
    final W = quality >= 1080 ? 1080 : 720;
    final scale = W / 1080.0;
    const fps = 30;
    final ts = DateTime.now().millisecondsSinceEpoch;
    final tmp = Directory('${Store.cacheDir.path}/exp_$ts')..createSync(recursive: true);
    final out = '${Store.exportDir.path}/montage_$ts.mp4';
    final total = p.total;

    final args = <String>['-y', '-hide_banner'];
    final fc = StringBuffer();
    var idx = 0;
    final vLab = <String>[];
    final aLab = <String>[];

    for (var i = 0; i < p.clips.length; i++) {
      final c = p.clips[i];
      final eff = c.effective;
      int vi = idx;
      String aSrc;
      if (c.isVideo) {
        args.addAll(['-ss', _f(c.start), '-t', _f(c.end - c.start), '-i', c.path]);
        idx++;
        final has = await _hasAudio(c.path);
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
      final vf = filterById(c.filter).vf;
      final sp = c.isVideo && (c.speed - 1.0).abs() > 0.001 ? '(PTS-STARTPTS)/${_f(c.speed)}' : 'PTS-STARTPTS';
      fc.write('[$vi:v]scale=$W:$H:force_original_aspect_ratio=increase,crop=$W:$H,setsar=1,fps=$fps,format=yuv420p${vf != null ? ',$vf' : ''},setpts=$sp[v$i];');
      final tempo = c.isVideo ? _atempo(c.speed) : '';
      fc.write('[$aSrc]aresample=44100,aformat=sample_fmts=fltp:channel_layouts=stereo,${tempo}volume=${_f(c.volume)},asetpts=PTS-STARTPTS[a$i];');
      vLab.add('v$i');
      aLab.add('a$i');
    }

    // ربط المقاطع (انتقالات الفيديو + دمج الصوت بنفس التوقيت)
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

    // النصوص والملصقات (كصور PNG شفافة فوق الفيديو)
    var ovN = 0;
    for (final o in p.overlays) {
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
        String chain = 'format=rgba';
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

    // الموسيقى
    var aOut = curA;
    if (p.musicPath != null && File(p.musicPath!).existsSync()) {
      args.addAll(['-stream_loop', '-1', '-i', p.musicPath!]);
      final mi = idx++;
      fc.write('[$mi:a]aresample=44100,aformat=sample_fmts=fltp:channel_layouts=stereo,volume=${_f(p.musicVolume)},atrim=0:${_f(total + 0.5)},asetpts=PTS-STARTPTS[mus];');
      fc.write('[$curA][mus]amix=inputs=2:duration=first:dropout_transition=0,volume=1.8[amixed];');
      aOut = 'amixed';
    }

    String graph = fc.toString();
    if (graph.endsWith(';')) graph = graph.substring(0, graph.length - 1);
    // تسمية الخرج النهائي
    final vOut = curV;

    Future<ExportResult> attempt(List<String> codec) async {
      final full = <String>[
        ...args,
        '-filter_complex', graph,
        '-map', '[$vOut]', '-map', '[$aOut]',
        ...codec,
        '-pix_fmt', 'yuv420p', '-r', '$fps',
        '-c:a', 'aac', '-b:a', '160k',
        '-movflags', '+faststart',
        '-t', _f(total),
        out,
      ];
      // إذا كان آخر تسمية من الفلتر هي خرجنا نمرّرها كما هي
      final completer = Completer<ExportResult>();
      final log = StringBuffer();
      await FFmpegKit.executeWithArgumentsAsync(full, (session) async {
        final rc = await session.getReturnCode();
        if (ReturnCode.isSuccess(rc)) {
          completer.complete(ExportResult.ok(out));
        } else {
          final tail = log.toString();
          completer.complete(ExportResult.fail(tail.length > 600 ? tail.substring(tail.length - 600) : tail));
        }
      }, (l) {
        log.writeln(l.getMessage());
        onLog?.call(l.getMessage());
      }, (st) {
        if (total > 0) onProgress(((st.getTime() / 1000.0) / total).clamp(0.0, 0.99));
      });
      return completer.future;
    }

    var res = await attempt(['-c:v', 'libx264', '-preset', 'veryfast', '-crf', quality >= 1080 ? '21' : '23']);
    if (res.path == null && (res.error ?? '').contains('libx264')) {
      res = await attempt(['-c:v', 'h264_mediacodec', '-b:v', quality >= 1080 ? '10M' : '5M']);
    }
    try {
      tmp.deleteSync(recursive: true);
    } catch (_) {}
    if (res.path != null) onProgress(1.0);
    return res;
  }

  static Future<String?> thumb(String videoOrImage, {double at = 0.3, bool isVideo = true}) async {
    final out = '${Store.cacheDir.path}/th_${videoOrImage.hashCode}_${at.toStringAsFixed(1)}.jpg';
    if (File(out).existsSync()) return out;
    final a = <String>['-y'];
    if (isVideo) a.addAll(['-ss', _f(at)]);
    a.addAll(['-i', videoOrImage, '-frames:v', '1', '-vf', 'scale=260:-2', '-q:v', '5', out]);
    final s = await FFmpegKit.executeWithArguments(a);
    final rc = await s.getReturnCode();
    return ReturnCode.isSuccess(rc) && File(out).existsSync() ? out : null;
  }
}

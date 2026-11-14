import 'dart:io';
import 'dart:math' as math;
import 'dart:ui' as ui;
import 'package:flutter/painting.dart';

/// رسم طبقات PNG برمجياً (جسيمات، إطارات، أنماط) تُركَّب فوق الفيديو بـ FFmpeg.
Future<void> _save(int w, int h, String out, void Function(Canvas c, Size s) paint) async {
  final rec = ui.PictureRecorder();
  final c = Canvas(rec);
  paint(c, Size(w.toDouble(), h.toDouble()));
  final img = await rec.endRecording().toImage(w, h);
  final d = await img.toByteData(format: ui.ImageByteFormat.png);
  await File(out).writeAsBytes(d!.buffer.asUint8List());
}

void _wrap(Canvas c, double h, double y, void Function(double dy) draw) {
  draw(0);
  if (y < 60) draw(h);
  if (y > h - 60) draw(-h);
}

/// بلاط جسيمات بارتفاع 2h (النمط مكرر مرتين ليدور بسلاسة)
Future<void> particleTile(String kind, int w, int h, String out) async {
  final r = math.Random(kind.hashCode);
  final s = w / 1080.0;
  final n = {'snow': 70, 'rain': 90, 'stars': 40, 'bubbles': 28, 'hearts': 24, 'confetti': 70, 'embers': 55, 'smoke': 14, 'fog': 8}[kind] ?? 40;
  final items = List.generate(n, (_) => (r.nextDouble() * w, r.nextDouble() * h, r.nextDouble(), r.nextDouble()));
  await _save(w, h * 2, out, (c, sz) {
    for (final off in [0.0, h.toDouble()]) {
      c.save();
      c.translate(0, off);
      for (final it in items) {
        final x = it.$1, y = it.$2, a = it.$3, b = it.$4;
        _wrap(c, h.toDouble(), y, (dy) {
          c.save();
          c.translate(x, y + dy);
          _particle(c, kind, s, a, b);
          c.restore();
        });
      }
      c.restore();
    }
  });
}

void _particle(Canvas c, String kind, double s, double a, double b) {
  switch (kind) {
    case 'snow':
      final rad = (3 + a * 9) * s;
      c.drawCircle(Offset.zero, rad, Paint()..color = Color.fromRGBO(255, 255, 255, 0.55 + 0.4 * b)..maskFilter = MaskFilter.blur(BlurStyle.normal, rad * 0.35));
      break;
    case 'rain':
      final len = (40 + a * 60) * s;
      c.drawLine(Offset.zero, Offset(-len * 0.12, len), Paint()..color = Color.fromRGBO(190, 215, 255, 0.35 + 0.35 * b)..strokeWidth = (1.5 + b * 1.5) * s..strokeCap = StrokeCap.round);
      break;
    case 'stars':
      final rad = (8 + a * 22) * s;
      final p = Path();
      for (var i = 0; i < 8; i++) {
        final ang = i * math.pi / 4;
        final rr = i.isEven ? rad : rad * 0.22;
        final pt = Offset(math.cos(ang) * rr, math.sin(ang) * rr);
        i == 0 ? p.moveTo(pt.dx, pt.dy) : p.lineTo(pt.dx, pt.dy);
      }
      p.close();
      c.drawPath(p, Paint()..color = Color.fromRGBO(255, 244, 190, 0.7 + 0.3 * b)..maskFilter = MaskFilter.blur(BlurStyle.normal, 1.5 * s));
      c.drawCircle(Offset.zero, rad * 0.9, Paint()..color = const Color(0x33FFF3B0)..maskFilter = MaskFilter.blur(BlurStyle.normal, rad * 0.6));
      break;
    case 'bubbles':
      final rad = (14 + a * 40) * s;
      c.drawCircle(Offset.zero, rad, Paint()..color = const Color(0x22BFE8FF));
      c.drawCircle(Offset.zero, rad, Paint()..style = PaintingStyle.stroke..strokeWidth = 2.5 * s..color = const Color(0xBBDDF3FF));
      c.drawCircle(Offset(-rad * 0.35, -rad * 0.35), rad * 0.18, Paint()..color = const Color(0xCCFFFFFF));
      break;
    case 'hearts':
      final sz = (18 + a * 34) * s;
      const cols = [Color(0xFFFF4D79), Color(0xFFFF2E63), Color(0xFFFF8FB1), Color(0xFFE91E63)];
      c.save();
      c.rotate((b - .5) * 0.7);
      final p = Path()
        ..moveTo(0, sz * 0.35)
        ..cubicTo(-sz * 1.2, -sz * 0.4, -sz * 0.5, -sz * 1.1, 0, -sz * 0.45)
        ..cubicTo(sz * 0.5, -sz * 1.1, sz * 1.2, -sz * 0.4, 0, sz * 0.35)
        ..close();
      c.drawPath(p, Paint()..color = cols[(b * 4).floor() % 4].withValues(alpha: 0.9));
      c.restore();
      break;
    case 'confetti':
      const cols = [Color(0xFFFFC107), Color(0xFFE53935), Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFFEC407A), Color(0xFF8E24AA)];
      c.rotate(a * 6.28);
      c.drawRRect(RRect.fromRectAndRadius(Rect.fromCenter(center: Offset.zero, width: (14 + b * 10) * s, height: (7 + a * 6) * s), Radius.circular(2 * s)), Paint()..color = cols[(a * 6).floor() % 6]);
      break;
    case 'embers':
      final rad = (3 + a * 7) * s;
      c.drawCircle(Offset.zero, rad * 2.6, Paint()..color = const Color(0x44FF7A18)..maskFilter = MaskFilter.blur(BlurStyle.normal, rad));
      c.drawCircle(Offset.zero, rad, Paint()..color = Color.lerp(const Color(0xFFFF8A00), const Color(0xFFFFE066), b)!);
      break;
    case 'smoke':
      final rad = (90 + a * 130) * s;
      c.drawCircle(Offset.zero, rad, Paint()..color = Color.fromRGBO(210, 210, 215, 0.16 + 0.1 * b)..maskFilter = MaskFilter.blur(BlurStyle.normal, rad * 0.55));
      break;
    case 'fog':
      final rad = (160 + a * 220) * s;
      c.drawOval(Rect.fromCenter(center: Offset.zero, width: rad * 2.4, height: rad), Paint()..color = Color.fromRGBO(235, 240, 245, 0.2 + 0.1 * b)..maskFilter = MaskFilter.blur(BlurStyle.normal, rad * 0.45));
      break;
  }
}

/// طبقات ثابتة (بدون حركة)
Future<void> staticLayer(String kind, int w, int h, String out, {int c1 = 0xFFFF7A18, int c2 = 0xFFFFC107}) async {
  final fw = w.toDouble(), fh = h.toDouble();
  final s = w / 1080.0;
  await _save(w, h, out, (c, sz) {
    switch (kind) {
      case 'fire_glow':
        c.drawRect(Rect.fromLTWH(0, fh * 0.55, fw, fh * 0.45), Paint()..shader = ui.Gradient.linear(Offset(0, fh), Offset(0, fh * 0.55), [const Color(0xCCFF5A00), const Color(0x66FF9A00), const Color(0x00FF9A00)], [0, 0.45, 1]));
        final r = math.Random(7);
        for (var i = 0; i < 9; i++) {
          final x = fw * (i + .5) / 9 + (r.nextDouble() - .5) * 60 * s;
          final hh = fh * (0.12 + r.nextDouble() * 0.16);
          final p = Path()..moveTo(x - 60 * s, fh)..quadraticBezierTo(x - 30 * s, fh - hh * .5, x, fh - hh)..quadraticBezierTo(x + 30 * s, fh - hh * .5, x + 60 * s, fh)..close();
          c.drawPath(p, Paint()..shader = ui.Gradient.linear(Offset(x, fh), Offset(x, fh - hh), [const Color(0xDDFFD54F), const Color(0x00FF7A18)])..maskFilter = MaskFilter.blur(BlurStyle.normal, 10 * s));
        }
        break;
      case 'rays':
        for (var i = 0; i < 6; i++) {
          final x0 = fw * (0.05 + i * 0.17);
          final p = Path()..moveTo(x0, 0)..lineTo(x0 + 70 * s, 0)..lineTo(x0 + 70 * s + fh * 0.55, fh)..lineTo(x0 + fh * 0.55 - 40 * s, fh)..close();
          c.drawPath(p, Paint()..shader = ui.Gradient.linear(Offset(x0, 0), Offset(x0 + fh * .5, fh), [const Color(0x66FFF6CC), const Color(0x00FFF6CC)])..maskFilter = MaskFilter.blur(BlurStyle.normal, 14 * s));
        }
        break;
      case 'bolt':
        final r = math.Random(3);
        final p = Path()..moveTo(fw * 0.55, 0);
        var x = fw * 0.55, y = 0.0;
        while (y < fh * 0.7) {
          y += 60 * s + r.nextDouble() * 80 * s;
          x += (r.nextDouble() - .5) * 140 * s;
          p.lineTo(x, y);
        }
        c.drawPath(p, Paint()..style = PaintingStyle.stroke..strokeWidth = 18 * s..color = const Color(0x88A8C8FF)..maskFilter = MaskFilter.blur(BlurStyle.normal, 14 * s));
        c.drawPath(p, Paint()..style = PaintingStyle.stroke..strokeWidth = 6 * s..color = _white);
        break;
      case 'scan':
        for (var y = 0.0; y < fh; y += 4 * s + 1) {
          c.drawRect(Rect.fromLTWH(0, y, fw, 1.6 * s + .4), Paint()..color = const Color(0x44000000));
        }
        break;
      case 'strip':
        const cols = [Color(0xFFE53935), Color(0xFFFB8C00), Color(0xFFFDD835), Color(0xFF43A047), Color(0xFF1E88E5), Color(0xFF8E24AA)];
        final bh = fh * 0.012;
        for (var i = 0; i < cols.length; i++) {
          c.drawRect(Rect.fromLTWH(0, fh - (i + 1) * bh - fh * .04, fw, bh), Paint()..color = cols[i]);
        }
        break;
      case 'corners':
        final rad = c2.toDouble() * s;
        c.saveLayer(Rect.fromLTWH(0, 0, fw, fh), Paint());
        c.drawRect(Rect.fromLTWH(0, 0, fw, fh), Paint()..color = Color(c1));
        c.drawRRect(RRect.fromRectAndRadius(Rect.fromLTWH(0, 0, fw, fh), Radius.circular(rad)), Paint()..blendMode = BlendMode.clear);
        c.restore();
        break;
      case 'circle_mask':
        c.drawRect(Rect.fromLTWH(0, 0, fw, fh), Paint()..color = const Color(0xFF000000));
        c.drawCircle(Offset(fw / 2, fh / 2), fw / 2, Paint()..color = const Color(0xFFFFFFFF));
        break;
      case 'phone':
        final rr = RRect.fromRectAndRadius(Rect.fromLTWH(0, 0, fw, fh), Radius.circular(70 * s));
        final inner = RRect.fromRectAndRadius(Rect.fromLTWH(22 * s, 22 * s, fw - 44 * s, fh - 44 * s), Radius.circular(52 * s));
        c.drawDRRect(rr, inner, Paint()..color = const Color(0xFF0E0E10));
        c.drawRRect(RRect.fromRectAndRadius(Rect.fromCenter(center: Offset(fw / 2, 22 * s), width: 230 * s, height: 40 * s), Radius.circular(20 * s)), Paint()..color = const Color(0xFF0E0E10));
        break;
      case 'leak':
        c.drawRect(Rect.fromLTWH(0, 0, fw, fh), Paint()..color = const Color(0xFF000000));
        c.drawCircle(Offset(fw * 0.1, fh * 0.15), fw * 0.7, Paint()..shader = ui.Gradient.radial(Offset(fw * 0.1, fh * 0.15), fw * 0.7, [const Color(0xFFFF6A2A), const Color(0x66FF3D6E), const Color(0x00000000)], [0, 0.5, 1]));
        c.drawCircle(Offset(fw * 0.95, fh * 0.85), fw * 0.5, Paint()..shader = ui.Gradient.radial(Offset(fw * 0.95, fh * 0.85), fw * 0.5, [const Color(0xFFFFC84A), const Color(0x00000000)]));
        break;
      case 'dust':
        c.drawRect(Rect.fromLTWH(0, 0, fw, fh), Paint()..color = const Color(0xFF000000));
        final r = math.Random(11);
        for (var i = 0; i < 260; i++) {
          c.drawCircle(Offset(r.nextDouble() * fw, r.nextDouble() * fh), (0.8 + r.nextDouble() * 2.2) * s, Paint()..color = Color.fromRGBO(255, 255, 255, 0.35 + r.nextDouble() * 0.5));
        }
        for (var i = 0; i < 14; i++) {
          final x = r.nextDouble() * fw, y = r.nextDouble() * fh;
          c.drawLine(Offset(x, y), Offset(x + (r.nextDouble() - .5) * 50 * s, y + r.nextDouble() * 70 * s), Paint()..color = const Color(0x88FFFFFF)..strokeWidth = 1.2 * s);
        }
        break;
      case 'stripes':
      case 'dots':
      case 'checks':
      case 'diag':
        c.drawRect(Rect.fromLTWH(0, 0, fw, fh), Paint()..color = Color(c1));
        final p2 = Paint()..color = Color(c2);
        final step = 90 * s;
        if (kind == 'stripes') {
          for (var x = 0.0; x < fw; x += step * 2) {
            c.drawRect(Rect.fromLTWH(x, 0, step, fh), p2);
          }
        } else if (kind == 'dots') {
          for (var y = step / 2; y < fh + step; y += step) {
            for (var x = step / 2 + ((y / step).floor().isOdd ? step / 2 : 0); x < fw + step; x += step) {
              c.drawCircle(Offset(x, y), step * 0.22, p2);
            }
          }
        } else if (kind == 'checks') {
          for (var y = 0.0; y < fh; y += step) {
            for (var x = 0.0; x < fw; x += step) {
              if (((x / step).floor() + (y / step).floor()).isEven) c.drawRect(Rect.fromLTWH(x, y, step, step), p2);
            }
          }
        } else {
          c.save();
          c.rotate(-0.6);
          for (var x = -fh; x < fw + fh; x += step * 1.6) {
            c.drawRect(Rect.fromLTWH(x, -fh, step * 0.7, fh * 3), p2);
          }
          c.restore();
        }
        break;
    }
  });
}

const _white = Color(0xFFFFFFFF);

import 'dart:math' as math;
import 'package:flutter/material.dart';

/// وصف شخصية (رسم برمجي مسطّح) تتحرك داخل القوالب.
class CharSpec {
  final bool female;
  final Color skin;
  final int hair; // 0 قصير 1 طويل 2 مجعد 3 كعكة 4 حجاب 5 قبعة/ذيل
  final Color hairColor;
  final Color top;
  final Color bottom;
  final Color accent;
  final bool glasses;
  final bool beard;
  final int motion; // 0 تلويح 1 رقص 2 قفز 3 مشي 4 إعجاب 5 دوران
  final List<Color> bg;
  final int bgStyle;
  CharSpec copyMotion(int m) => CharSpec(female: female, skin: skin, hair: hair, hairColor: hairColor, top: top, bottom: bottom, accent: accent, glasses: glasses, beard: beard, motion: m, bg: bg, bgStyle: bgStyle);

  const CharSpec({
    required this.female,
    required this.skin,
    required this.hair,
    required this.hairColor,
    required this.top,
    required this.bottom,
    required this.accent,
    required this.glasses,
    required this.beard,
    required this.motion,
    required this.bg,
    required this.bgStyle,
  });
}

const _skins = [Color(0xFFF6D3B3), Color(0xFFEBBE97), Color(0xFFD9A276), Color(0xFFB9825A), Color(0xFF8F5F3F), Color(0xFFF1C9A5)];
const _hairs = [Color(0xFF1B1B1F), Color(0xFF3B2416), Color(0xFF6B3F22), Color(0xFFB07A3C), Color(0xFF2A2A35), Color(0xFF8A2D1E)];

/// يولّد شخصية ثابتة من رقم القالب ولون القالب.
CharSpec charFor(int i, List<Color> bg, {bool? female}) {
  final r = math.Random(i * 7919 + 13);
  final f = female ?? (i % 2 == 1);
  final hairStyle = f ? [1, 3, 4, 2, 5, 1, 4][r.nextInt(7)] : [0, 2, 5, 0, 2][r.nextInt(5)];
  final tops = [const Color(0xFFEF4444), const Color(0xFF3B82F6), const Color(0xFF22C55E), const Color(0xFFF59E0B), const Color(0xFFA855F7), const Color(0xFF111827), const Color(0xFFF8FAFC), const Color(0xFFEC4899), const Color(0xFF14B8A6)];
  final bottoms = [const Color(0xFF1F2937), const Color(0xFF334155), const Color(0xFF0F172A), const Color(0xFF7C2D12), const Color(0xFF374151)];
  return CharSpec(
    female: f,
    skin: _skins[r.nextInt(_skins.length)],
    hair: hairStyle,
    hairColor: _hairs[r.nextInt(_hairs.length)],
    top: tops[r.nextInt(tops.length)],
    bottom: bottoms[r.nextInt(bottoms.length)],
    accent: tops[r.nextInt(tops.length)],
    glasses: r.nextInt(5) == 0,
    beard: !f && r.nextInt(3) == 0,
    motion: (i * 5 + r.nextInt(3)) % 6,
    bg: bg,
    bgStyle: r.nextInt(6),
  );
}

/// خلفية متحركة + شخصية. t بالثواني.
class CharPainter extends CustomPainter {
  final CharSpec s;
  final double t;
  final int seed;
  CharPainter(this.s, this.t, {this.seed = 1});

  @override
  bool shouldRepaint(CharPainter old) => old.t != t || old.s != s;

  @override
  void paint(Canvas canvas, Size size) {
    canvas.save();
    canvas.clipRect(Offset.zero & size);
    _paintBg(canvas, size);
    // الشخصية: صندوق وحدة 100×150
    final h = size.height * 0.84;
    final sc = h / 150.0;
    final ox = (size.width - 100 * sc) / 2;
    final oy = size.height - h - size.height * 0.02;
    // ظل أرضي
    canvas.drawOval(Rect.fromCenter(center: Offset(size.width / 2, size.height * 0.965), width: 60 * sc, height: 8 * sc), Paint()..color = Colors.black.withValues(alpha: .18));
    canvas.save();
    canvas.translate(ox, oy);
    canvas.scale(sc);
    _figure(canvas);
    canvas.restore();
    canvas.restore();
  }

  // ---------- الخلفية ----------
  void _paintBg(Canvas c, Size size) {
    final rect = Offset.zero & size;
    c.drawRect(rect, Paint()..shader = LinearGradient(colors: s.bg, begin: Alignment.topRight, end: Alignment.bottomLeft).createShader(rect));
    final r = math.Random(seed * 31 + s.bgStyle);
    final w = size.width, h = size.height;
    switch (s.bgStyle) {
      case 0: // فقاعات صاعدة
        for (var i = 0; i < 9; i++) {
          final x = r.nextDouble() * w;
          final sp = .05 + r.nextDouble() * .09;
          final y = h - (((t * sp + r.nextDouble()) % 1.0) * (h + 40)) + 20;
          final rad = (6 + r.nextDouble() * 16) * (w / 180);
          c.drawCircle(Offset(x, y), rad, Paint()..color = Colors.white.withValues(alpha: .13 + r.nextDouble() * .12));
        }
        break;
      case 1: // خطوط قطرية تتحرك
        final p = Paint()..color = Colors.white.withValues(alpha: .1)..strokeWidth = w * .07;
        for (var i = -4; i < 10; i++) {
          final x = i * w * .2 + (t * w * .06) % (w * .2);
          c.drawLine(Offset(x, 0), Offset(x - w * .4, h), p);
        }
        break;
      case 2: // نجوم تلمع
        for (var i = 0; i < 14; i++) {
          final x = r.nextDouble() * w, y = r.nextDouble() * h * .8;
          final tw = (math.sin(t * 2.4 + i) * .5 + .5);
          _star(c, Offset(x, y), (3 + r.nextDouble() * 5) * (w / 180) * (.5 + tw), Colors.white.withValues(alpha: .25 + tw * .5));
        }
        break;
      case 3: // أمواج
        for (var k = 0; k < 3; k++) {
          final path = Path()..moveTo(0, h);
          for (double x = 0; x <= w; x += 6) {
            path.lineTo(x, h * (.62 + k * .09) + math.sin((x / w) * 6.28 * 1.3 + t * (1.2 + k * .4) + k) * h * .03);
          }
          path.lineTo(w, h);
          path.close();
          c.drawPath(path, Paint()..color = Colors.white.withValues(alpha: .1 + k * .05));
        }
        break;
      case 4: // قصاصات ملونة تتساقط
        const cols = [Color(0xFFFACC15), Color(0xFFEC4899), Color(0xFF22D3EE), Color(0xFFFFFFFF), Color(0xFF34D399)];
        for (var i = 0; i < 20; i++) {
          final x = r.nextDouble() * w;
          final sp = .08 + r.nextDouble() * .12;
          final y = ((t * sp + r.nextDouble()) % 1.0) * (h + 20) - 10;
          c.save();
          c.translate(x + math.sin(t + i) * 6, y);
          c.rotate(t * 2 + i);
          c.drawRect(Rect.fromCenter(center: Offset.zero, width: 5 * (w / 180), height: 9 * (w / 180)), Paint()..color = cols[i % cols.length].withValues(alpha: .85));
          c.restore();
        }
        break;
      default: // حلقات تتوسع
        for (var k = 0; k < 4; k++) {
          final p = ((t * .18 + k / 4) % 1.0);
          c.drawCircle(Offset(w / 2, h * .55), p * w * .8, Paint()..style = PaintingStyle.stroke..strokeWidth = 3..color = Colors.white.withValues(alpha: (1 - p) * .35));
        }
    }
  }

  void _star(Canvas c, Offset o, double r, Color col) {
    final p = Path();
    for (var i = 0; i < 8; i++) {
      final a = i * math.pi / 4;
      final rr = i.isEven ? r : r * .35;
      final pt = Offset(o.dx + math.cos(a) * rr, o.dy + math.sin(a) * rr);
      i == 0 ? p.moveTo(pt.dx, pt.dy) : p.lineTo(pt.dx, pt.dy);
    }
    p.close();
    c.drawPath(p, Paint()..color = col);
  }

  // ---------- الشخصية ----------
  void _limb(Canvas c, Offset a, Offset b, double w, Color col) {
    c.drawLine(a, b, Paint()..color = col..strokeWidth = w..strokeCap = StrokeCap.round);
  }

  Offset _polar(Offset o, double len, double deg) {
    final r = deg * math.pi / 180;
    return Offset(o.dx + math.sin(r) * len, o.dy + math.cos(r) * len);
  }

  void _figure(Canvas c) {
    final ph = t * 2 * math.pi / 3.0; // دورة 3 ثواني
    double bob = 0, lean = 0, headTilt = 0;
    double aL = 18, aR = 18; // زاوية الذراع عن الأسفل للخارج
    double legSpread = 7, legSwing = 0;
    bool thumbs = false;
    switch (s.motion) {
      case 0:
        aR = 150 + math.sin(ph * 4) * 22;
        aL = 14 + math.sin(ph * 2) * 4;
        bob = math.sin(ph * 2) * 1.2;
        headTilt = math.sin(ph * 2) * 3;
        break;
      case 1:
        bob = math.sin(ph * 4).abs() * -4;
        aL = 70 + math.sin(ph * 4) * 55;
        aR = 70 - math.sin(ph * 4) * 55;
        lean = math.sin(ph * 2) * 4;
        headTilt = math.sin(ph * 2) * 5;
        legSwing = math.sin(ph * 4) * 5;
        break;
      case 2:
        bob = -(math.sin(ph * 2).abs()) * 16;
        aL = 150;
        aR = 150;
        legSpread = 10;
        break;
      case 3:
        bob = math.sin(ph * 4).abs() * -2;
        aL = 22 + math.sin(ph * 4) * 24;
        aR = 22 - math.sin(ph * 4) * 24;
        legSwing = math.sin(ph * 4) * 9;
        break;
      case 4:
        thumbs = true;
        bob = math.sin(ph * 2) * 1.5;
        aL = 16;
        headTilt = math.sin(ph * 2) * 4;
        break;
      default:
        lean = math.sin(ph * 2) * 7;
        aL = 78 + math.sin(ph * 2) * 12;
        aR = 78 - math.sin(ph * 2) * 12;
        headTilt = math.sin(ph * 2) * 8;
    }
    c.save();
    c.translate(50, 0);
    c.rotate(lean * math.pi / 180);
    c.translate(-50, bob);

    // الساقان
    final hip = const Offset(50, 96);
    final lf = Offset(41 - legSpread * .4 + legSwing * .3, 140);
    final rf = Offset(59 + legSpread * .4 - legSwing * .3, 140);
    _limb(c, Offset(44, hip.dy), lf, 11, s.bottom);
    _limb(c, Offset(56, hip.dy), rf, 11, s.bottom);
    c.drawOval(Rect.fromCenter(center: Offset(lf.dx - 1, 142), width: 16, height: 7), Paint()..color = const Color(0xFF222222));
    c.drawOval(Rect.fromCenter(center: Offset(rf.dx + 1, 142), width: 16, height: 7), Paint()..color = const Color(0xFF222222));

    // الشعر الخلفي
    _hairBack(c, headTilt);

    // الذراع اليسرى (خلف الجذع)
    final shL = const Offset(33, 60), shR = const Offset(67, 60);
    final elL = _polar(shL, 17, -aL * .6 - 8);
    final handL = _polar(elL, 16, -aL);
    _limb(c, shL, elL, 9, s.top);
    _limb(c, elL, handL, 8, s.skin);
    c.drawCircle(handL, 4.4, Paint()..color = s.skin);

    // الجذع
    final torso = RRect.fromRectAndRadius(const Rect.fromLTRB(32, 52, 68, 100), const Radius.circular(12));
    c.drawRRect(torso, Paint()..color = s.top);
    if (s.female && s.hair != 4) {
      // فستان بسيط: توسيع أسفل الجذع
      final dress = Path()..moveTo(32, 86)..lineTo(26, 108)..lineTo(74, 108)..lineTo(68, 86)..close();
      c.drawPath(dress, Paint()..color = s.top);
    }
    // طوق/ياقة
    c.drawArc(Rect.fromCenter(center: const Offset(50, 54), width: 16, height: 10), 0, math.pi, false, Paint()..color = Colors.white.withValues(alpha: .35)..style = PaintingStyle.stroke..strokeWidth = 2.4);

    // الذراع اليمنى (أمام)
    Offset elR, handR;
    if (thumbs) {
      elR = Offset(80, 74);
      handR = Offset(80, 56 + math.sin(ph * 4) * 1.5);
      _limb(c, shR, elR, 9, s.top);
      _limb(c, elR, handR, 8, s.skin);
      c.drawCircle(handR, 5, Paint()..color = s.skin);
      // إبهام
      _limb(c, handR + const Offset(0, -2), handR + const Offset(0, -9), 3.4, s.skin);
    } else {
      elR = _polar(shR, 17, aR * .6 + 8);
      handR = _polar(elR, 16, aR);
      _limb(c, shR, elR, 9, s.top);
      _limb(c, elR, handR, 8, s.skin);
      c.drawCircle(handR, 4.4, Paint()..color = s.skin);
    }

    // الرقبة
    c.drawRect(const Rect.fromLTRB(45, 46, 55, 56), Paint()..color = s.skin.withValues(alpha: 1));
    c.drawRect(const Rect.fromLTRB(45, 52, 55, 56), Paint()..color = Colors.black.withValues(alpha: .08));

    // الرأس
    c.save();
    c.translate(50, 44);
    c.rotate(headTilt * math.pi / 180);
    c.translate(-50, -44);
    _head(c, ph);
    c.restore();
    c.restore();
  }

  void _hairBack(Canvas c, double tilt) {
    c.save();
    c.translate(50, 44);
    c.rotate(tilt * math.pi / 180);
    c.translate(-50, -44);
    final p = Paint()..color = s.hairColor;
    switch (s.hair) {
      case 1: // طويل
        c.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTRB(31, 18, 69, 80), const Radius.circular(18)), p);
        break;
      case 4: // حجاب
        final path = Path()
          ..moveTo(50, 12)
          ..cubicTo(26, 12, 22, 40, 24, 62)
          ..lineTo(34, 78)
          ..lineTo(66, 78)
          ..lineTo(76, 62)
          ..cubicTo(78, 40, 74, 12, 50, 12)
          ..close();
        c.drawPath(path, Paint()..color = s.accent);
        break;
      case 3: // كعكة
        c.drawCircle(Offset(50, 11 + math.sin(t * 2) * .6), 8, p);
        break;
      case 5:
        if (s.female) {
          // ذيل حصان يتأرجح
          final sw = math.sin(t * 2.6) * 6;
          final tail = Path()..moveTo(66, 24)..quadraticBezierTo(82 + sw, 34, 76 + sw, 62)..quadraticBezierTo(70 + sw, 40, 64, 32)..close();
          c.drawPath(tail, p);
        }
        break;
    }
    c.restore();
  }

  void _head(Canvas c, double ph) {
    final skin = Paint()..color = s.skin;
    final hair = Paint()..color = s.hairColor;
    // أذنان
    c.drawCircle(const Offset(33.5, 36), 3.6, skin);
    c.drawCircle(const Offset(66.5, 36), 3.6, skin);
    // الوجه
    final face = Rect.fromCenter(center: const Offset(50, 34), width: 33, height: 38);
    if (s.hair == 4) {
      // إطار الحجاب أمام الوجه
      c.drawOval(Rect.fromCenter(center: const Offset(50, 36), width: 41, height: 44), Paint()..color = s.accent);
      c.drawOval(Rect.fromCenter(center: const Offset(50, 36), width: 31, height: 36), skin);
    } else {
      c.drawOval(face, skin);
    }
    // لحية
    if (s.beard) {
      final b = Path()..moveTo(35, 36)..quadraticBezierTo(50, 66, 65, 36)..quadraticBezierTo(50, 44, 35, 36)..close();
      c.drawPath(b, hair);
    }
    // شعر أمامي
    switch (s.hair) {
      case 0:
        c.drawPath(Path()..moveTo(33, 30)..cubicTo(32, 12, 68, 12, 67, 30)..cubicTo(60, 22, 46, 20, 33, 30)..close(), hair);
        break;
      case 1:
        c.drawPath(Path()..moveTo(32, 34)..cubicTo(30, 10, 70, 10, 68, 34)..cubicTo(62, 20, 44, 20, 32, 34)..close(), hair);
        break;
      case 2:
        for (var i = 0; i < 7; i++) {
          final a = math.pi + i * math.pi / 6;
          c.drawCircle(Offset(50 + math.cos(a) * 16, 28 + math.sin(a) * 15), 6.5, hair);
        }
        break;
      case 3:
        c.drawPath(Path()..moveTo(33, 30)..cubicTo(33, 12, 67, 12, 67, 30)..cubicTo(60, 21, 44, 21, 33, 30)..close(), hair);
        break;
      case 4:
        break;
      default:
        if (s.female) {
          c.drawPath(Path()..moveTo(32, 32)..cubicTo(30, 10, 70, 10, 68, 32)..cubicTo(60, 22, 44, 22, 32, 32)..close(), hair);
        } else {
          // قبعة
          c.drawPath(Path()..moveTo(32, 26)..cubicTo(32, 6, 68, 6, 68, 26)..close(), Paint()..color = s.accent);
          c.drawRRect(RRect.fromRectAndRadius(const Rect.fromLTRB(60, 22, 82, 28), const Radius.circular(3)), Paint()..color = s.accent);
        }
    }
    // العينان
    final blinkPhase = (t % 3.2);
    final blink = blinkPhase > 3.05 ? 0.12 : 1.0;
    for (final dx in [-7.0, 7.0]) {
      final e = Offset(50 + dx, 36);
      c.save();
      c.translate(e.dx, e.dy);
      c.scale(1, blink);
      c.drawOval(Rect.fromCenter(center: Offset.zero, width: 6.4, height: 7.6), Paint()..color = const Color(0xFF1A1A1A));
      c.drawCircle(const Offset(-1, -1.4), 1.2, Paint()..color = Colors.white);
      c.restore();
      c.drawLine(Offset(e.dx - 4.5, e.dy - 7), Offset(e.dx + 4.5, e.dy - 7 - (dx < 0 ? 0.6 : -0.6)), Paint()..color = s.hairColor.withValues(alpha: .9)..strokeWidth = 1.7..strokeCap = StrokeCap.round);
      if (s.female) {
        c.drawLine(Offset(e.dx + (dx < 0 ? -3.6 : 3.6), e.dy - 3.2), Offset(e.dx + (dx < 0 ? -5.6 : 5.6), e.dy - 5), Paint()..color = const Color(0xFF1A1A1A)..strokeWidth = 1.1..strokeCap = StrokeCap.round);
      }
    }
    if (s.female) {
      c.drawCircle(const Offset(39.5, 43), 3.4, Paint()..color = const Color(0xFFFF7A90).withValues(alpha: .35));
      c.drawCircle(const Offset(60.5, 43), 3.4, Paint()..color = const Color(0xFFFF7A90).withValues(alpha: .35));
    }
    // الأنف
    c.drawArc(Rect.fromCenter(center: const Offset(50, 41), width: 5, height: 4), 0.2, 2.7, false, Paint()..color = Colors.black.withValues(alpha: .25)..style = PaintingStyle.stroke..strokeWidth = 1.1);
    // الفم (يبتسم ويتكلم)
    final open = (math.sin(ph * 5) * .5 + .5) * (s.motion == 4 ? 0.4 : 1.0);
    final mouth = Path()..moveTo(46, 48.2)..quadraticBezierTo(50, 50.6 + open * 2.6, 54, 48.2)..quadraticBezierTo(50, 49.0 + open * 0.8, 46, 48.2)..close();
    c.drawPath(mouth, Paint()..color = const Color(0xFFB4394C));
    c.drawPath(mouth, Paint()..color = Colors.black.withValues(alpha: .15)..style = PaintingStyle.stroke..strokeWidth = .6);
    // نظارة
    if (s.glasses) {
      final gp = Paint()..color = const Color(0xFF222222)..style = PaintingStyle.stroke..strokeWidth = 1.6;
      c.drawCircle(const Offset(43, 36), 6.2, gp);
      c.drawCircle(const Offset(57, 36), 6.2, gp);
      c.drawLine(const Offset(49.2, 36), const Offset(50.8, 36), gp);
    }
  }
}

/// بطاقة تعرض شخصية متحركة. يمرّر الزمن من الأعلى لتتزامن كل البطاقات.
class CharView extends StatelessWidget {
  final CharSpec spec;
  final Animation<double> time; // ثواني
  final int seed;
  const CharView({super.key, required this.spec, required this.time, this.seed = 1});
  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: time,
      builder: (_, __) => CustomPaint(painter: CharPainter(spec, time.value, seed: seed), size: Size.infinite),
    );
  }
}

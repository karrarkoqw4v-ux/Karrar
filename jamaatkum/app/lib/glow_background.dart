import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'theme.dart';

/// خلفية سودا فيها بقع ضوء (سماوي/أحمر) تتحرك ببطء،
/// ولما [active] = true (المستخدم يكتب) تكبر وتتحرك أكثر وتصير أنور.
class GlowBackground extends StatefulWidget {
  final bool active;
  final Widget child;
  const GlowBackground({super.key, required this.active, required this.child});
  @override
  State<GlowBackground> createState() => _GlowBackgroundState();
}

class _GlowBackgroundState extends State<GlowBackground> with TickerProviderStateMixin {
  late final AnimationController _loop = AnimationController(vsync: this, duration: const Duration(seconds: 14))..repeat();
  late final AnimationController _boost = AnimationController(vsync: this, duration: const Duration(milliseconds: 700));

  @override
  void didUpdateWidget(GlowBackground old) {
    super.didUpdateWidget(old);
    if (widget.active != old.active) widget.active ? _boost.forward() : _boost.reverse();
  }

  @override
  void dispose() {
    _loop.dispose();
    _boost.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Stack(fit: StackFit.expand, children: [
        const ColoredBox(color: Colors.black),
        AnimatedBuilder(
          animation: Listenable.merge([_loop, _boost]),
          builder: (_, __) => CustomPaint(painter: _GlowPainter(_loop.value, Curves.easeOut.transform(_boost.value))),
        ),
        widget.child,
      ]);
}

class _GlowPainter extends CustomPainter {
  final double t, boost;
  _GlowPainter(this.t, this.boost);

  void _blob(Canvas c, Size s, Offset center, double r, Color col, double opacity) {
    final rect = Rect.fromCircle(center: center, radius: r);
    c.drawCircle(center, r, Paint()..shader = RadialGradient(colors: [col.withOpacity(opacity), col.withOpacity(0)]).createShader(rect));
  }

  @override
  void paint(Canvas canvas, Size s) {
    final a = t * 2 * math.pi;
    final amp = 0.10 + 0.14 * boost; // مدى الحركة
    final op = 0.28 + 0.30 * boost; // الإضاءة
    final r = s.shortestSide * (0.75 + 0.25 * boost);
    _blob(canvas, s, Offset(s.width * (0.75 + amp * math.cos(a)), s.height * (0.18 + amp * math.sin(a))), r, brand, op);
    _blob(canvas, s, Offset(s.width * (0.22 + amp * math.sin(a * 1.3)), s.height * (0.42 + amp * math.cos(a * 1.3))), r, cyan, op * .8);
    _blob(canvas, s, Offset(s.width * (0.5 + amp * math.cos(a * 0.7 + 2)), s.height * (0.7 + amp * math.sin(a * 0.7 + 2))), r * .8, const Color(0xFFB5179E), op * .45);
  }

  @override
  bool shouldRepaint(_GlowPainter o) => o.t != t || o.boost != boost;
}

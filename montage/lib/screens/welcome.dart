import 'dart:async';
import 'dart:math' as math;
import 'package:flutter/material.dart';
import '../characters.dart';
import '../store.dart';
import '../theme.dart';
import 'auth.dart';
import 'shell.dart';

/// شاشة البداية: شعار يسقط بنطّة + اسم التطبيق يظهر حرفاً حرفاً.
class Splash extends StatefulWidget {
  final VoidCallback? onDone;
  final Duration hold;
  const Splash({super.key, this.onDone, this.hold = const Duration(milliseconds: 2600)});
  @override
  State<Splash> createState() => _SplashState();
}

class _SplashState extends State<Splash> with SingleTickerProviderStateMixin {
  late final AnimationController c = AnimationController(vsync: this, duration: const Duration(milliseconds: 2400))..forward();
  Timer? _t;

  @override
  void initState() {
    super.initState();
    _t = Timer(widget.hold, () {
      if (!mounted) return;
      if (widget.onDone != null) {
        widget.onDone!();
      } else {
        Navigator.of(context).pushReplacement(PageRouteBuilder(pageBuilder: (_, __, ___) => !Store.onboarded ? const Onboarding() : (Store.user == null ? const AuthLanding() : const Shell()), transitionsBuilder: (_, a, __, child) => FadeTransition(opacity: a, child: child), transitionDuration: const Duration(milliseconds: 500)));
      }
    });
  }

  @override
  void dispose() {
    _t?.cancel();
    c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    const word = 'مونتاجي';
    return Scaffold(
      backgroundColor: const Color(0xFF0B0B0F),
      body: AnimatedBuilder(
        animation: c,
        builder: (_, __) {
          final t = c.value;
          final drop = Curves.bounceOut.transform((t / 0.45).clamp(0.0, 1.0));
          final glow = (math.sin(t * math.pi * 2) * .5 + .5);
          final reveal = ((t - 0.45) / 0.35).clamp(0.0, 1.0);
          final sub = ((t - 0.75) / 0.25).clamp(0.0, 1.0);
          return Stack(children: [
            // نبضات خلفية
            Positioned.fill(child: CustomPaint(painter: _Rings(t))),
            Center(
              child: Column(mainAxisSize: MainAxisSize.min, children: [
                Transform.translate(
                  offset: Offset(0, -260 * (1 - drop)),
                  child: Transform.scale(
                    scale: .8 + .2 * drop,
                    child: Container(
                      decoration: BoxDecoration(borderRadius: BorderRadius.circular(40), boxShadow: [BoxShadow(color: K.accent.withValues(alpha: .25 + .25 * glow), blurRadius: 50, spreadRadius: 2)]),
                      child: const AppLogo(size: 132),
                    ),
                  ),
                ),
                const SizedBox(height: 26),
                // الاسم: يظهر حرفاً حرفاً
                Row(mainAxisSize: MainAxisSize.min, textDirection: TextDirection.rtl, children: [
                  for (var i = 0; i < word.length; i++)
                    Opacity(
                      opacity: ((reveal * word.length) - i).clamp(0.0, 1.0),
                      child: Transform.translate(offset: Offset(0, 14 * (1 - ((reveal * word.length) - i).clamp(0.0, 1.0))), child: Text(word[i], style: const TextStyle(fontFamily: 'Tajawal', fontSize: 44, fontWeight: FontWeight.w800, color: Colors.white, height: 1.2))),
                    ),
                ]),
                const SizedBox(height: 8),
                Opacity(opacity: sub, child: Transform.translate(offset: Offset(0, 8 * (1 - sub)), child: Text('مونتاج احترافي… مجاناً', style: tx(15, color: Colors.white70)))),
              ]),
            ),
          ]);
        },
      ),
    );
  }
}

class _Rings extends CustomPainter {
  final double t;
  _Rings(this.t);
  @override
  void paint(Canvas canvas, Size size) {
    final c = Offset(size.width / 2, size.height * .42);
    for (var i = 0; i < 3; i++) {
      final p = ((t * 1.1 + i / 3) % 1.0);
      canvas.drawCircle(c, p * size.width * .9, Paint()..style = PaintingStyle.stroke..strokeWidth = 2..color = K.accent.withValues(alpha: (1 - p) * .22));
    }
  }

  @override
  bool shouldRepaint(_Rings o) => o.t != t;
}

class _Page {
  final String title;
  final String body;
  final CharSpec spec;
  const _Page(this.title, this.body, this.spec);
}

/// ثلاث شاشات ترحيب بشخصيات متحركة
class Onboarding extends StatefulWidget {
  const Onboarding({super.key});
  @override
  State<Onboarding> createState() => _OnboardingState();
}

class _OnboardingState extends State<Onboarding> with SingleTickerProviderStateMixin {
  final _pc = PageController();
  late final AnimationController _clock = AnimationController(vsync: this, duration: const Duration(seconds: 600))..repeat();
  late final Animation<double> time = Tween<double>(begin: 0, end: 600).animate(_clock);
  int _i = 0;

  late final List<_Page> pages = [
    _Page('قصّ ورتّب بسهولة', 'استورد فيديوهاتك وصورك، قصّ كل مقطع بدقة، وأضف الانتقالات والفلاتر على الخط الزمني.', charFor(11, const [Color(0xFF7C3AED), Color(0xFFEC4899)], female: false).copyMotion(1)),
    _Page('قوالب بشخصيات متحركة', 'أكثر من 85 قالباً بشخصيات ذكور وإناث وخلفيات متحركة. اختر قالباً وأضف صورك وانتهى.', charFor(8, const [Color(0xFF06B6D4), Color(0xFF8B5CF6)], female: true).copyMotion(0)),
    _Page('صدّر بدون علامة مائية', 'اختر الدقة من 480p إلى 4K ومعدل الإطار والجودة، واحفظ الفيديو مباشرة على جهازك.', charFor(19, const [Color(0xFFF59E0B), Color(0xFFEF4444)], female: false).copyMotion(4)),
  ];

  @override
  void dispose() {
    _clock.dispose();
    _pc.dispose();
    super.dispose();
  }

  Future<void> _done() async {
    await Store.setOnboarded();
    if (!mounted) return;
    Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => const AuthLanding()));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.white,
      body: SafeArea(
        child: Column(children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 8, 20, 0),
            child: Row(children: [
              const AppLogo(size: 38),
              const SizedBox(width: 10),
              const LogoWord(size: 24),
              const Spacer(),
              Tap(onTap: _done, child: Padding(padding: const EdgeInsets.all(8), child: Text('تخطّي', style: tx(15, color: K.sub)))),
            ]),
          ),
          Expanded(
            child: PageView.builder(
              controller: _pc,
              itemCount: pages.length,
              onPageChanged: (v) => setState(() => _i = v),
              itemBuilder: (_, i) {
                return AnimatedBuilder(
                  animation: _pc,
                  builder: (_, __) {
                    double off = 0;
                    if (_pc.hasClients && _pc.position.haveDimensions) off = (_pc.page ?? i.toDouble()) - i;
                    final p = pages[i];
                    return Padding(
                      padding: const EdgeInsets.fromLTRB(22, 14, 22, 0),
                      child: Column(children: [
                        Expanded(
                          child: Transform.translate(
                            offset: Offset(off * -80, 0),
                            child: Transform.scale(
                              scale: 1 - off.abs() * .12,
                              child: ClipRRect(borderRadius: BorderRadius.circular(30), child: CharView(spec: p.spec, time: time, seed: i + 3)),
                            ),
                          ),
                        ),
                        const SizedBox(height: 22),
                        Opacity(
                          opacity: (1 - off.abs() * 1.4).clamp(0.0, 1.0),
                          child: Column(children: [
                            Text(p.title, textAlign: TextAlign.center, style: tx(26, w: FontWeight.w800)),
                            const SizedBox(height: 10),
                            Text(p.body, textAlign: TextAlign.center, style: tx(15, color: K.sub, h: 1.7)),
                          ]),
                        ),
                      ]),
                    );
                  },
                );
              },
            ),
          ),
          const SizedBox(height: 14),
          Row(mainAxisAlignment: MainAxisAlignment.center, children: [
            for (var i = 0; i < pages.length; i++)
              AnimatedContainer(duration: const Duration(milliseconds: 250), margin: const EdgeInsets.all(4), width: _i == i ? 26 : 8, height: 8, decoration: BoxDecoration(color: _i == i ? K.ink : K.line, borderRadius: BorderRadius.circular(8))),
          ]),
          Padding(
            padding: const EdgeInsets.fromLTRB(22, 14, 22, 22),
            child: PillButton(_i == pages.length - 1 ? 'ابدأ الآن' : 'التالي', onTap: () {
              if (_i == pages.length - 1) {
                _done();
              } else {
                _pc.nextPage(duration: const Duration(milliseconds: 380), curve: Curves.easeOutCubic);
              }
            }),
          ),
        ]),
      ),
    );
  }
}

import 'package:flutter/material.dart';
import '../store.dart';
import '../theme.dart';
import 'auth.dart';

class Onboarding extends StatefulWidget {
  const Onboarding({super.key});
  @override
  State<Onboarding> createState() => _OnboardingState();
}

class _OnboardingState extends State<Onboarding> {
  final _pc = PageController();
  int _i = 0;

  static const _pages = [
    ('✂️', 'قص ورتّب بسهولة', 'استورد فيديوهات وصور، قصّ كل مقطع بدقة، ورتّبها بالسحب على الخط الزمني.', [Color(0xFF8B5CF6), Color(0xFFEC4899)]),
    ('🎨', 'فلاتر وانتقالات ونصوص', 'عشرة فلاتر جاهزة، انتقالات سلسة بين المقاطع، ونصوص متحركة بالعربي والإنجليزي.', [Color(0xFF06B6D4), Color(0xFF8B5CF6)]),
    ('🚀', 'صدّر بدون علامة مائية', 'جودة عالية محفوظة على جهازك مباشرة. مجاني بالكامل بدون قيود.', [Color(0xFFF59E0B), Color(0xFFEF4444)]),
  ];

  Future<void> _done() async {
    await Store.setOnboarded();
    if (!mounted) return;
    Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => const AuthPage()));
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Column(children: [
          Align(alignment: AlignmentDirectional.centerEnd, child: TextButton(onPressed: _done, child: const Text('تخطّي', style: TextStyle(color: C.sub)))),
          Expanded(
            child: PageView.builder(
              controller: _pc,
              itemCount: _pages.length,
              onPageChanged: (v) => setState(() => _i = v),
              itemBuilder: (_, i) {
                final p = _pages[i];
                return AnimatedBuilder(
                  animation: _pc,
                  builder: (_, __) {
                    double off = 0;
                    if (_pc.hasClients && _pc.position.haveDimensions) off = (_pc.page ?? i.toDouble()) - i;
                    return Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 28),
                      child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
                        Transform.translate(
                          offset: Offset(off * -120, 0),
                          child: Transform.scale(
                            scale: 1 - off.abs() * .25,
                            child: Container(
                              width: 220, height: 220,
                              decoration: BoxDecoration(gradient: LinearGradient(colors: p.$4, begin: Alignment.topRight, end: Alignment.bottomLeft), borderRadius: BorderRadius.circular(56), boxShadow: [BoxShadow(color: p.$4.first.withOpacity(.45), blurRadius: 40, offset: const Offset(0, 14))]),
                              child: Center(child: Text(p.$1, style: const TextStyle(fontSize: 96))),
                            ),
                          ),
                        ),
                        const SizedBox(height: 40),
                        Opacity(opacity: (1 - off.abs()).clamp(0.0, 1.0), child: Column(children: [
                          Text(p.$2, textAlign: TextAlign.center, style: const TextStyle(fontSize: 26, fontWeight: FontWeight.w800)),
                          const SizedBox(height: 12),
                          Text(p.$3, textAlign: TextAlign.center, style: const TextStyle(fontSize: 15, color: C.sub, height: 1.7)),
                        ])),
                      ]),
                    );
                  },
                );
              },
            ),
          ),
          Row(mainAxisAlignment: MainAxisAlignment.center, children: [
            for (var i = 0; i < _pages.length; i++)
              AnimatedContainer(duration: const Duration(milliseconds: 250), margin: const EdgeInsets.all(4), width: _i == i ? 26 : 8, height: 8, decoration: BoxDecoration(color: _i == i ? C.accent : C.line, borderRadius: BorderRadius.circular(8))),
          ]),
          Padding(
            padding: const EdgeInsets.all(22),
            child: GradButton(_i == _pages.length - 1 ? 'ابدأ الآن' : 'التالي', onTap: () {
              if (_i == _pages.length - 1) {
                _done();
              } else {
                _pc.nextPage(duration: const Duration(milliseconds: 350), curve: Curves.easeOut);
              }
            }),
          ),
        ]),
      ),
    );
  }
}

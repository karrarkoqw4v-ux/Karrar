import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'screens/auth.dart';
import 'screens/onboarding.dart';
import 'screens/shell.dart';
import 'store.dart';
import 'theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Store.init();
  runApp(const MontageApp());
}

class MontageApp extends StatelessWidget {
  const MontageApp({super.key});
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'مونتاج مرتاح',
      debugShowCheckedModeBanner: false,
      theme: buildTheme(),
      locale: const Locale('ar'),
      supportedLocales: const [Locale('ar')],
      localizationsDelegates: const [GlobalMaterialLocalizations.delegate, GlobalWidgetsLocalizations.delegate, GlobalCupertinoLocalizations.delegate],
      home: const Splash(),
    );
  }
}

class Splash extends StatefulWidget {
  const Splash({super.key});
  @override
  State<Splash> createState() => _SplashState();
}

class _SplashState extends State<Splash> with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(vsync: this, duration: const Duration(milliseconds: 1700))..forward();

  @override
  void initState() {
    super.initState();
    Timer(const Duration(milliseconds: 2100), _next);
  }

  void _next() {
    if (!mounted) return;
    Widget to;
    if (!Store.onboarded) {
      to = const Onboarding();
    } else if (Store.user == null) {
      to = const AuthPage();
    } else {
      to = const Shell();
    }
    Navigator.of(context).pushReplacement(PageRouteBuilder(pageBuilder: (_, __, ___) => to, transitionsBuilder: (_, a, __, child) => FadeTransition(opacity: a, child: child), transitionDuration: const Duration(milliseconds: 500)));
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Center(
        child: AnimatedBuilder(
          animation: _c,
          builder: (_, __) {
            final t = Curves.easeOutBack.transform(_c.value.clamp(0.0, 1.0));
            final o = Curves.easeIn.transform((_c.value * 1.6).clamp(0.0, 1.0));
            return Column(mainAxisSize: MainAxisSize.min, children: [
              Transform.scale(scale: .4 + .6 * t, child: Transform.rotate(angle: (1 - t) * -0.6, child: Container(
                width: 120, height: 120,
                decoration: BoxDecoration(borderRadius: BorderRadius.circular(32), boxShadow: [BoxShadow(color: C.accent.withOpacity(.5 * o), blurRadius: 40)]),
                child: ClipRRect(borderRadius: BorderRadius.circular(32), child: Image.asset('assets/logo.png')),
              ))),
              const SizedBox(height: 22),
              Opacity(opacity: o, child: Transform.translate(offset: Offset(0, 14 * (1 - o)), child: const Text('مونتاج مرتاح', style: TextStyle(fontSize: 30, fontWeight: FontWeight.w800)))),
              const SizedBox(height: 6),
              Opacity(opacity: o, child: const Text('مونتاج احترافي… مجاني وبدون علامة مائية', style: TextStyle(color: C.sub))),
            ]);
          },
        ),
      ),
    );
  }
}

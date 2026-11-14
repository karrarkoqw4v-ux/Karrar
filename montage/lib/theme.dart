import 'package:flutter/material.dart';

class C {
  static const bg = Color(0xFF0B0B12);
  static const surface = Color(0xFF15151F);
  static const surface2 = Color(0xFF1E1E2B);
  static const line = Color(0xFF2A2A3A);
  static const text = Color(0xFFF4F4FA);
  static const sub = Color(0xFF9A9AB5);
  static const accent = Color(0xFF8B5CF6);
  static const accent2 = Color(0xFFEC4899);
  static const cyan = Color(0xFF22D3EE);
  static const danger = Color(0xFFEF4444);
  static const grad = LinearGradient(colors: [accent, accent2], begin: Alignment.topRight, end: Alignment.bottomLeft);
}

ThemeData buildTheme() {
  final base = ThemeData(brightness: Brightness.dark, useMaterial3: true, fontFamily: 'Tajawal');
  return base.copyWith(
    scaffoldBackgroundColor: C.bg,
    colorScheme: const ColorScheme.dark(primary: C.accent, secondary: C.accent2, surface: C.surface),
    appBarTheme: const AppBarTheme(backgroundColor: C.bg, elevation: 0, centerTitle: true, titleTextStyle: TextStyle(fontFamily: 'Tajawal', fontSize: 18, fontWeight: FontWeight.w800, color: C.text)),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: C.surface,
      border: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: const BorderSide(color: C.line)),
      enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: const BorderSide(color: C.line)),
      focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: const BorderSide(color: C.accent, width: 1.5)),
      labelStyle: const TextStyle(color: C.sub),
    ),
    snackBarTheme: const SnackBarThemeData(behavior: SnackBarBehavior.floating),
  );
}

class GradButton extends StatelessWidget {
  final String text;
  final IconData? icon;
  final VoidCallback? onTap;
  final double height;
  const GradButton(this.text, {super.key, this.icon, this.onTap, this.height = 54});
  @override
  Widget build(BuildContext context) {
    return Opacity(
      opacity: onTap == null ? .5 : 1,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Ink(
          height: height,
          decoration: BoxDecoration(gradient: C.grad, borderRadius: BorderRadius.circular(16), boxShadow: const [BoxShadow(color: Color(0x558B5CF6), blurRadius: 18, offset: Offset(0, 6))]),
          child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
            if (icon != null) ...[Icon(icon, color: Colors.white), const SizedBox(width: 8)],
            Text(text, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800, color: Colors.white)),
          ]),
        ),
      ),
    );
  }
}

void toast(BuildContext c, String m) => ScaffoldMessenger.of(c).showSnackBar(SnackBar(content: Text(m)));

String fmtDur(double s) {
  final t = s.isNaN || s < 0 ? 0 : s;
  final m = (t ~/ 60).toString().padLeft(2, '0');
  final sec = (t % 60).toStringAsFixed(1).padLeft(4, '0');
  return '$m:$sec';
}

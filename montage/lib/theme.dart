import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class K {
  static const accent = Color(0xFF45C4DB);
  static const accentDark = Color(0xFF2BA9C2);
  static const ink = Color(0xFF111111);
  static const sub = Color(0xFF8A8F9A);
  static const fill = Color(0xFFF3F4F6);
  static const line = Color(0xFFE6E8EC);
  static const danger = Color(0xFFE5484D);
  // الوضع الداكن (المحرر)
  static const d0 = Color(0xFF111111);
  static const d1 = Color(0xFF1C1C1C);
  static const d2 = Color(0xFF262626);
  static const d3 = Color(0xFF353535);
  static const dsub = Color(0xFF9A9A9A);
}

ThemeData buildTheme() {
  final base = ThemeData(brightness: Brightness.light, useMaterial3: true, fontFamily: 'Tajawal');
  TextStyle? w(TextStyle? s, FontWeight fw) => s?.copyWith(fontFamily: 'Tajawal', fontWeight: fw);
  final t = base.textTheme;
  return base.copyWith(
    scaffoldBackgroundColor: Colors.white,
    colorScheme: ColorScheme.fromSeed(seedColor: K.accent, brightness: Brightness.light).copyWith(primary: K.accent, surface: Colors.white),
    textTheme: t.copyWith(
      bodyLarge: w(t.bodyLarge, FontWeight.w700),
      bodyMedium: w(t.bodyMedium, FontWeight.w700),
      bodySmall: w(t.bodySmall, FontWeight.w700),
      titleLarge: w(t.titleLarge, FontWeight.w800),
      titleMedium: w(t.titleMedium, FontWeight.w800),
      titleSmall: w(t.titleSmall, FontWeight.w800),
      labelLarge: w(t.labelLarge, FontWeight.w800),
      labelMedium: w(t.labelMedium, FontWeight.w700),
      labelSmall: w(t.labelSmall, FontWeight.w700),
      headlineSmall: w(t.headlineSmall, FontWeight.w800),
    ),
    appBarTheme: const AppBarTheme(backgroundColor: Colors.white, foregroundColor: K.ink, elevation: 0, scrolledUnderElevation: 0, centerTitle: true, titleTextStyle: TextStyle(fontFamily: 'Tajawal', fontSize: 18, fontWeight: FontWeight.w800, color: K.ink)),
    dividerColor: K.line,
    snackBarTheme: SnackBarThemeData(behavior: SnackBarBehavior.floating, backgroundColor: const Color(0xFF222222), contentTextStyle: const TextStyle(fontFamily: 'Tajawal', fontWeight: FontWeight.w700, color: Colors.white), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14))),
    bottomSheetTheme: const BottomSheetThemeData(backgroundColor: Colors.white, surfaceTintColor: Colors.transparent),
    dialogTheme: DialogThemeData(backgroundColor: Colors.white, surfaceTintColor: Colors.transparent, shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20))),
  );
}

TextStyle tx(double size, {FontWeight w = FontWeight.w700, Color color = K.ink, double? h}) => TextStyle(fontFamily: 'Tajawal', fontSize: size, fontWeight: w, color: color, height: h);

void toast(BuildContext c, String m) {
  ScaffoldMessenger.of(c).hideCurrentSnackBar();
  ScaffoldMessenger.of(c).showSnackBar(SnackBar(content: Text(m)));
}

String fmtDur(double s) {
  final t = s.isNaN || s < 0 ? 0 : s;
  final m = (t ~/ 60).toString().padLeft(2, '0');
  final sec = (t % 60).floor().toString().padLeft(2, '0');
  return '$m:$sec';
}

String fmtDurMs(double s) {
  final t = s.isNaN || s < 0 ? 0 : s;
  final m = (t ~/ 60).toString().padLeft(2, '0');
  final sec = (t % 60).toStringAsFixed(1).padLeft(4, '0');
  return '$m:$sec';
}

String fmtSize(int bytes) {
  if (bytes < 1024 * 1024) return '${(bytes / 1024).round()}KB';
  return '${(bytes / (1024 * 1024)).toStringAsFixed(bytes > 100 * 1024 * 1024 ? 0 : 1)}MB';
}

/// ضغطة مع حركة نابضة (تصغير ثم ارتداد) + اهتزاز خفيف.
class Tap extends StatefulWidget {
  final Widget child;
  final VoidCallback? onTap;
  final VoidCallback? onLongPress;
  final double scale;
  final bool haptic;
  const Tap({super.key, required this.child, this.onTap, this.onLongPress, this.scale = 0.9, this.haptic = true});
  @override
  State<Tap> createState() => _TapState();
}

class _TapState extends State<Tap> with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(vsync: this, duration: const Duration(milliseconds: 90), reverseDuration: const Duration(milliseconds: 420));
  late final Animation<double> _s = Tween<double>(begin: 1.0, end: widget.scale).animate(CurvedAnimation(parent: _c, curve: Curves.easeOut, reverseCurve: Curves.elasticOut));

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final enabled = widget.onTap != null || widget.onLongPress != null;
    return GestureDetector(
      behavior: HitTestBehavior.opaque,
      onTapDown: enabled ? (_) => _c.forward() : null,
      onTapUp: enabled ? (_) => _c.reverse() : null,
      onTapCancel: enabled ? () => _c.reverse() : null,
      onTap: widget.onTap == null
          ? null
          : () {
              if (widget.haptic) HapticFeedback.selectionClick();
              widget.onTap!();
            },
      onLongPress: widget.onLongPress == null
          ? null
          : () {
              HapticFeedback.mediumImpact();
              widget.onLongPress!();
            },
      child: ScaleTransition(scale: _s, child: widget.child),
    );
  }
}

/// شعار مونتاجي (الخيار 1): مربع أسود حرفه «م» أبيض ونقطة سماوية
class AppLogo extends StatelessWidget {
  final double size;
  const AppLogo({super.key, this.size = 84});
  @override
  Widget build(BuildContext context) {
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(color: const Color(0xFF0B0B0F), borderRadius: BorderRadius.circular(size * 0.227)),
      child: Stack(children: [
        Center(child: Padding(padding: EdgeInsets.only(top: size * 0.04), child: Text('م', style: TextStyle(fontFamily: 'Tajawal', fontWeight: FontWeight.w800, fontSize: size * 0.7, color: Colors.white, height: 1.0)))),
        Positioned(top: size * 0.2, right: size * 0.2, child: Container(width: size * 0.11, height: size * 0.11, decoration: const BoxDecoration(color: K.accent, shape: BoxShape.circle))),
      ]),
    );
  }
}

class LogoWord extends StatelessWidget {
  final double size;
  final Color color;
  const LogoWord({super.key, this.size = 36, this.color = K.ink});
  @override
  Widget build(BuildContext context) => Text('مونتاجي', style: TextStyle(fontFamily: 'Tajawal', fontWeight: FontWeight.w800, fontSize: size, color: color, height: 1.1));
}

/// زر رئيسي بنمط كاب كات (أسود/مفرغ/سماوي)
class PillButton extends StatelessWidget {
  final String text;
  final IconData? icon;
  final VoidCallback? onTap;
  final bool filled;
  final Color fillColor;
  final Color textColor;
  final double height;
  const PillButton(this.text, {super.key, this.icon, this.onTap, this.filled = true, this.fillColor = Colors.black, this.textColor = Colors.white, this.height = 50});
  @override
  Widget build(BuildContext context) {
    final disabled = onTap == null;
    final bg = filled ? (disabled ? const Color(0xFFECEEF1) : fillColor) : Colors.white;
    final fg = filled ? (disabled ? const Color(0xFFB8BCC4) : textColor) : K.ink;
    return Tap(
      onTap: onTap,
      scale: 0.96,
      child: Container(
        height: height,
        decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(height / 2), border: filled ? null : Border.all(color: const Color(0xFFD9DCE2))),
        child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [
          if (icon != null) ...[Icon(icon, color: fg, size: 22), const SizedBox(width: 10)],
          Text(text, style: tx(16, w: FontWeight.w800, color: fg)),
        ]),
      ),
    );
  }
}

/// اسم بتدرّج أصفر ← أخضر ← أحمر
class ColorWord extends StatelessWidget {
  final String text;
  final double size;
  const ColorWord(this.text, {super.key, this.size = 30});
  @override
  Widget build(BuildContext context) => ShaderMask(
        blendMode: BlendMode.srcIn,
        shaderCallback: (r) => const LinearGradient(colors: [Color(0xFFFFC107), Color(0xFF2EB85C), Color(0xFFE53935)], stops: [0.0, 0.5, 1.0]).createShader(r),
        child: Text(text, style: TextStyle(fontFamily: 'Tajawal', fontWeight: FontWeight.w800, fontSize: size, color: Colors.white, height: 1.2)),
      );
}

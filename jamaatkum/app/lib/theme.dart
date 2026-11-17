import 'package:flutter/material.dart';

const brand = Color(0xFFB9B3D9); // لافندر هادئ (نمط 6)
const onBrand = Color(0xFF1F1E26); // نص/أيقونات فوق اللون الأساسي
const likeColor = Color(0xFFE59AAE); // لون اللايك (وردي هادئ)
const outline = Color(0xFF6A6685); // حدود الأزرار الثانوية
const cyan = Color(0xFF25F4EE); // سماوي تيك توك
const brandDark = Color(0xFF9C95C4);
const bgDark = Color(0xFF000000);
const fieldFill = Color(0xFF1F1E26);

ThemeData buildTheme() => ThemeData(
      // بدون fontFamily = خط النظام الأصلي (Roboto + خط العربي الافتراضي بأندرويد)
      brightness: Brightness.dark,
      useMaterial3: true,
      colorScheme: ColorScheme.fromSeed(seedColor: brand, brightness: Brightness.dark),
      scaffoldBackgroundColor: bgDark,
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: fieldFill,
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        hintStyle: const TextStyle(color: Color(0xB3FFFFFF), fontSize: 14),
        prefixIconColor: const Color(0xB3FFFFFF),
        suffixIconColor: const Color(0xB3FFFFFF),
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: BorderSide.none),
        enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: BorderSide.none),
        focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: const BorderSide(color: brand, width: 1.6)),
        errorBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: const BorderSide(color: Color(0xFFFFB84D))),
        focusedErrorBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(14), borderSide: const BorderSide(color: Color(0xFFFFB84D), width: 1.6)),
      ),
    );

class GradientButton extends StatelessWidget {
  final String text;
  final VoidCallback? onPressed;
  final bool loading;
  final bool pill; // زر دائري بالكامل
  const GradientButton({super.key, required this.text, this.onPressed, this.loading = false, this.pill = false});
  @override
  Widget build(BuildContext context) => SizedBox(
        width: double.infinity,
        height: 48,
        child: DecoratedBox(
          decoration: BoxDecoration(
            gradient: const LinearGradient(colors: [brand, brand]),
            borderRadius: BorderRadius.circular(pill ? 30 : 14),
            
          ),
          child: ElevatedButton(
            onPressed: loading ? null : onPressed,
            style: ElevatedButton.styleFrom(
              backgroundColor: Colors.transparent,
              shadowColor: Colors.transparent,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(pill ? 30 : 14)),
            ),
            child: loading
                ? const SizedBox(width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 2.4, color: onBrand))
                : Text(text, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: onBrand)),
          ),
        ),
      );
}

class AuthLogo extends StatelessWidget {
  const AuthLogo({super.key});
  @override
  Widget build(BuildContext context) => Column(children: [
        Container(
          width: 64,
          height: 64,
          decoration: BoxDecoration(
            color: Colors.black,
            border: Border.all(color: Colors.white12),
            borderRadius: BorderRadius.circular(24),
          ),
          child: Stack(alignment: Alignment.center, children: [
            Transform(transform: Matrix4Translate.shift(-2.5, -2.5), child: const Icon(Icons.groups_rounded, size: 34, color: cyan)),
            Transform(transform: Matrix4Translate.shift(2.5, 2.5), child: const Icon(Icons.groups_rounded, size: 34, color: brand)),
            Icon(Icons.groups_rounded, size: 34, color: Colors.white),
          ]),
        ),
        const SizedBox(height: 10),
        const Text('جماعتكم', style: TextStyle(fontSize: 24, fontWeight: FontWeight.w800)),
      ]);
}

class Matrix4Translate {
  static Matrix4 shift(double x, double y) => Matrix4.translationValues(x, y, 0);
}

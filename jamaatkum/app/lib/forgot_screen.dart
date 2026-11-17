import 'package:flutter/material.dart';
import 'api.dart';
import 'theme.dart';

class ForgotScreen extends StatefulWidget {
  const ForgotScreen({super.key});
  @override
  State<ForgotScreen> createState() => _ForgotScreenState();
}

class _ForgotScreenState extends State<ForgotScreen> {
  final _email = TextEditingController();
  bool _loading = false, _sent = false;

  Future<void> _send() async {
    if (!RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$').hasMatch(_email.text.trim())) return;
    setState(() => _loading = true);
    try {
      await Api.forgot(_email.text.trim());
      setState(() => _sent = true);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(backgroundColor: Colors.transparent),
        body: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Text('نسيت كلمة السر؟', style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
            const SizedBox(height: 8),
            const Text('اكتب بريدك وراح نرسل لك رابط إعادة التعيين', style: TextStyle(fontSize: 13, color: Colors.white70)),
            const SizedBox(height: 22),
            TextField(
              controller: _email,
              keyboardType: TextInputType.emailAddress,
              textDirection: TextDirection.ltr,
              decoration: const InputDecoration(hintText: 'البريد الإلكتروني', prefixIcon: Icon(Icons.mail_outline)),
            ),
            const SizedBox(height: 14),
            if (_sent) const Padding(padding: EdgeInsets.only(bottom: 14), child: Text('تم الإرسال إذا كان البريد مسجّلاً ✓', style: TextStyle(color: Colors.greenAccent))),
            GradientButton(text: 'إرسال الرابط', loading: _loading, onPressed: _send),
            const SizedBox(height: 10),
            Center(child: TextButton(onPressed: () => Navigator.pop(context), child: const Text('رجوع لتسجيل الدخول', style: TextStyle(color: brand)))),
          ]),
        ),
      );
}

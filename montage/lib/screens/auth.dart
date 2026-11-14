import 'package:flutter/material.dart';
import '../store.dart';
import '../theme.dart';
import 'shell.dart';

class AuthPage extends StatefulWidget {
  const AuthPage({super.key});
  @override
  State<AuthPage> createState() => _AuthPageState();
}

class _AuthPageState extends State<AuthPage> {
  bool _register = false;
  final _name = TextEditingController();
  final _email = TextEditingController();
  final _pass = TextEditingController();
  String _err = '';
  bool _busy = false;

  void _goHome() => Navigator.of(context).pushAndRemoveUntil(MaterialPageRoute(builder: (_) => const Shell()), (_) => false);

  Future<void> _submit() async {
    setState(() { _busy = true; _err = ''; });
    final e = _register ? await Store.register(_name.text, _email.text, _pass.text) : await Store.login(_email.text, _pass.text);
    if (!mounted) return;
    setState(() => _busy = false);
    if (e == null) {
      _goHome();
    } else {
      setState(() => _err = e);
    }
  }

  void _google() {
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        backgroundColor: C.surface,
        title: const Text('الدخول بحساب جوجل'),
        content: const Text('الدخول بجوجل يحتاج ربط التطبيق بمعرّف OAuth من Google Cloud (باسم الحزمة وبصمة التوقيع). لم يُضبط في هذه النسخة، فاستخدم البريد أو تابع كزائر.', style: TextStyle(height: 1.7)),
        actions: [TextButton(onPressed: () => Navigator.pop(context), child: const Text('حسناً'))],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            const SizedBox(height: 18),
            Center(child: ClipRRect(borderRadius: BorderRadius.circular(26), child: Image.asset('assets/logo.png', width: 88, height: 88))),
            const SizedBox(height: 14),
            const Center(child: Text('مونتاج مرتاح', style: TextStyle(fontSize: 26, fontWeight: FontWeight.w800))),
            const SizedBox(height: 4),
            Center(child: Text(_register ? 'أنشئ حسابك' : 'سجّل دخولك', style: const TextStyle(color: C.sub))),
            const SizedBox(height: 26),
            if (_register) ...[TextField(controller: _name, decoration: const InputDecoration(labelText: 'الاسم')), const SizedBox(height: 12)],
            TextField(controller: _email, keyboardType: TextInputType.emailAddress, textDirection: TextDirection.ltr, decoration: const InputDecoration(labelText: 'البريد الإلكتروني')),
            const SizedBox(height: 12),
            TextField(controller: _pass, obscureText: true, textDirection: TextDirection.ltr, decoration: const InputDecoration(labelText: 'كلمة السر')),
            if (_err.isNotEmpty) Padding(padding: const EdgeInsets.only(top: 10), child: Text(_err, style: const TextStyle(color: C.danger))),
            const SizedBox(height: 18),
            GradButton(_busy ? '...' : (_register ? 'إنشاء الحساب' : 'دخول'), onTap: _busy ? null : _submit),
            const SizedBox(height: 10),
            TextButton(onPressed: () => setState(() { _register = !_register; _err = ''; }), child: Text(_register ? 'لدي حساب بالفعل' : 'ليس لدي حساب؟ أنشئ حساباً')),
            const Row(children: [Expanded(child: Divider(color: C.line)), Padding(padding: EdgeInsets.symmetric(horizontal: 12), child: Text('أو', style: TextStyle(color: C.sub))), Expanded(child: Divider(color: C.line))]),
            const SizedBox(height: 14),
            OutlinedButton.icon(
              onPressed: _google,
              icon: const Text('G', style: TextStyle(fontWeight: FontWeight.w900, fontSize: 20, color: Color(0xFF4285F4))),
              label: const Text('المتابعة بحساب جوجل'),
              style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(52), side: const BorderSide(color: C.line), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16))),
            ),
            const SizedBox(height: 10),
            OutlinedButton(
              onPressed: () async { await Store.guest(); if (mounted) _goHome(); },
              style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(52), side: const BorderSide(color: C.line), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16))),
              child: const Text('المتابعة كزائر'),
            ),
          ]),
        ),
      ),
    );
  }
}

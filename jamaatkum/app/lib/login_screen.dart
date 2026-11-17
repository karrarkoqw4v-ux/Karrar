import 'package:flutter/material.dart';
import 'api.dart';
import 'feed_screen.dart';
import 'forgot_screen.dart';
import 'glow_background.dart';
import 'register_screen.dart';
import 'theme.dart';

/// تصميم B: الشعار فوق + بطاقة سودا من تحت. لما المستخدم يكتب:
/// الكيبورد يطلع، البطاقة ترتفع، الشعار يصغر، والخلفية تتحرك أكثر.
class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});
  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _form = GlobalKey<FormState>();
  final _email = TextEditingController();
  final _pass = TextEditingController();
  final _emailFocus = FocusNode();
  final _passFocus = FocusNode();
  bool _hide = true, _loading = false, _typing = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _emailFocus.addListener(_focusChanged);
    _passFocus.addListener(_focusChanged);
  }

  void _focusChanged() => setState(() => _typing = _emailFocus.hasFocus || _passFocus.hasFocus);

  @override
  void dispose() {
    _emailFocus.dispose();
    _passFocus.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    FocusScope.of(context).unfocus();
    if (!_form.currentState!.validate()) return;
    setState(() { _loading = true; _error = null; });
    try {
      await Api.login(_email.text.trim(), _pass.text);
      if (!mounted) return;
      Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => const FeedScreen()));
    } on ApiException catch (e) {
      setState(() => _error = e.message);
    } catch (_) {
      setState(() => _error = 'تعذر الاتصال بالسيرفر');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final kb = MediaQuery.viewInsetsOf(context).bottom;
    return Scaffold(
      resizeToAvoidBottomInset: false, // نحرك البطاقة بأنفسنا بحركة ناعمة
      body: GlowBackground(
        active: _typing,
        child: SafeArea(
          bottom: false,
          child: Stack(children: [
            // الشعار: يصغر ويطلع لفوق لما يكتب
            AnimatedAlign(
              duration: const Duration(milliseconds: 350),
              curve: Curves.easeOutCubic,
              alignment: _typing ? const Alignment(0, -0.85) : const Alignment(0, -0.5),
              child: AnimatedScale(
                duration: const Duration(milliseconds: 350),
                scale: _typing ? 0.7 : 1,
                child: const AuthLogo(),
              ),
            ),
            // البطاقة: ترتفع مع الكيبورد
            AnimatedPositioned(
              duration: const Duration(milliseconds: 250),
              curve: Curves.easeOut,
              left: 0,
              right: 0,
              bottom: kb,
              child: Container(
                padding: const EdgeInsets.fromLTRB(24, 24, 24, 22),
                decoration: BoxDecoration(
                  color: const Color(0xFF161618),
                  borderRadius: const BorderRadius.vertical(top: Radius.circular(30)),
                  border: Border.all(color: Colors.white10),
                ),
                child: Form(
                  key: _form,
                  child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
                    const Text('تسجيل الدخول', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
                    const SizedBox(height: 14),
                    TextFormField(
                      controller: _email,
                      focusNode: _emailFocus,
                      keyboardType: TextInputType.emailAddress,
                      textInputAction: TextInputAction.next,
                      textDirection: TextDirection.ltr,
                      decoration: const InputDecoration(hintText: 'البريد الإلكتروني', prefixIcon: Icon(Icons.mail_outline)),
                      validator: (v) => RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$').hasMatch(v?.trim() ?? '') ? null : 'اكتب بريد إلكتروني صحيح',
                    ),
                    const SizedBox(height: 10),
                    TextFormField(
                      controller: _pass,
                      focusNode: _passFocus,
                      obscureText: _hide,
                      textDirection: TextDirection.ltr,
                      onFieldSubmitted: (_) => _submit(),
                      decoration: InputDecoration(
                        hintText: 'كلمة السر',
                        prefixIcon: const Icon(Icons.lock_outline),
                        suffixIcon: IconButton(icon: Icon(_hide ? Icons.visibility_off_outlined : Icons.visibility_outlined), onPressed: () => setState(() => _hide = !_hide)),
                      ),
                      validator: (v) => (v ?? '').isEmpty ? 'اكتب كلمة السر' : null,
                    ),
                    Align(
                      alignment: AlignmentDirectional.centerStart,
                      child: TextButton(
                        onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const ForgotScreen())),
                        child: const Text('نسيت كلمة السر؟', style: TextStyle(fontSize: 13, color: brand)),
                      ),
                    ),
                    if (_error != null) Padding(padding: const EdgeInsets.only(bottom: 8), child: Text(_error!, style: const TextStyle(color: Color(0xFFFFB84D)))),
                    GradientButton(text: 'دخول ومتابعة', loading: _loading, onPressed: _submit),
                    const Divider(height: 28, color: Colors.white10),
                    Row(mainAxisAlignment: MainAxisAlignment.center, children: [
                      const Text('ما عندك حساب؟', style: TextStyle(fontSize: 13, color: Colors.white70)),
                      TextButton(
                        onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const RegisterScreen())),
                        child: const Text('إنشاء حساب', style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: brand)),
                      ),
                    ]),
                  ]),
                ),
              ),
            ),
          ]),
        ),
      ),
    );
  }
}

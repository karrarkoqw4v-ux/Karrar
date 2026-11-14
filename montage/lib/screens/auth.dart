import 'package:flutter/material.dart';
import '../store.dart';
import '../theme.dart';
import 'shell.dart';

void goHome(BuildContext context) => Navigator.of(context).pushAndRemoveUntil(MaterialPageRoute(builder: (_) => const Shell()), (_) => false);

void showHelp(BuildContext context) {
  showModalBottomSheet(
    context: context,
    shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(24))),
    builder: (_) => Padding(
      padding: const EdgeInsets.all(22),
      child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text('المساعدة', style: tx(20, w: FontWeight.w800)),
        const SizedBox(height: 12),
        Text('• الحسابات محفوظة على هذا الجهاز فقط ولا تُرفع لأي خادم.\n• يمكنك المتابعة كزائر بدون تسجيل.\n• كل المعالجة (القص والفلاتر والتصدير) تتم على جهازك.\n• إذا نسيت كلمة السر استخدم «هل نسيت كلمة المرور؟» لاختيار كلمة جديدة.', style: tx(15, color: K.sub, h: 1.9)),
        const SizedBox(height: 10),
      ]),
    ),
  );
}

void showTerms(BuildContext context, {bool privacy = false}) {
  showDialog(
    context: context,
    builder: (_) => AlertDialog(
      title: Text(privacy ? 'سياسة الخصوصية' : 'شروط الخدمة', style: tx(18, w: FontWeight.w800)),
      content: SingleChildScrollView(
        child: Text(
          privacy
              ? 'مونتاجي لا يجمع بياناتك ولا يرفع ملفاتك. الحسابات والمشاريع والفيديوهات المُصدَّرة محفوظة على جهازك فقط، ويمكنك حذفها في أي وقت من المحذوفات أو بمسح بيانات التطبيق.'
              : 'مونتاجي تطبيق مونتاج مجاني. تُصدَّر الفيديوهات بدون علامة مائية. أنت مسؤول عن الحقوق في الوسائط والموسيقى التي تستخدمها داخل مشاريعك.',
          style: tx(15, color: K.sub, h: 1.8),
        ),
      ),
      actions: [TextButton(onPressed: () => Navigator.pop(context), child: Text('حسناً', style: tx(15, color: K.accentDark)))],
    ),
  );
}

class _Top extends StatelessWidget {
  final String? title;
  final bool back;
  const _Top({this.title, this.back = true});
  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(20, 10, 20, 10),
      child: Row(children: [
        if (back) Tap(onTap: () => Navigator.of(context).pop(), child: const Padding(padding: EdgeInsets.all(6), child: Icon(Icons.arrow_forward_ios_rounded, size: 22))) else const SizedBox(width: 34),
        Expanded(child: Center(child: Text(title ?? '', style: tx(18, w: FontWeight.w800)))),
        Tap(onTap: () => showHelp(context), child: Padding(padding: const EdgeInsets.all(6), child: Text('المساعدة', style: tx(15, color: K.sub)))),
      ]),
    );
  }
}

class AuthLanding extends StatelessWidget {
  const AuthLanding({super.key});
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Column(children: [
          const _Top(back: false),
          const Spacer(),
          const Column(children: [AppLogo(size: 100), SizedBox(height: 14), LogoWord(size: 40)]),
          const Spacer(),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 26),
            child: Column(children: [
              PillButton('تسجيل بالبريد الإلكتروني', icon: Icons.mail_rounded, onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const EmailLogin()))),
              const SizedBox(height: 12),
              PillButton('المتابعة كزائر', icon: Icons.person_rounded, filled: false, onTap: () async {
                await Store.guest();
                if (context.mounted) goHome(context);
              }),
              const SizedBox(height: 18),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 10),
                child: Text.rich(
                  TextSpan(style: tx(13, color: K.sub, h: 1.9), children: [
                    const TextSpan(text: 'لقد قرأت وأقر '),
                    WidgetSpan(alignment: PlaceholderAlignment.baseline, baseline: TextBaseline.alphabetic, child: Tap(scale: .96, onTap: () => showTerms(context), child: Text('بشروط الخدمة', style: tx(13, color: K.accentDark)))),
                    const TextSpan(text: ' و'),
                    WidgetSpan(alignment: PlaceholderAlignment.baseline, baseline: TextBaseline.alphabetic, child: Tap(scale: .96, onTap: () => showTerms(context, privacy: true), child: Text('سياسة الخصوصية', style: tx(13, color: K.accentDark)))),
                    const TextSpan(text: ' الخاصة بـ مونتاجي.'),
                  ]),
                  textAlign: TextAlign.center,
                ),
              ),
              const SizedBox(height: 26),
            ]),
          ),
        ]),
      ),
    );
  }
}

class _Field extends StatelessWidget {
  final TextEditingController c;
  final String hint;
  final bool pass;
  final bool ltr;
  final ValueChanged<String> onChanged;
  const _Field(this.c, this.hint, this.onChanged, {this.pass = false, this.ltr = false});
  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(color: K.fill, borderRadius: BorderRadius.circular(14)),
      child: TextField(
        controller: c,
        onChanged: onChanged,
        obscureText: pass,
        textDirection: ltr ? TextDirection.ltr : null,
        textAlign: ltr ? TextAlign.left : TextAlign.start,
        keyboardType: ltr && !pass ? TextInputType.emailAddress : TextInputType.text,
        style: tx(16),
        decoration: InputDecoration(hintText: hint, hintStyle: tx(16, w: FontWeight.w500, color: const Color(0xFFAAB0BA)), border: InputBorder.none, contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 16)),
      ),
    );
  }
}

class EmailLogin extends StatefulWidget {
  const EmailLogin({super.key});
  @override
  State<EmailLogin> createState() => _EmailLoginState();
}

class _EmailLoginState extends State<EmailLogin> {
  final _e = TextEditingController();
  final _p = TextEditingController();
  String _err = '';
  bool get ok => Store.validEmail(_e.text) && _p.text.length >= 6;

  Future<void> _go() async {
    final r = await Store.login(_e.text, _p.text);
    if (!mounted) return;
    if (r == null) {
      goHome(context);
    } else {
      setState(() => _err = r);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Column(children: [
          const _Top(title: 'تسجيل الدخول'),
          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 24),
              child: Column(children: [
                const SizedBox(height: 30),
                const Row(mainAxisAlignment: MainAxisAlignment.center, children: [AppLogo(size: 52), SizedBox(width: 12), LogoWord(size: 34)]),
                const SizedBox(height: 44),
                _Field(_e, 'أدخل عنوان البريد الإلكتروني', (_) => setState(() => _err = ''), ltr: true),
                const SizedBox(height: 12),
                _Field(_p, 'أدخل كلمة السر', (_) => setState(() => _err = ''), pass: true, ltr: true),
                if (_err.isNotEmpty) Padding(padding: const EdgeInsets.only(top: 10), child: Text(_err, style: tx(14, color: K.danger))),
                const SizedBox(height: 14),
                PillButton('استمرار', onTap: ok ? _go : null, fillColor: K.accent, height: 52),
                const SizedBox(height: 18),
                Tap(onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const ResetPage())), child: Text('هل نسيت كلمة المرور؟', style: tx(15, color: K.accentDark))),
              ]),
            ),
          ),
          Padding(
            padding: const EdgeInsets.only(bottom: 24),
            child: Tap(
              onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const RegisterPage())),
              child: Text.rich(TextSpan(style: tx(16, color: K.sub), children: [const TextSpan(text: 'أليس لديك حساب؟ '), TextSpan(text: 'سجّل الاشتراك', style: tx(16, color: K.accentDark))])),
            ),
          ),
        ]),
      ),
    );
  }
}

class RegisterPage extends StatefulWidget {
  const RegisterPage({super.key});
  @override
  State<RegisterPage> createState() => _RegisterPageState();
}

class _RegisterPageState extends State<RegisterPage> {
  final _n = TextEditingController();
  final _e = TextEditingController();
  final _p = TextEditingController();
  String _err = '';
  bool get ok => _n.text.trim().isNotEmpty && Store.validEmail(_e.text) && _p.text.length >= 6;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Column(children: [
          const _Top(title: 'إنشاء حساب'),
          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 24),
              child: Column(children: [
                const SizedBox(height: 24),
                const Row(mainAxisAlignment: MainAxisAlignment.center, children: [AppLogo(size: 52), SizedBox(width: 12), LogoWord(size: 34)]),
                const SizedBox(height: 36),
                _Field(_n, 'الاسم', (_) => setState(() => _err = '')),
                const SizedBox(height: 12),
                _Field(_e, 'أدخل عنوان البريد الإلكتروني', (_) => setState(() => _err = ''), ltr: true),
                const SizedBox(height: 12),
                _Field(_p, 'كلمة السر (6 أحرف على الأقل)', (_) => setState(() => _err = ''), pass: true, ltr: true),
                if (_err.isNotEmpty) Padding(padding: const EdgeInsets.only(top: 10), child: Text(_err, style: tx(14, color: K.danger))),
                const SizedBox(height: 14),
                PillButton('إنشاء الحساب', fillColor: K.accent, height: 52, onTap: ok ? () async {
                  final r = await Store.register(_n.text, _e.text, _p.text);
                  if (!mounted) return;
                  if (r == null) {
                    goHome(context);
                  } else {
                    setState(() => _err = r);
                  }
                } : null),
              ]),
            ),
          ),
        ]),
      ),
    );
  }
}

class ResetPage extends StatefulWidget {
  const ResetPage({super.key});
  @override
  State<ResetPage> createState() => _ResetPageState();
}

class _ResetPageState extends State<ResetPage> {
  final _e = TextEditingController();
  final _p = TextEditingController();
  String _err = '';
  bool get ok => Store.validEmail(_e.text) && _p.text.length >= 6;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Column(children: [
          const _Top(title: 'إعادة تعيين'),
          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 24),
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const SizedBox(height: 28),
                Text('نسيت كلمة السر', style: tx(30, w: FontWeight.w800)),
                const SizedBox(height: 8),
                Text('أدخل بريدك المسجّل على هذا الجهاز ثم اختر كلمة سر جديدة.', style: tx(16, color: K.sub, h: 1.8)),
                const SizedBox(height: 26),
                _Field(_e, 'أدخل عنوان البريد الإلكتروني', (_) => setState(() => _err = ''), ltr: true),
                const SizedBox(height: 12),
                _Field(_p, 'كلمة السر الجديدة', (_) => setState(() => _err = ''), pass: true, ltr: true),
                if (_err.isNotEmpty) Padding(padding: const EdgeInsets.only(top: 10), child: Text(_err, style: tx(14, color: K.danger))),
                const SizedBox(height: 14),
                PillButton('استمرار', fillColor: K.accent, height: 52, onTap: ok ? () async {
                  final r = await Store.resetPassword(_e.text, _p.text);
                  if (!mounted) return;
                  if (r == null) {
                    toast(context, 'تم تغيير كلمة السر. سجّل الدخول الآن');
                    Navigator.of(context).pop();
                  } else {
                    setState(() => _err = r);
                  }
                } : null),
              ]),
            ),
          ),
        ]),
      ),
    );
  }
}

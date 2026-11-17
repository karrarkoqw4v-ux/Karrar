import 'dart:async';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:image_cropper/image_cropper.dart';
import 'package:image_picker/image_picker.dart';
import 'api.dart';
import 'feed_screen.dart';
import 'theme.dart';

class RegisterScreen extends StatefulWidget {
  const RegisterScreen({super.key});
  @override
  State<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends State<RegisterScreen> {
  final _form = GlobalKey<FormState>();
  final _name = TextEditingController();
  final _user = TextEditingController();
  final _email = TextEditingController();
  final _pass = TextEditingController();
  File? _avatar;
  bool _hide = true, _loading = false, _checking = false;
  String? _userError; // null + _userOk = متاح
  bool _userOk = false;
  String? _serverError;
  Timer? _debounce;
  final _emailRe = RegExp(r'^[^@\s]+@[^@\s]+\.[^@\s]+$');

  // الصورة: المعرض (يطلب الصلاحية تلقائياً) ثم قص مربع ثابت 1:1 وتصغير لـ 512
  Future<void> _pickAvatar() async {
    final x = await ImagePicker().pickImage(source: ImageSource.gallery, maxWidth: 1024, maxHeight: 1024, imageQuality: 90);
    if (x == null) return;
    final c = await ImageCropper().cropImage(
      sourcePath: x.path,
      aspectRatio: const CropAspectRatio(ratioX: 1, ratioY: 1),
      maxWidth: 512,
      maxHeight: 512,
      compressQuality: 85,
      uiSettings: [
        AndroidUiSettings(toolbarTitle: 'تحديد الصورة', toolbarColor: bgDark, toolbarWidgetColor: Colors.white, activeControlsWidgetColor: brand, lockAspectRatio: true, hideBottomControls: true, cropFrameColor: Colors.white, cropGridColor: Colors.transparent, showCropGrid: false, cropStyle: CropStyle.circle), // إطار دائري، والناتج مربع 1:1
      ],
    );
    if (c != null) setState(() => _avatar = File(c.path));
  }

  void _onUserChanged(String v) {
    _debounce?.cancel();
    final u = v.trim().toLowerCase();
    setState(() { _userOk = false; _userError = null; });
    if (u.isEmpty) return;
    if (!RegExp(r'^[a-z0-9_.]{3,20}$').hasMatch(u)) {
      setState(() => _userError = '3-20 حرف إنجليزي أو أرقام أو _ .');
      return;
    }
    setState(() => _checking = true);
    _debounce = Timer(const Duration(milliseconds: 450), () async {
      try {
        final r = await Api.usernameCheck(u);
        if (!mounted) return;
        setState(() { _checking = false; _userError = r; _userOk = r == null; });
      } catch (_) {
        if (mounted) setState(() => _checking = false);
      }
    });
  }

  Future<void> _submit() async {
    setState(() => _serverError = null);
    if (!_form.currentState!.validate() || _userError != null || _checking) return;
    setState(() => _loading = true);
    try {
      await Api.register(name: _name.text.trim(), username: _user.text.trim().toLowerCase(), email: _email.text.trim(), password: _pass.text, avatar: _avatar);
      if (!mounted) return;
      Navigator.of(context).pushAndRemoveUntil(MaterialPageRoute(builder: (_) => const FeedScreen()), (_) => false);
    } on ApiException catch (e) {
      setState(() {
        if (e.field == 'username') _userError = e.message; else _serverError = e.message;
      });
    } catch (_) {
      setState(() => _serverError = 'تعذر الاتصال بالسيرفر');
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  @override
  void dispose() { _debounce?.cancel(); super.dispose(); }

  // حقول بخط سفلي فقط (تصميم 5)
  InputDecoration _dec(String hint, IconData icon, {Widget? suffix, String? error, String? helper, bool ok = false}) {
    UnderlineInputBorder line(Color c, [double w = 1.5]) => UnderlineInputBorder(borderSide: BorderSide(color: c, width: w));
    final base = ok ? Colors.greenAccent : Colors.white24;
    return InputDecoration(
      hintText: hint,
      filled: false,
      prefixIcon: Icon(icon),
      suffixIcon: suffix,
      errorText: error,
      helperText: helper,
      helperStyle: const TextStyle(color: Colors.greenAccent),
      contentPadding: const EdgeInsets.symmetric(vertical: 16),
      border: line(base),
      enabledBorder: line(base),
      focusedBorder: line(ok ? Colors.greenAccent : brand, 2),
      errorBorder: line(const Color(0xFFFFB84D)),
      focusedErrorBorder: line(const Color(0xFFFFB84D), 2),
    );
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        body: SafeArea(
          child: LayoutBuilder(
            builder: (context, box) => SingleChildScrollView(
              padding: const EdgeInsets.fromLTRB(24, 36, 24, 16),
              child: ConstrainedBox(
                constraints: BoxConstraints(minHeight: box.maxHeight - 52),
                child: IntrinsicHeight(
                  child: Form(
                    key: _form,
                    child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                      // الرأس: الصورة + العنوان
                      Row(children: [
                        GestureDetector(
                          onTap: _pickAvatar,
                          child: Stack(children: [
                            CircleAvatar(
                              radius: 42,
                              backgroundColor: fieldFill,
                              backgroundImage: _avatar != null ? FileImage(_avatar!) : null,
                              child: _avatar == null ? const Icon(Icons.person_rounded, size: 42, color: Colors.white54) : null,
                            ),
                            Positioned(
                              bottom: 0,
                              right: 0,
                              child: Container(
                                padding: const EdgeInsets.all(6),
                                decoration: BoxDecoration(color: brand, shape: BoxShape.circle, border: Border.all(color: Colors.black, width: 3)),
                                child: const Icon(Icons.camera_alt_rounded, size: 14, color: onBrand),
                              ),
                            ),
                          ]),
                        ),
                        const SizedBox(width: 16),
                        const Expanded(
                          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                            Text('حساب جديد', style: TextStyle(fontSize: 26, fontWeight: FontWeight.w800)),
                            SizedBox(height: 4),
                            Text('أضف صورتك وكمّل بياناتك', style: TextStyle(fontSize: 13, color: Colors.white70)),
                          ]),
                        ),
                      ]),
                      const SizedBox(height: 30),
                      TextFormField(
                        controller: _name,
                        textInputAction: TextInputAction.next,
                        decoration: _dec('الاسم الثلاثي', Icons.badge_outlined),
                        validator: (v) => (v ?? '').trim().split(RegExp(r'\s+')).where((w) => w.isNotEmpty).length >= 3 ? null : 'الاسم لازم يكون ثلاثي أو أكثر',
                      ),
                      const SizedBox(height: 8),
                      TextFormField(
                        controller: _user,
                        textDirection: TextDirection.ltr,
                        textInputAction: TextInputAction.next,
                        onChanged: _onUserChanged,
                        decoration: _dec(
                          'اسم المستخدم (اليوزر)',
                          Icons.alternate_email,
                          error: _userError,
                          helper: _userOk ? 'اسم المستخدم متاح ✓' : null,
                          ok: _userOk,
                          suffix: _checking
                              ? const Padding(padding: EdgeInsets.all(14), child: SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2)))
                              : _userOk
                                  ? const Icon(Icons.check_circle, color: Colors.greenAccent)
                                  : (_userError != null ? const Icon(Icons.cancel, color: Color(0xFFFFB84D)) : null),
                        ),
                        validator: (v) => (v ?? '').trim().isEmpty ? 'اكتب اسم المستخدم' : null,
                      ),
                      const SizedBox(height: 8),
                      TextFormField(
                        controller: _email,
                        keyboardType: TextInputType.emailAddress,
                        textDirection: TextDirection.ltr,
                        textInputAction: TextInputAction.next,
                        decoration: _dec('بريدك الإلكتروني', Icons.mail_outline),
                        validator: (v) => _emailRe.hasMatch(v?.trim() ?? '') ? null : 'اكتب بريد إلكتروني صحيح',
                      ),
                      const SizedBox(height: 8),
                      TextFormField(
                        controller: _pass,
                        obscureText: _hide,
                        textDirection: TextDirection.ltr,
                        decoration: _dec(
                          'كلمة السر (8 أحرف على الأقل)',
                          Icons.lock_outline,
                          suffix: IconButton(icon: Icon(_hide ? Icons.visibility_off_outlined : Icons.visibility_outlined), onPressed: () => setState(() => _hide = !_hide)),
                        ),
                        validator: (v) => (v ?? '').length >= 8 ? null : 'كلمة السر 8 أحرف على الأقل',
                      ),
                      const Spacer(),
                      const SizedBox(height: 20),
                      if (_serverError != null)
                        Padding(padding: const EdgeInsets.only(bottom: 10), child: Text(_serverError!, textAlign: TextAlign.center, style: const TextStyle(color: Color(0xFFFFB84D)))),
                      GradientButton(text: 'إنشاء ومتابعة', loading: _loading, onPressed: _submit, pill: true),
                      const SizedBox(height: 6),
                      TextButton(onPressed: () => Navigator.pop(context), child: const Text('رجوع لتسجيل الدخول', style: TextStyle(fontSize: 14, color: brand))),
                    ]),
                  ),
                ),
              ),
            ),
          ),
        ),
      );
}

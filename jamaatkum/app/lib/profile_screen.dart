import 'dart:convert';
import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'api.dart';
import 'feed_screen.dart' show fmt;
import 'glow_background.dart';
import 'login_screen.dart';
import 'theme.dart';

/// الملف الشخصي: بطاقة زجاجية (تصميم A) + قصص + تبويبات + شبكة الفيديوهات
class ProfileScreen extends StatefulWidget {
  const ProfileScreen({super.key});
  @override
  State<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends State<ProfileScreen> {
  Map<String, dynamic>? _user;
  List<dynamic> _myVideos = [];
  int _tab = 0;
  static const _tabs = [
    (Icons.grid_view_rounded, 'فيديوهاتي'),
    (Icons.repeat_rounded, 'إعادة'),
    (Icons.bookmark_border_rounded, 'محفوظ'),
    (Icons.favorite_border_rounded, 'إعجاب'),
  ];

  @override
  void initState() {
    super.initState();
    Api.currentUser().then((u) {
      if (mounted) setState(() => _user = u);
    });
    http.get(Uri.parse('$apiBase/videos')).then((r) {
      final all = jsonDecode(utf8.decode(r.bodyBytes)) as List;
      if (mounted && _user != null) setState(() => _myVideos = all.where((v) => v['author'] == _user!['username']).toList());
    }).catchError((_) {});
  }

  Future<void> _logout() async {
    await Api.logout();
    if (!mounted) return;
    Navigator.of(context, rootNavigator: true).pushAndRemoveUntil(MaterialPageRoute(builder: (_) => const LoginScreen()), (_) => false);
  }

  void _menu() => showModalBottomSheet(
        context: context,
        backgroundColor: const Color(0xFF161618),
        shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(26))),
        builder: (_) => SafeArea(
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            const SizedBox(height: 10),
            Container(width: 42, height: 5, decoration: BoxDecoration(color: Colors.white24, borderRadius: BorderRadius.circular(3))),
            ListTile(leading: const Icon(Icons.logout_rounded, color: brand), title: const Text('تسجيل الخروج'), onTap: () { Navigator.pop(context); _logout(); }),
            const SizedBox(height: 8),
          ]),
        ),
      );

  // زر دائري في الشريط العلوي
  Widget _circle(IconData icon, {VoidCallback? onTap, String? badge}) => GestureDetector(
        onTap: onTap,
        child: Stack(clipBehavior: Clip.none, children: [
          Container(
            width: 42,
            height: 42,
            decoration: BoxDecoration(shape: BoxShape.circle, border: Border.all(color: const Color(0xFF4C4A63), width: 1.5)),
            child: Icon(icon, size: 21),
          ),
          if (badge != null)
            Positioned(
              top: -3,
              left: -3,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1),
                constraints: const BoxConstraints(minWidth: 18, minHeight: 18),
                decoration: BoxDecoration(color: brand, borderRadius: BorderRadius.circular(9), border: Border.all(color: Colors.black, width: 2)),
                child: Text(badge, textAlign: TextAlign.center, style: const TextStyle(fontSize: 10, fontWeight: FontWeight.w800, color: onBrand)),
              ),
            ),
        ]),
      );

  Widget _stat(String n, String label) => Expanded(
        child: Column(children: [
          Text(n, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800)),
          const SizedBox(height: 2),
          Text(label, style: const TextStyle(fontSize: 12, color: Color(0xCCFFFFFF))),
        ]),
      );

  Widget _btn(IconData icon, String text, {bool primary = false, VoidCallback? onTap}) => GestureDetector(
        onTap: onTap,
        child: Container(
          height: 46,
          alignment: Alignment.center,
          decoration: BoxDecoration(
            color: primary ? brand : Colors.transparent,
            borderRadius: BorderRadius.circular(14),
            border: primary ? null : Border.all(color: outline, width: 1.5),
          ),
          child: Row(mainAxisSize: MainAxisSize.min, children: [
            Icon(icon, size: 18, color: primary ? onBrand : const Color(0xFFC9C5E6)),
            if (text.isNotEmpty) ...[const SizedBox(width: 8), Text(text, style: TextStyle(fontSize: 14.5, fontWeight: FontWeight.w800, color: primary ? onBrand : const Color(0xFFC9C5E6)))],
          ]),
        ),
      );

  Widget _avatar() {
    final path = _user?['avatar'] as String? ?? '';
    return Stack(clipBehavior: Clip.none, children: [
      Container(
        padding: const EdgeInsets.all(3),
        decoration: const BoxDecoration(shape: BoxShape.circle, gradient: SweepGradient(colors: [brand, cyan, brand])),
        child: Container(
          padding: const EdgeInsets.all(3),
          decoration: const BoxDecoration(color: Colors.black, shape: BoxShape.circle),
          child: CircleAvatar(
            radius: 39,
            backgroundColor: fieldFill,
            backgroundImage: path.isNotEmpty ? NetworkImage('$host$path') : null,
            child: path.isEmpty ? const Icon(Icons.person_rounded, size: 40, color: Colors.white54) : null,
          ),
        ),
      ),
      Positioned(
        bottom: 0,
        right: 0,
        child: Container(
          width: 28,
          height: 28,
          decoration: BoxDecoration(color: cyan, shape: BoxShape.circle, border: Border.all(color: Colors.black, width: 3)),
          child: const Icon(Icons.add, size: 16, color: Colors.black),
        ),
      ),
    ]);
  }

  @override
  Widget build(BuildContext context) {
    final u = _user;
    return GlowBackground(
      active: false,
      child: SafeArea(
        bottom: false,
        child: ListView(padding: const EdgeInsets.fromLTRB(16, 8, 16, 110), children: [
          Row(children: [
            _circle(Icons.edit_outlined),
            const Spacer(),
            _circle(Icons.notifications_none_rounded, badge: '٩+'),
            const SizedBox(width: 10),
            _circle(Icons.person_add_alt_1_outlined),
            const SizedBox(width: 10),
            _circle(Icons.menu_rounded, onTap: _menu),
          ]),
          const SizedBox(height: 14),
          ClipRRect(
            borderRadius: BorderRadius.circular(26),
            child: BackdropFilter(
              filter: ImageFilter.blur(sigmaX: 12, sigmaY: 12),
              child: Container(
                padding: const EdgeInsets.all(18),
                decoration: BoxDecoration(color: Colors.white.withOpacity(.07), borderRadius: BorderRadius.circular(26), border: Border.all(color: Colors.white.withOpacity(.15))),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Row(children: [
                    _avatar(),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Text(u?['name'] ?? '', style: const TextStyle(fontSize: 21, fontWeight: FontWeight.w800)),
                        const SizedBox(height: 2),
                        Text('@${u?['username'] ?? ''}', textDirection: TextDirection.ltr, style: const TextStyle(fontSize: 13.5, color: Color(0xCCFFFFFF))),
                      ]),
                    ),
                  ]),
                  const SizedBox(height: 14),
                  Container(
                    padding: const EdgeInsets.symmetric(vertical: 12),
                    decoration: BoxDecoration(color: Colors.black.withOpacity(.25), borderRadius: BorderRadius.circular(18)),
                    child: IntrinsicHeight(
                      child: Row(children: [
                        _stat(fmt(0), 'أتابعه'),
                        const VerticalDivider(color: Colors.white24, width: 1),
                        _stat(fmt(0), 'متابعين'),
                        const VerticalDivider(color: Colors.white24, width: 1),
                        _stat(fmt(_myVideos.fold<int>(0, (s, v) => s + (v['likes'] as int))), 'إعجابات'),
                      ]),
                    ),
                  ),
                  const SizedBox(height: 14),
                  Row(children: [
                    Expanded(flex: 14, child: _btn(Icons.edit_outlined, 'تعديل الملف', primary: true)),
                    const SizedBox(width: 8),
                    Expanded(flex: 10, child: _btn(Icons.account_balance_wallet_outlined, 'الرصيد')),
                    const SizedBox(width: 8),
                    SizedBox(width: 46, child: _btn(Icons.ios_share_rounded, '')),
                  ]),
                ]),
              ),
            ),
          ),
          const SizedBox(height: 16),
          // القصص المميزة
          SizedBox(
            height: 92,
            child: Row(children: [
              Column(children: [
                Container(
                  width: 62,
                  height: 62,
                  decoration: BoxDecoration(shape: BoxShape.circle, border: Border.all(color: Colors.white38, width: 2)),
                  child: const Icon(Icons.add, size: 28),
                ),
                const SizedBox(height: 7),
                const Text('جديد', style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
              ]),
            ]),
          ),
          // التبويبات
          SizedBox(
            height: 42,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: _tabs.length,
              separatorBuilder: (_, __) => const SizedBox(width: 6),
              itemBuilder: (_, i) => GestureDetector(
                onTap: () => setState(() => _tab = i),
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                  decoration: BoxDecoration(
                    color: i == _tab ? brand : Colors.transparent,
                    borderRadius: BorderRadius.circular(22),
                    border: Border.all(color: i == _tab ? brand : const Color(0xFF4C4A63), width: 1.5),
                  ),
                  child: Row(children: [
                    Icon(_tabs[i].$1, size: 16, color: i == _tab ? onBrand : const Color(0xFFC9C5E6)),
                    const SizedBox(width: 4),
                    Text(_tabs[i].$2, style: TextStyle(fontSize: 12, fontWeight: FontWeight.w700, color: i == _tab ? onBrand : const Color(0xFFC9C5E6))),
                  ]),
                ),
              ),
            ),
          ),
          const SizedBox(height: 14),
          if (_tab != 0 || _myVideos.isEmpty)
            const Padding(padding: EdgeInsets.symmetric(vertical: 40), child: Center(child: Text('ما فيه شي هنا بعد', style: TextStyle(color: Colors.white60))))
          else
            GridView.builder(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              itemCount: _myVideos.length,
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(crossAxisCount: 3, mainAxisSpacing: 8, crossAxisSpacing: 8, childAspectRatio: 3 / 4.2),
              itemBuilder: (_, i) => ClipRRect(
                borderRadius: BorderRadius.circular(14),
                child: Container(
                  decoration: const BoxDecoration(gradient: LinearGradient(begin: Alignment.topCenter, end: Alignment.bottomCenter, colors: [Color(0xFF355C7D), Color(0xFFC06C84)])),
                  alignment: Alignment.bottomRight,
                  padding: const EdgeInsets.all(8),
                  child: Row(mainAxisSize: MainAxisSize.min, children: [const Icon(Icons.play_arrow_rounded, size: 14), Text(fmt(_myVideos[i]['likes']), style: const TextStyle(fontSize: 11.5, fontWeight: FontWeight.w800))]),
                ),
              ),
            ),
        ]),
      ),
    );
  }
}

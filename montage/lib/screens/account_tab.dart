import 'package:flutter/material.dart';
import '../store.dart';
import '../theme.dart';
import 'auth.dart';
import 'player.dart';

class AccountTab extends StatefulWidget {
  final VoidCallback onChanged;
  const AccountTab({super.key, required this.onChanged});
  @override
  State<AccountTab> createState() => _AccountTabState();
}

class _AccountTabState extends State<AccountTab> {
  @override
  Widget build(BuildContext context) {
    final u = Store.user;
    final exports = Store.exports();
    final projects = Store.projects();
    return SafeArea(
      child: ListView(padding: const EdgeInsets.all(18), children: [
        const Text('الحساب', style: TextStyle(fontSize: 24, fontWeight: FontWeight.w800)),
        const SizedBox(height: 16),
        Container(
          padding: const EdgeInsets.all(18),
          decoration: BoxDecoration(gradient: C.grad, borderRadius: BorderRadius.circular(22)),
          child: Row(children: [
            CircleAvatar(radius: 30, backgroundColor: Colors.white24, child: Text((u?.name ?? '?').characters.first.toUpperCase(), style: const TextStyle(fontSize: 26, fontWeight: FontWeight.w800, color: Colors.white))),
            const SizedBox(width: 14),
            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(u?.name ?? '', style: const TextStyle(fontSize: 19, fontWeight: FontWeight.w800, color: Colors.white)),
              Text(u == null || u.guest ? 'حساب زائر' : u.email, style: const TextStyle(color: Colors.white70, fontSize: 13)),
            ])),
          ]),
        ),
        const SizedBox(height: 14),
        Row(children: [
          _stat('المشاريع', '${projects.length}'),
          const SizedBox(width: 10),
          _stat('المُصدَّرة', '${exports.length}'),
        ]),
        const SizedBox(height: 18),
        _section('الإعدادات'),
        Container(
          decoration: BoxDecoration(color: C.surface, borderRadius: BorderRadius.circular(16)),
          child: Column(children: [
            ListTile(
              leading: const Icon(Icons.high_quality_rounded, color: C.accent),
              title: const Text('جودة التصدير'),
              trailing: SegmentedButton<int>(
                segments: const [ButtonSegment(value: 720, label: Text('720p')), ButtonSegment(value: 1080, label: Text('1080p'))],
                selected: {Store.quality},
                onSelectionChanged: (s) async { await Store.setQuality(s.first); setState(() {}); },
                style: SegmentedButton.styleFrom(selectedBackgroundColor: C.accent),
              ),
            ),
            const Divider(height: 1, color: C.line),
            ListTile(
              leading: const Icon(Icons.cleaning_services_rounded, color: C.accent),
              title: const Text('مسح الملفات المؤقتة'),
              onTap: () async { await Store.clearCache(); if (mounted) toast(context, 'تم مسح الملفات المؤقتة'); },
            ),
          ]),
        ),
        const SizedBox(height: 18),
        _section('سجل التصدير'),
        if (exports.isEmpty)
          const Padding(padding: EdgeInsets.all(12), child: Text('لا يوجد سجل بعد', style: TextStyle(color: C.sub)))
        else
          Container(
            decoration: BoxDecoration(color: C.surface, borderRadius: BorderRadius.circular(16)),
            child: Column(children: [
              for (final r in exports.take(8))
                ListTile(
                  leading: const Icon(Icons.movie_creation_outlined, color: C.cyan),
                  title: Text(r.name),
                  subtitle: Text('${fmtDur(r.duration)} • ${r.at.year}/${r.at.month}/${r.at.day}', style: const TextStyle(color: C.sub, fontSize: 12)),
                  onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => PlayerPage(path: r.path, title: r.name))),
                ),
            ]),
          ),
        const SizedBox(height: 18),
        _section('عن التطبيق'),
        Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(color: C.surface, borderRadius: BorderRadius.circular(16)),
          child: const Text('مونتاج مرتاح — تطبيق مونتاج فيديو وصور مجاني بالكامل، بدون علامة مائية وبدون اشتراكات. كل المعالجة تتم على جهازك وملفاتك لا تُرفع لأي خادم.\nالإصدار 1.0.0', style: TextStyle(color: C.sub, height: 1.7)),
        ),
        const SizedBox(height: 18),
        OutlinedButton.icon(
          onPressed: () async {
            await Store.logout();
            if (!mounted) return;
            Navigator.of(context).pushAndRemoveUntil(MaterialPageRoute(builder: (_) => const AuthPage()), (_) => false);
          },
          icon: const Icon(Icons.logout, color: C.danger),
          label: const Text('تسجيل الخروج', style: TextStyle(color: C.danger)),
          style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(50), side: const BorderSide(color: C.danger), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14))),
        ),
      ]),
    );
  }

  Widget _stat(String l, String v) => Expanded(
        child: Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(color: C.surface, borderRadius: BorderRadius.circular(16)),
          child: Column(children: [Text(v, style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w800)), Text(l, style: const TextStyle(color: C.sub))]),
        ),
      );

  Widget _section(String t) => Padding(padding: const EdgeInsets.only(bottom: 8), child: Text(t, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w800)));
}

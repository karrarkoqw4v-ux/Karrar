import 'dart:io';
import 'package:flutter/material.dart';
import '../store.dart';
import '../theme.dart';
import 'auth.dart';
import 'player.dart';

class ProfileTab extends StatelessWidget {
  final VoidCallback onChanged;
  const ProfileTab({super.key, required this.onChanged});

  @override
  Widget build(BuildContext context) {
    final u = Store.user;
    final guest = u == null || u.guest;
    Widget row(IconData i, String t, VoidCallback f) => Tap(
          scale: 0.97,
          onTap: f,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 17),
            child: Row(children: [Icon(i, size: 28), const SizedBox(width: 14), Expanded(child: Text(t, style: tx(18, w: FontWeight.w800))), const Icon(Icons.chevron_left_rounded, color: K.sub, size: 28)]),
          ),
        );
    return SafeArea(
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(18, 8, 18, 0),
          child: Align(alignment: AlignmentDirectional.centerEnd, child: Tap(onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const SettingsPage())), child: const Padding(padding: EdgeInsets.all(8), child: Icon(Icons.settings_outlined, size: 30)))),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(22, 6, 22, 18),
          child: Row(children: [
            Container(width: 84, height: 84, decoration: BoxDecoration(color: guest ? const Color(0xFFE5E7EB) : K.ink, shape: BoxShape.circle), alignment: Alignment.center, child: guest ? const Icon(Icons.person_rounded, size: 50, color: Colors.white) : Text(u.name.characters.first, style: tx(36, w: FontWeight.w800, color: Colors.white))),
            const SizedBox(width: 16),
            Expanded(
              child: Tap(
                scale: 0.97,
                onTap: guest ? () => Navigator.of(context).pushAndRemoveUntil(MaterialPageRoute(builder: (_) => const AuthLanding()), (_) => false) : null,
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(guest ? 'انقر لتسجيل الدخول' : u.name, style: tx(22, w: FontWeight.w800)),
                  const SizedBox(height: 4),
                  Text(guest ? 'لحفظ اسمك وإعداداتك ›' : u.email, style: tx(14, color: K.sub)),
                ]),
              ),
            ),
          ]),
        ),
        row(Icons.history_rounded, 'عرض السجل', () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const HistoryPage()))),
        row(Icons.tune_rounded, 'الإعدادات', () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const SettingsPage()))),
        row(Icons.help_outline_rounded, 'مركز المساعدة', () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const HelpPage()))),
        if (!guest) row(Icons.logout_rounded, 'تسجيل الخروج', () async {
          final ok = await showDialog<bool>(context: context, builder: (_) => AlertDialog(title: Text('تسجيل الخروج', style: tx(18, w: FontWeight.w800)), content: Text('هل تريد تسجيل الخروج؟', style: tx(15, color: K.sub)), actions: [TextButton(onPressed: () => Navigator.pop(context, false), child: Text('إلغاء', style: tx(15, color: K.sub))), TextButton(onPressed: () => Navigator.pop(context, true), child: Text('خروج', style: tx(15, color: K.danger)))]));
          if (ok == true) {
            await Store.logout();
            if (context.mounted) Navigator.of(context).pushAndRemoveUntil(MaterialPageRoute(builder: (_) => const AuthLanding()), (_) => false);
          }
        }),
      ]),
    );
  }
}

class HistoryPage extends StatelessWidget {
  const HistoryPage({super.key});
  @override
  Widget build(BuildContext context) {
    final ex = Store.exports();
    return Scaffold(
      appBar: AppBar(title: Text('سجل التصدير', style: tx(18, w: FontWeight.w800))),
      body: ex.isEmpty
          ? Center(child: Text('لا يوجد سجل بعد', style: tx(15, color: K.sub)))
          : ListView(children: [
              for (final r in ex)
                ListTile(
                  leading: const Icon(Icons.movie_creation_outlined, size: 30),
                  title: Text(r.name, style: tx(17, w: FontWeight.w800)),
                  subtitle: Text('${fmtDur(r.duration)} • ${fmtSize(File(r.path).lengthSync())} • ${r.at.day}/${r.at.month}/${r.at.year}', style: tx(13, color: K.sub)),
                  onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => PlayerPage(path: r.path, title: r.name))),
                ),
            ]),
    );
  }
}

class SettingsPage extends StatefulWidget {
  const SettingsPage({super.key});
  @override
  State<SettingsPage> createState() => _SettingsPageState();
}

class _SettingsPageState extends State<SettingsPage> {
  @override
  Widget build(BuildContext context) {
    final o = Store.exportOptions;
    return Scaffold(
      appBar: AppBar(title: Text('الإعدادات', style: tx(18, w: FontWeight.w800))),
      body: ListView(children: [
        ListTile(
          leading: const Icon(Icons.high_quality_rounded, size: 28),
          title: Text('دقة التصدير الافتراضية', style: tx(16, w: FontWeight.w800)),
          trailing: DropdownButton<int>(
            value: o.res,
            underline: const SizedBox(),
            items: [for (final r in const [480, 720, 1080, 1440, 2160]) DropdownMenuItem(value: r, child: Text(r == 1440 ? '2K' : (r == 2160 ? '4K' : '${r}p'), style: tx(15)))],
            onChanged: (v) async { o.res = v!; await Store.saveExportOptions(o); setState(() {}); },
          ),
        ),
        ListTile(
          leading: const Icon(Icons.cleaning_services_rounded, size: 28),
          title: Text('مسح الملفات المؤقتة', style: tx(16, w: FontWeight.w800)),
          onTap: () async { await Store.clearCache(); if (context.mounted) toast(context, 'تم مسح الملفات المؤقتة'); },
        ),
        ListTile(
          leading: const Icon(Icons.info_outline_rounded, size: 28),
          title: Text('عن التطبيق', style: tx(16, w: FontWeight.w800)),
          subtitle: Text('مونتاجي 1.0.0 — مونتاج مجاني بدون علامة مائية. كل المعالجة على جهازك.', style: tx(13, color: K.sub, h: 1.6)),
        ),
      ]),
    );
  }
}

class HelpPage extends StatelessWidget {
  const HelpPage({super.key});
  @override
  Widget build(BuildContext context) {
    const faq = [
      ('كيف أبدأ مشروعاً؟', 'من تبويب «تعديل» اضغط «فيديو جديد» واختر فيديوهات أو صوراً من معرضك.'),
      ('كيف أستخدم قالباً؟', 'من تبويب «القوالب» اختر قالباً واضغط «استخدم القالب» ثم اختر صورك.'),
      ('كيف أضيف نصاً بخط عربي؟', 'في المحرر اضغط «النص» واختر الخط من القائمة (عربي وإنجليزي) والحركة والتوقيت.'),
      ('أين يُحفظ الفيديو؟', 'في ألبوم «مونتاجي» داخل معرض الجهاز وفي تبويب «المُصدَّرة» بالمكتبة.'),
      ('هل توجد علامة مائية؟', 'لا. التصدير بدون أي علامة مائية.'),
    ];
    return Scaffold(
      appBar: AppBar(title: Text('مركز المساعدة', style: tx(18, w: FontWeight.w800))),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        for (final f in faq)
          Container(
            margin: const EdgeInsets.only(bottom: 10),
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(color: K.fill, borderRadius: BorderRadius.circular(16)),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(f.$1, style: tx(16, w: FontWeight.w800)), const SizedBox(height: 6), Text(f.$2, style: tx(14, color: K.sub, h: 1.7))]),
          ),
      ]),
    );
  }
}

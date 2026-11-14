import 'dart:io';
import 'package:flutter/material.dart';
import '../media.dart';
import '../models.dart';
import '../store.dart';
import '../theme.dart';
import 'editor.dart';

class HomeTab extends StatelessWidget {
  final VoidCallback onChanged;
  final VoidCallback goDrafts;
  const HomeTab({super.key, required this.onChanged, required this.goDrafts});

  Future<void> _newProject(BuildContext context) async {
    final clips = await MediaImport.pick(context);
    if (clips.isEmpty) return;
    final p = Project(id: '${DateTime.now().millisecondsSinceEpoch}', name: 'مشروع ${Store.projects().length + 1}', clips: clips);
    await Store.saveProject(p);
    if (!context.mounted) return;
    await Navigator.of(context).push(MaterialPageRoute(builder: (_) => EditorPage(project: p)));
    onChanged();
  }

  @override
  Widget build(BuildContext context) {
    final u = Store.user;
    final recent = Store.projects().take(8).toList();
    return SafeArea(
      child: ListView(padding: const EdgeInsets.all(18), children: [
        Row(children: [
          ClipRRect(borderRadius: BorderRadius.circular(12), child: Image.asset('assets/logo.png', width: 42, height: 42)),
          const SizedBox(width: 10),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            const Text('مونتاج مرتاح', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w800)),
            Text('أهلاً ${u?.name ?? ''} 👋', style: const TextStyle(color: C.sub, fontSize: 13)),
          ])),
        ]),
        const SizedBox(height: 22),
        InkWell(
          onTap: () => _newProject(context),
          borderRadius: BorderRadius.circular(26),
          child: Ink(
            height: 190,
            decoration: BoxDecoration(gradient: C.grad, borderRadius: BorderRadius.circular(26), boxShadow: const [BoxShadow(color: Color(0x668B5CF6), blurRadius: 30, offset: Offset(0, 12))]),
            child: Stack(children: [
              Positioned(left: -30, top: -30, child: Container(width: 150, height: 150, decoration: BoxDecoration(shape: BoxShape.circle, color: Colors.white.withOpacity(.08)))),
              Positioned(right: -20, bottom: -40, child: Container(width: 170, height: 170, decoration: BoxDecoration(shape: BoxShape.circle, color: Colors.white.withOpacity(.08)))),
              Center(child: Column(mainAxisSize: MainAxisSize.min, children: [
                Container(width: 74, height: 74, decoration: const BoxDecoration(shape: BoxShape.circle, color: Colors.white), child: const Icon(Icons.add_rounded, size: 46, color: C.accent)),
                const SizedBox(height: 12),
                const Text('مشروع جديد', style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800, color: Colors.white)),
                const Text('استورد فيديو أو صور وابدأ التحرير', style: TextStyle(color: Colors.white70, fontSize: 13)),
              ])),
            ]),
          ),
        ),
        const SizedBox(height: 22),
        Row(children: [
          const Expanded(child: Text('مشاريعك الأخيرة', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w800))),
          TextButton(onPressed: goDrafts, child: const Text('عرض الكل')),
        ]),
        if (recent.isEmpty)
          Container(
            padding: const EdgeInsets.all(22),
            decoration: BoxDecoration(color: C.surface, borderRadius: BorderRadius.circular(18), border: Border.all(color: C.line)),
            child: const Center(child: Text('لا توجد مشاريع بعد. ابدأ مشروعك الأول!', style: TextStyle(color: C.sub))),
          )
        else
          SizedBox(
            height: 172,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: recent.length,
              separatorBuilder: (_, __) => const SizedBox(width: 12),
              itemBuilder: (_, i) => _ProjectCard(p: recent[i], onTap: () async {
                await Navigator.of(context).push(MaterialPageRoute(builder: (_) => EditorPage(project: recent[i])));
                onChanged();
              }),
            ),
          ),
        const SizedBox(height: 22),
        const Text('نصائح سريعة', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w800)),
        const SizedBox(height: 10),
        const _Tip('✂️', 'اضغط مطولاً على أي مقطع في الخط الزمني ثم اسحبه لإعادة الترتيب.'),
        const _Tip('🎞️', 'اضغط على الدائرة بين مقطعين لاختيار نوع الانتقال.'),
        const _Tip('🔤', 'النصوص العربية تُصدَّر متصلة وواضحة بكل الحركات.'),
      ]),
    );
  }
}

class _Tip extends StatelessWidget {
  final String e, t;
  const _Tip(this.e, this.t);
  @override
  Widget build(BuildContext context) => Container(
        margin: const EdgeInsets.only(bottom: 8),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(color: C.surface, borderRadius: BorderRadius.circular(14)),
        child: Row(children: [Text(e, style: const TextStyle(fontSize: 22)), const SizedBox(width: 10), Expanded(child: Text(t, style: const TextStyle(color: C.sub, height: 1.5)))]),
      );
}

class _ProjectCard extends StatelessWidget {
  final Project p;
  final VoidCallback onTap;
  const _ProjectCard({required this.p, required this.onTap});
  @override
  Widget build(BuildContext context) {
    final th = p.clips.isNotEmpty ? p.clips.first.thumb : null;
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(16),
      child: Container(
        width: 118,
        decoration: BoxDecoration(color: C.surface, borderRadius: BorderRadius.circular(16), border: Border.all(color: C.line)),
        child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Expanded(child: ClipRRect(borderRadius: const BorderRadius.vertical(top: Radius.circular(16)), child: th != null && File(th).existsSync() ? Image.file(File(th), fit: BoxFit.cover) : Container(color: C.surface2, child: const Icon(Icons.movie, color: C.sub)))),
          Padding(padding: const EdgeInsets.all(8), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(p.name, maxLines: 1, overflow: TextOverflow.ellipsis, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13)),
            Text('${p.clips.length} مقاطع • ${fmtDur(p.total)}', style: const TextStyle(color: C.sub, fontSize: 11)),
          ])),
        ]),
      ),
    );
  }
}

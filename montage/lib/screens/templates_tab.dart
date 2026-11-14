import 'package:flutter/material.dart';
import '../media.dart';
import '../models.dart';
import '../store.dart';
import '../filters.dart';
import '../templates.dart';
import '../theme.dart';
import 'editor.dart';

class TemplatesTab extends StatefulWidget {
  final VoidCallback onChanged;
  const TemplatesTab({super.key, required this.onChanged});
  @override
  State<TemplatesTab> createState() => _TemplatesTabState();
}

class _TemplatesTabState extends State<TemplatesTab> {
  String _cat = 'الكل';

  Future<void> _use(TemplateDef t) async {
    final clips = await MediaImport.pick(context);
    if (clips.isEmpty) return;
    for (final c in clips) {
      c.filter = t.filter;
      c.transition = t.transition;
      c.transitionDur = 0.45;
      if (c.isVideo) {
        final cap = t.imageSec * 2 < 3.0 ? 3.0 : t.imageSec * 2;
        if (c.end - c.start > cap) c.end = c.start + cap;
      } else {
        c.imageDur = t.imageSec;
      }
    }
    final p = Project(id: '${DateTime.now().millisecondsSinceEpoch}', name: 'قالب ${t.name}', clips: clips, template: t.id);
    if (t.title != null) {
      final end = p.total < 3.6 ? p.total : 3.6;
      p.overlays.add(OverlayItem(id: 'o${DateTime.now().microsecondsSinceEpoch}', text: t.title!, size: 110, position: t.titlePos, anim: t.titleAnim, start: 0.3, end: end < 0.8 ? 0.8 : end, box: false));
    }
    await Store.saveProject(p);
    if (!mounted) return;
    await Navigator.of(context).push(MaterialPageRoute(builder: (_) => EditorPage(project: p)));
    widget.onChanged();
  }

  void _details(TemplateDef t) {
    showModalBottomSheet(
      context: context,
      backgroundColor: C.surface,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(26))),
      builder: (_) => Padding(
        padding: const EdgeInsets.all(22),
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Container(height: 150, decoration: BoxDecoration(gradient: LinearGradient(colors: t.colors, begin: Alignment.topRight, end: Alignment.bottomLeft), borderRadius: BorderRadius.circular(22)), child: Center(child: Text(t.emoji, style: const TextStyle(fontSize: 64)))),
          const SizedBox(height: 14),
          Text(t.name, style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
          const SizedBox(height: 6),
          Text('فلتر: ${filterNameOf(t.filter)}  •  انتقال: ${transitionNameOf(t.transition)}  •  ${t.imageSec} ث لكل صورة', textAlign: TextAlign.center, style: const TextStyle(color: C.sub)),
          const SizedBox(height: 18),
          GradButton('استخدم القالب', icon: Icons.auto_awesome, onTap: () { Navigator.pop(context); _use(t); }),
          const SizedBox(height: 8),
        ]),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final list = kTemplates.where((t) => _cat == 'الكل' || t.category == _cat).toList();
    return SafeArea(
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        const Padding(padding: EdgeInsets.fromLTRB(18, 16, 18, 6), child: Text('القوالب', style: TextStyle(fontSize: 24, fontWeight: FontWeight.w800))),
        SizedBox(
          height: 48,
          child: ListView(scrollDirection: Axis.horizontal, padding: const EdgeInsets.symmetric(horizontal: 14), children: [
            for (final c in kTemplateCats)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 6),
                child: ChoiceChip(label: Text(c), selected: _cat == c, onSelected: (_) => setState(() => _cat = c), selectedColor: C.accent, backgroundColor: C.surface, labelStyle: TextStyle(color: _cat == c ? Colors.white : C.sub, fontWeight: FontWeight.w700), side: BorderSide.none),
              ),
          ]),
        ),
        Expanded(
          child: GridView.builder(
            padding: const EdgeInsets.all(16),
            gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(crossAxisCount: 2, mainAxisSpacing: 14, crossAxisSpacing: 14, childAspectRatio: .78),
            itemCount: list.length,
            itemBuilder: (_, i) {
              final t = list[i];
              return InkWell(
                onTap: () => _details(t),
                borderRadius: BorderRadius.circular(20),
                child: Ink(
                  decoration: BoxDecoration(gradient: LinearGradient(colors: t.colors, begin: Alignment.topRight, end: Alignment.bottomLeft), borderRadius: BorderRadius.circular(20)),
                  child: Stack(children: [
                    Positioned(top: 10, right: 10, child: Container(padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 3), decoration: BoxDecoration(color: Colors.black38, borderRadius: BorderRadius.circular(10)), child: Text(t.category, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700)))),
                    Center(child: Text(t.emoji, style: const TextStyle(fontSize: 58))),
                    Positioned(left: 0, right: 0, bottom: 0, child: Container(
                      padding: const EdgeInsets.all(12),
                      decoration: const BoxDecoration(borderRadius: BorderRadius.vertical(bottom: Radius.circular(20)), gradient: LinearGradient(colors: [Colors.transparent, Colors.black54], begin: Alignment.topCenter, end: Alignment.bottomCenter)),
                      child: Text(t.name, textAlign: TextAlign.center, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 15)),
                    )),
                  ]),
                ),
              );
            },
          ),
        ),
      ]),
    );
  }
}

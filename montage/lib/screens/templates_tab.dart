import 'package:flutter/material.dart';
import '../characters.dart';
import '../models.dart';
import '../store.dart';
import '../templates.dart';
import '../theme.dart';
import 'editor.dart';
import 'picker.dart';

class TemplatesTab extends StatefulWidget {
  final VoidCallback onChanged;
  const TemplatesTab({super.key, required this.onChanged});
  @override
  State<TemplatesTab> createState() => _TemplatesTabState();
}

class _TemplatesTabState extends State<TemplatesTab> with SingleTickerProviderStateMixin {
  late final AnimationController _clock = AnimationController(vsync: this, duration: const Duration(seconds: 600))..repeat();
  late final Animation<double> time = Tween<double>(begin: 0, end: 600).animate(_clock);
  String cat = 'الكل';
  String q = '';

  @override
  void dispose() {
    _clock.dispose();
    super.dispose();
  }

  Future<void> use(TemplateDef t) async {
    final clips = await pickMedia(context);
    if (clips == null || clips.isEmpty || !mounted) return;
    await applyTemplate(context, t, clips);
    widget.onChanged();
  }

  void details(TemplateDef t) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.white,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(26))),
      builder: (ctx) => DraggableScrollableSheet(
        expand: false,
        initialChildSize: .88,
        minChildSize: .5,
        maxChildSize: .95,
        builder: (_, sc) => Padding(
          padding: const EdgeInsets.fromLTRB(18, 14, 18, 18),
          child: Column(children: [
            Container(width: 42, height: 5, decoration: BoxDecoration(color: K.line, borderRadius: BorderRadius.circular(4))),
            const SizedBox(height: 14),
            Expanded(child: AspectRatio(aspectRatio: 9 / 16, child: ClipRRect(borderRadius: BorderRadius.circular(24), child: Stack(fit: StackFit.expand, children: [
              CharView(spec: t.char, time: time, seed: int.parse(t.id.substring(1))),
              Positioned(left: 0, right: 0, top: 36, child: Center(child: Text(t.title, textAlign: TextAlign.center, style: TextStyle(fontFamily: t.titleFont, fontFamilyFallback: const ['Tajawal'], fontSize: 34, fontWeight: FontWeight.w700, color: Colors.white, shadows: const [Shadow(color: Colors.black45, blurRadius: 8)])))),
            ])))),
            const SizedBox(height: 12),
            Text(t.name, style: tx(22, w: FontWeight.w800)),
            const SizedBox(height: 4),
            Text(t.category, style: tx(14, color: K.sub)),
            const SizedBox(height: 14),
            PillButton('استخدم القالب', icon: Icons.auto_awesome_rounded, fillColor: K.accent, onTap: () {
              Navigator.pop(ctx);
              use(t);
            }),
          ]),
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final list = kTemplates.where((t) => (cat == 'الكل' || t.category == cat) && (q.isEmpty || t.name.contains(q) || t.title.toLowerCase().contains(q.toLowerCase()))).toList();
    return SafeArea(
      child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(18, 12, 18, 6),
          child: Container(
            decoration: BoxDecoration(color: K.fill, borderRadius: BorderRadius.circular(14)),
            child: TextField(onChanged: (v) => setState(() => q = v), style: tx(15), decoration: InputDecoration(hintText: 'ابحث في ${kTemplates.length} قالباً', hintStyle: tx(15, w: FontWeight.w500, color: K.sub), prefixIcon: const Icon(Icons.search_rounded, color: K.ink), border: InputBorder.none, contentPadding: const EdgeInsets.symmetric(vertical: 14))),
          ),
        ),
        SizedBox(
          height: 52,
          child: ListView(scrollDirection: Axis.horizontal, padding: const EdgeInsets.symmetric(horizontal: 14), children: [
            for (final c in kTemplateCats)
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 8),
                child: Tap(
                  scale: 0.9,
                  onTap: () => setState(() => cat = c),
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 200),
                    padding: const EdgeInsets.symmetric(horizontal: 18),
                    alignment: Alignment.center,
                    decoration: BoxDecoration(borderRadius: BorderRadius.circular(20), border: Border.all(color: cat == c ? K.ink : K.line, width: cat == c ? 1.6 : 1)),
                    child: Text(c, style: tx(14, w: cat == c ? FontWeight.w800 : FontWeight.w700, color: cat == c ? K.ink : K.sub)),
                  ),
                ),
              ),
          ]),
        ),
        Expanded(
          child: list.isEmpty
              ? Center(child: Text('لا توجد قوالب مطابقة', style: tx(15, color: K.sub)))
              : GridView.builder(
                  padding: const EdgeInsets.fromLTRB(14, 8, 14, 14),
                  gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(crossAxisCount: 2, mainAxisSpacing: 14, crossAxisSpacing: 12, childAspectRatio: 0.58),
                  itemCount: list.length,
                  itemBuilder: (_, i) {
                    final t = list[i];
                    return Tap(
                      scale: 0.95,
                      onTap: () => details(t),
                      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                        Expanded(
                          child: ClipRRect(
                            borderRadius: BorderRadius.circular(16),
                            child: Stack(fit: StackFit.expand, children: [
                              CharView(spec: t.char, time: time, seed: int.parse(t.id.substring(1))),
                              Positioned(left: 8, right: 8, top: 26, child: Text(t.title, textAlign: TextAlign.center, maxLines: 2, overflow: TextOverflow.ellipsis, style: TextStyle(fontFamily: t.titleFont, fontFamilyFallback: const ['Tajawal'], fontSize: 20, fontWeight: FontWeight.w700, color: Colors.white, shadows: const [Shadow(color: Colors.black45, blurRadius: 6)]))),
                              Positioned(left: 8, bottom: 8, child: Container(padding: const EdgeInsets.all(5), decoration: BoxDecoration(color: Colors.black38, borderRadius: BorderRadius.circular(8)), child: const Icon(Icons.content_cut_rounded, size: 14, color: Colors.white))),
                            ]),
                          ),
                        ),
                        const SizedBox(height: 6),
                        Text(t.name, style: tx(15, w: FontWeight.w800), maxLines: 1, overflow: TextOverflow.ellipsis),
                        Text(t.category, style: tx(12, color: K.sub)),
                      ]),
                    );
                  },
                ),
        ),
      ]),
    );
  }
}

/// يطبّق القالب على الوسائط المختارة ويفتح المحرر
Future<void> applyTemplate(BuildContext context, TemplateDef t, List<MClip> clips) async {
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
  final p = Project(id: '${DateTime.now().millisecondsSinceEpoch}', name: Store.nextName(), clips: clips, template: t.id);
  final end = p.total < 3.6 ? p.total : 3.6;
  p.overlays.add(OverlayItem(id: 'o${DateTime.now().microsecondsSinceEpoch}', text: t.title, font: t.titleFont, size: 110, position: t.titlePos, anim: t.titleAnim, start: 0.3, end: end < 0.8 ? 0.8 : end));
  await Store.saveProject(p);
  if (!context.mounted) return;
  await Navigator.of(context).push(MaterialPageRoute(builder: (_) => EditorPage(project: p)));
}

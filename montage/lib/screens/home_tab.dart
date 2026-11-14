import 'dart:io';
import 'package:flutter/material.dart';
import '../models.dart';
import '../store.dart';
import '../theme.dart';
import 'editor.dart';
import 'picker.dart';

/// ينشئ مشروعاً من الوسائط المختارة ويفتح المحرر
Future<void> startProject(BuildContext context, List<MClip> clips, {bool photo = false, Tool? tool, VoidCallback? after, double? imageSec, String? transition}) async {
  if (clips.isEmpty) return;
  for (final c in clips) {
    if (!c.isVideo && imageSec != null) c.imageDur = imageSec;
    if (transition != null) c.transition = transition;
  }
  final p = Project(id: '${DateTime.now().millisecondsSinceEpoch}', name: Store.nextName(), clips: clips, photoMode: photo);
  await Store.saveProject(p);
  if (!context.mounted) return;
  await Navigator.of(context).push(MaterialPageRoute(builder: (_) => EditorPage(project: p, initialTool: tool)));
  after?.call();
}

class HomeTab extends StatelessWidget {
  final VoidCallback onChanged;
  final VoidCallback goLibrary;
  const HomeTab({super.key, required this.onChanged, required this.goLibrary});

  @override
  Widget build(BuildContext context) {
    final recent = Store.projects().take(10).toList();
    final tools = <(IconData, String, Tool?, PickKind, bool)>[
      (Icons.content_cut_rounded, 'قص', Tool.trim, PickKind.video, false),
      (Icons.auto_fix_high_rounded, 'فلاتر', Tool.filters, PickKind.both, false),
      (Icons.compare_rounded, 'انتقالات', Tool.transitions, PickKind.both, false),
      (Icons.title_rounded, 'نص', Tool.text, PickKind.both, false),
      (Icons.music_note_rounded, 'موسيقى', Tool.music, PickKind.both, false),
      (Icons.emoji_emotions_outlined, 'ملصقات', Tool.stickers, PickKind.both, false),
      (Icons.speed_rounded, 'سرعة', Tool.speed, PickKind.video, false),
      (Icons.volume_up_rounded, 'الصوت', Tool.volume, PickKind.video, false),
      (Icons.slideshow_rounded, 'عرض شرائح', null, PickKind.image, true),
    ];
    return SafeArea(
      child: ListView(padding: EdgeInsets.zero, children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(18, 10, 18, 4),
          child: Row(children: [
            Tap(onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const SearchProjectsPage())), child: Container(width: 46, height: 46, decoration: const BoxDecoration(color: K.fill, shape: BoxShape.circle), child: const Icon(Icons.search_rounded, size: 27))),
            const Spacer(),
            const LogoWord(size: 26),
            const SizedBox(width: 8),
            const AppLogo(size: 36),
          ]),
        ),
        const SizedBox(height: 8),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 18),
          child: Row(children: [
            Expanded(flex: 10, child: _BigCard(icon: Icons.photo_rounded, label: 'تعديل الصورة', onTap: () async {
              final l = await pickMedia(context, kind: PickKind.image, single: true);
              if (l != null && l.isNotEmpty && context.mounted) startProject(context, l, photo: true, after: onChanged);
            })),
            const SizedBox(width: 12),
            Expanded(flex: 14, child: _BigCard(icon: Icons.add_rounded, label: 'فيديو جديد', onTap: () async {
              final l = await pickMedia(context);
              if (l != null && l.isNotEmpty && context.mounted) startProject(context, l, after: onChanged);
            })),
          ]),
        ),
        Padding(
          padding: const EdgeInsets.fromLTRB(18, 22, 18, 8),
          child: Row(children: [Text('مشاريعك', style: tx(18, w: FontWeight.w800)), const Spacer(), Tap(onTap: goLibrary, child: Text('عرض الكل', style: tx(14, color: K.accentDark)))]),
        ),
        SizedBox(
          height: 118,
          child: recent.isEmpty
              ? Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 18),
                  child: Container(decoration: BoxDecoration(color: K.fill, borderRadius: BorderRadius.circular(16)), child: Center(child: Text('لا توجد مشاريع بعد — ابدأ بفيديو جديد', style: tx(14, color: K.sub)))),
                )
              : ListView.separated(
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.symmetric(horizontal: 18),
                  itemCount: recent.length,
                  separatorBuilder: (_, __) => const SizedBox(width: 10),
                  itemBuilder: (_, i) => _RecentThumb(p: recent[i], onTap: () async {
                    await Navigator.of(context).push(MaterialPageRoute(builder: (_) => EditorPage(project: recent[i])));
                    onChanged();
                  }),
                ),
        ),
        const SizedBox(height: 14),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 10),
          child: GridView.count(
            crossAxisCount: 3,
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            mainAxisSpacing: 6,
            childAspectRatio: 1.05,
            children: [
              for (final t in tools)
                Tap(
                  scale: 0.86,
                  onTap: () async {
                    final l = await pickMedia(context, kind: t.$4);
                    if (l == null || l.isEmpty || !context.mounted) return;
                    if (t.$5) {
                      startProject(context, l, imageSec: 2.5, transition: 'slide', after: onChanged);
                    } else {
                      startProject(context, l, tool: t.$3, after: onChanged);
                    }
                  },
                  child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
                    Icon(t.$1, size: 38, color: K.ink),
                    const SizedBox(height: 10),
                    Text(t.$2, style: tx(14), textAlign: TextAlign.center),
                  ]),
                ),
            ],
          ),
        ),
        const SizedBox(height: 20),
      ]),
    );
  }
}

class _BigCard extends StatelessWidget {
  final IconData icon;
  final String label;
  final VoidCallback onTap;
  const _BigCard({required this.icon, required this.label, required this.onTap});
  @override
  Widget build(BuildContext context) {
    return Tap(
      onTap: onTap,
      scale: 0.94,
      child: Container(
        height: 126,
        decoration: BoxDecoration(gradient: const LinearGradient(colors: [Color(0xFFDDF4F9), Color(0xFFBFE7F0)], begin: Alignment.topRight, end: Alignment.bottomLeft), borderRadius: BorderRadius.circular(22)),
        child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
          Container(width: 46, height: 46, decoration: BoxDecoration(color: K.ink, borderRadius: BorderRadius.circular(13)), child: Icon(icon, color: Colors.white, size: 30)),
          const SizedBox(height: 10),
          Text(label, style: tx(17, w: FontWeight.w800)),
        ]),
      ),
    );
  }
}

class _RecentThumb extends StatelessWidget {
  final Project p;
  final VoidCallback onTap;
  const _RecentThumb({required this.p, required this.onTap});
  @override
  Widget build(BuildContext context) {
    final th = p.clips.isNotEmpty ? p.clips.first.thumb : null;
    return Tap(
      onTap: onTap,
      scale: 0.92,
      child: Column(children: [
        Container(
          width: 84,
          height: 84,
          clipBehavior: Clip.hardEdge,
          decoration: BoxDecoration(color: K.fill, borderRadius: BorderRadius.circular(14)),
          child: Stack(fit: StackFit.expand, children: [
            if (th != null && File(th).existsSync()) Image.file(File(th), fit: BoxFit.cover) else const Icon(Icons.movie_rounded, color: K.sub),
            Positioned(left: 5, bottom: 4, child: Text(fmtDur(p.total), style: tx(11, color: Colors.white))),
            const Positioned(right: 5, bottom: 4, child: Icon(Icons.content_cut_rounded, size: 13, color: Colors.white)),
          ]),
        ),
        const SizedBox(height: 5),
        Text(p.name, style: tx(12, color: K.sub)),
      ]),
    );
  }
}

class SearchProjectsPage extends StatefulWidget {
  const SearchProjectsPage({super.key});
  @override
  State<SearchProjectsPage> createState() => _SearchProjectsPageState();
}

class _SearchProjectsPageState extends State<SearchProjectsPage> {
  String q = '';
  @override
  Widget build(BuildContext context) {
    final list = Store.projects().where((p) => q.isEmpty || p.name.contains(q)).toList();
    return Scaffold(
      appBar: AppBar(title: Text('بحث في المشاريع', style: tx(18, w: FontWeight.w800))),
      body: Column(children: [
        Padding(
          padding: const EdgeInsets.all(16),
          child: Container(
            decoration: BoxDecoration(color: K.fill, borderRadius: BorderRadius.circular(14)),
            child: TextField(autofocus: true, onChanged: (v) => setState(() => q = v), style: tx(16), decoration: InputDecoration(hintText: 'اكتب اسم المشروع', hintStyle: tx(16, w: FontWeight.w500, color: K.sub), prefixIcon: const Icon(Icons.search_rounded), border: InputBorder.none, contentPadding: const EdgeInsets.symmetric(vertical: 14))),
          ),
        ),
        Expanded(
          child: list.isEmpty
              ? Center(child: Text('لا نتائج', style: tx(15, color: K.sub)))
              : ListView(children: [
                  for (final p in list)
                    ListTile(
                      title: Text(p.name, style: tx(17, w: FontWeight.w800)),
                      subtitle: Text('${fmtDur(p.total)} • ${p.clips.length} مقاطع', style: tx(13, color: K.sub)),
                      onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => EditorPage(project: p))),
                    ),
                ]),
        ),
      ]),
    );
  }
}

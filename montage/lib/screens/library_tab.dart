import 'dart:io';
import 'package:flutter/material.dart';
import '../export.dart';
import '../models.dart';
import '../store.dart';
import '../theme.dart';
import 'editor.dart';
import 'home_tab.dart';
import 'picker.dart';
import 'player.dart';

class LibraryTab extends StatefulWidget {
  final VoidCallback onChanged;
  const LibraryTab({super.key, required this.onChanged});
  @override
  State<LibraryTab> createState() => _LibraryTabState();
}

class _LibraryTabState extends State<LibraryTab> {
  int tab = 0;
  bool selecting = false;
  final Set<String> sel = {};

  void _reload() => setState(() {});

  Future<void> _menu(Project p) async {
    await showModalBottomSheet(
      context: context,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(22))),
      builder: (ctx) => SafeArea(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          const SizedBox(height: 8),
          for (final a in [(Icons.edit_rounded, 'إعادة تسمية', 'rename'), (Icons.copy_rounded, 'تكرار', 'dup'), (Icons.delete_outline_rounded, 'حذف', 'del')])
            ListTile(
              leading: Icon(a.$1, color: a.$3 == 'del' ? K.danger : K.ink),
              title: Text(a.$2, style: tx(16, color: a.$3 == 'del' ? K.danger : K.ink)),
              onTap: () async {
                Navigator.pop(ctx);
                if (a.$3 == 'rename') {
                  final c = TextEditingController(text: p.name);
                  final v = await showDialog<String>(context: context, builder: (_) => AlertDialog(title: Text('اسم المشروع', style: tx(18, w: FontWeight.w800)), content: TextField(controller: c, autofocus: true, style: tx(16)), actions: [TextButton(onPressed: () => Navigator.pop(context), child: Text('إلغاء', style: tx(15, color: K.sub))), TextButton(onPressed: () => Navigator.pop(context, c.text), child: Text('حفظ', style: tx(15, color: K.accentDark)))]));
                  if (v != null && v.trim().isNotEmpty) {
                    p.name = v.trim();
                    await Store.saveProject(p);
                  }
                } else if (a.$3 == 'dup') {
                  final n = Project.fromJson(p.toJson())..id = '${DateTime.now().millisecondsSinceEpoch}'..name = Store.nextName();
                  await Store.saveProject(n);
                } else {
                  await Store.trashProject(p.id);
                  if (mounted) toast(context, 'نُقل إلى المحذوفات');
                }
                _reload();
                widget.onChanged();
              },
            ),
        ]),
      ),
    );
  }

  Widget _thumb(String? path, {double s = 76}) {
    final f = path != null && File(path).existsSync();
    return Container(width: s, height: s, clipBehavior: Clip.hardEdge, decoration: BoxDecoration(color: K.fill, borderRadius: BorderRadius.circular(12)), child: f ? Image.file(File(path), fit: BoxFit.cover) : const Icon(Icons.movie_rounded, color: K.sub));
  }

  @override
  Widget build(BuildContext context) {
    final projects = Store.projects();
    final exports = Store.exports();
    final trash = Store.trashed();
    final tabs = ['المشاريع', 'المُصدَّرة', 'المحذوفات'];
    return SafeArea(
      child: Stack(children: [
        Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(18, 10, 18, 0),
            child: Row(children: [
              Text('مكتبة', style: tx(28, w: FontWeight.w800)),
              const Spacer(),
              Tap(onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => const SearchProjectsPage())), child: const Padding(padding: EdgeInsets.all(8), child: Icon(Icons.search_rounded, size: 28))),
              if (tab == 0) Tap(onTap: () => setState(() { selecting = !selecting; sel.clear(); }), child: Padding(padding: const EdgeInsets.all(8), child: Text(selecting ? 'إلغاء' : 'تحديد', style: tx(15, color: K.accentDark)))),
            ]),
          ),
          Container(
            decoration: const BoxDecoration(border: Border(bottom: BorderSide(color: Color(0xFFEDEEF1)))),
            child: Row(children: [
              for (var i = 0; i < tabs.length; i++)
                Tap(
                  scale: 0.92,
                  onTap: () => setState(() { tab = i; selecting = false; sel.clear(); }),
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
                    decoration: BoxDecoration(border: Border(bottom: BorderSide(color: tab == i ? K.ink : Colors.transparent, width: 3))),
                    child: Text(tabs[i], style: tx(16, w: tab == i ? FontWeight.w800 : FontWeight.w700, color: tab == i ? K.ink : K.sub)),
                  ),
                ),
            ]),
          ),
          Expanded(child: _body(projects, exports, trash)),
        ]),
        if (tab == 0 && !selecting)
          Positioned(
            left: 16,
            bottom: 14,
            child: Tap(
              scale: 0.92,
              onTap: () async {
                final l = await pickMedia(context);
                if (l != null && l.isNotEmpty && context.mounted) await startProject(context, l, after: () { _reload(); widget.onChanged(); });
              },
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 22, vertical: 13),
                decoration: BoxDecoration(color: K.accent, borderRadius: BorderRadius.circular(28), boxShadow: [BoxShadow(color: K.accent.withValues(alpha: .45), blurRadius: 16, offset: const Offset(0, 6))]),
                child: Row(mainAxisSize: MainAxisSize.min, children: [const Icon(Icons.add_rounded, color: Colors.white, size: 26), const SizedBox(width: 6), Text('إنشاء', style: tx(17, w: FontWeight.w800, color: Colors.white))]),
              ),
            ),
          ),
        if (selecting && sel.isNotEmpty)
          Positioned(
            left: 16,
            right: 16,
            bottom: 14,
            child: PillButton('حذف (${sel.length})', icon: Icons.delete_outline_rounded, fillColor: K.danger, onTap: () async {
              for (final id in sel) {
                await Store.trashProject(id);
              }
              selecting = false;
              sel.clear();
              _reload();
              widget.onChanged();
            }),
          ),
      ]),
    );
  }

  Widget _body(List<Project> projects, List<ExportRec> exports, List<Project> trash) {
    if (tab == 0) {
      if (projects.isEmpty) return Center(child: Text('لا توجد مشاريع — اضغط «إنشاء»', style: tx(15, color: K.sub)));
      return ListView.builder(
        padding: const EdgeInsets.fromLTRB(0, 6, 0, 90),
        itemCount: projects.length,
        itemBuilder: (_, i) {
          final p = projects[i];
          final d = p.updatedAt;
          return Tap(
            scale: 0.97,
            onTap: () async {
              if (selecting) {
                setState(() => sel.contains(p.id) ? sel.remove(p.id) : sel.add(p.id));
              } else {
                await Navigator.of(context).push(MaterialPageRoute(builder: (_) => EditorPage(project: p)));
                _reload();
                widget.onChanged();
              }
            },
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 8),
              child: Row(children: [
                if (selecting) Padding(padding: const EdgeInsetsDirectional.only(end: 12), child: Icon(sel.contains(p.id) ? Icons.check_circle_rounded : Icons.radio_button_unchecked_rounded, color: sel.contains(p.id) ? K.accent : K.sub, size: 28)),
                _thumb(p.clips.isNotEmpty ? p.clips.first.thumb : null),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Text(p.name, style: tx(18, w: FontWeight.w800)),
                    const SizedBox(height: 3),
                    Text('${d.hour.toString().padLeft(2, '0')}:${d.minute.toString().padLeft(2, '0')} ${d.day.toString().padLeft(2, '0')}/${d.month.toString().padLeft(2, '0')}/${d.year}', style: tx(13, color: K.sub)),
                    const SizedBox(height: 2),
                    Row(children: [Text('${fmtDur(p.total)}  |  ${fmtSize(Store.projectSize(p))}', style: tx(13, color: K.sub)), const SizedBox(width: 6), const Icon(Icons.content_cut_rounded, size: 14, color: K.sub)]),
                  ]),
                ),
                if (!selecting) Tap(onTap: () => _menu(p), child: const Padding(padding: EdgeInsets.all(8), child: Icon(Icons.more_horiz_rounded, color: K.sub, size: 26))),
              ]),
            ),
          );
        },
      );
    }
    if (tab == 1) {
      if (exports.isEmpty) return Center(child: Text('لم تصدّر أي فيديو بعد', style: tx(15, color: K.sub)));
      return ListView.builder(
        padding: const EdgeInsets.only(bottom: 20),
        itemCount: exports.length,
        itemBuilder: (_, i) => _exportRow(exports[i]),
      );
    }
    if (trash.isEmpty) return Center(child: Text('سلة المحذوفات فارغة', style: tx(15, color: K.sub)));
    return ListView.builder(
      padding: const EdgeInsets.only(bottom: 20),
      itemCount: trash.length,
      itemBuilder: (_, i) {
        final p = trash[i];
        return Padding(
          padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 8),
          child: Row(children: [
            _thumb(p.clips.isNotEmpty ? p.clips.first.thumb : null),
            const SizedBox(width: 14),
            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(p.name, style: tx(18, w: FontWeight.w800)), Text(fmtDur(p.total), style: tx(13, color: K.sub))])),
            Tap(onTap: () async { await Store.restoreProject(p.id); _reload(); widget.onChanged(); }, child: Padding(padding: const EdgeInsets.all(8), child: Text('استعادة', style: tx(15, color: K.accentDark)))),
            Tap(onTap: () async { await Store.deleteForever(p.id); _reload(); }, child: const Padding(padding: EdgeInsets.all(8), child: Icon(Icons.delete_forever_rounded, color: K.danger))),
          ]),
        );
      },
    );
  }

  Widget _exportRow(ExportRec r) {
    return Tap(
      scale: 0.97,
      onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => PlayerPage(path: r.path, title: r.name))),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 8),
        child: Row(children: [
          FutureBuilder<String?>(future: r.path.endsWith('.jpg') ? Future.value(r.path) : Exporter.thumb(r.path), builder: (_, s) => _thumb(s.data)),
          const SizedBox(width: 14),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(r.name, style: tx(18, w: FontWeight.w800)),
            Text('${r.at.day.toString().padLeft(2, '0')}/${r.at.month.toString().padLeft(2, '0')}/${r.at.year}', style: tx(13, color: K.sub)),
            Text('${fmtDur(r.duration)}  |  ${fmtSize(File(r.path).lengthSync())}', style: tx(13, color: K.sub)),
          ])),
          Tap(onTap: () async { await Store.removeExport(r.path); _reload(); }, child: const Padding(padding: EdgeInsets.all(8), child: Icon(Icons.delete_outline_rounded, color: K.danger))),
        ]),
      ),
    );
  }
}

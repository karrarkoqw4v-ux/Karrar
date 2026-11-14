import 'dart:io';
import 'package:flutter/material.dart';
import '../export.dart';
import '../models.dart';
import '../store.dart';
import '../theme.dart';
import 'editor.dart';
import 'player.dart';

class DraftsTab extends StatefulWidget {
  final VoidCallback onChanged;
  const DraftsTab({super.key, required this.onChanged});
  @override
  State<DraftsTab> createState() => _DraftsTabState();
}

class _DraftsTabState extends State<DraftsTab> {
  @override
  Widget build(BuildContext context) {
    final projects = Store.projects();
    final exports = Store.exports();
    return SafeArea(
      child: DefaultTabController(
        length: 2,
        child: Column(children: [
          const Padding(padding: EdgeInsets.fromLTRB(18, 16, 18, 6), child: Align(alignment: AlignmentDirectional.centerStart, child: Text('المحفوظات', style: TextStyle(fontSize: 24, fontWeight: FontWeight.w800)))),
          TabBar(
            indicatorColor: C.accent,
            labelColor: C.text,
            unselectedLabelColor: C.sub,
            labelStyle: const TextStyle(fontFamily: 'Tajawal', fontWeight: FontWeight.w800),
            tabs: [Tab(text: 'المشاريع (${projects.length})'), Tab(text: 'المُصدَّرة (${exports.length})')],
          ),
          Expanded(
            child: TabBarView(children: [
              projects.isEmpty
                  ? const Center(child: Text('لا توجد مشاريع محفوظة', style: TextStyle(color: C.sub)))
                  : ListView.builder(
                      padding: const EdgeInsets.all(14),
                      itemCount: projects.length,
                      itemBuilder: (_, i) => _projectTile(projects[i]),
                    ),
              exports.isEmpty
                  ? const Center(child: Text('لم تصدّر أي فيديو بعد', style: TextStyle(color: C.sub)))
                  : ListView.builder(
                      padding: const EdgeInsets.all(14),
                      itemCount: exports.length,
                      itemBuilder: (_, i) => _exportTile(exports[i]),
                    ),
            ]),
          ),
        ]),
      ),
    );
  }

  Widget _thumb(String? path, {bool video = false}) {
    if (path != null && File(path).existsSync()) {
      return ClipRRect(borderRadius: BorderRadius.circular(12), child: Image.file(File(path), width: 64, height: 64, fit: BoxFit.cover));
    }
    if (video && path != null) {
      return FutureBuilder<String?>(
        future: Exporter.thumb(path),
        builder: (_, s) => s.data != null ? ClipRRect(borderRadius: BorderRadius.circular(12), child: Image.file(File(s.data!), width: 64, height: 64, fit: BoxFit.cover)) : _ph(),
      );
    }
    return _ph();
  }

  Widget _ph() => Container(width: 64, height: 64, decoration: BoxDecoration(color: C.surface2, borderRadius: BorderRadius.circular(12)), child: const Icon(Icons.movie, color: C.sub));

  Widget _projectTile(Project p) {
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      decoration: BoxDecoration(color: C.surface, borderRadius: BorderRadius.circular(16), border: Border.all(color: C.line)),
      child: ListTile(
        contentPadding: const EdgeInsets.all(10),
        leading: _thumb(p.clips.isNotEmpty ? p.clips.first.thumb : null),
        title: Text(p.name, style: const TextStyle(fontWeight: FontWeight.w800)),
        subtitle: Text('${p.clips.length} مقاطع • ${fmtDur(p.total)}\n${_fmtDate(p.updatedAt)}', style: const TextStyle(color: C.sub, fontSize: 12)),
        isThreeLine: true,
        onTap: () async {
          await Navigator.of(context).push(MaterialPageRoute(builder: (_) => EditorPage(project: p)));
          widget.onChanged();
        },
        trailing: PopupMenuButton<String>(
          color: C.surface2,
          onSelected: (v) async {
            if (v == 'del') {
              await Store.deleteProject(p.id);
              widget.onChanged();
            } else if (v == 'dup') {
              final n = Project.fromJson(p.toJson())..id = '${DateTime.now().millisecondsSinceEpoch}'..name = '${p.name} (نسخة)';
              await Store.saveProject(n);
              widget.onChanged();
            }
          },
          itemBuilder: (_) => const [PopupMenuItem(value: 'dup', child: Text('تكرار')), PopupMenuItem(value: 'del', child: Text('حذف', style: TextStyle(color: C.danger)))],
        ),
      ),
    );
  }

  Widget _exportTile(ExportRec r) {
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      decoration: BoxDecoration(color: C.surface, borderRadius: BorderRadius.circular(16), border: Border.all(color: C.line)),
      child: ListTile(
        contentPadding: const EdgeInsets.all(10),
        leading: _thumb(r.path, video: true),
        title: Text(r.name, style: const TextStyle(fontWeight: FontWeight.w800)),
        subtitle: Text('${fmtDur(r.duration)} • ${_fmtDate(r.at)}', style: const TextStyle(color: C.sub, fontSize: 12)),
        onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => PlayerPage(path: r.path, title: r.name))),
        trailing: IconButton(
          icon: const Icon(Icons.delete_outline, color: C.danger),
          onPressed: () async {
            await Store.removeExport(r.path);
            widget.onChanged();
          },
        ),
      ),
    );
  }

  String _fmtDate(DateTime d) => '${d.year}/${d.month.toString().padLeft(2, '0')}/${d.day.toString().padLeft(2, '0')} ${d.hour.toString().padLeft(2, '0')}:${d.minute.toString().padLeft(2, '0')}';
}

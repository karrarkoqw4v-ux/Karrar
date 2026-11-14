import 'dart:io';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:gal/gal.dart';
import '../custom.dart';
import '../models.dart';
import '../store.dart';
import '../theme.dart';
import 'picker.dart';
import 'player.dart';

/// المونتاج المخصص: قائمة الأدوات الخاصة
class CustomHub extends StatelessWidget {
  const CustomHub({super.key});
  @override
  Widget build(BuildContext context) {
    final groups = <String, List<JobDef>>{};
    for (final j in kJobs) {
      groups.putIfAbsent(j.group, () => []).add(j);
    }
    return Scaffold(
      appBar: AppBar(title: Text('المونتاج المخصص', style: tx(17, w: FontWeight.w800)), centerTitle: true),
      body: ListView(padding: const EdgeInsets.fromLTRB(16, 4, 16, 24), children: [
        Text('كل أداة تعمل على ملف واحد وتحفظ الناتج في معرض الجهاز. المعالجة برمجية على جهازك بدون ذكاء اصطناعي.', style: tx(13, color: K.sub, h: 1.7, w: FontWeight.w500)),
        for (final g in groups.entries) ...[
          Padding(padding: const EdgeInsets.fromLTRB(2, 20, 2, 8), child: Text(g.key, style: tx(15, w: FontWeight.w800))),
          for (final j in g.value)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Tap(
                scale: 0.97,
                onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => JobPage(job: j))),
                child: Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(color: K.fill, borderRadius: BorderRadius.circular(16)),
                  child: Row(children: [
                    Container(width: 44, height: 44, decoration: BoxDecoration(color: K.ink, borderRadius: BorderRadius.circular(12)), child: Icon(j.icon, color: Colors.white, size: 25)),
                    const SizedBox(width: 12),
                    Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text(j.title, style: tx(15, w: FontWeight.w800)),
                      const SizedBox(height: 2),
                      Text(j.sub, style: tx(12, color: K.sub, w: FontWeight.w500, h: 1.5)),
                    ])),
                    const Icon(Icons.chevron_left_rounded, color: K.sub),
                  ]),
                ),
              ),
            ),
        ],
      ]),
    );
  }
}

class JobPage extends StatefulWidget {
  final JobDef job;
  const JobPage({super.key, required this.job});
  @override
  State<JobPage> createState() => _JobPageState();
}

class _JobPageState extends State<JobPage> {
  MClip? clip;
  String? music;
  String? musicName;
  late final Map<String, String> vals = {for (final o in widget.job.opts) o.key: o.def};
  bool busy = false;
  double prog = 0;
  String? error;
  List<String> results = [];
  String savedMsg = '';

  Future<void> _pick() async {
    final l = await pickMedia(context, kind: widget.job.kind, single: true);
    if (l == null || l.isEmpty) return;
    setState(() { clip = l.first; results = []; error = null; });
  }

  Future<void> _pickMusic() async {
    final f = await FilePicker.pickFile(type: FileType.audio);
    if (f == null || f.path == null) return;
    setState(() { music = f.path; musicName = f.name; });
  }

  Future<void> _run() async {
    final c = clip;
    if (c == null) return;
    setState(() { busy = true; prog = 0; error = null; results = []; savedMsg = ''; });
    final r = await JobRunner.run(widget.job, c.path, c.isVideo, c.isVideo ? (c.end - c.start) : 0, vals, music: music, onProgress: (v) { if (mounted) setState(() => prog = v); });
    if (!mounted) return;
    if (r.files.isEmpty) {
      setState(() { busy = false; error = r.error ?? 'تعذر التنفيذ'; });
      return;
    }
    var saved = 'تم الحفظ';
    try {
      if (!await Gal.hasAccess(toAlbum: true)) await Gal.requestAccess(toAlbum: true);
      for (final f in r.files) {
        final e = f.split('.').last.toLowerCase();
        if (e == 'mp4') {
          await Gal.putVideo(f, album: 'مونتاجي');
        } else if (e == 'jpg' || e == 'png') {
          await Gal.putImage(f, album: 'مونتاجي');
        }
      }
      saved = 'حُفظ في معرض الجهاز (ألبوم مونتاجي)';
    } catch (_) {
      saved = 'تم الإنشاء. الملفات في مجلد التطبيق';
    }
    for (final f in r.files) {
      await Store.addExport(ExportRec(f, widget.job.title, DateTime.now(), 0, size: File(f).lengthSync()));
    }
    setState(() { busy = false; results = r.files; savedMsg = saved; });
  }

  String _sz(int b) => b > 1048576 ? '${(b / 1048576).toStringAsFixed(1)} MB' : '${(b / 1024).round()} KB';

  @override
  Widget build(BuildContext context) {
    final j = widget.job;
    final th = clip?.thumb;
    return Scaffold(
      appBar: AppBar(title: Text(j.title, style: tx(17, w: FontWeight.w800)), centerTitle: true),
      body: ListView(padding: const EdgeInsets.fromLTRB(16, 4, 16, 28), children: [
        Text(j.sub, style: tx(13, color: K.sub, h: 1.7, w: FontWeight.w500)),
        const SizedBox(height: 14),
        Tap(
          scale: 0.97,
          onTap: busy ? null : _pick,
          child: Container(
            height: 120,
            clipBehavior: Clip.hardEdge,
            decoration: BoxDecoration(color: const Color(0xFFDDF4F9), borderRadius: BorderRadius.circular(18)),
            child: clip == null
                ? Column(mainAxisAlignment: MainAxisAlignment.center, children: [
                    const Icon(Icons.add_photo_alternate_rounded, size: 34),
                    const SizedBox(height: 6),
                    Text('اختر الصورة أو الفيديو', style: tx(14)),
                  ])
                : Row(children: [
                    SizedBox(width: 120, height: 120, child: th != null && File(th).existsSync() ? Image.file(File(th), fit: BoxFit.cover) : const Icon(Icons.movie_rounded)),
                    const SizedBox(width: 12),
                    Expanded(child: Column(mainAxisAlignment: MainAxisAlignment.center, crossAxisAlignment: CrossAxisAlignment.start, children: [
                      Text(clip!.isVideo ? 'فيديو • ${fmtDur(clip!.end - clip!.start)}' : 'صورة', style: tx(14)),
                      const SizedBox(height: 4),
                      Text('اضغط لتغيير الملف', style: tx(12, color: K.sub, w: FontWeight.w500)),
                    ])),
                  ]),
          ),
        ),
        if (j.needsMusic) ...[
          const SizedBox(height: 10),
          Tap(
            scale: 0.97,
            onTap: busy ? null : _pickMusic,
            child: Container(
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
              decoration: BoxDecoration(color: K.fill, borderRadius: BorderRadius.circular(14)),
              child: Row(children: [
                const Icon(Icons.music_note_rounded, size: 22),
                const SizedBox(width: 10),
                Expanded(child: Text(musicName ?? 'اختر ملف الموسيقى', style: tx(14, w: musicName == null ? FontWeight.w500 : FontWeight.w800, color: musicName == null ? K.sub : K.ink), maxLines: 1, overflow: TextOverflow.ellipsis)),
              ]),
            ),
          ),
        ],
        for (final o in j.opts) ...[
          Padding(padding: const EdgeInsets.fromLTRB(2, 18, 2, 8), child: Text(o.label, style: tx(14, w: FontWeight.w800))),
          Wrap(spacing: 8, runSpacing: 8, children: [
            for (final c in o.choices)
              Builder(builder: (_) {
                final sel = o.multi ? vals[o.key]!.split(',').contains(c.$1) : vals[o.key] == c.$1;
                final isColor = o.key == 'color' || o.key == 'fcolor';
                return Tap(
                  scale: 0.9,
                  onTap: busy
                      ? null
                      : () => setState(() {
                            if (o.multi) {
                              final s = vals[o.key]!.split(',').where((e) => e.isNotEmpty).toSet();
                              if (!s.remove(c.$1)) s.add(c.$1);
                              vals[o.key] = s.join(',');
                            } else {
                              vals[o.key] = c.$1;
                            }
                          }),
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 160),
                    padding: EdgeInsets.symmetric(horizontal: isColor ? 10 : 14, vertical: 9),
                    decoration: BoxDecoration(color: sel ? K.ink : K.fill, borderRadius: BorderRadius.circular(22)),
                    child: Row(mainAxisSize: MainAxisSize.min, children: [
                      if (isColor) Container(width: 16, height: 16, margin: const EdgeInsetsDirectional.only(end: 6), decoration: BoxDecoration(color: Color(int.parse(c.$1) | 0xFF000000), shape: BoxShape.circle, border: Border.all(color: Colors.black26))),
                      Text(c.$2, style: tx(13, color: sel ? Colors.white : K.ink)),
                    ]),
                  ),
                );
              }),
          ]),
        ],
        const SizedBox(height: 24),
        if (busy)
          Column(children: [
            ClipRRect(borderRadius: BorderRadius.circular(6), child: LinearProgressIndicator(value: prog > 0 ? prog : null, minHeight: 8, color: K.accent, backgroundColor: K.fill)),
            const SizedBox(height: 8),
            Text('جارٍ المعالجة… ${(prog * 100).round()}%', style: tx(13, color: K.sub)),
          ])
        else
          PillButton(clip == null ? 'اختر ملفاً أولاً' : 'ابدأ', fillColor: K.accent, height: 50, onTap: clip == null || (j.needsMusic && music == null) ? null : _run),
        if (error != null) Padding(padding: const EdgeInsets.only(top: 12), child: Text(error!, style: tx(11, color: K.danger, w: FontWeight.w500), maxLines: 8, overflow: TextOverflow.ellipsis)),
        if (results.isNotEmpty) ...[
          const SizedBox(height: 16),
          Text(savedMsg, style: tx(13, color: K.accentDark)),
          const SizedBox(height: 8),
          for (final f in results)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Tap(
                scale: 0.97,
                onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => PlayerPage(path: f, title: j.title))),
                child: Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(color: K.fill, borderRadius: BorderRadius.circular(14)),
                  child: Row(children: [
                    const Icon(Icons.play_circle_fill_rounded, size: 30),
                    const SizedBox(width: 10),
                    Expanded(child: Text(f.split('/').last, style: tx(12, w: FontWeight.w600), maxLines: 1, overflow: TextOverflow.ellipsis)),
                    Text(_sz(File(f).lengthSync()), style: tx(12, color: K.sub)),
                  ]),
                ),
              ),
            ),
        ],
      ]),
    );
  }
}

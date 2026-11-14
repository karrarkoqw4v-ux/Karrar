import 'dart:io';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:gal/gal.dart';
import '../custom.dart';
import '../models.dart';
import '../store.dart';
import '../theme.dart';
import '../toolrun.dart';
import '../tools.dart';
import 'picker.dart';
import 'player.dart';

/// المونتاج المخصص: أقسام + بحث
class CustomHub extends StatefulWidget {
  const CustomHub({super.key});
  @override
  State<CustomHub> createState() => _CustomHubState();
}

class _CustomHubState extends State<CustomHub> {
  String q = '';

  @override
  Widget build(BuildContext context) {
    final all = kAllJobs;
    final secs = <String, List<JobDef>>{};
    for (final j in all) {
      secs.putIfAbsent(j.group, () => []).add(j);
    }
    final res = q.trim().isEmpty ? <JobDef>[] : all.where((j) => j.title.contains(q.trim()) || j.group.contains(q.trim())).toList();
    return Scaffold(
      appBar: AppBar(title: Text('المونتاج المخصص', style: tx(17, w: FontWeight.w800)), centerTitle: true),
      body: Column(children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 4, 16, 8),
          child: Container(
            decoration: BoxDecoration(color: K.fill, borderRadius: BorderRadius.circular(14)),
            child: TextField(
              onChanged: (v) => setState(() => q = v),
              style: tx(14),
              decoration: InputDecoration(hintText: 'ابحث في ${all.length} أداة', hintStyle: tx(14, w: FontWeight.w500, color: K.sub), prefixIcon: const Icon(Icons.search_rounded, size: 22), border: InputBorder.none, contentPadding: const EdgeInsets.symmetric(vertical: 12)),
            ),
          ),
        ),
        Expanded(
          child: q.trim().isNotEmpty
              ? (res.isEmpty ? Center(child: Text('لا نتائج', style: tx(14, color: K.sub))) : ListView(padding: const EdgeInsets.fromLTRB(16, 0, 16, 24), children: [for (final j in res) ToolRow(j)]))
              : GridView.count(
                  crossAxisCount: 2,
                  padding: const EdgeInsets.fromLTRB(16, 4, 16, 24),
                  mainAxisSpacing: 10,
                  crossAxisSpacing: 10,
                  childAspectRatio: 1.55,
                  children: [
                    for (final e in secs.entries)
                      Tap(
                        scale: 0.95,
                        onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => SectionPage(title: e.key, jobs: e.value))),
                        child: Container(
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(color: K.fill, borderRadius: BorderRadius.circular(18)),
                          child: Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
                            Container(width: 38, height: 38, decoration: BoxDecoration(color: K.ink, borderRadius: BorderRadius.circular(11)), child: Icon(kSectionIcons[e.key] ?? Icons.build_rounded, color: Colors.white, size: 22)),
                            Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                              Text(e.key, style: tx(13, w: FontWeight.w800), maxLines: 1, overflow: TextOverflow.ellipsis),
                              Text('${e.value.length} أداة', style: tx(11, color: K.sub, w: FontWeight.w500)),
                            ]),
                          ]),
                        ),
                      ),
                  ],
                ),
        ),
      ]),
    );
  }
}

class SectionPage extends StatelessWidget {
  final String title;
  final List<JobDef> jobs;
  const SectionPage({super.key, required this.title, required this.jobs});
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(title, style: tx(17, w: FontWeight.w800)), centerTitle: true),
      body: ListView(padding: const EdgeInsets.fromLTRB(16, 4, 16, 24), children: [for (final j in jobs) ToolRow(j)]),
    );
  }
}

class ToolRow extends StatelessWidget {
  final JobDef j;
  const ToolRow(this.j, {super.key});
  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Tap(
        scale: 0.97,
        onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => JobPage(job: j))),
        child: Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(color: K.fill, borderRadius: BorderRadius.circular(16)),
          child: Row(children: [
            Container(width: 40, height: 40, decoration: BoxDecoration(color: K.ink, borderRadius: BorderRadius.circular(12)), child: Icon(j.icon, color: Colors.white, size: 22)),
            const SizedBox(width: 12),
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(j.title, style: tx(14, w: FontWeight.w800)),
                if (j.sub.isNotEmpty) ...[const SizedBox(height: 2), Text(j.sub, style: tx(12, color: K.sub, w: FontWeight.w500, h: 1.5))],
              ]),
            ),
            const Icon(Icons.chevron_left_rounded, color: K.sub),
          ]),
        ),
      ),
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
  String? src; // مسار الصوت/GIF
  String? srcName;
  List<String> files = [];
  String? extra;
  String? extraName;
  String? music;
  String? musicName;
  late final Map<String, String> vals = {for (final o in widget.job.opts) o.key: o.def};
  final Map<String, TextEditingController> ctl = {};
  bool busy = false;
  double prog = 0;
  String? error;
  List<String> results = [];
  String savedMsg = '';

  JobDef get j => widget.job;
  bool get isNew => j.build != null;

  @override
  void initState() {
    super.initState();
    for (final o in j.opts) {
      if (o.text) ctl[o.key] = TextEditingController(text: o.def);
    }
  }

  @override
  void dispose() {
    for (final c in ctl.values) {
      c.dispose();
    }
    super.dispose();
  }

  bool get hasInput {
    switch (j.inp) {
      case In.multi:
        return files.isNotEmpty;
      case In.multiAudio:
        return files.length >= 2;
      case In.audio:
      case In.gif:
        return src != null;
      case In.av:
        return clip != null || src != null;
      default:
        return clip != null;
    }
  }

  bool get ready => hasInput && (!(j.extra == Extra.audio || j.extra == Extra.image) || extra != null) && (!j.needsMusic || music != null);

  Future<void> _pickMedia({bool multi = false}) async {
    final l = await pickMedia(context, kind: j.kind == PickKind.video && j.inp == In.av ? PickKind.video : j.kind, single: !multi);
    if (l == null || l.isEmpty) return;
    setState(() {
      if (multi) {
        files = l.map((e) => e.path).toList();
      } else {
        clip = l.first;
        src = null;
        srcName = null;
      }
      results = [];
      error = null;
    });
  }

  Future<void> _pickAudio({bool multi = false}) async {
    if (multi) {
      final l = await FilePicker.pickFiles(type: FileType.audio);
      final paths = l.map((e) => e.path).whereType<String>().toList();
      if (paths.isEmpty) return;
      setState(() => files = paths);
      return;
    }
    final f = await FilePicker.pickFile(type: FileType.audio);
    if (f == null || f.path == null) return;
    setState(() {
      src = f.path;
      srcName = f.name;
      clip = null;
      results = [];
    });
  }

  Future<void> _pickGif() async {
    final f = await FilePicker.pickFile(type: FileType.custom, allowedExtensions: const ['gif', 'webp']);
    if (f == null || f.path == null) return;
    setState(() {
      src = f.path;
      srcName = f.name;
      results = [];
    });
  }

  Future<void> _pickExtra() async {
    if (j.extra == Extra.audio) {
      final f = await FilePicker.pickFile(type: FileType.audio);
      if (f == null || f.path == null) return;
      setState(() { extra = f.path; extraName = f.name; });
    } else {
      final l = await pickMedia(context, kind: PickKind.image, single: true);
      if (l == null || l.isEmpty) return;
      setState(() { extra = l.first.path; extraName = 'صورة مختارة'; });
    }
  }

  Future<void> _pickMusic() async {
    final f = await FilePicker.pickFile(type: FileType.audio);
    if (f == null || f.path == null) return;
    setState(() { music = f.path; musicName = f.name; });
  }

  Future<void> _run() async {
    for (final e in ctl.entries) {
      vals[e.key] = e.value.text;
    }
    setState(() { busy = true; prog = 0; error = null; results = []; savedMsg = ''; });
    final JobResult r;
    if (isNew) {
      final c = clip;
      final path = c?.path ?? src;
      r = await ToolRunner.run(j, ToolInput(src: path, srcImage: c != null && !c.isVideo, files: files, extra: extra, p: vals), onProgress: (v) { if (mounted) setState(() => prog = v); });
    } else {
      final c = clip!;
      r = await JobRunner.run(j, c.path, c.isVideo, c.isVideo ? (c.end - c.start) : 0, vals, music: music, onProgress: (v) { if (mounted) setState(() => prog = v); });
    }
    if (!mounted) return;
    if (r.files.isEmpty) {
      setState(() { busy = false; error = r.error ?? 'تعذر التنفيذ'; });
      return;
    }
    var saved = 'تم الإنشاء';
    var gallery = 0;
    try {
      if (!await Gal.hasAccess(toAlbum: true)) await Gal.requestAccess(toAlbum: true);
      for (final f in r.files) {
        final e = f.split('.').last.toLowerCase();
        if (e == 'mp4' || e == 'mov' || e == 'webm' || e == 'mkv') {
          await Gal.putVideo(f, album: 'مونتاجي');
          gallery++;
        } else if (e == 'jpg' || e == 'png' || e == 'gif' || e == 'webp') {
          await Gal.putImage(f, album: 'مونتاجي');
          gallery++;
        }
      }
      if (gallery > 0) saved = 'حُفظ في معرض الجهاز (ألبوم مونتاجي)';
    } catch (_) {
      saved = 'تم الإنشاء. يمكنك حفظه بزر «حفظ كملف»';
    }
    for (final f in r.files) {
      await Store.addExport(ExportRec(f, j.title, DateTime.now(), 0, size: File(f).lengthSync()));
    }
    setState(() { busy = false; results = r.files; savedMsg = saved; });
  }

  Future<void> _saveAs(String f) async {
    try {
      final name = f.split('/').last;
      final u = await FilePicker.saveFile(fileName: name, bytes: await File(f).readAsBytes());
      if (u != null && mounted) toast(context, 'تم الحفظ');
    } catch (e) {
      if (mounted) toast(context, 'تعذر الحفظ');
    }
  }

  String _sz(int b) => b > 1048576 ? '${(b / 1048576).toStringAsFixed(1)} MB' : '${(b / 1024).round()} KB';

  Widget _pickBox({required IconData icon, required String text, String? sub, required VoidCallback onTap, String? thumb}) {
    return Tap(
      scale: 0.97,
      onTap: busy ? null : onTap,
      child: Container(
        height: 96,
        clipBehavior: Clip.hardEdge,
        decoration: BoxDecoration(color: const Color(0xFFDDF4F9), borderRadius: BorderRadius.circular(18)),
        child: Row(children: [
          if (thumb != null && File(thumb).existsSync()) SizedBox(width: 96, height: 96, child: Image.file(File(thumb), fit: BoxFit.cover)) else Padding(padding: const EdgeInsets.symmetric(horizontal: 18), child: Icon(icon, size: 32)),
          Expanded(
            child: Column(mainAxisAlignment: MainAxisAlignment.center, crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(text, style: tx(14), maxLines: 2, overflow: TextOverflow.ellipsis),
              if (sub != null) ...[const SizedBox(height: 3), Text(sub, style: tx(12, color: K.sub, w: FontWeight.w500))],
            ]),
          ),
        ]),
      ),
    );
  }

  Widget _inputs() {
    final children = <Widget>[];
    switch (j.inp) {
      case In.audio:
        children.add(_pickBox(icon: Icons.audiotrack_rounded, text: srcName ?? 'اختر ملف الصوت', sub: srcName == null ? null : 'اضغط للتغيير', onTap: _pickAudio));
        break;
      case In.av:
        children.add(Row(children: [
          Expanded(child: _pickBox(icon: Icons.movie_rounded, text: clip != null ? 'فيديو مختار' : 'اختر فيديو', sub: clip != null ? fmtDur(clip!.end - clip!.start) : null, thumb: clip?.thumb, onTap: _pickMedia)),
          const SizedBox(width: 10),
          Expanded(child: _pickBox(icon: Icons.audiotrack_rounded, text: srcName ?? 'أو ملف صوت', onTap: _pickAudio)),
        ]));
        break;
      case In.gif:
        children.add(_pickBox(icon: Icons.gif_box_rounded, text: srcName ?? 'اختر ملف GIF', sub: srcName == null ? null : 'اضغط للتغيير', onTap: _pickGif));
        break;
      case In.multi:
        children.add(_pickBox(icon: Icons.collections_rounded, text: files.isEmpty ? 'اختر الملفات بالترتيب' : 'تم اختيار ${files.length} ملفات', sub: files.isEmpty ? null : 'اضغط لإعادة الاختيار', onTap: () => _pickMedia(multi: true)));
        break;
      case In.multiAudio:
        children.add(_pickBox(icon: Icons.library_music_rounded, text: files.isEmpty ? 'اختر ملفين صوتيين أو أكثر' : 'تم اختيار ${files.length} ملفات', onTap: () => _pickAudio(multi: true)));
        break;
      default:
        children.add(_pickBox(
            icon: Icons.add_photo_alternate_rounded,
            text: clip == null ? (j.inp == In.image ? 'اختر الصورة' : (j.inp == In.video ? 'اختر الفيديو' : 'اختر الصورة أو الفيديو')) : (clip!.isVideo ? 'فيديو • ${fmtDur(clip!.end - clip!.start)}' : 'صورة'),
            sub: clip == null ? null : 'اضغط لتغيير الملف',
            thumb: clip?.thumb,
            onTap: _pickMedia));
    }
    if (j.extra != Extra.none) {
      final opt = j.extra == Extra.imageOpt;
      children.add(const SizedBox(height: 10));
      children.add(Tap(
        scale: 0.97,
        onTap: busy ? null : _pickExtra,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
          decoration: BoxDecoration(color: K.fill, borderRadius: BorderRadius.circular(14)),
          child: Row(children: [
            Icon(j.extra == Extra.audio ? Icons.music_note_rounded : Icons.image_rounded, size: 22),
            const SizedBox(width: 10),
            Expanded(child: Text(extraName ?? (j.extra == Extra.audio ? 'اختر ملف الصوت' : (opt ? 'اختر صورة (اختياري)' : 'اختر الصورة')), style: tx(14, w: extraName == null ? FontWeight.w500 : FontWeight.w800, color: extraName == null ? K.sub : K.ink), maxLines: 1, overflow: TextOverflow.ellipsis)),
          ]),
        ),
      ));
    }
    if (j.needsMusic) {
      children.add(const SizedBox(height: 10));
      children.add(Tap(
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
      ));
    }
    return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: children);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text(j.title, style: tx(16, w: FontWeight.w800)), centerTitle: true),
      body: ListView(padding: const EdgeInsets.fromLTRB(16, 4, 16, 28), children: [
        if (j.sub.isNotEmpty) Text(j.sub, style: tx(13, color: K.sub, h: 1.7, w: FontWeight.w500)),
        const SizedBox(height: 12),
        _inputs(),
        for (final o in j.opts) ...[
          Padding(padding: const EdgeInsets.fromLTRB(2, 18, 2, 8), child: Text(o.label, style: tx(14, w: FontWeight.w800))),
          if (o.text)
            Container(
              decoration: BoxDecoration(color: K.fill, borderRadius: BorderRadius.circular(14)),
              child: TextField(controller: ctl[o.key], style: tx(15), decoration: const InputDecoration(border: InputBorder.none, contentPadding: EdgeInsets.symmetric(horizontal: 14, vertical: 14))),
            )
          else
            Wrap(spacing: 8, runSpacing: 8, children: [
              for (final c in o.choices)
                Builder(builder: (_) {
                  final sel = o.multi ? vals[o.key]!.split(',').contains(c.$1) : vals[o.key] == c.$1;
                  final isColor = (o.key == 'color' || o.key == 'fcolor' || o.key == 'col' || o.key == 'c1' || o.key == 'c2' || o.key == 'bg') && c.$1.startsWith('0x') && !c.$1.contains(',');
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
          PillButton(!hasInput ? 'اختر الملف أولاً' : (ready ? 'ابدأ' : 'أكمل الاختيارات أعلاه'), fillColor: K.accent, height: 50, onTap: ready ? _run : null),
        if (error != null) Padding(padding: const EdgeInsets.only(top: 12), child: Text(error!, style: tx(11, color: K.danger, w: FontWeight.w500), maxLines: 8, overflow: TextOverflow.ellipsis)),
        if (results.isNotEmpty) ...[
          const SizedBox(height: 16),
          Text(savedMsg, style: tx(13, color: K.accentDark)),
          const SizedBox(height: 8),
          for (final f in results)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(color: K.fill, borderRadius: BorderRadius.circular(14)),
                child: Row(children: [
                  Tap(scale: 0.9, onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => PlayerPage(path: f, title: j.title))), child: const Icon(Icons.play_circle_fill_rounded, size: 32)),
                  const SizedBox(width: 10),
                  Expanded(child: Text('${f.split('/').last}\n${_sz(File(f).lengthSync())}', style: tx(11, w: FontWeight.w600, h: 1.5), maxLines: 2, overflow: TextOverflow.ellipsis)),
                  Tap(scale: 0.9, onTap: () => _saveAs(f), child: Container(padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8), decoration: BoxDecoration(color: K.ink, borderRadius: BorderRadius.circular(18)), child: Text('حفظ كملف', style: tx(12, color: Colors.white)))),
                ]),
              ),
            ),
        ],
      ]),
    );
  }
}

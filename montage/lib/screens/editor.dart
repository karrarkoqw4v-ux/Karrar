import 'dart:async';
import 'dart:io';
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:gal/gal.dart';
import 'package:video_player/video_player.dart';
import '../export.dart';
import '../filters.dart';
import '../media.dart';
import '../models.dart';
import '../store.dart';
import '../theme.dart';
import 'player.dart';

enum Tool { none, trim, filters, transitions, speed, volume, music }

const _textColors = [0xFFFFFFFF, 0xFF000000, 0xFFFACC15, 0xFFEF4444, 0xFF22C55E, 0xFF38BDF8, 0xFFA78BFA, 0xFFF472B6];
const _emojis = ['😀', '😂', '😍', '🥳', '😎', '🔥', '❤️', '⭐', '✨', '🎉', '🎊', '🎁', '👍', '👏', '💯', '🚀', '🌈', '☀️', '🌙', '🎵', '🎬', '📸', '🏆', '👑', '💎', '🌸', '🍕', '⚽', '🎮', '💥'];

class EditorPage extends StatefulWidget {
  final Project project;
  const EditorPage({super.key, required this.project});
  @override
  State<EditorPage> createState() => _EditorPageState();
}

class _EditorPageState extends State<EditorPage> {
  late final Project p = widget.project;
  int sel = 0;
  int cur = 0;
  VideoPlayerController? vc;
  VideoPlayerController? music;
  bool ready = false;
  bool playing = false;
  double local = 0;
  Timer? _timer;
  Tool tool = Tool.none;

  @override
  void initState() {
    super.initState();
    _timer = Timer.periodic(const Duration(milliseconds: 60), (_) => _tick());
    _load(0);
    _loadMusic();
  }

  @override
  void dispose() {
    _timer?.cancel();
    vc?.dispose();
    music?.dispose();
    Store.saveProject(p);
    super.dispose();
  }

  // ---------------- أزمنة ----------------
  List<double> _offs() {
    final r = <double>[];
    double acc = 0;
    for (var i = 0; i < p.clips.length; i++) {
      r.add(acc);
      acc += p.clips[i].effective - (i < p.clips.length - 1 ? p.joinDur(i) : 0);
    }
    return r;
  }

  double get globalT {
    if (p.clips.isEmpty) return 0;
    final o = _offs();
    return o[cur.clamp(0, o.length - 1)] + local;
  }

  void _save() => Store.saveProject(p);

  // ---------------- المعاينة ----------------
  Future<void> _load(int i, {double at = 0}) async {
    if (p.clips.isEmpty) {
      final old = vc;
      vc = null;
      ready = false;
      if (mounted) setState(() {});
      await old?.dispose();
      return;
    }
    i = i.clamp(0, p.clips.length - 1);
    final old = vc;
    vc = null;
    ready = false;
    cur = i;
    local = at;
    if (mounted) setState(() {});
    await old?.dispose();
    final c = p.clips[i];
    if (c.isVideo) {
      final nv = VideoPlayerController.file(File(c.path));
      vc = nv;
      try {
        await nv.initialize();
      } catch (_) {
        return;
      }
      if (!mounted || vc != nv) return;
      await nv.setVolume(c.volume.clamp(0.0, 1.0).toDouble());
      try {
        await nv.setPlaybackSpeed(c.speed);
      } catch (_) {}
      await nv.seekTo(Duration(milliseconds: ((c.start + at * c.speed) * 1000).round()));
      ready = true;
      if (playing) await nv.play();
    } else {
      ready = true;
    }
    if (mounted) setState(() {});
  }

  Future<void> _loadMusic() async {
    await music?.dispose();
    music = null;
    if (p.musicPath != null && File(p.musicPath!).existsSync()) {
      final m = VideoPlayerController.file(File(p.musicPath!));
      try {
        await m.initialize();
        await m.setLooping(true);
        await m.setVolume(p.musicVolume.clamp(0.0, 1.0).toDouble());
        music = m;
        if (playing) await m.play();
      } catch (_) {}
    }
    if (mounted) setState(() {});
  }

  void _tick() {
    if (!playing || !ready || p.clips.isEmpty || !mounted) return;
    final c = p.clips[cur.clamp(0, p.clips.length - 1)];
    if (c.isVideo) {
      final v = vc;
      if (v == null || !v.value.isInitialized) return;
      final pos = v.value.position.inMilliseconds / 1000.0;
      local = ((pos - c.start) / c.speed).clamp(0.0, c.effective).toDouble();
      if (pos >= c.end - 0.04) {
        _next();
        return;
      }
    } else {
      local += 0.06;
      if (local >= c.imageDur) {
        _next();
        return;
      }
    }
    setState(() {});
  }

  Future<void> _next() async {
    if (cur < p.clips.length - 1) {
      await _load(cur + 1);
    } else {
      playing = false;
      await vc?.pause();
      await music?.pause();
      if (mounted) setState(() {});
    }
  }

  Future<void> _togglePlay() async {
    if (p.clips.isEmpty) return;
    if (playing) {
      playing = false;
      await vc?.pause();
      await music?.pause();
    } else {
      playing = true;
      if (globalT >= p.total - 0.08) await _load(0);
      await vc?.play();
      await music?.play();
    }
    if (mounted) setState(() {});
  }

  Future<void> _seekGlobal(double t) async {
    final o = _offs();
    var i = 0;
    for (var k = 0; k < p.clips.length; k++) {
      if (t >= o[k]) i = k;
    }
    final at = (t - o[i]).clamp(0.0, p.clips[i].effective).toDouble();
    sel = i;
    await _load(i, at: at);
    final m = music;
    if (m != null && m.value.isInitialized && m.value.duration.inMilliseconds > 0) {
      await m.seekTo(Duration(milliseconds: (t * 1000).round() % m.value.duration.inMilliseconds));
    }
  }

  // ---------------- أدوات ----------------
  MClip? get selClip => p.clips.isEmpty ? null : p.clips[sel.clamp(0, p.clips.length - 1)];

  Future<void> _addClips() async {
    final l = await MediaImport.pick(context);
    if (l.isEmpty) return;
    setState(() {
      p.clips.addAll(l);
      sel = p.clips.length - l.length;
    });
    _save();
    if (p.clips.length == l.length) _load(0);
  }

  void _deleteSel() {
    if (p.clips.isEmpty) return;
    setState(() {
      p.clips.removeAt(sel);
      if (sel >= p.clips.length) sel = p.clips.length - 1;
      if (sel < 0) sel = 0;
    });
    _save();
    _load(sel);
  }

  void _dupSel() {
    final c = selClip;
    if (c == null) return;
    setState(() => p.clips.insert(sel + 1, c.copy()));
    _save();
  }

  void _pickTool(Tool t) {
    final c = selClip;
    if (c == null && t != Tool.music) return;
    setState(() => tool = tool == t ? Tool.none : t);
  }

  Future<void> _pickMusic() async {
    final r = await FilePicker.platform.pickFiles(type: FileType.audio);
    if (r == null || r.files.single.path == null) return;
    setState(() {
      p.musicPath = r.files.single.path;
      p.musicName = r.files.single.name;
    });
    _save();
    await _loadMusic();
  }

  // ---------------- النصوص والملصقات ----------------
  Future<void> _editOverlay({OverlayItem? existing, bool sticker = false}) async {
    final total = p.total <= 0.5 ? 0.5 : p.total;
    final o = existing ??
        OverlayItem(
          id: 'o${DateTime.now().microsecondsSinceEpoch}',
          sticker: sticker,
          text: sticker ? '🔥' : 'نص جديد',
          size: sticker ? 160 : 84,
          start: globalT.clamp(0.0, total).toDouble(),
          end: (globalT + 3).clamp(0.5, total).toDouble(),
          anim: sticker ? 'pop' : 'fade',
          position: sticker ? 1 : 2,
        );
    if (o.end <= o.start) o.end = (o.start + 1).clamp(0.5, total).toDouble();
    final ctrl = TextEditingController(text: o.text);
    bool delete = false;
    final ok = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      backgroundColor: C.surface,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(24))),
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setS) => Padding(
          padding: EdgeInsets.fromLTRB(18, 16, 18, MediaQuery.of(ctx).viewInsets.bottom + 16),
          child: SingleChildScrollView(
            child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              Text(o.sticker ? 'ملصق' : 'نص', textAlign: TextAlign.center, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
              const SizedBox(height: 12),
              if (!o.sticker) TextField(controller: ctrl, maxLines: 2, onChanged: (v) => o.text = v, decoration: const InputDecoration(labelText: 'النص'))
              else Wrap(spacing: 6, runSpacing: 6, children: [for (final e in _emojis) InkWell(onTap: () => setS(() => o.text = e), child: Container(padding: const EdgeInsets.all(6), decoration: BoxDecoration(color: o.text == e ? C.accent : C.surface2, borderRadius: BorderRadius.circular(10)), child: Text(e, style: const TextStyle(fontSize: 26))))]),
              const SizedBox(height: 12),
              if (!o.sticker) ...[
                Wrap(spacing: 8, children: [for (final c in _textColors) GestureDetector(onTap: () => setS(() => o.color = c), child: Container(width: 30, height: 30, decoration: BoxDecoration(color: Color(c), shape: BoxShape.circle, border: Border.all(color: o.color == c ? C.accent : C.line, width: o.color == c ? 3 : 1))))]),
                const SizedBox(height: 10),
                Wrap(spacing: 8, children: [for (final f in const [('Tajawal', 'تجوال'), ('serif', 'سيريف'), ('monospace', 'أحادي')]) ChoiceChip(label: Text(f.$2), selected: o.font == f.$1, onSelected: (_) => setS(() => o.font = f.$1))]),
                Row(children: [const Text('عريض'), Switch(value: o.bold, onChanged: (v) => setS(() => o.bold = v)), const SizedBox(width: 12), const Text('خلفية'), Switch(value: o.box, onChanged: (v) => setS(() => o.box = v))]),
              ],
              Row(children: [const Text('الحجم'), Expanded(child: Slider(value: o.size.clamp(30.0, 240.0).toDouble(), min: 30, max: 240, onChanged: (v) => setS(() => o.size = v)))]),
              Row(children: [
                const Text('الموضع  '),
                Expanded(child: SegmentedButton<int>(segments: const [ButtonSegment(value: 0, label: Text('أعلى')), ButtonSegment(value: 1, label: Text('وسط')), ButtonSegment(value: 2, label: Text('أسفل'))], selected: {o.position}, onSelectionChanged: (s) => setS(() => o.position = s.first))),
              ]),
              const SizedBox(height: 8),
              Wrap(spacing: 8, children: [for (final a in const [('none', 'بدون'), ('fade', 'تلاشي'), ('slide', 'انزلاق لأعلى'), ('pop', 'قفزة'), ('type', 'كتابة تدريجية')]) if (!(o.sticker && a.$1 == 'type')) ChoiceChip(label: Text(a.$2), selected: o.anim == a.$1, onSelected: (_) => setS(() => o.anim = a.$1))]),
              const SizedBox(height: 6),
              Text('التوقيت: ${fmtDur(o.start)}  →  ${fmtDur(o.end)}', style: const TextStyle(color: C.sub)),
              RangeSlider(values: RangeValues(o.start.clamp(0.0, total).toDouble(), o.end.clamp(0.0, total).toDouble()), min: 0, max: total, onChanged: (r) => setS(() { o.start = r.start; o.end = r.end; })),
              const SizedBox(height: 6),
              Row(children: [
                if (existing != null) Expanded(child: OutlinedButton(onPressed: () { delete = true; Navigator.pop(ctx, true); }, style: OutlinedButton.styleFrom(foregroundColor: C.danger, side: const BorderSide(color: C.danger)), child: const Text('حذف'))),
                if (existing != null) const SizedBox(width: 10),
                Expanded(flex: 2, child: GradButton('حفظ', onTap: () => Navigator.pop(ctx, true), height: 48)),
              ]),
            ]),
          ),
        ),
      ),
    );
    if (ok != true) return;
    setState(() {
      if (delete) {
        p.overlays.removeWhere((x) => x.id == o.id);
      } else if (existing == null) {
        if (o.text.trim().isEmpty) return;
        p.overlays.add(o);
      }
    });
    _save();
  }

  // ---------------- التصدير ----------------
  Future<void> _export() async {
    if (p.clips.isEmpty) {
      toast(context, 'أضف مقاطع أولاً');
      return;
    }
    if (playing) await _togglePlay();
    final prog = ValueNotifier<double>(0);
    final logs = ValueNotifier<String>('');
    bool done = false;
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (_) => PopScope(
        canPop: false,
        child: AlertDialog(
          backgroundColor: C.surface,
          title: const Text('جاري التصدير…'),
          content: ValueListenableBuilder<double>(
            valueListenable: prog,
            builder: (_, v, __) => Column(mainAxisSize: MainAxisSize.min, children: [
              LinearProgressIndicator(value: v == 0 ? null : v, minHeight: 8, borderRadius: BorderRadius.circular(8), color: C.accent),
              const SizedBox(height: 12),
              Text('${(v * 100).toStringAsFixed(0)}%', style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w800)),
              const SizedBox(height: 6),
              const Text('لا تغلق التطبيق أثناء المعالجة', style: TextStyle(color: C.sub, fontSize: 12)),
            ]),
          ),
        ),
      ),
    );
    final res = await Exporter.run(p, quality: Store.quality, onProgress: (v) => prog.value = v, onLog: (l) => logs.value = l);
    done = true;
    if (!mounted) return;
    Navigator.of(context, rootNavigator: true).pop();
    if (res.path == null) {
      showDialog(
        context: context,
        builder: (_) => AlertDialog(
          backgroundColor: C.surface,
          title: const Text('فشل التصدير'),
          content: SingleChildScrollView(child: Text(res.error ?? 'خطأ غير معروف', style: const TextStyle(fontSize: 11, color: C.sub), textDirection: TextDirection.ltr)),
          actions: [TextButton(onPressed: () => Navigator.pop(context), child: const Text('إغلاق'))],
        ),
      );
      return;
    }
    String saved = 'حُفظ في المحفوظات';
    try {
      if (!await Gal.hasAccess(toAlbum: true)) await Gal.requestAccess(toAlbum: true);
      await Gal.putVideo(res.path!, album: 'مونتاج مرتاح');
      saved = 'حُفظ في معرض الجهاز (ألبوم مونتاج مرتاح) وفي المحفوظات';
    } catch (_) {}
    await Store.addExport(ExportRec(res.path!, p.name, DateTime.now(), p.total));
    if (!mounted) return;
    showDialog(
      context: context,
      builder: (_) => AlertDialog(
        backgroundColor: C.surface,
        title: const Text('تم التصدير ✅'),
        content: Text('$saved\nبدون علامة مائية.', style: const TextStyle(height: 1.7)),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context), child: const Text('إغلاق')),
          FilledButton(onPressed: () { Navigator.pop(context); Navigator.of(context).push(MaterialPageRoute(builder: (_) => PlayerPage(path: res.path!, title: p.name))); }, child: const Text('مشاهدة')),
        ],
      ),
    );
    if (done) {}
  }

  Future<void> _rename() async {
    final c = TextEditingController(text: p.name);
    final v = await showDialog<String>(
      context: context,
      builder: (_) => AlertDialog(
        backgroundColor: C.surface,
        title: const Text('اسم المشروع'),
        content: TextField(controller: c, autofocus: true),
        actions: [TextButton(onPressed: () => Navigator.pop(context), child: const Text('إلغاء')), FilledButton(onPressed: () => Navigator.pop(context, c.text), child: const Text('حفظ'))],
      ),
    );
    if (v != null && v.trim().isNotEmpty) {
      setState(() => p.name = v.trim());
      _save();
    }
  }

  // ---------------- واجهة ----------------
  @override
  Widget build(BuildContext context) {
    return PopScope(
      onPopInvokedWithResult: (_, __) => _save(),
      child: Scaffold(
        appBar: AppBar(
          leading: IconButton(icon: const Icon(Icons.arrow_forward_ios_rounded), onPressed: () => Navigator.pop(context)),
          title: GestureDetector(onTap: _rename, child: Row(mainAxisSize: MainAxisSize.min, children: [Flexible(child: Text(p.name, overflow: TextOverflow.ellipsis)), const SizedBox(width: 4), const Icon(Icons.edit, size: 14, color: C.sub)])),
          actions: [
            Padding(
              padding: const EdgeInsetsDirectional.only(end: 12),
              child: GestureDetector(
                onTap: _export,
                child: Container(padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8), decoration: BoxDecoration(gradient: C.grad, borderRadius: BorderRadius.circular(12)), child: const Text('تصدير', style: TextStyle(fontWeight: FontWeight.w800, color: Colors.white))),
              ),
            ),
          ],
        ),
        body: Column(children: [
          Expanded(child: Center(child: _previewBox())),
          _playBar(),
          _timeline(),
          _overlayTrack(),
          if (tool != Tool.none) _panel(),
          _toolbar(),
        ]),
      ),
    );
  }

  Widget _previewBox() {
    return LayoutBuilder(builder: (context, cons) {
      var h = cons.maxHeight - 8;
      var w = h * 9 / 16;
      if (w > cons.maxWidth - 24) {
        w = cons.maxWidth - 24;
        h = w * 16 / 9;
      }
      final scale = w / 1080.0;
      final c = selClip == null || p.clips.isEmpty ? null : p.clips[cur.clamp(0, p.clips.length - 1)];
      Widget media;
      if (c == null) {
        media = const Center(child: Text('أضف مقاطع للبدء', style: TextStyle(color: C.sub)));
      } else if (c.isVideo) {
        final v = vc;
        media = (v != null && v.value.isInitialized)
            ? FittedBox(fit: BoxFit.cover, clipBehavior: Clip.hardEdge, child: SizedBox(width: v.value.size.width, height: v.value.size.height, child: VideoPlayer(v)))
            : const Center(child: CircularProgressIndicator());
      } else {
        media = Image.file(File(c.path), fit: BoxFit.cover, width: w, height: h);
      }
      final m = c == null ? null : filterById(c.filter).matrix;
      if (m != null) media = ColorFiltered(colorFilter: ColorFilter.matrix(m), child: media);
      final t = globalT;
      return Container(
        width: w,
        height: h,
        clipBehavior: Clip.hardEdge,
        decoration: BoxDecoration(color: Colors.black, borderRadius: BorderRadius.circular(14)),
        child: Stack(fit: StackFit.expand, children: [
          media,
          for (final o in p.overlays) if (t >= o.start && t <= o.end) _overlayView(o, t, scale),
          if (c != null) Positioned(top: 8, left: 8, child: Container(padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2), decoration: BoxDecoration(color: Colors.black54, borderRadius: BorderRadius.circular(8)), child: Text('${cur + 1}/${p.clips.length}', style: const TextStyle(fontSize: 11)))),
        ]),
      );
    });
  }

  Widget _overlayView(OverlayItem o, double t, double scale) {
    final a = t - o.start;
    final d = o.end - o.start;
    double opacity = 1;
    double dy = 0;
    double sc = 1;
    String text = o.text;
    switch (o.anim) {
      case 'fade':
        final fd = d / 2 < 0.35 ? d / 2 : 0.35;
        opacity = (a / (fd <= 0 ? 1 : fd)).clamp(0.0, 1.0) * ((o.end - t) / (fd <= 0 ? 1 : fd)).clamp(0.0, 1.0);
        break;
      case 'slide':
        dy = (1 - (a / 0.4).clamp(0.0, 1.0)) * 350 * scale;
        break;
      case 'pop':
        sc = 0.55 + 0.45 * (a / 0.25).clamp(0.0, 1.0);
        break;
      case 'type':
        final chars = o.text.runes.toList();
        final n = (chars.length * (a / (d * 0.6)).clamp(0.0, 1.0)).ceil();
        text = String.fromCharCodes(chars.take(n));
        break;
    }
    final ay = o.position == 0 ? -0.72 : (o.position == 1 ? 0.0 : 0.64);
    final style = TextStyle(
      fontFamily: o.font == 'Tajawal' ? 'Tajawal' : (o.font == 'serif' ? 'serif' : 'monospace'),
      fontSize: o.size * scale,
      color: Color(o.color),
      fontWeight: o.bold ? FontWeight.w800 : FontWeight.w500,
      height: 1.25,
      shadows: o.box || o.sticker ? null : const [Shadow(color: Color(0xCC000000), blurRadius: 4, offset: Offset(0, 1))],
    );
    return Align(
      alignment: Alignment(0, ay),
      child: Opacity(
        opacity: opacity,
        child: Transform.translate(
          offset: Offset(0, dy),
          child: Transform.scale(
            scale: sc,
            child: Container(
              constraints: BoxConstraints(maxWidth: 1080 * scale * 0.88),
              padding: EdgeInsets.symmetric(horizontal: (o.box ? 28 : 8) * scale, vertical: (o.box ? 14 : 4) * scale),
              decoration: o.box && !o.sticker ? BoxDecoration(color: Colors.black54, borderRadius: BorderRadius.circular(18 * scale)) : null,
              child: Text(text, textAlign: TextAlign.center, style: style),
            ),
          ),
        ),
      ),
    );
  }

  Widget _playBar() {
    final total = p.total;
    final t = globalT.clamp(0.0, total <= 0 ? 0.0 : total).toDouble();
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8),
      child: Row(children: [
        IconButton.filled(style: IconButton.styleFrom(backgroundColor: C.accent), onPressed: _togglePlay, icon: Icon(playing ? Icons.pause_rounded : Icons.play_arrow_rounded)),
        Text(fmtDur(t), style: const TextStyle(fontSize: 12, color: C.sub)),
        Expanded(child: Slider(value: total <= 0 ? 0 : t, max: total <= 0 ? 1 : total, onChanged: total <= 0 ? null : (v) => _seekGlobal(v))),
        Text(fmtDur(total), style: const TextStyle(fontSize: 12, color: C.sub)),
      ]),
    );
  }

  Widget _timeline() {
    return SizedBox(
      height: 96,
      child: p.clips.isEmpty
          ? Center(child: TextButton.icon(onPressed: _addClips, icon: const Icon(Icons.add_circle_outline), label: const Text('إضافة مقاطع')))
          : ReorderableListView.builder(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
              buildDefaultDragHandles: false,
              itemCount: p.clips.length,
              onReorder: (a, b) {
                setState(() {
                  if (b > a) b -= 1;
                  final c = p.clips.removeAt(a);
                  p.clips.insert(b, c);
                  sel = b;
                });
                _save();
                _load(0);
              },
              itemBuilder: (_, i) {
                final c = p.clips[i];
                final w = (c.effective * 22).clamp(76.0, 190.0).toDouble();
                final isSel = i == sel;
                return ReorderableDelayedDragStartListener(
                  key: ValueKey(c.id),
                  index: i,
                  child: Row(children: [
                    GestureDetector(
                      onTap: () { setState(() => sel = i); _seekGlobal(_offs()[i]); },
                      child: Container(
                        width: w,
                        height: 80,
                        clipBehavior: Clip.hardEdge,
                        decoration: BoxDecoration(borderRadius: BorderRadius.circular(10), border: Border.all(color: isSel ? C.accent : C.line, width: isSel ? 3 : 1), color: C.surface2),
                        child: Stack(fit: StackFit.expand, children: [
                          if (c.thumb != null && File(c.thumb!).existsSync()) ColorFiltered(colorFilter: ColorFilter.matrix(filterById(c.filter).matrix ?? const [1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0]), child: Image.file(File(c.thumb!), fit: BoxFit.cover)) else const Icon(Icons.movie, color: C.sub),
                          Positioned(bottom: 3, left: 3, child: Container(padding: const EdgeInsets.symmetric(horizontal: 5, vertical: 1), decoration: BoxDecoration(color: Colors.black54, borderRadius: BorderRadius.circular(6)), child: Text('${c.effective.toStringAsFixed(1)}s${c.speed != 1 ? '  ×${c.speed}' : ''}', style: const TextStyle(fontSize: 10)))),
                          if (!c.isVideo) const Positioned(top: 3, right: 3, child: Icon(Icons.image, size: 14)),
                        ]),
                      ),
                    ),
                    if (i < p.clips.length - 1)
                      GestureDetector(
                        onTap: () => setState(() { sel = i; tool = Tool.transitions; }),
                        child: Container(
                          margin: const EdgeInsets.symmetric(horizontal: 3),
                          width: 26,
                          height: 26,
                          decoration: BoxDecoration(color: C.surface2, shape: BoxShape.circle, border: Border.all(color: C.accent)),
                          child: Center(child: Text(transitionById(c.transition).icon, style: const TextStyle(fontSize: 13))),
                        ),
                      ),
                  ]),
                );
              },
              footer: Padding(padding: const EdgeInsets.only(left: 6), child: Center(child: IconButton.filledTonal(onPressed: _addClips, icon: const Icon(Icons.add)))),
            ),
    );
  }

  Widget _overlayTrack() {
    return SizedBox(
      height: 38,
      child: ListView(scrollDirection: Axis.horizontal, padding: const EdgeInsets.symmetric(horizontal: 10), children: [
        for (final o in p.overlays)
          Padding(
            padding: const EdgeInsets.only(left: 6, top: 4, bottom: 4),
            child: ActionChip(
              avatar: Icon(o.sticker ? Icons.emoji_emotions : Icons.title, size: 16),
              label: Text('${o.text.length > 10 ? o.text.substring(0, 10) : o.text}  ${o.start.toStringAsFixed(1)}-${o.end.toStringAsFixed(1)}', style: const TextStyle(fontSize: 11)),
              onPressed: () => _editOverlay(existing: o, sticker: o.sticker),
              backgroundColor: C.surface2,
              side: BorderSide.none,
            ),
          ),
        if (p.musicName != null)
          Padding(padding: const EdgeInsets.only(left: 6, top: 4, bottom: 4), child: Chip(avatar: const Icon(Icons.music_note, size: 16), label: Text(p.musicName!.length > 14 ? '${p.musicName!.substring(0, 14)}…' : p.musicName!, style: const TextStyle(fontSize: 11)), backgroundColor: C.surface2, side: BorderSide.none)),
      ]),
    );
  }

  Widget _panel() {
    final c = selClip;
    Widget body;
    switch (tool) {
      case Tool.trim:
        if (c == null) {
          body = const SizedBox();
        } else if (c.isVideo) {
          body = Column(children: [
            Text('البداية ${fmtDur(c.start)}   النهاية ${fmtDur(c.end)}   المدة ${fmtDur(c.end - c.start)}', style: const TextStyle(color: C.sub, fontSize: 12)),
            RangeSlider(
              values: RangeValues(c.start.clamp(0.0, c.srcDur).toDouble(), c.end.clamp(0.0, c.srcDur).toDouble()),
              min: 0,
              max: c.srcDur,
              onChanged: (r) { if (r.end - r.start >= 0.3) setState(() { c.start = r.start; c.end = r.end; }); },
              onChangeEnd: (_) { _save(); _load(sel); },
            ),
          ]);
        } else {
          body = Column(children: [
            Text('مدة عرض الصورة: ${c.imageDur.toStringAsFixed(1)} ث', style: const TextStyle(color: C.sub, fontSize: 12)),
            Slider(value: c.imageDur.clamp(0.5, 10.0).toDouble(), min: 0.5, max: 10, onChanged: (v) => setState(() => c.imageDur = v), onChangeEnd: (_) => _save()),
          ]);
        }
        break;
      case Tool.filters:
        body = SizedBox(
          height: 96,
          child: ListView(scrollDirection: Axis.horizontal, children: [
            for (final f in kFilters)
              GestureDetector(
                onTap: () { setState(() => c.filter = f.id); _save(); },
                child: Container(
                  width: 70,
                  margin: const EdgeInsets.symmetric(horizontal: 4),
                  child: Column(children: [
                    Container(
                      width: 62,
                      height: 62,
                      clipBehavior: Clip.hardEdge,
                      decoration: BoxDecoration(borderRadius: BorderRadius.circular(12), border: Border.all(color: c!.filter == f.id ? C.accent : C.line, width: c.filter == f.id ? 3 : 1)),
                      child: c.thumb != null && File(c.thumb!).existsSync()
                          ? ColorFiltered(colorFilter: ColorFilter.matrix(f.matrix ?? const [1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0]), child: Image.file(File(c.thumb!), fit: BoxFit.cover))
                          : Container(color: C.surface2),
                    ),
                    const SizedBox(height: 3),
                    Text(f.name, style: TextStyle(fontSize: 11, color: c.filter == f.id ? C.text : C.sub)),
                  ]),
                ),
              ),
          ]),
        );
        break;
      case Tool.transitions:
        final last = sel >= p.clips.length - 1;
        body = Column(children: [
          if (last) const Text('الانتقال يكون بين مقطعين. اختر مقطعاً ليس الأخير.', style: TextStyle(color: C.sub, fontSize: 12))
          else ...[
            SizedBox(
              height: 74,
              child: ListView(scrollDirection: Axis.horizontal, children: [
                for (final t in kTransitions)
                  GestureDetector(
                    onTap: () { setState(() => c.transition = t.id); _save(); },
                    child: Container(
                      width: 74,
                      margin: const EdgeInsets.symmetric(horizontal: 4),
                      decoration: BoxDecoration(color: c!.transition == t.id ? C.accent : C.surface2, borderRadius: BorderRadius.circular(14)),
                      child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [Text(t.icon, style: const TextStyle(fontSize: 22)), const SizedBox(height: 2), Text(t.name, style: const TextStyle(fontSize: 11))]),
                    ),
                  ),
              ]),
            ),
            Row(children: [
              const Text('المدة', style: TextStyle(fontSize: 12, color: C.sub)),
              Expanded(child: Slider(value: c!.transitionDur.clamp(0.2, 1.5).toDouble(), min: 0.2, max: 1.5, onChanged: (v) => setState(() => c.transitionDur = v), onChangeEnd: (_) => _save())),
              Text('${c.transitionDur.toStringAsFixed(1)}s', style: const TextStyle(fontSize: 12)),
              TextButton(onPressed: () { setState(() { for (final x in p.clips) { x.transition = c.transition; x.transitionDur = c.transitionDur; } }); _save(); }, child: const Text('للكل')),
            ]),
          ],
        ]);
        break;
      case Tool.speed:
        if (c == null || !c.isVideo) {
          body = const Text('السرعة للفيديو فقط', style: TextStyle(color: C.sub));
        } else {
          body = Column(children: [
            Text('السرعة ×${c.speed.toStringAsFixed(2)}', style: const TextStyle(color: C.sub, fontSize: 12)),
            Slider(value: c.speed.clamp(0.25, 4.0).toDouble(), min: 0.25, max: 4, divisions: 15, onChanged: (v) => setState(() => c.speed = v), onChangeEnd: (_) { _save(); _load(sel); }),
          ]);
        }
        break;
      case Tool.volume:
        body = Column(children: [
          if (c != null && c.isVideo) Row(children: [const Text('صوت المقطع', style: TextStyle(fontSize: 12, color: C.sub)), Expanded(child: Slider(value: c.volume.clamp(0.0, 2.0).toDouble(), min: 0, max: 2, onChanged: (v) { setState(() => c.volume = v); vc?.setVolume(v.clamp(0.0, 1.0).toDouble()); }, onChangeEnd: (_) => _save())), Text('${(c.volume * 100).round()}%', style: const TextStyle(fontSize: 12))])
          else const Text('الصور بدون صوت', style: TextStyle(color: C.sub, fontSize: 12)),
          if (p.musicPath != null) Row(children: [const Text('صوت الموسيقى', style: TextStyle(fontSize: 12, color: C.sub)), Expanded(child: Slider(value: p.musicVolume.clamp(0.0, 1.0).toDouble(), onChanged: (v) { setState(() => p.musicVolume = v); music?.setVolume(v); }, onChangeEnd: (_) => _save())), Text('${(p.musicVolume * 100).round()}%', style: const TextStyle(fontSize: 12))]),
        ]);
        break;
      case Tool.music:
        body = Row(children: [
          Expanded(child: Text(p.musicName ?? 'لم تختر موسيقى بعد', style: const TextStyle(color: C.sub), overflow: TextOverflow.ellipsis)),
          FilledButton.icon(onPressed: _pickMusic, icon: const Icon(Icons.library_music), label: const Text('من الجهاز')),
          if (p.musicPath != null) IconButton(onPressed: () { setState(() { p.musicPath = null; p.musicName = null; }); _save(); _loadMusic(); }, icon: const Icon(Icons.delete_outline, color: C.danger)),
        ]);
        break;
      case Tool.none:
        body = const SizedBox();
        break;
    }
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(14, 8, 14, 6),
      decoration: const BoxDecoration(color: C.surface, border: Border(top: BorderSide(color: C.line))),
      child: body,
    );
  }

  Widget _toolbar() {
    Widget item(IconData i, String t, VoidCallback f, {bool on = false, Color? col}) => InkWell(
          onTap: f,
          child: SizedBox(
            width: 68,
            child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
              Icon(i, color: on ? C.accent : (col ?? C.text), size: 25),
              const SizedBox(height: 3),
              Text(t, style: TextStyle(fontSize: 11, color: on ? C.accent : C.sub, fontWeight: on ? FontWeight.w800 : FontWeight.w500)),
            ]),
          ),
        );
    return Container(
      height: 66,
      decoration: const BoxDecoration(color: C.surface, border: Border(top: BorderSide(color: C.line))),
      child: SafeArea(
        top: false,
        child: ListView(scrollDirection: Axis.horizontal, children: [
          item(Icons.content_cut_rounded, 'قص', () => _pickTool(Tool.trim), on: tool == Tool.trim),
          item(Icons.auto_fix_high_rounded, 'فلاتر', () => _pickTool(Tool.filters), on: tool == Tool.filters),
          item(Icons.compare_rounded, 'انتقالات', () => _pickTool(Tool.transitions), on: tool == Tool.transitions),
          item(Icons.title_rounded, 'نص', () => _editOverlay()),
          item(Icons.music_note_rounded, 'موسيقى', () => _pickTool(Tool.music), on: tool == Tool.music),
          item(Icons.emoji_emotions_rounded, 'ملصقات', () => _editOverlay(sticker: true)),
          item(Icons.speed_rounded, 'سرعة', () => _pickTool(Tool.speed), on: tool == Tool.speed),
          item(Icons.volume_up_rounded, 'الصوت', () => _pickTool(Tool.volume), on: tool == Tool.volume),
          item(Icons.copy_rounded, 'تكرار', _dupSel),
          item(Icons.delete_outline_rounded, 'حذف', _deleteSel, col: C.danger),
          item(Icons.add_photo_alternate_rounded, 'إضافة', _addClips),
        ]),
      ),
    );
  }
}

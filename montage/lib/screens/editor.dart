import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:ui' as ui;
import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';
import '../export.dart';
import '../filters.dart';
import '../fonts.dart';
import '../models.dart';
import '../store.dart';
import '../theme.dart';
import 'export_sheet.dart';
import 'picker.dart';

enum Tool { none, edit, trim, filters, transitions, speed, volume, music, text, stickers, adjust, ratio, background }

const _textColors = [0xFFFFFFFF, 0xFF000000, 0xFFFACC15, 0xFFEF4444, 0xFF22C55E, 0xFF38BDF8, 0xFFA78BFA, 0xFFF472B6, 0xFFFB923C, 0xFF14B8A6];
const _emojis = ['😀', '😂', '😍', '🥳', '😎', '🔥', '❤️', '⭐', '✨', '🎉', '🎊', '🎁', '👍', '👏', '💯', '🚀', '🌈', '☀️', '🌙', '🎵', '🎬', '📸', '🏆', '👑', '💎', '🌸', '🍕', '⚽', '🎮', '💥'];
const _pps = 56.0; // بكسل لكل ثانية في الخط الزمني

class EditorPage extends StatefulWidget {
  final Project project;
  final Tool? initialTool;
  const EditorPage({super.key, required this.project, this.initialTool});
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
  bool full = false;
  final _sc = ScrollController();
  bool _user = false;
  bool _loadingClip = false;
  double? _pendingSeek;
  final List<String> _undo = [];
  final List<String> _redo = [];

  bool get photo => p.photoMode;

  @override
  void initState() {
    super.initState();
    _timer = Timer.periodic(const Duration(milliseconds: 50), (_) => _tick());
    _load(0);
    _loadMusic();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      final t = widget.initialTool;
      if (t == null) return;
      if (t == Tool.text) {
        _editOverlay();
      } else if (t == Tool.stickers) {
        _editOverlay(sticker: true);
      } else if (t == Tool.music) {
        setState(() => tool = Tool.music);
        _pickMusic();
      } else {
        setState(() => tool = t);
      }
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    vc?.dispose();
    music?.dispose();
    _sc.dispose();
    Store.saveProject(p);
    super.dispose();
  }

  // ---------------- التراجع / الإعادة ----------------
  void _snap() {
    _undo.add(p.encode());
    if (_undo.length > 40) _undo.removeAt(0);
    _redo.clear();
  }

  void _restore(String js) {
    final np = Project.fromJson(jsonDecode(js) as Map<String, dynamic>);
    p.clips..clear()..addAll(np.clips);
    p.overlays..clear()..addAll(np.overlays);
    p.musicPath = np.musicPath;
    p.musicName = np.musicName;
    p.musicVolume = np.musicVolume;
    p.ratio = np.ratio;
    p.fitBlur = np.fitBlur;
    if (sel >= p.clips.length) sel = p.clips.isEmpty ? 0 : p.clips.length - 1;
  }

  void _undoAct() {
    if (_undo.isEmpty) return;
    _redo.add(p.encode());
    _restore(_undo.removeLast());
    setState(() {});
    Store.saveProject(p);
    _load(sel.clamp(0, p.clips.isEmpty ? 0 : p.clips.length - 1));
    _loadMusic();
  }

  void _redoAct() {
    if (_redo.isEmpty) return;
    _undo.add(p.encode());
    _restore(_redo.removeLast());
    setState(() {});
    Store.saveProject(p);
    _load(sel.clamp(0, p.clips.isEmpty ? 0 : p.clips.length - 1));
    _loadMusic();
  }

  void _mutate(VoidCallback f) {
    _snap();
    setState(f);
    Store.saveProject(p);
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

  // ---------------- المعاينة ----------------
  Future<void> _load(int i, {double at = 0}) async {
    _loadingClip = true;
    if (p.clips.isEmpty) {
      final old = vc;
      vc = null;
      ready = false;
      if (mounted) setState(() {});
      await old?.dispose();
      _loadingClip = false;
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
        _loadingClip = false;
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
    _loadingClip = false;
    if (mounted) setState(() {});
    final pend = _pendingSeek;
    if (pend != null) {
      _pendingSeek = null;
      _scrub(pend);
    }
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
      local += 0.05;
      if (local >= c.imageDur) {
        _next();
        return;
      }
    }
    if (_sc.hasClients && !_user) _sc.jumpTo((globalT * _pps).clamp(0.0, _sc.position.maxScrollExtent).toDouble());
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
    if (p.clips.isEmpty || photo) return;
    if (playing) {
      playing = false;
      await vc?.pause();
      await music?.pause();
    } else {
      playing = true;
      if (globalT >= p.total - 0.08) {
        await _load(0);
        if (_sc.hasClients) _sc.jumpTo(0);
      }
      await vc?.play();
      await music?.play();
    }
    if (mounted) setState(() {});
  }

  /// تحريك المؤشر (من السحب على الخط الزمني)
  void _scrub(double t) {
    if (p.clips.isEmpty) return;
    final o = _offs();
    var i = 0;
    for (var k = 0; k < p.clips.length; k++) {
      if (t >= o[k]) i = k;
    }
    final at = (t - o[i]).clamp(0.0, p.clips[i].effective).toDouble();
    sel = i;
    final v = vc;
    if (i == cur && ready && (p.clips[i].isVideo ? (v != null && v.value.isInitialized) : true)) {
      local = at;
      if (p.clips[i].isVideo) v!.seekTo(Duration(milliseconds: ((p.clips[i].start + at * p.clips[i].speed) * 1000).round()));
      setState(() {});
    } else if (_loadingClip) {
      _pendingSeek = t;
    } else {
      _load(i, at: at);
    }
  }

  // ---------------- أدوات ----------------
  MClip? get selClip => p.clips.isEmpty ? null : p.clips[sel.clamp(0, p.clips.length - 1)];

  Future<void> _addClips() async {
    final l = await pickMedia(context);
    if (l == null || l.isEmpty) return;
    _mutate(() {
      p.clips.addAll(l);
      sel = p.clips.length - l.length;
    });
    if (p.clips.length == l.length) _load(0);
  }

  void _deleteSel() {
    if (p.clips.isEmpty) return;
    _mutate(() {
      p.clips.removeAt(sel);
      if (sel >= p.clips.length) sel = p.clips.length - 1;
      if (sel < 0) sel = 0;
    });
    _load(sel);
  }

  void _dupSel() {
    final c = selClip;
    if (c == null) return;
    _mutate(() => p.clips.insert(sel + 1, c.copy()));
  }

  void _moveSel(int d) {
    final j = sel + d;
    if (j < 0 || j >= p.clips.length) return;
    _mutate(() {
      final c = p.clips.removeAt(sel);
      p.clips.insert(j, c);
      sel = j;
    });
    _load(0);
  }

  void _splitAtPlayhead() {
    final c = selClip;
    if (c == null) return;
    final o = _offs();
    final loc = globalT - o[sel];
    if (loc < 0.2 || loc > c.effective - 0.2) {
      toast(context, 'ضع المؤشر داخل المقطع (بعيداً عن الأطراف) ثم اضغط تقسيم');
      return;
    }
    _mutate(() {
      final b = c.copy();
      if (c.isVideo) {
        final cut = c.start + loc * c.speed;
        b.start = cut;
        c.end = cut;
      } else {
        b.imageDur = c.imageDur - loc;
        c.imageDur = loc;
      }
      b.transition = c.transition;
      c.transition = 'cut';
      p.clips.insert(sel + 1, b);
    });
    _load(sel, at: loc.clamp(0.0, 99999.0).toDouble());
    toast(context, 'تم تقسيم المقطع');
  }

  void _pickTool(Tool t) {
    final c = selClip;
    if (c == null && t != Tool.music && t != Tool.ratio && t != Tool.text && t != Tool.stickers) return;
    setState(() => tool = tool == t ? Tool.none : t);
  }

  Future<void> _pickMusic() async {
    final f = await FilePicker.pickFile(type: FileType.audio);
    if (f == null || f.path == null) return;
    _mutate(() {
      p.musicPath = f.path;
      p.musicName = f.name;
    });
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
    var delete = false;
    var arTab = true;
    final ok = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      backgroundColor: K.d1,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(24))),
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setS) {
          final fl = kFonts.where((f) => f.arabic == arTab).toList();
          Widget chipRow(List<(String, String)> items, String cur, ValueChanged<String> f) => Wrap(spacing: 8, runSpacing: 8, children: [for (final a in items) Tap(scale: .92, onTap: () => setS(() => f(a.$1)), child: Container(padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8), decoration: BoxDecoration(color: cur == a.$1 ? K.accent : K.d2, borderRadius: BorderRadius.circular(18)), child: Text(a.$2, style: tx(14, color: Colors.white))))]);
          return Padding(
            padding: EdgeInsets.fromLTRB(18, 16, 18, MediaQuery.of(ctx).viewInsets.bottom + 16),
            child: SingleChildScrollView(
              child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                Text(o.sticker ? 'ملصق' : 'نص', textAlign: TextAlign.center, style: tx(19, w: FontWeight.w800, color: Colors.white)),
                const SizedBox(height: 12),
                if (!o.sticker) ...[
                  Container(
                    decoration: BoxDecoration(color: K.d2, borderRadius: BorderRadius.circular(14)),
                    child: TextField(controller: ctrl, maxLines: 2, onChanged: (v) => setS(() => o.text = v), style: tx(18, color: Colors.white), decoration: InputDecoration(hintText: 'اكتب النص هنا', hintStyle: tx(16, w: FontWeight.w500, color: K.dsub), border: InputBorder.none, contentPadding: const EdgeInsets.all(14))),
                  ),
                  const SizedBox(height: 14),
                  Container(
                    height: 64,
                    alignment: Alignment.center,
                    decoration: BoxDecoration(color: const Color(0xFF2F3B3E), borderRadius: BorderRadius.circular(14)),
                    child: Text(o.text.isEmpty ? 'معاينة' : o.text, maxLines: 1, overflow: TextOverflow.ellipsis, style: overlayStyle(o, 0.55)),
                  ),
                  const SizedBox(height: 14),
                  Row(children: [
                    Tap(onTap: () => setS(() => arTab = true), child: Padding(padding: const EdgeInsets.symmetric(horizontal: 10), child: Text('خطوط عربية', style: tx(16, w: FontWeight.w800, color: arTab ? K.accent : K.dsub)))),
                    Tap(onTap: () => setS(() => arTab = false), child: Padding(padding: const EdgeInsets.symmetric(horizontal: 10), child: Text('خطوط إنجليزية', style: tx(16, w: FontWeight.w800, color: !arTab ? K.accent : K.dsub)))),
                    const Spacer(),
                    Text('${fl.length} خط', style: tx(12, color: K.dsub)),
                  ]),
                  const SizedBox(height: 8),
                  SizedBox(
                    height: 68,
                    child: ListView(scrollDirection: Axis.horizontal, children: [
                      for (final f in fl)
                        Tap(
                          scale: .92,
                          onTap: () => setS(() => o.font = f.family),
                          child: Container(
                            width: 98,
                            margin: const EdgeInsets.only(left: 8),
                            decoration: BoxDecoration(color: o.font == f.family ? K.accent.withValues(alpha: .25) : K.d2, borderRadius: BorderRadius.circular(14), border: Border.all(color: o.font == f.family ? K.accent : Colors.transparent, width: 2)),
                            child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
                              Text(f.arabic ? 'مونتاجي' : 'Montagi', style: TextStyle(fontFamily: f.family, fontFamilyFallback: const ['Tajawal'], fontSize: 20, color: Colors.white, fontWeight: FontWeight.w700)),
                              const SizedBox(height: 2),
                              Text(f.label, maxLines: 1, overflow: TextOverflow.ellipsis, style: tx(10, color: K.dsub)),
                            ]),
                          ),
                        ),
                    ]),
                  ),
                  const SizedBox(height: 12),
                  Wrap(spacing: 10, runSpacing: 8, children: [for (final c in _textColors) Tap(scale: .85, onTap: () => setS(() => o.color = c), child: Container(width: 34, height: 34, decoration: BoxDecoration(color: Color(c), shape: BoxShape.circle, border: Border.all(color: o.color == c ? K.accent : K.d3, width: o.color == c ? 3 : 1.5))))]),
                  const SizedBox(height: 10),
                  Row(children: [
                    _toggle('عريض', o.bold, () => setS(() => o.bold = !o.bold)),
                    const SizedBox(width: 8),
                    _toggle('مائل', o.italic, () => setS(() => o.italic = !o.italic)),
                    const SizedBox(width: 8),
                    _toggle('خلفية', o.box, () => setS(() => o.box = !o.box)),
                  ]),
                ] else
                  Wrap(spacing: 8, runSpacing: 8, children: [for (final e in _emojis) Tap(scale: .85, onTap: () => setS(() => o.text = e), child: Container(padding: const EdgeInsets.all(8), decoration: BoxDecoration(color: o.text == e ? K.accent : K.d2, borderRadius: BorderRadius.circular(12)), child: Text(e, style: const TextStyle(fontSize: 28))))]),
                const SizedBox(height: 8),
                Row(children: [Text('الحجم', style: tx(14, color: K.dsub)), Expanded(child: Slider(activeColor: K.accent, value: o.size.clamp(30.0, 260.0).toDouble(), min: 30, max: 260, onChanged: (v) => setS(() => o.size = v)))]),
                Row(children: [Text('الموضع', style: tx(14, color: K.dsub)), const SizedBox(width: 12), chipRow(const [('0', 'أعلى'), ('1', 'وسط'), ('2', 'أسفل')], '${o.position}', (v) => o.position = int.parse(v))]),
                const SizedBox(height: 10),
                chipRow([for (final a in const [('none', 'بدون'), ('fade', 'تلاشي'), ('slide', 'انزلاق'), ('pop', 'قفزة'), ('type', 'كتابة')]) if (!(o.sticker && a.$1 == 'type')) a], o.anim, (v) => o.anim = v),
                const SizedBox(height: 8),
                Text('التوقيت: ${fmtDurMs(o.start)}  ←  ${fmtDurMs(o.end)}', style: tx(13, color: K.dsub)),
                RangeSlider(activeColor: K.accent, values: RangeValues(o.start.clamp(0.0, total).toDouble(), o.end.clamp(0.0, total).toDouble()), min: 0, max: total, onChanged: (r) => setS(() { o.start = r.start; o.end = r.end; })),
                const SizedBox(height: 6),
                Row(children: [
                  if (existing != null) Expanded(child: PillButton('حذف', filled: false, onTap: () { delete = true; Navigator.pop(ctx, true); }, height: 48)),
                  if (existing != null) const SizedBox(width: 10),
                  Expanded(flex: 2, child: PillButton('حفظ', fillColor: K.accent, height: 48, onTap: () => Navigator.pop(ctx, true))),
                ]),
              ]),
            ),
          );
        },
      ),
    );
    if (ok != true) return;
    _snap();
    setState(() {
      if (delete) {
        p.overlays.removeWhere((x) => x.id == o.id);
      } else if (existing == null) {
        if (o.text.trim().isEmpty) return;
        p.overlays.add(o);
      }
    });
    Store.saveProject(p);
  }

  Widget _toggle(String t, bool on, VoidCallback f) => Tap(scale: .92, onTap: f, child: Container(padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 9), decoration: BoxDecoration(color: on ? K.accent : K.d2, borderRadius: BorderRadius.circular(18)), child: Text(t, style: tx(14, color: Colors.white))));

  Future<void> _rename() async {
    final c = TextEditingController(text: p.name);
    final v = await showDialog<String>(
      context: context,
      builder: (_) => AlertDialog(
        title: Text('اسم المشروع', style: tx(18, w: FontWeight.w800)),
        content: TextField(controller: c, autofocus: true, style: tx(16)),
        actions: [TextButton(onPressed: () => Navigator.pop(context), child: Text('إلغاء', style: tx(15, color: K.sub))), TextButton(onPressed: () => Navigator.pop(context, c.text), child: Text('حفظ', style: tx(15, color: K.accentDark)))],
      ),
    );
    if (v != null && v.trim().isNotEmpty) _mutate(() => p.name = v.trim());
  }

  Future<void> _openExport() async {
    if (p.clips.isEmpty) {
      toast(context, 'أضف مقاطع أولاً');
      return;
    }
    if (playing) await _togglePlay();
    if (!mounted) return;
    await Navigator.of(context).push(MaterialPageRoute(builder: (_) => ExportPage(project: p)));
  }

  // ---------------- واجهة ----------------
  @override
  Widget build(BuildContext context) {
    return PopScope(
      onPopInvokedWithResult: (_, __) => Store.saveProject(p),
      child: Scaffold(
        backgroundColor: K.d0,
        body: SafeArea(
          child: Column(children: [
            _topBar(),
            Expanded(child: Center(child: _previewBox())),
            _controls(),
            if (!full && !photo) _timeline(),
            if (!full && tool != Tool.none && tool != Tool.edit) _panel(),
            if (!full) _toolbar(),
          ]),
        ),
      ),
    );
  }

  Widget _topBar() {
    final dm = dimsFor(p.ratio, Store.exportOptions.res);
    return Padding(
      padding: const EdgeInsets.fromLTRB(14, 8, 14, 6),
      child: Row(children: [
        Tap(scale: .93, onTap: _openExport, child: Container(padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 10), decoration: BoxDecoration(color: K.accent, borderRadius: BorderRadius.circular(12)), child: Row(mainAxisSize: MainAxisSize.min, children: [const Icon(Icons.ios_share_rounded, color: Colors.white, size: 22), const SizedBox(width: 8), Text('تصدير', style: tx(16, w: FontWeight.w800, color: Colors.white))]))),
        const SizedBox(width: 10),
        Expanded(child: Tap(onTap: _rename, child: Container(padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10), decoration: BoxDecoration(color: K.d2, borderRadius: BorderRadius.circular(12)), child: Row(children: [Expanded(child: Text(p.name, overflow: TextOverflow.ellipsis, style: tx(15, color: Colors.white))), Text('${dm.w}×${dm.h}', style: tx(11, color: K.dsub)), const Icon(Icons.arrow_drop_down_rounded, color: Colors.white)])))),
        const SizedBox(width: 6),
        Tap(onTap: () => Navigator.of(context).pop(), child: const Padding(padding: EdgeInsets.all(8), child: Icon(Icons.close_rounded, color: Colors.white, size: 30))),
      ]),
    );
  }

  Widget _previewBox() {
    return LayoutBuilder(builder: (context, cons) {
      final r = ratioValue(p.ratio);
      var h = cons.maxHeight - 6;
      var w = h * r;
      if (w > cons.maxWidth - 24) {
        w = cons.maxWidth - 24;
        h = w / r;
      }
      final scale = w / (r > 1 ? 1920.0 : 1080.0);
      final c = p.clips.isEmpty ? null : p.clips[cur.clamp(0, p.clips.length - 1)];
      Widget content(BoxFit fit) {
        if (c == null) return const SizedBox();
        if (c.isVideo) {
          final v = vc;
          return (v != null && v.value.isInitialized)
              ? FittedBox(fit: fit, clipBehavior: Clip.hardEdge, child: SizedBox(width: v.value.size.width, height: v.value.size.height, child: VideoPlayer(v)))
              : Stack(fit: StackFit.expand, children: [
                  if (c.thumb != null && File(c.thumb!).existsSync()) Image.file(File(c.thumb!), fit: fit, width: w, height: h),
                  const Center(child: SizedBox(width: 26, height: 26, child: CircularProgressIndicator(strokeWidth: 2.6, color: K.accent))),
                ]);
        }
        return Image.file(File(c.path), fit: fit, width: w, height: h);
      }

      Widget media;
      if (c == null) {
        media = Center(child: Text('أضف مقاطع للبدء', style: tx(15, color: K.dsub)));
      } else if (p.fitBlur) {
        media = Stack(fit: StackFit.expand, children: [ImageFiltered(imageFilter: ui.ImageFilter.blur(sigmaX: 18, sigmaY: 18), child: content(BoxFit.cover)), content(BoxFit.contain)]);
      } else {
        media = content(BoxFit.cover);
      }
      final m = c == null ? null : clipMatrix(c);
      if (m != null) media = ColorFiltered(colorFilter: ColorFilter.matrix(m), child: media);
      final t = photo ? 0.5 : globalT;
      return Container(
        width: w,
        height: h,
        clipBehavior: Clip.hardEdge,
        decoration: BoxDecoration(color: Colors.black, borderRadius: BorderRadius.circular(4)),
        child: Stack(fit: StackFit.expand, children: [
          media,
          for (final o in p.overlays) if (photo || (t >= o.start && t <= o.end)) _overlayView(o, photo ? 0.5 : t, scale, photo),
        ]),
      );
    });
  }

  Widget _overlayView(OverlayItem o, double t, double scale, bool still) {
    final a = t - o.start;
    final d = o.end - o.start;
    double opacity = 1, dy = 0, sc = 1;
    var text = o.text;
    if (!still) {
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
    }
    final ay = o.position == 0 ? -0.72 : (o.position == 1 ? 0.0 : 0.64);
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
              child: Text(text, textAlign: TextAlign.center, style: overlayStyle(o, scale)),
            ),
          ),
        ),
      ),
    );
  }

  Widget _controls() {
    final total = p.total;
    return Padding(
      padding: const EdgeInsets.fromLTRB(14, 6, 14, 2),
      child: Row(textDirection: TextDirection.ltr, children: [
        Tap(scale: .85, onTap: _undo.isEmpty ? null : _undoAct, child: Padding(padding: const EdgeInsets.all(8), child: Icon(Icons.undo_rounded, size: 28, color: _undo.isEmpty ? K.d3 : Colors.white))),
        Tap(scale: .85, onTap: _redo.isEmpty ? null : _redoAct, child: Padding(padding: const EdgeInsets.all(8), child: Icon(Icons.redo_rounded, size: 28, color: _redo.isEmpty ? K.d3 : Colors.white))),
        const Spacer(),
        if (!photo) ...[
          Text(fmtDur(globalT), style: tx(13, w: FontWeight.w800, color: Colors.white)),
          Text(' / ${fmtDur(total)}', style: tx(13, color: K.dsub)),
          const SizedBox(width: 14),
        ],
        Tap(scale: .8, onTap: photo ? null : _togglePlay, child: Container(width: 54, height: 54, decoration: BoxDecoration(color: photo ? K.d3 : Colors.white, shape: BoxShape.circle), child: Icon(playing ? Icons.pause_rounded : Icons.play_arrow_rounded, size: 36, color: Colors.black))),
        const Spacer(),
        Tap(scale: .85, onTap: () => setState(() => full = !full), child: Padding(padding: const EdgeInsets.all(8), child: Icon(full ? Icons.fullscreen_exit_rounded : Icons.fullscreen_rounded, size: 30, color: Colors.white))),
      ]),
    );
  }

  Widget _timeline() {
    final total = p.total;
    final half = MediaQuery.of(context).size.width / 2;
    final contentW = total * _pps + half * 2;
    final o = _offs();
    final selC = selClip;
    return SizedBox(
      height: 196,
      child: Directionality(
        textDirection: TextDirection.ltr,
        child: Stack(children: [
          NotificationListener<ScrollNotification>(
            onNotification: (n) {
              if (n is ScrollStartNotification && n.dragDetails != null) {
                _user = true;
                if (playing) _togglePlay();
              } else if (n is ScrollUpdateNotification && _user) {
                _scrub((n.metrics.pixels / _pps).clamp(0.0, total).toDouble());
              } else if (n is ScrollEndNotification) {
                _user = false;
              }
              return false;
            },
            child: SingleChildScrollView(
              controller: _sc,
              scrollDirection: Axis.horizontal,
              child: SizedBox(
                width: contentW,
                height: 196,
                child: Stack(children: [
                  // المسطرة
                  for (var s = 0; s <= total.ceil(); s++) ...[
                    Positioned(left: half + s * _pps - 18, top: 4, child: SizedBox(width: 36, child: Text(fmtDur(s.toDouble()), textAlign: TextAlign.center, style: tx(10, color: K.dsub)))),
                    Positioned(left: half + s * _pps + _pps / 2 - 2, top: 11, child: Container(width: 4, height: 4, decoration: const BoxDecoration(color: K.d3, shape: BoxShape.circle))),
                  ],
                  // المقاطع
                  for (var i = 0; i < p.clips.length; i++) _clipTile(i, half, o),
                  // مسار الصوت
                  Positioned(
                    left: half,
                    top: 100,
                    child: p.musicName != null
                        ? Tap(onTap: () => setState(() => tool = tool == Tool.volume ? Tool.none : Tool.volume), child: Container(width: (total * _pps).clamp(60.0, 100000.0).toDouble(), height: 34, padding: const EdgeInsets.symmetric(horizontal: 8), alignment: Alignment.centerLeft, decoration: BoxDecoration(color: const Color(0xFF1F4B52), borderRadius: BorderRadius.circular(8)), child: Row(children: [const Icon(Icons.music_note_rounded, size: 16, color: K.accent), const SizedBox(width: 4), Expanded(child: Text(p.musicName!, overflow: TextOverflow.ellipsis, style: tx(11, color: Colors.white)))])))
                        : Tap(onTap: _pickMusic, child: Container(height: 34, padding: const EdgeInsets.symmetric(horizontal: 12), decoration: BoxDecoration(color: K.d2, borderRadius: BorderRadius.circular(8)), child: Row(mainAxisSize: MainAxisSize.min, children: [const Icon(Icons.add_rounded, size: 18, color: K.dsub), const SizedBox(width: 6), Text('إضافة صوت', style: tx(12, color: K.dsub))]))),
                  ),
                  // مسار النص
                  for (final t in p.overlays)
                    Positioned(
                      left: half + t.start * _pps,
                      top: 146,
                      child: Tap(onTap: () => _editOverlay(existing: t, sticker: t.sticker), child: Container(width: ((t.end - t.start) * _pps).clamp(44.0, 100000.0).toDouble(), height: 34, padding: const EdgeInsets.symmetric(horizontal: 8), alignment: Alignment.centerLeft, decoration: BoxDecoration(color: const Color(0xFF3B2F55), borderRadius: BorderRadius.circular(8)), child: Row(children: [Icon(t.sticker ? Icons.emoji_emotions_rounded : Icons.title_rounded, size: 16, color: const Color(0xFFD6B8FF)), const SizedBox(width: 4), Expanded(child: Text(t.text, maxLines: 1, overflow: TextOverflow.ellipsis, style: tx(11, color: Colors.white)))]))),
                    ),
                  if (p.overlays.isEmpty)
                    Positioned(left: half, top: 146, child: Tap(onTap: () => _editOverlay(), child: Container(height: 34, padding: const EdgeInsets.symmetric(horizontal: 12), decoration: BoxDecoration(color: K.d2, borderRadius: BorderRadius.circular(8)), child: Row(mainAxisSize: MainAxisSize.min, children: [const Icon(Icons.add_rounded, size: 18, color: K.dsub), const SizedBox(width: 6), Text('إضافة نص', style: tx(12, color: K.dsub))])))),
                ]),
              ),
            ),
          ),
          // مؤشر التشغيل
          Positioned(left: half - 1, top: 0, bottom: 0, child: Container(width: 2, color: Colors.white)),
          // العمود الثابت (كتم الصوت + تسميات المسارات)
          Positioned(
            left: 0,
            top: 0,
            bottom: 0,
            width: 64,
            child: Container(
              color: K.d0.withValues(alpha: .92),
              child: Column(children: [
                const SizedBox(height: 28),
                Tap(
                  scale: .85,
                  onTap: selC == null || !selC.isVideo ? null : () => _mutate(() => selC.volume = selC.volume == 0 ? 1 : 0),
                  child: SizedBox(height: 58, child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [Icon((selC?.volume ?? 1) == 0 ? Icons.volume_off_rounded : Icons.volume_up_rounded, color: Colors.white, size: 26), Text('كتم صوت\nالمقطع', textAlign: TextAlign.center, style: tx(9, color: K.dsub, h: 1.1))])),
                ),
                const SizedBox(height: 16),
                Container(width: 32, height: 32, decoration: BoxDecoration(color: K.d2, borderRadius: BorderRadius.circular(8)), child: const Icon(Icons.music_note_rounded, size: 18, color: K.dsub)),
                const SizedBox(height: 14),
                Container(width: 32, height: 32, decoration: BoxDecoration(color: K.d2, borderRadius: BorderRadius.circular(8)), child: const Icon(Icons.title_rounded, size: 18, color: K.dsub)),
              ]),
            ),
          ),
        ]),
      ),
    );
  }

  Widget _clipTile(int i, double half, List<double> o) {
    final c = p.clips[i];
    final isLast = i == p.clips.length - 1;
    final w = ((c.effective - (isLast ? 0 : p.joinDur(i))) * _pps).clamp(24.0, 100000.0).toDouble();
    final x = half + o[i] * _pps;
    final isSel = i == sel;
    final m = clipMatrix(c);
    Widget img = (c.thumb != null && File(c.thumb!).existsSync())
        ? Row(children: [for (var k = 0; k < (w / 64).ceil() + 1; k++) SizedBox(width: 64, height: 54, child: Image.file(File(c.thumb!), fit: BoxFit.cover))])
        : Container(color: K.d2);
    if (m != null) img = ColorFiltered(colorFilter: ColorFilter.matrix(m), child: img);
    return Positioned(
      left: x,
      top: 28,
      child: Row(children: [
        Tap(
          scale: .97,
          haptic: false,
          onTap: () {
            setState(() => sel = i);
            final tt = o[i] + 0.01;
            if (_sc.hasClients) _sc.animateTo(tt * _pps, duration: const Duration(milliseconds: 200), curve: Curves.easeOut);
            _scrub(tt);
          },
          child: Container(
            width: w,
            height: 56,
            clipBehavior: Clip.hardEdge,
            decoration: BoxDecoration(borderRadius: BorderRadius.circular(6), border: Border.all(color: isSel ? Colors.white : Colors.transparent, width: 2), color: K.d2),
            child: Stack(fit: StackFit.expand, children: [
              OverflowBox(alignment: Alignment.centerLeft, maxWidth: 100000, child: img),
              Positioned(left: 4, bottom: 2, child: Text(c.effective.toStringAsFixed(1), style: tx(10, color: Colors.white).copyWith(shadows: const [Shadow(color: Colors.black87, blurRadius: 3)]))),
              if (c.speed != 1) Positioned(right: 4, top: 2, child: Text('×${c.speed.toStringAsFixed(1)}', style: tx(10, color: K.accent))),
            ]),
          ),
        ),
        if (!isLast)
          Transform.translate(
            offset: const Offset(-13, 0),
            child: Tap(
              scale: .8,
              onTap: () => setState(() { sel = i; tool = Tool.transitions; }),
              child: Container(width: 26, height: 26, decoration: BoxDecoration(color: Colors.white, shape: BoxShape.circle, border: Border.all(color: K.d1, width: 2)), child: Icon(_trIcon(c.transition), size: 15, color: Colors.black)),
            ),
          ),
      ]),
    );
  }

  IconData _trIcon(String id) {
    switch (id) {
      case 'cut': return Icons.content_cut_rounded;
      case 'fade': return Icons.blur_on_rounded;
      case 'slide': return Icons.arrow_back_rounded;
      case 'zoom': return Icons.zoom_in_rounded;
      case 'wipe': return Icons.swipe_left_rounded;
      case 'circle': return Icons.radio_button_unchecked_rounded;
      case 'dissolve': return Icons.grain_rounded;
      default: return Icons.rotate_right_rounded;
    }
  }

  // ---------------- اللوحات ----------------
  Widget _sliderRow(String label, double v, double min, double max, ValueChanged<double> on, {VoidCallback? start, VoidCallback? end, String? suffix}) {
    return Row(children: [
      SizedBox(width: 74, child: Text(label, style: tx(13, color: K.dsub))),
      Expanded(child: SliderTheme(data: SliderTheme.of(context).copyWith(trackHeight: 3, activeTrackColor: K.accent, inactiveTrackColor: K.d3, thumbColor: Colors.white), child: Slider(value: v.clamp(min, max).toDouble(), min: min, max: max, onChangeStart: (_) => start?.call(), onChanged: on, onChangeEnd: (_) => end?.call()))),
      SizedBox(width: 48, child: Text(suffix ?? v.toStringAsFixed(1), textAlign: TextAlign.end, style: tx(12, color: Colors.white))),
    ]);
  }

  Widget _panel() {
    final c = selClip;
    Widget body = const SizedBox();
    switch (tool) {
      case Tool.trim:
        if (c == null) break;
        if (c.isVideo) {
          body = Column(children: [
            Text('البداية ${fmtDurMs(c.start)}   النهاية ${fmtDurMs(c.end)}   المدة ${fmtDurMs(c.end - c.start)}', style: tx(12, color: K.dsub)),
            RangeSlider(activeColor: K.accent, values: RangeValues(c.start.clamp(0.0, c.srcDur).toDouble(), c.end.clamp(0.0, c.srcDur).toDouble()), min: 0, max: c.srcDur, onChangeStart: (_) => _snap(), onChanged: (r) { if (r.end - r.start >= 0.3) setState(() { c.start = r.start; c.end = r.end; }); }, onChangeEnd: (_) { Store.saveProject(p); _load(sel); }),
          ]);
        } else {
          body = _sliderRow('مدة العرض', c.imageDur, 0.5, 10, (v) => setState(() => c.imageDur = v), start: _snap, end: () => Store.saveProject(p), suffix: '${c.imageDur.toStringAsFixed(1)} ث');
        }
        break;
      case Tool.filters:
        if (c == null) break;
        body = SizedBox(
          height: 98,
          child: ListView(scrollDirection: Axis.horizontal, children: [
            for (final f in kFilters)
              Tap(
                scale: .9,
                onTap: () => _mutate(() => c.filter = f.id),
                child: Container(
                  width: 72,
                  margin: const EdgeInsets.symmetric(horizontal: 4),
                  child: Column(children: [
                    Container(
                      width: 64,
                      height: 64,
                      clipBehavior: Clip.hardEdge,
                      decoration: BoxDecoration(borderRadius: BorderRadius.circular(12), border: Border.all(color: c.filter == f.id ? K.accent : K.d3, width: c.filter == f.id ? 3 : 1)),
                      child: c.thumb != null && File(c.thumb!).existsSync() ? ColorFiltered(colorFilter: ColorFilter.matrix(f.matrix ?? const [1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 1, 0]), child: Image.file(File(c.thumb!), fit: BoxFit.cover)) : Container(color: K.d2),
                    ),
                    const SizedBox(height: 4),
                    Text(f.name, style: tx(11, color: c.filter == f.id ? Colors.white : K.dsub)),
                  ]),
                ),
              ),
          ]),
        );
        break;
      case Tool.transitions:
        if (c == null) break;
        final last = sel >= p.clips.length - 1;
        body = Column(children: [
          if (last) Text('الانتقال يكون بين مقطعين. اختر مقطعاً ليس الأخير.', style: tx(13, color: K.dsub))
          else ...[
            SizedBox(
              height: 80,
              child: ListView(scrollDirection: Axis.horizontal, children: [
                for (final t in kTransitions)
                  Tap(
                    scale: .9,
                    onTap: () => _mutate(() => c.transition = t.id),
                    child: Container(
                      width: 78,
                      margin: const EdgeInsets.symmetric(horizontal: 4),
                      decoration: BoxDecoration(color: c.transition == t.id ? K.accent : K.d2, borderRadius: BorderRadius.circular(14)),
                      child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [Icon(_trIcon(t.id), size: 26, color: Colors.white), const SizedBox(height: 4), Text(t.name, style: tx(12, color: Colors.white))]),
                    ),
                  ),
              ]),
            ),
            Row(children: [
              Expanded(child: _sliderRow('المدة', c.transitionDur, 0.2, 1.5, (v) => setState(() => c.transitionDur = v), start: _snap, end: () => Store.saveProject(p), suffix: '${c.transitionDur.toStringAsFixed(1)}s')),
              Tap(onTap: () => _mutate(() { for (final x in p.clips) { x.transition = c.transition; x.transitionDur = c.transitionDur; } }), child: Padding(padding: const EdgeInsets.all(8), child: Text('للكل', style: tx(14, color: K.accent)))),
            ]),
          ],
        ]);
        break;
      case Tool.speed:
        if (c == null || !c.isVideo) {
          body = Text('السرعة للفيديو فقط', style: tx(13, color: K.dsub));
        } else {
          body = Column(children: [
            Row(mainAxisAlignment: MainAxisAlignment.spaceEvenly, children: [for (final s in const [0.5, 1.0, 1.5, 2.0, 3.0]) Tap(scale: .9, onTap: () { _mutate(() => c.speed = s); _load(sel); }, child: Container(padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8), decoration: BoxDecoration(color: c.speed == s ? K.accent : K.d2, borderRadius: BorderRadius.circular(16)), child: Text('×$s', style: tx(14, color: Colors.white))))]),
            _sliderRow('السرعة', c.speed, 0.25, 4, (v) => setState(() => c.speed = v), start: _snap, end: () { Store.saveProject(p); _load(sel); }, suffix: '×${c.speed.toStringAsFixed(2)}'),
          ]);
        }
        break;
      case Tool.volume:
        body = Column(children: [
          if (c != null && c.isVideo) _sliderRow('صوت المقطع', c.volume, 0, 2, (v) { setState(() => c.volume = v); vc?.setVolume(v.clamp(0.0, 1.0).toDouble()); }, start: _snap, end: () => Store.saveProject(p), suffix: '${(c.volume * 100).round()}%')
          else Text('الصور بدون صوت', style: tx(13, color: K.dsub)),
          if (p.musicPath != null) _sliderRow('الموسيقى', p.musicVolume, 0, 1, (v) { setState(() => p.musicVolume = v); music?.setVolume(v); }, start: _snap, end: () => Store.saveProject(p), suffix: '${(p.musicVolume * 100).round()}%'),
        ]);
        break;
      case Tool.music:
        body = Row(children: [
          Expanded(child: Text(p.musicName ?? 'لم تختر موسيقى بعد', style: tx(14, color: K.dsub), overflow: TextOverflow.ellipsis)),
          Tap(onTap: _pickMusic, child: Container(padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 10), decoration: BoxDecoration(color: K.accent, borderRadius: BorderRadius.circular(14)), child: Row(mainAxisSize: MainAxisSize.min, children: [const Icon(Icons.library_music_rounded, color: Colors.white, size: 20), const SizedBox(width: 6), Text('من الجهاز', style: tx(14, color: Colors.white))]))),
          if (p.musicPath != null) Tap(onTap: () { _mutate(() { p.musicPath = null; p.musicName = null; }); _loadMusic(); }, child: const Padding(padding: EdgeInsets.all(10), child: Icon(Icons.delete_outline_rounded, color: K.danger))),
        ]);
        break;
      case Tool.adjust:
        if (c == null) break;
        body = Column(children: [
          _sliderRow('السطوع', c.adjB, -1, 1, (v) => setState(() => c.adjB = v), start: _snap, end: () => Store.saveProject(p), suffix: c.adjB.toStringAsFixed(2)),
          _sliderRow('التباين', c.adjC, 0.5, 1.5, (v) => setState(() => c.adjC = v), start: _snap, end: () => Store.saveProject(p), suffix: c.adjC.toStringAsFixed(2)),
          _sliderRow('التشبع', c.adjS, 0, 2, (v) => setState(() => c.adjS = v), start: _snap, end: () => Store.saveProject(p), suffix: c.adjS.toStringAsFixed(2)),
          Align(alignment: AlignmentDirectional.centerEnd, child: Tap(onTap: () => _mutate(() { for (final x in p.clips) { x.adjB = c.adjB; x.adjC = c.adjC; x.adjS = c.adjS; } }), child: Padding(padding: const EdgeInsets.all(6), child: Text('تطبيق على الكل', style: tx(13, color: K.accent))))),
        ]);
        break;
      case Tool.ratio:
        body = Row(mainAxisAlignment: MainAxisAlignment.spaceEvenly, children: [for (final r in const ['9:16', '16:9', '1:1', '4:5', '3:4']) Tap(scale: .9, onTap: () => _mutate(() => p.ratio = r), child: Container(padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10), decoration: BoxDecoration(color: p.ratio == r ? K.accent : K.d2, borderRadius: BorderRadius.circular(14)), child: Text(r, style: tx(15, color: Colors.white))))]);
        break;
      case Tool.background:
        body = Row(mainAxisAlignment: MainAxisAlignment.spaceEvenly, children: [
          for (final b in const [(false, 'ملء الإطار (قص)', Icons.crop_rounded), (true, 'احتواء + خلفية ضبابية', Icons.blur_circular_rounded)]) Tap(scale: .9, onTap: () => _mutate(() => p.fitBlur = b.$1), child: Container(padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12), decoration: BoxDecoration(color: p.fitBlur == b.$1 ? K.accent : K.d2, borderRadius: BorderRadius.circular(14)), child: Row(mainAxisSize: MainAxisSize.min, children: [Icon(b.$3, color: Colors.white, size: 22), const SizedBox(width: 6), Text(b.$2, style: tx(13, color: Colors.white))]))),
        ]);
        break;
      default:
        break;
    }
    return Container(width: double.infinity, padding: const EdgeInsets.fromLTRB(16, 10, 16, 6), decoration: const BoxDecoration(color: K.d1, border: Border(top: BorderSide(color: K.d2))), child: body);
  }

  Widget _toolbar() {
    Widget item(IconData i, String t, VoidCallback f, {bool on = false, Color? col}) => Tap(
          scale: 0.8,
          onTap: f,
          child: SizedBox(
            width: 76,
            child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
              Icon(i, color: on ? K.accent : (col ?? Colors.white), size: 31),
              const SizedBox(height: 4),
              Text(t, textAlign: TextAlign.center, style: tx(12, color: on ? K.accent : const Color(0xFFD8D8D8))),
            ]),
          ),
        );
    List<Widget> items;
    if (tool == Tool.edit || tool == Tool.trim || tool == Tool.speed || (tool == Tool.volume && false)) {
      items = [
        item(Icons.arrow_forward_ios_rounded, 'رجوع', () => setState(() => tool = Tool.none)),
        item(Icons.content_cut_rounded, 'قص', () => setState(() => tool = tool == Tool.trim ? Tool.edit : Tool.trim), on: tool == Tool.trim),
        item(Icons.vertical_split_rounded, 'تقسيم', _splitAtPlayhead),
        item(Icons.speed_rounded, 'سرعة', () => setState(() => tool = tool == Tool.speed ? Tool.edit : Tool.speed), on: tool == Tool.speed),
        item(Icons.volume_up_rounded, 'الصوت', () => _pickTool(Tool.volume)),
        item(Icons.copy_rounded, 'تكرار', _dupSel),
        item(Icons.chevron_left_rounded, 'تحريك ←', () => _moveSel(-1)),
        item(Icons.chevron_right_rounded, 'تحريك →', () => _moveSel(1)),
        item(Icons.delete_outline_rounded, 'حذف', _deleteSel, col: K.danger),
      ];
    } else {
      items = [
        if (!photo) item(Icons.content_cut_rounded, 'تعديل', () => _pickTool(Tool.edit)),
        if (!photo) item(Icons.music_note_rounded, 'الصوت', () => _pickTool(Tool.music), on: tool == Tool.music || tool == Tool.volume),
        item(Icons.title_rounded, 'النص', () => _editOverlay()),
        if (!photo) item(Icons.compare_rounded, 'الانتقالات', () => _pickTool(Tool.transitions), on: tool == Tool.transitions),
        item(Icons.auto_fix_high_rounded, 'الفلاتر', () => _pickTool(Tool.filters), on: tool == Tool.filters),
        item(Icons.tune_rounded, 'ضبط', () => _pickTool(Tool.adjust), on: tool == Tool.adjust),
        item(Icons.emoji_emotions_outlined, 'ملصقات', () => _editOverlay(sticker: true)),
        item(Icons.aspect_ratio_rounded, 'النسبة', () => _pickTool(Tool.ratio), on: tool == Tool.ratio),
        item(Icons.blur_on_rounded, 'الخلفية', () => _pickTool(Tool.background), on: tool == Tool.background),
        if (!photo) item(Icons.add_photo_alternate_outlined, 'إضافة', _addClips),
      ];
    }
    return Container(
      height: 80,
      decoration: const BoxDecoration(color: K.d1, border: Border(top: BorderSide(color: K.d2))),
      child: ListView(scrollDirection: Axis.horizontal, children: items),
    );
  }
}

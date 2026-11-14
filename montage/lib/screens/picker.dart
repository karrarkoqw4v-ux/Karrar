import 'dart:typed_data';
import 'package:flutter/material.dart';
import '../media.dart';
import '../models.dart';
import '../theme.dart';

export '../media.dart' show PickKind;

Future<List<MClip>?> pickMedia(BuildContext context, {PickKind kind = PickKind.both, bool single = false}) {
  return Navigator.of(context).push<List<MClip>>(MaterialPageRoute(fullscreenDialog: true, builder: (_) => MediaPickerPage(kind: kind, single: single)));
}

class MediaPickerPage extends StatefulWidget {
  final PickKind kind;
  final bool single;
  const MediaPickerPage({super.key, this.kind = PickKind.both, this.single = false});
  @override
  State<MediaPickerPage> createState() => _MediaPickerPageState();
}

class _MediaPickerPageState extends State<MediaPickerPage> {
  bool loading = true;
  bool denied = false;
  List<AlbumInfo> albums = [];
  AlbumInfo? album;
  final List<AssetInfo> items = [];
  int page = 0;
  bool more = true;
  late bool videoTab = widget.kind != PickKind.image;
  final List<AssetInfo> selected = [];
  final Map<String, Uint8List?> thumbs = {};
  final sc = ScrollController();
  bool importing = false;

  PickKind get activeKind => widget.kind == PickKind.both ? (videoTab ? PickKind.video : PickKind.image) : widget.kind;

  @override
  void initState() {
    super.initState();
    sc.addListener(() {
      if (sc.position.pixels > sc.position.maxScrollExtent - 600) _loadMore();
    });
    _init();
  }

  @override
  void dispose() {
    sc.dispose();
    super.dispose();
  }

  Future<void> _init() async {
    final ok = await MediaBackend.instance.requestAccess();
    if (!ok) {
      if (mounted) setState(() { loading = false; denied = true; });
      return;
    }
    await _loadAlbums();
  }

  Future<void> _loadAlbums() async {
    setState(() => loading = true);
    albums = await MediaBackend.instance.albums(activeKind);
    album = albums.isNotEmpty ? albums.first : null;
    items.clear();
    page = 0;
    more = true;
    if (album != null) await _loadMore();
    if (mounted) setState(() => loading = false);
  }

  Future<void> _loadMore() async {
    if (!more || album == null) return;
    final l = await MediaBackend.instance.assets(album!, activeKind, page, 120);
    if (l.length < 120) more = false;
    page++;
    items.addAll(l);
    if (mounted) setState(() {});
  }

  Future<void> _confirm() async {
    if (selected.isEmpty) return;
    setState(() => importing = true);
    final out = <MClip>[];
    for (final a in selected) {
      final path = await MediaBackend.instance.filePath(a);
      if (path == null) continue;
      final c = await MediaImport.fromPath(path, knownDuration: a.video ? a.duration : null);
      if (c != null) out.add(c);
    }
    if (!mounted) return;
    Navigator.of(context).pop(out);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: K.d0,
      body: SafeArea(
        child: Column(children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(14, 8, 14, 4),
            child: Row(children: [
              Tap(onTap: () => Navigator.of(context).pop(), child: const Padding(padding: EdgeInsets.all(8), child: Icon(Icons.close_rounded, color: Colors.white, size: 30))),
              const Spacer(),
              Tap(
                onTap: albums.isEmpty ? null : _albumSheet,
                child: Row(mainAxisSize: MainAxisSize.min, children: [
                  Container(width: 22, height: 22, decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(6)), child: const Icon(Icons.keyboard_arrow_down_rounded, size: 20, color: Colors.black)),
                  const SizedBox(width: 8),
                  Text(album?.name ?? 'الألبومات', style: tx(19, w: FontWeight.w800, color: Colors.white)),
                ]),
              ),
              const Spacer(),
              const SizedBox(width: 46),
            ]),
          ),
          if (widget.kind == PickKind.both)
            Container(
              decoration: const BoxDecoration(border: Border(bottom: BorderSide(color: K.d2))),
              child: Row(children: [
                _tab('مقاطع الفيديو', videoTab, () { if (!videoTab) { videoTab = true; selected.clear(); _loadAlbums(); } }),
                _tab('الصور', !videoTab, () { if (videoTab) { videoTab = false; selected.clear(); _loadAlbums(); } }),
              ]),
            ),
          Expanded(child: _grid()),
          Container(
            color: K.d0,
            padding: const EdgeInsets.fromLTRB(16, 10, 16, 14),
            child: Row(children: [
              Expanded(child: Text(selected.isEmpty ? (widget.single ? 'اختر عنصراً واحداً' : 'اختر الوسائط') : 'تم اختيار ${selected.length}', style: tx(14, color: K.dsub))),
              Tap(
                scale: 0.94,
                onTap: selected.isEmpty || importing ? null : _confirm,
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 30, vertical: 12),
                  decoration: BoxDecoration(color: selected.isEmpty ? const Color(0xFF1F3C42) : K.accent, borderRadius: BorderRadius.circular(12)),
                  child: importing ? const SizedBox(width: 22, height: 22, child: CircularProgressIndicator(strokeWidth: 2.4, color: Colors.white)) : Text('إضافة', style: tx(17, w: FontWeight.w800, color: selected.isEmpty ? Colors.white38 : Colors.white)),
                ),
              ),
            ]),
          ),
        ]),
      ),
    );
  }

  Widget _tab(String t, bool on, VoidCallback f) => Expanded(
        child: Tap(
          scale: 0.95,
          onTap: f,
          child: Container(
            alignment: Alignment.center,
            padding: const EdgeInsets.symmetric(vertical: 13),
            decoration: BoxDecoration(border: Border(bottom: BorderSide(color: on ? K.accent : Colors.transparent, width: 3))),
            child: Text(t, style: tx(17, w: on ? FontWeight.w800 : FontWeight.w700, color: on ? K.accent : K.dsub)),
          ),
        ),
      );

  Widget _grid() {
    if (loading) return const Center(child: CircularProgressIndicator(color: K.accent));
    if (denied) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(28),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            const Icon(Icons.photo_library_outlined, size: 56, color: K.dsub),
            const SizedBox(height: 14),
            Text('نحتاج إذن الوصول إلى الصور والفيديو لعرض معرضك', textAlign: TextAlign.center, style: tx(16, color: Colors.white, h: 1.7)),
            const SizedBox(height: 18),
            PillButton('السماح بالوصول', fillColor: K.accent, onTap: () async { await MediaBackend.instance.openSettings(); }),
            const SizedBox(height: 10),
            PillButton('اختيار من النظام', filled: false, onTap: () async {
              final l = await MediaImport.systemPick();
              if (mounted) Navigator.of(context).pop(l);
            }),
          ]),
        ),
      );
    }
    if (items.isEmpty) return Center(child: Text('لا توجد وسائط', style: tx(15, color: K.dsub)));
    return GridView.builder(
      controller: sc,
      padding: const EdgeInsets.all(1),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(crossAxisCount: 4, mainAxisSpacing: 2, crossAxisSpacing: 2),
      itemCount: items.length,
      itemBuilder: (_, i) {
        final a = items[i];
        final idx = selected.indexWhere((x) => x.id == a.id);
        return Tap(
          scale: 0.94,
          haptic: false,
          onTap: () => setState(() {
            if (idx >= 0) {
              selected.removeAt(idx);
            } else if (widget.single) {
              selected..clear()..add(a);
            } else {
              selected.add(a);
            }
          }),
          child: Stack(fit: StackFit.expand, children: [
            FutureBuilder<Uint8List?>(
              future: thumbs.containsKey(a.id) ? Future.value(thumbs[a.id]) : MediaBackend.instance.thumb(a).then((v) => thumbs[a.id] = v),
              builder: (_, s) => s.data == null ? Container(color: K.d2) : Image.memory(s.data!, fit: BoxFit.cover, gaplessPlayback: true),
            ),
            if (idx >= 0) Container(color: Colors.black38),
            Positioned(
              top: 6,
              left: 6,
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 160),
                width: 26,
                height: 26,
                decoration: BoxDecoration(color: idx >= 0 ? K.accent : Colors.black26, shape: BoxShape.circle, border: Border.all(color: Colors.white, width: 2)),
                alignment: Alignment.center,
                child: idx >= 0 ? Text('${idx + 1}', style: tx(13, w: FontWeight.w800, color: Colors.white)) : null,
              ),
            ),
            if (a.video) Positioned(left: 6, bottom: 4, child: Text(fmtDur(a.duration), style: tx(14, w: FontWeight.w800, color: Colors.white, ).copyWith(shadows: const [Shadow(color: Colors.black87, blurRadius: 4)]))),
          ]),
        );
      },
    );
  }

  void _albumSheet() {
    showModalBottomSheet(
      context: context,
      backgroundColor: K.d1,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(22))),
      builder: (ctx) => SafeArea(
        child: ListView(shrinkWrap: true, children: [
          for (final a in albums)
            ListTile(
              title: Text(a.name, style: tx(17, color: Colors.white)),
              trailing: Text('${a.count}', style: tx(14, color: K.dsub)),
              onTap: () async {
                Navigator.pop(ctx);
                setState(() { album = a; items.clear(); page = 0; more = true; });
                await _loadMore();
              },
            ),
        ]),
      ),
    );
  }
}

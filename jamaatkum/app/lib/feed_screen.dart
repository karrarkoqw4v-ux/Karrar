import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:video_player/video_player.dart';
import 'api.dart';
import 'chat_list_screen.dart';
import 'profile_screen.dart';
import 'theme.dart';

/// أرقام مختصرة بالعربي: 164000 -> ١٦٤ ألف
String fmt(int n) {
  const d = '٠١٢٣٤٥٦٧٨٩';
  String ar(String s) => s.split('').map((c) => int.tryParse(c) != null ? d[int.parse(c)] : (c == '.' ? '٫' : c)).join();
  if (n >= 1000) {
    final v = n / 1000;
    return '${ar(v >= 100 || v == v.roundToDouble() ? v.round().toString() : v.toStringAsFixed(1))} ألف';
  }
  return ar(n.toString());
}

/// الشاشة الرئيسية: الفيديوهات + الشريط السفلي (تصميم N2: عائم دائري)
class FeedScreen extends StatefulWidget {
  const FeedScreen({super.key});
  @override
  State<FeedScreen> createState() => _FeedScreenState();
}

class _FeedScreenState extends State<FeedScreen> {
  int _tab = 0; // 0 رئيسية، 1 ريلز، 2 رسائل، 3 بحث، 4 أنا
  late Future<List<dynamic>> _videos;

  @override
  void initState() {
    super.initState();
    _videos = http.get(Uri.parse('$apiBase/videos')).then((r) => jsonDecode(utf8.decode(r.bodyBytes)) as List);
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        extendBody: true,
        body: Stack(children: [
          Positioned.fill(
            child: IndexedStack(index: switch (_tab) { 0 => 0, 2 => 3, 4 => 2, _ => 1 }, children: [
              FutureBuilder<List<dynamic>>(
                future: _videos,
                builder: (c, s) {
                  if (!s.hasData) return const Center(child: CircularProgressIndicator());
                  return PageView.builder(
                    scrollDirection: Axis.vertical,
                    itemCount: s.data!.length,
                    itemBuilder: (c, i) => VideoPage(video: s.data![i], active: _tab == 0),
                  );
                },
              ),
              const Center(child: Text('قريباً', style: TextStyle(color: Colors.white70))),
              const ProfileScreen(),
              const ChatListScreen(),
            ]),
          ),
          Positioned(left: 14, right: 14, bottom: 12 + MediaQuery.paddingOf(context).bottom, child: _NavBar(index: _tab, onTap: (i) => setState(() => _tab = i))),
        ]),
      );
}

class _NavBar extends StatelessWidget {
  final int index;
  final ValueChanged<int> onTap;
  const _NavBar({required this.index, required this.onTap});

  @override
  Widget build(BuildContext context) {
    // الترتيب من اليمين: رئيسية، ريلز، رسائل، بحث، أنا
    Widget item(int i, Widget icon, {bool dot = false}) => GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTap: () => onTap(i),
          child: Container(
            width: 46,
            height: 46,
            decoration: i == index ? BoxDecoration(color: brand, shape: BoxShape.circle, ) : null,
            child: Stack(clipBehavior: Clip.none, alignment: Alignment.center, children: [
              IconTheme(data: IconThemeData(color: i == index ? onBrand : Colors.white), child: icon),
              if (dot)
                Positioned(top: 8, left: 8, child: Container(width: 10, height: 10, decoration: BoxDecoration(color: brand, shape: BoxShape.circle, border: Border.all(color: const Color(0xFF16161A), width: 2))))
            ]),
          ),
        );
    return Container(
      height: 62,
      padding: const EdgeInsets.symmetric(horizontal: 10),
      decoration: BoxDecoration(
        color: const Color(0xEE16161A),
        borderRadius: BorderRadius.circular(32),
        border: Border.all(color: Colors.white12),
        boxShadow: const [BoxShadow(color: Colors.black54, blurRadius: 30, offset: Offset(0, 10))],
      ),
      child: Row(mainAxisAlignment: MainAxisAlignment.spaceAround, children: [
        item(0, Icon(index == 0 ? Icons.home_rounded : Icons.home_outlined, size: 26)),
        item(1, const Icon(Icons.slow_motion_video_outlined, size: 26)),
        item(2, const Icon(Icons.send_outlined, size: 24), dot: true),
        item(3, const Icon(Icons.search_rounded, size: 26)),
        item(
          4,
          Container(
            width: 28,
            height: 28,
            decoration: BoxDecoration(shape: BoxShape.circle, color: fieldFill, border: Border.all(color: Colors.white, width: 2)),
            child: const Icon(Icons.person, size: 16, color: Colors.white70),
          ),
        ),
      ]),
    );
  }
}

class VideoPage extends StatefulWidget {
  final Map<String, dynamic> video;
  final bool active;
  const VideoPage({super.key, required this.video, this.active = true});
  @override
  State<VideoPage> createState() => _VideoPageState();
}

class _VideoPageState extends State<VideoPage> {
  late VideoPlayerController _ctrl;
  late int likes = widget.video['likes'];
  bool liked = false, saved = false;

  @override
  void initState() {
    super.initState();
    _ctrl = VideoPlayerController.networkUrl(Uri.parse(widget.video['url']))
      ..initialize().then((_) {
        _ctrl.setLooping(true);
        if (widget.active) _ctrl.play();
        if (mounted) setState(() {});
      });
  }

  @override
  void didUpdateWidget(VideoPage old) {
    super.didUpdateWidget(old);
    if (widget.active != old.active && _ctrl.value.isInitialized) widget.active ? _ctrl.play() : _ctrl.pause();
  }

  @override
  void dispose() {
    _ctrl.dispose();
    super.dispose();
  }

  Future<void> _like() async {
    if (liked) return;
    setState(() { liked = true; likes++; });
    try {
      final r = await http.post(Uri.parse('$apiBase/videos/${widget.video['id']}/like'));
      if (mounted) setState(() => likes = jsonDecode(r.body)['likes']);
    } catch (_) {}
  }

  // زر من أزرار الجنب (تصميم R1: أيقونة بيضا + رقم)
  Widget _btn(IconData icon, String label, {Color? color, VoidCallback? onTap}) => GestureDetector(
        onTap: onTap,
        behavior: HitTestBehavior.opaque,
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          Icon(icon, size: 32, color: color ?? Colors.white, shadows: const [Shadow(color: Colors.black45, blurRadius: 8)]),
          const SizedBox(height: 3),
          Text(label, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, shadows: [Shadow(color: Colors.black54, blurRadius: 6)])),
        ]),
      );

  @override
  Widget build(BuildContext context) {
    final bottom = 62 + 12 + MediaQuery.paddingOf(context).bottom + 16; // فوق الشريط السفلي
    return Stack(fit: StackFit.expand, children: [
      _ctrl.value.isInitialized
          ? FittedBox(fit: BoxFit.cover, child: SizedBox(width: _ctrl.value.size.width, height: _ctrl.value.size.height, child: VideoPlayer(_ctrl)))
          : const Center(child: CircularProgressIndicator()),
      // تدرج سفلي عشان النصوص تبان
      const Positioned.fill(child: DecoratedBox(decoration: BoxDecoration(gradient: LinearGradient(begin: Alignment.center, end: Alignment.bottomCenter, colors: [Colors.transparent, Color(0xB3000000)])))),
      Positioned(
        left: 10,
        bottom: bottom,
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          _btn(liked ? Icons.favorite : Icons.favorite_border, fmt(likes), color: liked ? likeColor : null, onTap: _like),
          const SizedBox(height: 20),
          _btn(Icons.mode_comment_outlined, fmt(0)),
          const SizedBox(height: 20),
          _btn(Icons.repeat_rounded, fmt(0)),
          const SizedBox(height: 20),
          _btn(Icons.send_outlined, fmt(0)),
          const SizedBox(height: 20),
          _btn(saved ? Icons.bookmark : Icons.bookmark_border, '', color: saved ? Colors.white : null, onTap: () => setState(() => saved = !saved)),
          const SizedBox(height: 14),
          const Icon(Icons.notes_rounded, size: 28),
        ]),
      ),
      Positioned(
        right: 16,
        left: 80,
        bottom: bottom,
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisSize: MainAxisSize.min, children: [
          Text('@${widget.video['author']}', style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
          const SizedBox(height: 4),
          Text(widget.video['title']),
        ]),
      ),
    ]);
  }
}

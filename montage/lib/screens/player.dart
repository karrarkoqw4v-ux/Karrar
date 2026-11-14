import 'dart:io';
import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';
import '../theme.dart';

class PlayerPage extends StatefulWidget {
  final String path;
  final String title;
  const PlayerPage({super.key, required this.path, required this.title});
  @override
  State<PlayerPage> createState() => _PlayerPageState();
}

class _PlayerPageState extends State<PlayerPage> {
  VideoPlayerController? _c;
  bool _ready = false;
  bool get isImage => widget.path.endsWith('.jpg') || widget.path.endsWith('.png') || widget.path.endsWith('.gif');

  @override
  void initState() {
    super.initState();
    if (!isImage) {
      final c = VideoPlayerController.file(File(widget.path));
      _c = c;
      c.initialize().then((_) {
        if (!mounted) return;
        setState(() => _ready = true);
        c.setLooping(true);
        c.play();
      });
      c.addListener(() { if (mounted) setState(() {}); });
    }
  }

  @override
  void dispose() {
    _c?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final c = _c;
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(backgroundColor: Colors.black, foregroundColor: Colors.white, title: Text(widget.title, style: tx(18, w: FontWeight.w800, color: Colors.white))),
      body: Column(children: [
        Expanded(child: Center(child: isImage ? Image.file(File(widget.path)) : (_ready && c != null ? AspectRatio(aspectRatio: c.value.aspectRatio, child: VideoPlayer(c)) : const CircularProgressIndicator(color: K.accent)))),
        if (!isImage && _ready && c != null) ...[
          Slider(
            activeColor: K.accent,
            value: c.value.position.inMilliseconds.clamp(0, c.value.duration.inMilliseconds).toDouble(),
            max: c.value.duration.inMilliseconds.toDouble(),
            onChanged: (x) => c.seekTo(Duration(milliseconds: x.toInt())),
          ),
          Padding(
            padding: const EdgeInsets.only(bottom: 28),
            child: Tap(onTap: () => c.value.isPlaying ? c.pause() : c.play(), child: Container(width: 66, height: 66, decoration: const BoxDecoration(color: K.accent, shape: BoxShape.circle), child: Icon(c.value.isPlaying ? Icons.pause_rounded : Icons.play_arrow_rounded, size: 40, color: Colors.white))),
          ),
        ],
      ]),
    );
  }
}

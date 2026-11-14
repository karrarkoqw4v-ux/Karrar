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
  late final VideoPlayerController _c = VideoPlayerController.file(File(widget.path));
  bool _ready = false;

  @override
  void initState() {
    super.initState();
    _c.initialize().then((_) {
      if (!mounted) return;
      setState(() => _ready = true);
      _c.play();
    });
    _c.addListener(() { if (mounted) setState(() {}); });
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final v = _c.value;
    return Scaffold(
      appBar: AppBar(title: Text(widget.title)),
      body: Column(children: [
        Expanded(child: Center(child: _ready ? AspectRatio(aspectRatio: v.aspectRatio, child: VideoPlayer(_c)) : const CircularProgressIndicator())),
        if (_ready) ...[
          Slider(
            value: v.position.inMilliseconds.clamp(0, v.duration.inMilliseconds).toDouble(),
            max: v.duration.inMilliseconds.toDouble(),
            onChanged: (x) => _c.seekTo(Duration(milliseconds: x.toInt())),
          ),
          Padding(padding: const EdgeInsets.only(bottom: 24), child: IconButton.filled(
            style: IconButton.styleFrom(backgroundColor: C.accent, minimumSize: const Size(64, 64)),
            iconSize: 34,
            onPressed: () => v.isPlaying ? _c.pause() : _c.play(),
            icon: Icon(v.isPlaying ? Icons.pause : Icons.play_arrow_rounded),
          )),
        ],
      ]),
    );
  }
}

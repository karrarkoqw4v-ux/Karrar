import 'dart:io';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import 'package:video_player/video_player.dart';
import 'export.dart';
import 'models.dart';

const _videoExt = ['mp4', 'mov', 'mkv', 'webm', '3gp', 'avi', 'm4v', 'mpeg', 'ts'];

class MediaImport {
  static final _picker = ImagePicker();

  static bool isVideoPath(String p) => _videoExt.contains(p.split('.').last.toLowerCase());

  /// يفتح معرض الجهاز ويرجع مقاطع جاهزة (مع المدة والصورة المصغّرة).
  static Future<List<MClip>> pick(BuildContext context) async {
    List<XFile> files;
    try {
      files = await _picker.pickMultipleMedia();
    } catch (_) {
      return [];
    }
    final out = <MClip>[];
    for (final f in files) {
      final c = await fromPath(f.path);
      if (c != null) out.add(c);
    }
    return out;
  }

  static Future<MClip?> fromPath(String path) async {
    final id = '${DateTime.now().microsecondsSinceEpoch}${path.hashCode.abs() % 997}';
    if (isVideoPath(path)) {
      double dur = 0;
      final vc = VideoPlayerController.file(File(path));
      try {
        await vc.initialize();
        dur = vc.value.duration.inMilliseconds / 1000.0;
      } catch (_) {
        return null;
      } finally {
        await vc.dispose();
      }
      if (dur <= 0) return null;
      final th = await Exporter.thumb(path, at: dur > 1 ? 0.4 : 0.0);
      return MClip(id: id, path: path, type: ClipType.video, srcDur: dur, start: 0, end: dur, thumb: th);
    }
    final th = await Exporter.thumb(path, isVideo: false);
    return MClip(id: id, path: path, type: ClipType.image, imageDur: 3, thumb: th ?? path);
  }
}

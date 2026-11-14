import 'dart:async';
import 'package:ffmpeg_kit_flutter_new/ffmpeg_kit.dart';
import 'package:ffmpeg_kit_flutter_new/ffprobe_kit.dart';
import 'package:ffmpeg_kit_flutter_new/return_code.dart';
import 'export.dart';

/// تنفيذ FFmpeg على الجهاز عبر ffmpeg_kit
class KitRunner implements FfRunner {
  @override
  Future<bool> run(List<String> args, {void Function(double seconds)? onTime, void Function(String line)? onLog}) async {
    final c = Completer<bool>();
    await FFmpegKit.executeWithArgumentsAsync(args, (s) async {
      final rc = await s.getReturnCode();
      if (!c.isCompleted) c.complete(ReturnCode.isSuccess(rc));
    }, (l) => onLog?.call(l.getMessage()), (st) => onTime?.call(st.getTime() / 1000.0));
    return c.future;
  }

  @override
  Future<bool> hasAudio(String path) async {
    try {
      final s = await FFprobeKit.getMediaInformation(path);
      final info = s.getMediaInformation();
      if (info == null) return false;
      for (final st in info.getStreams()) {
        if (st.getType() == 'audio') return true;
      }
    } catch (_) {}
    return false;
  }

  @override
  Future<MInfo> probe(String path) async {
    var w = 0, h = 0;
    var dur = 0.0;
    var hv = false, ha = false;
    try {
      final s = await FFprobeKit.getMediaInformation(path);
      final info = s.getMediaInformation();
      if (info != null) {
        dur = double.tryParse(info.getDuration() ?? '') ?? 0;
        for (final st in info.getStreams()) {
          if (st.getType() == 'video' && !hv) {
            hv = true;
            w = st.getWidth() ?? 0;
            h = st.getHeight() ?? 0;
          } else if (st.getType() == 'audio') {
            ha = true;
          }
        }
      }
    } catch (_) {}
    return MInfo(w, h, dur, hv, ha);
  }
}

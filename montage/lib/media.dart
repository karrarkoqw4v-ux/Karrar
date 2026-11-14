import 'dart:io';
import 'dart:typed_data';
import 'package:image_picker/image_picker.dart';
import 'package:photo_manager/photo_manager.dart';
import 'package:video_player/video_player.dart';
import 'export.dart';
import 'models.dart';

enum PickKind { both, video, image }

class AlbumInfo {
  final String id;
  final String name;
  final int count;
  final dynamic native;
  AlbumInfo(this.id, this.name, this.count, [this.native]);
}

class AssetInfo {
  final String id;
  final bool video;
  final double duration;
  final dynamic native;
  AssetInfo(this.id, this.video, this.duration, [this.native]);
}

/// واجهة الوسائط (على الجهاز: photo_manager، وفي الاختبار: بيانات مولَّدة)
abstract class MediaBackend {
  static late MediaBackend instance;
  Future<bool> requestAccess();
  Future<void> openSettings();
  Future<List<AlbumInfo>> albums(PickKind kind);
  Future<List<AssetInfo>> assets(AlbumInfo album, PickKind kind, int page, int size);
  Future<Uint8List?> thumb(AssetInfo a);
  Future<String?> filePath(AssetInfo a);
}

class PhotoManagerBackend implements MediaBackend {
  RequestType _t(PickKind k) => k == PickKind.video ? RequestType.video : (k == PickKind.image ? RequestType.image : RequestType.common);

  @override
  Future<bool> requestAccess() async {
    final s = await PhotoManager.requestPermissionExtend();
    return s.hasAccess;
  }

  @override
  Future<void> openSettings() => PhotoManager.openSetting();

  @override
  Future<List<AlbumInfo>> albums(PickKind kind) async {
    final l = await PhotoManager.getAssetPathList(type: _t(kind), onlyAll: false);
    final out = <AlbumInfo>[];
    for (final a in l) {
      final n = await a.assetCountAsync;
      if (n > 0) out.add(AlbumInfo(a.id, a.isAll ? 'كل الوسائط' : a.name, n, a));
    }
    out.sort((a, b) => a.name == 'كل الوسائط' ? -1 : (b.name == 'كل الوسائط' ? 1 : b.count.compareTo(a.count)));
    return out;
  }

  @override
  Future<List<AssetInfo>> assets(AlbumInfo album, PickKind kind, int page, int size) async {
    final p = album.native as AssetPathEntity;
    final l = await p.getAssetListPaged(page: page, size: size);
    return l.map((e) => AssetInfo(e.id, e.type == AssetType.video, e.videoDuration.inMilliseconds / 1000.0, e)).toList();
  }

  @override
  Future<Uint8List?> thumb(AssetInfo a) => (a.native as AssetEntity).thumbnailDataWithSize(const ThumbnailSize.square(240));

  @override
  Future<String?> filePath(AssetInfo a) async => (await (a.native as AssetEntity).originFile)?.path;
}

const _videoExt = ['mp4', 'mov', 'mkv', 'webm', '3gp', 'avi', 'm4v', 'mpeg', 'ts'];

class MediaImport {
  static bool isVideoPath(String p) => _videoExt.contains(p.split('.').last.toLowerCase());

  /// اختيار احتياطي من نظام أندرويد
  static Future<List<MClip>> systemPick() async {
    List<XFile> files;
    try {
      files = await ImagePicker().pickMultipleMedia();
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

  static Future<MClip?> fromPath(String path, {double? knownDuration}) async {
    final id = '${DateTime.now().microsecondsSinceEpoch}${path.hashCode.abs() % 997}';
    if (isVideoPath(path)) {
      double dur = knownDuration ?? 0;
      if (dur <= 0) {
        final vc = VideoPlayerController.file(File(path));
        try {
          await vc.initialize();
          dur = vc.value.duration.inMilliseconds / 1000.0;
        } catch (_) {
          return null;
        } finally {
          await vc.dispose();
        }
      }
      if (dur <= 0) return null;
      final th = await Exporter.thumb(path, at: dur > 1 ? 0.4 : 0.0);
      return MClip(id: id, path: path, type: ClipType.video, srcDur: dur, start: 0, end: dur, thumb: th);
    }
    final th = await Exporter.thumb(path, isVideo: false);
    return MClip(id: id, path: path, type: ClipType.image, imageDur: 3, thumb: th ?? path);
  }
}

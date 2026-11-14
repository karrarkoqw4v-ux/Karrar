import 'dart:convert';

enum ClipType { video, image }

class MClip {
  String id;
  String path;
  ClipType type;
  double srcDur; // مدة المصدر بالثواني (فيديو)
  double start;
  double end;
  double imageDur;
  double speed;
  double volume;
  String filter;
  String transition; // الانتقال بين هذا المقطع والذي بعده
  double transitionDur;
  String? thumb;

  MClip({
    required this.id,
    required this.path,
    required this.type,
    this.srcDur = 0,
    this.start = 0,
    this.end = 0,
    this.imageDur = 3,
    this.speed = 1,
    this.volume = 1,
    this.filter = 'normal',
    this.transition = 'fade',
    this.transitionDur = 0.5,
    this.thumb,
  });

  bool get isVideo => type == ClipType.video;
  double get effective => isVideo ? ((end - start) / speed) : imageDur;

  MClip copy() => MClip.fromJson(toJson())..id = '${DateTime.now().microsecondsSinceEpoch}';

  Map<String, dynamic> toJson() => {
        'id': id, 'path': path, 'type': type.name, 'srcDur': srcDur, 'start': start, 'end': end,
        'imageDur': imageDur, 'speed': speed, 'volume': volume, 'filter': filter,
        'transition': transition, 'transitionDur': transitionDur, 'thumb': thumb,
      };

  factory MClip.fromJson(Map<String, dynamic> j) => MClip(
        id: j['id'], path: j['path'], type: j['type'] == 'image' ? ClipType.image : ClipType.video,
        srcDur: (j['srcDur'] ?? 0).toDouble(), start: (j['start'] ?? 0).toDouble(), end: (j['end'] ?? 0).toDouble(),
        imageDur: (j['imageDur'] ?? 3).toDouble(), speed: (j['speed'] ?? 1).toDouble(), volume: (j['volume'] ?? 1).toDouble(),
        filter: j['filter'] ?? 'normal', transition: j['transition'] ?? 'fade', transitionDur: (j['transitionDur'] ?? 0.5).toDouble(),
        thumb: j['thumb'],
      );
}

/// نص أو ملصق فوق الفيديو. أحجام الخط بالنسبة لعرض 1080.
class OverlayItem {
  String id;
  bool sticker;
  String text;
  int color; // ARGB
  String font; // 'Tajawal' أو 'serif' أو 'monospace'
  double size;
  bool bold;
  bool box; // خلفية خلف النص
  int position; // 0 أعلى 1 وسط 2 أسفل
  String anim; // none fade slide pop type
  double start;
  double end;

  OverlayItem({
    required this.id,
    this.sticker = false,
    required this.text,
    this.color = 0xFFFFFFFF,
    this.font = 'Tajawal',
    this.size = 72,
    this.bold = true,
    this.box = false,
    this.position = 2,
    this.anim = 'fade',
    this.start = 0,
    this.end = 3,
  });

  Map<String, dynamic> toJson() => {
        'id': id, 'sticker': sticker, 'text': text, 'color': color, 'font': font, 'size': size, 'bold': bold,
        'box': box, 'position': position, 'anim': anim, 'start': start, 'end': end,
      };

  factory OverlayItem.fromJson(Map<String, dynamic> j) => OverlayItem(
        id: j['id'], sticker: j['sticker'] ?? false, text: j['text'] ?? '', color: j['color'] ?? 0xFFFFFFFF,
        font: j['font'] ?? 'Tajawal', size: (j['size'] ?? 72).toDouble(), bold: j['bold'] ?? true, box: j['box'] ?? false,
        position: j['position'] ?? 2, anim: j['anim'] ?? 'fade', start: (j['start'] ?? 0).toDouble(), end: (j['end'] ?? 3).toDouble(),
      );
}

class Project {
  String id;
  String name;
  DateTime createdAt;
  DateTime updatedAt;
  List<MClip> clips;
  List<OverlayItem> overlays;
  String? musicPath;
  String? musicName;
  double musicVolume;
  String? template;

  Project({
    required this.id,
    required this.name,
    DateTime? createdAt,
    DateTime? updatedAt,
    List<MClip>? clips,
    List<OverlayItem>? overlays,
    this.musicPath,
    this.musicName,
    this.musicVolume = 0.6,
    this.template,
  })  : createdAt = createdAt ?? DateTime.now(),
        updatedAt = updatedAt ?? DateTime.now(),
        clips = clips ?? [],
        overlays = overlays ?? [];

  /// الانتقال الفعلي بين المقطع i والمقطع i+1 (بالثواني)
  double joinDur(int i) {
    if (i < 0 || i >= clips.length - 1) return 0;
    final c = clips[i];
    final limit = 0.45 * (clips[i].effective < clips[i + 1].effective ? clips[i].effective : clips[i + 1].effective);
    if (c.transition == 'cut') return 0.04;
    final d = c.transitionDur < limit ? c.transitionDur : limit;
    return d < 0.04 ? 0.04 : d;
  }

  double get total {
    double t = 0;
    for (final c in clips) {
      t += c.effective;
    }
    for (var i = 0; i < clips.length - 1; i++) {
      t -= joinDur(i);
    }
    return t < 0 ? 0 : t;
  }

  Map<String, dynamic> toJson() => {
        'id': id, 'name': name, 'createdAt': createdAt.toIso8601String(), 'updatedAt': updatedAt.toIso8601String(),
        'clips': clips.map((c) => c.toJson()).toList(), 'overlays': overlays.map((o) => o.toJson()).toList(),
        'musicPath': musicPath, 'musicName': musicName, 'musicVolume': musicVolume, 'template': template,
      };

  factory Project.fromJson(Map<String, dynamic> j) => Project(
        id: j['id'], name: j['name'] ?? 'مشروع', createdAt: DateTime.tryParse(j['createdAt'] ?? ''), updatedAt: DateTime.tryParse(j['updatedAt'] ?? ''),
        clips: ((j['clips'] ?? []) as List).map((e) => MClip.fromJson(Map<String, dynamic>.from(e))).toList(),
        overlays: ((j['overlays'] ?? []) as List).map((e) => OverlayItem.fromJson(Map<String, dynamic>.from(e))).toList(),
        musicPath: j['musicPath'], musicName: j['musicName'], musicVolume: (j['musicVolume'] ?? 0.6).toDouble(), template: j['template'],
      );

  String encode() => jsonEncode(toJson());
}

class ExportRec {
  final String path;
  final String name;
  final DateTime at;
  final double duration;
  ExportRec(this.path, this.name, this.at, this.duration);
  Map<String, dynamic> toJson() => {'path': path, 'name': name, 'at': at.toIso8601String(), 'dur': duration};
  factory ExportRec.fromJson(Map<String, dynamic> j) => ExportRec(j['path'], j['name'] ?? '', DateTime.tryParse(j['at'] ?? '') ?? DateTime.now(), (j['dur'] ?? 0).toDouble());
}

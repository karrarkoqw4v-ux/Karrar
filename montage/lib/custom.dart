import 'dart:io';
import 'package:flutter/material.dart';
import 'export.dart';
import 'filters.dart';
import 'media.dart';
import 'toolrun.dart';

/// خيار داخل أداة المونتاج المخصص (اختيار واحد أو متعدد)
class JobOpt {
  final String key;
  final String label;
  final List<(String, String)> choices;
  final String def;
  final bool multi;
  final bool text; // حقل نص حر
  const JobOpt(this.key, this.label, this.choices, this.def, {this.multi = false, this.text = false});
}

class JobDef {
  final String id;
  final String group;
  final String title;
  final String sub;
  final IconData icon;
  final PickKind kind;
  final bool needsMusic;
  final List<JobOpt> opts;
  final In inp;
  final Extra extra;
  final Future<Spec> Function(Ctx c)? build;
  JobDef(this.id, this.group, this.title, this.sub, this.icon, this.kind, this.opts, {this.needsMusic = false, this.inp = In.both, this.extra = Extra.none, this.build});
}

const _frames = [('9:16', 'طولي 9:16'), ('1:1', 'مربع 1:1'), ('4:5', '4:5'), ('16:9', 'عرضي 16:9')];
const _colors = [
  ('0x000000', 'أسود'), ('0xFFFFFF', 'أبيض'), ('0xE53935', 'أحمر'), ('0xFDD835', 'أصفر'), ('0x43A047', 'أخضر'),
  ('0x1E88E5', 'أزرق'), ('0x8E24AA', 'بنفسجي'), ('0xFB8C00', 'برتقالي'), ('0xEC407A', 'وردي'),
];
const _tiers = [('720', 'HD 720'), ('1080', 'Full HD 1080'), ('1440', '2K'), ('2160', '4K')];
const _strength = [('1', 'خفيف'), ('2', 'متوسط'), ('3', 'قوي')];

final List<JobDef> kJobs = [
  JobDef('bg_color', 'الخلفية', 'تلوين الخلفية', 'ضع خلفية بلون تختاره حول الفيديو أو الصورة', Icons.format_color_fill_rounded, PickKind.both,
      [const JobOpt('color', 'لون الخلفية', _colors, '0x000000'), const JobOpt('frame', 'الإطار', _frames, '9:16')]),
  JobDef('bg_blur', 'الخلفية', 'تغبيش الخلفية', 'خلفية مموّهة من نفس المقطع', Icons.blur_on_rounded, PickKind.both,
      [const JobOpt('frame', 'الإطار', _frames, '9:16'), const JobOpt('blur', 'قوة التغبيش', _strength, '2')]),
  JobDef('bg_square', 'الخلفية', 'تغبيش الخلفية مع قص مربع', 'المقطع يُقصّ مربعاً في الوسط فوق خلفية مموّهة', Icons.crop_square_rounded, PickKind.both,
      [const JobOpt('frame', 'الإطار', [('9:16', 'طولي 9:16'), ('4:5', '4:5'), ('16:9', 'عرضي 16:9')], '9:16'), const JobOpt('blur', 'قوة التغبيش', _strength, '2')]),
  JobDef('music_freq', 'الموسيقى', 'موسيقى مع ترددات', 'أعمدة ترددات متحركة مع الموسيقى', Icons.graphic_eq_rounded, PickKind.both,
      [const JobOpt('frame', 'الإطار', [('9:16', 'طولي 9:16'), ('1:1', 'مربع 1:1'), ('16:9', 'عرضي 16:9')], '9:16'),
       const JobOpt('fcolor', 'لون الترددات', [('0x45C4DB', 'سماوي'), ('0xFDD835', 'أصفر'), ('0x43A047', 'أخضر'), ('0xE53935', 'أحمر'), ('0xFFFFFF', 'أبيض')], '0x45C4DB')],
      needsMusic: true),
  JobDef('music_fade', 'الموسيقى', 'موسيقى مع تلاشي الصوت', 'دخول وخروج تدريجي للموسيقى', Icons.volume_down_rounded, PickKind.both,
      [const JobOpt('fade', 'مدة التلاشي', [('1', 'ثانية'), ('2', 'ثانيتان'), ('4', '4 ثوانٍ')], '2')], needsMusic: true),
  JobDef('music_loud', 'الموسيقى', 'موسيقى بصوت عالٍ', 'ترفع الموسيقى مع حماية من التشويه', Icons.volume_up_rounded, PickKind.both,
      [const JobOpt('gain', 'مستوى الرفع', [('2', 'عالٍ'), ('3', 'أعلى'), ('4', 'أقصى')], '3')], needsMusic: true),
  JobDef('q_filter', 'الجودة', 'فلاتر بدقة عالية', 'طبّق فلتراً وصدّر بدقة 720 حتى 4K', Icons.auto_fix_high_rounded, PickKind.both,
      [JobOpt('filter', 'الفلتر', [for (final f in kFilters) if (f.vf != null) (f.id, f.name)], kFilters.firstWhere((f) => f.vf != null).id), const JobOpt('res', 'الدقة', _tiers, '1080')]),
  JobDef('q_enhance', 'الجودة', 'توضيح بأعلى دقة', 'يرفع الدقة ويزيل التشويش ويحدّد التفاصيل (برمجياً)', Icons.hd_rounded, PickKind.both,
      [const JobOpt('res', 'الدقة', _tiers, '2160'), const JobOpt('str', 'قوة التوضيح', _strength, '2')]),
  JobDef('q_real', 'الجودة', 'إضافة دقة واقعية', 'تفاصيل وحبيبات سينمائية خفيفة لمظهر طبيعي', Icons.high_quality_rounded, PickKind.both,
      [const JobOpt('res', 'الدقة', _tiers, '1440'), const JobOpt('str', 'القوة', _strength, '2')]),
  JobDef('resize', 'الحجم', 'تحديد الحجم', 'اقصص الصورة أو الفيديو على أكثر من حجم دفعة واحدة', Icons.aspect_ratio_rounded, PickKind.both,
      [const JobOpt('sizes', 'الأحجام', [
        ('1080x1080', 'مربع 1080'), ('1080x1350', 'إنستغرام 4:5'), ('1080x1920', 'ستوري / تيك توك'), ('1920x1080', 'يوتيوب 16:9'),
        ('1200x630', 'مشاركة رابط'), ('1280x720', 'HD 720'), ('800x800', 'صورة حساب'), ('1500x500', 'غلاف'),
      ], '1080x1080', multi: true),
       const JobOpt('mode', 'طريقة الملء', [('fill', 'قص'), ('fit', 'احتواء مع تغبيش')], 'fill')]),
  JobDef('sticker', 'ملصقات', 'صانع الملصقات', 'ملصقات تيليجرام وتيك توك بحركة وشفافية', Icons.emoji_emotions_rounded, PickKind.both,
      [const JobOpt('plat', 'المنصة', [
        ('tg_static', 'تيليجرام ثابت'), ('tg_anim', 'تيليجرام متحرك'), ('tg_emoji', 'تيليجرام مميز 100px'),
        ('tt_static', 'تيك توك ثابت'), ('tt_anim', 'تيك توك متحرك'),
      ], 'tg_anim'),
       const JobOpt('motion', 'الحركة', [('none', 'بدون'), ('bounce', 'قفز'), ('pulse', 'نبض'), ('shake', 'اهتزاز'), ('swing', 'تأرجح'), ('spin', 'دوران')], 'bounce'),
       const JobOpt('rm', 'إزالة خلفية بلون', [('none', 'لا'), ('white', 'أبيض'), ('black', 'أسود'), ('green', 'أخضر')], 'none'),
       const JobOpt('hd', 'تعلية الدقة والتوضيح', [('1', 'نعم'), ('0', 'لا')], '1')]),
];

class JobResult {
  final List<String> files;
  final String? error;
  JobResult(this.files, [this.error]);
}

String _f(double v) => v.toStringAsFixed(3);

(int, int) _frameDims(String r, {int base = 1080}) {
  switch (r) {
    case '1:1':
      return (base, base);
    case '4:5':
      return (base, (base * 5 / 4).round());
    case '16:9':
      return ((base * 16 / 9).round() ~/ 2 * 2, base);
    default:
      return (base, (base * 16 / 9).round() ~/ 2 * 2);
  }
}

String _short(String t) => "scale=w='if(gt(iw,ih),-2,$t)':h='if(gt(iw,ih),$t,-2)':flags=lanczos";

class JobRunner {
  /// يشغّل أداة على ملف واحد. [isVideo] لنوع المصدر، [dur] مدة الفيديو.
  static Future<JobResult> run(JobDef j, String src, bool isVideo, double dur, Map<String, String> p, {String? music, void Function(double)? onProgress, void Function(String)? onLog}) async {
    final ts = DateTime.now().millisecondsSinceEpoch;
    final out = Exporter.outDir.path;
    final runner = Exporter.runner;
    final log = StringBuffer();
    void lg(String l) {
      log.writeln(l);
      onLog?.call(l);
    }

    Future<JobResult> exec(List<String> a, String outFile, {double total = 0}) async {
      final ok = await runner.run(a, onTime: (s) {
        if (total > 0) onProgress?.call((s / total).clamp(0.0, 0.98));
      }, onLog: lg);
      if (ok && File(outFile).existsSync()) return JobResult([outFile]);
      final t = log.toString();
      return JobResult([], t.length > 600 ? t.substring(t.length - 600) : t);
    }

    final vEnc = ['-c:v', 'libx264', '-preset', 'veryfast', '-crf', '20', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '192k', '-movflags', '+faststart'];
    List<String> inp({double? t}) => isVideo ? ['-i', src] : (t != null ? ['-loop', '1', '-t', _f(t), '-i', src] : ['-i', src]);
    // يبني ناتج فيديو/صورة من فلتر نصّي واحد
    Future<JobResult> single(String vf, String tag, {bool keepAudio = true}) async {
      if (isVideo) {
        final f = '$out/montagi_${tag}_$ts.mp4';
        final hasA = keepAudio && await runner.hasAudio(src);
        return exec(['-y', '-hide_banner', '-i', src, '-vf', '$vf,setsar=1', if (!hasA) '-an', ...vEnc, f], f, total: dur);
      }
      final f = '$out/montagi_${tag}_$ts.jpg';
      return exec(['-y', '-hide_banner', '-i', src, '-vf', '$vf,setsar=1', '-frames:v', '1', '-q:v', '2', f], f);
    }

    String blurBg(int w, int h, int s) {
      final r = s == 1 ? 14 : (s == 2 ? 28 : 46);
      return 'scale=$w:$h:force_original_aspect_ratio=increase,crop=$w:$h,boxblur=$r:2';
    }

    switch (j.id) {
      case 'bg_color':
        {
          final (w, h) = _frameDims(p['frame']!);
          final c = p['color']!;
          return single('scale=$w:$h:force_original_aspect_ratio=decrease:flags=lanczos,pad=$w:$h:(ow-iw)/2:(oh-ih)/2:color=$c', 'bgcolor');
        }
      case 'bg_blur':
        {
          final (w, h) = _frameDims(p['frame']!);
          final vf = '[0:v]split[a][b];[a]${blurBg(w, h, int.parse(p['blur']!))}[bg];[b]scale=$w:$h:force_original_aspect_ratio=decrease:flags=lanczos[fg];[bg][fg]overlay=(W-w)/2:(H-h)/2';
          return _graph(j, vf, src, isVideo, dur, 'bgblur', ts, exec, vEnc);
        }
      case 'bg_square':
        {
          final (w, h) = _frameDims(p['frame']!);
          final sq = w < h ? w : h;
          final vf = "[0:v]split[a][b];[a]${blurBg(w, h, int.parse(p['blur']!))}[bg];[b]crop='min(iw,ih)':'min(iw,ih)',scale=$sq:$sq:flags=lanczos[fg];[bg][fg]overlay=(W-w)/2:(H-h)/2";
          return _graph(j, vf, src, isVideo, dur, 'bgsquare', ts, exec, vEnc);
        }
      case 'q_filter':
        {
          final f = filterById(p['filter']!).vf;
          final t = p['res']!;
          final vf = '${_short(t)}${f != null ? ',$f' : ''}';
          return single(vf, 'q${t}p');
        }
      case 'q_enhance':
        {
          final s = int.parse(p['str']!);
          final amt = [0.0, 0.6, 1.0, 1.5][s];
          final dn = [0.0, 0.8, 1.4, 2.2][s];
          return single('${_short(p['res']!)},hqdn3d=$dn:$dn:${dn * 4}:${dn * 4},unsharp=5:5:$amt:5:5:0.0,eq=contrast=1.03:saturation=1.05', 'enhance${p['res']}');
        }
      case 'q_real':
        {
          final s = int.parse(p['str']!);
          final amt = [0.0, 0.5, 0.9, 1.3][s];
          final gr = [0, 2, 4, 7][s];
          return single('${_short(p['res']!)},hqdn3d=1.5:1.2:3:3,unsharp=7:7:$amt:7:7:0.0,eq=contrast=1.05:saturation=1.08:gamma=1.02,noise=alls=$gr:allf=t', 'real${p['res']}');
        }
      case 'resize':
        {
          final sizes = p['sizes']!.split(',').where((e) => e.isNotEmpty).toList();
          if (sizes.isEmpty) return JobResult([], 'اختر حجماً واحداً على الأقل');
          final files = <String>[];
          for (var k = 0; k < sizes.length; k++) {
            final wh = sizes[k].split('x');
            final w = int.parse(wh[0]), h = int.parse(wh[1]);
            final String vf;
            if (p['mode'] == 'fit') {
              vf = '[0:v]split[a][b];[a]${blurBg(w, h, 2)}[bg];[b]scale=$w:$h:force_original_aspect_ratio=decrease:flags=lanczos[fg];[bg][fg]overlay=(W-w)/2:(H-h)/2';
            } else {
              vf = '[0:v]scale=$w:$h:force_original_aspect_ratio=increase:flags=lanczos,crop=$w:$h';
            }
            final ext = isVideo ? 'mp4' : 'jpg';
            final f = '$out/montagi_${w}x${h}_$ts.$ext';
            final a = ['-y', '-hide_banner', ...inp(), '-filter_complex', '$vf,setsar=1[v]', '-map', '[v]', if (isVideo) ...['-map', '0:a?'], ...(isVideo ? vEnc : ['-frames:v', '1', '-q:v', '2']), f];
            final r = await exec(a, f, total: dur * sizes.length);
            if (r.files.isEmpty) return r;
            files.addAll(r.files);
            onProgress?.call((k + 1) / sizes.length * .98);
          }
          return JobResult(files);
        }
      case 'music_freq':
      case 'music_fade':
      case 'music_loud':
        return _music(j, src, isVideo, dur, p, music, ts, exec, vEnc, runner);
      case 'sticker':
        return _sticker(src, isVideo, dur, p, ts, exec);
    }
    return JobResult([], 'أداة غير معروفة');
  }

  static Future<JobResult> _graph(JobDef j, String vf, String src, bool isVideo, double dur, String tag, int ts, Future<JobResult> Function(List<String>, String, {double total}) exec, List<String> vEnc) async {
    final out = Exporter.outDir.path;
    if (isVideo) {
      final f = '$out/montagi_${tag}_$ts.mp4';
      return exec(['-y', '-hide_banner', '-i', src, '-filter_complex', '$vf,setsar=1[v]', '-map', '[v]', '-map', '0:a?', ...vEnc, f], f, total: dur);
    }
    final f = '$out/montagi_${tag}_$ts.jpg';
    return exec(['-y', '-hide_banner', '-i', src, '-filter_complex', '$vf,setsar=1[v]', '-map', '[v]', '-frames:v', '1', '-q:v', '2', f], f);
  }

  static Future<JobResult> _music(JobDef j, String src, bool isVideo, double dur, Map<String, String> p, String? music, int ts, Future<JobResult> Function(List<String>, String, {double total}) exec, List<String> vEnc, FfRunner runner) async {
    if (music == null || !File(music).existsSync()) return JobResult([], 'اختر ملف موسيقى أولاً');
    final (w, h) = _frameDims(p['frame'] ?? '9:16', base: 720);
    final d = isVideo ? (dur <= 0 ? 10.0 : dur) : 15.0;
    final hasA = isVideo && await runner.hasAudio(src);
    String mchain;
    double srcVol = 0.5;
    switch (j.id) {
      case 'music_fade':
        final fd = double.parse(p['fade']!).clamp(0.5, d / 3);
        mchain = 'volume=0.9,afade=t=in:st=0:d=${_f(fd)},afade=t=out:st=${_f(d - fd)}:d=${_f(fd)}';
        break;
      case 'music_loud':
        final g = double.parse(p['gain']!);
        mchain = 'volume=$g,alimiter=limit=0.95';
        srcVol = 0.35;
        break;
      default:
        mchain = 'volume=1.0';
    }
    final g = StringBuffer();
    g.write('[0:v]scale=$w:$h:force_original_aspect_ratio=decrease,pad=$w:$h:(ow-iw)/2:(oh-ih)/2:color=black,setsar=1[base];');
    g.write('[1:a]aresample=44100,aformat=sample_fmts=fltp:channel_layouts=stereo,$mchain,atrim=0:${_f(d)},asetpts=PTS-STARTPTS[mus];');
    var aLab = 'mus';
    if (hasA) {
      g.write('[0:a]aresample=44100,aformat=sample_fmts=fltp:channel_layouts=stereo,volume=$srcVol[a0];[a0][mus]amix=inputs=2:duration=longest:dropout_transition=0,volume=1.6[mix];');
      aLab = 'mix';
    }
    var vLab = 'base';
    if (j.id == 'music_freq') {
      final fh = (h * 0.22).round();
      final col = p['fcolor']!;
      g.write('[$aLab]asplit[aout][afq];');
      g.write('[afq]showfreqs=s=${w}x$fh:mode=bar:fscale=log:ascale=sqrt:win_size=2048:colors=$col|$col,format=rgba,colorkey=black:0.18:0.1[fq];');
      g.write('[base][fq]overlay=0:H-h-${(h * 0.06).round()}:shortest=1[vv]');
      vLab = 'vv';
      aLab = 'aout';
    } else {
      g.write('[base]null[vv]');
      vLab = 'vv';
    }
    final f = '${Exporter.outDir.path}/montagi_${j.id}_$ts.mp4';
    final args = [
      '-y', '-hide_banner',
      if (isVideo) ...['-i', src] else ...['-loop', '1', '-framerate', '30', '-t', _f(d), '-i', src],
      '-stream_loop', '-1', '-i', music,
      '-filter_complex', g.toString(),
      '-map', '[$vLab]', '-map', '[$aLab]',
      '-t', _f(d),
      ...vEnc, f,
    ];
    return exec(args, f, total: d);
  }

  static Future<JobResult> _sticker(String src, bool isVideo, double dur, Map<String, String> p, int ts, Future<JobResult> Function(List<String>, String, {double total}) exec) async {
    final plat = p['plat']!;
    final motion = plat.endsWith('static') ? 'none' : p['motion']!;
    final anim = !plat.endsWith('static');
    final S = plat == 'tg_emoji' ? 100 : 512;
    final inner = anim && motion != 'none' ? (S * 0.8).round() : S;
    final rm = p['rm']!;
    final key = rm == 'white' ? 'colorkey=0xFFFFFF:0.28:0.12' : (rm == 'black' ? 'colorkey=0x000000:0.22:0.1' : (rm == 'green' ? 'colorkey=0x00FF00:0.35:0.15' : ''));
    final hd = p['hd'] == '1';
    final pre = StringBuffer('[0:v]format=rgba');
    if (key.isNotEmpty) pre.write(',$key');
    pre.write(',scale=$inner:$inner:force_original_aspect_ratio=decrease:flags=lanczos');
    if (hd) pre.write(',unsharp=5:5:0.9:5:5:0.0');
    pre.write(',format=rgba');
    final g = StringBuffer(pre.toString());
    final padFull = 'pad=$S:$S:(ow-iw)/2:(oh-ih)/2:color=black@0';
    switch (motion) {
      case 'bounce':
        g.write('[st];color=c=black@0:s=${S}x$S:r=30,format=rgba[cv];[cv][st]overlay=x=(W-w)/2:y=\'(H-h)/2-abs(sin(t*3.6))*H*0.1\':format=auto:shortest=1,format=rgba');
        break;
      case 'pulse':
        g.write(",$padFull,scale=w='iw*(1+0.1*sin(2*PI*t/1.2))':h=-1:eval=frame,format=rgba,pad=w=$S:h=$S:x='(ow-iw)/2':y='(oh-ih)/2':color=black@0,crop=$S:$S");
        break;
      case 'shake':
        g.write(",$padFull,rotate=a='0.14*sin(2*PI*t*2.2)':c=black@0:ow=$S:oh=$S");
        break;
      case 'swing':
        g.write(",$padFull,rotate=a='0.35*sin(2*PI*t/1.5)':c=black@0:ow=$S:oh=$S");
        break;
      case 'spin':
        g.write(",$padFull,rotate=a='2*PI*t/3':c=black@0:ow=$S:oh=$S");
        break;
      default:
        g.write(',$padFull');
    }
    g.write('[v]');
    final outDir = Exporter.outDir.path;
    final clip = (isVideo ? (dur > 0 ? (dur < 3 ? dur : 3.0) : 3.0) : 3.0);
    final input = anim
        ? (isVideo ? ['-t', _f(clip), '-i', src] : ['-loop', '1', '-framerate', '30', '-t', _f(clip), '-i', src])
        : ['-i', src];
    final base = ['-y', '-hide_banner', ...input, '-filter_complex', g.toString(), '-map', '[v]', '-an'];
    switch (plat) {
      case 'tg_static':
        final f = '$outDir/montagi_sticker_$ts.webp';
        return exec([...base, '-frames:v', '1', '-c:v', 'libwebp', '-lossless', '0', '-quality', '92', f], f);
      case 'tt_static':
        final f = '$outDir/montagi_sticker_$ts.png';
        return exec([...base, '-frames:v', '1', f], f);
      case 'tt_anim':
        final f = '$outDir/montagi_sticker_$ts.webp';
        return exec([...base, '-r', '20', '-t', _f(clip), '-c:v', 'libwebp_anim', '-loop', '0', '-lossless', '0', '-quality', '80', '-compression_level', '4', f], f, total: clip);
      default: // tg_anim / tg_emoji : WebM VP9 مع شفافية، أقل من 256KB
        final f = '$outDir/montagi_sticker_$ts.webm';
        JobResult? last;
        for (final b in [420, 300, 200, 130, 80]) {
          last = await exec([...base, '-r', '30', '-t', _f(clip), '-c:v', 'libvpx-vp9', '-pix_fmt', 'yuva420p', '-b:v', '${b}k', '-auto-alt-ref', '0', '-row-mt', '1', f], f, total: clip);
          if (last.files.isEmpty) return last;
          if (File(f).lengthSync() <= 256 * 1024) break;
        }
        return last!;
    }
  }
}

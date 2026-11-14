import 'dart:io';
import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'custom.dart';
import 'media.dart';
import 'toolrun.dart';

const sBasic = 'أساسيات الفيديو';
const sFx = 'تأثيرات الفيديو';
const sLook = 'الألوان والأجواء';
const sPart = 'جسيمات وأجواء متحركة';
const sOvl = 'إضافات على الفيديو';
const sBg = 'الخلفيات والإطارات';
const sAudio = 'الصوت';
const sConv = 'تحويل وضغط';
const sGif = 'GIF';
const sSoc = 'المنصات الاجتماعية';
const sImg = 'الصور';

/// ترتيب الأقسام وأيقوناتها
const kSectionOrder = [
  'الخلفية', 'الموسيقى', 'الجودة', 'الحجم', 'ملصقات',
  sBasic, sFx, sLook, sPart, sOvl, sBg, sAudio, sConv, sGif, sSoc, sImg,
];
const kSectionIcons = <String, IconData>{
  'الخلفية': Icons.format_color_fill_rounded,
  'الموسيقى': Icons.graphic_eq_rounded,
  'الجودة': Icons.hd_rounded,
  'الحجم': Icons.aspect_ratio_rounded,
  'ملصقات': Icons.emoji_emotions_rounded,
  sBasic: Icons.movie_edit,
  sFx: Icons.auto_fix_high_rounded,
  sLook: Icons.palette_rounded,
  sPart: Icons.ac_unit_rounded,
  sOvl: Icons.layers_rounded,
  sBg: Icons.wallpaper_rounded,
  sAudio: Icons.headphones_rounded,
  sConv: Icons.transform_rounded,
  sGif: Icons.gif_box_rounded,
  sSoc: Icons.share_rounded,
  sImg: Icons.image_rounded,
};

PickKind _pk(In i) => i == In.video || i == In.gif ? PickKind.video : (i == In.image ? PickKind.image : PickKind.both);

JobDef _t(String id, String sec, String title, In inp, Future<Spec> Function(Ctx c) b, {List<JobOpt> opts = const [], Extra extra = Extra.none, String sub = '', IconData? icon}) =>
    JobDef(id, sec, title, sub, icon ?? kSectionIcons[sec] ?? Icons.build_rounded, _pk(inp), opts, inp: inp, extra: extra, build: b);

// ---------- خيارات مشتركة ----------
JobOpt _c(String key, String label, List<(String, String)> ch, String def, {bool multi = false}) => JobOpt(key, label, ch, def, multi: multi);
JobOpt _txt(String key, String label, String def) => JobOpt(key, label, const [], def, text: true);
const _frame3 = [('9:16', 'طولي 9:16'), ('1:1', 'مربع 1:1'), ('16:9', 'عرضي 16:9')];
const _pos4 = [('tl', 'أعلى يسار'), ('tr', 'أعلى يمين'), ('bl', 'أسفل يسار'), ('br', 'أسفل يمين'), ('c', 'وسط')];
const _pos3 = [('top', 'أعلى'), ('mid', 'وسط'), ('bot', 'أسفل')];
const _colorsHex = [
  ('0xFFFFFF', 'أبيض'), ('0x000000', 'أسود'), ('0xFDD835', 'أصفر'), ('0xE53935', 'أحمر'),
  ('0x43A047', 'أخضر'), ('0x1E88E5', 'أزرق'), ('0xEC407A', 'وردي'), ('0xFB8C00', 'برتقالي'),
];
const _durs = [('5', '5 ث'), ('8', '8 ث'), ('12', '12 ث')];

(int, int) _fr(String f) => f == '1:1' ? (1080, 1080) : (f == '16:9' ? (1920, 1080) : (1080, 1920));

String _short(String t) => "scale=w='if(gt(iw,ih),-2,$t)':h='if(gt(iw,ih),$t,-2)':flags=lanczos";

String _fitBlur(int W, int H, {int blur = 28}) =>
    '[0:v]split[a][b];[a]scale=$W:$H:force_original_aspect_ratio=increase,crop=$W:$H,boxblur=$blur:2[bg];[b]scale=$W:$H:force_original_aspect_ratio=decrease:flags=lanczos[fg];[bg][fg]overlay=(W-w)/2:(H-h)/2';

int _hex(String s) => int.parse(s) | 0xFF000000;

/// إحداثيات overlay حسب موضع الزاوية
String _ovPos(String p, {int m = 36}) {
  switch (p) {
    case 'tl':
      return 'x=$m:y=$m';
    case 'tr':
      return 'x=W-w-$m:y=$m';
    case 'bl':
      return 'x=$m:y=H-h-$m';
    case 'br':
      return 'x=W-w-$m:y=H-h-$m';
    default:
      return 'x=(W-w)/2:y=(H-h)/2';
  }
}

String _yPos(String p) => p == 'top' ? 'y=H*0.08' : (p == 'bot' ? 'y=H-h-H*0.08' : 'y=(H-h)/2');

Spec _vf(String vf, {bool anim = false, List<String> pre = const [], double? dur, bool noAudio = false}) => Spec(vf: vf, anim: anim, pre: pre, dur: dur, noAudio: noAudio);

/// طبقة جسيمات تتحرك فوق الفيديو
Future<Spec> _particles(Ctx c, String kind, {required double speed, bool fall = true, String? baseVf, String? staticKind, String? post}) async {
  final tile = await c.tile(kind);
  final ins = ['-loop', '1', '-framerate', '30', '-i', tile];
  var fc = '[0:v]scale=${c.w}:${c.h},setsar=1${baseVf != null ? ',$baseVf' : ''}[b];';
  final y = fall ? "y='mod(t*$speed,${c.h})-${c.h}'" : "y='-mod(t*$speed,${c.h})'";
  fc += "[b][1:v]overlay=x=0:$y:format=auto:shortest=1";
  if (staticKind != null) {
    final st = await c.layer(staticKind);
    ins.addAll(['-loop', '1', '-framerate', '30', '-i', st]);
    fc += '[m];[m][2:v]overlay=0:0:format=auto:shortest=1';
  }
  if (post != null) fc += ',$post';
  fc += '[v]';
  return Spec(fc: fc, inputs: ins, anim: true, dur: c.isVideo ? null : c.n('dur', 6));
}

const _durOpt = JobOpt('dur', 'المدة (للصور)', _durs, '8');


// ---------- بانيات تتكرر ----------
Future<Spec> _bw(Ctx c) async => _vf('hue=s=0');
Future<Spec> _sketch(Ctx c) async => Spec(fc: '[0:v]scale=${c.w}:${c.h},split[a][b];[a]format=gray,negate,gblur=sigma=12[g];[b]format=gray[o];[o][g]blend=all_mode=dodge,eq=contrast=1.25,format=yuv420p[v]');
Future<Spec> _cartoon(Ctx c) async => Spec(
    fc: '[0:v]scale=${c.w}:${c.h},split[a][b];[a]edgedetect=low=0.1:high=0.25,negate,format=yuv420p[e];'
        "[b]lutrgb=r='trunc(val/40)*40+20':g='trunc(val/40)*40+20':b='trunc(val/40)*40+20',eq=saturation=1.5,hqdn3d=4,format=yuv420p[k];[k][e]blend=all_mode=multiply[v]");
Future<Spec> _flip(Ctx c) async => _vf(c.o('d') == 'v' ? 'vflip' : (c.o('d') == 'hv' ? 'hflip,vflip' : 'hflip'));
Future<Spec> _bright(Ctx c) async => _vf('eq=brightness=${c.o('b')}:contrast=${c.o('c')}:saturation=${c.o('s')}');
Future<Spec> _rot(Ctx c) async {
  final a = c.o('a');
  return _vf(a == '90cw' ? 'transpose=1' : (a == '90ccw' ? 'transpose=2' : 'hflip,vflip'));
}

const _brightOpts = [
  JobOpt('b', 'السطوع', [('-0.2', 'أغمق'), ('-0.1', 'أقل'), ('0', 'كما هو'), ('0.1', 'أعلى'), ('0.2', 'أفتح')], '0'),
  JobOpt('c', 'التباين', [('0.8', 'أقل'), ('1', 'كما هو'), ('1.2', 'أعلى'), ('1.4', 'قوي')], '1'),
  JobOpt('s', 'التشبع', [('0', 'أبيض وأسود'), ('0.7', 'أقل'), ('1', 'كما هو'), ('1.3', 'أعلى'), ('1.7', 'قوي')], '1'),
];

Future<Spec> _text(Ctx c, {required bool fadeIn}) async {
  final t = c.o('text').trim();
  if (t.isEmpty) throw ToolError('اكتب النص أولاً');
  final png = await c.text(t, size: c.n('size', 90), color: _hex(c.o('color')), box: c.o('box') == '1', maxW: c.w * 0.9);
  final pos = c.o('pos');
  final en = fadeIn ? ":enable='gte(t,0.3)'" : '';
  return Spec(fc: '[0:v]scale=${c.w}:${c.h}[b];[b][1:v]overlay=x=(W-w)/2:${_yPos(pos)}$en[v]', inputs: ['-i', png]);
}

const _textOpts = [
  JobOpt('text', 'النص', [], 'مونتاجي', text: true),
  JobOpt('size', 'الحجم', [('60', 'صغير'), ('90', 'متوسط'), ('130', 'كبير')], '90'),
  JobOpt('color', 'اللون', _colorsHex, '0xFFFFFF'),
  JobOpt('pos', 'المكان', _pos3, 'bot'),
  JobOpt('box', 'خلفية للنص', [('0', 'لا'), ('1', 'نعم')], '0'),
];

String _tf(double s) => s.toStringAsFixed(3);

Future<Spec> _concat(Ctx c, {bool slide = false}) async {
  if (c.files.length < 2) throw ToolError('اختر ملفين على الأقل');
  final W = c.w, H = c.h;
  final ins = <String>[];
  final g = StringBuffer();
  final secImg = c.n('sec', 3);
  final durs = <double>[];
  for (var i = 0; i < c.files.length; i++) {
    final img = isImagePath(c.files[i]);
    final double d = img ? secImg : (c.infos[i].dur > 0 ? c.infos[i].dur : 3.0);
    durs.add(d);
    ins.addAll(img ? ['-loop', '1', '-framerate', '30', '-t', _tf(d), '-i', c.files[i]] : ['-i', c.files[i]]);
    g.write('[$i:v]scale=$W:$H:force_original_aspect_ratio=decrease,pad=$W:$H:(ow-iw)/2:(oh-ih)/2:color=black,setsar=1,fps=30,format=yuv420p[v$i];');
  }
  final n = c.files.length;
  if (slide) {
    final fd = 0.5;
    var prev = 'v0';
    var off = durs[0] - fd;
    for (var i = 1; i < n; i++) {
      g.write('[$prev][v$i]xfade=transition=${c.o('tr')}:duration=$fd:offset=${_tf(off)}[x$i];');
      prev = 'x$i';
      off += durs[i] - fd;
    }
    g.write('[$prev]null[v]');
    return Spec(fc: g.toString(), allInputs: ins, noAudio: true, dur: durs.reduce((a, b) => a + b) - fd * (n - 1));
  }
  var anyA = false;
  for (var i = 0; i < n; i++) {
    final hasA = !isImagePath(c.files[i]) && c.infos[i].hasAudio;
    anyA = anyA || hasA;
    if (hasA) {
      g.write('[$i:a]aresample=44100,aformat=sample_fmts=fltp:channel_layouts=stereo,asetpts=PTS-STARTPTS[a$i];');
    } else {
      g.write('anullsrc=r=44100:cl=stereo,atrim=0:${_tf(durs[i])}[a$i];');
    }
  }
  for (var i = 0; i < n; i++) {
    g.write('[v$i][a$i]');
  }
  g.write('concat=n=$n:v=1:a=1[v][a]');
  return Spec(fc: g.toString(), allInputs: ins);
}

// ---------- الأدوات ----------
final List<JobDef> kNewTools = [
  // ================= أساسيات الفيديو =================
  _t('stab', sBasic, 'تثبيت الاهتزاز', In.video, (c) async {
    final trf = '${c.tmp}/s.trf';
    final ok = await c.run.run(['-y', '-hide_banner', '-i', c.src, '-vf', 'vidstabdetect=shakiness=6:accuracy=9:result=$trf', '-f', 'null', '-']);
    if (ok && File(trf).existsSync()) return _vf('vidstabtransform=input=$trf:smoothing=15:zoom=1:optzoom=1,unsharp=5:5:0.6');
    return _vf('deshake');
  }),
  _t('reverse', sBasic, 'عكس الفيديو', In.video, (c) async => Spec(vf: 'reverse', af: 'areverse', pre: const ['-t', '60']), sub: 'حتى 60 ثانية'),
  _t('slowmo', sBasic, 'حركة بطيئة سلسة', In.video, (c) async {
    final f = c.n('f', 2);
    return Spec(vf: "scale=-2:'min(720,ih)',setpts=${f}*PTS,minterpolate=fps=60", af: atempoChain(1 / f), pre: const ['-t', '20']);
  }, opts: [_c('f', 'درجة البطء', [('2', '2×'), ('4', '4×')], '2')], sub: 'حتى 20 ثانية'),
  _t('timelapse', sBasic, 'تسريع (تايم لابس)', In.video, (c) async => Spec(vf: 'setpts=PTS/${c.n('f', 4)}', noAudio: true, post: const ['-r', '30']),
      opts: [_c('f', 'السرعة', [('2', '2×'), ('4', '4×'), ('8', '8×'), ('16', '16×')], '4')]),
  _t('rotate', sBasic, 'تدوير الفيديو', In.both, _rot, opts: [_c('a', 'الاتجاه', [('90cw', '90° يمين'), ('90ccw', '90° يسار'), ('180', '180°')], '90cw')]),
  _t('flip', sBasic, 'قلب الفيديو', In.both, _flip, opts: [_c('d', 'الاتجاه', [('h', 'أفقي'), ('v', 'عمودي'), ('hv', 'الاثنان')], 'h')]),
  _t('trim', sBasic, 'قص الفيديو', In.video, (c) async {
    final s = c.n('a', 0), e = c.n('b', 5);
    if (e <= s) throw ToolError('نهاية القص يجب أن تكون بعد البداية');
    return Spec(pre: ['-ss', _tf(s), '-t', _tf(e - s)], dur: e - s);
  }, opts: [_txt('a', 'البداية (ثانية)', '0'), _txt('b', 'النهاية (ثانية)', '5')]),
  _t('loop', sBasic, 'تكرار المقطع (حلقة)', In.video, (c) async => Spec(pre: ['-stream_loop', '${c.n('n', 3).toInt() - 1}'], dur: c.dur * c.n('n', 3)),
      opts: [_c('n', 'عدد التكرار', [('2', '2'), ('3', '3'), ('5', '5'), ('10', '10')], '3')]),
  _t('bright', sBasic, 'ضبط السطوع والتباين', In.both, _bright, opts: _brightOpts),
  _t('cutframe', sBasic, 'تحديد نقطة القص بالإطار', In.video, (c) async {
    final a = c.n('a', 0).toInt(), b = c.n('b', 90).toInt();
    if (b <= a) throw ToolError('رقم الإطار الأخير يجب أن يكون بعد الأول');
    return Spec(vf: 'trim=start_frame=$a:end_frame=$b,setpts=PTS-STARTPTS', noAudio: true);
  }, opts: [_txt('a', 'الإطار الأول', '0'), _txt('b', 'الإطار الأخير', '90')]),
  _t('fps', sBasic, 'تغيير معدل الإطارات (FPS)', In.video, (c) async => _vf('fps=${c.o('n')}'), opts: [_c('n', 'FPS', [('15', '15'), ('24', '24'), ('30', '30'), ('60', '60')], '30')]),
  _t('res', sBasic, 'تغيير الدقة', In.both, (c) async => _vf(_short(c.o('t'))), opts: [_c('t', 'الدقة', [('480', '480'), ('720', '720'), ('1080', '1080'), ('1440', '2K'), ('2160', '4K')], '1080')]),
  _t('zoom', sBasic, 'تكبير وتقريب (زوم)', In.both, (c) async => _vf("scale=${c.w}:${c.h},crop='iw/${c.o('z')}':'ih/${c.o('z')}',scale=${c.w}:${c.h}:flags=lanczos"),
      opts: [_c('z', 'التقريب', [('1.25', '1.25×'), ('1.5', '1.5×'), ('2', '2×'), ('3', '3×')], '1.5')]),
  _t('cammove', sBasic, 'حركة الكاميرا (زوم تدريجي)', In.video, (c) async {
    final z = c.n('z', 0.2);
    final d = c.dur;
    final dir = c.o('dir');
    final sc = dir == 'out' ? "'iw*(1+$z-$z*t/${_tf(d)})'" : "'iw*(1+$z*t/${_tf(d)})'";
    final sc2 = dir == 'out' ? "'ih*(1+$z-$z*t/${_tf(d)})'" : "'ih*(1+$z*t/${_tf(d)})'";
    return Spec(fc: '[0:v]scale=${c.w}:${c.h},scale=w=$sc:h=$sc2:eval=frame,crop=${c.w}:${c.h}[v]');
  }, opts: [_c('dir', 'الاتجاه', [('in', 'تقريب'), ('out', 'ابتعاد')], 'in'), _c('z', 'القوة', [('0.1', 'خفيفة'), ('0.2', 'متوسطة'), ('0.4', 'قوية')], '0.2')]),
  _t('merge', sBasic, 'دمج مقاطع', In.multi, (c) => _concat(c), sub: 'فيديوهات وصور بالترتيب'),
  _t('split_parts', sBasic, 'تقطيع الفيديو لأجزاء', In.video, (c) async => Spec(post: ['-c', 'copy', '-f', 'segment', '-segment_time', c.o('n'), '-reset_timestamps', '1'], enc: const [], pattern: 'part_%03d.mp4'),
      opts: [_c('n', 'طول الجزء', [('15', '15 ث'), ('30', '30 ث'), ('60', '60 ث')], '30')]),

  // ================= تأثيرات الفيديو =================
  _t('bw', sFx, 'تأثير الأبيض والأسود', In.both, _bw),
  _t('oldfilm', sFx, 'تأثير الفيلم القديم', In.both, (c) async => _vf('curves=preset=vintage,eq=contrast=1.1:saturation=0.75,noise=alls=18:allf=t,vignette=PI/4')),
  _t('dreamy', sFx, 'تأثير الضبابية الحالمة', In.both, (c) async => const Spec(fc: '[0:v]split[a][b];[b]gblur=sigma=18[g];[a][g]blend=all_mode=screen:all_opacity=0.5,eq=saturation=1.2:brightness=0.03[v]')),
  _t('glitch', sFx, 'تأثير الاهتزاز (Glitch)', In.both, (c) async => _vf('rgbashift=rh=-10:bh=10:gv=4,noise=alls=12:allf=t,eq=saturation=1.3:contrast=1.1')),
  _t('colorpop', sFx, 'تأثير اللون الواحد (Color Pop)', In.both, (c) async => _vf('colorhold=color=${c.o('col')}:similarity=0.32:blend=0.12'),
      opts: [_c('col', 'اللون المحتفظ به', [('0xE53935', 'أحمر'), ('0x43A047', 'أخضر'), ('0x1E88E5', 'أزرق'), ('0xFDD835', 'أصفر'), ('0xFB8C00', 'برتقالي'), ('0xEC407A', 'وردي')], '0xE53935')]),
  _t('night', sFx, 'تأثير التلوين الليلي', In.both, (c) async => _vf('colorbalance=rs=-.2:gs=-.1:bs=.35:rm=-.1:bm=.2,eq=brightness=-0.08:contrast=1.1:saturation=0.9,vignette=PI/5')),
  _t('chroma', sFx, 'تأثير الشاشة الخضراء (Chroma Key)', In.both, (c) async {
    final key = c.o('key');
    final sim = c.o('sim');
    if (c.extra != null) {
      return Spec(fc: '[0:v]scale=${c.w}:${c.h},chromakey=$key:$sim:0.12[fg];[1:v]scale=${c.w}:${c.h}:force_original_aspect_ratio=increase,crop=${c.w}:${c.h}[bg];[bg][fg]overlay=shortest=1[v]', inputs: ['-loop', '1', '-framerate', '30', '-i', c.extra!]);
    }
    return Spec(fc: 'color=c=${c.o('bg')}:s=${c.w}x${c.h}:r=30[bg];[0:v]scale=${c.w}:${c.h},chromakey=$key:$sim:0.12[fg];[bg][fg]overlay=shortest=1[v]');
  }, extra: Extra.imageOpt, opts: [
    _c('key', 'لون الشاشة', [('0x00FF00', 'أخضر'), ('0x0000FF', 'أزرق')], '0x00FF00'),
    _c('sim', 'الحساسية', [('0.2', 'دقيقة'), ('0.3', 'متوسطة'), ('0.45', 'واسعة')], '0.3'),
    _c('bg', 'لون الخلفية (إن لم تختر صورة)', _colorsHex, '0x000000'),
  ], sub: 'اختر صورة خلفية اختيارية'),
  _t('mirror', sFx, 'تأثير الانعكاس (مرآة)', In.both, (c) async {
    switch (c.o('side')) {
      case 'r':
        return const Spec(fc: '[0:v]split[a][b];[a]crop=iw/2:ih:iw/2:0,hflip[l];[b]crop=iw/2:ih:iw/2:0[r];[l][r]hstack[v]');
      case 't':
        return const Spec(fc: '[0:v]split[a][b];[a]crop=iw:ih/2:0:0[t];[b]crop=iw:ih/2:0:0,vflip[d];[t][d]vstack[v]');
      case 'b':
        return const Spec(fc: '[0:v]split[a][b];[a]crop=iw:ih/2:0:ih/2,vflip[t];[b]crop=iw:ih/2:0:ih/2[d];[t][d]vstack[v]');
      default:
        return const Spec(fc: '[0:v]split[a][b];[a]crop=iw/2:ih:0:0[l];[b]crop=iw/2:ih:0:0,hflip[r];[l][r]hstack[v]');
    }
  }, opts: [_c('side', 'الجهة المنسوخة', [('l', 'اليسار'), ('r', 'اليمين'), ('t', 'الأعلى'), ('b', 'الأسفل')], 'l')]),
  _t('echo_v', sFx, 'تأثير التكرار (Echo مرئي)', In.video, (c) async => _vf("tmix=frames=5:weights='1 0.8 0.6 0.4 0.2'")),
  _t('mosaic', sFx, 'تأثير البكسل (Mosaic)', In.both, (c) async {
    final n = c.n('n', 16).toInt();
    return _vf('scale=${c.w}:${c.h},scale=trunc(iw/$n):trunc(ih/$n):flags=neighbor,scale=${c.w}:${c.h}:flags=neighbor');
  }, opts: [_c('n', 'حجم البكسل', [('8', 'صغير'), ('16', 'متوسط'), ('32', 'كبير')], '16')]),
  _t('sketch', sFx, 'تأثير الرسم (Sketch)', In.both, _sketch),
  _t('cartoon', sFx, 'تأثير الكرتون', In.both, _cartoon),
  _t('letterbox', sFx, 'إضافة شريط سينمائي (Letterbox)', In.both, (c) async {
    final r = c.n('r', 2.39);
    final bar = math.max(0, ((c.h - c.w / r) / 2).floor() ~/ 2 * 2);
    return _vf('scale=${c.w}:${c.h},drawbox=x=0:y=0:w=iw:h=$bar:color=black:t=fill,drawbox=x=0:y=ih-$bar:w=iw:h=$bar:color=black:t=fill');
  }, opts: [_c('r', 'النسبة', [('2.39', '2.39:1'), ('2', '2:1'), ('1.85', '1.85:1')], '2.39')]),
  _t('blurpart', sFx, 'تغبيش جزء من الفيديو', In.both, (c) async {
    final y = c.o('y');
    final hh = c.o('hh');
    return Spec(fc: "[0:v]scale=${c.w}:${c.h},split[a][b];[b]crop=iw*0.6:ih*$hh:iw*0.2:ih*$y,boxblur=25:3[bl];[a][bl]overlay=W*0.2:H*$y[v]");
  }, opts: [
    _c('y', 'مكان المنطقة', [('0.08', 'أعلى'), ('0.3', 'قبل الوسط'), ('0.45', 'وسط'), ('0.7', 'أسفل')], '0.45'),
    _c('hh', 'ارتفاع المنطقة', [('0.12', 'شريط'), ('0.25', 'متوسط'), ('0.4', 'كبير')], '0.25'),
  ], sub: 'لإخفاء وجه أو نص'),
  _t('colorcorr', sFx, 'تصحيح الألوان', In.both, (c) async {
    final t = c.o('t');
    final cb = t == 'warm' ? 'colorbalance=rs=.1:bs=-.1:rm=.06:bm=-.06,' : (t == 'cool' ? 'colorbalance=rs=-.1:bs=.1:rm=-.06:bm=.06,' : '');
    return _vf('${cb}eq=contrast=${c.o('c')}:saturation=${c.o('s')}:gamma=${c.o('g')}');
  }, opts: [
    _c('t', 'حرارة اللون', [('cool', 'بارد'), ('neutral', 'محايد'), ('warm', 'دافئ')], 'neutral'),
    _c('c', 'التباين', [('0.9', 'أقل'), ('1.05', 'طبيعي'), ('1.2', 'أعلى')], '1.05'),
    _c('s', 'التشبع', [('0.85', 'أقل'), ('1.1', 'طبيعي'), ('1.4', 'أعلى')], '1.1'),
    _c('g', 'الإضاءة', [('0.9', 'أغمق'), ('1', 'طبيعي'), ('1.15', 'أفتح')], '1'),
  ]),
  _t('glow', sFx, 'إضافة تأثير الوهج', In.both, (c) async => const Spec(fc: "[0:v]split[a][b];[b]lutyuv=y='if(gt(val,165),val,0)',gblur=sigma=25[g];[a][g]blend=all_mode=screen[v]")),
  _t('neon', sFx, 'إضافة تأثير التوهج النيوني', In.both, (c) async {
    final col = c.o('col');
    return Spec(fc: '[0:v]scale=${c.w}:${c.h},split[a][b];[b]edgedetect=low=0.08:high=0.2,gblur=sigma=3,format=rgb24,colorchannelmixer=$col[ed];[a]eq=brightness=-0.25:saturation=0.8,format=rgb24[d];[d][ed]blend=all_mode=screen[v]');
  }, opts: [_c('col', 'لون النيون', [('rr=0.1:gg=0.9:bb=1', 'سماوي'), ('rr=1:gg=0.2:bb=0.8', 'وردي'), ('rr=0.4:gg=1:bb=0.3', 'أخضر'), ('rr=1:gg=0.8:bb=0.1', 'أصفر')], 'rr=0.1:gg=0.9:bb=1')]),
  _t('movcolor', sFx, 'إضافة تأثير الألوان المتحركة', In.both, (c) async => Spec(vf: "hue=h='t*80'", anim: true, dur: c.isVideo ? null : c.n('dur', 6)), opts: const [_durOpt]),
  _t('wave', sFx, 'إضافة تأثير الموجة', In.both, (c) async => Spec(
      vf: "scale=720:-2,geq=lum='lum(X+8*sin(Y/22+T*5),Y)':cb='cb(X+4*sin(Y/11+T*5),Y)':cr='cr(X+4*sin(Y/11+T*5),Y)'", anim: true, dur: c.isVideo ? null : c.n('dur', 6)), opts: const [_durOpt]),
  _t('spin', sFx, 'إضافة تأثير الدوران', In.both, (c) async => Spec(vf: "scale=${c.w}:${c.h},rotate=a='t*${c.o('v')}':c=black:ow=${c.w}:oh=${c.h}", anim: true, dur: c.isVideo ? null : c.n('dur', 6)),
      opts: [_c('v', 'سرعة الدوران', [('0.4', 'بطيء'), ('0.9', 'متوسط'), ('1.8', 'سريع')], '0.9'), _durOpt]),
  _t('pulse', sFx, 'إضافة تأثير النبض', In.both, (c) async => Spec(
      fc: "[0:v]scale=${c.w}:${c.h},scale=w='iw*(1+0.07*abs(sin(t*5)))':h='ih*(1+0.07*abs(sin(t*5)))':eval=frame,crop=${c.w}:${c.h}[v]", anim: true, dur: c.isVideo ? null : c.n('dur', 6)), opts: const [_durOpt]),
  _t('shake', sFx, 'إضافة تأثير الاهتزاز', In.both, (c) async => Spec(
      fc: "[0:v]scale=trunc(${c.w}*1.08/2)*2:trunc(${c.h}*1.08/2)*2,crop=${c.w}:${c.h}:x='(iw-ow)/2+((iw-ow)/2-2)*sin(t*37)':y='(ih-oh)/2+((ih-oh)/2-2)*sin(t*29+1)'[v]", anim: true, dur: c.isVideo ? null : c.n('dur', 6)), opts: const [_durOpt]),
  _t('punch', sFx, 'إضافة تأثير التكبير السريع (Punch Zoom)', In.both, (c) async => Spec(
      fc: "[0:v]scale=${c.w}:${c.h},scale=w='iw*(1+0.22*pow(max(0,sin(t*3.1)),8))':h='ih*(1+0.22*pow(max(0,sin(t*3.1)),8))':eval=frame,crop=${c.w}:${c.h}[v]", anim: true, dur: c.isVideo ? null : c.n('dur', 6)), opts: const [_durOpt]),
  _t('strip', sFx, 'إضافة تأثير الشريط الملوّن', In.both, (c) async {
    final l = await c.layer('strip');
    return Spec(fc: '[0:v]scale=${c.w}:${c.h}[b];[b][1:v]overlay=0:0[v]', inputs: ['-i', l]);
  }),
  _t('oldtv', sFx, 'إضافة تأثير شاشة التلفاز القديم', In.both, (c) async {
    final l = await c.layer('scan');
    return Spec(fc: '[0:v]scale=${c.w}:${c.h},eq=saturation=0.75:contrast=1.15,noise=alls=22:allf=t,vignette=PI/3,rgbashift=rh=2:bh=-2[a];[a][1:v]overlay=0:0[v]', inputs: ['-i', l]);
  }),
  _t('vhs', sFx, 'إضافة تأثير الكاميرا المنزلية (VHS)', In.both, (c) async {
    final l = await c.layer('scan');
    return Spec(fc: '[0:v]scale=${c.w}:${c.h},rgbashift=rh=-6:bh=6,noise=alls=20:allf=t,eq=saturation=1.3:contrast=0.95,gblur=sigma=0.8[a];[a][1:v]overlay=0:0[v]', inputs: ['-i', l]);
  }),

  // ================= الألوان والأجواء =================
  _t('cine_bw', sLook, 'إضافة تأثير الأبيض والأسود السينمائي', In.both, (c) async {
    final bar = ((c.h - c.w / 2.39) / 2).floor() ~/ 2 * 2;
    return _vf("scale=${c.w}:${c.h},hue=s=0,eq=contrast=1.25:gamma=0.9,curves=all='0/0 0.5/0.45 1/1',noise=alls=8:allf=t,vignette=PI/4,drawbox=x=0:y=0:w=iw:h=$bar:color=black:t=fill,drawbox=x=0:y=ih-$bar:w=iw:h=$bar:color=black:t=fill");
  }),
  _t('faded', sLook, 'إضافة تأثير الألوان الباهتة', In.both, (c) async => _vf("curves=all='0/0.12 1/0.9',eq=saturation=0.65:contrast=0.95")),
  _t('vivid', sLook, 'إضافة تأثير الألوان الزاهية', In.both, (c) async => _vf('eq=saturation=1.6:contrast=1.12,unsharp=5:5:0.6')),
  _t('sunset', sLook, 'إضافة تأثير الغروب', In.both, (c) async => _vf("colorbalance=rs=.25:gs=-.03:bs=-.2:rm=.18:bm=-.15,eq=saturation=1.25:contrast=1.05,curves=all='0/0 0.6/0.55 1/1'")),
  _t('dawn', sLook, 'إضافة تأثير الفجر', In.both, (c) async => const Spec(fc: '[0:v]colorbalance=rs=.12:bs=.14:gm=-.04,eq=brightness=0.04:saturation=0.95,split[a][b];[b]gblur=sigma=14[g];[a][g]blend=all_mode=screen:all_opacity=0.25[v]')),
  _t('underwater', sLook, 'إضافة تأثير تحت الماء', In.both, (c) async => _vf('colorbalance=rs=-.35:gs=.05:bs=.3,eq=saturation=0.9:contrast=1.05,gblur=sigma=0.7')),
  _t('desert', sLook, 'إضافة تأثير الصحراء', In.both, (c) async => _vf('colorbalance=rs=.2:gs=.08:bs=-.18,eq=saturation=0.85:contrast=1.15:brightness=0.03')),
  _t('leak', sLook, 'إضافة تأثير الضوء (Light Leak)', In.both, (c) async {
    final l = await c.layer('leak');
    return Spec(fc: '[0:v]scale=${c.w}:${c.h}[a];[a][1:v]blend=all_mode=screen:all_opacity=${c.o('op')}[v]', inputs: ['-i', l]);
  }, opts: [_c('op', 'القوة', [('0.35', 'خفيفة'), ('0.6', 'متوسطة'), ('0.9', 'قوية')], '0.6')]),
  _t('grain', sLook, 'إضافة غبار وحبيبات فيلم', In.both, (c) async {
    final l = await c.layer('dust');
    return Spec(fc: '[0:v]scale=${c.w}:${c.h},noise=alls=${c.o('g')}:allf=t[a];[a][1:v]blend=all_mode=screen:all_opacity=0.6[v]', inputs: ['-i', l]);
  }, opts: [_c('g', 'الحبيبات', [('8', 'خفيفة'), ('16', 'متوسطة'), ('28', 'قوية')], '16')]),

  // ================= جسيمات وأجواء متحركة =================
  _t('snow', sPart, 'إضافة تأثير الثلج', In.both, (c) => _particles(c, 'snow', speed: 140), opts: const [_durOpt]),
  _t('rain', sPart, 'إضافة تأثير المطر', In.both, (c) => _particles(c, 'rain', speed: 900, baseVf: 'eq=brightness=-0.07:saturation=0.85'), opts: const [_durOpt]),
  _t('stars', sPart, 'إضافة تأثير النجوم', In.both, (c) => _particles(c, 'stars', speed: 14, baseVf: 'eq=brightness=-0.1'), opts: const [_durOpt]),
  _t('bubbles', sPart, 'إضافة تأثير الفقاعات', In.both, (c) => _particles(c, 'bubbles', speed: 90, fall: false), opts: const [_durOpt]),
  _t('hearts', sPart, 'إضافة تأثير القلوب', In.both, (c) => _particles(c, 'hearts', speed: 110, fall: false), opts: const [_durOpt]),
  _t('confetti', sPart, 'إضافة تأثير الكونفيتي', In.both, (c) => _particles(c, 'confetti', speed: 260), opts: const [_durOpt]),
  _t('fire', sPart, 'إضافة تأثير النار', In.both, (c) => _particles(c, 'embers', speed: 200, fall: false, baseVf: 'colorbalance=rs=.1:bs=-.1', staticKind: 'fire_glow'), opts: const [_durOpt]),
  _t('smoke', sPart, 'إضافة تأثير الدخان', In.both, (c) => _particles(c, 'smoke', speed: 60, fall: false, baseVf: 'eq=contrast=0.95'), opts: const [_durOpt]),
  _t('lightning', sPart, 'إضافة تأثير البرق', In.both, (c) async {
    final l = await c.layer('bolt');
    final dd = c.isVideo ? null : c.n('dur', 6);
    return Spec(
        fc: "[0:v]scale=${c.w}:${c.h},eq=brightness=-0.08,eq=brightness='0.35*lt(mod(t,4),0.15)':eval=frame[a];[a][1:v]overlay=0:0:enable='lt(mod(t,4),0.15)+between(mod(t,4),0.3,0.4)':format=auto[v]",
        inputs: ['-loop', '1', '-framerate', '30', '-i', l], anim: true, dur: dd);
  }, opts: const [_durOpt]),
  _t('fog', sPart, 'إضافة تأثير الضباب', In.both, (c) => _particles(c, 'fog', speed: 35, fall: false), opts: const [_durOpt]),
  _t('rays', sPart, 'إضافة تأثير الأشعة الضوئية', In.both, (c) async {
    final l = await c.layer('rays');
    return Spec(fc: '[0:v]scale=${c.w}:${c.h}[a];[a][1:v]overlay=0:0:format=auto[v]', inputs: ['-loop', '1', '-framerate', '30', '-i', l]);
  }),

  // ================= إضافات على الفيديو =================
  _t('intro', sOvl, 'إضافة مقدمة وخاتمة نصية', In.video, (c) async {
    final t = c.o('text').trim();
    if (t.isEmpty) throw ToolError('اكتب النص أولاً');
    final png = await c.text(t, size: 110, maxW: c.w * 0.85);
    final d = c.n('d', 2);
    final bg = c.o('bg');
    final card = 'color=c=$bg:s=${c.w}x${c.h}:r=30:d=$d[c0];[c0][1:v]overlay=(W-w)/2:(H-h)/2:format=auto,format=yuv420p,setsar=1[card];';
    final mainV = '[0:v]scale=${c.w}:${c.h},setsar=1,fps=30,format=yuv420p[m];';
    final ms = (d * 1000).round();
    final place = c.o('place');
    String fc;
    if (place == 'end') {
      fc = '$card$mainV[m][card]concat=n=2:v=1:a=0[v]';
      if (c.hasA) fc += ';[0:a]apad=pad_dur=$d[a]';
    } else if (place == 'start') {
      fc = '$card$mainV[card][m]concat=n=2:v=1:a=0[v]';
      if (c.hasA) fc += ';[0:a]adelay=$ms|$ms[a]';
    } else {
      fc = '$card$mainV[card]split[c1][c2];[c1][m][c2]concat=n=3:v=1:a=0[v]';
      if (c.hasA) fc += ';[0:a]adelay=$ms|$ms,apad=pad_dur=$d[a]';
    }
    return Spec(fc: fc, inputs: ['-i', png]);
  }, opts: [
    _txt('text', 'النص', 'مونتاجي'),
    _c('place', 'مكان البطاقة', [('start', 'مقدمة'), ('end', 'خاتمة'), ('both', 'الاثنان')], 'start'),
    _c('d', 'المدة', [('2', '2 ث'), ('3', '3 ث'), ('4', '4 ث')], '2'),
    _c('bg', 'لون البطاقة', [('0x000000', 'أسود'), ('0x1E88E5', 'أزرق'), ('0xE53935', 'أحمر'), ('0x43A047', 'أخضر'), ('0x8E24AA', 'بنفسجي')], '0x000000'),
  ]),
  _t('countdown', sOvl, 'عدّاد تنازلي', In.both, (c) async {
    final n = c.n('n', 3).toInt();
    final ins = <String>[];
    final g = StringBuffer('[0:v]scale=${c.w}:${c.h}[b0];');
    for (var i = 0; i < n; i++) {
      final png = await c.text('${n - i}', size: 420, color: 0xFFFFFFFF, maxW: 600);
      ins.addAll(['-i', png]);
      g.write("[b$i][${i + 1}:v]overlay=(W-w)/2:(H-h)/2:enable='between(t,$i,${i + 1})'[b${i + 1}];");
    }
    g.write('[b$n]null[v]');
    return Spec(fc: g.toString(), inputs: ins, anim: true, dur: c.isVideo ? null : math.max(c.n('dur', 6), n + 1.0));
  }, opts: [_c('n', 'العدّ من', [('3', '3'), ('5', '5'), ('10', '10')], '3'), const JobOpt('dur', 'المدة (للصور)', _durs, '8')]),
  _t('timer', sOvl, 'عداد زمني على الفيديو', In.video, (c) async {
    final secs = math.min(c.dur.ceil(), 90);
    final ins = <String>[];
    final g = StringBuffer('[0:v]scale=${c.w}:${c.h}[b0];');
    final pos = c.o('pos');
    for (var i = 0; i < secs; i++) {
      final lab = '${(i ~/ 60).toString().padLeft(2, '0')}:${(i % 60).toString().padLeft(2, '0')}';
      final png = await c.text(lab, size: 64, box: true, maxW: 400);
      ins.addAll(['-i', png]);
      g.write("[b$i][${i + 1}:v]overlay=${_ovPos(pos)}:enable='between(t,$i,${i + 1})'[b${i + 1}];");
    }
    g.write('[b$secs]null[v]');
    return Spec(fc: g.toString(), inputs: ins);
  }, opts: [_c('pos', 'المكان', _pos4, 'tr')], sub: 'أول 90 ثانية'),
  _t('channel', sOvl, 'إضافة اسم القناة أسفل الفيديو', In.both, (c) => _text(c, fadeIn: true), opts: _textOpts),
  _t('progress', sOvl, 'إضافة شريط تقدم', In.video, (c) async {
    final col = c.o('col');
    final hh = c.n('hh', 12).toInt();
    final y = c.o('side') == 'top' ? '0' : 'H-h';
    return Spec(fc: "color=c=$col:s=${c.w}x$hh:r=30[bar];[0:v]scale=${c.w}:${c.h}[b];[b][bar]overlay=x='-w+w*t/${_tf(c.dur)}':y=$y:shortest=1[v]");
  }, opts: [
    _c('col', 'اللون', [('0xFDD835', 'أصفر'), ('0x43A047', 'أخضر'), ('0xE53935', 'أحمر'), ('0x45C4DB', 'سماوي'), ('0xFFFFFF', 'أبيض')], '0xFDD835'),
    _c('hh', 'السماكة', [('8', 'رفيع'), ('14', 'متوسط'), ('24', 'سميك')], '14'),
    _c('side', 'المكان', [('bot', 'أسفل'), ('top', 'أعلى')], 'bot'),
  ]),
  _t('phone', sOvl, 'إضافة إطار هاتف', In.both, (c) async {
    final l = await c.layer('phone');
    return Spec(fc: '[0:v]scale=${c.w}:${c.h}:force_original_aspect_ratio=increase,crop=${c.w}:${c.h}[a];[a][1:v]overlay=0:0[v]', inputs: ['-i', l]);
  }),
  _t('logo', sOvl, 'إضافة شعار ثابت', In.both, (c) async {
    return Spec(
        fc: '[1:v]scale=${(c.w * c.n('sz', 0.22)).round()}:-1,format=rgba,colorchannelmixer=aa=${c.o('op')}[l];[0:v]scale=${c.w}:${c.h}[b];[b][l]overlay=${_ovPos(c.o('pos'))}[v]',
        inputs: ['-i', c.extra!]);
  }, extra: Extra.image, opts: [
    _c('pos', 'المكان', _pos4, 'br'),
    _c('sz', 'الحجم', [('0.12', 'صغير'), ('0.22', 'متوسط'), ('0.36', 'كبير')], '0.22'),
    _c('op', 'الشفافية', [('0.4', 'خفيف'), ('0.7', 'متوسط'), ('1', 'كامل')], '1'),
  ], sub: 'اختر صورة الشعار'),
  _t('watermark', sOvl, 'علامة مائية خاصة بك', In.both, (c) async {
    final t = c.o('text').trim();
    if (t.isEmpty) throw ToolError('اكتب نص العلامة');
    final png = await c.text(t, size: c.n('size', 56), color: 0xFFFFFFFF, maxW: c.w * 0.8);
    return Spec(fc: '[1:v]format=rgba,colorchannelmixer=aa=${c.o('op')}[l];[0:v]scale=${c.w}:${c.h}[b];[b][l]overlay=${_ovPos(c.o('pos'))}[v]', inputs: ['-i', png]);
  }, opts: [
    _txt('text', 'نص العلامة', '@اسمك'),
    _c('size', 'الحجم', [('40', 'صغير'), ('56', 'متوسط'), ('90', 'كبير')], '56'),
    _c('pos', 'المكان', _pos4, 'br'),
    _c('op', 'الشفافية', [('0.35', 'خفيفة'), ('0.6', 'متوسطة'), ('0.9', 'قوية')], '0.6'),
  ]),
  _t('text_img', sOvl, 'إضافة نص على الصورة أو الفيديو', In.both, (c) => _text(c, fadeIn: false), opts: _textOpts),

  // ================= الخلفيات والإطارات =================
  _t('bg_anim', sBg, 'إضافة خلفية متحركة', In.both, (c) async {
    final pal = c.o('pal').split(',');
    final d = c.dur;
    return Spec(
        fc: 'gradients=s=${c.w}x${c.h}:r=30:d=${_tf(d)}:speed=0.015:nb_colors=3:c0=${pal[0]}:c1=${pal[1]}:c2=${pal[2]}[bg];[0:v]scale=trunc(${c.w}*0.84/2)*2:-2[fg];[bg][fg]overlay=(W-w)/2:(H-h)/2:shortest=1[v]',
        anim: true, dur: c.isVideo ? null : c.n('dur', 6));
  }, opts: [_palOpt(), _durOpt]),
  _t('bg_grad', sBg, 'إضافة خلفية متدرجة', In.both, (c) async {
    final pal = c.o('pal').split(',');
    return Spec(fc: 'gradients=s=${c.w}x${c.h}:r=30:d=${_tf(c.dur)}:speed=0:nb_colors=2:c0=${pal[0]}:c1=${pal[1]}[bg];[0:v]scale=trunc(${c.w}*0.84/2)*2:-2[fg];[bg][fg]overlay=(W-w)/2:(H-h)/2:shortest=1[v]');
  }, opts: [_palOpt()]),
  _t('bg_pat', sBg, 'إضافة خلفية بنمط (خطوط، نقاط)', In.both, (c) async {
    final l = await c.layer(c.o('pat'), c1: _hex(c.o('c1')), c2: _hex(c.o('c2')));
    return Spec(fc: '[0:v]scale=trunc(${c.w}*0.84/2)*2:-2[fg];[1:v][fg]overlay=(W-w)/2:(H-h)/2:shortest=1[v]', inputs: ['-loop', '1', '-framerate', '30', '-i', l]);
  }, opts: [
    _c('pat', 'النمط', [('stripes', 'خطوط'), ('dots', 'نقاط'), ('checks', 'مربعات'), ('diag', 'مائل')], 'stripes'),
    _c('c1', 'اللون الأول', [('0x1E88E5', 'أزرق'), ('0x000000', 'أسود'), ('0xE53935', 'أحمر'), ('0x43A047', 'أخضر'), ('0x8E24AA', 'بنفسجي')], '0x1E88E5'),
    _c('c2', 'اللون الثاني', [('0x42A5F5', 'أزرق فاتح'), ('0x333333', 'رمادي'), ('0xFFCDD2', 'وردي'), ('0xFDD835', 'أصفر'), ('0xFFFFFF', 'أبيض')], '0x42A5F5'),
  ]),
  _t('border', sBg, 'إضافة إطار حول الفيديو', In.both, (c) async {
    final b = c.n('b', 24).toInt();
    return _vf('scale=${c.w}:${c.h},pad=iw+${2 * b}:ih+${2 * b}:$b:$b:color=${c.o('col')}');
  }, opts: [
    _c('b', 'السماكة', [('12', 'رفيع'), ('24', 'متوسط'), ('48', 'سميك')], '24'),
    _c('col', 'اللون', _colorsHex, '0xFFFFFF'),
  ]),
  _t('rounded', sBg, 'زوايا مدورة', In.both, (c) async {
    final l = await c.layer('corners', c1: _hex(c.o('col')), c2: (c.n('r', 60).toInt()));
    return Spec(fc: '[0:v]scale=${c.w}:${c.h}[a];[a][1:v]overlay=0:0[v]', inputs: ['-i', l]);
  }, opts: [
    _c('r', 'الاستدارة', [('40', 'خفيفة'), ('90', 'متوسطة'), ('160', 'كبيرة')], '90'),
    _c('col', 'لون الزوايا', _colorsHex, '0x000000'),
  ]),
  _t('softedge', sBg, 'إضافة حدود مموّهة', In.both, (c) async => Spec(
      fc: "[0:v]scale=${c.w}:${c.h},split[a][b];[a]gblur=sigma=30[bl];[b]format=rgba,geq=r='r(X,Y)':g='g(X,Y)':b='b(X,Y)':a='clip(min(min(X,W-X),min(Y,H-Y))*255/60,0,255)'[fg];[bl][fg]overlay=0:0[v]")),
  _t('shadow', sBg, 'إضافة ظل للصورة', In.image, (c) async => Spec(
      fc: '[0:v]scale=${c.w}:${c.h},format=rgba,pad=iw+120:ih+120:60:60:color=black@0,split[a][b];[b]lutrgb=r=0:g=0:b=0,boxblur=28:3[sh];color=c=white:s=${c.w + 120}x${c.h + 120}[w];[w][sh]overlay=18:18[x];[x][a]overlay=0:0[v]')),

  // ================= الصوت =================
  _t('rm_audio', sAudio, 'إزالة الصوت', In.video, (c) async => const Spec(copyV: true, noAudio: true)),
  _t('extract_mp3', sAudio, 'استخراج الصوت (MP3)', In.video, (c) async => const Spec(out: Out.audio, ext: 'mp3')),
  _t('to_audio', sAudio, 'تحويل الفيديو إلى صوت فقط', In.video, (c) async => const Spec(out: Out.audio, ext: 'm4a')),
  _t('echo_a', sAudio, 'صدى الصوت', In.av, (c) async => Spec(av: true, af: 'aecho=0.8:0.88:${c.o('d')}:${c.o('v')}'),
      opts: [_c('d', 'مدى الصدى', [('40|80', 'قصير'), ('60|120', 'متوسط'), ('200|400', 'طويل')], '60|120'), _c('v', 'القوة', [('0.3|0.2', 'خفيف'), ('0.5|0.4', 'متوسط'), ('0.7|0.6', 'قوي')], '0.5|0.4')]),
  _t('pitch', sAudio, 'تغيير نبرة الصوت', In.av, (c) async {
    final r = math.pow(2, c.n('n', 4) / 12).toDouble();
    return Spec(av: true, af: 'aresample=44100,asetrate=${(44100 * r).round()},aresample=44100,${atempoChain(1 / r)}');
  }, opts: [_c('n', 'النبرة', [('-6', 'ثقيل جداً'), ('-3', 'ثقيل'), ('3', 'رفيع'), ('6', 'رفيع جداً'), ('9', 'طفولي')], '3')]),
  _t('denoise_a', sAudio, 'إزالة ضجيج الصوت', In.av, (c) async => const Spec(av: true, af: 'highpass=f=80,afftdn=nf=-25')),
  _t('vol_down', sAudio, 'خفض صوت الفيديو الأصلي', In.av, (c) async => Spec(av: true, af: 'volume=${c.o('v')}'), opts: [_c('v', 'المستوى', [('0.6', '60%'), ('0.35', '35%'), ('0.15', '15%')], '0.35')]),
  _t('vol_up', sAudio, 'رفع صوت الفيديو', In.av, (c) async => Spec(av: true, af: 'volume=${c.o('v')},alimiter=limit=0.95'), opts: [_c('v', 'مستوى الرفع', [('1.5', '150%'), ('2', '200%'), ('3', '300%')], '2')]),
  _t('normalize', sAudio, 'تطبيع مستوى الصوت', In.av, (c) async => const Spec(av: true, af: 'loudnorm=I=-16:TP=-1.5:LRA=11')),
  _t('cut_audio', sAudio, 'قص الصوت', In.av, (c) async {
    final s = c.n('a', 0), e = c.n('b', 10);
    if (e <= s) throw ToolError('نهاية القص يجب أن تكون بعد البداية');
    return Spec(av: true, pre: ['-ss', _tf(s), '-t', _tf(e - s)], out: Out.audio, ext: 'm4a');
  }, opts: [_txt('a', 'البداية (ثانية)', '0'), _txt('b', 'النهاية (ثانية)', '10')]),
  _t('merge_audio', sAudio, 'دمج مقاطع صوتية', In.multiAudio, (c) async {
    if (c.files.length < 2) throw ToolError('اختر ملفين صوتيين على الأقل');
    final ins = <String>[];
    final g = StringBuffer();
    for (var i = 0; i < c.files.length; i++) {
      ins.addAll(['-i', c.files[i]]);
      g.write('[$i:a]aresample=44100,aformat=channel_layouts=stereo[a$i];');
    }
    for (var i = 0; i < c.files.length; i++) {
      g.write('[a$i]');
    }
    g.write('concat=n=${c.files.length}:v=0:a=1[a]');
    return Spec(fc: g.toString(), allInputs: ins, out: Out.audio, ext: 'm4a');
  }),
  _t('afade', sAudio, 'موسيقى بأعلى الصوت تدريجياً (Fade)', In.av, (c) async {
    final d = math.min(c.n('d', 2), math.max(0.5, c.dur / 3));
    return Spec(av: true, af: 'afade=t=in:st=0:d=${_tf(d)},afade=t=out:st=${_tf(math.max(0, c.dur - d))}:d=${_tf(d)}');
  }, opts: [_c('d', 'مدة التدرّج', [('1', 'ثانية'), ('2', 'ثانيتان'), ('4', '4 ثوانٍ')], '2')]),
  _t('sfx', sAudio, 'مؤثر صوتي عند بداية الفيديو', In.video, (c) async {
    final d = c.n('at', 0);
    final ms = (d * 1000).round();
    final fc = c.hasA
        ? '[1:a]adelay=$ms|$ms,volume=1.5[s];[0:a][s]amix=inputs=2:duration=first:normalize=0[a]'
        : '[1:a]adelay=$ms|$ms,volume=1.5[a]';
    return Spec(fc: fc, inputs: ['-i', c.extra!], copyV: true);
  }, extra: Extra.audio, opts: [_c('at', 'يبدأ عند', [('0', 'البداية'), ('1', 'ثانية 1'), ('3', 'ثانية 3'), ('5', 'ثانية 5')], '0')]),
  _t('atempo_a', sAudio, 'تغيير سرعة الصوت دون تغيير النبرة', In.av, (c) async => Spec(out: Out.audio, ext: 'm4a', af: atempoChain(c.n('s', 1.25))),
      opts: [_c('s', 'السرعة', [('0.75', '0.75×'), ('1.25', '1.25×'), ('1.5', '1.5×'), ('2', '2×')], '1.25')]),
  _t('areverse', sAudio, 'عكس الصوت', In.av, (c) async => const Spec(out: Out.audio, ext: 'm4a', af: 'areverse')),
  _t('mono', sAudio, 'صوت ستيريو إلى مونو', In.av, (c) async => const Spec(av: true, af: 'pan=mono|c0=0.5*c0+0.5*c1')),
  _t('silence', sAudio, 'إزالة الصمت من الصوت', In.av, (c) async => const Spec(out: Out.audio, ext: 'm4a', af: 'silenceremove=start_periods=1:start_threshold=-45dB:stop_periods=-1:stop_duration=0.5:stop_threshold=-45dB')),
  _t('radio', sAudio, 'إضافة صوت راديو قديم', In.av, (c) async => const Spec(av: true, af: 'highpass=f=400,lowpass=f=3000,acrusher=bits=10:mix=0.3,volume=1.4')),
  _t('theater', sAudio, 'إضافة صوت مسرح (صدى القاعة)', In.av, (c) async => const Spec(av: true, af: 'aecho=0.8:0.9:200|400|600:0.35|0.25|0.15')),
  _t('audio_video', sAudio, 'تحويل الصوت إلى فيديو بصورة ثابتة', In.audio, (c) async {
    final (W, H) = _fr(c.o('f'));
    return Spec(
        allInputs: ['-loop', '1', '-framerate', '30', '-i', c.extra!, '-i', c.src],
        fc: '[0:v]scale=$W:$H:force_original_aspect_ratio=decrease,pad=$W:$H:(ow-iw)/2:(oh-ih)/2:color=black,setsar=1,format=yuv420p[v]',
        out: Out.video,
        shortest: true,
        post: const ['-map', '1:a'],
        dur: c.dur);
  }, extra: Extra.image, opts: [_c('f', 'الإطار', _frame3, '16:9')]),
  _t('mix_av', sAudio, 'دمج صوت مع فيديو', In.video, (c) async {
    final v = c.o('v');
    final String fc;
    if (c.o('mode') == 'mix' && c.hasA) {
      fc = '[0:a]volume=0.7[o];[1:a]volume=$v[n];[o][n]amix=inputs=2:duration=first:normalize=0[a]';
    } else {
      fc = '[1:a]volume=$v[a]';
    }
    return Spec(fc: fc, inputs: ['-stream_loop', '-1', '-i', c.extra!], copyV: true, shortest: true, dur: c.dur);
  }, extra: Extra.audio, opts: [
    _c('mode', 'الصوت الأصلي', [('replace', 'استبدال'), ('mix', 'دمج')], 'mix'),
    _c('v', 'مستوى الصوت المضاف', [('0.6', 'منخفض'), ('1', 'عادي'), ('1.6', 'عالٍ')], '1'),
  ]),

  // ================= تحويل وضغط =================
  _t('compress', sConv, 'ضغط الفيديو لحجم معيّن', In.video, (c) async {
    final mb = c.n('mb', 16);
    final kbps = math.max(120, (mb * 8192 / math.max(c.dur, 1) * 0.92 - 96).floor());
    return Spec(
        vf: "scale='if(gt(iw,ih),-2,min(720,iw))':'if(gt(iw,ih),min(720,ih),-2)'",
        enc: ['-c:v', 'libx264', '-preset', 'veryfast', '-b:v', '${kbps}k', '-maxrate', '${kbps}k', '-bufsize', '${kbps * 2}k', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '96k', '-movflags', '+faststart']);
  }, opts: [_c('mb', 'الحجم المستهدف', [('8', '8 MB'), ('16', '16 MB (واتساب)'), ('25', '25 MB'), ('50', '50 MB (تيليجرام)')], '16')]),
  _t('convert', sConv, 'تحويل الصيغة', In.video, (c) async {
    switch (c.o('fmt')) {
      case 'webm':
        return const Spec(ext: 'webm', enc: ['-c:v', 'libvpx-vp9', '-b:v', '1500k', '-pix_fmt', 'yuv420p', '-c:a', 'libopus', '-b:a', '128k']);
      case 'mkv':
        return const Spec(ext: 'mkv', enc: ['-c:v', 'libx264', '-preset', 'veryfast', '-crf', '21', '-pix_fmt', 'yuv420p', '-c:a', 'aac']);
      case 'mov':
        return const Spec(ext: 'mov', enc: ['-c:v', 'libx264', '-preset', 'veryfast', '-crf', '21', '-pix_fmt', 'yuv420p', '-c:a', 'aac']);
      case 'avi':
        return const Spec(ext: 'avi', enc: ['-c:v', 'mpeg4', '-q:v', '4', '-c:a', 'libmp3lame', '-q:a', '4']);
      default:
        return const Spec(ext: 'mp4');
    }
  }, opts: [_c('fmt', 'الصيغة', [('mp4', 'MP4'), ('mkv', 'MKV'), ('mov', 'MOV'), ('webm', 'WebM'), ('avi', 'AVI')], 'mp4')]),
  _t('to_gif', sConv, 'تحويل الفيديو إلى GIF', In.video, (c) async => Spec(out: Out.gif, gifFps: c.n('fps', 12).toInt(), gifW: c.n('w', 480).toInt(), dur: math.min(c.dur, c.n('max', 8))),
      opts: [_c('fps', 'FPS', [('8', '8'), ('12', '12'), ('18', '18')], '12'), _c('w', 'العرض', [('320', '320'), ('480', '480'), ('720', '720')], '480'), _c('max', 'الحد الأقصى', [('4', '4 ث'), ('8', '8 ث'), ('15', '15 ث')], '8')]),
  _t('frames', sConv, 'تحويل الفيديو إلى صور', In.video, (c) async => Spec(vf: 'fps=1/${c.o('n')}', out: Out.image, post: const ['-frames:v', '60', '-q:v', '2'], pattern: 'frame_%03d.jpg'),
      opts: [_c('n', 'صورة كل', [('1', 'ثانية'), ('2', 'ثانيتين'), ('5', '5 ثوانٍ')], '1')], sub: 'حتى 60 صورة'),

  // ================= GIF =================
  _t('gif2video', sGif, 'تحويل GIF إلى فيديو', In.gif, (c) async => Spec(vf: 'scale=trunc(iw/2)*2:trunc(ih/2)*2', pre: ['-stream_loop', '${c.n('n', 1).toInt() - 1}'], out: Out.video, noAudio: true),
      opts: [_c('n', 'التكرار', [('1', 'مرة'), ('3', '3 مرات'), ('5', '5 مرات')], '1')]),
  _t('webp2gif', sGif, 'تحويل WebP المتحرك إلى GIF', In.gif, (c) async => const Spec(out: Out.gif)),
  _t('gif_shrink', sGif, 'تقليل حجم GIF', In.gif, (c) async => Spec(out: Out.gif, gifFps: c.n('fps', 10).toInt(), gifW: c.n('w', 320).toInt(), gifColors: c.n('col', 96).toInt()),
      opts: [_c('w', 'العرض', [('240', '240'), ('320', '320'), ('480', '480')], '320'), _c('fps', 'FPS', [('8', '8'), ('10', '10'), ('12', '12')], '10'), _c('col', 'الألوان', [('48', '48'), ('96', '96'), ('160', '160')], '96')]),
  _t('gif_speed', sGif, 'تسريع GIF', In.gif, (c) async => Spec(vf: 'setpts=PTS/${c.o('s')}', out: Out.gif, gifW: 400), opts: [_c('s', 'السرعة', [('0.5', 'أبطأ 0.5×'), ('1.5', '1.5×'), ('2', '2×'), ('3', '3×')], '2')]),
  _t('gif_reverse', sGif, 'عكس GIF', In.gif, (c) async => const Spec(vf: 'reverse', out: Out.gif, gifW: 400)),
  _t('gif_text', sGif, 'إضافة نص على GIF', In.gif, (c) async {
    final t = c.o('text').trim();
    if (t.isEmpty) throw ToolError('اكتب النص أولاً');
    final png = await c.text(t, size: c.n('size', 90), color: _hex(c.o('color')), box: c.o('box') == '1', maxW: c.w * 0.9);
    return Spec(fc: '[0:v]scale=${c.w}:${c.h}[b];[b][1:v]overlay=x=(W-w)/2:${_yPos(c.o('pos'))}[v]', inputs: ['-i', png], out: Out.gif, gifW: 400);
  }, opts: _textOpts),
  _t('gif_cut', sGif, 'قص GIF', In.gif, (c) async {
    final s = c.n('a', 0), e = c.n('b', 3);
    if (e <= s) throw ToolError('نهاية القص يجب أن تكون بعد البداية');
    return Spec(pre: ['-ss', _tf(s), '-t', _tf(e - s)], out: Out.gif, gifW: 400, dur: e - s);
  }, opts: [_txt('a', 'البداية (ثانية)', '0'), _txt('b', 'النهاية (ثانية)', '3')]),
  _t('gif_image', sGif, 'دمج GIF مع صورة', In.gif, (c) async => Spec(
      fc: '[1:v]scale=${(c.w * c.n('sz', 0.3)).round()}:-1[l];[0:v]scale=${c.w}:${c.h}[b];[b][l]overlay=${_ovPos(c.o('pos'))}[v]', inputs: ['-i', c.extra!], out: Out.gif, gifW: 400),
      extra: Extra.image, opts: [_c('pos', 'مكان الصورة', _pos4, 'br'), _c('sz', 'الحجم', [('0.2', 'صغير'), ('0.3', 'متوسط'), ('0.5', 'كبير')], '0.3')]),
  _t('gif_from_images', sGif, 'إنشاء GIF من صور', In.multi, (c) async {
    final s = await _concat(c, slide: true);
    return Spec(fc: s.fc, allInputs: s.allInputs, out: Out.gif, gifW: 400, dur: s.dur);
  }, opts: [_c('sec', 'مدة الصورة', [('0.5', '0.5 ث'), ('1', '1 ث'), ('2', '2 ث')], '1'), _c('tr', 'الانتقال', [('fade', 'تلاشي'), ('slideleft', 'انزلاق'), ('circleopen', 'دائرة'), ('wipeleft', 'مسح')], 'fade')]),
  _t('images2video', sGif, 'تحويل الصور إلى فيديو', In.multi, (c) => _concat(c, slide: true),
      opts: [_c('sec', 'مدة الصورة', [('2', '2 ث'), ('3', '3 ث'), ('5', '5 ث')], '3'), _c('tr', 'الانتقال', [('fade', 'تلاشي'), ('slideleft', 'انزلاق'), ('circleopen', 'دائرة'), ('wipeleft', 'مسح'), ('dissolve', 'ذوبان')], 'fade')]),
  _t('split_screen', sBasic, 'شاشة مقسومة', In.multi, (c) async {
    final n = c.files.length;
    if (n < 2 || n > 4) throw ToolError('اختر من 2 إلى 4 ملفات');
    final W = c.o('f') == '16:9' ? 1920 : 1080, H = c.o('f') == '16:9' ? 1080 : 1920;
    final ins = <String>[];
    final g = StringBuffer();
    final cw = n == 2 ? (c.o('dir') == 'h' ? W ~/ 2 : W) : W ~/ 2;
    final ch = n == 2 ? (c.o('dir') == 'h' ? H : H ~/ 2) : H ~/ 2;
    for (var i = 0; i < n; i++) {
      ins.addAll(isImagePath(c.files[i]) ? ['-loop', '1', '-framerate', '30', '-t', '10', '-i', c.files[i]] : ['-stream_loop', '-1', '-i', c.files[i]]);
      g.write('[$i:v]scale=$cw:$ch:force_original_aspect_ratio=increase,crop=$cw:$ch,setsar=1,fps=30[s$i];');
    }
    for (var i = 0; i < n; i++) {
      g.write('[s$i]');
    }
    final layout = n == 2 ? (c.o('dir') == 'h' ? '0_0|w0_0' : '0_0|0_h0') : (n == 3 ? '0_0|w0_0|0_h0' : '0_0|w0_0|0_h0|w0_h0');
    g.write('xstack=inputs=$n:layout=$layout:fill=black[v]');
    var d = 0.0;
    for (var i = 0; i < n; i++) {
      if (!isImagePath(c.files[i]) && c.infos[i].dur > d) d = c.infos[i].dur;
    }
    if (d <= 0) d = 6;
    return Spec(fc: g.toString(), allInputs: ins, noAudio: true, dur: d);
  }, opts: [_c('f', 'الإطار', [('9:16', 'طولي'), ('16:9', 'عرضي')], '9:16'), _c('dir', 'عند ملفين', [('v', 'فوق وتحت'), ('h', 'جنباً إلى جنب')], 'v')], sub: '2 إلى 4 ملفات'),
  _t('pip', sBasic, 'صورة داخل صورة', In.multi, (c) async {
    if (c.files.length != 2) throw ToolError('اختر ملفين: الأول الخلفية والثاني الصغير');
    final W = c.w, H = c.h;
    final ins = <String>[];
    for (var i = 0; i < 2; i++) {
      ins.addAll(isImagePath(c.files[i]) ? ['-loop', '1', '-framerate', '30', '-t', '10', '-i', c.files[i]] : ['-stream_loop', '-1', '-i', c.files[i]]);
    }
    final pw = (W * c.n('sz', 0.3)).round() ~/ 2 * 2;
    var d = c.infos[0].dur;
    if (isImagePath(c.files[0]) || d <= 0) d = 6;
    return Spec(
        fc: '[0:v]scale=$W:$H:force_original_aspect_ratio=increase,crop=$W:$H,setsar=1[a];[1:v]scale=$pw:-2,setsar=1[b];[a][b]overlay=${_ovPos(c.o('pos'))}:shortest=0[v]',
        allInputs: ins,
        noAudio: true,
        dur: d);
  }, opts: [_c('pos', 'مكان الصغير', _pos4, 'br'), _c('sz', 'حجمه', [('0.25', 'صغير'), ('0.35', 'متوسط'), ('0.5', 'كبير')], '0.3')], sub: 'الأول الخلفية والثاني الصغير'),

  // ================= المنصات الاجتماعية =================
  _t('story', sSoc, 'تحويل الفيديو إلى ستوري انستغرام', In.both, (c) async => Spec(fc: '${_fitBlur(1080, 1920)},setsar=1[v]', pre: c.isVideo ? const ['-t', '60'] : const []), sub: '1080×1920'),
  _t('reels', sSoc, 'تحويل الفيديو إلى ريلز', In.both, (c) async => Spec(fc: '${_fitBlur(1080, 1920)},setsar=1[v]', pre: c.isVideo ? const ['-t', '90'] : const []), sub: '1080×1920 حتى 90 ث'),
  _t('shorts', sSoc, 'تحويل الفيديو إلى شورتس', In.both, (c) async => Spec(fc: '${_fitBlur(1080, 1920)},setsar=1[v]', pre: c.isVideo ? const ['-t', '60'] : const []), sub: '1080×1920 حتى 60 ث'),
  _t('whatsapp', sSoc, 'تحويل الفيديو إلى حالة واتساب (30 ثانية)', In.video, (c) async => Spec(
      fc: '${_fitBlur(720, 1280)},setsar=1[v]',
      pre: const ['-t', '30'],
      enc: const ['-c:v', 'libx264', '-preset', 'veryfast', '-b:v', '1800k', '-maxrate', '2200k', '-bufsize', '4000k', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '96k', '-movflags', '+faststart'])),
  _t('tiktok', sSoc, 'تحويل الفيديو إلى فيديو تيك توك', In.both, (c) async => Spec(fc: '${_fitBlur(1080, 1920)},setsar=1[v]', pre: c.isVideo ? const ['-t', '180'] : const []), sub: '1080×1920'),
  _t('post_sq', sSoc, 'تحويل الفيديو إلى منشور مربع', In.both, (c) async => Spec(fc: '${_fitBlur(1080, 1080)},setsar=1[v]')),
  _t('cover_make', sSoc, 'تحويل الفيديو إلى غلاف', In.both, (c) async => Spec(out: Out.image, pre: c.isVideo ? ['-ss', _tf(c.dur * 0.1)] : const [], fc: '${_fitBlur(1080, 1920)},setsar=1[v]')),
  _t('land2port', sSoc, 'تحويل الفيديو الأفقي إلى عمودي', In.both, (c) async => Spec(fc: '${_fitBlur(1080, 1920, blur: c.n('bl', 28).toInt())},setsar=1[v]'),
      opts: [_c('bl', 'تغبيش الخلفية', [('12', 'خفيف'), ('28', 'متوسط'), ('50', 'قوي')], '28')]),
  _t('port2land', sSoc, 'تحويل الفيديو العمودي إلى أفقي', In.both, (c) async => Spec(fc: '${_fitBlur(1920, 1080, blur: c.n('bl', 28).toInt())},setsar=1[v]'),
      opts: [_c('bl', 'تغبيش الخلفية', [('12', 'خفيف'), ('28', 'متوسط'), ('50', 'قوي')], '28')]),
  _t('sq_crop', sSoc, 'قص مربع للفيديو', In.both, (c) async => _vf("crop='min(iw,ih)':'min(iw,ih)',scale=1080:1080:flags=lanczos")),
  _t('tg_sticker_v', sSoc, 'تحويل الفيديو إلى ملصق متحرك', In.video, (c) async => Spec(
      vf: 'scale=512:512:force_original_aspect_ratio=decrease,pad=512:512:(ow-iw)/2:(oh-ih)/2:color=black@0,format=yuva420p',
      pre: const ['-t', '3'],
      ext: 'webm',
      enc: const ['-c:v', 'libvpx-vp9', '-pix_fmt', 'yuva420p', '-b:v', '220k', '-auto-alt-ref', '0', '-r', '30'],
      noAudio: true), sub: 'WebM لتيليجرام'),
  _t('yt_thumb', sSoc, 'إنشاء صورة مصغّرة ليوتيوب', In.both, (c) async {
    final t = c.o('text').trim();
    final String fc;
    final ins = <String>[];
    if (t.isEmpty) {
      fc = '[0:v]scale=1280:720:force_original_aspect_ratio=increase,crop=1280:720,eq=saturation=1.3:contrast=1.1[v]';
    } else {
      final png = await c.text(t, size: 150, color: _hex(c.o('color')), box: false, maxW: 1100);
      ins.addAll(['-i', png]);
      fc = '[0:v]scale=1280:720:force_original_aspect_ratio=increase,crop=1280:720,eq=saturation=1.3:contrast=1.1[b];[b][1:v]overlay=x=(W-w)/2:${_yPos(c.o('pos'))}[v]';
    }
    return Spec(fc: fc, inputs: ins, out: Out.image, pre: c.isVideo ? ['-ss', _tf(c.dur * 0.2)] : const []);
  }, opts: [_txt('text', 'نص العنوان (اختياري)', ''), _c('color', 'اللون', _colorsHex, '0xFDD835'), _c('pos', 'المكان', _pos3, 'bot')]),
  _t('grab_frame', sSoc, 'استخراج صورة من الفيديو', In.video, (c) async => Spec(out: Out.image, pre: ['-ss', _tf(math.min(c.n('t', 1), math.max(0, c.dur - 0.1)))]),
      opts: [_txt('t', 'عند الثانية', '1')]),
  _t('grab_cover', sSoc, 'استخراج غلاف الفيديو', In.video, (c) async => Spec(out: Out.image, pre: ['-ss', _tf(c.dur * 0.12)], vf: 'scale=1280:-2:flags=lanczos,unsharp=5:5:0.6')),

  // ================= الصور =================
  _t('img_rotate', sImg, 'تدوير الصورة بزاوية مخصصة', In.image, (c) async => _vf('rotate=${c.o('a')}*PI/180:c=black:ow=rotw(${c.o('a')}*PI/180):oh=roth(${c.o('a')}*PI/180)'),
      opts: [_c('a', 'الزاوية', [('15', '15°'), ('30', '30°'), ('45', '45°'), ('-15', '-15°'), ('-30', '-30°'), ('-45', '-45°')], '15')]),
  _t('circle', sImg, 'قص الصورة بشكل دائري', In.image, (c) async {
    final m = await c.layer('circle_mask', lw: 800, lh: 800);
    return Spec(fc: '[0:v]crop=min(iw\\,ih):min(iw\\,ih),scale=800:800,format=rgba[a];[1:v]format=gray[m];[a][m]alphamerge[v]', inputs: ['-i', m], ext: 'png', out: Out.image);
  }),
  _t('img_sticker', sImg, 'تحويل الصورة إلى ملصق', In.image, (c) async => const Spec(
      fc: '[0:v]format=rgba,scale=480:480:force_original_aspect_ratio=decrease,pad=512:512:(ow-iw)/2:(oh-ih)/2:color=black@0,split=3[a][b][c];[b]alphaextract,dilation,dilation,dilation,dilation,dilation,dilation[m];[c]lutrgb=r=255:g=255:b=255[w];[w][m]alphamerge[o];[o][a]overlay=0:0[v]',
      ext: 'webp', out: Out.image, post: ['-c:v', 'libwebp', '-lossless', '0', '-quality', '92']), sub: 'ملصق بحدّ أبيض 512×512'),
  _t('rm_white', sImg, 'إزالة الخلفية البيضاء من الصورة', In.image, (c) async => Spec(vf: 'format=rgba,colorkey=0xFFFFFF:${c.o('sim')}:0.12', ext: 'png', out: Out.image),
      opts: [_c('sim', 'الحساسية', [('0.15', 'دقيقة'), ('0.28', 'متوسطة'), ('0.4', 'واسعة')], '0.28')]),
  _t('img_bw', sImg, 'تحويل الصورة إلى أبيض وأسود', In.image, _bw),
  _t('img_sketch', sImg, 'تحويل الصورة إلى رسم بالقلم', In.image, _sketch),
  _t('img_cartoon', sImg, 'تحويل الصورة إلى كرتون', In.image, _cartoon),
  _t('img_resize', sImg, 'تغيير حجم الصورة', In.image, (c) async {
    final t = c.o('w');
    return _vf("scale='min($t,iw)':-2:flags=lanczos");
  }, opts: [_c('w', 'العرض', [('480', '480'), ('720', '720'), ('1080', '1080'), ('1920', '1920')], '1080')]),
  _t('img_compress', sImg, 'ضغط الصورة', In.image, (c) async => Spec(vf: "scale='min(${c.o('w')},iw)':-2", out: Out.image, post: ['-q:v', c.o('q')]),
      opts: [_c('w', 'أقصى عرض', [('1080', '1080'), ('1600', '1600'), ('2400', '2400')], '1600'), _c('q', 'الجودة', [('4', 'عالية'), ('9', 'متوسطة'), ('16', 'منخفضة')], '9')]),
  _t('img_convert', sImg, 'تحويل صيغة الصورة', In.image, (c) async {
    final f = c.o('fmt');
    return Spec(out: Out.image, ext: f, post: f == 'webp' ? const ['-c:v', 'libwebp', '-quality', '90'] : const []);
  }, opts: [_c('fmt', 'الصيغة', [('jpg', 'JPG'), ('png', 'PNG'), ('webp', 'WebP')], 'png')]),
  _t('collage', sImg, 'دمج صور في صورة واحدة (كولاج)', In.multi, (c) async {
    final n = c.files.length;
    if (n < 2 || n > 9) throw ToolError('اختر من 2 إلى 9 صور');
    final cols = n <= 3 ? n : (n <= 4 ? 2 : 3);
    final cell = 600;
    final ins = <String>[];
    final g = StringBuffer();
    for (var i = 0; i < n; i++) {
      ins.addAll(['-i', c.files[i]]);
      g.write('[$i:v]scale=$cell:$cell:force_original_aspect_ratio=increase,crop=$cell:$cell,setsar=1[s$i];');
    }
    final lay = <String>[];
    for (var i = 0; i < n; i++) {
      final x = i % cols, y = i ~/ cols;
      lay.add('${x == 0 ? '0' : List.generate(x, (_) => 'w0').join('+')}_${y == 0 ? '0' : List.generate(y, (_) => 'h0').join('+')}');
    }
    for (var i = 0; i < n; i++) {
      g.write('[s$i]');
    }
    g.write('xstack=inputs=$n:layout=${lay.join('|')}:fill=white[v]');
    return Spec(fc: g.toString(), allInputs: ins, out: Out.image);
  }, sub: '2 إلى 9 صور'),
  _t('img_border', sImg, 'إضافة إطار للصورة', In.image, (c) async {
    final b = c.n('b', 40).toInt();
    return _vf('scale=${c.w}:${c.h},pad=iw+${2 * b}:ih+${2 * b}:$b:$b:color=${c.o('col')}');
  }, opts: [_c('b', 'السماكة', [('20', 'رفيع'), ('40', 'متوسط'), ('80', 'سميك')], '40'), _c('col', 'اللون', _colorsHex, '0xFFFFFF')]),
  _t('img_flip', sImg, 'قلب الصورة', In.image, _flip, opts: [_c('d', 'الاتجاه', [('h', 'أفقي'), ('v', 'عمودي'), ('hv', 'الاثنان')], 'h')]),
  _t('img_sharp', sImg, 'توضيح الصورة', In.image, (c) async => _vf('unsharp=7:7:${c.o('a')}:7:7:0.0'), opts: [_c('a', 'القوة', [('0.6', 'خفيفة'), ('1.1', 'متوسطة'), ('1.8', 'قوية')], '1.1')]),
  _t('img_denoise', sImg, 'تقليل تشويش الصورة', In.image, (c) async => _vf('hqdn3d=${c.o('a')}:${c.o('a')}:6:6'), opts: [_c('a', 'القوة', [('2', 'خفيفة'), ('4', 'متوسطة'), ('8', 'قوية')], '4')]),
  _t('img_bright', sImg, 'تغيير سطوع الصورة', In.image, (c) async => _vf('eq=brightness=${c.o('b')}'), opts: [_c('b', 'السطوع', [('-0.25', 'أغمق كثيراً'), ('-0.12', 'أغمق'), ('0.12', 'أفتح'), ('0.25', 'أفتح كثيراً')], '0.12')]),
  _t('img_sat', sImg, 'تغيير تشبع الألوان', In.image, (c) async => _vf('eq=saturation=${c.o('s')}'), opts: [_c('s', 'التشبع', [('0.4', 'باهت'), ('0.8', 'أقل'), ('1.4', 'أعلى'), ('1.9', 'قوي')], '1.4')]),
  _t('img_warm', sImg, 'تغيير حرارة اللون', In.image, (c) async => _vf(c.o('t') == 'warm' ? 'colorbalance=rs=.18:gs=.04:bs=-.18:rm=.1:bm=-.1' : 'colorbalance=rs=-.18:bs=.2:rm=-.1:bm=.12'), opts: [_c('t', 'الاتجاه', [('warm', 'أدفأ'), ('cool', 'أبرد')], 'warm')]),
  _t('img_move', sImg, 'تحويل الصورة إلى فيديو بحركة', In.image, (c) async {
    final d = c.n('dur', 8);
    final fr = (d * 30).round();
    final W = c.w, H = c.h;
    final z = c.o('m');
    String zp;
    switch (z) {
      case 'out':
        zp = "z='if(eq(on,0),1.35,max(1.0,zoom-0.0013))':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)'";
        break;
      case 'left':
        zp = "z=1.25:x='(iw-iw/zoom)*(1-on/$fr)':y='ih/2-(ih/zoom/2)'";
        break;
      case 'right':
        zp = "z=1.25:x='(iw-iw/zoom)*on/$fr':y='ih/2-(ih/zoom/2)'";
        break;
      default:
        zp = "z='min(zoom+0.0013,1.35)':x='iw/2-(iw/zoom/2)':y='ih/2-(ih/zoom/2)'";
    }
    return Spec(vf: 'scale=${W * 2}:${H * 2},zoompan=$zp:d=$fr:s=${W}x$H:fps=30', out: Out.video, dur: d, noAudio: true);
  }, opts: [_c('m', 'الحركة', [('in', 'تقريب'), ('out', 'ابتعاد'), ('left', 'تحرك لليسار'), ('right', 'تحرك لليمين')], 'in'), _durOpt]),
  _t('yt_thumb_img', sImg, 'إنشاء صورة مصغّرة بنص', In.image, (c) async {
    final t = c.o('text').trim();
    if (t.isEmpty) throw ToolError('اكتب النص أولاً');
    final png = await c.text(t, size: 150, color: _hex(c.o('color')), maxW: 1100);
    return Spec(fc: '[0:v]scale=1280:720:force_original_aspect_ratio=increase,crop=1280:720,eq=saturation=1.3:contrast=1.1[b];[b][1:v]overlay=x=(W-w)/2:${_yPos(c.o('pos'))}[v]', inputs: ['-i', png]);
  }, opts: [_txt('text', 'النص', 'عنوان'), _c('color', 'اللون', _colorsHex, '0xFDD835'), _c('pos', 'المكان', _pos3, 'bot')]),
];

JobOpt _palOpt() => const JobOpt('pal', 'الألوان', [
      ('0xFF512F,0xDD2476,0xFFC371', 'غروب'),
      ('0x1E3C72,0x2A5298,0x00C6FF', 'محيط'),
      ('0x8E2DE2,0x4A00E0,0xFF0080', 'نيون'),
      ('0x11998E,0x38EF7D,0x0F2027', 'غابة'),
      ('0x232526,0x414345,0x0F0F0F', 'داكن'),
    ], '0xFF512F,0xDD2476,0xFFC371');

/// كل الأدوات بترتيب الأقسام
List<JobDef> get kAllJobs {
  final all = [...kJobs, ...kNewTools];
  final out = <JobDef>[];
  for (final sec in kSectionOrder) {
    out.addAll(all.where((j) => j.group == sec));
  }
  return out;
}

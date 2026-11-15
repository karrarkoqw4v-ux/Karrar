import 'package:flutter/material.dart';
import 'custom.dart';
import 'media.dart';
import 'toolrun.dart';
import 'tools.dart';

const sReal = 'واقعية الفيديوهات';

double _p(int s, List<double> v) => v[(s - 1).clamp(0, v.length - 1)];

const _sCurve = "curves=all='0/0 0.25/0.2 0.75/0.8 1/1'";

JobOpt _res(String def) => JobOpt('res', 'الدقة', const [('orig', 'كما هي'), ('1080', 'Full HD'), ('1440', '2K'), ('2160', '4K')], def);
const JobOpt _str = JobOpt('s', 'القوة', [('1', 'خفيفة'), ('2', 'متوسطة'), ('3', 'قوية')], '2');

/// أداة واقعية: [chain] تبني سلسلة الفلاتر حسب القوة (1..3)
JobDef _rt(String id, String title, String Function(int s) chain, {String res = 'orig', bool showRes = true, String sub = '', bool showStr = true}) {
  return JobDef(id, sReal, title, sub, Icons.auto_awesome_rounded, PickKind.both, [if (showRes) _res(res), if (showStr) _str],
      inp: In.both, build: (Ctx c) async {
    final s = int.tryParse(c.o('s')) ?? 2;
    final r = c.p['res'] ?? 'orig';
    final sc = r == 'orig' ? '' : '${shortScale(r)},';
    return Spec(vf: '$sc${chain(s)}', enc: c.isVideo ? const ['-c:v', 'libx264', '-preset', 'veryfast', '-crf', '17', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '192k', '-movflags', '+faststart'] : null);
  });
}

final List<JobDef> kRealTools = [
  // ===== رفع الدقة والتفاصيل =====
  _rt('r_up1080', 'رفع الدقة إلى Full HD', (s) => 'cas=${_p(s, [.3, .5, .7])}', res: '1080'),
  _rt('r_up2k', 'رفع الدقة إلى 2K', (s) => 'hqdn3d=1:1:3:3,unsharp=5:5:${_p(s, [.5, .8, 1.1])}:5:5:0.0', res: '1440'),
  _rt('r_up4k', 'رفع الدقة إلى 4K', (s) => 'hqdn3d=1.2:1.2:3:3,unsharp=5:5:${_p(s, [.5, .8, 1.2])}:5:5:0.0', res: '2160'),
  _rt('r_up4k_detail', 'رفع الدقة 4K مع تفاصيل حادة', (s) => 'hqdn3d=1:1:3:3,unsharp=5:5:${_p(s, [.6, .9, 1.3])}:5:5:0.0,unsharp=13:13:0.35:13:13:0.0,cas=0.4', res: '2160'),
  JobDef('r_up2x', sReal, 'تكبير الدقة ضعفين (2×)', '', Icons.auto_awesome_rounded, PickKind.both, [_str], inp: In.both, build: (c) async {
    final s = int.tryParse(c.o('s')) ?? 2;
    return Spec(vf: 'scale=trunc(iw*2/2)*2:trunc(ih*2/2)*2:flags=lanczos,hqdn3d=1:1:3:3,unsharp=5:5:${_p(s, [.5, .8, 1.1])}:5:5:0.0');
  }),
  _rt('r_edges', 'تحديد الحواف الدقيق', (s) => 'cas=${_p(s, [.35, .6, .85])}'),
  _rt('r_fine', 'إبراز التفاصيل الدقيقة', (s) => 'unsharp=3:3:${_p(s, [.8, 1.3, 2.0])}:3:3:0.0'),
  _rt('r_texture', 'إبراز الملمس', (s) => 'unsharp=9:9:${_p(s, [.5, .8, 1.1])}:9:9:0.0'),
  _rt('r_clarity', 'وضوح عالٍ (Clarity)', (s) => 'unsharp=13:13:${_p(s, [.35, .55, .8])}:13:13:0.0,eq=contrast=1.04'),
  _rt('r_cine_sharp', 'حدة سينمائية ناعمة', (s) => 'unsharp=7:7:${_p(s, [.4, .6, .9])}:7:7:0.0'),
  _rt('r_skin', 'تنعيم البشرة الواقعي', (s) => 'smartblur=lr=2:ls=${_p(s, [.4, .6, .9])}:lt=20,unsharp=3:3:0.3:3:3:0.0'),

  // ===== إزالة التشويش والعيوب =====
  _rt('r_dn_light', 'إزالة تشويش خفيفة', (s) => 'hqdn3d=${_p(s, [1, 1.5, 2.5])}:${_p(s, [1, 1.5, 2.5])}:3:3'),
  _rt('r_dn_strong', 'إزالة تشويش قوية', (s) => 'hqdn3d=${_p(s, [3, 4, 6])}:${_p(s, [2, 3, 5])}:8:8,atadenoise'),
  _rt('r_dn_low', 'إزالة تشويش الإضاءة المنخفضة', (s) => 'hqdn3d=${_p(s, [4, 6, 8])}:${_p(s, [3, 5, 7])}:9:9,eq=gamma=1.12:brightness=0.03'),
  _rt('r_dn_grain', 'إزالة حبيبات الفيديو', (s) => 'atadenoise=0a=${_p(s, [.02, .03, .05])}:0b=${_p(s, [.04, .06, .09])}'),
  _rt('r_deband', 'إزالة تقطع التدرجات', (s) => 'deband=range=${_p(s, [12, 20, 30]).round()}:blur=1', showStr: true),
  _rt('r_deflicker', 'إزالة الوميض', (s) => 'deflicker=size=${_p(s, [5, 7, 11]).round()}:mode=am'),
  _rt('r_deblock', 'إزالة مربعات الضغط', (s) => 'deblock=filter=${s >= 3 ? 'strong' : 'weak'}:block=8,hqdn3d=1:1:2:2'),
  _rt('r_clean', 'تنظيف شامل للفيديو', (s) => 'deband=range=20:blur=1,hqdn3d=${_p(s, [1.5, 2.5, 4])}:${_p(s, [1.2, 2, 3])}:4:4,unsharp=5:5:${_p(s, [.4, .6, .9])}:5:5:0.0'),

  // ===== الألوان =====
  _rt('r_vib', 'ألوان أوضح (Vibrance)', (s) => 'vibrance=intensity=${_p(s, [.4, .7, 1.0])}'),
  _rt('r_col_real', 'ألوان واقعية طبيعية', (s) => 'eq=saturation=${_p(s, [1.08, 1.14, 1.22])}:contrast=1.04,vibrance=intensity=${_p(s, [.2, .35, .5])}'),
  _rt('r_warm', 'ألوان سينمائية دافئة', (s) => 'colorbalance=rs=${_p(s, [.04, .07, .1])}:bs=-${_p(s, [.04, .07, .1])}:rm=.05:bm=-.05,$_sCurve,eq=saturation=1.08'),
  _rt('r_cool', 'ألوان سينمائية باردة', (s) => 'colorbalance=rs=-${_p(s, [.04, .06, .09])}:bs=${_p(s, [.05, .08, .12])}:rm=-.04:bm=.05,$_sCurve,eq=saturation=1.05'),
  _rt('r_autocol', 'تحسين الألوان تلقائياً', (s) => 'normalize=smoothing=30:independence=${_p(s, [.3, .5, .8])}'),
  _rt('r_autolight', 'تحسين الإضاءة تلقائياً', (s) => 'normalize=smoothing=30:independence=0,eq=gamma=${_p(s, [1.02, 1.06, 1.1])}'),
  _rt('r_shadows', 'رفع تفاصيل الظلال', (s) => "curves=all='0/${_p(s, [.03, .05, .08])} 0.25/${_p(s, [.3, .34, .38])} 0.5/0.55 1/1'"),
  _rt('r_highs', 'استعادة الإضاءات العالية', (s) => "curves=all='0/0 0.75/${_p(s, [.73, .7, .66])} 1/${_p(s, [.97, .94, .9])}'"),
  _rt('r_contrast', 'تباين سينمائي', (s) => "curves=all='0/0 0.25/${_p(s, [.21, .18, .14])} 0.75/${_p(s, [.79, .82, .86])} 1/1'"),
  _rt('r_soft_contrast', 'تباين ناعم (فيلمي)', (s) => "curves=all='0/${_p(s, [.04, .06, .09])} 0.5/0.5 1/${_p(s, [.97, .95, .92])}'"),
  _rt('r_blacks', 'تعميق الألوان الداكنة', (s) => "curves=all='0/0 0.15/${_p(s, [.12, .09, .06])} 1/1'"),
  _rt('r_skin_tone', 'درجة بشرة طبيعية', (s) => 'colorbalance=rm=${_p(s, [.03, .05, .07])}:gm=.01:bm=-${_p(s, [.02, .03, .05])},eq=saturation=1.05'),
  _rt('r_sky', 'سماء أزرق أعمق', (s) => 'colorbalance=bs=${_p(s, [.05, .08, .12])}:bm=${_p(s, [.04, .06, .09])}:rs=-.03,eq=saturation=1.1'),
  _rt('r_green', 'أخضر نباتات أنضر', (s) => 'colorbalance=gm=${_p(s, [.05, .08, .12])}:rm=-.03,eq=saturation=1.1'),
  _rt('r_golden', 'ضوء ذهبي (Golden Hour)', (s) => 'colorbalance=rs=${_p(s, [.08, .12, .17])}:gs=.04:bs=-${_p(s, [.1, .14, .19])}:rm=.1:gm=.02:bm=-.1,eq=saturation=1.2:contrast=1.05'),
  _rt('r_tungsten', 'تصحيح الإضاءة الصفراء', (s) => 'colorbalance=rs=-${_p(s, [.07, .1, .14])}:bs=${_p(s, [.08, .12, .17])}:rm=-.07:bm=.08'),
  _rt('r_fluor', 'تصحيح الإضاءة الباردة', (s) => 'colorbalance=rs=${_p(s, [.07, .1, .14])}:bs=-${_p(s, [.07, .1, .14])}:rm=.07:bm=-.07'),
  _rt('r_white', 'بياض أنقى', (s) => "eq=brightness=${_p(s, [.01, .02, .03])}:contrast=1.06,curves=all='0/0 0.8/0.86 1/1'"),
  _rt('r_tame', 'تهدئة التشبع المفرط', (s) => 'eq=saturation=${_p(s, [.92, .85, .75])}'),

  // ===== HDR وإضاءة =====
  _rt('r_hdr', 'تأثير HDR واقعي', (s) => 'unsharp=13:13:${_p(s, [.4, .6, .9])}:13:13:0.0,$_sCurve,eq=saturation=${_p(s, [1.1, 1.2, 1.3])},vibrance=intensity=.3'),
  _rt('r_hdr_soft', 'HDR خفيف', (s) => 'unsharp=13:13:${_p(s, [.25, .4, .55])}:13:13:0.0,eq=saturation=1.1:contrast=1.03'),
  _rt('r_shadow_detail', 'تفاصيل الظلال والعتمة', (s) => "curves=all='0/0 0.2/${_p(s, [.26, .3, .35])} 0.5/0.55 1/1',unsharp=7:7:0.5:7:7:0.0"),
  _rt('r_vig', 'تعتيم الأطراف الناعم', (s) => 'vignette=PI/${_p(s, [7, 5.5, 4])}'),
  _rt('r_light3d', 'إضاءة ثلاثية الأبعاد (عمق)', (s) => 'vignette=PI/5,unsharp=13:13:${_p(s, [.4, .6, .8])}:13:13:0.0,eq=contrast=1.05'),
  _rt('r_dehaze', 'إزالة الضباب من المشهد', (s) => "eq=contrast=${_p(s, [1.1, 1.18, 1.28])}:brightness=-0.03:saturation=1.15,curves=all='0/0 0.2/0.13 1/1',unsharp=9:9:0.4:9:9:0.0"),
  _rt('r_haze_light', 'إزالة ضباب خفيف', (s) => "eq=contrast=${_p(s, [1.05, 1.1, 1.15])}:saturation=1.08,curves=all='0/0 0.2/0.16 1/1'"),

  // ===== مظاهر الأفلام والكاميرات =====
  _rt('r_film', 'فيلم سينمائي واقعي', (s) => "noise=alls=${_p(s, [6, 10, 16]).round()}:allf=t,$_sCurve,colorbalance=rs=.03:bs=-.03,vignette=PI/6"),
  _rt('r_grain35', 'حبيبات فيلم 35mm', (s) => 'noise=alls=${_p(s, [8, 14, 22]).round()}:allf=t'),
  _rt('r_kodak', 'ألوان كوداك', (s) => "colorbalance=rs=.06:gs=.02:bs=-.08:rm=.05,eq=saturation=${_p(s, [1.1, 1.18, 1.28])}:contrast=1.08,curves=all='0/0.03 0.5/0.5 1/0.97'"),
  _rt('r_fuji', 'ألوان فوجي', (s) => 'colorbalance=gs=.04:bs=.03:gm=.04,eq=saturation=${_p(s, [1.08, 1.14, 1.2])}:contrast=1.06'),
  _rt('r_doc', 'مظهر وثائقي طبيعي', (s) => 'eq=saturation=0.9:contrast=1.05,unsharp=5:5:${_p(s, [.4, .6, .8])}:5:5:0.0'),
  _rt('r_pro', 'مظهر تصوير احترافي', (s) => 'unsharp=9:9:${_p(s, [.4, .6, .8])}:9:9:0.0,eq=saturation=1.1:contrast=1.06,vibrance=intensity=.3'),
  _rt('r_log', 'مظهر كاميرا سينما (Log)', (s) => "curves=all='0/${_p(s, [.05, .07, .1])} 0.5/0.5 1/${_p(s, [.95, .92, .88])}',eq=saturation=0.9"),
  _rt('r_phone', 'مظهر هاتف حديث', (s) => 'unsharp=5:5:${_p(s, [.6, .9, 1.2])}:5:5:0.0,vibrance=intensity=${_p(s, [.3, .5, .7])},eq=contrast=1.05'),
  _rt('r_dslr', 'مظهر DSLR', (s) => 'vignette=PI/7,$_sCurve,unsharp=7:7:${_p(s, [.4, .5, .7])}:7:7:0.0'),
  _rt('r_reels', 'مظهر ريلز مشرق', (s) => 'eq=brightness=0.04:contrast=1.08:saturation=${_p(s, [1.2, 1.3, 1.45])},vibrance=intensity=.4'),
  _rt('r_highkey', 'مظهر نظيف مشرق (High Key)', (s) => "eq=brightness=${_p(s, [.04, .07, .1])}:contrast=0.95,curves=all='0/0.05 1/1'"),
  _rt('r_moody', 'مظهر داكن مزاجي', (s) => "eq=brightness=-0.06:contrast=${_p(s, [1.1, 1.15, 1.25])}:saturation=0.9,curves=all='0/0 0.3/0.22 1/1',vignette=PI/5"),

  // ===== حركة وتثبيت وترميم =====
  JobDef('r_smooth60', sReal, 'حركة أنعم 60 إطاراً', 'حتى 20 ثانية', Icons.auto_awesome_rounded, PickKind.video, const [], inp: In.video, build: (c) async => const Spec(
      vf: "scale=-2:'min(1080,ih)',minterpolate=fps=60:mi_mode=mci:mc_mode=aobmc:vsbmc=1", pre: ['-t', '20'])),
  _rt('r_deshake', 'تثبيت واقعي خفيف', (s) => 'deshake=rx=${_p(s, [16, 32, 48]).round()}:ry=${_p(s, [16, 32, 48]).round()}', showRes: false),
  JobDef('r_stab_sharp', sReal, 'تثبيت قوي مع توضيح', '', Icons.auto_awesome_rounded, PickKind.video, const [], inp: In.video, build: (c) async {
    final trf = '${c.tmp}/s.trf';
    final ok = await c.run.run(['-y', '-hide_banner', '-i', c.src, '-vf', 'vidstabdetect=shakiness=6:accuracy=9:result=$trf', '-f', 'null', '-']);
    if (ok) return Spec(vf: 'vidstabtransform=input=$trf:smoothing=15:zoom=1:optzoom=1,unsharp=5:5:0.7:5:5:0.0');
    return const Spec(vf: 'deshake,unsharp=5:5:0.7:5:5:0.0');
  }),
  _rt('r_old', 'تحسين الفيديوهات القديمة', (s) => 'hqdn3d=${_p(s, [2.5, 3.5, 5])}:${_p(s, [2, 3, 4])}:6:6,deband=range=20:blur=1,unsharp=5:5:${_p(s, [.5, .8, 1.1])}:5:5:0.0,eq=saturation=1.15:contrast=1.05', res: '1080'),
  _rt('r_repair', 'ترميم الفيديو التالف', (s) => "deblock=filter=weak:block=8,deband=range=24:blur=1,hqdn3d=${_p(s, [2, 3, 4.5])}:${_p(s, [1.5, 2.5, 4])}:5:5,unsharp=5:5:0.6:5:5:0.0,eq=contrast=1.05:saturation=1.1"),
  _rt('r_night', 'تحسين الفيديو المظلم', (s) => 'eq=gamma=${_p(s, [1.2, 1.35, 1.55])}:brightness=0.04,hqdn3d=${_p(s, [4, 5, 7])}:${_p(s, [3, 4, 6])}:8:8,unsharp=5:5:0.6:5:5:0.0'),
  _rt('r_sport', 'تحسين فيديو الحركة السريعة', (s) => 'unsharp=5:5:${_p(s, [.8, 1.1, 1.5])}:5:5:0.0,eq=contrast=1.06:saturation=1.12'),

  // ===== الأقوى: خطوط معالجة كاملة =====
  _rt('r_full', 'واقعية كاملة (الأقوى)', (s) => "deband=range=20:blur=1,hqdn3d=${_p(s, [1.2, 2, 3])}:${_p(s, [1, 1.6, 2.5])}:4:4,unsharp=5:5:${_p(s, [.5, .8, 1.1])}:5:5:0.0,unsharp=13:13:0.3:13:13:0.0,vibrance=intensity=${_p(s, [.3, .5, .7])},$_sCurve,noise=alls=3:allf=t", res: '1440'),
  _rt('r_full4k', 'واقعية كاملة بدقة 4K', (s) => "deband=range=20:blur=1,hqdn3d=${_p(s, [1.2, 2, 3])}:${_p(s, [1, 1.6, 2.5])}:4:4,unsharp=5:5:${_p(s, [.6, .9, 1.2])}:5:5:0.0,unsharp=13:13:0.35:13:13:0.0,vibrance=intensity=${_p(s, [.3, .5, .7])},$_sCurve", res: '2160'),
  _rt('r_ultra', 'واقعية خارقة (دقة + ألوان + تفاصيل)', (s) => "deband=range=24:blur=1,hqdn3d=${_p(s, [1.5, 2.2, 3.2])}:${_p(s, [1.2, 1.8, 2.6])}:4:4,cas=0.5,unsharp=13:13:0.4:13:13:0.0,vibrance=intensity=${_p(s, [.35, .55, .8])},colorbalance=rs=.03:bs=-.03,$_sCurve,eq=saturation=1.1,noise=alls=4:allf=t,vignette=PI/7", res: '2160'),
  _rt('r_vivid_real', 'ألوان نابضة واقعية', (s) => 'vibrance=intensity=${_p(s, [.6, .9, 1.2])},eq=saturation=${_p(s, [1.1, 1.18, 1.28])}:contrast=1.06,unsharp=5:5:0.6:5:5:0.0'),
];

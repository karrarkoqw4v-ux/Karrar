/// فلاتر: مصفوفة ألوان للمعاينة + تعبير FFmpeg للتصدير (نفس المظهر تقريباً).
class FilterDef {
  final String id;
  final String name;
  final List<double>? matrix;
  final String? vf;
  const FilterDef(this.id, this.name, this.matrix, this.vf);
}

List<double> _mul(List<double> a, List<double> b) {
  // a ∘ b : يطبَّق b أولاً ثم a (مصفوفات 4x5)
  final r = List<double>.filled(20, 0);
  for (var i = 0; i < 4; i++) {
    for (var j = 0; j < 5; j++) {
      double s = 0;
      for (var k = 0; k < 4; k++) {
        s += a[i * 5 + k] * b[k * 5 + j];
      }
      if (j == 4) s += a[i * 5 + 4];
      r[i * 5 + j] = s;
    }
  }
  return r;
}

List<double> _sat(double s) => [
      0.2126 + 0.7874 * s, 0.7152 - 0.7152 * s, 0.0722 - 0.0722 * s, 0, 0,
      0.2126 - 0.2126 * s, 0.7152 + 0.2848 * s, 0.0722 - 0.0722 * s, 0, 0,
      0.2126 - 0.2126 * s, 0.7152 - 0.7152 * s, 0.0722 + 0.9278 * s, 0, 0,
      0, 0, 0, 1, 0,
    ];

List<double> _lin(double r, double g, double b, double o) => [
      r, 0, 0, 0, o * 255,
      0, g, 0, 0, o * 255,
      0, 0, b, 0, o * 255,
      0, 0, 0, 1, 0,
    ];

List<double> _con(double c) => _lin(c, c, c, 0.5 * (1 - c));

final List<FilterDef> kFilters = [
  const FilterDef('normal', 'طبيعي', null, null),
  FilterDef('bw', 'أبيض وأسود', _sat(0), 'hue=s=0'),
  const FilterDef('sepia', 'سيبيا', [0.393, 0.769, 0.189, 0, 0, 0.349, 0.686, 0.168, 0, 0, 0.272, 0.534, 0.131, 0, 0, 0, 0, 0, 1, 0],
      'colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131'),
  FilterDef('vivid', 'نابض', _mul(_con(1.1), _sat(1.5)), 'eq=saturation=1.5:contrast=1.1'),
  FilterDef('cool', 'بارد', _lin(0.9, 0.98, 1.12, 0), 'colorbalance=rs=-.1:bs=.15:rm=-.1:bm=.15'),
  FilterDef('warm', 'دافئ', _lin(1.12, 1.0, 0.88, 0), 'colorbalance=rs=.12:bs=-.1:rm=.1:bm=-.08'),
  FilterDef('fade', 'باهت', _mul(_lin(1, 1, 1, 0.05), _mul(_con(0.85), _sat(0.8))), 'eq=contrast=0.85:brightness=0.06:saturation=0.8'),
  FilterDef('dramatic', 'درامي', _mul(_con(1.35), _sat(1.15)), 'eq=contrast=1.35:saturation=1.15:brightness=-0.04,vignette=PI/5'),
  FilterDef('vintage', 'كلاسيكي', _mul(_lin(1.08, 1.0, 0.82, 0.02), _mul(_con(0.92), _sat(0.75))),
      'colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131,eq=contrast=0.92:saturation=0.9'),
  FilterDef('neon', 'نيون', _mul(_lin(1.05, 0.9, 1.25, 0), _mul(_con(1.2), _sat(1.8))), 'eq=saturation=1.8:contrast=1.2,colorbalance=bs=.2:rs=.1'),
];

FilterDef filterById(String id) => kFilters.firstWhere((f) => f.id == id, orElse: () => kFilters.first);

class TransitionDef {
  final String id;
  final String name;
  final String xfade;
  final String icon;
  const TransitionDef(this.id, this.name, this.xfade, this.icon);
}

const List<TransitionDef> kTransitions = [
  TransitionDef('cut', 'قطع مباشر', 'fade', '✂️'),
  TransitionDef('fade', 'تلاشي', 'fade', '🌫️'),
  TransitionDef('slide', 'انزلاق', 'slideleft', '➡️'),
  TransitionDef('zoom', 'تكبير', 'zoomin', '🔍'),
  TransitionDef('wipe', 'مسح', 'wipeleft', '🧹'),
  TransitionDef('circle', 'دائرة', 'circleopen', '⭕'),
  TransitionDef('dissolve', 'ذوبان', 'dissolve', '✨'),
  TransitionDef('radial', 'دوران', 'radial', '🌀'),
];

TransitionDef transitionById(String id) => kTransitions.firstWhere((t) => t.id == id, orElse: () => kTransitions[1]);

String filterNameOf(String id) => filterById(id).name;
String transitionNameOf(String id) => transitionById(id).name;

import 'dart:io';
import 'package:flutter/material.dart';
import 'package:gal/gal.dart';
import '../export.dart';
import '../models.dart';
import '../store.dart';
import '../theme.dart';
import 'player.dart';

const _resLabels = ['480p', '720p', '1080p', '2K', '4K'];
const _resVals = [480, 720, 1080, 1440, 2160];
const _fpsVals = [24, 25, 30, 50, 60];

/// شاشة إعدادات التصدير بنمط كاب كات (داكنة) + تنفيذ التصدير
class ExportPage extends StatefulWidget {
  final Project project;
  const ExportPage({super.key, required this.project});
  @override
  State<ExportPage> createState() => _ExportPageState();
}

class _ExportPageState extends State<ExportPage> {
  late ExportOptions opt = Store.exportOptions;
  bool busy = false;
  double prog = 0;
  String? error;
  String? resultPath;
  bool get photo => widget.project.photoMode;

  Widget _slider(String title, String? note, int idx, List<String> labels, ValueChanged<int> onChange) {
    return Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Row(children: [Text(title, style: tx(18, w: FontWeight.w800, color: Colors.white)), const Spacer(), if (note != null) Text(note, style: tx(12, color: K.dsub))]),
      Directionality(textDirection: TextDirection.ltr, child: SliderTheme(
        data: SliderTheme.of(context).copyWith(trackHeight: 4, activeTrackColor: K.accent, inactiveTrackColor: K.d3, thumbColor: Colors.white, overlayColor: K.accent.withValues(alpha: .2), thumbShape: const RoundSliderThumbShape(enabledThumbRadius: 11), tickMarkShape: const RoundSliderTickMarkShape(tickMarkRadius: 2), activeTickMarkColor: K.accent, inactiveTickMarkColor: K.d3),
        child: Slider(value: idx.toDouble(), min: 0, max: (labels.length - 1).toDouble(), divisions: labels.length - 1, onChanged: busy ? null : (v) => setState(() => onChange(v.round()))),
      )),
      Directionality(textDirection: TextDirection.ltr, child: Padding(padding: const EdgeInsets.symmetric(horizontal: 6), child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [for (var i = 0; i < labels.length; i++) Text(labels[i], style: tx(12, color: i == idx ? Colors.white : K.dsub, w: i == idx ? FontWeight.w800 : FontWeight.w700))]))),
    ]);
  }

  Future<void> _start() async {
    setState(() { busy = true; prog = 0; error = null; });
    await Store.saveExportOptions(opt);
    final res = await Exporter.run(widget.project, opt: opt, onProgress: (v) { if (mounted) setState(() => prog = v); });
    if (!mounted) return;
    if (res.path == null) {
      setState(() { busy = false; error = res.error ?? 'خطأ غير معروف'; });
      return;
    }
    var saved = 'حُفظ في المكتبة';
    try {
      if (!await Gal.hasAccess(toAlbum: true)) await Gal.requestAccess(toAlbum: true);
      if (res.path!.endsWith('.jpg')) {
        await Gal.putImage(res.path!, album: 'مونتاجي');
      } else if (res.path!.endsWith('.gif')) {
        await Gal.putImage(res.path!, album: 'مونتاجي');
      } else {
        await Gal.putVideo(res.path!, album: 'مونتاجي');
      }
      saved = 'حُفظ في معرض الجهاز (ألبوم مونتاجي) وفي المكتبة';
    } catch (_) {}
    await Store.addExport(ExportRec(res.path!, widget.project.name, DateTime.now(), widget.project.total, size: File(res.path!).lengthSync()));
    if (!mounted) return;
    setState(() { busy = false; resultPath = res.path; error = null; });
    toast(context, saved);
  }

  @override
  Widget build(BuildContext context) {
    final est = opt.estimateBytes(widget.project);
    final resIdx = _resVals.indexOf(opt.res).clamp(0, 4);
    final fpsIdx = _fpsVals.indexOf(opt.fps).clamp(0, 4);
    return Scaffold(
      backgroundColor: K.d1,
      body: SafeArea(
        child: Column(children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 10, 16, 6),
            child: Row(children: [
              Tap(
                onTap: busy ? null : (resultPath != null ? () => Navigator.of(context).pop() : _start),
                child: Container(padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 10), decoration: BoxDecoration(color: K.accent, borderRadius: BorderRadius.circular(12)), child: Row(mainAxisSize: MainAxisSize.min, children: [
                  if (busy) const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2.4, color: Colors.white)) else Icon(resultPath != null ? Icons.check_rounded : Icons.ios_share_rounded, color: Colors.white, size: 22),
                  const SizedBox(width: 8),
                  Text(busy ? '${(prog * 100).round()}%' : (resultPath != null ? 'تم' : 'تصدير'), style: tx(16, w: FontWeight.w800, color: Colors.white)),
                ])),
              ),
              const Spacer(),
              if (!photo) ...[
                Tap(onTap: busy ? null : () => setState(() => opt.gif = true), child: Text('GIF', style: tx(24, w: FontWeight.w800, color: opt.gif ? Colors.white : K.dsub))),
                const SizedBox(width: 18),
                Tap(onTap: busy ? null : () => setState(() => opt.gif = false), child: Text('فيديو', style: tx(24, w: FontWeight.w800, color: !opt.gif ? Colors.white : K.dsub))),
              ] else
                Text('صورة', style: tx(24, w: FontWeight.w800, color: Colors.white)),
              const SizedBox(width: 12),
              Tap(onTap: busy ? null : () => Navigator.of(context).pop(), child: const Icon(Icons.close_rounded, color: Colors.white, size: 30)),
            ]),
          ),
          if (busy) LinearProgressIndicator(value: prog == 0 ? null : prog, minHeight: 4, color: K.accent, backgroundColor: K.d3),
          Expanded(
            child: ListView(padding: const EdgeInsets.fromLTRB(22, 18, 22, 22), children: [
              _slider('الدقة', 'الدقة القياسية', resIdx, _resLabels, (i) => opt.res = _resVals[i]),
              const SizedBox(height: 26),
              if (!photo && !opt.gif) ...[
                _slider('معدل الإطار', 'تشغيل أكثر سلاسة', fpsIdx, const ['24', '25', '30', '50', '60'], (i) => opt.fps = _fpsVals[i]),
                const SizedBox(height: 26),
                _slider('معدل البت', 'جودة الفيديو', opt.quality, const ['منخفضة', 'موصى بها', 'عالية'], (i) => opt.quality = i),
                const SizedBox(height: 26),
              ],
              Container(
                padding: const EdgeInsets.symmetric(vertical: 16),
                decoration: const BoxDecoration(border: Border(top: BorderSide(color: K.d3), bottom: BorderSide(color: K.d3))),
                child: Row(children: [Text('علامة مائية', style: tx(18, w: FontWeight.w800, color: Colors.white)), const Spacer(), Text('لا شيء', style: tx(15, color: K.dsub)), const Icon(Icons.chevron_left_rounded, color: K.dsub)]),
              ),
              const SizedBox(height: 18),
              Center(child: Text(photo ? 'صورة بدقة ${dimsFor(widget.project.ratio, opt.res).w}×${dimsFor(widget.project.ratio, opt.res).h}' : 'حجم الملف المقدر: ${fmtSize(est)}', style: tx(14, color: K.dsub))),
              if (error != null) ...[
                const SizedBox(height: 18),
                Container(padding: const EdgeInsets.all(14), decoration: BoxDecoration(color: const Color(0xFF3A1D1D), borderRadius: BorderRadius.circular(14)), child: Text('فشل التصدير:\n$error', textDirection: TextDirection.ltr, style: tx(11, color: const Color(0xFFFFB4B4)))),
              ],
              if (resultPath != null) ...[
                const SizedBox(height: 22),
                PillButton('مشاهدة', icon: Icons.play_arrow_rounded, fillColor: K.accent, onTap: () => Navigator.of(context).push(MaterialPageRoute(builder: (_) => PlayerPage(path: resultPath!, title: widget.project.name)))),
              ],
            ]),
          ),
        ]),
      ),
    );
  }
}

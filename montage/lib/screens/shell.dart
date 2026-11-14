import 'package:flutter/material.dart';
import '../theme.dart';
import 'home_tab.dart';
import 'library_tab.dart';
import 'profile_tab.dart';
import 'templates_tab.dart';

class Shell extends StatefulWidget {
  final int initial;
  const Shell({super.key, this.initial = 0});
  @override
  State<Shell> createState() => _ShellState();
}

class _ShellState extends State<Shell> {
  late int _i = widget.initial;
  int _rev = 0;
  void refresh() => setState(() => _rev++);
  void goTab(int i) => setState(() => _i = i);

  @override
  Widget build(BuildContext context) {
    final tabs = [
      HomeTab(key: ValueKey('h$_rev'), onChanged: refresh, goLibrary: () => goTab(2)),
      TemplatesTab(key: const ValueKey('t'), onChanged: refresh),
      LibraryTab(key: ValueKey('l$_rev'), onChanged: refresh),
      ProfileTab(key: ValueKey('p$_rev'), onChanged: refresh),
    ];
    const items = [
      (Icons.content_cut_outlined, Icons.content_cut_rounded, 'تعديل'),
      (Icons.view_carousel_outlined, Icons.view_carousel_rounded, 'القوالب'),
      (Icons.folder_outlined, Icons.folder_rounded, 'مكتبة'),
      (Icons.person_outline_rounded, Icons.person_rounded, 'أنا'),
    ];
    return Scaffold(
      body: IndexedStack(index: _i, children: tabs),
      bottomNavigationBar: Container(
        decoration: const BoxDecoration(color: Colors.white, border: Border(top: BorderSide(color: Color(0xFFEDEEF1)))),
        child: SafeArea(
          top: false,
          child: SizedBox(
            height: 68,
            child: Row(children: [
              for (var k = 0; k < items.length; k++)
                Expanded(
                  child: Tap(
                    scale: 0.82,
                    onTap: () => setState(() => _i = k),
                    child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
                      AnimatedScale(
                        scale: _i == k ? 1.12 : 1.0,
                        duration: const Duration(milliseconds: 260),
                        curve: Curves.elasticOut,
                        child: Icon(_i == k ? items[k].$2 : items[k].$1, size: 31, color: _i == k ? K.ink : const Color(0xFF9AA0AA)),
                      ),
                      const SizedBox(height: 3),
                      Text(items[k].$3, style: tx(12, w: _i == k ? FontWeight.w800 : FontWeight.w700, color: _i == k ? K.ink : const Color(0xFF9AA0AA))),
                    ]),
                  ),
                ),
            ]),
          ),
        ),
      ),
    );
  }
}

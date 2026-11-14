import 'package:flutter/material.dart';
import '../theme.dart';
import 'account_tab.dart';
import 'drafts_tab.dart';
import 'home_tab.dart';
import 'templates_tab.dart';

class Shell extends StatefulWidget {
  const Shell({super.key});
  @override
  State<Shell> createState() => _ShellState();
}

class _ShellState extends State<Shell> {
  int _i = 0;
  int _rev = 0; // لإعادة تحميل القوائم بعد الرجوع من المحرر
  void refresh() => setState(() => _rev++);

  @override
  Widget build(BuildContext context) {
    final tabs = [
      HomeTab(key: ValueKey('h$_rev'), onChanged: refresh, goDrafts: () => setState(() => _i = 2)),
      TemplatesTab(key: ValueKey('t$_rev'), onChanged: refresh),
      DraftsTab(key: ValueKey('d$_rev'), onChanged: refresh),
      AccountTab(key: ValueKey('a$_rev'), onChanged: refresh),
    ];
    const items = [
      (Icons.movie_edit, 'المونتاج'),
      (Icons.dashboard_customize_rounded, 'القوالب'),
      (Icons.folder_special_rounded, 'المحفوظات'),
      (Icons.person_rounded, 'الحساب'),
    ];
    return Scaffold(
      body: IndexedStack(index: _i, children: tabs),
      bottomNavigationBar: Container(
        decoration: const BoxDecoration(color: C.surface, border: Border(top: BorderSide(color: C.line))),
        child: SafeArea(
          child: SizedBox(
            height: 66,
            child: Row(children: [
              for (var k = 0; k < items.length; k++)
                Expanded(
                  child: InkWell(
                    onTap: () => setState(() => _i = k),
                    child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
                      AnimatedContainer(
                        duration: const Duration(milliseconds: 220),
                        padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 4),
                        decoration: BoxDecoration(gradient: _i == k ? C.grad : null, borderRadius: BorderRadius.circular(14)),
                        child: Icon(items[k].$1, color: _i == k ? Colors.white : C.sub, size: 24),
                      ),
                      const SizedBox(height: 3),
                      Text(items[k].$2, style: TextStyle(fontSize: 11, fontWeight: _i == k ? FontWeight.w800 : FontWeight.w500, color: _i == k ? C.text : C.sub)),
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

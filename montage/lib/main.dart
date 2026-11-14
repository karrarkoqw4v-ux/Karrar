import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'export.dart';
import 'ffkit.dart';
import 'media.dart';
import 'screens/welcome.dart';
import 'store.dart';
import 'theme.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await Store.init();
  Exporter.runner = KitRunner();
  Exporter.tempDir = Store.cacheDir;
  Exporter.outDir = Store.exportDir;
  MediaBackend.instance = PhotoManagerBackend();
  runApp(const MontagiApp());
}

class MontagiApp extends StatelessWidget {
  const MontagiApp({super.key});
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'مونتاجي',
      debugShowCheckedModeBanner: false,
      theme: buildTheme(),
      locale: const Locale('ar'),
      supportedLocales: const [Locale('ar')],
      localizationsDelegates: const [GlobalMaterialLocalizations.delegate, GlobalWidgetsLocalizations.delegate, GlobalCupertinoLocalizations.delegate],
      home: const Splash(),
      builder: (context, child) => child!,
    );
  }
}


import 'package:flutter/material.dart';
import 'api.dart';
import 'feed_screen.dart';
import 'login_screen.dart';
import 'notifications.dart';
import 'theme.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  Notifications.init(); // يطلب إذن الإشعارات أول ما يفتح التطبيق
  runApp(const JamaatkumApp());
}

class JamaatkumApp extends StatelessWidget {
  const JamaatkumApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
        title: 'جماعتكم',
        debugShowCheckedModeBanner: false,
        theme: buildTheme(),
        builder: (c, child) => Directionality(textDirection: TextDirection.rtl, child: child!),
        home: FutureBuilder<bool>(
          future: Api.hasSession(),
          builder: (c, s) => s.connectionState != ConnectionState.done
              ? const Scaffold(body: Center(child: CircularProgressIndicator()))
              : (s.data! ? const FeedScreen() : const LoginScreen()),
        ),
      );
}

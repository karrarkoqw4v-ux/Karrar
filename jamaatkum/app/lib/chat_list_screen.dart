import 'dart:async';
import 'package:flutter/material.dart';
import 'api.dart';
import 'chat_screen.dart';
import 'notifications.dart';
import 'theme.dart';

/// وقت مختصر بالعربي: الآن، قبل ٥ د، قبل ٣ س، أمس...
String relTime(String iso) {
  if (iso.isEmpty) return '';
  final t = DateTime.tryParse(iso);
  if (t == null) return '';
  final d = DateTime.now().toUtc().difference(t.toUtc());
  const nums = '٠١٢٣٤٥٦٧٨٩';
  String ar(int n) => n.toString().split('').map((c) => nums[int.parse(c)]).join();
  if (d.inMinutes < 1) return 'الآن';
  if (d.inMinutes < 60) return 'قبل ${ar(d.inMinutes)} د';
  if (d.inHours < 24) return 'قبل ${ar(d.inHours)} س';
  if (d.inDays < 2) return 'أمس';
  return 'قبل ${ar(d.inDays)} يوم';
}

/// قائمة المحادثات
class ChatListScreen extends StatefulWidget {
  const ChatListScreen({super.key});
  @override
  State<ChatListScreen> createState() => _ChatListScreenState();
}

class _ChatListScreenState extends State<ChatListScreen> {
  List<dynamic>? _convs;
  String? _error;
  int _lastUnread = -1; // -1 = أول تحميل، ما نبلّغ عنه
  Timer? _poll;

  @override
  void initState() {
    super.initState();
    _load();
    // فحص دوري: لو زاد عدد الرسائل غير المقروءة نطلع إشعار (التطبيق لازم يكون شغّال)
    _poll = Timer.periodic(const Duration(seconds: 15), (_) => _load(silent: true));
  }

  @override
  void dispose() {
    _poll?.cancel();
    super.dispose();
  }

  Future<void> _load({bool silent = false}) async {
    try {
      final c = await Api.conversations();
      final unread = c.fold<int>(0, (s, x) => s + (x['unreadCount'] as int? ?? 0));
      if (_lastUnread != -1 && unread > _lastUnread) {
        Notifications.show(id: 1, title: 'رسالة جديدة', body: unread == 1 ? 'عندك رسالة جديدة' : 'عندك $unread رسائل غير مقروءة');
      }
      _lastUnread = unread;
      if (mounted) setState(() => _convs = c);
    } on ApiException catch (e) {
      if (mounted && !silent) setState(() => _error = e.message);
    } catch (_) {
      if (mounted && !silent) setState(() => _error = 'تعذر الاتصال بالسيرفر');
    }
  }

  Future<void> _openChat(Map<String, dynamic> conv) async {
    await Navigator.push(context, MaterialPageRoute(builder: (_) => ChatScreen(convId: conv['id'], otherUser: conv['user'])));
    _load(); // تحديث آخر رسالة وعداد غير المقروء بعد الرجوع
  }

  Future<void> _newChat() async {
    final ctrl = TextEditingController();
    String? err;
    bool loading = false;
    final conv = await showDialog<Map<String, dynamic>>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setD) => AlertDialog(
          backgroundColor: const Color(0xFF17161C),
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
          title: const Text('محادثة جديدة', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w800)),
          content: TextField(
            controller: ctrl,
            autofocus: true,
            textDirection: TextDirection.ltr,
            decoration: InputDecoration(hintText: 'اسم المستخدم (اليوزر)', errorText: err, prefixIcon: const Icon(Icons.alternate_email)),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('إلغاء')),
            TextButton(
              onPressed: loading
                  ? null
                  : () async {
                      final u = ctrl.text.trim();
                      if (u.isEmpty) return;
                      setD(() { loading = true; err = null; });
                      try {
                        final c = await Api.startConversation(u);
                        if (ctx.mounted) Navigator.pop(ctx, c);
                      } on ApiException catch (e) {
                        setD(() { err = e.message; loading = false; });
                      } catch (_) {
                        setD(() { err = 'تعذر الاتصال بالسيرفر'; loading = false; });
                      }
                    },
              child: const Text('بدء', style: TextStyle(color: brand, fontWeight: FontWeight.bold)),
            ),
          ],
        ),
      ),
    );
    if (conv != null) _openChat(conv);
  }

  Widget _avatar(Map<String, dynamic>? user) {
    final path = user?['avatar'] as String? ?? '';
    return CircleAvatar(
      radius: 28,
      backgroundColor: fieldFill,
      backgroundImage: path.isNotEmpty ? NetworkImage('$host$path') : null,
      child: path.isEmpty ? const Icon(Icons.person_rounded, size: 28, color: Colors.white38) : null,
    );
  }

  @override
  Widget build(BuildContext context) => SafeArea(
        bottom: false,
        child: Column(children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 10, 16, 6),
            child: Row(children: [
              Text('المحادثات', style: const TextStyle(fontSize: 21, fontWeight: FontWeight.w800)),
              const Spacer(),
              IconButton(
                onPressed: _newChat,
                icon: Container(
                  width: 38,
                  height: 38,
                  decoration: BoxDecoration(shape: BoxShape.circle, border: Border.all(color: outline, width: 1.5)),
                  child: const Icon(Icons.edit_outlined, size: 19),
                ),
              ),
            ]),
          ),
          Expanded(
            child: _error != null
                ? Center(child: Text(_error!, style: const TextStyle(color: Color(0xFFFFB84D))))
                : _convs == null
                    ? const Center(child: CircularProgressIndicator())
                    : _convs!.isEmpty
                        ? Center(
                            child: Column(mainAxisSize: MainAxisSize.min, children: [
                              const Icon(Icons.chat_bubble_outline_rounded, size: 54, color: Colors.white30),
                              const SizedBox(height: 10),
                              const Text('ما عندك محادثات بعد', style: TextStyle(color: Colors.white60)),
                              const SizedBox(height: 14),
                              TextButton(onPressed: _newChat, child: const Text('ابدأ محادثة', style: TextStyle(color: brand, fontWeight: FontWeight.bold))),
                            ]),
                          )
                        : RefreshIndicator(
                            onRefresh: _load,
                            child: ListView.builder(
                              padding: const EdgeInsets.only(bottom: 120),
                              itemCount: _convs!.length,
                              itemBuilder: (c, i) {
                                final conv = _convs![i] as Map<String, dynamic>;
                                final user = conv['user'] as Map<String, dynamic>?;
                                final unread = (conv['unreadCount'] as int?) ?? 0;
                                return ListTile(
                                  onTap: () => _openChat(conv),
                                  leading: _avatar(user),
                                  title: Text(user?['name'] ?? '', style: TextStyle(fontSize: 15, fontWeight: unread > 0 ? FontWeight.w800 : FontWeight.w700)),
                                  subtitle: Text(
                                    (conv['lastMessage'] as String?)?.isNotEmpty == true ? conv['lastMessage'] : 'ابدأ المحادثة',
                                    maxLines: 1,
                                    overflow: TextOverflow.ellipsis,
                                    style: TextStyle(fontSize: 13, color: unread > 0 ? Colors.white : Colors.white60),
                                  ),
                                  trailing: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.end, children: [
                                    Text(relTime(conv['lastAt'] ?? ''), style: const TextStyle(fontSize: 11.5, color: Colors.white54)),
                                    if (unread > 0) ...[
                                      const SizedBox(height: 6),
                                      Container(width: 10, height: 10, decoration: const BoxDecoration(color: cyan, shape: BoxShape.circle)),
                                    ],
                                  ]),
                                );
                              },
                            ),
                          ),
          ),
        ]),
      );
}

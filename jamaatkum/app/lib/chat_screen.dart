import 'dart:async';
import 'package:flutter/material.dart';
import 'api.dart';
import 'theme.dart';

class ChatScreen extends StatefulWidget {
  final String convId;
  final Map<String, dynamic> otherUser;
  const ChatScreen({super.key, required this.convId, required this.otherUser});
  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  List<dynamic> _msgs = [];
  bool _loading = true, _sending = false;
  String? _meId;
  final _ctrl = TextEditingController();
  final _scroll = ScrollController();
  Timer? _poll;

  @override
  void initState() {
    super.initState();
    _init();
    _poll = Timer.periodic(const Duration(seconds: 4), (_) => _load());
  }

  Future<void> _init() async {
    final u = await Api.currentUser();
    _meId = u?['id'];
    await _load(initial: true);
  }

  @override
  void dispose() {
    _poll?.cancel();
    _ctrl.dispose();
    _scroll.dispose();
    super.dispose();
  }

  Future<void> _load({bool initial = false}) async {
    try {
      final m = await Api.messages(widget.convId);
      if (!mounted) return;
      final grew = m.length != _msgs.length;
      setState(() { _msgs = m; _loading = false; });
      if (grew) _scrollToEnd();
    } catch (_) {
      if (mounted && initial) setState(() => _loading = false);
    }
  }

  void _scrollToEnd() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scroll.hasClients) _scroll.animateTo(_scroll.position.maxScrollExtent, duration: const Duration(milliseconds: 250), curve: Curves.easeOut);
    });
  }

  Future<void> _send() async {
    final text = _ctrl.text.trim();
    if (text.isEmpty || _sending) return;
    setState(() => _sending = true);
    _ctrl.clear();
    try {
      final m = await Api.sendMessage(widget.convId, text);
      setState(() => _msgs = [..._msgs, m]);
      _scrollToEnd();
    } on ApiException catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(e.message)));
    } finally {
      if (mounted) setState(() => _sending = false);
    }
  }

  Widget _avatar() {
    final path = widget.otherUser['avatar'] as String? ?? '';
    return CircleAvatar(radius: 18, backgroundColor: fieldFill, backgroundImage: path.isNotEmpty ? NetworkImage('$host$path') : null, child: path.isEmpty ? const Icon(Icons.person_rounded, size: 18, color: Colors.white38) : null);
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          backgroundColor: Colors.black,
          titleSpacing: 0,
          title: Row(children: [
            _avatar(),
            const SizedBox(width: 10),
            Expanded(child: Text(widget.otherUser['name'] ?? '', style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700), overflow: TextOverflow.ellipsis)),
          ]),
        ),
        body: Column(children: [
          Expanded(
            child: _loading
                ? const Center(child: CircularProgressIndicator())
                : _msgs.isEmpty
                    ? const Center(child: Text('ابعث أول رسالة 👋', style: TextStyle(color: Colors.white54)))
                    : ListView.builder(
                        controller: _scroll,
                        padding: const EdgeInsets.fromLTRB(12, 14, 12, 14),
                        itemCount: _msgs.length,
                        itemBuilder: (c, i) {
                          final m = _msgs[i] as Map<String, dynamic>;
                          final isMe = m['senderId'] == _meId;
                          return Align(
                            alignment: isMe ? Alignment.centerRight : Alignment.centerLeft,
                            child: Container(
                              constraints: BoxConstraints(maxWidth: MediaQuery.sizeOf(context).width * .74),
                              margin: const EdgeInsets.symmetric(vertical: 4),
                              padding: const EdgeInsets.symmetric(horizontal: 15, vertical: 10),
                              decoration: BoxDecoration(
                                color: isMe ? brand : fieldFill,
                                borderRadius: BorderRadius.circular(20),
                              ),
                              child: Text(m['text'] ?? '', style: TextStyle(fontSize: 15.5, color: isMe ? onBrand : Colors.white, height: 1.4)),
                            ),
                          );
                        },
                      ),
          ),
          SafeArea(
            top: false,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(10, 6, 10, 10),
              child: Row(children: [
                Expanded(
                  child: Container(
                    decoration: BoxDecoration(color: fieldFill, borderRadius: BorderRadius.circular(26)),
                    padding: const EdgeInsets.symmetric(horizontal: 16),
                    child: TextField(
                      controller: _ctrl,
                      minLines: 1,
                      maxLines: 4,
                      textInputAction: TextInputAction.send,
                      onSubmitted: (_) => _send(),
                      decoration: const InputDecoration(border: InputBorder.none, hintText: 'مراسلة…', isDense: true, contentPadding: EdgeInsets.symmetric(vertical: 12)),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                GestureDetector(
                  onTap: _send,
                  child: CircleAvatar(
                    radius: 23,
                    backgroundColor: brand,
                    child: _sending
                        ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2.2, color: onBrand))
                        : const Icon(Icons.send_rounded, color: onBrand, size: 20),
                  ),
                ),
              ]),
            ),
          ),
        ]),
      );
}

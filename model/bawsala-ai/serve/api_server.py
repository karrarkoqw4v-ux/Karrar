"""يقدّم نموذجك كخادم متوافق مع OpenAI، فيستخدمه تطبيق بوصلة كأي مزوّد (لا ذكاء خارجي).
    python serve/api_server.py --ckpt out/chat.pt --port 8000 [--key SECRET]
"""
import argparse
import json
import os
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from serve.auth import check as auth_check
from serve.model_runtime import Runtime
from serve.openai_compat import chunk, completion

LOCK = threading.Lock()


def build_handler(rt: Runtime, key: str):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *a):
            pass

        def _send(self, code, obj):
            body = json.dumps(obj, ensure_ascii=False).encode()
            self.send_response(code)
            self.send_header("Content-Type", "application/json")
            self.send_header("Access-Control-Allow-Origin", "*")
            self.end_headers()
            self.wfile.write(body)

        def do_OPTIONS(self):
            self.send_response(204)
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Access-Control-Allow-Headers", "*")
            self.end_headers()

        def do_GET(self):
            if self.path.rstrip("/").endswith("/models"):
                return self._send(200, {"object": "list", "data": [{"id": "bawsala", "object": "model"}]})
            self._send(200, {"ok": True, "model": "bawsala", "params_m": rt.params_millions()})

        def do_POST(self):
            if not auth_check(self.headers.get("Authorization", ""), key):
                return self._send(401, {"error": {"message": "invalid key"}})
            if not self.path.rstrip("/").endswith("/chat/completions"):
                return self._send(404, {"error": {"message": "not found"}})
            try:
                length = int(self.headers.get("Content-Length", 0))
                req = json.loads(self.rfile.read(length) or b"{}")
                ids = rt.build_prompt(req.get("messages", []))
                max_new = min(int(req.get("max_tokens") or 300), 1024)
                temperature = float(req.get("temperature", 0.7))
            except Exception as e:
                return self._send(400, {"error": {"message": str(e)}})

            if req.get("stream"):
                self.send_response(200)
                self.send_header("Content-Type", "text/event-stream")
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
                cid = ""
                try:
                    with LOCK:
                        for piece in rt.stream(ids, max_new, temperature):
                            cid = cid or "chatcmpl-live"
                            self.wfile.write(("data: " + json.dumps(chunk(cid, piece), ensure_ascii=False) + "\n\n").encode())
                            self.wfile.flush()
                    self.wfile.write(("data: " + json.dumps(chunk(cid, "", done=True)) + "\n\n").encode())
                    self.wfile.write(b"data: [DONE]\n\n")
                except Exception:
                    pass
                return
            with LOCK:
                text = "".join(rt.stream(ids, max_new, temperature))
            self._send(200, completion(text))

    return Handler


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ckpt", default="out/chat.pt")
    ap.add_argument("--tokenizer", default="out/tokenizer.json")
    ap.add_argument("--port", type=int, default=8000)
    ap.add_argument("--host", default="0.0.0.0")
    ap.add_argument("--key", default=os.environ.get("BAWSALA_KEY", ""))
    args = ap.parse_args()

    rt = Runtime(args.ckpt, args.tokenizer)
    print(f"بوصلة LM يعمل على http://{args.host}:{args.port}/v1  ({rt.params_millions()}M معامل، {rt.device})")
    ThreadingHTTPServer((args.host, args.port), build_handler(rt, args.key)).serve_forever()


if __name__ == "__main__":
    main()

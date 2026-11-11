"""Serve YOUR model (out/chat.pt) as an OpenAI-compatible API, so the Bawsala app can use it like any provider (no third-party AI).
    python serve.py --ckpt out/chat.pt --port 8000 [--key SECRET]
In the app: Settings > Models > custom provider: base URL http://<your-computer-ip>:8000/v1 , model "bawsala", key = SECRET (or any text if no --key)."""
import argparse, json, os, time, uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import torch, torch.nn.functional as F
from tokenizers import Tokenizer
from model import GPT, Config

ap = argparse.ArgumentParser()
ap.add_argument("--ckpt", default="out/chat.pt"); ap.add_argument("--tokenizer", default="out/tokenizer.json")
ap.add_argument("--port", type=int, default=8000); ap.add_argument("--host", default="0.0.0.0"); ap.add_argument("--key", default=os.environ.get("BAWSALA_KEY", ""))
a = ap.parse_args()
dev = torch.device("cuda" if torch.cuda.is_available() else "cpu")
tok = Tokenizer.from_file(a.tokenizer)
st = torch.load(a.ckpt, map_location=dev); cfg = Config(**st["cfg"])
M = GPT(cfg).to(dev); M.load_state_dict(st["model"]); M.eval()
U, A, E, B = (tok.token_to_id(x) for x in ("<|user|>", "<|assistant|>", "<|eos|>", "<|bos|>"))
LOCK = __import__("threading").Lock()

def build(msgs):
    ids = [B]
    for m in msgs:
        t = tok.encode(str(m.get("content", ""))).ids
        ids += ([A] + t + [E]) if m.get("role") == "assistant" else ([U] + t)   # system/user -> user turn
    return ids + [A]

@torch.no_grad()
def stream(ids, max_new, temp, top_k=50):
    """yields decoded text pieces; stops at <eos>"""
    out = []; prev = ""
    for _ in range(max_new):
        lg = M(torch.tensor([ids[-cfg.ctx:]], device=dev))[0][:, -1] / max(temp, 1e-5)
        if top_k:
            v, _ = torch.topk(lg, min(top_k, lg.size(-1))); lg[lg < v[:, [-1]]] = -float("inf")
        n = int(torch.multinomial(F.softmax(lg, -1), 1))
        if n == E: break
        ids.append(n); out.append(n)
        txt = tok.decode(out)
        if not txt.endswith("�"):          # wait until a multi-byte char is complete
            piece, prev = txt[len(prev):], txt
            if piece: yield piece

class H(BaseHTTPRequestHandler):
    def log_message(self, *x): pass
    def _send(self, code, obj, ctype="application/json"):
        b = obj if isinstance(obj, bytes) else json.dumps(obj, ensure_ascii=False).encode()
        self.send_response(code); self.send_header("Content-Type", ctype); self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "*"); self.send_header("Content-Length", str(len(b))); self.end_headers(); self.wfile.write(b)
    def do_OPTIONS(self): self._send(204, b"")
    def _auth(self):
        if a.key and self.headers.get("Authorization", "") != "Bearer " + a.key:
            self._send(401, {"error": {"message": "invalid key"}}); return False
        return True
    def do_GET(self):
        if self.path.rstrip("/") in ("/v1/models", "/models"):
            return self._send(200, {"object": "list", "data": [{"id": "bawsala", "object": "model", "owned_by": "you"}]})
        self._send(200, {"ok": True, "model": "bawsala", "params_m": round(M.n_params() / 1e6, 1), "device": str(dev)})
    def do_POST(self):
        if not self._auth(): return
        if not self.path.rstrip("/").endswith("/chat/completions"): return self._send(404, {"error": {"message": "not found"}})
        try:
            req = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))) or b"{}")
            ids = build(req.get("messages", [])); mx = min(int(req.get("max_tokens") or 300), 1024); tp = float(req.get("temperature", 0.7))
        except Exception as e:
            return self._send(400, {"error": {"message": str(e)}})
        cid, now = "chatcmpl-" + uuid.uuid4().hex[:12], int(time.time())
        if req.get("stream"):
            self.send_response(200); self.send_header("Content-Type", "text/event-stream"); self.send_header("Access-Control-Allow-Origin", "*"); self.end_headers()
            def ev(d): self.wfile.write(("data: " + json.dumps(d, ensure_ascii=False) + "\n\n").encode()); self.wfile.flush()
            try:
                with LOCK:
                    for p in stream(ids, mx, tp): ev({"id": cid, "object": "chat.completion.chunk", "created": now, "model": "bawsala", "choices": [{"index": 0, "delta": {"content": p}, "finish_reason": None}]})
                ev({"id": cid, "object": "chat.completion.chunk", "created": now, "model": "bawsala", "choices": [{"index": 0, "delta": {}, "finish_reason": "stop"}]})
                self.wfile.write(b"data: [DONE]\n\n")
            except Exception: pass
            return
        with LOCK: text = "".join(stream(ids, mx, tp))
        self._send(200, {"id": cid, "object": "chat.completion", "created": now, "model": "bawsala", "choices": [{"index": 0, "message": {"role": "assistant", "content": text}, "finish_reason": "stop"}]})

print(f"Bawsala LM serving on http://{a.host}:{a.port}/v1  ({M.n_params()/1e6:.1f}M params, {dev})")
ThreadingHTTPServer((a.host, a.port), H).serve_forever()

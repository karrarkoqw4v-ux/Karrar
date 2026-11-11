"""Stage 2: teach the pre-trained model to CHAT. Fine-tunes ckpt.pt on dialogues (jsonl lines: {"messages":[{"role":"user","content":..},{"role":"assistant","content":..}]}).
Loss is computed ONLY on the assistant's words. Example:  python sft.py --data data/sft/chat.jsonl --epochs 3
"""
import argparse, json, math, os, random
import torch
from tokenizers import Tokenizer
from model import GPT, Config

def encode(tok, msgs, ctx):
    U, A, E, B = (tok.token_to_id(x) for x in ("<|user|>", "<|assistant|>", "<|eos|>", "<|bos|>"))
    ids, lab = [B], [-100]
    for m in msgs:
        t = tok.encode(m["content"]).ids
        if m["role"] == "user":
            ids += [U] + t; lab += [-100] * (len(t) + 1)
        else:
            ids += [A] + t + [E]; lab += [-100] + t + [E]
    return ids[:ctx], lab[:ctx]

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ckpt", default="out/ckpt.pt"); ap.add_argument("--tokenizer", default="out/tokenizer.json")
    ap.add_argument("--data", default="data/sft/chat.jsonl"); ap.add_argument("--out", default="out/chat.pt")
    ap.add_argument("--epochs", type=int, default=3); ap.add_argument("--lr", type=float, default=5e-5); ap.add_argument("--batch", type=int, default=8)
    a = ap.parse_args()
    dev = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    tok = Tokenizer.from_file(a.tokenizer)
    s = torch.load(a.ckpt, map_location=dev)
    cfg = Config(**s["cfg"]); m = GPT(cfg).to(dev); m.load_state_dict(s["model"]); m.train()
    rows = [json.loads(l)["messages"] for l in open(a.data, encoding="utf-8") if l.strip()]
    data = [encode(tok, r, cfg.ctx) for r in rows]
    print(len(data), "dialogues")
    opt = torch.optim.AdamW(m.parameters(), lr=a.lr, weight_decay=0.0)
    fp16 = dev.type == "cuda" and not torch.cuda.is_bf16_supported()
    dt = torch.float16 if fp16 else torch.bfloat16
    sc = torch.cuda.amp.GradScaler(enabled=fp16)
    steps = a.epochs * math.ceil(len(data) / a.batch); k = 0
    for ep in range(a.epochs):
        random.shuffle(data)
        for i in range(0, len(data), a.batch):
            ch = data[i:i + a.batch]; T = max(len(x) for x, _ in ch)
            X = torch.tensor([x + [0] * (T - len(x)) for x, _ in ch], device=dev)
            Y = torch.tensor([y + [-100] * (T - len(y)) for _, y in ch], device=dev)
            for g in opt.param_groups: g["lr"] = a.lr * 0.5 * (1 + math.cos(math.pi * k / max(1, steps)))
            with torch.autocast(device_type=dev.type, dtype=dt, enabled=dev.type == "cuda"):
                logits, _ = m(X[:, :-1]); loss = torch.nn.functional.cross_entropy(logits.reshape(-1, logits.size(-1)).float(), Y[:, 1:].reshape(-1), ignore_index=-100)
            sc.scale(loss).backward(); sc.unscale_(opt); torch.nn.utils.clip_grad_norm_(m.parameters(), 1.0); sc.step(opt); sc.update(); opt.zero_grad(set_to_none=True); k += 1
            if k % 10 == 0: print(f"epoch {ep+1} step {k}/{steps} loss {loss.item():.3f}")
    torch.save({"model": m.state_dict(), "cfg": cfg.__dict__}, a.out); print("saved", a.out)

if __name__ == "__main__":
    main()

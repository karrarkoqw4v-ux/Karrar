"""المرحلة 4ب: يعلّم النموذج المسبَّق-تدريبه كيف يحاور (الخسارة على كلام المساعد فقط).
    python train/05_sft.py --data out/chat.jsonl --epochs 3
"""
import argparse
import json
import math
import os
import random
import sys

import torch

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from core import ModelConfig, BawsalaGPT  # noqa: E402
from tokenizers import Tokenizer  # noqa: E402


def encode_dialogue(tok, messages, ctx):
    user_id, asst_id, eos_id, bos_id = (tok.token_to_id(t) for t in ("<|user|>", "<|assistant|>", "<|eos|>", "<|bos|>"))
    ids, labels = [bos_id], [-100]
    for m in messages:
        tokens = tok.encode(m["content"]).ids
        if m["role"] == "user":
            ids += [user_id] + tokens
            labels += [-100] * (len(tokens) + 1)
        else:
            ids += [asst_id] + tokens + [eos_id]
            labels += [-100] + tokens + [eos_id]
    return ids[:ctx], labels[:ctx]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ckpt", default="out/ckpt.pt")
    ap.add_argument("--tokenizer", default="out/tokenizer.json")
    ap.add_argument("--data", default="out/chat.jsonl")
    ap.add_argument("--out", default="out/chat.pt")
    ap.add_argument("--epochs", type=int, default=3)
    ap.add_argument("--lr", type=float, default=5e-5)
    ap.add_argument("--batch", type=int, default=8)
    args = ap.parse_args()

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    tok = Tokenizer.from_file(args.tokenizer)
    state = torch.load(args.ckpt, map_location=device)
    cfg = ModelConfig(**state["cfg"])
    model = BawsalaGPT(cfg).to(device)
    model.load_state_dict(state["model"])
    model.train()

    rows = [json.loads(line)["messages"] for line in open(args.data, encoding="utf-8") if line.strip()]
    data = [encode_dialogue(tok, r, cfg.ctx) for r in rows]
    print(len(data), "حواراً للتدريب")

    opt = torch.optim.AdamW(model.parameters(), lr=args.lr, weight_decay=0.0)
    fp16 = device.type == "cuda" and not torch.cuda.is_bf16_supported()
    dtype = torch.float16 if fp16 else torch.bfloat16
    scaler = torch.cuda.amp.GradScaler(enabled=fp16)
    total_steps, k = args.epochs * math.ceil(len(data) / args.batch), 0

    for epoch in range(args.epochs):
        random.shuffle(data)
        for i in range(0, len(data), args.batch):
            chunk = data[i:i + args.batch]
            T = max(len(x) for x, _ in chunk)
            X = torch.tensor([x + [0] * (T - len(x)) for x, _ in chunk], device=device)
            Y = torch.tensor([y + [-100] * (T - len(y)) for _, y in chunk], device=device)
            for g in opt.param_groups:
                g["lr"] = args.lr * 0.5 * (1 + math.cos(math.pi * k / max(1, total_steps)))
            with torch.autocast(device_type=device.type, dtype=dtype, enabled=device.type == "cuda"):
                logits, _ = model(X[:, :-1])
                loss = torch.nn.functional.cross_entropy(
                    logits.reshape(-1, logits.size(-1)).float(), Y[:, 1:].reshape(-1), ignore_index=-100)
            scaler.scale(loss).backward()
            scaler.unscale_(opt)
            torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
            scaler.step(opt)
            scaler.update()
            opt.zero_grad(set_to_none=True)
            k += 1
            if k % 10 == 0:
                print(f"epoch {epoch + 1} step {k}/{total_steps} loss {loss.item():.3f}")

    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
    torch.save({"model": model.state_dict(), "cfg": cfg.__dict__}, args.out)
    print("تم الحفظ في", args.out)


if __name__ == "__main__":
    main()

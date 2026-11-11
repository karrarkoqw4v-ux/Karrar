"""المرحلة 3: التدريب الأولي (المعرفة العامة). يدعم CPU للاختبار وGPU (fp16 على T4، bf16 على A100) للتدريب الحقيقي.
قابل للاستئناف (--resume) وللحفظ قبل انتهاء جلسة Colab/Kaggle (--time-limit بالثواني).
    python train/03_pretrain.py --preset small --max-steps 20000 --resume
"""
import argparse
import json
import math
import os
import sys
import time

import numpy as np
import torch
from tokenizers import Tokenizer

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from core import PRESETS, BawsalaGPT

T_START = time.time()


def get_batch(data, batch_size, ctx, device):
    ix = np.random.randint(0, len(data) - ctx - 1, batch_size)
    x = torch.from_numpy(np.stack([data[i:i + ctx].astype(np.int64) for i in ix]))
    y = torch.from_numpy(np.stack([data[i + 1:i + 1 + ctx].astype(np.int64) for i in ix]))
    return x.to(device), y.to(device)


def lr_at(step, args):
    if step < args.warmup:
        return args.lr * (step + 1) / args.warmup
    if step >= args.max_steps:
        return args.lr * 0.1
    ratio = (step - args.warmup) / max(1, args.max_steps - args.warmup)
    return args.lr * 0.1 + 0.5 * (1 + math.cos(math.pi * ratio)) * (args.lr - args.lr * 0.1)


@torch.no_grad()
def evaluate(model, data, args, device, ctx):
    model.eval()
    losses = []
    for _ in range(args.eval_iters):
        x, y = get_batch(data, args.batch, ctx, device)
        with torch.autocast(device_type=device.type, dtype=args.dtype, enabled=args.amp):
            losses.append(model(x, y)[1].item())
    model.train()
    return sum(losses) / len(losses)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preset", default="tiny", choices=list(PRESETS))
    ap.add_argument("--data", default="out/bin")
    ap.add_argument("--tokenizer", default="out/tokenizer.json")
    ap.add_argument("--out", default="out")
    ap.add_argument("--batch", type=int, default=8)
    ap.add_argument("--grad-accum", type=int, default=4)
    ap.add_argument("--lr", type=float, default=6e-4)
    ap.add_argument("--warmup", type=int, default=200)
    ap.add_argument("--max-steps", type=int, default=2000)
    ap.add_argument("--eval-every", type=int, default=100)
    ap.add_argument("--eval-iters", type=int, default=20)
    ap.add_argument("--resume", action="store_true")
    ap.add_argument("--time-limit", type=int, default=0, help="ثوانٍ؛ يحفظ ويتوقف قبل انتهاء الجلسة")
    args = ap.parse_args()

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    args.amp = device.type == "cuda" and torch.cuda.is_bf16_supported()
    fp16 = device.type == "cuda" and not args.amp  # بطاقات T4 المجانية لا تدعم bf16
    args.dtype = torch.float16 if fp16 else torch.bfloat16
    args.amp = args.amp or fp16
    scaler = torch.cuda.amp.GradScaler(enabled=fp16)

    tok = Tokenizer.from_file(args.tokenizer)
    cfg = PRESETS[args.preset]
    cfg.vocab_size = tok.get_vocab_size()
    model = BawsalaGPT(cfg).to(device)
    print(f"preset={args.preset} params={model.n_params() / 1e6:.1f}M device={device} fp16={fp16}")

    train_data = np.memmap(os.path.join(args.data, "train.bin"), dtype=np.uint16, mode="r")
    val_data = np.memmap(os.path.join(args.data, "val.bin"), dtype=np.uint16, mode="r")
    opt = torch.optim.AdamW(model.parameters(), lr=args.lr, betas=(0.9, 0.95), weight_decay=0.1)

    ckpt_path = os.path.join(args.out, "ckpt.pt")
    step, history = 0, []
    if args.resume and os.path.exists(ckpt_path):
        state = torch.load(ckpt_path, map_location=device)
        model.load_state_dict(state["model"])
        opt.load_state_dict(state["opt"])
        step, history = state["step"], state["history"]
        print("استُؤنف من الخطوة", step)

    model.train()
    while step < args.max_steps:
        for g in opt.param_groups:
            g["lr"] = lr_at(step, args)
        loss_acc = 0.0
        for _ in range(args.grad_accum):
            x, y = get_batch(train_data, args.batch, cfg.ctx, device)
            with torch.autocast(device_type=device.type, dtype=args.dtype, enabled=args.amp):
                loss = model(x, y)[1] / args.grad_accum
            scaler.scale(loss).backward()
            loss_acc += loss.item()
        scaler.unscale_(opt)
        torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
        scaler.step(opt)
        scaler.update()
        opt.zero_grad(set_to_none=True)
        step += 1
        if step % 10 == 0:
            history.append({"step": step, "train_loss": round(loss_acc, 4)})
        if step % args.eval_every == 0 or step >= args.max_steps:
            vl = evaluate(model, val_data, args, device, cfg.ctx)
            history.append({"step": step, "val_loss": round(vl, 4), "ppl": round(math.exp(min(vl, 20)), 2)})
            print(f"step {step}: val_loss {vl:.3f}")
            torch.save({"model": model.state_dict(), "opt": opt.state_dict(), "step": step,
                        "history": history, "cfg": cfg.__dict__}, ckpt_path)
            json.dump({"preset": args.preset, "step": step, "history": history},
                      open(os.path.join(args.out, "metrics.json"), "w"), ensure_ascii=False)
        if args.time_limit and time.time() - T_START > args.time_limit:
            print("انتهى الوقت المحدد، أوقفتُ بأمان عند الخطوة", step)
            break
    print("تم")


if __name__ == "__main__":
    main()

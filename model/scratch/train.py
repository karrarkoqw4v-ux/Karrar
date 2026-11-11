"""Pre-training loop for the from-scratch GPT. Single GPU (or CPU for smoke tests). Resumable. Writes metrics.json for the Studio UI.
Example (Kaggle/Colab GPU):  python train.py --preset small --data data/bin --tokenizer out/tokenizer.json --max-steps 20000
"""
import argparse, json, math, os, time
import numpy as np
import torch
from tokenizers import Tokenizer
from model import GPT, PRESETS

def batch(data, bs, T, dev):
    ix = np.random.randint(0, len(data) - T - 1, bs)
    x = torch.from_numpy(np.stack([data[i:i + T].astype(np.int64) for i in ix]))
    y = torch.from_numpy(np.stack([data[i + 1:i + 1 + T].astype(np.int64) for i in ix]))
    return x.to(dev), y.to(dev)

def lr_at(it, a):
    if it < a.warmup:
        return a.lr * (it + 1) / a.warmup
    if it >= a.max_steps:
        return a.lr * 0.1
    r = (it - a.warmup) / max(1, a.max_steps - a.warmup)
    return a.lr * 0.1 + 0.5 * (1 + math.cos(math.pi * r)) * (a.lr - a.lr * 0.1)

@torch.no_grad()
def evaluate(model, data, a, dev, ctx):
    model.eval()
    ls = []
    for _ in range(a.eval_iters):
        x, y = batch(data, a.batch, ctx, dev)
        with torch.autocast(device_type=dev.type, dtype=a.dtype, enabled=a.amp):
            ls.append(model(x, y)[1].item())
    model.train()
    return sum(ls) / len(ls)

T_START = time.time()

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--preset", default="tiny", choices=list(PRESETS))
    ap.add_argument("--data", default="data/bin")
    ap.add_argument("--tokenizer", default="out/tokenizer.json")
    ap.add_argument("--out", default="out")
    ap.add_argument("--studio-dir", default="studio")
    ap.add_argument("--batch", type=int, default=8)
    ap.add_argument("--grad-accum", type=int, default=4)
    ap.add_argument("--lr", type=float, default=6e-4)
    ap.add_argument("--warmup", type=int, default=200)
    ap.add_argument("--max-steps", type=int, default=2000)
    ap.add_argument("--eval-every", type=int, default=100)
    ap.add_argument("--eval-iters", type=int, default=20)
    ap.add_argument("--sample-every", type=int, default=500)
    ap.add_argument("--resume", action="store_true")
    ap.add_argument("--time-limit", type=int, default=0, help="seconds; save a checkpoint and stop (for Colab/Kaggle session limits)")
    a = ap.parse_args()

    dev = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    a.amp = dev.type == "cuda" and torch.cuda.is_bf16_supported()
    a.fp16 = dev.type == "cuda" and not a.amp          # T4 (free Colab/Kaggle) has no bf16 -> fp16 + loss scaling
    a.dtype = torch.float16 if a.fp16 else torch.bfloat16
    a.amp = a.amp or a.fp16
    scaler = torch.cuda.amp.GradScaler(enabled=a.fp16)
    tok = Tokenizer.from_file(a.tokenizer)
    cfg = PRESETS[a.preset]
    cfg.vocab_size = tok.get_vocab_size()
    model = GPT(cfg).to(dev)
    print(f"preset={a.preset} params={model.n_params()/1e6:.1f}M device={dev} bf16={a.amp}")
    train = np.memmap(os.path.join(a.data, "train.bin"), dtype=np.uint16, mode="r")
    val = np.memmap(os.path.join(a.data, "val.bin"), dtype=np.uint16, mode="r")
    opt = torch.optim.AdamW(model.parameters(), lr=a.lr, betas=(0.9, 0.95), weight_decay=0.1)
    os.makedirs(a.out, exist_ok=True); os.makedirs(a.studio_dir, exist_ok=True)
    ck = os.path.join(a.out, "ckpt.pt")
    it, hist, samples = 0, [], []
    if a.resume and os.path.exists(ck):
        s = torch.load(ck, map_location=dev)
        model.load_state_dict(s["model"]); opt.load_state_dict(s["opt"]); it, hist, samples = s["it"], s["hist"], s.get("samples", [])
        print("resumed at step", it)
    tokens_per_step = a.batch * a.grad_accum * cfg.ctx
    t0 = time.time()
    model.train()
    while it < a.max_steps:
        for g in opt.param_groups:
            g["lr"] = lr_at(it, a)
        loss_acc = 0.0
        for _ in range(a.grad_accum):
            x, y = batch(train, a.batch, cfg.ctx, dev)
            with torch.autocast(device_type=dev.type, dtype=a.dtype, enabled=a.amp):
                loss = model(x, y)[1] / a.grad_accum
            scaler.scale(loss).backward()
            loss_acc += loss.item()
        scaler.unscale_(opt)
        torch.nn.utils.clip_grad_norm_(model.parameters(), 1.0)
        scaler.step(opt); scaler.update(); opt.zero_grad(set_to_none=True)
        if a.time_limit and time.time() - T_START > a.time_limit:   # Colab sessions die: save and stop cleanly, then rerun with --resume
            a.max_steps = it
        it += 1
        if it % 10 == 0 or it == 1:
            el = time.time() - t0
            hist.append({"step": it, "train_loss": round(loss_acc, 4), "lr": round(lr_at(it, a), 6),
                         "tokens": it * tokens_per_step, "tok_per_s": int(10 * tokens_per_step / max(el, 1e-9)) if it > 1 else 0})
            t0 = time.time()
        if it % a.eval_every == 0 or it >= a.max_steps:
            vl = evaluate(model, val, a, dev, cfg.ctx)
            hist.append({"step": it, "val_loss": round(vl, 4), "ppl": round(math.exp(min(vl, 20)), 2)})
            print(f"step {it}: val_loss {vl:.3f} ppl {math.exp(min(vl, 20)):.1f}")
        if it % a.sample_every == 0 or it >= a.max_steps:
            bos = tok.token_to_id("<|bos|>")
            out = model.generate(torch.tensor([[bos]], device=dev), max_new=80, eos=tok.token_to_id("<|eos|>"))
            samples.append({"step": it, "text": tok.decode(out[0].tolist(), skip_special_tokens=True)})
            torch.save({"model": model.state_dict(), "opt": opt.state_dict(), "it": it, "hist": hist, "samples": samples, "cfg": cfg.__dict__}, ck)
        if it % 10 == 0 or it == a.max_steps:
            meta = {"preset": a.preset, "params_m": round(model.n_params() / 1e6, 1), "device": str(dev), "max_steps": a.max_steps,
                    "tokens_seen": it * tokens_per_step, "history": hist, "samples": samples}
            for d in (a.out, a.studio_dir):
                json.dump(meta, open(os.path.join(d, "metrics.json"), "w"), ensure_ascii=False)
    print("done")

if __name__ == "__main__":
    main()

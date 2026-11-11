"""المرحلة 2: تنظيف النصوص وتوكنتها وكتابتها كملفات uint16 (train.bin/val.bin) حتى هدف توكنات معيّن.
نصوص عامة: فقرات مفصولة بسطر فارغ. كود (--code): مستندات كاملة مفصولة بـ<|doc|> بدون تقطيع سطري.
    python train/02_prep_data.py --input data/raw --target-tokens 600000000
"""
import argparse
import glob
import hashlib
import json
import os
import re

import numpy as np
from tokenizers import Tokenizer

TATWEEL = re.compile("ـ")
SPACES = re.compile(r"[ \t]+")
AR = re.compile(r"[؀-ۿ]")


def clean_line(line: str) -> str:
    return SPACES.sub(" ", TATWEEL.sub("", line)).strip()


def good_line(line: str, min_len: int, min_ar: float) -> bool:
    if len(line) < min_len:
        return False
    letters = sum(c.isalpha() for c in line) or 1
    return len(AR.findall(line)) / letters >= min_ar


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True)
    ap.add_argument("--tokenizer", default="out/tokenizer.json")
    ap.add_argument("--out", default="out/bin")
    ap.add_argument("--target-tokens", type=int, default=600_000_000)
    ap.add_argument("--min-len", type=int, default=20)
    ap.add_argument("--min-arabic", type=float, default=0.5)
    ap.add_argument("--val-every", type=int, default=200)
    ap.add_argument("--code", action="store_true", help="وضع الكود: مستندات كاملة بلا فلترة سطرية")
    ap.add_argument("--append", action="store_true")
    a = ap.parse_args()

    tok = Tokenizer.from_file(a.tokenizer)
    eos = tok.token_to_id("<|eos|>")
    os.makedirs(a.out, exist_ok=True)
    mode = "ab" if a.append else "wb"
    f_train = open(os.path.join(a.out, "train.bin"), mode)
    f_val = open(os.path.join(a.out, "val.bin"), mode)
    seen, n_train, n_val, docs, dropped, dup = set(), 0, 0, 0, 0, 0

    for path in sorted(glob.glob(a.input.rstrip("/") + "/**/*.txt", recursive=True)):
        raw = open(path, encoding="utf-8", errors="ignore").read()
        blocks = raw.split("\n<|doc|>\n") if a.code else re.split(r"\n\s*\n", raw)
        for block in blocks:
            if a.code:
                h = hashlib.blake2b(block.encode(), digest_size=8).digest()
                if len(block) < 30 or h in seen:
                    dup += 1
                    continue
                seen.add(h)
                ids = tok.encode(block).ids + [eos]
            else:
                lines = []
                for ln in block.splitlines():
                    ln = clean_line(ln)
                    if not good_line(ln, a.min_len, a.min_arabic):
                        dropped += 1
                        continue
                    h = hashlib.blake2b(ln.encode(), digest_size=8).digest()
                    if h in seen:
                        dup += 1
                        continue
                    seen.add(h)
                    lines.append(ln)
                if not lines:
                    continue
                ids = tok.encode("\n".join(lines)).ids + [eos]
            arr = np.array(ids, dtype=np.uint16)
            docs += 1
            if docs % a.val_every == 0:
                arr.tofile(f_val)
                n_val += len(arr)
            else:
                arr.tofile(f_train)
                n_train += len(arr)
            if n_train + n_val >= a.target_tokens:
                break
        if n_train + n_val >= a.target_tokens:
            break

    f_train.close()
    f_val.close()
    card = {"train_tokens": n_train, "val_tokens": n_val, "documents": docs,
            "dropped_lines": dropped, "duplicate_lines": dup, "target": a.target_tokens, "code_mode": a.code}
    json.dump(card, open(os.path.join(a.out, "data_card.json"), "w"), indent=2)
    print(card)


if __name__ == "__main__":
    main()

"""Clean text, de-duplicate, tokenize and write uint16 shards (train.bin / val.bin) until a token target is reached (default: 2 billion).
Only feed it text you are allowed to use (see data/sources.md)."""
import argparse, glob, hashlib, json, os, re
import numpy as np
from tokenizers import Tokenizer

TATWEEL = re.compile("ـ")
SPACES = re.compile(r"[ \t ]+")
AR = re.compile(r"[؀-ۿ]")

def clean(line):
    line = TATWEEL.sub("", line)
    line = SPACES.sub(" ", line).strip()
    return line

def good(line, min_len, min_ar):
    if len(line) < min_len:
        return False
    letters = sum(c.isalpha() for c in line) or 1
    return len(AR.findall(line)) / letters >= min_ar

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True)
    ap.add_argument("--tokenizer", default="out/tokenizer.json")
    ap.add_argument("--out", default="data/bin")
    ap.add_argument("--target-tokens", type=int, default=2_000_000_000)
    ap.add_argument("--min-len", type=int, default=20)
    ap.add_argument("--min-arabic", type=float, default=0.5, help="share of Arabic letters; lower it to keep code/English")
    ap.add_argument("--code", action="store_true", help="source code: keep each document whole (split on <|doc|>), exact-dedupe documents, no line filtering")
    ap.add_argument("--append", action="store_true", help="append to existing train.bin/val.bin (mix text + code)")
    ap.add_argument("--val-every", type=int, default=200, help="every Nth document goes to validation")
    a = ap.parse_args()
    tok = Tokenizer.from_file(a.tokenizer)
    eos = tok.token_to_id("<|eos|>")
    os.makedirs(a.out, exist_ok=True)
    ftr, fva = open(os.path.join(a.out, "train.bin"), "ab" if a.append else "wb"), open(os.path.join(a.out, "val.bin"), "ab" if a.append else "wb")
    seen, n_tr, n_va, docs, dropped, dup = set(), 0, 0, 0, 0, 0
    for path in sorted(glob.glob(a.input.rstrip("/") + "/**/*.txt", recursive=True)):
        with open(path, encoding="utf-8", errors="ignore") as f:
            for block in (f.read().split("\n<|doc|>\n") if a.code else re.split(r"\n\s*\n", f.read())):
                if a.code:
                    h = hashlib.blake2b(block.encode(), digest_size=8).digest()
                    if len(block) < 30 or h in seen:
                        dup += 1; continue
                    seen.add(h)
                    arr = np.array(tok.encode(block).ids + [eos], dtype=np.uint16); docs += 1
                    if docs % a.val_every == 0: arr.tofile(fva); n_va += len(arr)
                    else: arr.tofile(ftr); n_tr += len(arr)
                    if n_tr + n_va >= a.target_tokens: break
                    continue
                lines = []
                for ln in block.splitlines():
                    ln = clean(ln)
                    if not good(ln, a.min_len, a.min_arabic):
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
                    arr.tofile(fva); n_va += len(arr)
                else:
                    arr.tofile(ftr); n_tr += len(arr)
                if n_tr + n_va >= a.target_tokens:
                    break
        if n_tr + n_va >= a.target_tokens:
            break
    ftr.close(); fva.close()
    card = {"train_tokens": n_tr, "val_tokens": n_va, "documents": docs, "dropped_lines": dropped, "duplicate_lines": dup, "target": a.target_tokens}
    json.dump(card, open(os.path.join(a.out, "data_card.json"), "w"), indent=2)
    print(card)

if __name__ == "__main__":
    main()

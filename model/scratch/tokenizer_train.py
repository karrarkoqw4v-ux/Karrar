"""Train a byte-level BPE tokenizer on your own text (Arabic-friendly: NFKC, no information loss thanks to byte-level)."""
import argparse, glob
from tokenizers import Tokenizer, models, trainers, pre_tokenizers, decoders, normalizers

SPECIAL = ["<|pad|>", "<|bos|>", "<|eos|>", "<|user|>", "<|assistant|>"]

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True, help="folder with .txt files")
    ap.add_argument("--vocab", type=int, default=32000)
    ap.add_argument("--out", default="out/tokenizer.json")
    a = ap.parse_args()
    files = glob.glob(a.input.rstrip("/") + "/**/*.txt", recursive=True)
    if not files:
        raise SystemExit("no .txt files found in " + a.input)
    tok = Tokenizer(models.BPE())
    tok.normalizer = normalizers.NFKC()
    tok.pre_tokenizer = pre_tokenizers.ByteLevel(add_prefix_space=False)
    tok.decoder = decoders.ByteLevel()
    tr = trainers.BpeTrainer(vocab_size=a.vocab, special_tokens=SPECIAL, initial_alphabet=pre_tokenizers.ByteLevel.alphabet(), show_progress=True)
    tok.train(files, tr)
    import os
    os.makedirs(os.path.dirname(a.out), exist_ok=True)
    tok.save(a.out)
    print("saved", a.out, "vocab", tok.get_vocab_size())

if __name__ == "__main__":
    main()

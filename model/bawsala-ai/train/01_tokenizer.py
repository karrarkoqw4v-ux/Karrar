"""المرحلة 1: تدريب توكنايزر BPE على كل النصوص المجموعة (عربي + 26 لغة برمجة).
    python train/01_tokenizer.py --input data/raw --vocab 32000
"""
import argparse
import glob
import os

from tokenizers import Tokenizer, decoders, models, normalizers, pre_tokenizers, trainers

SPECIAL = ["<|pad|>", "<|bos|>", "<|eos|>", "<|user|>", "<|assistant|>", "<|doc|>"]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", nargs="+", required=True, help="مجلد أو أكثر فيها ملفات .txt")
    ap.add_argument("--vocab", type=int, default=32000)
    ap.add_argument("--out", default="out/tokenizer.json")
    a = ap.parse_args()

    files = []
    for d in a.input:
        files += glob.glob(d.rstrip("/") + "/**/*.txt", recursive=True)
    if not files:
        raise SystemExit("لا توجد ملفات .txt في المسارات المعطاة")

    tok = Tokenizer(models.BPE())
    tok.normalizer = normalizers.NFKC()
    tok.pre_tokenizer = pre_tokenizers.ByteLevel(add_prefix_space=False)
    tok.decoder = decoders.ByteLevel()
    trainer = trainers.BpeTrainer(vocab_size=a.vocab, special_tokens=SPECIAL,
                                   initial_alphabet=pre_tokenizers.ByteLevel.alphabet(), show_progress=True)
    tok.train(files, trainer)
    os.makedirs(os.path.dirname(a.out), exist_ok=True)
    tok.save(a.out)
    print(f"تم الحفظ في {a.out}، حجم المفردات {tok.get_vocab_size()}، من {len(files)} ملفاً")


if __name__ == "__main__":
    main()

"""أداة سطر أوامر بسيطة لتوليد نص من نقطة تفتيش مدرَّبة (بدون خادم)، للاختبار السريع.
    python train/09_cli_generate.py --prompt "بوصلة"
"""
import argparse
import os
import sys

import torch

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from core import ModelConfig, BawsalaGPT  # noqa: E402
from tokenizers import Tokenizer  # noqa: E402


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ckpt", default="out/ckpt.pt")
    ap.add_argument("--tokenizer", default="out/tokenizer.json")
    ap.add_argument("--prompt", default="")
    ap.add_argument("--max-new", type=int, default=120)
    ap.add_argument("--temperature", type=float, default=0.8)
    args = ap.parse_args()

    tok = Tokenizer.from_file(args.tokenizer)
    state = torch.load(args.ckpt, map_location="cpu")
    model = BawsalaGPT(ModelConfig(**state["cfg"]))
    model.load_state_dict(state["model"])
    model.eval()

    ids = [tok.token_to_id("<|bos|>")] + tok.encode(args.prompt).ids
    out = model.generate(torch.tensor([ids]), max_new=args.max_new, temperature=args.temperature,
                          eos=tok.token_to_id("<|eos|>"))
    print(tok.decode(out[0].tolist(), skip_special_tokens=True))


if __name__ == "__main__":
    main()

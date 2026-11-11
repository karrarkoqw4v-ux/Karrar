"""Talk to your fine-tuned model in the terminal:  python chat.py"""
import argparse, torch
from tokenizers import Tokenizer
from model import GPT, Config
ap = argparse.ArgumentParser(); ap.add_argument("--ckpt", default="out/chat.pt"); ap.add_argument("--tokenizer", default="out/tokenizer.json"); a = ap.parse_args()
tok = Tokenizer.from_file(a.tokenizer); s = torch.load(a.ckpt, map_location="cpu"); m = GPT(Config(**s["cfg"])); m.load_state_dict(s["model"]); m.eval()
U, A, E, B = (tok.token_to_id(x) for x in ("<|user|>", "<|assistant|>", "<|eos|>", "<|bos|>")); hist = [B]
while True:
    q = input("أنت: ").strip()
    if not q: break
    hist += [U] + tok.encode(q).ids + [A]
    out = m.generate(torch.tensor([hist]), max_new=200, temperature=0.7, eos=E)[0].tolist()
    ans = out[len(hist):]; hist = out
    print("النموذج:", tok.decode([t for t in ans if t != E]))

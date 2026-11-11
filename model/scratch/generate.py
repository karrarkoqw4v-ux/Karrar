"""Generate text from a trained checkpoint:  python generate.py --prompt "بوصلة" """
import argparse, torch
from tokenizers import Tokenizer
from model import GPT, Config

ap = argparse.ArgumentParser()
ap.add_argument("--ckpt", default="out/ckpt.pt")
ap.add_argument("--tokenizer", default="out/tokenizer.json")
ap.add_argument("--prompt", default="")
ap.add_argument("--max-new", type=int, default=120)
ap.add_argument("--temperature", type=float, default=0.8)
a = ap.parse_args()
tok = Tokenizer.from_file(a.tokenizer)
s = torch.load(a.ckpt, map_location="cpu")
m = GPT(Config(**s["cfg"]))
m.load_state_dict(s["model"]); m.eval()
ids = [tok.token_to_id("<|bos|>")] + tok.encode(a.prompt).ids
out = m.generate(torch.tensor([ids]), max_new=a.max_new, temperature=a.temperature, eos=tok.token_to_id("<|eos|>"))
print(tok.decode(out[0].tolist(), skip_special_tokens=True))

"""يحمّل نموذجاً مدرَّباً (chat.pt) ويبني/يفكّك الحوار بصيغة الرموز الخاصة. يُستخدم من api_server.py وchat_cli.py."""
import sys
import os

import torch
import torch.nn.functional as F
from tokenizers import Tokenizer

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from core import ModelConfig, BawsalaGPT


class Runtime:
    def __init__(self, ckpt: str, tokenizer: str):
        self.device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
        self.tok = Tokenizer.from_file(tokenizer)
        state = torch.load(ckpt, map_location=self.device)
        self.cfg = ModelConfig(**state["cfg"])
        self.model = BawsalaGPT(self.cfg).to(self.device)
        self.model.load_state_dict(state["model"])
        self.model.eval()
        self.user_id, self.asst_id, self.eos_id, self.bos_id = (
            self.tok.token_to_id(t) for t in ("<|user|>", "<|assistant|>", "<|eos|>", "<|bos|>"))

    def build_prompt(self, messages: list) -> list:
        ids = [self.bos_id]
        for m in messages:
            tokens = self.tok.encode(str(m.get("content", ""))).ids
            if m.get("role") == "assistant":
                ids += [self.asst_id] + tokens + [self.eos_id]
            else:
                ids += [self.user_id] + tokens
        return ids + [self.asst_id]

    @torch.no_grad()
    def stream(self, ids: list, max_new: int, temperature: float, top_k: int = 50):
        out, prev = [], ""
        for _ in range(max_new):
            logits = self.model(torch.tensor([ids[-self.cfg.ctx:]], device=self.device))[0][:, -1] / max(temperature, 1e-5)
            if top_k:
                v, _ = torch.topk(logits, min(top_k, logits.size(-1)))
                logits[logits < v[:, [-1]]] = -float("inf")
            nxt = int(torch.multinomial(F.softmax(logits, -1), 1))
            if nxt == self.eos_id:
                break
            ids.append(nxt)
            out.append(nxt)
            text = self.tok.decode(out)
            if not text.endswith("�"):  # انتظر اكتمال حرف متعدد البايت
                piece, prev = text[len(prev):], text
                if piece:
                    yield piece

    def params_millions(self) -> float:
        return round(self.model.n_params() / 1e6, 1)

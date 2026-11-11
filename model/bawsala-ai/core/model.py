"""النموذج الكامل: تجميع الطبقات، التدريب (forward مع خسارة)، والتوليد (generate)."""
import math

import torch
import torch.nn as nn
import torch.nn.functional as F

from .block import Block
from .config import ModelConfig
from .norm import RMSNorm
from .rope import rope_cache


class BawsalaGPT(nn.Module):
    def __init__(self, c: ModelConfig):
        super().__init__()
        self.c = c
        self.emb = nn.Embedding(c.vocab_size, c.d_model)
        self.blocks = nn.ModuleList(Block(c) for _ in range(c.n_layer))
        self.norm = RMSNorm(c.d_model)
        self.head = nn.Linear(c.d_model, c.vocab_size, bias=False)
        self.head.weight = self.emb.weight  # ربط أوزان الإدخال والإخراج (tied embeddings)
        self.apply(self._init_weights)
        for name, p in self.named_parameters():
            if name.endswith("out.weight") or name.endswith("down.weight"):
                nn.init.normal_(p, mean=0.0, std=0.02 / math.sqrt(2 * c.n_layer))
        self._rope = None

    def _init_weights(self, m):
        if isinstance(m, (nn.Linear, nn.Embedding)):
            nn.init.normal_(m.weight, mean=0.0, std=0.02)

    def n_params(self):
        return sum(p.numel() for p in self.parameters())

    def forward(self, idx, targets=None):
        B, T = idx.shape
        if self._rope is None or self._rope[0].device != idx.device or self._rope[0].size(0) < self.c.ctx:
            self._rope = rope_cache(self.c.ctx, self.c.d_model // self.c.n_head, idx.device)
        x = self.emb(idx)
        for b in self.blocks:
            x = b(x, *self._rope)
        logits = self.head(self.norm(x))
        loss = None
        if targets is not None:
            loss = F.cross_entropy(logits.view(-1, logits.size(-1)), targets.view(-1), ignore_index=-100)
        return logits, loss

    @torch.no_grad()
    def generate(self, idx, max_new=100, temperature=0.8, top_k=50, eos=None):
        for _ in range(max_new):
            logits, _ = self(idx[:, -self.c.ctx:])
            logits = logits[:, -1] / max(temperature, 1e-5)
            if top_k:
                v, _ = torch.topk(logits, min(top_k, logits.size(-1)))
                logits[logits < v[:, [-1]]] = -float("inf")
            nxt = torch.multinomial(F.softmax(logits, dim=-1), 1)
            idx = torch.cat([idx, nxt], dim=1)
            if eos is not None and int(nxt) == eos:
                break
        return idx

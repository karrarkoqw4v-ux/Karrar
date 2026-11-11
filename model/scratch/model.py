"""A small GPT-style language model written from scratch (decoder-only Transformer).
RMSNorm + rotary position embeddings + SwiGLU MLP + causal attention. No external model code."""
import math
from dataclasses import dataclass
import torch
import torch.nn as nn
import torch.nn.functional as F

@dataclass
class Config:
    vocab_size: int = 32000
    n_layer: int = 12
    n_head: int = 12
    d_model: int = 768
    d_ff: int = 2048
    ctx: int = 1024

PRESETS = {
    "tiny":  Config(vocab_size=2048,  n_layer=4,  n_head=4,  d_model=128,  d_ff=352,  ctx=256),    # smoke tests on CPU
    "small": Config(vocab_size=32000, n_layer=12, n_head=12, d_model=768,  d_ff=2048, ctx=1024),   # ~110M, fits a free Kaggle T4/P100
    "b1":    Config(vocab_size=32000, n_layer=22, n_head=16, d_model=2048, d_ff=5632, ctx=2048),   # ~1.2B, needs a 40-80GB GPU
}

class RMSNorm(nn.Module):
    def __init__(self, d, eps=1e-6):
        super().__init__()
        self.w = nn.Parameter(torch.ones(d))
        self.eps = eps
    def forward(self, x):
        return x * torch.rsqrt(x.pow(2).mean(-1, keepdim=True) + self.eps) * self.w

def rope_cache(ctx, hd, device):
    inv = 1.0 / (10000 ** (torch.arange(0, hd, 2, device=device).float() / hd))
    t = torch.arange(ctx, device=device).float()
    f = torch.outer(t, inv)
    return torch.cos(f), torch.sin(f)

def apply_rope(x, cos, sin):
    # x: (B, H, T, hd)
    T = x.size(2)
    x1, x2 = x[..., ::2], x[..., 1::2]
    c, s = cos[:T][None, None], sin[:T][None, None]
    out = torch.stack((x1 * c - x2 * s, x1 * s + x2 * c), dim=-1)
    return out.flatten(-2)

class Attn(nn.Module):
    def __init__(self, c: Config):
        super().__init__()
        self.h, self.hd = c.n_head, c.d_model // c.n_head
        self.qkv = nn.Linear(c.d_model, 3 * c.d_model, bias=False)
        self.o = nn.Linear(c.d_model, c.d_model, bias=False)
    def forward(self, x, cos, sin):
        B, T, D = x.shape
        q, k, v = self.qkv(x).view(B, T, 3, self.h, self.hd).permute(2, 0, 3, 1, 4)
        q, k = apply_rope(q, cos, sin), apply_rope(k, cos, sin)
        y = F.scaled_dot_product_attention(q, k, v, is_causal=True)
        return self.o(y.transpose(1, 2).reshape(B, T, D))

class MLP(nn.Module):
    def __init__(self, c: Config):
        super().__init__()
        self.g = nn.Linear(c.d_model, c.d_ff, bias=False)
        self.u = nn.Linear(c.d_model, c.d_ff, bias=False)
        self.d = nn.Linear(c.d_ff, c.d_model, bias=False)
    def forward(self, x):
        return self.d(F.silu(self.g(x)) * self.u(x))

class Block(nn.Module):
    def __init__(self, c: Config):
        super().__init__()
        self.n1, self.a, self.n2, self.m = RMSNorm(c.d_model), Attn(c), RMSNorm(c.d_model), MLP(c)
    def forward(self, x, cos, sin):
        x = x + self.a(self.n1(x), cos, sin)
        return x + self.m(self.n2(x))

class GPT(nn.Module):
    def __init__(self, c: Config):
        super().__init__()
        self.c = c
        self.emb = nn.Embedding(c.vocab_size, c.d_model)
        self.blocks = nn.ModuleList(Block(c) for _ in range(c.n_layer))
        self.norm = RMSNorm(c.d_model)
        self.head = nn.Linear(c.d_model, c.vocab_size, bias=False)
        self.head.weight = self.emb.weight  # tied embeddings
        self.apply(self._init)
        for n, p in self.named_parameters():
            if n.endswith("o.weight") or n.endswith("d.weight"):
                nn.init.normal_(p, mean=0.0, std=0.02 / math.sqrt(2 * c.n_layer))
        self._rope = None
    def _init(self, m):
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
        loss = None if targets is None else F.cross_entropy(logits.view(-1, logits.size(-1)), targets.view(-1), ignore_index=-100)
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

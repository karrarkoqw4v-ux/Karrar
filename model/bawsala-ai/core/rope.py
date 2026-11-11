"""Rotary Position Embeddings: يعطي النموذج إحساساً بترتيب الكلمات بدون جدول مواضع ثابت."""
import torch


def rope_cache(ctx: int, head_dim: int, device):
    inv = 1.0 / (10000 ** (torch.arange(0, head_dim, 2, device=device).float() / head_dim))
    t = torch.arange(ctx, device=device).float()
    freqs = torch.outer(t, inv)
    return torch.cos(freqs), torch.sin(freqs)


def apply_rope(x, cos, sin):
    # x: (batch, heads, seq, head_dim)
    seq_len = x.size(2)
    x1, x2 = x[..., ::2], x[..., 1::2]
    c, s = cos[:seq_len][None, None], sin[:seq_len][None, None]
    out = torch.stack((x1 * c - x2 * s, x1 * s + x2 * c), dim=-1)
    return out.flatten(-2)

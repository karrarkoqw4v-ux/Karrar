"""طبقة الانتباه الذاتي (Self-Attention) مع RoPE، سببية (كل كلمة ترى ما قبلها فقط)."""
import torch.nn as nn
import torch.nn.functional as F

from .config import ModelConfig
from .rope import apply_rope


class CausalSelfAttention(nn.Module):
    def __init__(self, c: ModelConfig):
        super().__init__()
        assert c.d_model % c.n_head == 0, "d_model يجب أن يقبل القسمة على n_head"
        self.n_head, self.head_dim = c.n_head, c.d_model // c.n_head
        self.qkv = nn.Linear(c.d_model, 3 * c.d_model, bias=False)
        self.out = nn.Linear(c.d_model, c.d_model, bias=False)

    def forward(self, x, cos, sin):
        B, T, D = x.shape
        q, k, v = self.qkv(x).view(B, T, 3, self.n_head, self.head_dim).permute(2, 0, 3, 1, 4)
        q, k = apply_rope(q, cos, sin), apply_rope(k, cos, sin)
        y = F.scaled_dot_product_attention(q, k, v, is_causal=True)
        return self.out(y.transpose(1, 2).reshape(B, T, D))

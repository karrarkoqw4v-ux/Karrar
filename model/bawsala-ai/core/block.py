"""طبقة Transformer واحدة: تطبيع + انتباه + تطبيع + MLP، مع اتصالات متبقية (residual)."""
import torch.nn as nn

from .attention import CausalSelfAttention
from .config import ModelConfig
from .mlp import SwiGLU
from .norm import RMSNorm


class Block(nn.Module):
    def __init__(self, c: ModelConfig):
        super().__init__()
        self.norm1 = RMSNorm(c.d_model)
        self.attn = CausalSelfAttention(c)
        self.norm2 = RMSNorm(c.d_model)
        self.mlp = SwiGLU(c)

    def forward(self, x, cos, sin):
        x = x + self.attn(self.norm1(x), cos, sin)
        return x + self.mlp(self.norm2(x))

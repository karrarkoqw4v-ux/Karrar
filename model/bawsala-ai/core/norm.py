"""RMSNorm: تطبيع الطبقات (أخف وأسرع من LayerNorm، نفس الفكرة المستخدمة في LLaMA)."""
import torch
import torch.nn as nn


class RMSNorm(nn.Module):
    def __init__(self, d: int, eps: float = 1e-6):
        super().__init__()
        self.w = nn.Parameter(torch.ones(d))
        self.eps = eps

    def forward(self, x):
        return x * torch.rsqrt(x.pow(2).mean(-1, keepdim=True) + self.eps) * self.w

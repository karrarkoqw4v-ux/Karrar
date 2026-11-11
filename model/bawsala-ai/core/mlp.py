"""SwiGLU MLP: الطبقة التي تعالج كل كلمة بمفردها بعد الانتباه (أثبتت كفاءة أعلى من ReLU/GELU العادية)."""
import torch.nn as nn
import torch.nn.functional as F

from .config import ModelConfig


class SwiGLU(nn.Module):
    def __init__(self, c: ModelConfig):
        super().__init__()
        self.gate = nn.Linear(c.d_model, c.d_ff, bias=False)
        self.up = nn.Linear(c.d_model, c.d_ff, bias=False)
        self.down = nn.Linear(c.d_ff, c.d_model, bias=False)

    def forward(self, x):
        return self.down(F.silu(self.gate(x)) * self.up(x))

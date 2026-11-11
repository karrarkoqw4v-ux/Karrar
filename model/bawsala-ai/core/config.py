"""إعدادات بنية النموذج: الأحجام المتاحة (tiny/small/b1) وخصائص كل طبقة."""
from dataclasses import dataclass


@dataclass
class ModelConfig:
    vocab_size: int = 32000
    n_layer: int = 12
    n_head: int = 12
    d_model: int = 768
    d_ff: int = 2048
    ctx: int = 1024
    dropout: float = 0.0


PRESETS = {
    "tiny": ModelConfig(vocab_size=2048, n_layer=4, n_head=4, d_model=128, d_ff=352, ctx=256),
    "small": ModelConfig(vocab_size=32000, n_layer=12, n_head=12, d_model=768, d_ff=2048, ctx=1024),
    "b1": ModelConfig(vocab_size=32000, n_layer=22, n_head=16, d_model=2048, d_ff=5632, ctx=2048),
}

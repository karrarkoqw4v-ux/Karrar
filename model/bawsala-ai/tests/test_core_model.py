"""يتأكد أن النموذج يُبنى، يمرّر بيانات (forward)، يحسب خسارة، ويولّد نصاً، بلا أخطاء."""
import os
import sys

import torch

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from core import PRESETS, BawsalaGPT


def test_forward_and_generate():
    cfg = PRESETS["tiny"]
    model = BawsalaGPT(cfg)
    x = torch.randint(0, cfg.vocab_size, (2, 16))
    y = torch.randint(0, cfg.vocab_size, (2, 16))
    logits, loss = model(x, y)
    assert logits.shape == (2, 16, cfg.vocab_size)
    assert loss.item() > 0
    out = model.generate(torch.randint(0, cfg.vocab_size, (1, 1)), max_new=5)
    assert out.shape[1] == 6
    print("نجح: forward + generate،", model.n_params(), "معامل")


if __name__ == "__main__":
    test_forward_and_generate()

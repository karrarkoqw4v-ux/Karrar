"""الفلاتر الجديدة: تعمل وتغيّر الصورة وتحافظ على الأبعاد"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import photolib  # noqa: E402

img = (np.random.RandomState(3).rand(240, 320, 3) * 200 + 25).astype(np.uint8)
for tid in ("f_clarendon", "f_rose_film", "f_mint_cool", "f_golden_matte", "f_duo_teal_orange", "f_duo_purple", "f_duo_green_gold"):
    out, a = photolib.TOOLS[tid].fn(img, None)
    assert out.shape == img.shape and out.dtype == np.uint8, tid
    assert np.abs(out.astype(int) - img.astype(int)).mean() > 2, tid
print("OK photolib filters")

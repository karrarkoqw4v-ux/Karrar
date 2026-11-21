"""أدوات الإطارات والقياسات الجديدة: 3:4 و2:3 والدائرة"""
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import photolib  # noqa: E402

img = (np.random.RandomState(1).rand(480, 640, 3) * 200 + 20).astype(np.uint8)
for tid, ratio in (("l_crop_34", 3 / 4), ("l_crop_23", 2 / 3)):
    out, a = photolib.TOOLS[tid].fn(img, None)
    h, w = out.shape[:2]
    assert abs(w / h - ratio) < 0.01, (tid, w, h)
out, a = photolib.TOOLS["l_circle"].fn(img, None)
h, w = out.shape[:2]
assert h == w == 480
assert (out[2, 2] == 255).all() and (out[-3, -3] == 255).all()  # الزوايا بيضاء
assert not (out[h // 2, w // 2] == 255).all()  # الوسط صورة
print("OK photolib layout")

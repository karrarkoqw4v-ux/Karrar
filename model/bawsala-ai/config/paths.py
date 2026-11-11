"""مسارات المشروع المركزية: كل ملف آخر يستورد من هنا بدل كتابة مسارات متفرقة."""
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATA = os.path.join(ROOT, "data")
OUT = os.path.join(ROOT, "out")
BIN = os.path.join(OUT, "bin")
CKPT = os.path.join(OUT, "ckpt.pt")
TOKENIZER = os.path.join(OUT, "tokenizer.json")
SKILLS = os.path.join(ROOT, "skills")

for d in (OUT, BIN):
    os.makedirs(d, exist_ok=True)

"""المرحلة 4أ: يبني ملف حوارات chat.jsonl من data/qa (الأساس) + data/dialects (أسلوب كل لهجة) + ملفاتك الخاصة.
ضع حواراتك في data/qa/mine/*.txt بصيغة: س: ... / ج: ... (سطر فارغ بين كل حوار).
    python train/04_build_sft.py
"""
import glob
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from data import qa  # noqa: E402
import data.dialects as dialects_pkg  # noqa: E402


def row(user, assistant):
    return json.dumps({"messages": [{"role": "user", "content": user}, {"role": "assistant", "content": assistant}]},
                       ensure_ascii=False)


def main():
    rows = []
    for pairs in qa.TOPICS.values():
        for q, a in pairs:
            rows.append(row(q, a))
    for name in getattr(dialects_pkg, "ALL", []):
        mod = __import__(f"data.dialects.{name}", fromlist=["x"])
        rows.append(row("سلّم علي", mod.SAMPLE_GREETING))
    for path in glob.glob("data/qa/mine/*.txt"):
        for block in re.split(r"\n\s*\n", open(path, encoding="utf-8").read()):
            m = re.match(r"\s*س:\s*(.+?)\n\s*ج:\s*(.+)", block, re.S)
            if m:
                rows.append(row(m[1].strip(), m[2].strip()))
    if os.path.exists("data/qa/self_learned.jsonl"):
        rows += [l.strip() for l in open("data/qa/self_learned.jsonl", encoding="utf-8") if l.strip()]
    os.makedirs("out", exist_ok=True)
    open("out/chat.jsonl", "w", encoding="utf-8").write("\n".join(rows) + "\n")
    print(len(rows), "حواراً -> out/chat.jsonl")


if __name__ == "__main__":
    main()

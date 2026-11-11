"""Build data/sft/chat.jsonl from: data/sft/seed.jsonl (30 hand-written examples) + your own files in data/sft/mine/*.txt written like:
س: سؤالك
ج: الجواب الذي تريد أن يتعلمه النموذج
(سطر فارغ بين كل حوار). Hundreds+ of good examples are needed for a useful chatbot: the more you write/curate, the better it gets."""
import glob, json, re
rows = [l for l in open("data/sft/seed.jsonl", encoding="utf-8") if l.strip()]
for f in glob.glob("data/sft/mine/*.txt"):
    for blk in re.split(r"\n\s*\n", open(f, encoding="utf-8").read()):
        m = re.match(r"\s*س:\s*(.+?)\n\s*ج:\s*(.+)", blk, re.S)
        if m: rows.append(json.dumps({"messages": [{"role": "user", "content": m[1].strip()}, {"role": "assistant", "content": m[2].strip()}]}, ensure_ascii=False) + "\n")
open("data/sft/chat.jsonl", "w", encoding="utf-8").writelines(rows); print(len(rows), "dialogues ->", "data/sft/chat.jsonl")

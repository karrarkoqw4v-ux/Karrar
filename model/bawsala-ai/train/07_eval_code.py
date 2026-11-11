"""المرحلة 5: تعلّم بالاختبار. يطلب من النموذج المخدوم (serve/api_server.py) كتابة كود،
يشغّله فعلاً عبر skills/programming/<لغة>.py، ويحفظ الحلول الناجحة فقط لإعادة التدريب عليها لاحقاً.
    python train/07_eval_code.py --url http://127.0.0.1:8000/v1 --samples 4
"""
import argparse
import importlib
import json
import re
import sys
import urllib.request
import os

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

TASKS = [
    {"id": "py-add", "lang": "python", "prompt": "اكتب دالة بايثون add(a,b) تُرجع المجموع. الكود فقط.", "test": "assert add(2,3)==5"},
    {"id": "js-add", "lang": "javascript", "prompt": "اكتب دالة جافاسكربت add(a,b) تُرجع المجموع. الكود فقط.", "test": "const assert=require('assert');assert.strictEqual(add(2,3),5);"},
]


def ask(url, key, prompt, temperature):
    body = json.dumps({"messages": [{"role": "user", "content": prompt}], "max_tokens": 300, "temperature": temperature}).encode()
    req = urllib.request.Request(url.rstrip("/") + "/chat/completions", body,
                                  {"Content-Type": "application/json", "Authorization": "Bearer " + key})
    return json.load(urllib.request.urlopen(req, timeout=300))["choices"][0]["message"]["content"]


def extract_code(text):
    m = re.search(r"```[a-zA-Z+#]*\n(.*?)```", text, re.S)
    return (m.group(1) if m else text).strip()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", default="http://127.0.0.1:8000/v1")
    ap.add_argument("--key", default="x")
    ap.add_argument("--samples", type=int, default=4)
    ap.add_argument("--temperature", type=float, default=0.8)
    ap.add_argument("--save", default="data/qa/self_learned.jsonl")
    args = ap.parse_args()

    os.makedirs(os.path.dirname(args.save), exist_ok=True)
    out = open(args.save, "a", encoding="utf-8")
    solved = 0
    for t in TASKS:
        mod = importlib.import_module(f"skills.programming.{t['lang']}")
        for _ in range(args.samples):
            answer = ask(args.url, args.key, t["prompt"], args.temperature)
            ok, _log = mod.run(extract_code(answer), t["test"])
            if ok:
                row = {"messages": [{"role": "user", "content": t["prompt"]}, {"role": "assistant", "content": answer}]}
                out.write(json.dumps(row, ensure_ascii=False) + "\n")
                solved += 1
                break
        print(("PASS " if ok else "FAIL ") + t["id"])
    print(f"حُلَّ {solved}/{len(TASKS)}")


if __name__ == "__main__":
    main()

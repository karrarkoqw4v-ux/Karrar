"""المرحلة 6: تقييم جودة الرد العام (ليس الكود). يعيد طرح أسئلة data/qa على النموذج ويقارن تشابه الرد نصياً.
هذا تقييم تقريبي جداً (تشابه كلمات)، وليس بديلاً عن تقييم بشري حقيقي.
    python train/08_eval_chat.py --url http://127.0.0.1:8000/v1
"""
import argparse
import json
import sys
import urllib.request
import os

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from data import qa  # noqa: E402


def ask(url, key, prompt):
    body = json.dumps({"messages": [{"role": "user", "content": prompt}], "max_tokens": 150}).encode()
    req = urllib.request.Request(url.rstrip("/") + "/chat/completions", body,
                                  {"Content-Type": "application/json", "Authorization": "Bearer " + key})
    return json.load(urllib.request.urlopen(req, timeout=120))["choices"][0]["message"]["content"]


def word_overlap(a: str, b: str) -> float:
    wa, wb = set(a.split()), set(b.split())
    return len(wa & wb) / max(1, len(wa | wb))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", default="http://127.0.0.1:8000/v1")
    ap.add_argument("--key", default="x")
    args = ap.parse_args()

    total, score = 0, 0.0
    for topic, pairs in qa.TOPICS.items():
        for q, expected in pairs:
            got = ask(args.url, args.key, q)
            s = word_overlap(got, expected)
            score += s
            total += 1
            print(f"[{topic}] تشابه={s:.2f}  سؤال={q!r}")
    print(f"\nمعدل التشابه الكلي: {score / max(1, total):.2f} على {total} سؤالاً")
    print("تنبيه: هذا مقياس تقريبي (تطابق كلمات)، لا يقيس صحة المعنى.")


if __name__ == "__main__":
    main()

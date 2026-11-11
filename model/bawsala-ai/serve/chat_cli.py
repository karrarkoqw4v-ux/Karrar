"""محادثة في الطرفية مباشرة من ملف النموذج، بدون خادم.
    python serve/chat_cli.py --ckpt out/chat.pt
"""
import argparse
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from serve.model_runtime import Runtime


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--ckpt", default="out/chat.pt")
    ap.add_argument("--tokenizer", default="out/tokenizer.json")
    args = ap.parse_args()

    rt = Runtime(args.ckpt, args.tokenizer)
    history = []
    print("اكتب رسالتك (سطر فارغ للخروج):")
    while True:
        q = input("أنت: ").strip()
        if not q:
            break
        history.append({"role": "user", "content": q})
        ids = rt.build_prompt(history)
        answer = "".join(rt.stream(ids, max_new=200, temperature=0.7))
        history.append({"role": "assistant", "content": answer})
        print("النموذج:", answer)


if __name__ == "__main__":
    main()

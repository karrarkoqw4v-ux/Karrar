"""يجمع نصوص الروابط التي وضعها المستخدم بنفسه في urls.txt (لا زحف عشوائي أبداً)."""
import os

from .fetch import fetch_page
from .state import load, save


def run(urls_file: str, out_dir: str, state_file: str) -> dict:
    urls = [l.strip() for l in open(urls_file, encoding="utf-8") if l.strip() and not l.startswith("#")] \
        if os.path.exists(urls_file) else []
    state = load(state_file)
    os.makedirs(out_dir, exist_ok=True)
    ok, failed = 0, 0
    for i, url in enumerate(urls):
        if url in state["done"]:
            continue
        r = fetch_page(url)
        if r["ok"]:
            open(os.path.join(out_dir, f"page-{i:04d}.txt"), "w", encoding="utf-8").write(r["text"])
            state["done"].append(url)
            ok += 1
        else:
            state["failed"][url] = r["reason"]
            failed += 1
        save(state_file, state)
    return {"ok": ok, "failed": failed, "total": len(urls)}

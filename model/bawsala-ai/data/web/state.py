"""يحفظ تقدّم جمع صفحات الويب (أيّ روابط تمّ جلبها) ليُستأنف لاحقاً بدل إعادة كل شيء."""
import json
import os


def load(path: str) -> dict:
    if os.path.exists(path):
        return json.load(open(path, encoding="utf-8"))
    return {"done": [], "failed": {}}


def save(path: str, state: dict):
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    json.dump(state, open(path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

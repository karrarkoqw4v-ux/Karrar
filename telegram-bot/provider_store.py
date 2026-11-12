"""المزوّد الافتراضي الذي يضبطه الأدمن عبر الأزرار (➕ إضافة API)، محفوظ محلياً، يطبَّق فوراً بلا إعادة تشغيل."""
import json
import os

import config

STORE_FILE = os.path.join(os.path.dirname(__file__), "provider.json")


def load() -> dict:
    if os.path.exists(STORE_FILE):
        return json.load(open(STORE_FILE, encoding="utf-8"))
    return {}


def save(name: str, base_url: str, api_key: str, model: str):
    json.dump({"name": name, "base_url": base_url, "api_key": api_key, "model": model},
               open(STORE_FILE, "w", encoding="utf-8"), ensure_ascii=False)


def set_model(model: str):
    d = load()
    if d:
        d["model"] = model
        json.dump(d, open(STORE_FILE, "w", encoding="utf-8"), ensure_ascii=False)


def active() -> dict:
    """يرجع المزوّد النشط الآن: مزوّد الأدمن إن ضُبط، وإلا القيم من .env الافتراضية."""
    d = load()
    if d and d.get("api_key"):
        return d
    return {"name": "افتراضي (.env)", "base_url": config.AI_BASE_URL, "api_key": config.AI_API_KEY, "model": config.AI_MODEL}

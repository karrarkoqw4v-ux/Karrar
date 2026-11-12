"""نماذج ذكاء اصطناعي متعددة مجانية مفتوحة المصدر، كلها عبر مفتاح OpenRouter واحد فقط.
القائمة تُجلب حيّة من OpenRouter نفسه (لا رقم ثابت مختلَق): النماذج المجانية الفعلية تتغيّر مع الوقت."""
import time

import requests

_cache = {"at": 0, "models": []}
CACHE_SECONDS = 1800


def free_models(force: bool = False) -> list:
    if not force and time.time() - _cache["at"] < CACHE_SECONDS and _cache["models"]:
        return _cache["models"]
    try:
        r = requests.get("https://openrouter.ai/api/v1/models", timeout=20)
        data = r.json().get("data", [])
        free = [m["id"] for m in data if m.get("pricing", {}).get("prompt") == "0"
                and m.get("pricing", {}).get("completion") == "0"]
        _cache["models"], _cache["at"] = free, time.time()
    except Exception:
        pass  # عند فشل الجلب، يبقى آخر ما تم تخزينه مؤقتاً (قد يكون فارغاً أول مرة)
    return _cache["models"]


def ask_openrouter(api_key: str, model: str, history: list, system_prompt: str) -> str:
    try:
        r = requests.post(
            "https://openrouter.ai/api/v1/chat/completions",
            headers={"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"},
            json={"model": model, "messages": [{"role": "system", "content": system_prompt}] + history, "max_tokens": 700},
            timeout=45,
        )
        data = r.json()
        if "choices" not in data:
            return f"⚠️ خطأ من OpenRouter: {data.get('error', data)}"
        return data["choices"][0]["message"]["content"].strip()
    except Exception as e:
        return f"⚠️ تعذّر الوصول للنموذج: {e}"

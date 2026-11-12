"""استدعاء نموذج الذكاء الافتراضي: يقرأ المزوّد الذي ضبطه الأدمن بالأزرار (provider_store)، أو قيم .env احتياطياً."""
import requests

import config
import provider_store


def ask(history: list) -> str:
    p = provider_store.active()
    if not p.get("api_key"):
        return "⚠️ لم يُضبط مفتاح نموذج ذكاء بعد. الأدمن: لوحة التحكم ← ➕ إضافة API."
    try:
        r = requests.post(
            f"{p['base_url'].rstrip('/')}/chat/completions",
            headers={"Authorization": f"Bearer {p['api_key']}", "Content-Type": "application/json"},
            json={"model": p["model"], "messages": [{"role": "system", "content": config.SYSTEM_PROMPT}] + history,
                  "max_tokens": 600, "temperature": 0.7},
            timeout=40,
        )
        data = r.json()
        if "choices" not in data:
            return f"⚠️ خطأ من مزوّد النموذج: {data.get('error', data)}"
        return data["choices"][0]["message"]["content"].strip()
    except Exception as e:
        return f"⚠️ تعذّر الوصول لنموذج الذكاء: {e}"

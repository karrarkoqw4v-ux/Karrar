"""استدعاء نموذج الذكاء الاصطناعي، بنفس منطق مزوّد متوافق مع OpenAI المستخدم في تطبيق بوصلة نفسه."""
import requests

import config


def ask(history: list) -> str:
    if not config.AI_API_KEY:
        return "⚠️ لم يُضبط مفتاح نموذج ذكاء بعد. الأدمن: ضع AI_API_KEY في ملف .env."
    try:
        r = requests.post(
            f"{config.AI_BASE_URL.rstrip('/')}/chat/completions",
            headers={"Authorization": f"Bearer {config.AI_API_KEY}", "Content-Type": "application/json"},
            json={"model": config.AI_MODEL, "messages": [{"role": "system", "content": config.SYSTEM_PROMPT}] + history,
                  "max_tokens": 600, "temperature": 0.7},
            timeout=40,
        )
        data = r.json()
        if "choices" not in data:
            return f"⚠️ خطأ من مزوّد النموذج: {data.get('error', data)}"
        return data["choices"][0]["message"]["content"].strip()
    except Exception as e:
        return f"⚠️ تعذّر الوصول لنموذج الذكاء: {e}"

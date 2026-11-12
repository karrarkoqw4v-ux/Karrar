"""إعدادات البوت، كلها من متغيرات البيئة (ملف .env محلي، لا يُرفع لأي مستودع)."""
import os

try:
    from dotenv import load_dotenv
    load_dotenv()
except ImportError:
    pass  # اختياري؛ إن لم تثبّت python-dotenv عرّف المتغيرات في النظام مباشرة

BOT_TOKEN = os.environ.get("BOT_TOKEN", "")
ADMIN_ID = int(os.environ.get("ADMIN_ID", "0") or 0)
AI_BASE_URL = os.environ.get("AI_BASE_URL", "https://api.groq.com/openai/v1")
AI_API_KEY = os.environ.get("AI_API_KEY", "")
AI_MODEL = os.environ.get("AI_MODEL", "llama-3.3-70b-versatile")
OPENROUTER_API_KEY = os.environ.get("OPENROUTER_API_KEY", "")

API = f"https://api.telegram.org/bot{BOT_TOKEN}"
PREMIUM_EMOJI_FILE = os.path.join(os.path.dirname(__file__), "premium_emoji.json")
STATE_FILE = os.path.join(os.path.dirname(__file__), "state.json")

SYSTEM_PROMPT = (
    "أنت بوصلة، مساعد ذكاء اصطناعي عربي ودود ومباشر. أجب بإيجاز ووضوح، واعترف إن لم تعرف الجواب بدل اختلاقه. "
    "لا تدّعِ أنك تستطيع فعل ما لا تستطيعه فعلاً داخل تيليجرام."
)

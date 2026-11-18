import os
from pathlib import Path

from dotenv import load_dotenv

load_dotenv()

BASE = Path(__file__).parent
BOT_TOKEN = os.getenv("BOT_TOKEN", "").strip()
ADMIN_IDS = {int(x) for x in os.getenv("ADMIN_IDS", "").replace(" ", "").split(",") if x.strip().lstrip("-").isdigit()}
FORCE_CHANNELS = [x.strip() for x in os.getenv("FORCE_CHANNELS", "").split(",") if x.strip()]
MAX_PARALLEL = int(os.getenv("MAX_PARALLEL", "8"))
MAX_DURATION = int(os.getenv("MAX_DURATION", "3600"))
COOKIES_FILE = os.getenv("COOKIES_FILE", "").strip() or None
PROXY = os.getenv("PROXY", "").strip() or None
TELEGRAM_API_URL = os.getenv("TELEGRAM_API_URL", "").strip() or None
DB_PATH = os.getenv("DB_PATH", str(BASE / "bot.db"))
TMP_DIR = Path(os.getenv("TMP_DIR", str(BASE / "downloads")))
TMP_DIR.mkdir(parents=True, exist_ok=True)
# حد الرفع: 50MB على الخادم الرسمي، 2000MB مع Bot API محلي
MAX_UPLOAD_MB = 1990 if TELEGRAM_API_URL else 49

WELCOME = (
    "⌁︙آهلا بكَِ اغاتي في بوت التيك توك 💘\n"
    "⌁︙يمكنك ايضاً التحميل من ( اليوتيوب ، انستكرام ، تيكتوك ، فيسبوك ، تويتر ، سناب شات ، ساوند كلاود )"
)
WAIT = "لحظه من فضلك . . ."

"""لهجة: الصومالية (عربي) (Somali Arabic-speakers). تُستخدم لبناء أمثلة تدريبية في data/qa وملف build_sft في train/."""
DIALECT_ID = "somali_arabic"
NAME_AR = "الصومالية (عربي)"
NAME_EN = "Somali Arabic-speakers"
LOCALE = "ar"
MARKER_WORDS = r""  # كلمات تدل على اللهجة (لاكتشافها تلقائياً من كلام المستخدم)
SAMPLE_GREETING = "أهلاً بك، كيف أساعدك؟"


def detect(text: str) -> bool:
    import re
    if not MARKER_WORDS:
        return False
    return bool(re.search(MARKER_WORDS, text or ""))

"""لهجة: المغربية (Moroccan). تُستخدم لبناء أمثلة تدريبية في data/qa وملف build_sft في train/."""
DIALECT_ID = "morocco"
NAME_AR = "المغربية"
NAME_EN = "Moroccan"
LOCALE = "ar-MA"
MARKER_WORDS = r"كيداير|دابا|بزاف"  # كلمات تدل على اللهجة (لاكتشافها تلقائياً من كلام المستخدم)
SAMPLE_GREETING = "أهلان، كيداير اليوم؟"


def detect(text: str) -> bool:
    import re
    if not MARKER_WORDS:
        return False
    return bool(re.search(MARKER_WORDS, text or ""))

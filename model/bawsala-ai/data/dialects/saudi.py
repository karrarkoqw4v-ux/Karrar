"""لهجة: السعودية (الخليجية) (Saudi). تُستخدم لبناء أمثلة تدريبية في data/qa وملف build_sft في train/."""
DIALECT_ID = "saudi"
NAME_AR = "السعودية (الخليجية)"
NAME_EN = "Saudi"
LOCALE = "ar-SA"
MARKER_WORDS = r"وش|الحين|يالله"  # كلمات تدل على اللهجة (لاكتشافها تلقائياً من كلام المستخدم)
SAMPLE_GREETING = "هلا، وش اقدر اسوي لك الحين؟"


def detect(text: str) -> bool:
    import re
    if not MARKER_WORDS:
        return False
    return bool(re.search(MARKER_WORDS, text or ""))

"""لهجة: العُمانية (Omani). تُستخدم لبناء أمثلة تدريبية في data/qa وملف build_sft في train/."""
DIALECT_ID = "oman"
NAME_AR = "العُمانية"
NAME_EN = "Omani"
LOCALE = "ar-OM"
MARKER_WORDS = r"شو|هسه"  # كلمات تدل على اللهجة (لاكتشافها تلقائياً من كلام المستخدم)
SAMPLE_GREETING = "هلا، شو اخبارك؟"


def detect(text: str) -> bool:
    import re
    if not MARKER_WORDS:
        return False
    return bool(re.search(MARKER_WORDS, text or ""))

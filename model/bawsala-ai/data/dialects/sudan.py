"""لهجة: السودانية (Sudanese). تُستخدم لبناء أمثلة تدريبية في data/qa وملف build_sft في train/."""
DIALECT_ID = "sudan"
NAME_AR = "السودانية"
NAME_EN = "Sudanese"
LOCALE = "ar-SD"
MARKER_WORDS = r"شنو|هسي"  # كلمات تدل على اللهجة (لاكتشافها تلقائياً من كلام المستخدم)
SAMPLE_GREETING = "أهلا، شنو الأخبار؟"


def detect(text: str) -> bool:
    import re
    if not MARKER_WORDS:
        return False
    return bool(re.search(MARKER_WORDS, text or ""))

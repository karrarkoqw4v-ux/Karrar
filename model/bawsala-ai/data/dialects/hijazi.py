"""لهجة: الحجازية (Hijazi). تُستخدم لبناء أمثلة تدريبية في data/qa وملف build_sft في train/."""
DIALECT_ID = "hijazi"
NAME_AR = "الحجازية"
NAME_EN = "Hijazi"
LOCALE = "ar-SA"
MARKER_WORDS = r"ايش|دحين"  # كلمات تدل على اللهجة (لاكتشافها تلقائياً من كلام المستخدم)
SAMPLE_GREETING = "هلا فيك، ايش حالك؟"


def detect(text: str) -> bool:
    import re
    if not MARKER_WORDS:
        return False
    return bool(re.search(MARKER_WORDS, text or ""))

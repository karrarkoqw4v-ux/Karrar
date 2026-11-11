"""لهجة: القطرية (Qatari). تُستخدم لبناء أمثلة تدريبية في data/qa وملف build_sft في train/."""
DIALECT_ID = "qatar"
NAME_AR = "القطرية"
NAME_EN = "Qatari"
LOCALE = "ar-QA"
MARKER_WORDS = r"شخبارك|الحين"  # كلمات تدل على اللهجة (لاكتشافها تلقائياً من كلام المستخدم)
SAMPLE_GREETING = "هلا، شخبارك اليوم؟"


def detect(text: str) -> bool:
    import re
    if not MARKER_WORDS:
        return False
    return bool(re.search(MARKER_WORDS, text or ""))

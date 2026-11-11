"""لهجة: الأردنية (Jordanian). تُستخدم لبناء أمثلة تدريبية في data/qa وملف build_sft في train/."""
DIALECT_ID = "jordan"
NAME_AR = "الأردنية"
NAME_EN = "Jordanian"
LOCALE = "ar-JO"
MARKER_WORDS = r"كيفك|هسا|كثير"  # كلمات تدل على اللهجة (لاكتشافها تلقائياً من كلام المستخدم)
SAMPLE_GREETING = "أهلا، كيفك اليوم؟"


def detect(text: str) -> bool:
    import re
    if not MARKER_WORDS:
        return False
    return bool(re.search(MARKER_WORDS, text or ""))

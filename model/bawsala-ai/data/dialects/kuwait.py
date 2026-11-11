"""لهجة: الكويتية (Kuwaiti). تُستخدم لبناء أمثلة تدريبية في data/qa وملف build_sft في train/."""
DIALECT_ID = "kuwait"
NAME_AR = "الكويتية"
NAME_EN = "Kuwaiti"
LOCALE = "ar-KW"
MARKER_WORDS = r"شلونك|وايد|هالحين"  # كلمات تدل على اللهجة (لاكتشافها تلقائياً من كلام المستخدم)
SAMPLE_GREETING = "هلا والله، شلونك اليوم؟"


def detect(text: str) -> bool:
    import re
    if not MARKER_WORDS:
        return False
    return bool(re.search(MARKER_WORDS, text or ""))

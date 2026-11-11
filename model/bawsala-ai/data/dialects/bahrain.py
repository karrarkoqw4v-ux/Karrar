"""لهجة: البحرينية (Bahraini). تُستخدم لبناء أمثلة تدريبية في data/qa وملف build_sft في train/."""
DIALECT_ID = "bahrain"
NAME_AR = "البحرينية"
NAME_EN = "Bahraini"
LOCALE = "ar-BH"
MARKER_WORDS = r"شخبارك|وايد"  # كلمات تدل على اللهجة (لاكتشافها تلقائياً من كلام المستخدم)
SAMPLE_GREETING = "هلا بيك، شخبارك؟"


def detect(text: str) -> bool:
    import re
    if not MARKER_WORDS:
        return False
    return bool(re.search(MARKER_WORDS, text or ""))

"""لهجة: المصرية (Egyptian). تُستخدم لبناء أمثلة تدريبية في data/qa وملف build_sft في train/."""
DIALECT_ID = "egypt"
NAME_AR = "المصرية"
NAME_EN = "Egyptian"
LOCALE = "ar-EG"
MARKER_WORDS = r"ازيك|دلوقتي|قوي|كده"  # كلمات تدل على اللهجة (لاكتشافها تلقائياً من كلام المستخدم)
SAMPLE_GREETING = "أهلاً بيك، ازيك؟ دلوقتي اقدر اساعدك."


def detect(text: str) -> bool:
    import re
    if not MARKER_WORDS:
        return False
    return bool(re.search(MARKER_WORDS, text or ""))

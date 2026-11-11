"""لهجة: التشادية (Chadian Arabic). تُستخدم لبناء أمثلة تدريبية في data/qa وملف build_sft في train/."""
DIALECT_ID = "chadian"
NAME_AR = "التشادية"
NAME_EN = "Chadian Arabic"
LOCALE = "ar-TD"
MARKER_WORDS = r""  # كلمات تدل على اللهجة (لاكتشافها تلقائياً من كلام المستخدم)
SAMPLE_GREETING = "أهلا، كيف الحال اليوم؟"


def detect(text: str) -> bool:
    import re
    if not MARKER_WORDS:
        return False
    return bool(re.search(MARKER_WORDS, text or ""))

"""لهجة: الفلسطينية (Palestinian). تُستخدم لبناء أمثلة تدريبية في data/qa وملف build_sft في train/."""
DIALECT_ID = "palestine"
NAME_AR = "الفلسطينية"
NAME_EN = "Palestinian"
LOCALE = "ar-PS"
MARKER_WORDS = r"كيفك|هلأ"  # كلمات تدل على اللهجة (لاكتشافها تلقائياً من كلام المستخدم)
SAMPLE_GREETING = "أهلا فيك، كيفك؟"


def detect(text: str) -> bool:
    import re
    if not MARKER_WORDS:
        return False
    return bool(re.search(MARKER_WORDS, text or ""))

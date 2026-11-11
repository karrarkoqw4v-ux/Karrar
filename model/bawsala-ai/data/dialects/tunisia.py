"""لهجة: التونسية (Tunisian). تُستخدم لبناء أمثلة تدريبية في data/qa وملف build_sft في train/."""
DIALECT_ID = "tunisia"
NAME_AR = "التونسية"
NAME_EN = "Tunisian"
LOCALE = "ar-TN"
MARKER_WORDS = r"شنوة|توا|برشا"  # كلمات تدل على اللهجة (لاكتشافها تلقائياً من كلام المستخدم)
SAMPLE_GREETING = "أهلا، شنوة أحوالك؟"


def detect(text: str) -> bool:
    import re
    if not MARKER_WORDS:
        return False
    return bool(re.search(MARKER_WORDS, text or ""))

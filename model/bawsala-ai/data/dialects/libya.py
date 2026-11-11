"""لهجة: الليبية (Libyan). تُستخدم لبناء أمثلة تدريبية في data/qa وملف build_sft في train/."""
DIALECT_ID = "libya"
NAME_AR = "الليبية"
NAME_EN = "Libyan"
LOCALE = "ar-LY"
MARKER_WORDS = r"شن|توا|برشا"  # كلمات تدل على اللهجة (لاكتشافها تلقائياً من كلام المستخدم)
SAMPLE_GREETING = "أهلا، شن أخبارك؟"


def detect(text: str) -> bool:
    import re
    if not MARKER_WORDS:
        return False
    return bool(re.search(MARKER_WORDS, text or ""))

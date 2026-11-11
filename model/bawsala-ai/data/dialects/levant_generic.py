"""لهجة: الشامية العامة (Generic Levantine). تُستخدم لبناء أمثلة تدريبية في data/qa وملف build_sft في train/."""
DIALECT_ID = "levant_generic"
NAME_AR = "الشامية العامة"
NAME_EN = "Generic Levantine"
LOCALE = "ar"
MARKER_WORDS = r"شو|هلق"  # كلمات تدل على اللهجة (لاكتشافها تلقائياً من كلام المستخدم)
SAMPLE_GREETING = "أهلين، شو بقدر ساعدك فيه؟"


def detect(text: str) -> bool:
    import re
    if not MARKER_WORDS:
        return False
    return bool(re.search(MARKER_WORDS, text or ""))

"""لهجة: المغاربية العامة (Generic Maghrebi). تُستخدم لبناء أمثلة تدريبية في data/qa وملف build_sft في train/."""
DIALECT_ID = "maghreb_generic"
NAME_AR = "المغاربية العامة"
NAME_EN = "Generic Maghrebi"
LOCALE = "ar"
MARKER_WORDS = r"واش|دابا"  # كلمات تدل على اللهجة (لاكتشافها تلقائياً من كلام المستخدم)
SAMPLE_GREETING = "أهلا، واش نقدر نعاونك؟"


def detect(text: str) -> bool:
    import re
    if not MARKER_WORDS:
        return False
    return bool(re.search(MARKER_WORDS, text or ""))

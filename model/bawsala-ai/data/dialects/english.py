"""لهجة: الإنجليزية (عام) (English (general)). تُستخدم لبناء أمثلة تدريبية في data/qa وملف build_sft في train/."""
DIALECT_ID = "english"
NAME_AR = "الإنجليزية (عام)"
NAME_EN = "English (general)"
LOCALE = "en"
MARKER_WORDS = r""  # كلمات تدل على اللهجة (لاكتشافها تلقائياً من كلام المستخدم)
SAMPLE_GREETING = "Hi! How can I help you today?"


def detect(text: str) -> bool:
    import re
    if not MARKER_WORDS:
        return False
    return bool(re.search(MARKER_WORDS, text or ""))

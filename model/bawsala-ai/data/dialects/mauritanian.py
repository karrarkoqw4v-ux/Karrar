"""لهجة: الموريتانية (الحسانية) (Mauritanian Hassaniya). تُستخدم لبناء أمثلة تدريبية في data/qa وملف build_sft في train/."""
DIALECT_ID = "mauritanian"
NAME_AR = "الموريتانية (الحسانية)"
NAME_EN = "Mauritanian Hassaniya"
LOCALE = "ar-MR"
MARKER_WORDS = r""  # كلمات تدل على اللهجة (لاكتشافها تلقائياً من كلام المستخدم)
SAMPLE_GREETING = "أهلاً، كيفاش نعاونك؟"


def detect(text: str) -> bool:
    import re
    if not MARKER_WORDS:
        return False
    return bool(re.search(MARKER_WORDS, text or ""))

"""لهجة: الفصحى (Modern Standard Arabic). تُستخدم لبناء أمثلة تدريبية في data/qa وملف build_sft في train/."""
DIALECT_ID = "msa"
NAME_AR = "الفصحى"
NAME_EN = "Modern Standard Arabic"
LOCALE = "ar"
MARKER_WORDS = r""  # كلمات تدل على اللهجة (لاكتشافها تلقائياً من كلام المستخدم)
SAMPLE_GREETING = "أهلاً بك، كيف يمكنني مساعدتك؟"


def detect(text: str) -> bool:
    import re
    if not MARKER_WORDS:
        return False
    return bool(re.search(MARKER_WORDS, text or ""))

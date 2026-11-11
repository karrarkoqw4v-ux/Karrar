"""لهجة: السورية (Syrian). تُستخدم لبناء أمثلة تدريبية في data/qa وملف build_sft في train/."""
DIALECT_ID = "syria"
NAME_AR = "السورية"
NAME_EN = "Syrian"
LOCALE = "ar-SY"
MARKER_WORDS = r"شو|هلق|كتير|منيح"  # كلمات تدل على اللهجة (لاكتشافها تلقائياً من كلام المستخدم)
SAMPLE_GREETING = "أهلين فيك، كيفك؟ هلق فيني ساعدك."


def detect(text: str) -> bool:
    import re
    if not MARKER_WORDS:
        return False
    return bool(re.search(MARKER_WORDS, text or ""))

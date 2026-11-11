"""لهجة: اللبنانية (Lebanese). تُستخدم لبناء أمثلة تدريبية في data/qa وملف build_sft في train/."""
DIALECT_ID = "lebanon"
NAME_AR = "اللبنانية"
NAME_EN = "Lebanese"
LOCALE = "ar-LB"
MARKER_WORDS = r"كيفك|هلأ|كتير"  # كلمات تدل على اللهجة (لاكتشافها تلقائياً من كلام المستخدم)
SAMPLE_GREETING = "أهلا فيك، كيفك اليوم؟"


def detect(text: str) -> bool:
    import re
    if not MARKER_WORDS:
        return False
    return bool(re.search(MARKER_WORDS, text or ""))

"""لهجة: الجزائرية (Algerian). تُستخدم لبناء أمثلة تدريبية في data/qa وملف build_sft في train/."""
DIALECT_ID = "algeria"
NAME_AR = "الجزائرية"
NAME_EN = "Algerian"
LOCALE = "ar-DZ"
MARKER_WORDS = r"واش|دروك|بزاف"  # كلمات تدل على اللهجة (لاكتشافها تلقائياً من كلام المستخدم)
SAMPLE_GREETING = "أهلا، واش راك اليوم؟"


def detect(text: str) -> bool:
    import re
    if not MARKER_WORDS:
        return False
    return bool(re.search(MARKER_WORDS, text or ""))

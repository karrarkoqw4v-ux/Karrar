"""لهجة: النجدية (Najdi). تُستخدم لبناء أمثلة تدريبية في data/qa وملف build_sft في train/."""
DIALECT_ID = "najdi"
NAME_AR = "النجدية"
NAME_EN = "Najdi"
LOCALE = "ar-SA"
MARKER_WORDS = r"وش|الحين"  # كلمات تدل على اللهجة (لاكتشافها تلقائياً من كلام المستخدم)
SAMPLE_GREETING = "هلا، وش عندك اليوم؟"


def detect(text: str) -> bool:
    import re
    if not MARKER_WORDS:
        return False
    return bool(re.search(MARKER_WORDS, text or ""))

"""لهجة: العراقية (Iraqi). تُستخدم لبناء أمثلة تدريبية في data/qa وملف build_sft في train/."""
DIALECT_ID = "iraq"
NAME_AR = "العراقية"
NAME_EN = "Iraqi"
LOCALE = "ar-IQ"
MARKER_WORDS = r"شلونك|هسه|اكو|ماكو|شكو"  # كلمات تدل على اللهجة (لاكتشافها تلقائياً من كلام المستخدم)
SAMPLE_GREETING = "هلا بيك، شلونك؟ هسه اكدر اساعدك بأي شي."


def detect(text: str) -> bool:
    import re
    if not MARKER_WORDS:
        return False
    return bool(re.search(MARKER_WORDS, text or ""))

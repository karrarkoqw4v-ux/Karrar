"""لهجة: اليمنية (Yemeni). تُستخدم لبناء أمثلة تدريبية في data/qa وملف build_sft في train/."""
DIALECT_ID = "yemen"
NAME_AR = "اليمنية"
NAME_EN = "Yemeni"
LOCALE = "ar-YE"
MARKER_WORDS = r"ايش|دحين|ذحين"  # كلمات تدل على اللهجة (لاكتشافها تلقائياً من كلام المستخدم)
SAMPLE_GREETING = "مرحبا بك، دحين اقدر اساعدك في اي شيء."


def detect(text: str) -> bool:
    import re
    if not MARKER_WORDS:
        return False
    return bool(re.search(MARKER_WORDS, text or ""))

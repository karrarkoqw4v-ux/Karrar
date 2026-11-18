"""رسم النص العربي بشكل صحيح مع Pillow: يستخدم raqm إن وُجد وإلا arabic_reshaper+bidi"""
import arabic_reshaper
from bidi.algorithm import get_display
from PIL import features

HAS_RAQM = features.check("raqm")


def shape(text: str) -> str:
    """نص جاهز للرسم/القياس"""
    if HAS_RAQM:
        return text
    return get_display(arabic_reshaper.reshape(text))


def draw_kw() -> dict:
    return {"language": "ar"} if HAS_RAQM else {}

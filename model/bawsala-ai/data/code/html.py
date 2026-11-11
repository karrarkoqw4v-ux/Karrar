"""جمع كود html المرخّص لتعليم النموذج هذه اللغة تحديداً.
ترخيص مقبول لهذه اللغة في المصادر الشائعة: public-domain. ملاحظة أسلوبية: علامات مغلقة دوماً، lang وcharset في head
"""
from config.policy import license_ok

LANGUAGE = "html"
FILE_EXT = ".html"
TYPICAL_LICENSE = "public-domain"
STYLE_NOTE = "علامات مغلقة دوماً، lang وcharset في head"

# مثال تدريبي قصير يُستخدم في اختبارات الوحدة (tests/) للتأكد أن المهارة حيّة
SAMPLE_SNIPPET = '<!doctype html>\n<html><body><p>Hello</p></body></html>\n'


def mission(data_dir: str = "data/raw/code/html", max_tokens: int = 60_000_000) -> dict:
    """يُرجع وصف مهمة جاهزاً لـ train/06_data_missions.py (لا يجلب شيئاً بنفسه، فقط يصف المهمة)."""
    assert license_ok(TYPICAL_LICENSE), "ترخيص هذه اللغة غير مسموح في config/policy.py"
    return {
        "id": "code-html",
        "kind": "hf_dataset",
        "name": "bigcode/the-stack-smol",
        "data_dir": f"data/html",
        "license": TYPICAL_LICENSE,
        "max_tokens": max_tokens,
        "ext": FILE_EXT,
    }

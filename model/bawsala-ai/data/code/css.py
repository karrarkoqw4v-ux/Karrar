"""جمع كود css المرخّص لتعليم النموذج هذه اللغة تحديداً.
ترخيص مقبول لهذه اللغة في المصادر الشائعة: public-domain. ملاحظة أسلوبية: selector { خاصية: قيمة; }، استخدم متغيرات :root
"""
from config.policy import license_ok

LANGUAGE = "css"
FILE_EXT = ".css"
TYPICAL_LICENSE = "public-domain"
STYLE_NOTE = "selector { خاصية: قيمة; }، استخدم متغيرات :root"

# مثال تدريبي قصير يُستخدم في اختبارات الوحدة (tests/) للتأكد أن المهارة حيّة
SAMPLE_SNIPPET = 'body { margin: 0; }\n'


def mission(data_dir: str = "data/raw/code/css", max_tokens: int = 60_000_000) -> dict:
    """يُرجع وصف مهمة جاهزاً لـ train/06_data_missions.py (لا يجلب شيئاً بنفسه، فقط يصف المهمة)."""
    assert license_ok(TYPICAL_LICENSE), "ترخيص هذه اللغة غير مسموح في config/policy.py"
    return {
        "id": "code-css",
        "kind": "hf_dataset",
        "name": "bigcode/the-stack-smol",
        "data_dir": f"data/css",
        "license": TYPICAL_LICENSE,
        "max_tokens": max_tokens,
        "ext": FILE_EXT,
    }

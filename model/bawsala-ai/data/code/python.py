"""جمع كود python المرخّص لتعليم النموذج هذه اللغة تحديداً.
ترخيص مقبول لهذه اللغة في المصادر الشائعة: apache-2.0. ملاحظة أسلوبية: 4 مسافات للإزاحة، لا فواصل منقوطة، PEP 8
"""
from config.policy import license_ok

LANGUAGE = "python"
FILE_EXT = ".py"
TYPICAL_LICENSE = "apache-2.0"
STYLE_NOTE = "4 مسافات للإزاحة، لا فواصل منقوطة، PEP 8"

# مثال تدريبي قصير يُستخدم في اختبارات الوحدة (tests/) للتأكد أن المهارة حيّة
SAMPLE_SNIPPET = 'def add(a, b):\n    return a + b\n'


def mission(data_dir: str = "data/raw/code/python", max_tokens: int = 60_000_000) -> dict:
    """يُرجع وصف مهمة جاهزاً لـ train/06_data_missions.py (لا يجلب شيئاً بنفسه، فقط يصف المهمة)."""
    assert license_ok(TYPICAL_LICENSE), "ترخيص هذه اللغة غير مسموح في config/policy.py"
    return {
        "id": "code-python",
        "kind": "hf_dataset",
        "name": "bigcode/the-stack-smol",
        "data_dir": f"data/python",
        "license": TYPICAL_LICENSE,
        "max_tokens": max_tokens,
        "ext": FILE_EXT,
    }

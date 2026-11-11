"""جمع كود kotlin المرخّص لتعليم النموذج هذه اللغة تحديداً.
ترخيص مقبول لهذه اللغة في المصادر الشائعة: apache-2.0. ملاحظة أسلوبية: fun للدوال، null-safety مدمجة بعلامة ?
"""
from config.policy import license_ok

LANGUAGE = "kotlin"
FILE_EXT = ".kt"
TYPICAL_LICENSE = "apache-2.0"
STYLE_NOTE = "fun للدوال، null-safety مدمجة بعلامة ?"

# مثال تدريبي قصير يُستخدم في اختبارات الوحدة (tests/) للتأكد أن المهارة حيّة
SAMPLE_SNIPPET = 'fun add(a: Int, b: Int): Int = a + b\n'


def mission(data_dir: str = "data/raw/code/kotlin", max_tokens: int = 60_000_000) -> dict:
    """يُرجع وصف مهمة جاهزاً لـ train/06_data_missions.py (لا يجلب شيئاً بنفسه، فقط يصف المهمة)."""
    assert license_ok(TYPICAL_LICENSE), "ترخيص هذه اللغة غير مسموح في config/policy.py"
    return {
        "id": "code-kotlin",
        "kind": "hf_dataset",
        "name": "bigcode/the-stack-smol",
        "data_dir": f"data/kotlin",
        "license": TYPICAL_LICENSE,
        "max_tokens": max_tokens,
        "ext": FILE_EXT,
    }

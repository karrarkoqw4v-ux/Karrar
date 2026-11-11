"""جمع كود scala المرخّص لتعليم النموذج هذه اللغة تحديداً.
ترخيص مقبول لهذه اللغة في المصادر الشائعة: apache-2.0. ملاحظة أسلوبية: val ثابت، var متغيّر، كل شيء تعبير (expression)
"""
from config.policy import license_ok

LANGUAGE = "scala"
FILE_EXT = ".scala"
TYPICAL_LICENSE = "apache-2.0"
STYLE_NOTE = "val ثابت، var متغيّر، كل شيء تعبير (expression)"

# مثال تدريبي قصير يُستخدم في اختبارات الوحدة (tests/) للتأكد أن المهارة حيّة
SAMPLE_SNIPPET = 'def add(a: Int, b: Int): Int = a + b\n'


def mission(data_dir: str = "data/raw/code/scala", max_tokens: int = 60_000_000) -> dict:
    """يُرجع وصف مهمة جاهزاً لـ train/06_data_missions.py (لا يجلب شيئاً بنفسه، فقط يصف المهمة)."""
    assert license_ok(TYPICAL_LICENSE), "ترخيص هذه اللغة غير مسموح في config/policy.py"
    return {
        "id": "code-scala",
        "kind": "hf_dataset",
        "name": "bigcode/the-stack-smol",
        "data_dir": f"data/scala",
        "license": TYPICAL_LICENSE,
        "max_tokens": max_tokens,
        "ext": FILE_EXT,
    }

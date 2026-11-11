"""جمع كود dart المرخّص لتعليم النموذج هذه اللغة تحديداً.
ترخيص مقبول لهذه اللغة في المصادر الشائعة: bsd-3-clause. ملاحظة أسلوبية: يُستخدم مع Flutter، null-safety بعلامة ?
"""
from config.policy import license_ok

LANGUAGE = "dart"
FILE_EXT = ".dart"
TYPICAL_LICENSE = "bsd-3-clause"
STYLE_NOTE = "يُستخدم مع Flutter، null-safety بعلامة ?"

# مثال تدريبي قصير يُستخدم في اختبارات الوحدة (tests/) للتأكد أن المهارة حيّة
SAMPLE_SNIPPET = 'int add(int a, int b) => a + b;\n'


def mission(data_dir: str = "data/raw/code/dart", max_tokens: int = 60_000_000) -> dict:
    """يُرجع وصف مهمة جاهزاً لـ train/06_data_missions.py (لا يجلب شيئاً بنفسه، فقط يصف المهمة)."""
    assert license_ok(TYPICAL_LICENSE), "ترخيص هذه اللغة غير مسموح في config/policy.py"
    return {
        "id": "code-dart",
        "kind": "hf_dataset",
        "name": "bigcode/the-stack-smol",
        "data_dir": f"data/dart",
        "license": TYPICAL_LICENSE,
        "max_tokens": max_tokens,
        "ext": FILE_EXT,
    }

"""جمع كود typescript المرخّص لتعليم النموذج هذه اللغة تحديداً.
ترخيص مقبول لهذه اللغة في المصادر الشائعة: mit. ملاحظة أسلوبية: أنواع صريحة، interface/type للعقود
"""
from config.policy import license_ok

LANGUAGE = "typescript"
FILE_EXT = ".ts"
TYPICAL_LICENSE = "mit"
STYLE_NOTE = "أنواع صريحة، interface/type للعقود"

# مثال تدريبي قصير يُستخدم في اختبارات الوحدة (tests/) للتأكد أن المهارة حيّة
SAMPLE_SNIPPET = 'function add(a: number, b: number): number {\n  return a + b;\n}\n'


def mission(data_dir: str = "data/raw/code/typescript", max_tokens: int = 60_000_000) -> dict:
    """يُرجع وصف مهمة جاهزاً لـ train/06_data_missions.py (لا يجلب شيئاً بنفسه، فقط يصف المهمة)."""
    assert license_ok(TYPICAL_LICENSE), "ترخيص هذه اللغة غير مسموح في config/policy.py"
    return {
        "id": "code-typescript",
        "kind": "hf_dataset",
        "name": "bigcode/the-stack-smol",
        "data_dir": f"data/typescript",
        "license": TYPICAL_LICENSE,
        "max_tokens": max_tokens,
        "ext": FILE_EXT,
    }

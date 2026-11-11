"""جمع كود csharp المرخّص لتعليم النموذج هذه اللغة تحديداً.
ترخيص مقبول لهذه اللغة في المصادر الشائعة: mit. ملاحظة أسلوبية: PascalCase للدوال والأصناف، ; إلزامية
"""
from config.policy import license_ok

LANGUAGE = "csharp"
FILE_EXT = ".cs"
TYPICAL_LICENSE = "mit"
STYLE_NOTE = "PascalCase للدوال والأصناف، ; إلزامية"

# مثال تدريبي قصير يُستخدم في اختبارات الوحدة (tests/) للتأكد أن المهارة حيّة
SAMPLE_SNIPPET = 'static int Add(int a, int b) => a + b;\n'


def mission(data_dir: str = "data/raw/code/csharp", max_tokens: int = 60_000_000) -> dict:
    """يُرجع وصف مهمة جاهزاً لـ train/06_data_missions.py (لا يجلب شيئاً بنفسه، فقط يصف المهمة)."""
    assert license_ok(TYPICAL_LICENSE), "ترخيص هذه اللغة غير مسموح في config/policy.py"
    return {
        "id": "code-csharp",
        "kind": "hf_dataset",
        "name": "bigcode/the-stack-smol",
        "data_dir": f"data/csharp",
        "license": TYPICAL_LICENSE,
        "max_tokens": max_tokens,
        "ext": FILE_EXT,
    }

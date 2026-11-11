"""جمع كود java المرخّص لتعليم النموذج هذه اللغة تحديداً.
ترخيص مقبول لهذه اللغة في المصادر الشائعة: apache-2.0. ملاحظة أسلوبية: كل ملف بصنف عام واحد، camelCase للدوال
"""
from config.policy import license_ok

LANGUAGE = "java"
FILE_EXT = ".java"
TYPICAL_LICENSE = "apache-2.0"
STYLE_NOTE = "كل ملف بصنف عام واحد، camelCase للدوال"

# مثال تدريبي قصير يُستخدم في اختبارات الوحدة (tests/) للتأكد أن المهارة حيّة
SAMPLE_SNIPPET = 'static int add(int a, int b) {\n    return a + b;\n}\n'


def mission(data_dir: str = "data/raw/code/java", max_tokens: int = 60_000_000) -> dict:
    """يُرجع وصف مهمة جاهزاً لـ train/06_data_missions.py (لا يجلب شيئاً بنفسه، فقط يصف المهمة)."""
    assert license_ok(TYPICAL_LICENSE), "ترخيص هذه اللغة غير مسموح في config/policy.py"
    return {
        "id": "code-java",
        "kind": "hf_dataset",
        "name": "bigcode/the-stack-smol",
        "data_dir": f"data/java",
        "license": TYPICAL_LICENSE,
        "max_tokens": max_tokens,
        "ext": FILE_EXT,
    }

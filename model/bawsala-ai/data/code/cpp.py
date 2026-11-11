"""جمع كود cpp المرخّص لتعليم النموذج هذه اللغة تحديداً.
ترخيص مقبول لهذه اللغة في المصادر الشائعة: bsd-3-clause. ملاحظة أسلوبية: استخدم RAII بدل التحرير اليدوي للذاكرة
"""
from config.policy import license_ok

LANGUAGE = "cpp"
FILE_EXT = ".cpp"
TYPICAL_LICENSE = "bsd-3-clause"
STYLE_NOTE = "استخدم RAII بدل التحرير اليدوي للذاكرة"

# مثال تدريبي قصير يُستخدم في اختبارات الوحدة (tests/) للتأكد أن المهارة حيّة
SAMPLE_SNIPPET = 'int add(int a, int b) {\n    return a + b;\n}\n'


def mission(data_dir: str = "data/raw/code/cpp", max_tokens: int = 60_000_000) -> dict:
    """يُرجع وصف مهمة جاهزاً لـ train/06_data_missions.py (لا يجلب شيئاً بنفسه، فقط يصف المهمة)."""
    assert license_ok(TYPICAL_LICENSE), "ترخيص هذه اللغة غير مسموح في config/policy.py"
    return {
        "id": "code-cpp",
        "kind": "hf_dataset",
        "name": "bigcode/the-stack-smol",
        "data_dir": f"data/cpp",
        "license": TYPICAL_LICENSE,
        "max_tokens": max_tokens,
        "ext": FILE_EXT,
    }

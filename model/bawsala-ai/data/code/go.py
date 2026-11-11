"""جمع كود go المرخّص لتعليم النموذج هذه اللغة تحديداً.
ترخيص مقبول لهذه اللغة في المصادر الشائعة: bsd-3-clause. ملاحظة أسلوبية: تبويب (tab) للإزاحة، لا استثناءات بل error يُرجَع
"""
from config.policy import license_ok

LANGUAGE = "go"
FILE_EXT = ".go"
TYPICAL_LICENSE = "bsd-3-clause"
STYLE_NOTE = "تبويب (tab) للإزاحة، لا استثناءات بل error يُرجَع"

# مثال تدريبي قصير يُستخدم في اختبارات الوحدة (tests/) للتأكد أن المهارة حيّة
SAMPLE_SNIPPET = 'func add(a, b int) int {\n\treturn a + b\n}\n'


def mission(data_dir: str = "data/raw/code/go", max_tokens: int = 60_000_000) -> dict:
    """يُرجع وصف مهمة جاهزاً لـ train/06_data_missions.py (لا يجلب شيئاً بنفسه، فقط يصف المهمة)."""
    assert license_ok(TYPICAL_LICENSE), "ترخيص هذه اللغة غير مسموح في config/policy.py"
    return {
        "id": "code-go",
        "kind": "hf_dataset",
        "name": "bigcode/the-stack-smol",
        "data_dir": f"data/go",
        "license": TYPICAL_LICENSE,
        "max_tokens": max_tokens,
        "ext": FILE_EXT,
    }

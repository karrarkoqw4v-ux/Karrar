"""جمع كود rust المرخّص لتعليم النموذج هذه اللغة تحديداً.
ترخيص مقبول لهذه اللغة في المصادر الشائعة: mit. ملاحظة أسلوبية: لا فاصلة منقوطة في آخر تعبير = قيمة الإرجاع
"""
from config.policy import license_ok

LANGUAGE = "rust"
FILE_EXT = ".rs"
TYPICAL_LICENSE = "mit"
STYLE_NOTE = "لا فاصلة منقوطة في آخر تعبير = قيمة الإرجاع"

# مثال تدريبي قصير يُستخدم في اختبارات الوحدة (tests/) للتأكد أن المهارة حيّة
SAMPLE_SNIPPET = 'fn add(a: i32, b: i32) -> i32 {\n    a + b\n}\n'


def mission(data_dir: str = "data/raw/code/rust", max_tokens: int = 60_000_000) -> dict:
    """يُرجع وصف مهمة جاهزاً لـ train/06_data_missions.py (لا يجلب شيئاً بنفسه، فقط يصف المهمة)."""
    assert license_ok(TYPICAL_LICENSE), "ترخيص هذه اللغة غير مسموح في config/policy.py"
    return {
        "id": "code-rust",
        "kind": "hf_dataset",
        "name": "bigcode/the-stack-smol",
        "data_dir": f"data/rust",
        "license": TYPICAL_LICENSE,
        "max_tokens": max_tokens,
        "ext": FILE_EXT,
    }

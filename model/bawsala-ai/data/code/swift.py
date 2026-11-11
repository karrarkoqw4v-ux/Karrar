"""جمع كود swift المرخّص لتعليم النموذج هذه اللغة تحديداً.
ترخيص مقبول لهذه اللغة في المصادر الشائعة: apache-2.0. ملاحظة أسلوبية: let ثابت، var متغيّر، Optional بعلامة ?
"""
from config.policy import license_ok

LANGUAGE = "swift"
FILE_EXT = ".swift"
TYPICAL_LICENSE = "apache-2.0"
STYLE_NOTE = "let ثابت، var متغيّر، Optional بعلامة ?"

# مثال تدريبي قصير يُستخدم في اختبارات الوحدة (tests/) للتأكد أن المهارة حيّة
SAMPLE_SNIPPET = 'func add(_ a: Int, _ b: Int) -> Int {\n    return a + b\n}\n'


def mission(data_dir: str = "data/raw/code/swift", max_tokens: int = 60_000_000) -> dict:
    """يُرجع وصف مهمة جاهزاً لـ train/06_data_missions.py (لا يجلب شيئاً بنفسه، فقط يصف المهمة)."""
    assert license_ok(TYPICAL_LICENSE), "ترخيص هذه اللغة غير مسموح في config/policy.py"
    return {
        "id": "code-swift",
        "kind": "hf_dataset",
        "name": "bigcode/the-stack-smol",
        "data_dir": f"data/swift",
        "license": TYPICAL_LICENSE,
        "max_tokens": max_tokens,
        "ext": FILE_EXT,
    }

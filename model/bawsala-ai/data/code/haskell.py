"""جمع كود haskell المرخّص لتعليم النموذج هذه اللغة تحديداً.
ترخيص مقبول لهذه اللغة في المصادر الشائعة: bsd-3-clause. ملاحظة أسلوبية: دالة نقية بلا آثار جانبية، النوع يُكتب منفصلاً
"""
from config.policy import license_ok

LANGUAGE = "haskell"
FILE_EXT = ".hs"
TYPICAL_LICENSE = "bsd-3-clause"
STYLE_NOTE = "دالة نقية بلا آثار جانبية، النوع يُكتب منفصلاً"

# مثال تدريبي قصير يُستخدم في اختبارات الوحدة (tests/) للتأكد أن المهارة حيّة
SAMPLE_SNIPPET = 'add :: Int -> Int -> Int\nadd a b = a + b\n'


def mission(data_dir: str = "data/raw/code/haskell", max_tokens: int = 60_000_000) -> dict:
    """يُرجع وصف مهمة جاهزاً لـ train/06_data_missions.py (لا يجلب شيئاً بنفسه، فقط يصف المهمة)."""
    assert license_ok(TYPICAL_LICENSE), "ترخيص هذه اللغة غير مسموح في config/policy.py"
    return {
        "id": "code-haskell",
        "kind": "hf_dataset",
        "name": "bigcode/the-stack-smol",
        "data_dir": f"data/haskell",
        "license": TYPICAL_LICENSE,
        "max_tokens": max_tokens,
        "ext": FILE_EXT,
    }

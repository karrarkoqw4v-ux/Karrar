"""جمع كود lua المرخّص لتعليم النموذج هذه اللغة تحديداً.
ترخيص مقبول لهذه اللغة في المصادر الشائعة: mit. ملاحظة أسلوبية: الفهرسة تبدأ من 1 لا من 0
"""
from config.policy import license_ok

LANGUAGE = "lua"
FILE_EXT = ".lua"
TYPICAL_LICENSE = "mit"
STYLE_NOTE = "الفهرسة تبدأ من 1 لا من 0"

# مثال تدريبي قصير يُستخدم في اختبارات الوحدة (tests/) للتأكد أن المهارة حيّة
SAMPLE_SNIPPET = 'function add(a, b)\n  return a + b\nend\n'


def mission(data_dir: str = "data/raw/code/lua", max_tokens: int = 60_000_000) -> dict:
    """يُرجع وصف مهمة جاهزاً لـ train/06_data_missions.py (لا يجلب شيئاً بنفسه، فقط يصف المهمة)."""
    assert license_ok(TYPICAL_LICENSE), "ترخيص هذه اللغة غير مسموح في config/policy.py"
    return {
        "id": "code-lua",
        "kind": "hf_dataset",
        "name": "bigcode/the-stack-smol",
        "data_dir": f"data/lua",
        "license": TYPICAL_LICENSE,
        "max_tokens": max_tokens,
        "ext": FILE_EXT,
    }

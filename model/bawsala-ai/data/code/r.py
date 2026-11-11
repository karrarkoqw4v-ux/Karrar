"""جمع كود r المرخّص لتعليم النموذج هذه اللغة تحديداً.
ترخيص مقبول لهذه اللغة في المصادر الشائعة: mit. ملاحظة أسلوبية: <- للإسناد عرفاً، vectors أساس كل شيء
"""
from config.policy import license_ok

LANGUAGE = "r"
FILE_EXT = ".r"
TYPICAL_LICENSE = "mit"
STYLE_NOTE = "<- للإسناد عرفاً، vectors أساس كل شيء"

# مثال تدريبي قصير يُستخدم في اختبارات الوحدة (tests/) للتأكد أن المهارة حيّة
SAMPLE_SNIPPET = 'add <- function(a, b) a + b\n'


def mission(data_dir: str = "data/raw/code/r", max_tokens: int = 60_000_000) -> dict:
    """يُرجع وصف مهمة جاهزاً لـ train/06_data_missions.py (لا يجلب شيئاً بنفسه، فقط يصف المهمة)."""
    assert license_ok(TYPICAL_LICENSE), "ترخيص هذه اللغة غير مسموح في config/policy.py"
    return {
        "id": "code-r",
        "kind": "hf_dataset",
        "name": "bigcode/the-stack-smol",
        "data_dir": f"data/r",
        "license": TYPICAL_LICENSE,
        "max_tokens": max_tokens,
        "ext": FILE_EXT,
    }

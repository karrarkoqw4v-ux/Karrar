"""جمع كود ruby المرخّص لتعليم النموذج هذه اللغة تحديداً.
ترخيص مقبول لهذه اللغة في المصادر الشائعة: mit. ملاحظة أسلوبية: لا أقواس إلزامية، end تغلق الكتل
"""
from config.policy import license_ok

LANGUAGE = "ruby"
FILE_EXT = ".rb"
TYPICAL_LICENSE = "mit"
STYLE_NOTE = "لا أقواس إلزامية، end تغلق الكتل"

# مثال تدريبي قصير يُستخدم في اختبارات الوحدة (tests/) للتأكد أن المهارة حيّة
SAMPLE_SNIPPET = 'def add(a, b)\n  a + b\nend\n'


def mission(data_dir: str = "data/raw/code/ruby", max_tokens: int = 60_000_000) -> dict:
    """يُرجع وصف مهمة جاهزاً لـ train/06_data_missions.py (لا يجلب شيئاً بنفسه، فقط يصف المهمة)."""
    assert license_ok(TYPICAL_LICENSE), "ترخيص هذه اللغة غير مسموح في config/policy.py"
    return {
        "id": "code-ruby",
        "kind": "hf_dataset",
        "name": "bigcode/the-stack-smol",
        "data_dir": f"data/ruby",
        "license": TYPICAL_LICENSE,
        "max_tokens": max_tokens,
        "ext": FILE_EXT,
    }

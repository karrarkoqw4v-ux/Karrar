"""جمع كود assembly المرخّص لتعليم النموذج هذه اللغة تحديداً.
ترخيص مقبول لهذه اللغة في المصادر الشائعة: public-domain. ملاحظة أسلوبية: لا تجريد؛ تحكم مباشر بالسجلات والذاكرة
"""
from config.policy import license_ok

LANGUAGE = "assembly"
FILE_EXT = ".asm"
TYPICAL_LICENSE = "public-domain"
STYLE_NOTE = "لا تجريد؛ تحكم مباشر بالسجلات والذاكرة"

# مثال تدريبي قصير يُستخدم في اختبارات الوحدة (tests/) للتأكد أن المهارة حيّة
SAMPLE_SNIPPET = 'add eax, ebx\n'


def mission(data_dir: str = "data/raw/code/assembly", max_tokens: int = 60_000_000) -> dict:
    """يُرجع وصف مهمة جاهزاً لـ train/06_data_missions.py (لا يجلب شيئاً بنفسه، فقط يصف المهمة)."""
    assert license_ok(TYPICAL_LICENSE), "ترخيص هذه اللغة غير مسموح في config/policy.py"
    return {
        "id": "code-assembly",
        "kind": "hf_dataset",
        "name": "bigcode/the-stack-smol",
        "data_dir": f"data/assembly",
        "license": TYPICAL_LICENSE,
        "max_tokens": max_tokens,
        "ext": FILE_EXT,
    }

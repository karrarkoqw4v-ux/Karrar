"""جمع كود powershell المرخّص لتعليم النموذج هذه اللغة تحديداً.
ترخيص مقبول لهذه اللغة في المصادر الشائعة: mit. ملاحظة أسلوبية: Verb-Noun لتسمية الدوال (Get-X, Set-X)
"""
from config.policy import license_ok

LANGUAGE = "powershell"
FILE_EXT = ".ps1"
TYPICAL_LICENSE = "mit"
STYLE_NOTE = "Verb-Noun لتسمية الدوال (Get-X, Set-X)"

# مثال تدريبي قصير يُستخدم في اختبارات الوحدة (tests/) للتأكد أن المهارة حيّة
SAMPLE_SNIPPET = 'function Add($a, $b) { return $a + $b }\n'


def mission(data_dir: str = "data/raw/code/powershell", max_tokens: int = 60_000_000) -> dict:
    """يُرجع وصف مهمة جاهزاً لـ train/06_data_missions.py (لا يجلب شيئاً بنفسه، فقط يصف المهمة)."""
    assert license_ok(TYPICAL_LICENSE), "ترخيص هذه اللغة غير مسموح في config/policy.py"
    return {
        "id": "code-powershell",
        "kind": "hf_dataset",
        "name": "bigcode/the-stack-smol",
        "data_dir": f"data/powershell",
        "license": TYPICAL_LICENSE,
        "max_tokens": max_tokens,
        "ext": FILE_EXT,
    }

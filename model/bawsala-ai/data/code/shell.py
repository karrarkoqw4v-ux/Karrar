"""جمع كود shell المرخّص لتعليم النموذج هذه اللغة تحديداً.
ترخيص مقبول لهذه اللغة في المصادر الشائعة: public-domain. ملاحظة أسلوبية: set -euo pipefail في بداية أي سكربت جاد
"""
from config.policy import license_ok

LANGUAGE = "shell"
FILE_EXT = ".sh"
TYPICAL_LICENSE = "public-domain"
STYLE_NOTE = "set -euo pipefail في بداية أي سكربت جاد"

# مثال تدريبي قصير يُستخدم في اختبارات الوحدة (tests/) للتأكد أن المهارة حيّة
SAMPLE_SNIPPET = 'add() { echo $(( $1 + $2 )); }\n'


def mission(data_dir: str = "data/raw/code/shell", max_tokens: int = 60_000_000) -> dict:
    """يُرجع وصف مهمة جاهزاً لـ train/06_data_missions.py (لا يجلب شيئاً بنفسه، فقط يصف المهمة)."""
    assert license_ok(TYPICAL_LICENSE), "ترخيص هذه اللغة غير مسموح في config/policy.py"
    return {
        "id": "code-shell",
        "kind": "hf_dataset",
        "name": "bigcode/the-stack-smol",
        "data_dir": f"data/shell",
        "license": TYPICAL_LICENSE,
        "max_tokens": max_tokens,
        "ext": FILE_EXT,
    }

"""جمع كود php المرخّص لتعليم النموذج هذه اللغة تحديداً.
ترخيص مقبول لهذه اللغة في المصادر الشائعة: mit. ملاحظة أسلوبية: <?php في بداية الملف، $ قبل كل متغير
"""
from config.policy import license_ok

LANGUAGE = "php"
FILE_EXT = ".php"
TYPICAL_LICENSE = "mit"
STYLE_NOTE = "<?php في بداية الملف، $ قبل كل متغير"

# مثال تدريبي قصير يُستخدم في اختبارات الوحدة (tests/) للتأكد أن المهارة حيّة
SAMPLE_SNIPPET = 'function add($a, $b) {\n    return $a + $b;\n}\n'


def mission(data_dir: str = "data/raw/code/php", max_tokens: int = 60_000_000) -> dict:
    """يُرجع وصف مهمة جاهزاً لـ train/06_data_missions.py (لا يجلب شيئاً بنفسه، فقط يصف المهمة)."""
    assert license_ok(TYPICAL_LICENSE), "ترخيص هذه اللغة غير مسموح في config/policy.py"
    return {
        "id": "code-php",
        "kind": "hf_dataset",
        "name": "bigcode/the-stack-smol",
        "data_dir": f"data/php",
        "license": TYPICAL_LICENSE,
        "max_tokens": max_tokens,
        "ext": FILE_EXT,
    }

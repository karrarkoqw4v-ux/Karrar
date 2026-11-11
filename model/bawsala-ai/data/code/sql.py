"""جمع كود sql المرخّص لتعليم النموذج هذه اللغة تحديداً.
ترخيص مقبول لهذه اللغة في المصادر الشائعة: public-domain. ملاحظة أسلوبية: كلمات مفتاحية بأحرف كبيرة عرفاً، ; تنهي الجملة
"""
from config.policy import license_ok

LANGUAGE = "sql"
FILE_EXT = ".sql"
TYPICAL_LICENSE = "public-domain"
STYLE_NOTE = "كلمات مفتاحية بأحرف كبيرة عرفاً، ; تنهي الجملة"

# مثال تدريبي قصير يُستخدم في اختبارات الوحدة (tests/) للتأكد أن المهارة حيّة
SAMPLE_SNIPPET = 'SELECT a + b AS sum FROM numbers;\n'


def mission(data_dir: str = "data/raw/code/sql", max_tokens: int = 60_000_000) -> dict:
    """يُرجع وصف مهمة جاهزاً لـ train/06_data_missions.py (لا يجلب شيئاً بنفسه، فقط يصف المهمة)."""
    assert license_ok(TYPICAL_LICENSE), "ترخيص هذه اللغة غير مسموح في config/policy.py"
    return {
        "id": "code-sql",
        "kind": "hf_dataset",
        "name": "bigcode/the-stack-smol",
        "data_dir": f"data/sql",
        "license": TYPICAL_LICENSE,
        "max_tokens": max_tokens,
        "ext": FILE_EXT,
    }

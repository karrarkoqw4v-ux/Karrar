"""جمع كود sql_plsql المرخّص لتعليم النموذج هذه اللغة تحديداً.
ترخيص مقبول لهذه اللغة في المصادر الشائعة: public-domain. ملاحظة أسلوبية: خاص بقواعد بيانات تدعم إجراءات مخزّنة (PostgreSQL/Oracle)
"""
from config.policy import license_ok

LANGUAGE = "sql_plsql"
FILE_EXT = ".sql"
TYPICAL_LICENSE = "public-domain"
STYLE_NOTE = "خاص بقواعد بيانات تدعم إجراءات مخزّنة (PostgreSQL/Oracle)"

# مثال تدريبي قصير يُستخدم في اختبارات الوحدة (tests/) للتأكد أن المهارة حيّة
SAMPLE_SNIPPET = 'CREATE FUNCTION add(a INT, b INT) RETURNS INT AS $$ SELECT a + b $$ LANGUAGE SQL;\n'


def mission(data_dir: str = "data/raw/code/sql_plsql", max_tokens: int = 60_000_000) -> dict:
    """يُرجع وصف مهمة جاهزاً لـ train/06_data_missions.py (لا يجلب شيئاً بنفسه، فقط يصف المهمة)."""
    assert license_ok(TYPICAL_LICENSE), "ترخيص هذه اللغة غير مسموح في config/policy.py"
    return {
        "id": "code-sql_plsql",
        "kind": "hf_dataset",
        "name": "bigcode/the-stack-smol",
        "data_dir": f"data/sql_plsql",
        "license": TYPICAL_LICENSE,
        "max_tokens": max_tokens,
        "ext": FILE_EXT,
    }

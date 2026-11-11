"""مهارة برمجة rust: يكتب النموذج كوداً بهذه اللغة، وهذا الملف يحكم عليه عملياً (تنفيذ فعلي إن أمكن)."""
EXT = ".rs"

RUNNER = None

def can_execute_here() -> bool:
    return False  # لا مترجم متاح لهذه اللغة في هذه البيئة؛ التقييم يبقى نصياً فقط

def run(code: str, test: str, timeout: int = 10):
    return None, 'التنفيذ الفعلي لهذه اللغة غير متاح هنا؛ راجعها يدوياً'


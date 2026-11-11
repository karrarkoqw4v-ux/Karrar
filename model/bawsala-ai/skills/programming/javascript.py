"""مهارة برمجة javascript: يكتب النموذج كوداً بهذه اللغة، وهذا الملف يحكم عليه عملياً (تنفيذ فعلي إن أمكن)."""
EXT = ".js"

RUNNER = ['node']

def can_execute_here() -> bool:
    import shutil
    return shutil.which(RUNNER[0]) is not None

def run(code: str, test: str, timeout: int = 10):
    """يكتب code+test في ملف مؤقت وينفّذه فعلاً، يُرجع (نجح: bool, الخرج: str)."""
    import subprocess, tempfile, os as _os
    if not can_execute_here():
        return None, 'لا يوجد مترجم/مفسّر ' + RUNNER[0] + ' على هذا الجهاز'
    with tempfile.TemporaryDirectory() as d:
        f = _os.path.join(d, 'snippet{}'.format(EXT))
        open(f, 'w', encoding='utf-8').write(code + '\n' + test + '\n')
        try:
            r = subprocess.run(RUNNER + [f], cwd=d, capture_output=True, timeout=timeout, text=True)
            return r.returncode == 0, (r.stdout + r.stderr)[-500:]
        except subprocess.TimeoutExpired:
            return False, 'تجاوز الوقت المحدد (حلقة لا نهائية محتملة)'


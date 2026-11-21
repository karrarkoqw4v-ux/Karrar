"""المشرف: يشغّل main.py ويعيد تشغيله خلال ثانية إن انتهى لأي سبب (انهيار/تجمّد/إعادة تشغيل ذاتية).
استخدمه كأمر التشغيل على الاستضافة:  python run_forever.py"""
import os
import signal
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
SCRIPT = os.getenv("BOT_SCRIPT", "main.py")
child = None


def _stop(sig, frm):
    if child and child.poll() is None:
        child.terminate()
        try:
            child.wait(10)
        except subprocess.TimeoutExpired:
            child.kill()
    sys.exit(0)


signal.signal(signal.SIGTERM, _stop)
signal.signal(signal.SIGINT, _stop)
restarts = 0
fast = 0
while True:
    env = dict(os.environ, RESTART_COUNT=str(restarts), PYTHONUNBUFFERED="1")
    t0 = time.time()
    print(f"▶️ تشغيل البوت (إعادة رقم {restarts})", flush=True)
    child = subprocess.Popen([sys.executable, os.path.join(HERE, SCRIPT)], env=env, cwd=HERE)
    code = child.wait()
    up = time.time() - t0
    fast = fast + 1 if up < 20 else 0
    base = float(os.getenv("RESTART_DELAY", "1"))
    delay = base if fast <= 3 else min(30.0, 2.0 ** (fast - 3))  # أول 3 انهيارات سريعة: ثانية واحدة، ثم تباطؤ تدريجي لمنع حلقة انهيار
    print(f"⚠️ توقفت العملية (الرمز {code}) بعد {up:.0f}ث — إعادة التشغيل بعد {delay:.0f}ث", flush=True)
    restarts += 1
    time.sleep(delay)

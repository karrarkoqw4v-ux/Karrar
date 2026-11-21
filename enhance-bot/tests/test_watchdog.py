"""اختبار: (1) المشرف يعيد تشغيل العملية بعد انتهائها، (2) الحارس ينهي العملية عند تجمّد الحلقة."""
import os, subprocess, sys, tempfile, time, shutil
from pathlib import Path
HERE = Path(__file__).parent.parent
# --- 1) المشرف
d = Path(tempfile.mkdtemp()); shutil.copy(HERE / "run_forever.py", d / "run_forever.py")
(d / "main.py").write_text("import os,sys\nopen('log.txt','a').write(os.environ['RESTART_COUNT']+'\\n')\nsys.exit(3)\n")
p = subprocess.Popen([sys.executable, "run_forever.py"], cwd=d, env=dict(os.environ, RESTART_DELAY="0.3"), stdout=subprocess.DEVNULL)
time.sleep(4.5); p.terminate(); p.wait(10)
lines = (d / "log.txt").read_text().split()
assert len(lines) >= 4 and lines[:4] == ["0", "1", "2", "3"], lines
print("SUPERVISOR restarts OK", lines[:5])
# --- 2) الحارس
sys.path.insert(0, str(HERE)); os.environ["DB_PATH"] = tempfile.mktemp()
import watchdog
hit = []; watchdog._EXIT = lambda c: hit.append(c)
watchdog.STATE["loop_beat"] = time.time() - 100
watchdog.start_guard(stall=2); time.sleep(2.5)
assert hit == [97], hit
print("GUARD stall exit OK")
import asyncio
async def beat():
    t = asyncio.create_task(watchdog.beat_task(None)); await asyncio.sleep(2.3); t.cancel()
asyncio.run(beat()); assert watchdog.loop_age() < 2 and Path(watchdog.HEART).exists(); print("HEARTBEAT OK")
print("WATCHDOG OK")

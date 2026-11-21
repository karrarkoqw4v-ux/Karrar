"""مراقب الصحة 24/7: نبض كل ثانية + خيط حارس مستقل + إعادة تشغيل ذاتية عند التجمّد + تنبيه المسؤول عند العودة بعد توقف.
يعمل مع run_forever.py (المشرف) الذي يعيد تشغيل العملية خلال ثانية إن انتهت لأي سبب."""
import asyncio
import logging
import os
import threading
import time
from pathlib import Path

import db

log = logging.getLogger("watchdog")
HEART = Path(os.getenv("HEARTBEAT_FILE", "/tmp/voibot_heartbeat"))
STALL_SECS = float(os.getenv("WATCHDOG_STALL", "45"))  # حلقة الأحداث متجمدة أكثر من هذا => نعيد التشغيل
MAX_RSS_MB = int(os.getenv("MAX_RSS_MB", "0") or 0)  # 0 = بدون حدّ ذاكرة
_EXIT = os._exit  # قابلة للاستبدال بالاختبار
STATE = {
    "started": time.time(), "loop_beat": time.time(), "last_update": time.time(), "max_lag": 0.0,
    "getme_ms": None, "getme_fail": 0, "getme_ok_at": None, "restarts": int(os.getenv("RESTART_COUNT", "0") or 0), "downtime": 0.0,
}


def touch_update():
    STATE["last_update"] = time.time()


def rss_mb() -> float:
    try:
        for ln in Path("/proc/self/status").read_text().splitlines():
            if ln.startswith("VmRSS:"):
                return int(ln.split()[1]) / 1024
    except Exception:  # noqa: BLE001
        pass
    return 0.0


def loop_age() -> float:
    return time.time() - STATE["loop_beat"]


def health_json() -> dict:
    return {"ok": loop_age() < STALL_SECS, "loop_age": round(loop_age(), 2), "uptime": int(time.time() - STATE["started"]), "restarts": STATE["restarts"],
            "getme_ms": STATE["getme_ms"], "getme_fail": STATE["getme_fail"], "rss_mb": round(rss_mb())}


def start_guard(idle_fn=None, stall: float | None = None):
    """خيط مستقل يفحص كل ثانية: إن توقف نبض الحلقة => إنهاء العملية فوراً ليعيدها المشرف"""
    limit = stall if stall is not None else STALL_SECS

    def run():
        while True:
            time.sleep(1)
            age = time.time() - STATE["loop_beat"]
            if age > limit:
                log.error("الحلقة متجمدة منذ %.0f ثانية — إعادة تشغيل ذاتية", age)
                _EXIT(97)
                return
            if MAX_RSS_MB:
                r = rss_mb()
                if r > MAX_RSS_MB * 1.5 or (r > MAX_RSS_MB and (idle_fn is None or idle_fn())):
                    log.error("استهلاك الذاكرة %.0fMB تجاوز الحد — إعادة تشغيل ذاتية", r)
                    _EXIT(98)
                    return
    t = threading.Thread(target=run, name="watchdog-guard", daemon=True)
    t.start()
    return t


async def beat_task(bot=None):
    """نبض كل ثانية: يقيس تأخر الحلقة ويكتب ملف النبض ويخزّن آخر نبضة ويفحص اتصال تيليجرام كل 30 ثانية"""
    last = time.time()
    n = 0
    while True:
        await asyncio.sleep(1)
        now = time.time()
        STATE["max_lag"] = max(STATE["max_lag"], now - last - 1)
        last = now
        STATE["loop_beat"] = now
        n += 1
        try:
            HEART.write_text(str(now))
        except Exception:  # noqa: BLE001
            pass
        if n % 10 == 0:
            await asyncio.to_thread(db.put_setting, "last_beat", str(now))
        if bot is not None and n % 30 == 0:
            t0 = time.time()
            try:
                await asyncio.wait_for(bot.get_me(), 15)
                STATE["getme_ms"] = int((time.time() - t0) * 1000)
                STATE["getme_ok_at"] = time.time()
                STATE["getme_fail"] = 0
            except Exception as e:  # noqa: BLE001
                STATE["getme_fail"] += 1
                log.warning("فحص اتصال تيليجرام فشل (%d): %s", STATE["getme_fail"], type(e).__name__)


async def announce_recovery(bot, admin_ids):
    """إن كان آخر نبض مخزّن قديماً: البوت كان متوقفاً => ننبّه المسؤول بمدة التوقف"""
    try:
        last = float(db.setting("last_beat") or 0)
    except ValueError:
        last = 0
    gap = time.time() - last if last else 0
    if 25 < gap < 7 * 86400:
        STATE["downtime"] = gap
        mins = gap / 60
        txt = f"♻️ <b>رجع البوت للعمل</b> بعد توقف حوالي {mins:.1f} دقيقة" + (f"\nإعادة التشغيل رقم {STATE['restarts']}" if STATE["restarts"] else "")
        await db.add_alert("recover", txt.replace("\n", " "))
        if os.getenv("ANNOUNCE_START", "1") != "0":
            for a in admin_ids:
                try:
                    await bot.send_message(a, txt)
                except Exception:  # noqa: BLE001
                    pass

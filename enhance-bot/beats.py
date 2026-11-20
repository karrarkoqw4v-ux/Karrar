"""كشف إيقاع الموسيقى (BPM + طور) بـ NumPy: تدفق طيفي (spectral flux) ثم ارتباط ذاتي.
يُستخدم لمزامنة الفلاش/الزوم/الاهتزاز مع ضربات الصوت فعلاً."""
import asyncio
import subprocess

import numpy as np

SR = 11025
HOP = 256
WIN = 1024
_cache: dict[str, dict | None] = {}


def _decode(path: str, seconds: int = 60) -> np.ndarray | None:
    p = subprocess.run(["ffmpeg", "-nostdin", "-loglevel", "error", "-t", str(seconds), "-i", path, "-vn", "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"], capture_output=True)
    if p.returncode != 0 or len(p.stdout) < SR * 2 * 4:
        return None
    return np.frombuffer(p.stdout, np.float32)


def analyze_sync(path: str) -> dict | None:
    x = _decode(path)
    if x is None:
        return None
    n = 1 + (len(x) - WIN) // HOP
    if n < 40:
        return None
    idx = np.arange(WIN)[None, :] + HOP * np.arange(n)[:, None]
    fr = x[idx] * np.hanning(WIN)[None, :]
    mag = np.abs(np.fft.rfft(fr, axis=1))
    mag = np.log1p(10 * mag[:, 2:180])  # نطاق الإيقاع (طبول/باس) تقريباً 40Hz–1.9kHz
    flux = np.maximum(0, np.diff(mag, axis=0)).sum(axis=1)
    flux = np.concatenate([[0], flux])
    flux = flux - np.convolve(flux, np.ones(21) / 21, mode="same")  # إزالة الاتجاه
    flux = np.maximum(flux, 0)
    raw_peak = float(np.percentile(flux, 99))
    if flux.max() <= 1e-6:
        return None
    flux /= flux.max()
    fps = SR / HOP
    lags = np.arange(int(fps * 60 / 180), int(fps * 60 / 60) + 1)  # 60..180 BPM
    ac = np.array([np.dot(flux[:-l], flux[l:]) for l in lags])
    # نفضّل 80..160 BPM لتفادي أخطاء المضاعفات
    bpm_axis = 60 * fps / lags
    w = np.where((bpm_axis >= 80) & (bpm_axis <= 160), 1.0, 0.75)
    best = int(np.argmax(ac * w))
    period = lags[best] / fps
    # صقل الدورة والطور بدقة أقل من إطار: نمسح دورات حول القيمة التقريبية ونقيس تطابق الشبكة مع قمم التدفق
    t = np.arange(len(flux)) / fps
    total = t[-1]
    best_sc, best = -1.0, (period, 0.0)
    for P in np.linspace(period * 0.96, period * 1.04, 81):
        for ph in np.linspace(0, P, 24, endpoint=False):
            gt = np.arange(ph, total, P)
            sc = float(np.interp(gt, t, flux).sum()) / max(len(gt), 1)
            if sc > best_sc:
                best_sc, best = sc, (P, ph)
    P, ph = best
    ph = (ph + 0.68 * WIN / SR) % P  # تعويض تأخر نافذة التحليل
    base = float(flux.mean()) + 1e-9
    conf = best_sc / base
    if raw_peak < 0.6 or conf < 2.2:
        return None  # لا إيقاع واضح
    return {"period": float(P), "phase": float(ph), "bpm": float(60 / P), "conf": float(conf)}


async def analyze(path: str) -> dict | None:
    if path not in _cache:
        _cache[path] = await asyncio.to_thread(analyze_sync, path)
    return _cache[path]

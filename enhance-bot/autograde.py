"""تحليل الفيديو واختيار إعدادات التلوين تلقائياً (Auto Color Grading)
نأخذ ~8 لقطات، نقيس: السطوع، المدى الديناميكي، التشبع، ميل اللون، التشويش، الحدة،
ثم نحوّلها إلى سلسلة فلاتر FFmpeg مضبوطة على هذا الفيديو تحديداً (بدون حرق الألوان)."""
import cv2
import numpy as np


def analyze(path: str, dur: float, n: int = 8) -> dict:
    cap = cv2.VideoCapture(path)
    frames = []
    for i in range(n):
        t = dur * (i + 0.5) / n
        cap.set(cv2.CAP_PROP_POS_MSEC, t * 1000)
        ok, fr = cap.read()
        if not ok:
            continue
        h, w = fr.shape[:2]
        k = 320 / max(w, 1)
        frames.append(cv2.resize(fr, (320, max(2, int(h * k))), interpolation=cv2.INTER_AREA))
    cap.release()
    if not frames:
        return {}
    L, S, P1, P99, NOISE, SHARP = [], [], [], [], [], []
    bgr = np.zeros(3)
    for fr in frames:
        f = fr.astype(np.float32) / 255
        y = 0.114 * f[..., 0] + 0.587 * f[..., 1] + 0.299 * f[..., 2]
        L.append(float(y.mean()))
        P1.append(float(np.percentile(y, 1)))
        P99.append(float(np.percentile(y, 99)))
        hsv = cv2.cvtColor(fr, cv2.COLOR_BGR2HSV)
        S.append(float(hsv[..., 1].mean() / 255))
        bgr += f.reshape(-1, 3).mean(axis=0)
        g = cv2.cvtColor(fr, cv2.COLOR_BGR2GRAY)
        lap = cv2.Laplacian(g, cv2.CV_32F)
        SHARP.append(float(lap.var()))
        # تقدير التشويش (Immerkaer): انحراف مطلق وسطي لفلتر الكشف
        k = np.array([[1, -2, 1], [-2, 4, -2], [1, -2, 1]], np.float32)
        r = cv2.filter2D(g.astype(np.float32), -1, k)
        NOISE.append(float(np.sqrt(np.pi / 2) * np.abs(r).mean() / 6))
    bgr /= len(frames)
    b, g_, r = bgr
    return {"luma": float(np.mean(L)), "p1": float(np.mean(P1)), "p99": float(np.mean(P99)), "sat": float(np.mean(S)),
            "rgb": (float(r), float(g_), float(b)), "noise": float(np.mean(NOISE)), "sharp": float(np.mean(SHARP))}


def _c(v, lo, hi):
    return max(lo, min(hi, v))


MODES = {
    # قوة كل جانب: (ألوان، تباين، حدة، تنقية)
    "natural": dict(sat=0.7, contrast=0.7, sharp=0.8, hdr=0.0),
    "grade": dict(sat=1.0, contrast=1.0, sharp=1.0, hdr=0.5),
    "vivid": dict(sat=1.5, contrast=1.1, sharp=1.0, hdr=0.5),
    "hdr": dict(sat=1.25, contrast=1.3, sharp=1.35, hdr=1.0),
}


def chain(st: dict, mode: str = "grade") -> str:
    """يبني سلسلة فلاتر FFmpeg من إحصائيات الفيديو. عدّل MODES لتغيير القوة."""
    m = MODES[mode]
    if not st:
        return "eq=saturation=1.1:contrast=1.04"
    import math
    parts = []
    # 1) نقطتا الأسود والأبيض: نفرد المدى الفعلي للفيديو (الظلام يُرفع، الباهت يُمدّ) بحدّ أقصى ~2.2× لتفادي تضخيم الضجيج
    mean = _c(st["luma"], 0.04, 0.96)
    lo = _c(st["p1"] * 0.85, 0.0, 0.15)
    hi = _c(max(st["p99"] * 1.06 + 0.02, lo + 0.555), 0.45, 1.0)  # أقصى مدّ ~1.8×
    gain = 1.0 / max(hi - lo, 0.2)
    # نحدّ مخرجات الأسود والأبيض (0.015..0.985) كي لا يحدث قصّ صلب (clipping)
    parts.append(f"colorlevels=rimin={lo:.3f}:gimin={lo:.3f}:bimin={lo:.3f}:rimax={hi:.3f}:gimax={hi:.3f}:bimax={hi:.3f}:romin=0.015:gomin=0.015:bomin=0.015:romax=0.985:gomax=0.985:bomax=0.985")
    # 2) تعريض: بعد المدّ نقرّب متوسط السطوع من ~0.47 (gamma>1 يفتّح)
    mean2 = _c((mean - lo) / max(hi - lo, 0.2), 0.04, 0.96)
    gamma = _c(math.log(mean2) / math.log(0.47), 0.85, 1.4)
    gamma = 1 + (gamma - 1) * (0.8 + 0.2 * m["contrast"])
    # 3) توازن أبيض جزئي (ميل لوني) — نصف قوة الرمادي العالمي لتجنّب تغيير الأجواء
    r, g, b = st["rgb"]
    mu = (r + g + b) / 3
    gr, gg, gb = (_c(1 + (mu / max(x, 1e-3) - 1) * 0.5, 0.88, 1.12) for x in (r, g, b))
    parts.append(f"colorchannelmixer=rr={gr:.3f}:gg={gg:.3f}:bb={gb:.3f}")
    # 4) تباين: خفيف بعد المدّ (المدّ نفسه يعطي معظم التباين)
    contrast = 1 + 0.07 * m["contrast"]
    # 5) تشبع: نرفع الباهت أكثر من الزاهي (vibrance) ثم تشبعاً عاماً خفيفاً
    vib = _c((0.45 - st["sat"]) * 1.6, 0.1, 0.6) * m["sat"]
    sat = 1 + 0.06 * m["sat"]
    parts.append(f"eq=gamma={gamma:.3f}:contrast={contrast:.3f}:saturation={sat:.3f}")
    parts.append(f"vibrance=intensity={min(vib, 1.5):.2f}")
    # 6) منحنى S لعمق الظلال والإضاءات (يُضاف بقوة التباين)
    k = 0.03 * m["contrast"]
    parts.append(f"curves=all='0/0 0.25/{0.25 - k:.3f} 0.75/{0.75 + k:.3f} 1/1'")
    # 7) تنقية ذكية: فقط إذا كان التشويش عالياً (لا نضيّع الملامح)
    dn = _c((st["noise"] - 1.2) * 0.9 + (gain - 1.2) * 1.2, 0.0, 6.0)  # المدّ الكبير يضخّم الضجيج فنزيد التنقية
    if dn > 0.4:
        parts.append(f"hqdn3d={dn:.2f}:{dn * 0.8:.2f}:{dn * 2.2:.2f}:{dn * 2.2:.2f}")
    # 8) حدة: أعلى للصور الضبابية، وأخف للحادة أصلاً
    blur = _c(1.0 - st["sharp"] / 400.0, 0.0, 1.0)
    amt = (0.45 + 0.9 * blur) * m["sharp"]
    parts.append(f"unsharp=5:5:{amt:.2f}:5:5:0.0")
    if m["hdr"] > 0:
        parts.append(f"unsharp=13:13:{0.45 * m['hdr'] + 0.15:.2f}:13:13:0.0")  # تباين محلي (مظهر HDR)
    return ",".join(parts)

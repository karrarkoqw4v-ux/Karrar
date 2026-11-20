"""معالجات الصورة/الفيديو بمكتبات عالمية: OpenCV + NumPy (HDR بدمج التعريضات، Retinex، إزالة الضباب، NLM، CLAHE...).
كل دالة تأخذ إطار BGR (uint8) وتُرجع إطاراً بنفس الشكل أو أكبر (للتكبير)."""
import cv2
import numpy as np

cv2.setNumThreads(1)


def _f(img):
    return img.astype(np.float32) / 255.0


def _u(img):
    return np.clip(img * 255.0 + 0.5, 0, 255).astype(np.uint8)


def _blur_big(img, sigma):
    """تمويه كبير سريع عبر التصغير"""
    h, w = img.shape[:2]
    k = 4 if sigma > 20 else 2
    small = cv2.resize(img, (max(1, w // k), max(1, h // k)), interpolation=cv2.INTER_AREA)
    small = cv2.GaussianBlur(small, (0, 0), sigma / k)
    return cv2.resize(small, (w, h), interpolation=cv2.INTER_LINEAR)


def clahe(frame, clip=2.0, tiles=8, mix=1.0):
    lab = cv2.cvtColor(frame, cv2.COLOR_BGR2LAB)
    l, a, b = cv2.split(lab)
    l2 = cv2.createCLAHE(clipLimit=clip, tileGridSize=(tiles, tiles)).apply(l)
    l = cv2.addWeighted(l2, mix, l, 1 - mix, 0)
    return cv2.cvtColor(cv2.merge([l, a, b]), cv2.COLOR_LAB2BGR)


def sharpen(frame, a1=0.7, s1=1.0, a2=0.35, s2=3.0):
    f = _f(frame)
    b1 = cv2.GaussianBlur(f, (0, 0), s1)
    b2 = _blur_big(f, s2 * 4) if s2 > 6 else cv2.GaussianBlur(f, (0, 0), s2)
    return _u(f + a1 * (f - b1) + a2 * (f - b2))


def saturation(frame, k=1.15):
    hsv = cv2.cvtColor(frame, cv2.COLOR_BGR2HSV).astype(np.float32)
    hsv[..., 1] = np.clip(hsv[..., 1] * k, 0, 255)
    return cv2.cvtColor(hsv.astype(np.uint8), cv2.COLOR_HSV2BGR)


def vibrance(frame, k=0.5):
    """رفع التشبع للألوان الباهتة أكثر من الزاهية (طبيعي)"""
    hsv = cv2.cvtColor(frame, cv2.COLOR_BGR2HSV).astype(np.float32)
    s = hsv[..., 1] / 255.0
    hsv[..., 1] = np.clip(hsv[..., 1] * (1 + k * (1 - s)), 0, 255)
    return cv2.cvtColor(hsv.astype(np.uint8), cv2.COLOR_HSV2BGR)


def hdr_fusion(frame, strength=1.0, gammas=(0.5, 1.0, 1.9)):
    """HDR واقعي: تعريضات اصطناعية + دمج Mertens (طريقة معيارية) ثم تحسين تباين محلي"""
    f = _f(frame)
    exps = [_u(np.clip(f ** g, 0, 1)) for g in gammas]  # Mertens يتوقع صوراً 8-bit
    merge = cv2.createMergeMertens(contrast_weight=1.0 * strength, saturation_weight=1.0, exposure_weight=1.0)
    fused = np.nan_to_num(merge.process(exps), nan=0.5)
    fused = np.clip(fused, 0, 1)
    # نمزج بين الأصل والمدموج حسب القوة لتفادي المظهر المبالغ فيه
    out = _u(f * (1 - min(1.0, 0.75 * strength)) + fused * min(1.0, 0.75 * strength))
    out = clahe(out, clip=1.4 + 0.8 * strength, mix=0.55)
    return out


def retinex(frame, mix=0.6, sigmas=(12, 60, 220)):
    """MSR: تحسين الإضاءة المنخفضة وإظهار تفاصيل الظلال"""
    f = _f(frame) + 1e-3
    lg = np.log(f)
    msr = sum(lg - np.log(_blur_big(f, s) + 1e-3) for s in sigmas) / len(sigmas)
    lo, hi = np.percentile(msr, 1), np.percentile(msr, 99)
    r = np.clip((msr - lo) / max(hi - lo, 1e-3), 0, 1)
    return _u(f * (1 - mix) + r * mix)


def dehaze(frame, omega=0.92, t0=0.25, patch=15):
    """إزالة الضباب (Dark Channel Prior — He et al.)"""
    f = _f(frame)
    h, w = f.shape[:2]
    small = cv2.resize(f, (max(1, w // 2), max(1, h // 2)), interpolation=cv2.INTER_AREA)
    dark = cv2.erode(np.min(small, axis=2), np.ones((patch, patch), np.uint8))
    n = max(1, int(dark.size * 0.001))
    idx = np.argpartition(dark.ravel(), -n)[-n:]
    A = small.reshape(-1, 3)[idx].max(axis=0)
    A = np.maximum(A, 0.5)
    t = 1 - omega * cv2.erode(np.min(small / A, axis=2), np.ones((patch, patch), np.uint8))
    t = cv2.GaussianBlur(t, (0, 0), 6)
    t = cv2.resize(t, (w, h), interpolation=cv2.INTER_LINEAR)
    t = np.clip(t, t0, 1)[..., None]
    return _u((f - A) / t + A)


def denoise(frame, h=3.5):
    return cv2.fastNlMeansDenoisingColored(frame, None, h, h, 5, 11)


def denoise_fast(frame):
    return cv2.bilateralFilter(frame, 5, 28, 5)


def detail(frame, amount=1.5, sigma=0.12):
    """تضخيم التفاصيل: فصل القاعدة عن التفاصيل (فلتر ثنائي) ثم تعزيزها"""
    f = _f(frame)
    h, w = f.shape[:2]
    small = cv2.resize(f, (max(1, w // 2), max(1, h // 2)), interpolation=cv2.INTER_AREA)
    base = cv2.bilateralFilter(small, 7, sigma * 255 / 8, 5)
    base = cv2.resize(base, (w, h), interpolation=cv2.INTER_LINEAR)
    return _u(base + amount * (f - base))


def white_balance(frame, strength=0.8):
    f = frame.astype(np.float32)
    means = f.reshape(-1, 3).mean(axis=0)
    gain = means.mean() / np.maximum(means, 1)
    gain = 1 + (gain - 1) * strength
    return np.clip(f * gain, 0, 255).astype(np.uint8)


def filmic(frame, exposure=1.25, sat=1.1):
    x = _f(frame) * exposure
    a, b, c, d, e = 2.51, 0.03, 2.43, 0.59, 0.14
    y = np.clip((x * (a * x + b)) / (x * (c * x + d) + e), 0, 1)
    return saturation(_u(y), sat)


def upscale(frame, factor=2.0, maxside=2560):
    h, w = frame.shape[:2]
    k = min(factor, maxside / max(w, h))
    if k <= 1.01:
        return frame
    return cv2.resize(frame, (int(w * k) // 2 * 2, int(h * k) // 2 * 2), interpolation=cv2.INTER_LANCZOS4)


def super_res(frame, factor=2.0):
    """تكبير + إزالة تشويش + توضيح متعدد المقاييس (برمجي)"""
    up = upscale(denoise_fast(frame), factor)
    return sharpen(up, 0.9, 1.0, 0.35, 3.0)


def ultra(frame):
    f = white_balance(frame, 0.5)
    f = denoise_fast(f)
    f = retinex(f, mix=0.3)
    f = hdr_fusion(f, 0.9)
    f = detail(f, 1.35)
    f = sharpen(f, 0.6, 1.0, 0.25, 3.0)
    return vibrance(f, 0.45)


def _pseudo_hdr(frame):
    """دمج تعريضات اصطناعية في إشعاعية (Debevec-lite): خطّي ثم متوسط موزون بدالة القبّعة"""
    f = _f(frame)
    gam = (0.55, 1.0, 1.8)
    times = (0.5, 1.0, 2.0)
    num = np.zeros_like(f)
    den = np.zeros_like(f) + 1e-6
    for g, t in zip(gam, times):
        e = np.clip(f ** g, 0, 1)
        w = 1.0 - np.abs(2 * e - 1) ** 8
        num += w * (e ** 2.2) / t
        den += w
    return (num / den).astype(np.float32)


def tonemap(frame, method="drago", gamma_out=1.0, sat=1.15):
    """تحويل نغمي (Tone Mapping) احترافي بخوارزميات OpenCV: Drago / Reinhard / Mantiuk"""
    hdr = _pseudo_hdr(frame)
    hdr = hdr / max(float(np.exp(np.log(hdr + 1e-4).mean())), 1e-4) * 0.18  # نطبّع المتوسط الهندسي إلى رمادي 18%
    if method == "reinhard":
        tm = cv2.createTonemapReinhard(1.0, 0.0, 0.0, 0.0)
    elif method == "mantiuk":
        tm = cv2.createTonemapMantiuk(1.0, 0.8, 1.0)
    else:
        tm = cv2.createTonemapDrago(1.0, 1.0, 0.85)
    ldr = np.nan_to_num(tm.process(hdr.copy()))
    ldr = np.clip(ldr / max(float(np.percentile(ldr, 99.5)), 1e-3), 0, 1)
    m = float(np.clip(ldr.mean(), 0.05, 0.95))
    k = float(np.clip(np.log(0.46) / np.log(m), 0.55, 1.25)) * gamma_out
    out = _u(ldr ** k)
    return saturation(clahe(out, 1.3, 8, 0.4), sat)


# ---------------- دقة الآيفون ----------------
import os
import threading
from pathlib import Path

_MODELS = Path(__file__).parent / "models"
_tls = threading.local()


def _sr_model(scale: int):
    """شبكة تكبير خفيفة (ESPCN) لكل خيط — تُستخدم إن توفّر opencv-contrib والملف"""
    key = f"sr{scale}"
    m = getattr(_tls, key, None)
    if m is None:
        m = False
        f = _MODELS / f"ESPCN_x{scale}.pb"
        if f.exists() and hasattr(cv2, "dnn_superres"):
            try:
                m = cv2.dnn_superres.DnnSuperResImpl_create()
                m.readModel(str(f))
                m.setModel("espcn", scale)
            except Exception:
                m = False
        setattr(_tls, key, m)
    return m or None


def upscale_smart(frame, target_short=1080):
    """تكبير حتى الجانب القصير = target_short: شبكة ESPCN ×2/×3 ثم تصغير بالمساحة (supersampling)، وإلا Lanczos"""
    h, w = frame.shape[:2]
    s = min(w, h)
    if s >= target_short:
        return frame
    need = target_short / s
    scale = 2 if need <= 2 else 3
    m = _sr_model(scale)
    up = m.upsample(frame) if m is not None else cv2.resize(frame, (w * scale, h * scale), interpolation=cv2.INTER_LANCZOS4)
    k = target_short / min(up.shape[:2])
    if k < 1:
        up = cv2.resize(up, (int(up.shape[1] * k) // 2 * 2, int(up.shape[0] * k) // 2 * 2), interpolation=cv2.INTER_AREA)
    return up


def _fusion_soft(frame, w=0.35):
    """دمج تعريضات (Mertens) بدون CLAHE — يرفع الظلال ويحمي الإضاءات بلا تشريط على التدرجات"""
    f = _f(frame)
    exps = [_u(np.clip(f ** g, 0, 1)) for g in (0.6, 1.0, 1.6)]
    fused = np.clip(np.nan_to_num(cv2.createMergeMertens(1.0, 1.0, 1.0).process(exps), nan=0.5), 0, 1)
    return _u(f * (1 - w) + fused * w)


def iphone(frame, strength=1.0, target_short=1080):
    """مظهر «كاميرا آيفون»: تنظيف ضغط خفيف + تكبير ذكي (ESPCN) + HDR ذكي ناعم + ألوان طبيعية + حدة دقيقة + حبيبات دقيقة تمنع التشريط"""
    h, w = frame.shape[:2]
    # 1) تنظيف خفيف لآثار الضغط (لا نمسح الملمس)
    f = cv2.bilateralFilter(frame, 5, 14 if min(w, h) > 900 else 18, 4)
    # 2) تكبير ذكي
    f = upscale_smart(f, target_short)
    # 3) Smart HDR ناعم (بدون تباين محلي عنيف)
    f = _fusion_soft(f, 0.38 * strength)
    # 4) ألوان طبيعية: توازن أبيض جزئي + دفء خفيف + حيوية معتدلة
    f = white_balance(f, 0.3)
    fl = f.astype(np.float32)
    fl[..., 2] *= 1.02
    fl[..., 0] *= 0.98
    f = vibrance(np.clip(fl, 0, 255).astype(np.uint8), 0.28 * strength)
    # 5) حدة دقيقة نصف قطر صغير فقط (تفاصيل بدون هالات)
    f = sharpen(f, 0.45 * strength, 0.9, 0.12 * strength, 2.0)
    # 6) منحنى نغمي ناعم: كتف للإضاءات + تباين خفيف
    x = f.astype(np.float32) / 255.0
    x = np.where(x > 0.86, 0.86 + (x - 0.86) * 0.72, x)
    x = x + 0.035 * strength * np.sin(np.pi * (x - 0.5)) * (x * (1 - x)) * 4
    # 7) حبيبات دقيقة جداً (dithering) تمنع ظهور التشريط على البشرة والسماء
    x = x + np.random.default_rng().normal(0, 1.1 / 255, x.shape).astype(np.float32)
    return _u(x)


CV_FUNCS = {
    "cv_iphone": (lambda f: iphone(f, 1.0, 1080), "📱 دقة الآيفون (Full HD)"),
    "cv_iphone_2k": (lambda f: iphone(f, 1.1, 1440), "📱 دقة الآيفون برو (2K)"),
    "cv_tm_drago": (lambda f: tonemap(f, "drago"), "تحويل نغمي HDR (Drago)"),
    "cv_tm_reinhard": (lambda f: tonemap(f, "reinhard"), "تحويل نغمي HDR (Reinhard)"),
    "cv_tm_mantiuk": (lambda f: tonemap(f, "mantiuk", 1.0, 1.2), "تحويل نغمي HDR (Mantiuk) تفاصيل أعلى"),
    "cv_hdr": (lambda f: hdr_fusion(f, 0.9), "HDR واقعي (دمج تعريضات)"),
    "cv_hdr_max": (lambda f: vibrance(sharpen(hdr_fusion(f, 1.5), 0.5), 0.5), "HDR أقوى + وضوح"),
    "cv_clahe": (lambda f: clahe(f, 2.2, 8, 0.9), "تباين محلي ذكي (CLAHE)"),
    "cv_lowlight": (lambda f: retinex(f, 0.7), "إضاءة الظلام (Retinex)"),
    "cv_dehaze": (dehaze, "إزالة الضباب (Dark Channel)"),
    "cv_denoise": (denoise, "تنقية التشويش (NLM)"),
    "cv_detail": (lambda f: detail(f, 1.6), "تضخيم التفاصيل"),
    "cv_wb": (white_balance, "توازن أبيض تلقائي"),
    "cv_sharp": (lambda f: sharpen(f, 0.9, 1.0, 0.45, 3.0), "توضيح متعدد المقاييس"),
    "cv_super2": (lambda f: super_res(f, 2.0), "تكبير 2× + تنقية + توضيح"),
    "cv_filmic": (filmic, "ألوان فيلمية (ACES)"),
    "cv_ultra": (ultra, "واقعية فائقة (كل الأدوات معاً)"),
}


# نسخ المعاينة السريعة: بدون تكبير (المعاينة على نسخة مصغّرة)، النتيجة النهائية تستخدم النسخ الكاملة
PREVIEW_FUNCS = {
    "cv_iphone": lambda f: iphone(f, 1.0, 0),
    "cv_iphone_2k": lambda f: iphone(f, 1.1, 0),
    "cv_super2": lambda f: sharpen(denoise_fast(f), 0.9, 1.0, 0.35, 3.0),
}

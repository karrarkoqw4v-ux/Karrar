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


CV_FUNCS = {
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

"""أدوات مونتاج الصور: خلفيات (إزالة/تضبيب/إخفاء/استبدال) + تحسين + فلاتر + تأثيرات فنية + إطارات + تحويل.
كل أداة دالة تأخذ (img BGR uint8, alpha float|None) وتُرجع (img, alpha). الأحجام النسبية تعتمد على min(h,w) فتعمل على المعاينة والأصل."""
import hashlib
from pathlib import Path

import cv2
import numpy as np
from PIL import Image, ImageDraw, ImageOps

import art
import cvlib

BASE = Path(__file__).parent
MODELS = BASE / "models"
cv2.setNumThreads(2)

# ---------------------------------------------------------------- القناع (فصل الشخص/الجسم عن الخلفية)
_sess = None
_mcache: dict = {}


def _u2net(img):
    global _sess
    if _sess is None:
        import onnxruntime as ort
        _sess = ort.InferenceSession(str(MODELS / "u2netp.onnx"), providers=["CPUExecutionProvider"])
    h, w = img.shape[:2]
    x = cv2.cvtColor(cv2.resize(img, (320, 320), interpolation=cv2.INTER_AREA), cv2.COLOR_BGR2RGB).astype(np.float32)
    x = x / max(float(x.max()), 1e-6)
    x = (x - np.array([0.485, 0.456, 0.406], np.float32)) / np.array([0.229, 0.224, 0.225], np.float32)
    out = _sess.run(None, {_sess.get_inputs()[0].name: x.transpose(2, 0, 1)[None]})[0][0, 0]
    out = (out - out.min()) / max(float(out.max() - out.min()), 1e-6)
    return cv2.resize(out, (w, h), interpolation=cv2.INTER_CUBIC)


def _grabcut(img):
    h, w = img.shape[:2]
    s = 480.0 / max(h, w)
    sm = cv2.resize(img, (int(w * s), int(h * s))) if s < 1 else img.copy()
    m = np.zeros(sm.shape[:2], np.uint8)
    rect = (int(sm.shape[1] * .08), int(sm.shape[0] * .06), int(sm.shape[1] * .84), int(sm.shape[0] * .88))
    bg, fg = np.zeros((1, 65)), np.zeros((1, 65))
    cv2.grabCut(sm, m, rect, bg, fg, 4, cv2.GC_INIT_WITH_RECT)
    out = np.where((m == 1) | (m == 3), 1.0, 0.0).astype(np.float32)
    return cv2.resize(out, (w, h))


def mask(img) -> np.ndarray:
    """قناع ناعم 0..1 للجسم الرئيسي (يعمل بنموذج U²-Net الصغير، وبديل GrabCut عند غياب onnxruntime)"""
    key = (img.shape, hashlib.md5(cv2.resize(img, (48, 48), interpolation=cv2.INTER_AREA).tobytes()).hexdigest())
    if key in _mcache:
        return _mcache[key]
    try:
        m = _u2net(img)
    except Exception:  # noqa: BLE001
        m = _grabcut(img)
    m = np.clip((m - 0.25) / 0.5, 0, 1)
    m = m * m * (3 - 2 * m)  # smoothstep: حواف أنظف
    try:
        m = cv2.ximgproc.guidedFilter(img, m.astype(np.float32), max(4, min(img.shape[:2]) // 120), 1e-3)
    except Exception:  # noqa: BLE001
        m = cv2.GaussianBlur(m, (0, 0), max(0.8, min(img.shape[:2]) / 700))
    m = np.clip(m, 0, 1).astype(np.float32)
    if len(_mcache) > 6:
        _mcache.clear()
    _mcache[key] = m
    return m


# ---------------------------------------------------------------- السجل
class Tool:
    def __init__(self, id, cat, title, fn, alpha=False, steps=None, desc=""):
        self.id, self.cat, self.title, self.fn, self.alpha, self.steps, self.desc = id, cat, title, fn, alpha, steps or [], desc


TOOLS: dict[str, Tool] = {}
CATS = {
    "bg": ("layers", "🖼", "الخلفية: إزالة • تضبيب • إخفاء"),
    "enh": ("gem", "💎", "تحسين الجودة والوضوح"),
    "flt": ("brush", "🎨", "فلاتر وألوان"),
    "art": ("star", "✨", "تأثيرات فنية"),
    "lay": ("grid", "▦", "إطارات وقياسات"),
    "trf": ("move", "↔", "تحويل وقص"),
    "txt": ("chat", "💬", "نصوص وعلامة مائية"),
    "pro": ("gem", "🏆", "أدوات المصممين المحترفين"),
}


def tool(id, cat, title, alpha=False, steps=None, desc=""):
    def deco(fn):
        TOOLS[id] = Tool(id, cat, title, fn, alpha, steps, desc)
        return fn
    return deco


def by_cat(cat):
    return [t for t in TOOLS.values() if t.cat == cat]


def _S(img):
    return min(img.shape[:2])


def _f(img):
    return img.astype(np.float32) / 255.0


def _u(x):
    return np.clip(x * 255.0 + 0.5, 0, 255).astype(np.uint8)


def _m3(m):
    return m[..., None]


def comp(fg, bg, m):
    return _u(_f(fg) * _m3(m) + _f(bg) * (1 - _m3(m)))


def blur(img, frac):
    k = max(1.0, _S(img) * frac)
    return cvlib._blur_big(img, k) if k > 6 else cv2.GaussianBlur(img, (0, 0), k)


def disc_blur(img, frac):
    """تمويه عدسي دائري (بوكيه) أجمل من الغاوسي"""
    r = max(2, int(_S(img) * frac))
    sc = 1.0
    if r > 28:
        sc = 28.0 / r
        small = cv2.resize(img, None, fx=sc, fy=sc, interpolation=cv2.INTER_AREA)
        r = 28
    else:
        small = img
    k = np.zeros((2 * r + 1, 2 * r + 1), np.float32)
    cv2.circle(k, (r, r), r, 1.0, -1)
    k /= k.sum()
    x = _f(small)
    hl = np.clip((x - 0.75) * 3, 0, 1)  # إضاءات مضاعفة لتظهر بقع بوكيه
    out = cv2.filter2D(x + hl * 0.8, -1, k)
    out = np.clip(out, 0, 1)
    out = _u(out)
    return cv2.resize(out, (img.shape[1], img.shape[0]), interpolation=cv2.INTER_CUBIC) if sc != 1.0 else out


def grad_bg(shape, c1, c2, angle=90):
    h, w = shape[:2]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    a = np.deg2rad(angle)
    t = (xx / w * np.cos(a) + yy / h * np.sin(a))
    t = (t - t.min()) / max(t.max() - t.min(), 1e-6)
    t = t[..., None]
    return _u(np.array(c1, np.float32) / 255 * (1 - t) + np.array(c2, np.float32) / 255 * t)


def solid(shape, bgr):
    return np.full(shape, bgr, np.uint8)


def vignette_map(img, power=1.6, strength=0.6):
    h, w = img.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    d = np.sqrt(((xx - w / 2) / (w / 2)) ** 2 + ((yy - h / 2) / (h / 2)) ** 2) / 1.414
    return 1 - strength * np.clip(d, 0, 1) ** power


def lut_apply(img, lut):
    return cv2.LUT(img, lut)


def curve(points):
    xs, ys = zip(*points)
    return np.clip(np.interp(np.arange(256), np.array(xs) * 255, np.array(ys) * 255), 0, 255).astype(np.uint8)


def grade(img, sh=(0, 0, 0), mid=(0, 0, 0), hi=(0, 0, 0), sat=1.0, con=1.0, bright=0.0, gamma=1.0):
    """تلوين: sh/mid/hi = إزاحة (B,G,R) للظلال/المتوسطات/الإضاءات (قيم صغيرة -0.2..0.2)"""
    x = _f(img)
    lum = (0.114 * x[..., 0] + 0.587 * x[..., 1] + 0.299 * x[..., 2])[..., None]
    ws = (1 - lum) ** 2
    wh = lum ** 2
    wm = 1 - ws - wh
    x = x + ws * np.array(sh, np.float32) + wm * np.array(mid, np.float32) + wh * np.array(hi, np.float32)
    x = np.clip(x, 0, 1) ** (1 / gamma)
    x = (x - 0.5) * con + 0.5 + bright
    if sat != 1.0:
        g = (0.114 * x[..., 0] + 0.587 * x[..., 1] + 0.299 * x[..., 2])[..., None]
        x = g + (x - g) * sat
    return _u(x)


def hsv_sat(img, k):
    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV).astype(np.float32)
    hsv[..., 1] = np.clip(hsv[..., 1] * k, 0, 255)
    return cv2.cvtColor(hsv.astype(np.uint8), cv2.COLOR_HSV2BGR)


def gray3(img):
    return cv2.cvtColor(cv2.cvtColor(img, cv2.COLOR_BGR2GRAY), cv2.COLOR_GRAY2BGR)


def grain(img, amt=0.04):
    n = np.random.default_rng(7).normal(0, amt, img.shape[:2]).astype(np.float32)
    return _u(_f(img) + n[..., None])


def screen(a, b):
    return _u(1 - (1 - _f(a)) * (1 - _f(b)))


# ================================================================ الخلفية
@tool("bg_remove", "bg", "إزالة الخلفية (PNG شفاف)", alpha=True, desc="يفصل الشخص/الجسم الرئيسي ويحذف الخلفية؛ التصدير يكون PNG شفافاً.")
def bg_remove(img, a):
    return img, mask(img) if a is None else a * mask(img)


def _bgfx(name, title, fn, desc=""):
    @tool(name, "bg", title, desc=desc)
    def _f2(img, a, _fn=fn):
        m = mask(img)
        return comp(img, _fn(img), m), a
    return _f2


_bgfx("bg_blur_soft", "تضبيب الخلفية (ناعم)", lambda i: blur(i, 0.012))
_bgfx("bg_blur", "تضبيب الخلفية (متوسط)", lambda i: blur(i, 0.025))
_bgfx("bg_blur_max", "تضبيب الخلفية (قوي)", lambda i: blur(i, 0.055))
_bgfx("bg_bokeh", "وضع البورتريه (بوكيه عدسة)", lambda i: disc_blur(i, 0.03), "تمويه عدسي دائري مع بقع ضوء كالكاميرا الاحترافية.")
_bgfx("bg_white", "خلفية بيضاء", lambda i: solid(i.shape, (255, 255, 255)))
_bgfx("bg_black", "خلفية سوداء", lambda i: solid(i.shape, (0, 0, 0)))
_bgfx("bg_green", "خلفية خضراء (كروما)", lambda i: solid(i.shape, (60, 200, 40)))
_bgfx("bg_blue", "خلفية زرقاء", lambda i: solid(i.shape, (200, 110, 30)))
_bgfx("bg_red", "خلفية حمراء", lambda i: solid(i.shape, (40, 40, 200)))
_bgfx("bg_sunset", "خلفية تدرج غروب", lambda i: grad_bg(i.shape, (60, 40, 200), (40, 170, 255), 90))
_bgfx("bg_neon", "خلفية نيون بنفسجي", lambda i: grad_bg(i.shape, (200, 40, 150), (255, 160, 20), 60))
_bgfx("bg_ocean", "خلفية تدرج بحري", lambda i: grad_bg(i.shape, (120, 60, 10), (220, 200, 90), 90))
_bgfx("bg_studio", "خلفية استوديو رمادية", lambda i: _u(_f(solid(i.shape, (150, 150, 150))) * vignette_map(i, 1.4, 0.55)[..., None]))
_bgfx("bg_dark", "تعتيم الخلفية (التركيز على الشخص)", lambda i: _u(_f(i) * 0.25))
_bgfx("bg_bw", "الشخص بالألوان والخلفية أبيض وأسود", gray3, "Color Splash.")
_bgfx("bg_desat_blur", "خلفية باهتة مضببة (سينمائي)", lambda i: _u(_f(blur(gray3(i), 0.03)) * 0.7))
_bgfx("bg_pixel", "إخفاء الخلفية بالبكسلة", lambda i: _pixelate(i, 0.03))
_bgfx("bg_mosaic", "إخفاء الخلفية بموزاييك كبير", lambda i: _pixelate(i, 0.07))
_bgfx("bg_zoom", "خلفية بضبابية سرعة (زوم)", lambda i: _zoomblur(i, 0.12))
_bgfx("bg_sketch", "خلفية رسم رصاص", lambda i: _sketch(i))
_bgfx("bg_vivid_blur", "خلفية ملونة مضببة فاقعة", lambda i: hsv_sat(blur(i, 0.03), 1.8))
_bgfx("bg_mirror", "خلفية معكوسة مضببة", lambda i: blur(i[:, ::-1], 0.02))


def _hide(name, title, fn):
    @tool(name, "bg", title)
    def _f2(img, a, _fn=fn):
        m = mask(img)
        return comp(_fn(img), img, m), a
    return _f2


_hide("hide_blur", "إخفاء الشخص/الجسم بالتضبيب", lambda i: blur(i, 0.05))
_hide("hide_pixel", "إخفاء الشخص/الجسم بالبكسلة", lambda i: _pixelate(i, 0.04))
_hide("hide_black", "إخفاء الشخص كظل أسود", lambda i: _u(_f(i) * 0.0))


@tool("bg_sticker", "bg", "ستيكر بحدّ أبيض (شفاف)", alpha=True, desc="يقص الجسم ويضيف إطاراً أبيض كالملصق.")
def bg_sticker(img, a):
    m = mask(img) if a is None else a
    k = max(3, int(_S(img) * 0.012)) | 1
    grow = cv2.dilate((m > 0.4).astype(np.uint8), cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (2 * k + 1, 2 * k + 1)))
    grow = cv2.GaussianBlur(grow.astype(np.float32), (0, 0), 1.2)
    out = comp(img, solid(img.shape, (255, 255, 255)), m)
    return out, np.clip(np.maximum(grow, m), 0, 1)


@tool("bg_outline_glow", "bg", "توهج نيون حول الشخص")
def bg_outline_glow(img, a):
    m = mask(img)
    k = max(3, int(_S(img) * 0.01))
    edge = cv2.dilate((m > 0.5).astype(np.uint8), np.ones((k, k), np.uint8)) - cv2.erode((m > 0.5).astype(np.uint8), np.ones((k, k), np.uint8))
    glow = cv2.GaussianBlur(edge.astype(np.float32), (0, 0), _S(img) * 0.012)
    col = np.array([255, 120, 40], np.float32) / 255
    dark = _f(img) * 0.55
    out = dark * (1 - _m3(m) * 0.0) + _f(img) * _m3(m) * 0.45
    out = np.clip(out + glow[..., None] * col * 2.2 + edge[..., None].astype(np.float32) * np.array([1, 1, 1], np.float32) * 0.9, 0, 1)
    return _u(out), a


@tool("bg_shadow", "bg", "ظل ناعم على خلفية بيضاء")
def bg_shadow(img, a):
    m = mask(img)
    off = int(_S(img) * 0.02)
    sh = np.roll(np.roll(cv2.GaussianBlur(m, (0, 0), _S(img) * 0.02), off, 0), off, 1)
    bg = _u(np.ones_like(_f(img)) * (1 - 0.55 * sh[..., None]))
    return comp(img, bg, m), a


def _pixelate(img, frac):
    h, w = img.shape[:2]
    b = max(4, int(_S(img) * frac))
    return cv2.resize(cv2.resize(img, (max(1, w // b), max(1, h // b)), interpolation=cv2.INTER_AREA), (w, h), interpolation=cv2.INTER_NEAREST)


def _zoomblur(img, amt):
    h, w = img.shape[:2]
    acc = _f(img)
    n = 10
    for i in range(1, n):
        s = 1 + amt * i / n
        M = cv2.getRotationMatrix2D((w / 2, h / 2), 0, s)
        acc += _f(cv2.warpAffine(img, M, (w, h), flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_REFLECT))
    return _u(acc / n)


def _sketch(img):
    g = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    inv = 255 - cv2.GaussianBlur(g, (0, 0), max(2, _S(img) * 0.008))
    s = cv2.divide(g, 255 - inv + 1, scale=256)
    return cv2.cvtColor(s, cv2.COLOR_GRAY2BGR)


# ================================================================ التحسين
def _enh(id, title, fn, desc=""):
    @tool(id, "enh", title, desc=desc)
    def _f2(img, a, _fn=fn):
        return _fn(img), a
    return _f2


def _auto(img):
    lab = cv2.cvtColor(img, cv2.COLOR_BGR2LAB)
    l, a, b = cv2.split(lab)
    lo, hi = np.percentile(l, [0.8, 99.2])
    l = np.clip((l.astype(np.float32) - lo) * 255 / max(hi - lo, 40), 0, 255).astype(np.uint8)
    l = cv2.createCLAHE(1.6, (8, 8)).apply(l)
    out = cv2.cvtColor(cv2.merge([l, a, b]), cv2.COLOR_LAB2BGR)
    return cvlib.sharpen(cvlib.vibrance(out, 0.35), 0.4, 1.0, 0.1, 2.0)


def _skin(img, strength=1.0):
    """تنعيم البشرة بفصل التردد: نعومة اللون مع الحفاظ على الملمس"""
    s = _S(img)
    low = cv2.bilateralFilter(img, 0, 22 * strength + 8, max(3, s * 0.008))
    low = cv2.bilateralFilter(low, 0, 18 * strength + 6, max(3, s * 0.012))
    detail = _f(img) - _f(cv2.GaussianBlur(img, (0, 0), max(1.5, s * 0.004)))
    out = _f(low) + detail * 0.65
    ycc = cv2.cvtColor(img, cv2.COLOR_BGR2YCrCb)
    skin = ((ycc[..., 1] > 135) & (ycc[..., 1] < 180) & (ycc[..., 2] > 85) & (ycc[..., 2] < 135)).astype(np.float32)
    skin = cv2.GaussianBlur(skin, (0, 0), max(2, s * 0.01))[..., None]
    return _u(_f(img) * (1 - skin) + np.clip(out, 0, 1) * skin)


_enh("auto", "تحسين تلقائي ذكي", _auto, "يضبط التباين والسطوع والألوان والحدة تلقائياً.")
_enh("iphone", "دقة الآيفون", lambda i: cvlib.iphone(i, 1.0, 0), "HDR ذكي ناعم + ألوان طبيعية + حدة دقيقة.")
_enh("iphone_up", "دقة الآيفون + تكبير ذكي", lambda i: cvlib.iphone(i, 1.0, max(1080, _S(i) * 2)))
_enh("hdr", "HDR واقعي", lambda i: cvlib.hdr_fusion(i, 0.9))
_enh("hdr_max", "HDR أقوى + وضوح", lambda i: cvlib.vibrance(cvlib.sharpen(cvlib.hdr_fusion(i, 1.5), 0.5), 0.5))
_enh("tm_drago", "تحويل نغمي HDR (Drago)", lambda i: cvlib.tonemap(i, "drago"))
_enh("tm_mantiuk", "تحويل نغمي HDR تفاصيل أعلى", lambda i: cvlib.tonemap(i, "mantiuk", 1.0, 1.2))
_enh("clahe", "تباين محلي ذكي", lambda i: cvlib.clahe(i, 2.2, 8, 0.9))
_enh("lowlight", "إنارة الصور المظلمة", lambda i: cvlib.retinex(i, 0.7))
_enh("dehaze", "إزالة الضباب", cvlib.dehaze)
_enh("denoise", "تنقية التشويش", lambda i: cvlib.denoise(i, 4.0))
_enh("sharp", "توضيح ناعم", lambda i: cvlib.sharpen(i, 0.6, 1.0, 0.25, 2.5))
_enh("sharp_max", "توضيح قوي", lambda i: cvlib.sharpen(i, 1.1, 1.0, 0.6, 3.0))
_enh("detail", "تضخيم التفاصيل", lambda i: cvlib.detail(i, 1.6))
_enh("wb", "توازن أبيض تلقائي", cvlib.white_balance)
_enh("vibrance", "حيوية الألوان", lambda i: cvlib.vibrance(i, 0.7))
_enh("brighten", "رفع السطوع", lambda i: _u(np.clip(_f(i) ** 0.8, 0, 1)))
_enh("shadows", "رفع الظلال", lambda i: _u(np.clip(_f(i) + (1 - _f(i)) ** 3 * 0.3, 0, 1)))
_enh("contrast", "تباين قوي", lambda i: grade(i, con=1.25, sat=1.1))
_enh("skin", "تنعيم البشرة (طبيعي)", lambda i: _skin(i, 1.0), "تنعيم بفصل التردد يحفظ ملمس الجلد.")
_enh("skin_max", "تنعيم البشرة (قوي)", lambda i: _skin(i, 2.0))
_enh("upscale2", "تكبير 2× بجودة عالية", lambda i: cvlib.super_res(i, 2.0))
_enh("ultra", "واقعية فائقة (كل الأدوات)", cvlib.ultra)
_enh("deblur", "تقليل الاهتزاز (Deblur)", lambda i: cvlib.sharpen(cv2.addWeighted(i, 1.6, cv2.GaussianBlur(i, (0, 0), 2.2), -0.6, 0), 0.5, 1.0, 0.1, 2.0))


# ================================================================ الفلاتر
def _flt(id, title, fn):
    @tool(id, "flt", title)
    def _f2(img, a, _fn=fn):
        return _fn(img), a
    return _f2


_flt("f_cin", "سينمائي تيل وبرتقالي", lambda i: grade(i, sh=(.08, .02, -.06), hi=(-.06, 0, .08), con=1.12, sat=1.1))
_flt("f_violet", "ليل بنفسجي", lambda i: grade(i, sh=(.12, -.04, .03), mid=(.06, -.03, .02), hi=(.03, 0, .01), con=1.1, sat=.9))
_flt("f_teal", "تيل داكن بارد", lambda i: grade(i, sh=(.05, .02, -.06), mid=(.04, .02, -.04), con=1.15, sat=.85, bright=-.03))
_flt("f_gold", "ذهبي دافئ", lambda i: grade(i, sh=(-.04, 0, .04), mid=(-.05, .01, .07), hi=(-.07, 0, .07), sat=1.1, con=1.05))
_flt("f_cool", "أزرق بارد", lambda i: grade(i, sh=(.08, .01, -.04), mid=(.06, 0, -.03), hi=(.04, 0, -.02), sat=1.0))
_flt("f_vintage", "قديم (فينتج)", lambda i: grain(grade(i, sh=(.03, .02, .0), mid=(-.02, .0, .05), hi=(-.04, .0, .05), sat=.7, con=.9, bright=.03), 0.03))
_flt("f_kodak", "كوداك دافئ", lambda i: grade(i, sh=(.0, .01, .03), mid=(-.03, .0, .06), hi=(-.05, .02, .06), sat=1.15, con=1.05))
_flt("f_bw", "أبيض وأسود", gray3)
_flt("f_bw_hi", "أبيض وأسود تباين عالٍ", lambda i: grade(gray3(i), con=1.45))
_flt("f_sepia", "سيبيا", lambda i: grade(gray3(i), mid=(-.15, .0, .12), hi=(-.1, .02, .1)))
_flt("f_matte", "مات باهت", lambda i: _u(np.clip(_f(i) * 0.82 + 0.1, 0, 1)))
_flt("f_pastel", "باستيل ناعم", lambda i: grade(i, sat=.75, con=.85, bright=.08))
_flt("f_vivid", "حيوي فاقع", lambda i: grade(i, sat=1.45, con=1.12))
_flt("f_cyber", "سايبر نيون", lambda i: grade(i, sh=(.18, -.05, .1), hi=(.05, .1, -.05), sat=1.35, con=1.15))
_flt("f_moody", "داكن مزاجي", lambda i: grade(i, con=1.2, sat=.85, bright=-.06, gamma=1.1))
_flt("f_fade", "باهت فيلمي", lambda i: grade(i, con=.8, sat=.9, bright=.06))
_flt("f_cross", "كروس بروسس", lambda i: grade(i, sh=(.1, -.02, -.05), hi=(-.08, .06, .08), sat=1.25, con=1.15))
_flt("f_duo_pink", "ثنائي لون أزرق-وردي", lambda i: _duotone(i, (140, 40, 20), (190, 140, 255)))
_flt("f_duo_sun", "ثنائي لون بنفسجي-برتقالي", lambda i: _duotone(i, (90, 20, 60), (60, 160, 255)))
_flt("f_sunset", "غروب", lambda i: grade(i, sh=(-.02, -.02, .06), mid=(-.06, .0, .08), hi=(-.08, .02, .08), sat=1.2))
_flt("f_forest", "غابة خضراء", lambda i: grade(i, sh=(.0, .05, -.02), mid=(-.02, .05, -.03), sat=1.1))
_flt("f_ice", "جليدي", lambda i: grade(i, sh=(.06, .02, -.04), hi=(.04, .03, .0), sat=.9, bright=.04))
_flt("f_neg", "نيجاتيف", lambda i: 255 - i)
_flt("f_posterize", "بوستر (ألوان محدودة)", lambda i: (i // 48) * 48 + 24)
_flt("f_thermal", "حراري", lambda i: cv2.applyColorMap(cv2.cvtColor(i, cv2.COLOR_BGR2GRAY), cv2.COLORMAP_INFERNO))


def _duotone(img, c_dark, c_light):
    g = _f(cv2.cvtColor(img, cv2.COLOR_BGR2GRAY))[..., None]
    return _u(np.array(c_dark, np.float32) / 255 * (1 - g) + np.array(c_light, np.float32) / 255 * g)


# ================================================================ التأثيرات الفنية
def _art(id, title, fn, desc=""):
    @tool(id, "art", title, desc=desc)
    def _f2(img, a, _fn=fn):
        return _fn(img), a
    return _f2


def _glow(img, k=0.9):
    b = cv2.GaussianBlur(np.clip((_f(img) - 0.55) * 2.2, 0, 1), (0, 0), max(3, _S(img) * 0.03))
    return _u(1 - (1 - _f(img)) * (1 - b * k))


def _lightleak(img):
    h, w = img.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    d = np.sqrt((xx / w - 1.0) ** 2 + (yy / h - 0.0) ** 2)
    g = np.clip(1 - d * 1.5, 0, 1)[..., None]
    col = np.array([40, 110, 255], np.float32) / 255
    return _u(1 - (1 - _f(img)) * (1 - g ** 1.5 * col * 0.9))


def _glitch(img):
    s = max(2, int(_S(img) * 0.012))
    out = img.copy()
    out[..., 2] = np.roll(img[..., 2], s, 1)
    out[..., 0] = np.roll(img[..., 0], -s, 1)
    h = img.shape[0]
    rng = np.random.default_rng(3)
    for _ in range(7):
        y = int(rng.integers(0, h - 4))
        t = int(rng.integers(3, max(4, h // 28)))
        out[y:y + t] = np.roll(out[y:y + t], int(rng.integers(-s * 6, s * 6)), 1)
    return out


def _vhs(img):
    g = _glitch(img)
    sc = img.copy()
    sc[::3] = _u(_f(sc[::3]) * 0.82)
    return grain(cv2.addWeighted(sc, 0.5, g, 0.5, 0), 0.05)


def _halftone(img):
    s = max(6, int(_S(img) * 0.012))
    g = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    h, w = g.shape
    out = np.full((h, w, 3), 255, np.uint8)
    for y in range(0, h - s, s):
        for x in range(0, w - s, s):
            v = g[y:y + s, x:x + s].mean() / 255
            r = int((1 - v) * s * 0.75)
            if r > 0:
                c = img[y:y + s, x:x + s].reshape(-1, 3).mean(0)
                cv2.circle(out, (x + s // 2, y + s // 2), r, tuple(int(k) for k in c), -1)
    return out


def _cartoon(img):
    s = _S(img)
    sm = cv2.bilateralFilter(cv2.pyrDown(img) if s > 900 else img, 9, 40, 9)
    sm = cv2.resize(sm, (img.shape[1], img.shape[0]))
    q = (sm // 28) * 28 + 14
    e = cv2.adaptiveThreshold(cv2.medianBlur(cv2.cvtColor(img, cv2.COLOR_BGR2GRAY), 7), 255, cv2.ADAPTIVE_THRESH_MEAN_C, cv2.THRESH_BINARY, 9, 6)
    return cv2.bitwise_and(q, q, mask=e)


def _oil(img):
    s = 640.0 / max(img.shape[:2])
    sm = cv2.resize(img, None, fx=s, fy=s) if s < 1 else img
    try:
        o = cv2.xphoto.oilPainting(sm, 5, 1)
    except Exception:  # noqa: BLE001
        o = cv2.stylization(sm, sigma_s=60, sigma_r=0.45)
    return cv2.resize(o, (img.shape[1], img.shape[0]), interpolation=cv2.INTER_CUBIC)


def _tiltshift(img):
    h = img.shape[0]
    b = blur(img, 0.02)
    yy = np.abs(np.linspace(-1, 1, h))[:, None]
    m = np.clip((yy - 0.25) / 0.5, 0, 1)
    m = np.repeat(m, img.shape[1], 1)
    return comp(b, img, m)


def _chroma(img):
    s = max(2, int(_S(img) * 0.008))
    out = img.copy()
    out[..., 2] = np.roll(img[..., 2], s, 1)
    out[..., 0] = np.roll(img[..., 0], -s, 1)
    return out


def _bokeh_lights(img):
    h, w = img.shape[:2]
    rng = np.random.default_rng(5)
    ov = np.zeros((h, w, 3), np.float32)
    for _ in range(28):
        c = (int(rng.integers(0, w)), int(rng.integers(0, h)))
        r = int(rng.integers(max(6, _S(img) // 40), max(12, _S(img) // 14)))
        col = [float(x) for x in rng.choice([[1.0, .85, .5], [1.0, .6, .8], [.6, .8, 1.0], [1, 1, 1]])]
        cv2.circle(ov, c, r, tuple(col[::-1]), -1)
    ov = cv2.GaussianBlur(ov, (0, 0), 2.0) * 0.45
    return _u(1 - (1 - _f(img)) * (1 - ov))


def _snow(img):
    h, w = img.shape[:2]
    rng = np.random.default_rng(9)
    ov = np.zeros((h, w), np.float32)
    for _ in range(int(w * h / 9000)):
        cv2.circle(ov, (int(rng.integers(0, w)), int(rng.integers(0, h))), int(rng.integers(1, max(2, _S(img) // 140))), 1.0, -1)
    ov = cv2.GaussianBlur(ov, (0, 0), 1.2)
    return _u(1 - (1 - _f(img)) * (1 - np.clip(ov * 1.4, 0, 1)[..., None]))


def _rain(img):
    h, w = img.shape[:2]
    rng = np.random.default_rng(11)
    ov = np.zeros((h, w), np.float32)
    L = max(10, _S(img) // 18)
    for _ in range(int(w * h / 3500)):
        x, y = int(rng.integers(0, w)), int(rng.integers(0, h))
        cv2.line(ov, (x, y), (x - L // 4, y + L), 1.0, 1)
    ov = cv2.GaussianBlur(ov, (0, 0), 0.8) * 0.5
    return _u(1 - (1 - _f(cvlib.saturation(img, 0.85)) * 0.88) * (1 - ov[..., None]))


def _stars(img):
    h, w = img.shape[:2]
    rng = np.random.default_rng(13)
    ov = np.zeros((h, w), np.float32)
    for _ in range(40):
        x, y = int(rng.integers(0, w)), int(rng.integers(0, h))
        r = int(rng.integers(max(3, _S(img) // 90), max(5, _S(img) // 32)))
        cv2.line(ov, (x - r, y), (x + r, y), 1.0, 1)
        cv2.line(ov, (x, y - r), (x, y + r), 1.0, 1)
        cv2.circle(ov, (x, y), max(1, r // 4), 1.0, -1)
    ov = cv2.GaussianBlur(ov, (0, 0), 1.0) * 1.3
    return _u(1 - (1 - _f(img)) * (1 - np.clip(ov, 0, 1)[..., None] * np.array([.9, .95, 1.0], np.float32)))


def _dreamy(img):
    b = cv2.GaussianBlur(img, (0, 0), max(4, _S(img) * 0.02))
    return grade(cv2.addWeighted(img, 0.6, b, 0.5, 0), sat=1.1, bright=.02)


def _dispersion_mirror(img):
    return _u((_f(img) + _f(img[:, ::-1])) * 0.5)


def _kaleido(img):
    h, w = img.shape[:2]
    q = img[:h // 2, :w // 2]
    top = np.hstack([q, q[:, ::-1]])
    return np.vstack([top, top[::-1]])[:h, :w] if top.shape[1] * 2 >= w else cv2.resize(np.vstack([top, top[::-1]]), (w, h))


def _double(img):
    s = int(_S(img) * 0.03)
    sh = np.roll(img, s, 1)
    return _u(np.maximum(_f(img), _f(sh) * 0.55))


def _mblur(img):
    k = max(5, int(_S(img) * 0.03)) | 1
    kern = np.zeros((k, k), np.float32)
    kern[k // 2] = 1.0 / k
    return cv2.filter2D(img, -1, kern)


_art("a_vignette", "تعتيم الأطراف", lambda i: _u(_f(i) * vignette_map(i, 1.5, 0.65)[..., None]))
_art("a_grain", "حبيبات فيلم", lambda i: grain(i, 0.05))
_art("a_glow", "توهج الإضاءات (Bloom)", _glow)
_art("a_leak", "تسرب ضوء دافئ", _lightleak)
_art("a_dreamy", "حالم ناعم", _dreamy)
_art("a_glitch", "غليتش RGB", _glitch)
_art("a_vhs", "VHS قديم", _vhs)
_art("a_chroma", "انحراف لوني (Chromatic)", _chroma)
_art("a_halftone", "هاف تون (نقاط)", _halftone)
_art("a_pixel", "بكسلة فنية", lambda i: _pixelate(i, 0.012))
_art("a_sketch", "رسم رصاص", _sketch)
_art("a_csketch", "رسم ملون", lambda i: cv2.pencilSketch(i, sigma_s=60, sigma_r=0.07, shade_factor=0.05)[1])
_art("a_cartoon", "كرتون", _cartoon)
_art("a_oil", "رسم زيتي", _oil)
_art("a_water", "ألوان مائية", lambda i: cv2.stylization(i, sigma_s=70, sigma_r=0.4))
_art("a_emboss", "نقش بارز", lambda i: cv2.cvtColor(np.clip(cv2.filter2D(cv2.cvtColor(i, cv2.COLOR_BGR2GRAY).astype(np.float32), -1, np.array([[-2, -1, 0], [-1, 1, 1], [0, 1, 2]], np.float32)) + 128, 0, 255).astype(np.uint8), cv2.COLOR_GRAY2BGR))
_art("a_tilt", "تصغير (Tilt-Shift)", _tiltshift)
_art("a_bokeh", "أضواء بوكيه", _bokeh_lights)
_art("a_snow", "ثلج", _snow)
_art("a_rain", "مطر", _rain)
_art("a_stars", "نجوم لامعة", _stars)
_art("a_double", "تعريض مزدوج", _double)
_art("a_mirror", "انعكاس متناظر", _dispersion_mirror)
_art("a_kaleido", "كاليدوسكوب", _kaleido)
_art("a_mblur", "ضبابية حركة", _mblur)
_art("a_zoom", "ضبابية زوم", lambda i: _zoomblur(i, 0.1))
_art("a_solar", "سولاريزيشن", lambda i: np.where(i < 128, i, 255 - i).astype(np.uint8))
_art("a_edge", "حواف نيون", lambda i: _u(np.clip(cv2.GaussianBlur(cv2.Canny(i, 60, 140), (0, 0), 1.3).astype(np.float32)[..., None] / 255 * np.array([1.0, .5, .1]) * 2 + _f(i) * 0.15, 0, 1)))


# ---- أدوات إضافية (جولة التحسين)
_enh("e_portrait", "بورتريه احترافي (تنعيم + تحسين)", lambda i: cvlib.sharpen(_skin(_auto(i), 0.8), 0.35, 1.0, 0.1, 2.0), "تحسين تلقائي مع تنعيم بشرة طبيعي ووضوح للعينين.")
_enh("e_food", "طعام شهي", lambda i: cvlib.vibrance(grade(cvlib.sharpen(i, 0.5, 1.0, 0.2, 2.5), con=1.12, sat=1.15, hi=(-.02, .0, .04)), 0.5))
_enh("e_night", "تحسين التصوير الليلي", lambda i: cvlib.sharpen(cvlib.denoise(cvlib.retinex(i, 0.6), 3.0), 0.5, 1.0, 0.15, 2.0))
_enh("e_landscape", "مناظر طبيعية HDR", lambda i: cvlib.vibrance(cvlib.dehaze(cvlib.hdr_fusion(i, 1.0), 0.6, 0.35), 0.5))
_enh("e_clean", "تنظيف وتنقية", lambda i: cvlib.sharpen(cv2.bilateralFilter(i, 7, 25, 7), 0.4, 1.0, 0.1, 2.0))
_enh("e_teeth_eyes", "إضاءة الوجه (Face Light)", lambda i: _facelight(i))

_flt("f_golden_hour", "ساعة ذهبية", lambda i: grade(i, sh=(-.04, .0, .06), mid=(-.08, .01, .1), hi=(-.1, .0, .1), sat=1.2, con=1.06))
_flt("f_moonlight", "ضوء القمر", lambda i: grade(i, sh=(.1, .0, -.06), mid=(.1, .0, -.06), hi=(.06, .0, -.02), sat=.8, con=1.1, bright=-.04))
_flt("f_academia", "أكاديمي داكن", lambda i: grade(i, sh=(-.02, .02, .05), mid=(-.05, .02, .06), sat=.75, con=1.12, bright=-.04))
_flt("f_tokyo", "نيون طوكيو", lambda i: grade(i, sh=(.2, -.08, .12), hi=(.1, -.02, -.04), sat=1.35, con=1.15))
_flt("f_clean", "ساطع نظيف", lambda i: grade(i, sat=1.1, con=1.08, bright=.05))
_flt("f_autumn", "خريفي", lambda i: grade(i, sh=(-.04, .02, .08), mid=(-.1, .02, .1), sat=1.2, con=1.05))
_flt("f_noir", "نوار سينمائي", lambda i: grain(grade(gray3(i), con=1.5, bright=-.04), 0.04))
_flt("f_dream_pink", "وردي حالم", lambda i: grade(cv2.addWeighted(i, 0.7, cv2.GaussianBlur(i, (0, 0), max(3, _S(i) * 0.015)), 0.4, 0), sh=(.02, -.02, .06), hi=(.02, .0, .05), sat=1.1, bright=.04))
_flt("f_summer", "صيفي مشمس", lambda i: grade(i, sh=(-.02, .02, .03), hi=(-.04, .03, .06), sat=1.3, con=1.08, bright=.03))
_flt("f_cold_blue", "أزرق ثلجي حاد", lambda i: grade(i, sh=(.12, .02, -.08), hi=(.08, .03, -.02), sat=.95, con=1.15))

_art("a_light_streak", "خطوط ضوء", lambda i: _streaks(i))
_art("a_softfocus", "تركيز ناعم (Soft Focus)", lambda i: _u(_f(i) * 0.65 + _f(cv2.GaussianBlur(i, (0, 0), max(3, _S(i) * 0.012))) * 0.5))
_art("a_sparkle", "لمعان وبريق", lambda i: _stars(_glow(i, 0.7)))
_art("a_fog", "ضباب سينمائي", lambda i: _fog(i))
_art("a_ghost", "ظل شبحي", lambda i: _u(np.clip(_f(i) * 0.8 + _f(np.roll(cv2.GaussianBlur(i, (0, 0), 4), int(_S(i) * 0.05), 1)) * 0.35, 0, 1)))
_art("a_split_tone", "تلوين ثنائي ناعم", lambda i: grade(i, sh=(.12, .0, .06), hi=(-.06, .03, .08), sat=1.1))
_art("a_dust", "غبار وخدوش فيلم", lambda i: _dust(i))
_art("a_neon_frame", "إطار نيون متوهج", lambda i: _neonframe(i))


def _facelight(img):
    h, w = img.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    d = np.sqrt(((xx - w * 0.5) / (w * 0.6)) ** 2 + ((yy - h * 0.38) / (h * 0.45)) ** 2)
    g = np.clip(1 - d, 0, 1)[..., None] ** 1.5
    return _u(np.clip(_f(img) * (1 + 0.28 * g) + 0.02 * g, 0, 1))


def _streaks(img):
    h, w = img.shape[:2]
    ov = np.zeros((h, w), np.float32)
    rng = np.random.default_rng(21)
    for _ in range(6):
        x0 = int(rng.integers(0, w))
        cv2.line(ov, (x0, 0), (x0 - int(h * 0.45), h), float(rng.uniform(0.5, 1)), max(2, _S(img) // 60))
    ov = cv2.GaussianBlur(ov, (0, 0), max(4, _S(img) * 0.02)) * 0.7
    col = np.array([.6, .85, 1.0], np.float32)
    return _u(1 - (1 - _f(img)) * (1 - ov[..., None] * col))


def _fog(img):
    h, w = img.shape[:2]
    rng = np.random.default_rng(33)
    n = cv2.resize(rng.random((max(4, h // 90), max(4, w // 90))).astype(np.float32), (w, h), interpolation=cv2.INTER_CUBIC)
    n = cv2.GaussianBlur(n, (0, 0), max(6, _S(img) * 0.03))
    n = (n - n.min()) / max(n.max() - n.min(), 1e-6)
    lift = (0.18 + 0.3 * n)[..., None]
    return _u(np.clip(_f(img) * (1 - lift * 0.55) + lift * 0.55 * np.array([.85, .88, .95], np.float32), 0, 1))


def _dust(img):
    h, w = img.shape[:2]
    rng = np.random.default_rng(44)
    ov = np.zeros((h, w), np.float32)
    for _ in range(int(w * h / 12000)):
        cv2.circle(ov, (int(rng.integers(0, w)), int(rng.integers(0, h))), int(rng.integers(1, max(2, _S(img) // 300 + 2))), 1.0, -1)
    for _ in range(6):
        x = int(rng.integers(0, w))
        cv2.line(ov, (x, 0), (x + int(rng.integers(-4, 4)), h), 0.35, 1)
    ov = cv2.GaussianBlur(ov, (0, 0), 0.7)
    return grain(_u(np.clip(_f(img) + ov[..., None] * 0.5, 0, 1)), 0.03)


def _neonframe(img):
    b = max(6, int(_S(img) * 0.018))
    out = img.copy()
    glow = np.zeros(img.shape[:2], np.float32)
    cv2.rectangle(glow, (b * 2, b * 2), (img.shape[1] - b * 2, img.shape[0] - b * 2), 1.0, max(2, b // 2))
    g = cv2.GaussianBlur(glow, (0, 0), b)
    col = np.array([1.0, 0.45, 0.9], np.float32)
    out = np.clip(_f(img) + g[..., None] * col * 1.4 + glow[..., None] * 0.9, 0, 1)
    return _u(out)


_art("a_spin_blur", "ضبابية دوّارة", lambda i: _spinblur(i))
_art("a_pixel_slice", "شرائح بكسل (غليتش)", lambda i: _slices(i))
_art("a_trail", "أثر حركة", lambda i: _u(np.clip((_f(i) + _f(np.roll(i, int(_S(i) * 0.02), 1)) * 0.6 + _f(np.roll(i, int(_S(i) * 0.04), 1)) * 0.35) / 1.95, 0, 1)))
_art("a_rgb_pop", "انقسام RGB قوي", lambda i: _rgbpop(i))
_flt("f_invert_soft", "نيجاتيف ناعم", lambda i: _u(1 - _f(i) * 0.85))


def _spinblur(img):
    h, w = img.shape[:2]
    acc = _f(img)
    n = 9
    for k in range(1, n):
        M = cv2.getRotationMatrix2D((w / 2, h / 2), k * 0.6, 1.0)
        acc += _f(cv2.warpAffine(img, M, (w, h), flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_REFLECT))
    blur_img = _u(acc / n)
    m = vignette_map(img, 1.2, 1.0)[..., None]
    return _u(_f(img) * m + _f(blur_img) * (1 - m))


def _slices(img):
    h, w = img.shape[:2]
    rng = np.random.default_rng(55)
    out = img.copy()
    for _ in range(14):
        y = int(rng.integers(0, h - 8))
        t = int(rng.integers(max(3, h // 80), max(6, h // 20)))
        out[y:y + t] = np.roll(out[y:y + t], int(rng.integers(-w // 10, w // 10)), 1)
    return _glitch(out)


def _rgbpop(img):
    s = max(3, int(_S(img) * 0.02))
    out = img.copy()
    out[..., 2] = np.roll(img[..., 2], s, 1)
    out[..., 0] = np.roll(img[..., 0], -s, 1)
    out[..., 1] = np.roll(img[..., 1], s, 0)
    return cvlib.sharpen(grade(out, sat=1.25, con=1.12), 0.4, 1.0, 0.1, 2.0)


_bgfx("bg_rose", "خلفية تدرج وردي", lambda i: grad_bg(i.shape, (150, 90, 230), (210, 170, 255), 70))
_bgfx("bg_gold", "خلفية تدرج ذهبي", lambda i: grad_bg(i.shape, (30, 90, 160), (110, 200, 250), 100))
_flt("f_teal_pop", "تيل بارز (ظلال تيل وبشرة دافئة)", lambda i: grade(i, sh=(.1, .05, -.08), mid=(.02, .0, .02), hi=(-.04, .0, .08), sat=1.15, con=1.12))
_enh("e_clarity", "وضوح محلي (Clarity)", lambda i: cvlib.sharpen(cvlib.clahe(i, 1.6, 8, 0.6), 0.5, 1.0, 0.2, 3.0))


_flt("f_mono_blue", "أحادي أزرق", lambda i: _duotone(i, (60, 20, 5), (255, 210, 150)))
_enh("e_sky", "تقوية السماء", lambda i: _sky(i))


def _sky(img):
    h = img.shape[0]
    top = np.clip(1.0 - np.linspace(0, 1.6, h), 0, 1)[:, None, None].astype(np.float32)
    hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV).astype(np.float32)
    blue = ((hsv[..., 0] > 90) & (hsv[..., 0] < 130)).astype(np.float32)[..., None] * top
    hsv[..., 1] = np.clip(hsv[..., 1] * (1 + 0.45 * blue[..., 0]), 0, 255)
    hsv[..., 2] = np.clip(hsv[..., 2] * (1 - 0.1 * blue[..., 0]), 0, 255)
    return cvlib.dehaze(cv2.cvtColor(hsv.astype(np.uint8), cv2.COLOR_HSV2BGR), 0.5, 0.4)


_art("a_focus_radial", "تركيز دائري (طمس الأطراف)", lambda i: _radial_focus(i))
_art("a_lens_vignette_warm", "عدسة دافئة بحواف داكنة", lambda i: _u(_f(grade(i, hi=(-.03, .0, .05), sat=1.08)) * vignette_map(i, 1.3, 0.5)[..., None]))


def _radial_focus(img):
    h, w = img.shape[:2]
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    d = np.sqrt(((xx - w / 2) / (w / 2)) ** 2 + ((yy - h / 2) / (h / 2)) ** 2)
    m = np.clip((d - 0.45) / 0.55, 0, 1)
    return comp(blur(img, 0.03), img, 1 - m)


def _halation(img):
    hl = np.clip((_f(img) - 0.62) * 2.6, 0, 1)
    g = cv2.GaussianBlur(hl, (0, 0), max(4, _S(img) * 0.018))
    lum = g.mean(axis=2, keepdims=True)
    red = lum * np.array([0.06, 0.2, 1.0], np.float32)
    return _u(1 - (1 - _f(img)) * (1 - np.clip(red * 1.2, 0, 1)))


_art("a_halation", "توهج أحمر فيلمي (Halation)", _halation)
_flt("f_expired", "فيلم منتهي الصلاحية", lambda i: grain(grade(i, sh=(.06, .08, -.02), mid=(.0, .03, .03), hi=(-.02, .04, .05), sat=.85, con=.88, bright=.05), 0.045))


def _prism(img):
    s = max(3, int(_S(img) * 0.014))
    out = img.copy().astype(np.float32)
    r = np.roll(img[..., 2], s, 1).astype(np.float32)
    b = np.roll(img[..., 0], -s, 1).astype(np.float32)
    out[..., 2] = r * 0.9 + out[..., 2] * 0.1
    out[..., 0] = b * 0.9 + out[..., 0] * 0.1
    glow = cv2.GaussianBlur(np.clip((_f(img) - 0.6) * 2, 0, 1), (0, 0), max(3, _S(img) * 0.02))
    return _u(np.clip(out / 255 + glow * 0.5, 0, 1))


_art("a_prism", "منشور ضوئي (Prism)", _prism)


_SHB = {"teal": (.10, .04, -.08), "blue": (.14, .0, -.06), "purple": (.12, -.06, .05), "green": (-.04, .10, -.04)}
_HIB = {"orange": (-.10, .02, .10), "gold": (-.10, .05, .08), "pink": (.04, -.03, .08), "white": (.03, .03, .03)}


@tool("pro_split_tone", "pro", "تلوين ثنائي احترافي (ظلال/إضاءات)", steps=[
    ("choice", "لون الظلال:", [("تيل", "teal"), ("أزرق", "blue"), ("بنفسجي", "purple"), ("أخضر", "green")], "sh"),
    ("choice", "لون الإضاءات:", [("برتقالي", "orange"), ("ذهبي", "gold"), ("وردي", "pink"), ("أبيض", "white")], "hi")])
def pro_split_tone(img, a, p=None):
    p = p or {}
    return grade(img, sh=_SHB.get(p.get("sh", "teal")), hi=_HIB.get(p.get("hi", "orange")), sat=1.08, con=1.05), a


def _pro(id, title, fn, desc=""):
    @tool(id, "pro", title, desc=desc)
    def _f2(img, a, _fn=fn):
        return _fn(img), a
    return _f2


def _dodge_burn(img):
    lab = cv2.cvtColor(img, cv2.COLOR_BGR2LAB).astype(np.float32)
    L = lab[..., 0]
    low = cv2.GaussianBlur(L, (0, 0), max(6, _S(img) * 0.03))
    lab[..., 0] = np.clip(L + (L - low) * 0.7, 0, 255)
    return cv2.cvtColor(lab.astype(np.uint8), cv2.COLOR_LAB2BGR)


def _luma_sharp(img):
    lab = cv2.cvtColor(img, cv2.COLOR_BGR2LAB)
    l, a_, b_ = cv2.split(lab)
    bl = cv2.GaussianBlur(l, (0, 0), max(1.0, _S(img) * 0.0016))
    l2 = cv2.addWeighted(l, 1.7, bl, -0.7, 0)
    return cv2.cvtColor(cv2.merge([l2, a_, b_]), cv2.COLOR_LAB2BGR)


_pro("pro_dodge_burn", "تباين محلي (Dodge & Burn)", _dodge_burn, "يبرز التفاصيل والأبعاد كما في الريتاتش الاحترافي.")
_pro("pro_luma_sharp", "حدة إضاءة فقط (Luma Sharpen)", _luma_sharp, "توضيح بدون هالات ألوان.")
_pro("pro_bleach", "تدريج ملوّن باهت (Bleach Bypass)", lambda i: grade(cvlib.sharpen(i, 0.4, 1.0, 0.1, 2.0), sat=.5, con=1.35, bright=-.03), "مظهر أفلام الأكشن: ألوان خافتة وتباين قوي.")
def _edge_burn(img):
    m = vignette_map(img, 1.1, 0.5)[..., None]
    return _u(_f(img) * m * 1.08)


_pro("pro_edge_burn", "حرق الأطراف الاحترافي (Edge Burn)", _edge_burn, "يوجّه النظر للمركز كما يفعل المصورون.")
_pro("pro_color_pop", "ألوان بارزة احترافية", lambda i: grade(cvlib.vibrance(i, 0.6), sat=1.12, con=1.1))
_pro("pro_cine_curve", "منحنى سينمائي (S-Curve)", lambda i: cv2.LUT(i, curve([(0, 0), (.25, .18), (.5, .5), (.75, .84), (1, 1)])))


PHOTO_PRO_PACKS = [
    ("🏆 بورتريه استوديو", ["e_portrait", "bg_studio", "pro_dodge_burn", "a_vignette"]),
    ("🛍 منتجات (خلفية بيضاء + ظل)", ["auto", "bg_shadow", "pro_luma_sharp"]),
    ("📱 ستوري احترافي", ["e_portrait", "pro_color_pop", "l_fit_story"]),
    ("🎬 سينمائي احترافي", ["pro_cine_curve", "pro_split_tone", "a_halation", "a_grain", "l_cinema"]),
    ("🌅 غروب مصممين", ["auto", "f_golden_hour", "pro_dodge_burn", "a_halation", "a_vignette"]),
    ("🌙 ليلي فني", ["e_night", "f_moonlight", "a_prism", "a_vignette"]),
    ("💎 دقة آيفون برو", ["iphone_up", "pro_luma_sharp", "pro_color_pop"]),
    ("🧊 ستيكر احترافي", ["e_portrait", "bg_sticker"]),
    ("🎨 نيون فني", ["f_tokyo", "a_halation", "a_rgb_pop", "a_neon_frame"]),
    ("🍂 دفء فاخر", ["f_autumn", "pro_dodge_burn", "a_softfocus"]),
    ("🌸 بورتريه وردي", ["bg_rose", "e_portrait", "a_sparkle", "pro_color_pop"]),
    ("📰 مجلة فاخرة", ["auto", "f_matte", "pro_luma_sharp", "l_b_white"]),
    ("💥 أكشن درامي", ["pro_bleach", "pro_dodge_burn", "a_vignette"]),
    ("🪩 حفلة نيون", ["f_tokyo", "a_light_streak", "a_sparkle", "a_neon_frame"]),
]


# ---- قوالب جاهزة للصور (بنقرة واحدة تركّب عدة أدوات)
PHOTO_TEMPLATES = [
    ("🧑 بورتريه احترافي", ["e_portrait", "a_vignette"]),
    ("🌅 غروب ذهبي", ["auto", "f_golden_hour", "a_glow", "a_vignette"]),
    ("🌙 ليلي بنفسجي", ["f_violet", "a_stars", "a_glow", "a_grain"]),
    ("🖤 نوار سينمائي", ["f_noir", "a_vignette"]),
    ("🌃 نيون طوكيو", ["f_tokyo", "a_glow", "a_chroma"]),
    ("📷 دقة الآيفون + إطار", ["iphone", "l_b_white"]),
    ("🎞 فيلم قديم", ["f_kodak", "a_grain", "a_dust", "a_vignette"]),
    ("💕 وردي حالم", ["f_dream_pink", "a_softfocus", "a_sparkle"]),
    ("🏔 طبيعة HDR", ["e_landscape", "f_summer"]),
    ("🍽 طعام شهي", ["e_food", "a_vignette"]),
    ("❄️ جليدي بارد", ["f_cold_blue", "a_snow"]),
    ("🎥 سينمائي تيل", ["f_cin", "a_grain", "l_cinema"]),
    ("🌧 مطر حزين", ["f_teal", "a_rain", "a_vignette"]),
    ("🍂 خريف دافئ", ["f_autumn", "a_softfocus", "a_vignette"]),
    ("✨ بورتريه بوكيه", ["bg_bokeh", "e_portrait", "a_glow"]),
    ("🧊 ستيكر شفاف", ["auto", "bg_sticker"]),
    ("📱 ستوري مضبب", ["auto", "l_fit_story"]),
    ("👑 ستوديو فاخر", ["e_portrait", "bg_studio", "a_vignette"]),
    ("🔥 نيون بإطار متوهج", ["f_cyber", "a_neon_frame"]),
    ("📸 مجلة أزياء", ["auto", "f_matte", "l_b_white"]),
    ("🌀 ريمكس سبين", ["f_cin", "a_spin_blur", "a_rgb_pop"]),
    ("👻 أثر وغليتش", ["f_cyber", "a_trail", "a_pixel_slice"]),
    ("🔁 نيجاتيف ناعم", ["f_moody", "f_invert_soft", "a_chroma"]),
    ("💥 بوب RGB", ["auto", "a_rgb_pop", "a_glow"]),
    ("☁️ سماء درامية", ["e_sky", "e_clarity", "a_vignette"]),
    ("🌸 بورتريه وردي", ["bg_rose", "e_portrait", "a_sparkle"]),
    ("🏅 بورتريه ذهبي", ["bg_gold", "e_portrait", "a_glow"]),
    ("🔵 أحادي أزرق فني", ["f_mono_blue", "a_grain", "a_vignette"]),
    ("🔺 منشور ضوئي فني", ["f_cin", "a_prism", "a_glow"]),
    ("🎬 هالة فيلمية", ["f_expired", "a_halation", "a_vignette"]),
]


# ================================================================ الإطارات والقياسات
def _lay(id, title, fn):
    @tool(id, "lay", title)
    def _f2(img, a, _fn=fn):
        out = _fn(img)
        if a is not None and out.shape[:2] != a.shape[:2]:
            a = None  # القياس تغيّر: الشفافية تُلغى
        return out, a
    return _f2


def _border(img, col, frac=0.03):
    b = max(4, int(_S(img) * frac))
    return cv2.copyMakeBorder(img, b, b, b, b, cv2.BORDER_CONSTANT, value=col)


def _polaroid(img):
    b = max(8, int(_S(img) * 0.04))
    out = cv2.copyMakeBorder(img, b, b * 4, b, b, cv2.BORDER_CONSTANT, value=(250, 250, 250))
    return out


def _rounded(img, frac=0.06):
    # يملأ الزوايا بالأبيض (الشفافية الحقيقية تُؤخذ من «إزالة الخلفية»)
    h, w = img.shape[:2]
    r = int(_S(img) * frac)
    m = np.zeros((h, w), np.uint8)
    cv2.rectangle(m, (r, 0), (w - r, h), 255, -1)
    cv2.rectangle(m, (0, r), (w, h - r), 255, -1)
    for cx, cy in ((r, r), (w - r, r), (r, h - r), (w - r, h - r)):
        cv2.circle(m, (cx, cy), r, 255, -1)
    m = cv2.GaussianBlur(m, (0, 0), 1.2).astype(np.float32) / 255
    return comp(img, solid(img.shape, (255, 255, 255)), m)


def _fit_ratio(img, rw, rh, blurfill=True):
    h, w = img.shape[:2]
    target = rw / rh
    if abs(w / h - target) < 0.01:
        return img
    if w / h > target:
        W, H = w, int(round(w / target))
    else:
        W, H = int(round(h * target)), h
    bg = cv2.resize(img, (W, H), interpolation=cv2.INTER_AREA)
    bg = blur(bg, 0.05) if blurfill else solid(bg.shape, (0, 0, 0))
    bg = _u(_f(bg) * 0.75)
    y, x = (H - h) // 2, (W - w) // 2
    bg[y:y + h, x:x + w] = img
    return bg


def _crop_ratio(img, rw, rh):
    h, w = img.shape[:2]
    target = rw / rh
    if w / h > target:
        nw = int(h * target)
        x = (w - nw) // 2
        return img[:, x:x + nw]
    nh = int(w / target)
    y = (h - nh) // 2
    return img[y:y + nh]


_lay("l_b_white", "إطار أبيض", lambda i: _border(i, (255, 255, 255)))
_lay("l_b_black", "إطار أسود", lambda i: _border(i, (0, 0, 0)))
_lay("l_b_gold", "إطار ذهبي", lambda i: _border(_border(i, (40, 170, 230), 0.012), (255, 255, 255), 0.02))
_lay("l_polaroid", "بولارويد", _polaroid)
_lay("l_round", "زوايا مدورة", _rounded)
_lay("l_fit_story", "ستوري 9:16 (خلفية مضببة)", lambda i: _fit_ratio(i, 9, 16))
_lay("l_fit_post", "منشور 4:5 (خلفية مضببة)", lambda i: _fit_ratio(i, 4, 5))
_lay("l_fit_sq", "مربع 1:1 (خلفية مضببة)", lambda i: _fit_ratio(i, 1, 1))
_lay("l_fit_wide", "عريض 16:9 (خلفية مضببة)", lambda i: _fit_ratio(i, 16, 9))
_lay("l_crop_sq", "قص مربع 1:1", lambda i: _crop_ratio(i, 1, 1))
_lay("l_crop_45", "قص 4:5", lambda i: _crop_ratio(i, 4, 5))
_lay("l_crop_story", "قص 9:16", lambda i: _crop_ratio(i, 9, 16))
_lay("l_crop_wide", "قص 16:9", lambda i: _crop_ratio(i, 16, 9))
_lay("l_cinema", "شريط سينمائي 2.35:1", lambda i: _crop_ratio(i, 2.35, 1))


# ================================================================ التحويل
@tool("t_rot_r", "trf", "تدوير 90° يمين")
def t_rot_r(img, a):
    return cv2.rotate(img, cv2.ROTATE_90_CLOCKWISE), None if a is None else cv2.rotate(a, cv2.ROTATE_90_CLOCKWISE)


@tool("t_rot_l", "trf", "تدوير 90° يسار")
def t_rot_l(img, a):
    return cv2.rotate(img, cv2.ROTATE_90_COUNTERCLOCKWISE), None if a is None else cv2.rotate(a, cv2.ROTATE_90_COUNTERCLOCKWISE)


@tool("t_flip_h", "trf", "قلب أفقي (مرآة)")
def t_flip_h(img, a):
    return img[:, ::-1].copy(), None if a is None else a[:, ::-1].copy()


@tool("t_flip_v", "trf", "قلب عمودي")
def t_flip_v(img, a):
    return img[::-1].copy(), None if a is None else a[::-1].copy()


@tool("t_up2", "trf", "تكبير الدقة 2× (حتى 4096)")
def t_up2(img, a):
    k = min(2.0, 4096 / max(img.shape[:2]))
    if k <= 1.05:
        return img, a
    out = cv2.resize(img, None, fx=k, fy=k, interpolation=cv2.INTER_CUBIC)
    return cvlib.sharpen(out, 0.5, 1.0, 0.15, 2.0), None if a is None else cv2.resize(a, (out.shape[1], out.shape[0]))


@tool("t_straight", "trf", "تعديل ميلان بسيط (+3°)")
def t_straight(img, a):
    h, w = img.shape[:2]
    M = cv2.getRotationMatrix2D((w / 2, h / 2), 3, 1.08)
    return cv2.warpAffine(img, M, (w, h), flags=cv2.INTER_CUBIC, borderMode=cv2.BORDER_REFLECT), a


# ================================================================ النصوص
def _text_draw(img, p):
    im = Image.fromarray(cv2.cvtColor(img, cv2.COLOR_BGR2RGB)).convert("RGBA")
    w, h = im.size
    size = max(14, int(min(w, h) * float(p.get("size", 0.07))))
    f = art.font(size)
    t = art.shape(str(p.get("text", "")))
    ov = Image.new("RGBA", im.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(ov)
    bb = d.textbbox((0, 0), t, font=f, **art._kw())
    tw, th = bb[2] - bb[0], bb[3] - bb[1]
    pos = p.get("pos", "bot")
    y = {"top": int(h * 0.06), "mid": (h - th) // 2, "bot": int(h * 0.88) - th}.get(pos, int(h * 0.88) - th)
    x = (w - tw) // 2
    col = p.get("col", (255, 255, 255))
    if p.get("wm"):
        x, y = w - tw - int(w * 0.03), h - th - int(h * 0.03)
        col = (255, 255, 255)
    sw = max(2, size // 12)
    d.text((x, y), t, font=f, fill=tuple(col) + (235,), stroke_width=sw, stroke_fill=(0, 0, 0, 255), **art._kw())
    out = Image.alpha_composite(im, ov).convert("RGB")
    return cv2.cvtColor(np.array(out), cv2.COLOR_RGB2BGR)


@tool("text", "txt", "إضافة نص", steps=[("text", "✍️ اكتب النص (عربي أو إنجليزي):"), ("choice", "مكان النص:", [("⬆️ أعلى", "top"), ("⏺ وسط", "mid"), ("⬇️ أسفل", "bot")], "pos")])
def t_text(img, a, p=None):
    return _text_draw(img, p or {"text": "نص", "pos": "bot"}), a


@tool("text_big", "txt", "نص كبير", steps=[("text", "✍️ اكتب النص:"), ("choice", "مكان النص:", [("⬆️ أعلى", "top"), ("⏺ وسط", "mid"), ("⬇️ أسفل", "bot")], "pos")])
def t_text_big(img, a, p=None):
    p = dict(p or {"text": "نص", "pos": "mid"}, size=0.13)
    return _text_draw(img, p), a


@tool("tag", "txt", "وسم / تاج تعديل (EDIT • REC • LIVE)", steps=[
    ("text", "✍️ اكتب نص الوسم (مثل EDIT أو @اسمك):"),
    ("choice", "شكل الوسم:", [("🏷 EDIT", "edit"), ("🔴 REC", "rec"), ("🔴 LIVE", "live"), ("💠 نيون", "neon"), ("⚪ شارة", "badge"), ("🟡 ذهبي", "gold")], "style"),
    ("choice", "مكان الوسم:", [("↖ أعلى يسار", "tl"), ("↗ أعلى يمين", "tr"), ("↙ أسفل يسار", "bl"), ("↘ أسفل يمين", "br")], "pos")])
def t_tag(img, a, p=None):
    p = p or {}
    h, w = img.shape[:2]
    lay = art.tag_layer(p.get("style", "edit"), p.get("text", "EDIT"), w, h, p.get("pos", "tl"))
    im = Image.fromarray(cv2.cvtColor(img, cv2.COLOR_BGR2RGB)).convert("RGBA")
    return cv2.cvtColor(np.array(Image.alpha_composite(im, lay).convert("RGB")), cv2.COLOR_RGB2BGR), a


@tool("watermark", "txt", "علامة مائية باسمك", steps=[("text", "✍️ اكتب اسمك/حسابك للعلامة المائية:")])
def t_wm(img, a, p=None):
    return _text_draw(img, dict(p or {"text": "@name"}, wm=True, size=0.035)), a


# ================================================================ المحرّك
def load(path: str, maxside: int | None = None):
    im = ImageOps.exif_transpose(Image.open(path))
    if im.mode in ("RGBA", "LA", "P"):
        bg = Image.new("RGB", im.size, (255, 255, 255))
        im = im.convert("RGBA")
        bg.paste(im, mask=im.split()[-1])
        im = bg
    im = im.convert("RGB")
    if maxside and max(im.size) > maxside:
        s = maxside / max(im.size)
        im = im.resize((max(1, int(im.width * s)), max(1, int(im.height * s))), Image.LANCZOS)
    return cv2.cvtColor(np.array(im), cv2.COLOR_RGB2BGR)


def apply_one(state, tid, params=None):
    img, a = state
    t = TOOLS[tid]
    if t.steps:
        out, a2 = t.fn(img, a, params or {})
    else:
        out, a2 = t.fn(img, a)
    return np.ascontiguousarray(out), a2


def apply_stack(img, stack):
    st = (img, None)
    for tid, p in stack:
        st = apply_one(st, tid, p)
    return st


def to_jpeg(state, q=90, maxside=1280) -> bytes:
    img, a = state
    if a is not None:  # معاينة الشفافية على رقعة شطرنج
        h, w = img.shape[:2]
        c = max(8, _S(img) // 40)
        yy, xx = np.mgrid[0:h, 0:w]
        chk = (((yy // c) + (xx // c)) % 2).astype(np.float32)[..., None] * 0.18 + 0.78
        img = _u(_f(img) * _m3(a) + chk * (1 - _m3(a)))
    if max(img.shape[:2]) > maxside:
        s = maxside / max(img.shape[:2])
        img = cv2.resize(img, None, fx=s, fy=s, interpolation=cv2.INTER_AREA)
    ok, buf = cv2.imencode(".jpg", img, [cv2.IMWRITE_JPEG_QUALITY, q])
    return buf.tobytes()


def to_file(state, path_base: str) -> str:
    """التصدير الكامل: PNG شفاف إن وُجدت شفافية وإلا JPEG جودة 97"""
    img, a = state
    if a is not None:
        rgba = np.dstack([img, _u(a)[..., 0] if a.ndim == 3 else (np.clip(a, 0, 1) * 255).astype(np.uint8)])
        p = path_base + ".png"
        cv2.imwrite(p, rgba)
        return p
    p = path_base + ".jpg"
    cv2.imwrite(p, img, [cv2.IMWRITE_JPEG_QUALITY, 97])
    return p

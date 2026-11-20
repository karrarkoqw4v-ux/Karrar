"""150 قالب «ستايل مصمم» أصلي: 10 عائلات × 15 قالباً، كل قالب تركيبة منسّقة من التلوين + الحركة + اللمسات.
(هذه تصاميم أصلية بأسلوب الإيديت الشائع في تيك توك وليست نسخاً من قوالب أشخاص بعينهم.)"""
import random

import effects as E

FAMILIES = [
    # (المفتاح، الاسم، تلوين، حركة/تأثير، لمسات)
    ("cin", "🎬 سينمائي", ["teal_orange", "cin_warm", "cin_cool", "kodak", "fuji", "matte", "auto_grade", "hdr_cine", "faded"],
     ["filmgrain", "vignette", "letterbox", "zoom_in", "glow", "zoom_out", "tilt"], ["cv_sharp", "cv_clahe", "enh_all", "cv_filmic"]),
    ("neon", "🌃 نيون وسايبر", ["cyber", "night", "neon", "moody", "hue_cycle", "ice"],
     ["glitch_burst", "shake_beat", "hue_pulse", "strobe", "glow", "color_flicker", "vhs"], ["cv_sharp", "enh_colors", "cv_detail"]),
    ("dark", "🖤 دراما وغموض", ["noir", "moody", "bw", "cin_cool", "night", "faded"],
     ["vignette", "filmgrain", "glitch_burst", "tilt", "zoom_in", "shake_beat", "rain"], ["cv_clahe", "enh_shadows", "cv_lowlight"]),
    ("gold", "👑 ذهبي وفاخر", ["golden", "sunset", "kodak", "cin_warm", "desert", "highkey"],
     ["glow", "flash_beat", "vignette", "letterbox", "embers", "zoom_beat", "stars"], ["cv_hdr", "cv_sharp", "enh_colors"]),
    ("love", "💕 رومانسي وحالم", ["pinkish", "dreamy", "pastel", "highkey", "sunset"],
     ["hearts", "stars", "glow", "bubbles", "zoom_in", "dreamy", "vignette"], ["enh_colors", "cv_detail"]),
    ("nature", "🌿 طبيعة وأجواء", ["forest", "underwater", "ice", "auto_vivid", "desert", "enh_colors"],
     ["snow", "rain", "bubbles", "stars", "zoom_in", "zoom_out", "glow"], ["cv_dehaze", "cv_clahe", "cv_tm_drago"]),
    ("edit", "⚡ إيديت وإيقاع", ["auto_vivid", "vivid", "auto_grade", "hue_cycle", "teal_orange", "cyber"],
     ["flash_beat", "zoom_beat", "shake_beat", "flip_alt", "zoom_cut", "strobe", "color_flicker", "ramp_cine", "ramp_pulse", "glitch_burst", "tilt", "hue_pulse"], ["cv_sharp", "enh_colors"]),
    ("retro", "📼 ريترو وأفلام قديمة", ["vintage", "sepia", "faded", "kodak", "matte", "bw"],
     ["vhs", "oldtv", "filmgrain", "vignette", "glitch_burst", "tilt"], ["enh_denoise", "cv_wb"]),
    ("reels", "📱 ريلز وستوري", ["auto_vivid", "vivid", "auto_hdr", "highkey", "teal_orange"],
     ["zoom_beat", "punch", "confetti", "flash_beat", "hearts", "stars", "color_flicker", "glow"], ["cv_sharp", "enh_colors", "cv_clahe"]),
    ("real", "🔬 واقعية HDR", ["auto_hdr", "auto_grade", "hdr_nat", "hdr_strong", "hdr_cine", "hdr_gold", "enh_light"],
     ["vignette", "filmgrain", "glow", "zoom_in"], ["cv_tm_drago", "cv_tm_reinhard", "cv_tm_mantiuk", "cv_hdr", "cv_hdr_max", "cv_ultra", "cv_dehaze", "cv_super2", "cv_detail"]),
]
PER = 15
_SHORT = {"ratio916": "طولي"}


def _short(eid: str) -> str:
    t = E.EFFECTS[eid].title
    for cut in (" (", " —", "  "):
        t = t.split(cut)[0]
    return t


def _build() -> dict[str, list[tuple[str, list]]]:
    out: dict[str, list] = {}
    gseen: set = set()
    for key, name, grades, fxs, fins in FAMILIES:
        rnd = random.Random(key)
        seen, items = gseen, []
        guard = 0
        while len(items) < PER and guard < 5000:
            guard += 1
            g = rnd.choice(grades)
            nfx = rnd.choice([1, 2, 2, 3])
            fx = rnd.sample(fxs, min(nfx, len(fxs)))
            fin = rnd.choice(fins + [None, None])
            parts = [g] + fx + ([fin] if fin else [])
            # لا نجمع تأثيرين متعارضين: سرعتين، أو أكثر من واحد من الأعمدة الثقيلة
            if sum(1 for e in parts if E.EFFECTS[e].cat == "cv") > 2:
                continue
            if sum(1 for e in parts if e.startswith("ramp_")) > 1:
                continue
            sig = tuple(sorted(parts))
            if sig in seen:
                continue
            seen.add(sig)
            title = f"{_short(g)} + {_short(fx[0])}" + (f" + {_short(fx[1])}" if len(fx) > 1 else "")
            items.append((title, [(e, {}) for e in parts]))
        assert len(items) == PER, (key, len(items))
        out[key] = items
    return out


DESIGNER = _build()
FAMILY_NAMES = {k: n for k, n, *_ in FAMILIES}
assert sum(len(v) for v in DESIGNER.values()) == 150

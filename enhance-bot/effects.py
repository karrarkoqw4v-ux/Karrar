"""مكتبة التأثيرات: كل تأثير يُنتج قطعاً (Pieces) تُجمَّع في أمر FFmpeg واحد."""
from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable

import art

SC = "curves=all='0/0 0.25/0.2 0.75/0.8 1/1'"


@dataclass
class Piece:
    kind: str  # vf | cx | af | pre | music | mute | out | speed | dims
    val: object = None
    inputs: list = field(default_factory=list)  # مدخلات إضافية لـ cx


@dataclass
class Ctx:
    w: int
    h: int
    dur: float
    has_a: bool
    tmp: Path
    p: dict = field(default_factory=dict)
    beat: dict | None = None
    stats: dict | None = None
    _n: int = 0

    def name(self, ext="png") -> str:
        self._n += 1
        return str(self.tmp / f"x{self._n}_{id(self) % 9999}.{ext}")


def vf(s: str) -> Piece:
    return Piece("vf", s)


def af(s: str) -> Piece:
    return Piece("af", s)


def cx(tmpl: str, inputs: list | None = None) -> Piece:
    """{i}=مدخل الفيديو الحالي، {o}=المخرج، {n}=رقم أول مدخل إضافي"""
    return Piece("cx", tmpl, inputs or [])


@dataclass
class Effect:
    id: str
    cat: str
    title: str
    build: Callable[[Ctx], list[Piece]]
    steps: list = field(default_factory=list)  # ("text"|"audio"|"range"|"choice", prompt, [(label,value)])
    motion: bool = False  # المعاينة كمقطع متحرك
    noprev: bool = False  # بدون صورة معاينة
    desc: str = ""


CATS = {
    "auto": ("✨", "تلقائي ذكي (يحلّل فيديوك)"),
    "ed": ("🥁", "إيديت مصممين (ريتم وتقطيع)"),
    "col": ("🎨", "فلاتر الألوان"),
    "hdr": ("🌈", "HDR والتحسين"),
    "res": ("🖥", "الدقة"),
    "edit": ("✂️", "المونتاج"),
    "fx": ("🎬", "تأثيرات الحركة"),
    "part": ("🎉", "جسيمات وأجواء"),
    "cv": ("🔬", "واقعية فائقة (مكتبات عالمية)"),
    "aud": ("🎵", "الصوت"),
    "out": ("📤", "تصدير خاص"),
}
EFFECTS: dict[str, Effect] = {}


def reg(id, cat, title, build, **kw):
    EFFECTS[id] = Effect(id, cat, title, build, **kw)


def simple(id, cat, title, chain: str, **kw):
    reg(id, cat, title, lambda c, _c=chain: [vf(_c)], **kw)


def split_fx(id, cat, title, tmpl: str, **kw):
    reg(id, cat, title, lambda c, _t=tmpl: [cx(_t)], **kw)


# =============== تلقائي ذكي: يحلّل الفيديو ويضبط الألوان والحدة ===============
import autograde


def _auto(mode: str):
    def b(c: Ctx):
        return [vf(autograde.chain(c.stats or {}, mode))]
    return b


reg("auto_grade", "auto", "تحسين تلقائي ذكي", _auto("grade"), desc="يقيس سطوع وتباين وتشبع وتشويش وحدة فيديوك ويضبط الفلاتر عليه تلقائياً.")
reg("auto_natural", "auto", "تلقائي طبيعي (خفيف)", _auto("natural"))
reg("auto_vivid", "auto", "تلقائي ألوان غنية", _auto("vivid"))
reg("auto_hdr", "auto", "تلقائي HDR قوي", _auto("hdr"))

# =============== إيديت مصممين: تأثيرات على إيقاع الصوت فعلاً (أو 0.6ث إن لم يوجد إيقاع) ===============
def _P(c: Ctx, mult: float = 1.0):
    bt = c.beat or {"period": 0.6, "phase": 0.0}
    P = bt["period"]
    while P < 0.3:
        P *= 2
    return P * mult, bt["phase"]


def _tt(c: Ctx, mult: float = 1.0):
    P, ph = _P(c, mult)
    return f"mod(t-{ph:.3f}+{P * 100:.2f},{P:.4f})", P


def _flash(c: Ctx):
    m, P = _tt(c)
    return [vf(f"eq=brightness='0.5*exp(-9*{m})':contrast='1+0.25*exp(-9*{m})':eval=frame")]


def _zoombeat(c: Ctx):
    m, P = _tt(c)
    z = f"(1+0.13*exp(-7*{m}))"
    return [vf(f"scale=w='iw*{z}':h='ih*{z}':eval=frame,crop={c.w}:{c.h}")]


def _shakebeat(c: Ctx):
    m, P = _tt(c)
    env = f"exp(-8*{m})"
    return [vf(f"scale=trunc({c.w}*1.1/2)*2:trunc({c.h}*1.1/2)*2,crop={c.w}:{c.h}:x='(iw-ow)/2+((iw-ow)/2-2)*{env}*sin(t*90)':y='(ih-oh)/2+((ih-oh)/2-2)*{env}*cos(t*80)'")]


def _huepulse(c: Ctx):
    m, P = _tt(c)
    return [vf(f"hue=h='50*exp(-6*{m})':s='1+0.6*exp(-6*{m})'")]


def _strobe(c: Ctx):
    m, P = _tt(c, 0.5)
    return [vf(f"eq=brightness='if(lt({m},0.05),0.45,0)':eval=frame")]


def _flipalt(c: Ctx):
    P, ph = _P(c, 2.0)
    return [cx(f"[{{i}}]split[a][b];[b]hflip[f];[a][f]overlay=0:0:enable='gt(mod(t-{ph:.3f}+{P * 100:.2f},{P:.4f}),{P / 2:.4f})'[{{o}}]")]


def _zoomcut(c: Ctx):
    P, ph = _P(c, 2.0)
    return [cx(f"[{{i}}]split[a][b];[b]crop=iw/1.35:ih/1.35,scale={c.w}:{c.h}:flags=bicubic[z];[a][z]overlay=0:0:enable='gt(mod(t-{ph:.3f}+{P * 100:.2f},{P:.4f}),{P / 2:.4f})'[{{o}}]")]


def _glitchburst(c: Ctx):
    m, P = _tt(c)
    en = f"lt({m},{min(0.25, P / 2):.3f})"
    return [vf(f"rgbashift=rh=-12:bh=12:gv=5:enable='{en}',noise=alls=35:allf=t:enable='{en}'")]


def _tilt(c: Ctx):
    P, ph = _P(c, 2.0)
    return [vf(f"rotate=a='0.05*sin(2*PI*(t-{ph:.3f})/{P:.4f})':c=black:ow={c.w}:oh={c.h},scale=trunc({c.w}*1.08/2)*2:trunc({c.h}*1.08/2)*2,crop={c.w}:{c.h}")]


def _colorflicker(c: Ctx):
    P, ph = _P(c, 1.0)
    return [vf(f"eq=saturation='1+0.55*abs(sin(PI*(t-{ph:.3f})/{P:.4f}))':contrast='1+0.12*abs(sin(PI*(t-{ph:.3f})/{P:.4f}))':eval=frame")]


def _ramp(segs):
    """تغيير سرعة على مقاطع: segs = [(نسبة من المدة، سرعة)] — يكتم الصوت لتفادي عدم التزامن"""
    def b(c: Ctx):
        d = max(c.dur, 0.5)
        tot = sum(f for f, _ in segs)
        t0, parts, labs, newd = 0.0, [], [], 0.0
        for k, (frac, sp) in enumerate(segs):
            span = d * frac / tot
            t1 = t0 + span
            parts.append(f"[a{k}]trim={t0:.3f}:{t1:.3f},setpts=(PTS-STARTPTS)/{sp}[s{k}]")
            labs.append(f"[s{k}]")
            newd += span / sp
            t0 = t1
        n = len(segs)
        g = f"[{{i}}]split={n}" + "".join(f"[a{k}]" for k in range(n)) + ";" + ";".join(parts) + ";" + "".join(labs) + f"concat=n={n}:v=1:a=0[{{o}}]"
        c.dur = newd
        return [cx(g), Piece("mute")]
    return b


reg("flash_beat", "ed", "فلاش على الإيقاع", _flash, motion=True, desc="ومضة بيضاء مع كل ضربة (تتزامن مع صوت الفيديو أو الموسيقى المضافة).")
reg("zoom_beat", "ed", "زوم نبضي على الإيقاع", _zoombeat, motion=True)
reg("shake_beat", "ed", "اهتزاز على الضربات", _shakebeat, motion=True)
reg("hue_pulse", "ed", "نبض ألوان على الإيقاع", _huepulse, motion=True)
reg("strobe", "ed", "ستروب سريع", _strobe, motion=True)
reg("flip_alt", "ed", "قلب أفقي متناوب", _flipalt, motion=True)
reg("zoom_cut", "ed", "قطع زوم متناوب", _zoomcut, motion=True)
reg("glitch_burst", "ed", "غليتش دفعات", _glitchburst, motion=True)
reg("tilt", "ed", "ميلان متمايل", _tilt, motion=True)


def _blurburst(c: Ctx):
    m, P = _tt(c, 2.0)
    en = f"lt({m},{min(0.3, P / 3):.3f})"
    return [vf(f"avgblur=sizeX=2:sizeY=61:enable='{en}',eq=brightness=-0.05:enable='{en}'")]


def _redflash(c: Ctx):
    m, P = _tt(c, 2.0)
    return [vf(f"drawbox=x=0:y=0:w=iw:h=ih:color=0xE0143C@0.32:t=fill:enable='lt({m},{min(0.2, P / 3):.3f})'")]


def _rgbbeat(c: Ctx):
    m, P = _tt(c)
    en = f"lt({m},{min(0.22, P / 3):.3f})"
    return [vf(f"rgbashift=rh=-14:bh=14:rv=3:bv=-3:edge=smear:enable='{en}'")]


def _bwflash(c: Ctx):
    m, P = _tt(c, 2.0)
    return [vf(f"hue=s='if(lt({m},{min(0.25, P / 3):.3f}),0,1)'")]


def _bloombeat(c: Ctx):
    m, P = _tt(c)
    mT = m.replace("mod(t-", "mod(T-")
    return [cx(f"[{{i}}]split[a][b];[b]lutyuv=y='if(gt(val,150),val,0)':u=128:v=128,gblur=sigma=22[g];[a][g]blend=c0_expr='A+(255-A)*B/255*(0.15+0.7*exp(-6*{mT}))':c1_expr=A:c2_expr=A[{{o}}]")]


def _contrastpulse(c: Ctx):
    m, P = _tt(c)
    return [vf(f"eq=contrast='1+0.35*exp(-7*{m})':saturation='1+0.4*exp(-7*{m})':eval=frame")]


def _zoomout(c: Ctx):
    m, P = _tt(c)
    return [vf(f"scale=w='iw*(1.18-0.18*(1-exp(-6*{m})))':h='ih*(1.18-0.18*(1-exp(-6*{m})))':eval=frame,crop={c.w}:{c.h}")]


def _filmburn(c: Ctx):
    m, P = _tt(c, 4.0)
    return [vf(f"colorbalance=rs=0.0:bs=0.0,drawbox=x=0:y=0:w=iw:h=ih:color=0xFF8A1E@0.0:t=fill,eq=brightness='0.18*exp(-3*{m})':saturation='1+0.3*exp(-3*{m})':eval=frame")]


reg("rgb_beat", "ed", "انقسام ألوان RGB على الضربة", _rgbbeat, motion=True)
reg("bw_flash", "ed", "وميض أبيض وأسود على الإيقاع", _bwflash, motion=True)
reg("bloom_beat", "ed", "توهج نبضي على الإيقاع", _bloombeat, motion=True)
reg("contrast_pulse", "ed", "نبض تباين وألوان", _contrastpulse, motion=True)
reg("zoom_out_beat", "ed", "زوم عكسي (يبتعد) على الضربة", _zoomout, motion=True)
reg("film_burn", "ed", "حرق فيلم دافئ", _filmburn, motion=True)
def _spin(c: Ctx):
    m, P = _tt(c)
    return [vf(f"rotate=a='0.16*exp(-7*{m})*sin(9*t)':c=black:ow={c.w}:oh={c.h},scale=trunc({c.w}*1.18/2)*2:trunc({c.h}*1.18/2)*2,crop={c.w}:{c.h}")]


def _whipzoom(c: Ctx):
    m, P = _tt(c, 2.0)
    en = f"lt({m},{min(0.2, P / 3):.3f})"
    return [vf(f"scale=w='iw*(1+0.4*exp(-8*{m}))':h='ih*(1+0.4*exp(-8*{m}))':eval=frame,crop={c.w}:{c.h},avgblur=sizeX=2:sizeY=45:enable='{en}'")]


def _pixelbeat(c: Ctx):
    m, P = _tt(c, 2.0)
    return [cx(f"[{{i}}]split[a][b];[b]scale=iw/26:ih/26:flags=area,scale={c.w}:{c.h}:flags=neighbor[p];[a][p]overlay=0:0:enable='lt({m},{min(0.22, P / 3):.3f})'[{{o}}]")]


def _invert(c: Ctx):
    m, P = _tt(c, 2.0)
    return [vf(f"negate=enable='lt({m},{min(0.12, P / 4):.3f})'")]


def _tag(c: Ctx):
    p = c.p
    png = art.tag_png(p.get("style", "edit"), p.get("text", "EDIT"), c.w, c.h, p.get("pos", "tl"), c.name())
    return [cx("[{i}][{n}:v]overlay=0:0:format=auto:shortest=1[{o}]", [["-loop", "1", "-framerate", "30", "-i", png]])]


reg("spin_beat", "ed", "دوران نبضي على الإيقاع", _spin, motion=True)
reg("whip_zoom", "ed", "زوم خاطف مع ضبابية (Whip)", _whipzoom, motion=True)
reg("pixel_beat", "ed", "بكسلة خاطفة على الضربة", _pixelbeat, motion=True)
reg("invert_beat", "ed", "وميض نيجاتيف على الإيقاع", _invert, motion=True)
reg("trail", "fx", "أثر حركة (Trail)", lambda c: [vf("tmix=frames=5:weights='1 1 1 1 1',eq=saturation=1.1")], motion=True)
reg("tag", "edit", "وسم / تاج تعديل (EDIT • REC • LIVE)", _tag,
    steps=[("text", "✍️ اكتب نص الوسم (مثل EDIT أو @اسمك):"),
           ("choice", "شكل الوسم:", [("🏷 EDIT", "edit"), ("🔴 REC", "rec"), ("🔴 LIVE", "live"), ("💠 نيون", "neon"), ("⚪ شارة", "badge"), ("🟡 ذهبي", "gold")], "style"),
           ("choice", "مكان الوسم:", [("↖ أعلى يسار", "tl"), ("↗ أعلى يمين", "tr"), ("↙ أسفل يسار", "bl"), ("↘ أسفل يمين", "br")], "pos")],
    desc="علامة/وسم على شكل حبّة فوق الفيديو بأشكال مختلفة.")
reg("mirror_split", "fx", "مرآة منتصف (نصف معكوس)", lambda c: [cx("[{i}]split[a][b];[a]crop=iw/2:ih:0:0[l];[b]crop=iw/2:ih:0:0,hflip[r];[l][r]hstack[{o}]")], motion=True)
reg("film_flicker", "fx", "رجفة فيلم قديم (سطوع)", lambda c: [vf("eq=brightness='0.035*sin(t*37)+0.025*sin(t*91)':contrast='1+0.04*sin(t*53)':eval=frame")], motion=True)
reg("vert_shake", "ed", "اهتزاز عمودي على الإيقاع", lambda c: [vf(f"scale=trunc({c.w}*1.08/2)*2:trunc({c.h}*1.08/2)*2,crop={c.w}:{c.h}:x='(iw-ow)/2':y='(ih-oh)/2+((ih-oh)/2-2)*exp(-8*{_tt(c)[0]})*sin(t*70)'")], motion=True)
reg("vignette_pulse", "ed", "تعتيم أطراف نبضي", lambda c: [vf(f"vignette=angle='0.95-0.32*exp(-6*{_tt(c)[0]})':eval=frame")], motion=True)
reg("sat_drop", "ed", "سقوط الألوان على الضربة", lambda c: [vf(f"eq=saturation='1-0.85*exp(-8*{_tt(c, 2.0)[0]})':eval=frame")], motion=True)
reg("blur_burst", "ed", "ضبابية حركة على الضربة", _blurburst, motion=True, desc="ضبابية حركة عمودية خاطفة مع انتقال الإيقاع.")
reg("red_flash", "ed", "وميض أحمر على الإيقاع", _redflash, motion=True)
reg("color_flicker", "ed", "تقلّب الألوان", _colorflicker, motion=True)
reg("ramp_cine", "ed", "سلو-فاست سينمائي (يكتم الصوت)", _ramp([(0.3, 1.0), (0.25, 0.4), (0.45, 1.8)]), motion=True)
reg("ramp_pulse", "ed", "سرعة متقطعة بطيء/سريع (يكتم الصوت)", _ramp([(0.2, 1.6), (0.15, 0.45), (0.2, 1.8), (0.15, 0.45), (0.3, 1.5)]), motion=True)

# =============== فلاتر الألوان ===============
simple("cin_warm", "col", "سينمائي دافئ", f"colorbalance=rs=.07:bs=-.07:rm=.05:bm=-.05,{SC},eq=saturation=1.08")
simple("cin_cool", "col", "سينمائي بارد", f"colorbalance=rs=-.06:bs=.08:rm=-.04:bm=.05,{SC},eq=saturation=1.05")
simple("teal_orange", "col", "تيل وبرتقالي (هوليوود)", "colorbalance=rs=-.1:bs=.12:rh=.14:bh=-.1,eq=saturation=1.15:contrast=1.06")
simple("vintage", "col", "قديم (Vintage)", "curves=preset=vintage,eq=saturation=0.9")
simple("bw", "col", "أبيض وأسود", "hue=s=0")
simple("noir", "col", "نوار (أسود قوي)", "hue=s=0,eq=contrast=1.4:brightness=-0.05,vignette=PI/4")
simple("sepia", "col", "سيبيا", "colorchannelmixer=.393:.769:.189:0:.349:.686:.168:0:.272:.534:.131")
simple("faded", "col", "باهت فيلمي", "curves=all='0/0.12 1/0.9',eq=saturation=0.65:contrast=0.95")
simple("vivid", "col", "زاهي", "eq=saturation=1.6:contrast=1.1,unsharp=5:5:0.5:5:5:0.0")
simple("golden", "col", "ذهبي (Golden Hour)", "colorbalance=rs=.12:gs=.04:bs=-.14:rm=.1:gm=.02:bm=-.1,eq=saturation=1.2:contrast=1.05")
simple("sunset", "col", "غروب", "colorbalance=rs=.25:gs=-.03:bs=-.2:rm=.18:bm=-.15,eq=saturation=1.25:contrast=1.05")
simple("night", "col", "ليلي", "colorbalance=rs=-.2:gs=-.1:bs=.35:rm=-.1:bm=.2,eq=brightness=-0.08:contrast=1.1:saturation=0.9,vignette=PI/5")
simple("cyber", "col", "سايبر بانك", "colorbalance=rs=.2:bs=.25:gs=-.15,eq=saturation=1.4:contrast=1.1,hue=h=-10")
simple("moody", "col", "داكن مزاجي", "eq=brightness=-0.06:contrast=1.15:saturation=0.9,curves=all='0/0 0.3/0.22 1/1',vignette=PI/5")
simple("highkey", "col", "مشرق نظيف", "eq=brightness=0.07:contrast=0.95,curves=all='0/0.05 1/1'")
simple("kodak", "col", "كوداك", "colorbalance=rs=.06:gs=.02:bs=-.08:rm=.05,eq=saturation=1.18:contrast=1.08,curves=all='0/0.03 0.5/0.5 1/0.97'")
simple("fuji", "col", "فوجي", "colorbalance=gs=.04:bs=.03:gm=.04,eq=saturation=1.14:contrast=1.06")
simple("matte", "col", "مات (Matte)", "curves=all='0/0.08 1/0.92',eq=saturation=0.85")
simple("pastel", "col", "باستيل", "eq=saturation=0.7:brightness=0.06:contrast=0.9")
simple("underwater", "col", "تحت الماء", "colorbalance=rs=-.35:gs=.05:bs=.3,eq=saturation=0.9:contrast=1.05,gblur=sigma=0.7")
simple("desert", "col", "صحراء", "colorbalance=rs=.2:gs=.08:bs=-.18,eq=saturation=0.85:contrast=1.15:brightness=0.03")
simple("pinkish", "col", "وردي حالم", "colorbalance=rs=.12:bs=.08:gs=-.05,eq=saturation=1.15")
simple("violet_night", "col", "ليل بنفسجي سينمائي", "colorbalance=rs=.03:gs=-.04:bs=.12:rm=.03:bm=.1:bh=.04,eq=contrast=1.1:saturation=0.9,curves=all='0/0.02 0.5/0.5 1/1':b='0/0.03 0.5/0.55 1/1',vignette=PI/5")
simple("teal_dark", "col", "تيل داكن بارد", "colorbalance=rs=-.06:gs=.02:bs=.05:rm=-.04:bm=.04,eq=contrast=1.12:saturation=0.85,vignette=PI/4.5")
simple("golden_hour", "col", "ساعة ذهبية", "colorbalance=rs=.08:gs=.02:bs=-.08:rm=.1:gm=.03:bm=-.08:rh=.06:bh=-.06,eq=saturation=1.2:contrast=1.06")
simple("moonlight", "col", "ضوء القمر", "colorbalance=rs=-.08:bs=.16:rm=-.05:bm=.1:bh=.05,eq=brightness=-0.04:saturation=0.8:contrast=1.1")
simple("dark_academia", "col", "أكاديمي داكن دافئ", "colorbalance=rs=.05:gs=.02:bs=-.05:rm=.04:bm=-.04,eq=saturation=0.75:contrast=1.12:brightness=-0.04,vignette=PI/4.5")
simple("tokyo", "col", "نيون طوكيو", "colorbalance=rs=.14:gs=-.08:bs=.2:rh=-.05:bh=.1,eq=saturation=1.35:contrast=1.15")
simple("clean_bright", "col", "ساطع نظيف", "eq=brightness=0.05:contrast=1.08:saturation=1.12,unsharp=5:5:0.5")
simple("autumn", "col", "خريفي", "colorbalance=rs=.1:gs=.02:bs=-.1:rm=.08:bm=-.08,eq=saturation=1.2:contrast=1.05")
simple("ice", "col", "جليدي", "colorbalance=bs=.15:gs=.03:rs=-.1,eq=saturation=0.95:brightness=0.03")
simple("forest", "col", "أخضر الغابة", "colorbalance=gs=.1:rs=-.05:bs=-.03,eq=saturation=1.15")
simple("colorpop", "col", "احتفظ بالأحمر فقط", "colorhold=color=0xE53935:similarity=0.3:blend=0.12")
simple("invert", "col", "عكس الألوان", "negate")
split_fx("dreamy", "col", "حالم ناعم", "[{i}]split[a][b];[b]gblur=sigma=18[g];[a][g]blend=c0_mode=screen:c0_opacity=0.5:c1_opacity=0:c2_opacity=0,eq=saturation=1.2:brightness=0.03[{o}]")
split_fx("glow", "col", "توهج", "[{i}]split[a][b];[b]lutyuv=y='if(gt(val,165),val,0)':u=128:v=128,gblur=sigma=25[g];[a][g]blend=c0_mode=screen:c1_opacity=0:c2_opacity=0[{o}]")
split_fx("neon", "col", "حواف نيون", "[{i}]split[a][b];[b]edgedetect=low=0.08:high=0.2,gblur=sigma=3,format=rgb24,colorchannelmixer=rr=0.1:gg=0.9:bb=1[e];[a]eq=brightness=-0.25:saturation=0.8,format=rgb24[d];[d][e]blend=c0_mode=screen:c1_opacity=0:c2_opacity=0[{o}]")

# =============== HDR والتحسين (عدة أنماط ليختار المستخدم) ===============
simple("hdr_nat", "hdr", "HDR طبيعي", f"unsharp=13:13:0.45:13:13:0.0,curves=all='0/0.02 0.25/0.28 0.5/0.52 0.8/0.82 1/0.98',vibrance=intensity=0.45,eq=saturation=1.12:contrast=1.05")
simple("hdr_strong", "hdr", "HDR قوي (تفاصيل حادة)", f"unsharp=9:9:0.9:9:9:0.0,unsharp=13:13:0.8:13:13:0.0,{SC},vibrance=intensity=0.8,eq=saturation=1.25:contrast=1.08")
simple("hdr_cine", "hdr", "HDR سينمائي", "unsharp=13:13:0.55:13:13:0.0,colorbalance=rs=-.05:bs=.07:rh=.08:bh=-.06,curves=all='0/0.02 0.5/0.5 1/0.96',eq=saturation=1.1:contrast=1.1")
simple("hdr_gold", "hdr", "HDR ذهبي دافئ", "unsharp=13:13:0.6:13:13:0.0,colorbalance=rs=.08:bs=-.1:rm=.06:bm=-.06,vibrance=intensity=0.6,eq=saturation=1.15:contrast=1.07")
simple("enh_all", "hdr", "تحسين شامل", f"hqdn3d=1.5:1.2:4:4,deband=range=20:blur=1,unsharp=5:5:0.7:5:5:0.0,unsharp=13:13:0.3:13:13:0.0,vibrance=intensity=0.5,{SC},eq=saturation=1.1:contrast=1.04")
simple("enh_colors", "hdr", "ألوان أوضح", "vibrance=intensity=0.8,eq=saturation=1.15:contrast=1.05")
simple("enh_light", "hdr", "تحسين الإضاءة تلقائياً", "normalize=smoothing=30:independence=0,eq=gamma=1.06")
simple("enh_shadows", "hdr", "إنقاذ الظلال", "curves=all='0/0.05 0.25/0.34 0.5/0.55 1/1',unsharp=7:7:0.4:7:7:0.0")
simple("enh_dehaze", "hdr", "إزالة الضباب", "eq=contrast=1.2:brightness=-0.03:saturation=1.18,curves=all='0/0 0.2/0.13 1/1',unsharp=9:9:0.4:9:9:0.0")
simple("enh_denoise", "hdr", "إزالة التشويش", "hqdn3d=4:3:8:8,atadenoise")
simple("enh_sharp", "hdr", "توضيح فائق", "cas=0.7,unsharp=5:5:0.8:5:5:0.0")

# =============== الدقة ===============
def _res(target: int):
    def b(c: Ctx):
        s = min(c.w, c.h)
        if s == target:
            return []
        if c.w >= c.h:
            nw, nh = round(c.w * target / c.h / 2) * 2, target
        else:
            nw, nh = target, round(c.h * target / c.w / 2) * 2
        c.w, c.h = nw, nh
        return [vf(f"scale={nw}:{nh}:flags=lanczos"), vf("unsharp=5:5:0.6:5:5:0.0")]
    return b


for k, t, name in (("r720", 720, "HD 720p"), ("r1080", 1080, "Full HD 1080p"), ("r2k", 1440, "دقة 2K"), ("r4k", 2160, "دقة 4K (حتى 40 ثانية)")):
    reg(k, "res", name, _res(t))


def _x2(c: Ctx):
    c.w, c.h = c.w * 2, c.h * 2
    return [vf("scale=iw*2:ih*2:flags=lanczos"), vf("hqdn3d=1:1:3:3,unsharp=5:5:0.7:5:5:0.0")]


reg("r2x", "res", "ضعف الدقة 2×", _x2)

# =============== المونتاج ===============
def _trim(c: Ctx):
    a, b = c.p["range"]
    c.dur = b - a
    return [Piece("pre", ["-ss", f"{a:.3f}", "-t", f"{b - a:.3f}"])]


reg("trim", "edit", "قص الفيديو (بداية/نهاية)", _trim, steps=[("range", "✍️ اكتب البداية والنهاية بالثواني أو mm:ss — مثال: 0:05 0:20")], noprev=True)


def _speed(f: float):
    def b(c: Ctx):
        c.dur /= f
        return [vf(f"setpts=PTS/{f}"), Piece("speed", f)]
    return b


reg("speed2", "edit", "تسريع 2×", _speed(2), noprev=True)
reg("speed3", "edit", "تسريع 3×", _speed(3), noprev=True)
reg("slow05", "edit", "بطيء 0.5×", _speed(0.5), noprev=True)
reg("slow025", "edit", "بطيء جداً 0.25×", _speed(0.25), noprev=True)
reg("reverse", "edit", "عكس الفيديو (حتى 60ث)", lambda c: [Piece("pre", ["-t", "60"]), vf("reverse"), af("areverse")], noprev=True)
reg("rot_r", "edit", "تدوير 90° يمين", lambda c: _rot(c, "transpose=1"))
reg("rot_l", "edit", "تدوير 90° يسار", lambda c: _rot(c, "transpose=2"))


def _rot(c: Ctx, f: str):
    c.w, c.h = c.h, c.w
    return [vf(f)]


simple("flip_h", "edit", "قلب أفقي", "hflip")
simple("flip_v", "edit", "قلب عمودي", "vflip")


def _fit(W: int, H: int):
    def b(c: Ctx):
        c.w, c.h = W, H
        return [cx(f"[{{i}}]split[a][b];[a]scale={W}:{H}:force_original_aspect_ratio=increase,crop={W}:{H},boxblur=28:2[bg];[b]scale={W}:{H}:force_original_aspect_ratio=decrease:flags=lanczos[fg];[bg][fg]overlay=(W-w)/2:(H-h)/2[{{o}}]")]
    return b


reg("ratio916", "edit", "طولي 9:16 (ستوري/ريلز) بخلفية مموّهة", _fit(1080, 1920))
reg("ratio11", "edit", "مربع 1:1 بخلفية مموّهة", _fit(1080, 1080))
reg("ratio169", "edit", "عرضي 16:9 بخلفية مموّهة", _fit(1920, 1080))


def _cropsq(c: Ctx):
    s = min(c.w, c.h)
    c.w = c.h = s
    return [vf("crop='min(iw,ih)':'min(iw,ih)'")]


reg("crop_sq", "edit", "قص مربع من الوسط", _cropsq)
reg("zoom15", "edit", "تقريب 1.5×", lambda c: [vf("crop='iw/1.5':'ih/1.5'"), vf(f"scale={c.w}:{c.h}:flags=lanczos")])


def _fade(c: Ctx):
    d = min(1.0, max(0.3, c.dur / 6))
    return [vf(f"fade=t=in:st=0:d={d:.2f},fade=t=out:st={max(0, c.dur - d):.2f}:d={d:.2f}"), af(f"afade=t=in:st=0:d={d:.2f},afade=t=out:st={max(0, c.dur - d):.2f}:d={d:.2f}")]


reg("fade_io", "edit", "اختفاء تدريجي بداية ونهاية", _fade)


def _letter(c: Ctx):
    bar = max(0, int((c.h - c.w / 2.39) / 2) // 2 * 2)
    return [vf(f"drawbox=x=0:y=0:w=iw:h={bar}:color=black:t=fill,drawbox=x=0:y=ih-{bar}:w=iw:h={bar}:color=black:t=fill")]


reg("letterbox", "edit", "شريط سينمائي 2.39", _letter)
reg("border", "edit", "إطار حول الفيديو", lambda c: [vf(f"pad=iw+{c.p['b'] * 2}:ih+{c.p['b'] * 2}:{c.p['b']}:{c.p['b']}:color={c.p['col']}"), Piece("dims", (c.w + c.p["b"] * 2, c.h + c.p["b"] * 2))],
    steps=[("choice", "اختر لون الإطار:", [("⚪ أبيض", "0xFFFFFF"), ("⚫ أسود", "0x000000"), ("🟡 ذهبي", "0xF5C542"), ("🔴 أحمر", "0xE53935")], "col"), ("choice", "السماكة:", [("رفيع", 14), ("متوسط", 28), ("سميك", 56)], "b")])


def _stab(c: Ctx):
    return [Piece("stab")]


reg("stab", "edit", "تثبيت الاهتزاز", _stab, noprev=True)


def _text(c: Ctx):
    png = art.text_png(c.p["text"], c.w, c.h, c.p["pos"], c.name(), box=c.p.get("box", False))
    return [cx("[{i}][{n}:v]overlay=0:0:format=auto:shortest=1[{o}]", [["-loop", "1", "-framerate", "30", "-i", png]])]


reg("text", "edit", "إضافة نص / ترجمة", _text, steps=[("text", "✍️ اكتب النص (عربي أو إنجليزي):"), ("choice", "مكان النص:", [("⬆️ أعلى", "top"), ("⏺ وسط", "mid"), ("⬇️ أسفل", "bot")], "pos")])
def _text_pro(c: Ctx):
    p = c.p
    info = art.text_png_pro(p, c.w, c.h, c.name())
    s = float(p.get("start", 0) or 0)
    e = float(p.get("end", 0) or 0) or max(c.dur, s + 1)
    cxp = c.w * float(p.get("x", 50)) / 100
    cyp = c.h * float(p.get("y", 80)) / 100
    anim = p.get("anim", "none")
    pre = "format=rgba"
    x = f"{cxp:.1f}-w/2"
    y = f"{cyp:.1f}-h/2"
    if anim == "fade":
        pre += f",fade=t=in:st={s:.2f}:d=0.4:alpha=1,fade=t=out:st={max(s, e - 0.4):.2f}:d=0.4:alpha=1"
    elif anim == "pop":
        pre += f",scale=w='iw*max(0.05,min(1,0.55+0.45*(t-{s:.2f})/0.35))':h=-1:eval=frame"
    elif anim == "slide":
        y = f"{cyp:.1f}-h/2+{0.09 * c.h:.1f}*max(0,1-(t-{s:.2f})/0.45)"
    elif anim == "bounce":
        y = f"{cyp:.1f}-h/2-{0.025 * c.h:.1f}*abs(sin(4*(t-{s:.2f})))"
    tm = f"[{{n}}:v]{pre}[tx];[{{i}}][tx]overlay=x='{x}':y='{y}':enable='between(t,{s:.2f},{e:.2f})':format=auto:shortest=1[{{o}}]"
    return [cx(tm, [["-loop", "1", "-framerate", "30", "-i", info["path"]]])]


reg("text_pro", "edit", "نص احترافي (من التطبيق المصغّر)", _text_pro, desc="نص بخط وحجم ولون وتأثير ومكان من محرر النصوص.")
reg("watermark", "edit", "علامة مائية باسمك", lambda c: [cx("[{i}][{n}:v]overlay=0:0:format=auto:shortest=1[{o}]", [["-loop", "1", "-framerate", "30", "-i", art.text_png(c.p["text"], c.w, c.h, "bot", c.name(), size_ratio=0.04)]])], steps=[("text", "✍️ اكتب نص العلامة (مثل @اسمك):")])

# =============== تأثيرات الحركة ===============
simple("glitch", "fx", "غليتش (اهتزاز رقمي)", "rgbashift=rh=-10:bh=10:gv=4,noise=alls=12:allf=t,eq=saturation=1.3:contrast=1.1", motion=True)
simple("vhs", "fx", "كاميرا VHS قديمة", "rgbashift=rh=-6:bh=6,noise=alls=20:allf=t,eq=saturation=1.3:contrast=0.95,gblur=sigma=0.8", motion=True)
simple("oldtv", "fx", "تلفاز قديم", "eq=saturation=0.75:contrast=1.15,noise=alls=22:allf=t,vignette=PI/3,rgbashift=rh=2:bh=-2", motion=True)
simple("filmgrain", "fx", "حبيبات فيلم 35mm", "noise=alls=14:allf=t", motion=True)
simple("vignette", "fx", "تعتيم الأطراف", "vignette=PI/4.5")
simple("mirror", "fx", "مرآة (انعكاس)", "split[a][b];[a]crop=iw/2:ih:0:0[l];[b]crop=iw/2:ih:0:0,hflip[r];[l][r]hstack")
simple("mosaic", "fx", "بكسل (Mosaic)", "scale=trunc(iw/16):trunc(ih/16):flags=neighbor,scale=iw*16:ih*16:flags=neighbor")


def _mirror(c: Ctx):
    return [cx("[{i}]split[a][b];[a]crop=iw/2:ih:0:0[l];[b]crop=iw/2:ih:0:0,hflip[r];[l][r]hstack[{o}]")]


reg("mirror", "fx", "مرآة (انعكاس)", _mirror)


def _mosaic(c: Ctx):
    return [vf(f"scale={c.w // 16 or 1}:{c.h // 16 or 1}:flags=neighbor,scale={c.w}:{c.h}:flags=neighbor")]


reg("mosaic", "fx", "بكسل (Mosaic)", _mosaic)
reg("shake", "fx", "اهتزاز الكاميرا", lambda c: [vf(f"scale=trunc({c.w}*1.08/2)*2:trunc({c.h}*1.08/2)*2,crop={c.w}:{c.h}:x='(iw-ow)/2+((iw-ow)/2-2)*sin(t*37)':y='(ih-oh)/2+((ih-oh)/2-2)*sin(t*29+1)'")], motion=True)
reg("pulse", "fx", "نبض", lambda c: [vf(f"scale=w='iw*(1+0.07*abs(sin(t*5)))':h='ih*(1+0.07*abs(sin(t*5)))':eval=frame,crop={c.w}:{c.h}")], motion=True)
reg("punch", "fx", "تكبير سريع (Punch Zoom)", lambda c: [vf(f"scale=w='iw*(1+0.22*pow(max(0,sin(t*3.1)),8))':h='ih*(1+0.22*pow(max(0,sin(t*3.1)),8))':eval=frame,crop={c.w}:{c.h}")], motion=True)
reg("spin", "fx", "دوران", lambda c: [vf(f"rotate=a='t*0.9':c=black:ow={c.w}:oh={c.h}")], motion=True)
reg("wave", "fx", "موجة", lambda c: [vf("geq=lum='lum(X+8*sin(Y/22+T*5),Y)':cb='cb(X+4*sin(Y/11+T*5),Y)':cr='cr(X+4*sin(Y/11+T*5),Y)'")], motion=True)
reg("hue_cycle", "fx", "ألوان متحركة", lambda c: [vf("hue=h='t*80'")], motion=True)
reg("zoom_in", "fx", "تقريب تدريجي (حركة كاميرا)", lambda c: [vf(f"scale=w='iw*(1+0.2*t/{max(c.dur, 1):.2f})':h='ih*(1+0.2*t/{max(c.dur, 1):.2f})':eval=frame,crop={c.w}:{c.h}")], motion=True)
reg("zoom_out", "fx", "ابتعاد تدريجي (حركة كاميرا)", lambda c: [vf(f"scale=w='iw*(1.2-0.2*t/{max(c.dur, 1):.2f})':h='ih*(1.2-0.2*t/{max(c.dur, 1):.2f})':eval=frame,crop={c.w}:{c.h}")], motion=True)

# =============== جسيمات وأجواء ===============
def _part(kind: str, speed: int, fall: bool, base: str | None = None):
    def b(c: Ctx):
        tile = art.particle_tile(kind, c.w, c.h, c.name())
        y = f"y='mod(t*{speed},{c.h})-{c.h}'" if fall else f"y='-mod(t*{speed},{c.h})'"
        pre = [vf(base)] if base else []
        return pre + [cx(f"[{{i}}][{{n}}:v]overlay=x=0:{y}:format=auto:shortest=1[{{o}}]", [["-loop", "1", "-framerate", "30", "-i", tile]])]
    return b


reg("snow", "part", "ثلج ❄️", _part("snow", 140, True), motion=True)
reg("rain", "part", "مطر 🌧", _part("rain", 900, True, "eq=brightness=-0.07:saturation=0.85"), motion=True)
reg("stars", "part", "نجوم ✨", _part("stars", 14, True, "eq=brightness=-0.1"), motion=True)
reg("bubbles", "part", "فقاعات 🫧", _part("bubbles", 90, False), motion=True)
reg("hearts", "part", "قلوب ❤️", _part("hearts", 110, False), motion=True)
reg("confetti", "part", "كونفيتي 🎉", _part("confetti", 260, True), motion=True)
reg("embers", "part", "شرر ونار 🔥", _part("embers", 200, False, "colorbalance=rs=.1:bs=-.1"), motion=True)

# =============== الصوت ===============
reg("mute", "aud", "كتم الصوت", lambda c: [Piece("mute")], noprev=True)
reg("vol_up", "aud", "رفع الصوت", lambda c: [af("volume=2.2,alimiter=limit=0.95")], noprev=True)
reg("vol_down", "aud", "خفض الصوت", lambda c: [af("volume=0.4")], noprev=True)
reg("denoise_a", "aud", "إزالة ضجيج الصوت", lambda c: [af("highpass=f=80,afftdn=nf=-25")], noprev=True)
reg("norm", "aud", "توحيد مستوى الصوت", lambda c: [af("loudnorm=I=-16:TP=-1.5:LRA=11")], noprev=True)
reg("echo", "aud", "صدى", lambda c: [af("aecho=0.8:0.88:60|120:0.4|0.3")], noprev=True)
reg("bass", "aud", "تعزيز الجهير", lambda c: [af("bass=g=9,alimiter=limit=0.95")], noprev=True)
reg("voice_deep", "aud", "صوت غليظ", lambda c: [af("aresample=44100,asetrate=35280,aresample=44100,atempo=1.25")], noprev=True)
reg("voice_high", "aud", "صوت رفيع", lambda c: [af("aresample=44100,asetrate=57330,aresample=44100,atempo=0.7692")], noprev=True)
reg("music", "aud", "إضافة موسيقى (ملف أو رسالة صوتية)", lambda c: [Piece("music", (c.p["audio"], c.p["vol"], float(c.p.get("start", 0) or 0), bool(c.p.get("rep", 0))))],
    steps=[("audio", "🎧 أرسل ملف الموسيقى (MP3/صوت) أو رسالة صوتية حتى 20MB:"),
           ("choice", "مستوى الموسيقى:", [("🔈 منخفض", 0.4), ("🔉 متوسط", 0.8), ("🔊 عالٍ", 1.4)], "vol"),
           ("choice", "ابدأ الموسيقى من:", [("البداية", 0), ("الثانية 10", 10), ("الثانية 30", 30), ("الثانية 60", 60)], "start"),
           ("choice", "الصوت الأصلي للفيديو:", [("🎚 دمج مع الموسيقى", 0), ("🔇 استبدال بالموسيقى", 1)], "rep")], noprev=True)

# =============== تصدير خاص ===============
reg("out_gif", "out", "تحويل إلى GIF متحرك", lambda c: [Piece("out", "gif")], noprev=True)
reg("out_mp3", "out", "استخراج الصوت MP3", lambda c: [Piece("out", "mp3")], noprev=True)
reg("out_cover", "out", "صورة غلاف من الفيديو", lambda c: [Piece("out", "cover")], noprev=True)


# =============== واقعية فائقة: OpenCV (تُطبَّق في مرحلة منفصلة) ===============
import cvlib

for _id, (_fn, _name) in cvlib.CV_FUNCS.items():
    reg(_id, "cv", _name, lambda c, _i=_id: [Piece("cv", _i)], desc="معالجة إطار بإطار بمكتبة OpenCV (أبطأ من الباقي لكن نتيجتها أكثر واقعية).")


def by_cat(cat: str) -> list[Effect]:
    return [e for e in EFFECTS.values() if e.cat == cat]

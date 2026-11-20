"""25 قالباً جاهزاً: كل قالب = مجموعة تأثيرات تُطبَّق دفعة واحدة"""
T = lambda eid, **p: (eid, p)  # noqa: E731

TEMPLATES = [
    ("🎬 سينمائي هوليوود", [T("teal_orange"), T("filmgrain"), T("vignette"), T("letterbox")]),
    ("📱 ريلز ترند", [T("ratio916"), T("vivid"), T("punch")]),
    ("🕰 ذكريات قديمة", [T("vintage"), T("filmgrain"), T("vignette")]),
    ("💕 رومانسي حالم", [T("pinkish"), T("dreamy"), T("hearts")]),
    ("❄️ شتاء ثلجي", [T("ice"), T("highkey"), T("snow")]),
    ("🔥 ناري", [T("sunset"), T("embers"), T("vignette")]),
    ("🌃 نيون الليل", [T("cyber"), T("neon"), T("glow")]),
    ("📼 ريترو VHS", [T("vhs"), T("oldtv"), T("vintage")]),
    ("🖤 نوار فاخر", [T("noir"), T("filmgrain"), T("letterbox")]),
    ("🌅 غروب ذهبي", [T("golden"), T("vignette"), T("glow")]),
    ("🌧 مطر حزين", [T("cin_cool"), T("rain"), T("vignette")]),
    ("🎉 احتفال", [T("vivid"), T("confetti"), T("punch")]),
    ("✨ أحلام النجوم", [T("night"), T("stars"), T("dreamy")]),
    ("💎 HDR فاخر", [T("cv_hdr_max")]),
    ("🔬 واقعية فائقة", [T("cv_ultra"), T("r1080")]),
    ("🌊 تحت الماء", [T("underwater"), T("bubbles")]),
    ("🏜 صحراء", [T("desert"), T("cv_dehaze")]),
    ("📸 مجلة أزياء", [T("highkey"), T("matte"), T("cv_sharp")]),
    ("🌿 طبيعة", [T("forest"), T("cv_clahe"), T("enh_colors")]),
    ("⚡ غليتش تيك توك", [T("ratio916"), T("glitch"), T("punch")]),
    ("🎞 فيلم كوداك", [T("kodak"), T("filmgrain"), T("vignette")]),
    ("🌌 مزاجي داكن", [T("moody"), T("vignette"), T("filmgrain")]),
    ("🪩 حفلة", [T("hue_cycle"), T("confetti"), T("pulse")]),
    ("🏙 ليلي مضيء", [T("cv_lowlight"), T("cin_cool"), T("glow")]),
    ("🎥 احترافي كامل", [T("cv_wb"), T("cv_hdr"), T("cv_sharp"), T("r1080")]),
]
assert len(TEMPLATES) == 25

# قوالب الإيديت: تقطيع وإيقاع وحركات (تتزامن مع إيقاع صوت الفيديو/الموسيقى المضافة)
EDIT_TEMPLATES = [
    ("⚡ إيديت إيقاع سريع", [T("auto_vivid"), T("flash_beat"), T("zoom_beat")]),
    ("🎬 ريمكس سينمائي", [T("ramp_cine"), T("teal_orange"), T("filmgrain"), T("vignette"), T("letterbox")]),
    ("🖤 غليتش مظلم", [T("noir"), T("glitch_burst"), T("color_flicker")]),
    ("🌃 نيون متقلب", [T("cyber"), T("neon"), T("shake_beat")]),
    ("🔄 قلب وتقطيع", [T("auto_vivid"), T("flip_alt"), T("zoom_cut")]),
    ("🔥 HDR ناري", [T("auto_hdr"), T("embers"), T("zoom_beat")]),
    ("🌀 سلو-فاست درامي", [T("ramp_pulse"), T("cin_cool"), T("vignette")]),
    ("📱 ريلز مشرق", [T("ratio916"), T("auto_vivid"), T("zoom_beat")]),
    ("🪩 ستروب حفلة", [T("strobe"), T("hue_pulse"), T("confetti")]),
    ("😵 اهتزاز متوتر", [T("noir"), T("shake_beat"), T("glitch_burst"), T("filmgrain")]),
    ("🎨 تلوين تلقائي كامل", [T("auto_grade"), T("cv_sharp"), T("vignette")]),
    ("👑 ذهبي فاخر", [T("golden"), T("flash_beat"), T("glow"), T("letterbox")]),
]
assert len(EDIT_TEMPLATES) == 12
TEMPLATE_SETS = {"l": ("🎞 قوالب الأجواء والألوان", TEMPLATES, 5), "e": ("🔥 قوالب الإيديت والإيقاع", EDIT_TEMPLATES, 4)}

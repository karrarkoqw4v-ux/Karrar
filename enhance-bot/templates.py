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

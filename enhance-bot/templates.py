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
# قوالب مستوحاة من أسلوبين شائعين على تيك توك (ألوان وحركة، وليست نسخاً لعمل أحد)
TT_TEMPLATES = [
    ("🌌 ليل بنفسجي حالم", [T("violet_night"), T("stars"), T("glow"), T("filmgrain"), T("zoom_in")]),
    ("🌙 قمر وغموض", [T("violet_night"), T("dreamy"), T("vignette"), T("filmgrain")]),
    ("🔮 سينمائي بنفسجي هادئ", [T("violet_night"), T("cv_clahe"), T("letterbox"), T("zoom_in")]),
    ("🌃 حديقة الليل", [T("night"), T("violet_night"), T("stars"), T("glow")]),
    ("🖤 تيل داكن حزين", [T("teal_dark"), T("vignette"), T("filmgrain"), T("zoom_in")]),
    ("🩸 أحمر وأبيض درامي", [T("teal_dark"), T("red_flash"), T("glow"), T("zoom_beat")]),
    ("⚡ ضبابية وغليتش", [T("teal_dark"), T("blur_burst"), T("glitch_burst"), T("shake_beat")]),
    ("🔥 كاين إيديت مظلم", [T("teal_dark"), T("blur_burst"), T("red_flash"), T("flash_beat"), T("vignette")]),
]
# قوالب إيقاع وسينما جديدة (أساليب أصلية)
NEW_TEMPLATES = [
    ("🌅 ساعة ذهبية سينمائية", [T("golden_hour"), T("glow"), T("vignette"), T("filmgrain"), T("letterbox")]),
    ("🌙 قمر هادئ", [T("moonlight"), T("stars"), T("dreamy"), T("vignette")]),
    ("📚 أكاديمي داكن", [T("dark_academia"), T("filmgrain"), T("vignette"), T("zoom_in")]),
    ("🌃 طوكيو الليل", [T("tokyo"), T("bloom_beat"), T("glitch_burst"), T("rgb_beat")]),
    ("⚡ نبض التباين", [T("auto_vivid"), T("contrast_pulse"), T("zoom_beat")]),
    ("🎞 حرق فيلم", [T("kodak"), T("film_burn"), T("filmgrain"), T("vignette")]),
    ("🖤 وميض أبيض وأسود", [T("teal_dark"), T("bw_flash"), T("flash_beat"), T("vignette")]),
    ("🌀 زوم عكسي إيقاعي", [T("cin_cool"), T("zoom_out_beat"), T("rgb_beat"), T("glow")]),
    ("🍂 خريف دافئ", [T("autumn"), T("dreamy"), T("vignette"), T("filmgrain")]),
    ("✨ ساطع نظيف", [T("clean_bright"), T("cv_sharp"), T("bloom_beat")]),
    ("💥 انفجار ألوان", [T("hue_pulse"), T("rgb_beat"), T("zoom_beat"), T("strobe")]),
    ("🎬 ترايلر درامي", [T("teal_dark"), T("letterbox"), T("zoom_out_beat"), T("film_burn"), T("vignette")]),
    ("🔴 أحمر وحيد", [T("hold_red"), T("vignette_pulse"), T("zoom_beat"), T("filmgrain")]),
    ("🫥 ألوان تسقط", [T("auto_vivid"), T("sat_drop"), T("rgb_beat"), T("flash_beat")]),
    ("🪞 مرآة وإيقاع", [T("tokyo"), T("mirror_split"), T("vert_shake"), T("bloom_beat")]),
]
# قوالب بروح تطبيقات المونتاج العالمية (تنفيذ أصلي بالكامل)
CAPCUT_TEMPLATES = [
    ("⚡ ريمكس سريع", [T("auto_vivid"), T("whip_zoom"), T("rgb_beat"), T("flash_beat")]),
    ("🌀 سبين نبضي", [T("teal_orange"), T("spin_beat"), T("zoom_beat"), T("vignette")]),
    ("👻 ظل متحرك", [T("cyber"), T("trail"), T("glitch_burst")]),
    ("🔁 نيجاتيف خاطف", [T("moody"), T("invert_beat"), T("strobe")]),
    ("🧩 بكسل بيت", [T("vivid"), T("pixel_beat"), T("bloom_beat")]),
    ("🐌 سلو-فاست ذهبي", [T("ramp_cine"), T("golden_hour"), T("bloom_beat"), T("filmgrain")]),
    ("🏷 تاج إيديت", [T("auto_grade"), T("zoom_beat"), T("tag", text="EDIT", style="edit", pos="tl")]),
    ("🔴 REC فيلم", [T("vintage"), T("vhs"), T("tag", text="REC", style="rec", pos="tl")]),
    ("💠 نيون بيت + وسم", [T("tokyo"), T("hue_pulse"), T("glow"), T("tag", text="NEON", style="neon", pos="tr")]),
    ("🎬 هوليوود ريمكس", [T("cin_cool"), T("letterbox"), T("whip_zoom"), T("film_burn")]),
    ("📱 تيك توك ترند + وسم", [T("ratio916"), T("vivid"), T("punch"), T("tag", text="NEW", style="live", pos="tl")]),
    ("💥 غليتش وايب", [T("cyber"), T("glitch_burst"), T("rgb_beat"), T("blur_burst")]),
]
TEMPLATE_SETS = {"c": ("🎥 قوالب بروح كاب كات", CAPCUT_TEMPLATES, 4), "n": ("🆕 قوالب الإيقاع والسينما الجديدة", NEW_TEMPLATES, 5), "k": ("🎵 قوالب ستايل تيك توك", TT_TEMPLATES, 4), "l": ("🎞 قوالب الأجواء والألوان", TEMPLATES, 5), "e": ("🔥 قوالب الإيديت والإيقاع", EDIT_TEMPLATES, 4)}

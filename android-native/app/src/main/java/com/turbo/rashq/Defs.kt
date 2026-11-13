package com.turbo.rashq

data class BDef(val key: String, val name: String, val cat: String, val style: String, val emoji: String, val route: String)

object Defs {
    val titles = mapOf("main" to "القائمة الرئيسية", "rashq" to "خدمات الرشق", "games" to "الألعاب والتطبيقات",
        "points" to "اجمع نقاط مجاناً", "recharge" to "شحن رصيدك", "transfer" to "تحويل نقاط", "account" to "حسابي")
    val cats = listOf("main", "rashq", "games", "points", "recharge", "transfer", "account")

    private fun b(k: String, n: String, c: String, s: String, e: String, r: String) = BDef(k, n, c, s, e, r)
    val all: List<BDef> = listOf(
        b("btn_rashq", "خدمات الرشق", "main", "primary", "1f680", "menu:rashq"),
        b("btn_games", "الألعاب والتطبيقات", "main", "primary", "1f3ae", "menu:games"),
        b("btn_tamweel", "تمويل قناتك", "main", "primary", "1f4e2", "tamweel"),
        b("btn_recharge", "شحن رصيدك", "main", "success", "1f4b3", "menu:recharge"),
        b("btn_points", "اجمع نقاط مجاناً", "main", "success", "1f381", "menu:points"),
        b("btn_orders", "طلباتي", "main", "primary", "1f4e6", "orders"),
        b("btn_track", "تتبع طلب", "main", "primary", "1f50d", "track"),
        b("btn_account", "حسابي", "main", "primary", "1f464", "menu:account"),
        b("btn_transfer", "تحويل نقاط", "main", "primary", "1f504", "menu:transfer"),
        b("btn_code", "استخدام الكود", "main", "primary", "1f39f", "code"),
        b("btn_channels", "قنواتي الممولة", "main", "primary", "1f5c2", "channels"),
        b("btn_terms", "الشروط والأحكام", "main", "danger", "1f4c4", "terms"),
        b("btn_channel", "قناة البوت", "main", "danger", "1f4e2", "url:channel_link"),
        b("btn_smart_support", "الدعم الذكي", "main", "success", "1f916", "support"),
        b("btn_stats", "طلبات أُنجزت", "main", "success", "1f4ca", "stats"),
        // rashq platforms
        b("btn_plat_free", "خدمات مجانية", "rashq", "success", "1f381", "cat:خدمات مجانية"),
        b("btn_plat_tiktok", "تيك توك", "rashq", "primary", "1f3b5", "cat:تيك توك"),
        b("btn_plat_instagram", "انستغرام", "rashq", "primary", "1f4f8", "cat:انستغرام"),
        b("btn_plat_facebook", "فيسبوك", "rashq", "primary", "1f465", "cat:فيسبوك"),
        b("btn_plat_youtube", "يوتيوب", "rashq", "danger", "25b6", "cat:يوتيوب"),
        b("btn_plat_kwai", "كواي", "rashq", "primary", "1f3ac", "cat:كواي"),
        b("btn_plat_telegram", "تليجرام", "rashq", "primary", "2708", "cat:تليجرام"),
        b("btn_plat_threads", "ثريدز", "rashq", "primary", "1f9f5", "cat:ثريدز"),
        b("btn_plat_whatsapp", "واتساب", "rashq", "success", "1f4de", "cat:واتساب"),
        b("btn_plat_premium", "خدمات مميزة", "rashq", "primary", "1f451", "cat:خدمات مميزة"),
        b("btn_plat_tg_inter", "تفاعلات تليجرام", "rashq", "primary", "1f44d", "cat:تفاعلات تليجرام"),
        // games
        b("btn_game_pubg", "شدات ببجي موبايل", "games", "primary", "1f52b", "cat:شدات ببجي موبايل"),
        b("btn_game_stars", "نجوم تليجرام", "games", "primary", "2b50", "cat:نجوم تليجرام"),
        b("btn_game_netflix", "حسابات نتفلكس", "games", "danger", "1f4fa".let { "1f3ac" }, "cat:حسابات نتفلكس"),
        b("btn_game_ff", "جواهر فري فاير", "games", "primary", "1f525", "cat:جواهر فري فاير"),
        b("btn_game_tg_num", "ارقام تليجرام", "games", "primary", "1f4f1", "cat:ارقام تليجرام"),
        b("btn_game_premium", "تمويل اشتراك اجباري", "games", "primary", "1f4e2", "cat:تمويل اشتراك اجباري"),
        b("btn_game_verify", "تفعيل تليجرام مميز", "games", "primary", "1f48e", "cat:تفعيل تليجرام مميز"),
        b("btn_game_asiacell", "رصيد اسياسيل", "games", "success", "1f4f6", "cat:رصيد اسياسيل"),
        b("btn_game_ludo", "جواهر لودو", "games", "primary", "1f3b2", "cat:جواهر لودو"),
        b("btn_game_zain", "رصيد اثير", "games", "success", "1f4f6", "cat:رصيد اثير"),
        b("btn_game_ludo_gold", "ذهب لودو", "games", "primary", "1fa99", "cat:ذهب لودو"),
        // points
        b("btn_daily", "الهدية اليومية", "points", "success", "1f381", "gift:daily"),
        b("btn_subscribe", "الاشتراك بالقنوات", "points", "primary", "1f4e2", "subscribe"),
        b("btn_weekly", "الهدية الأسبوعية", "points", "success", "1f381", "gift:weekly"),
        b("btn_wheel", "عجلة الحظ", "points", "primary", "1f3a1", "wheel"),
        b("btn_referral", "رابط الدعوة", "points", "primary", "1f517", "referral"),
        // recharge
        b("btn_recharge_asiacell", "شحن تلقائي اسياسيل", "recharge", "success", "1f4f6", "code"),
        b("btn_recharge_stars", "شحن عبر النجوم", "recharge", "primary", "2b50", "support"),
        b("btn_recharge_support", "الشراء من فريق الدعم", "recharge", "primary", "1f4ac", "support"),
        // transfer
        b("btn_transfer_username", "اليوزر نيم", "transfer", "primary", "1f464", "transfer:username"),
        b("btn_transfer_id", "الايدي الرقمي", "transfer", "primary", "1f4be", "transfer:id"),
        // account
        b("btn_acc_orders", "طلباتي", "account", "primary", "1f4e6", "orders"),
        b("btn_acc_recharge", "شحن النقاط", "account", "success", "1f4b3", "menu:recharge"),
        b("btn_acc_collect", "تجميع النقاط", "account", "success", "1f381", "menu:points"),
        b("btn_acc_transfer", "تحويل النقاط", "account", "primary", "1f504", "menu:transfer"),
    )
    val byKey = all.associateBy { it.key }
    fun ofCat(c: String) = all.filter { it.cat == c }

    private val defaults: Map<String, List<List<String>>> = mapOf(
        "rashq" to listOf(listOf("btn_plat_free"), listOf("btn_plat_tiktok", "btn_plat_instagram"), listOf("btn_plat_facebook", "btn_plat_youtube"),
            listOf("btn_plat_kwai", "btn_plat_telegram"), listOf("btn_plat_threads", "btn_plat_whatsapp"), listOf("btn_plat_premium", "btn_plat_tg_inter")),
        "games" to listOf(listOf("btn_game_pubg", "btn_game_stars"), listOf("btn_game_netflix", "btn_game_ff"), listOf("btn_game_tg_num", "btn_game_premium"),
            listOf("btn_game_verify", "btn_game_asiacell"), listOf("btn_game_ludo", "btn_game_zain"), listOf("btn_game_ludo_gold")),
        "points" to listOf(listOf("btn_daily", "btn_subscribe"), listOf("btn_weekly", "btn_wheel"), listOf("btn_referral")),
        "recharge" to listOf(listOf("btn_recharge_asiacell"), listOf("btn_recharge_stars"), listOf("btn_recharge_support")),
        "transfer" to listOf(listOf("btn_transfer_username", "btn_transfer_id")),
        "account" to listOf(listOf("btn_acc_orders", "btn_acc_recharge"), listOf("btn_acc_collect", "btn_acc_transfer")),
        "main" to ofCat("main").map { it.key }.chunked(2),
    )
    fun defaultLayout(c: String) = defaults[c] ?: emptyList()

    fun layout(c: String): List<List<String>> {
        val raw = Repo.get("layout_$c")
        val valid = ofCat(c).map { it.key }.toSet()
        var rows = if (raw.isBlank()) defaultLayout(c) else raw.split("|").map { r -> r.split(",").filter { it.isNotBlank() } }
        rows = rows.map { r -> r.filter { it in valid } }.filter { it.isNotEmpty() }
        val used = rows.flatten().toSet()
        val missing = valid - used
        return rows + missing.map { listOf(it) }
    }
    fun saveLayout(c: String, rows: List<List<String>>) = Repo.put("layout_$c", rows.filter { it.isNotEmpty() }.joinToString("|") { it.joinToString(",") })
    fun resetLayout(c: String) { Repo.del("layout_$c"); ofCat(c).forEach { Repo.del("bn_${it.key}"); Repo.del("bs_${it.key}"); Repo.del("be_${it.key}") } }

    fun name(k: String): String { val d = byKey[k]!!; return Repo.get("bn_$k", d.name) }
    fun style(k: String): String { val d = byKey[k]!!; return Repo.get("bs_$k", d.style) }
    fun emoji(k: String): String { val d = byKey[k]!!; return Repo.get("be_$k", d.emoji) }

    /** op: up/down/left/right/join/split */
    fun move(c: String, key: String, op: String) {
        val rows = layout(c).map { it.toMutableList() }.toMutableList()
        val ri = rows.indexOfFirst { key in it }; if (ri < 0) return
        val ci = rows[ri].indexOf(key)
        when (op) {
            "up" -> if (ri > 0) { val r = rows.removeAt(ri); rows.add(ri - 1, r) }
            "down" -> if (ri < rows.size - 1) { val r = rows.removeAt(ri); rows.add(ri + 1, r) }
            "left" -> if (ci > 0) { val t = rows[ri][ci - 1]; rows[ri][ci - 1] = key; rows[ri][ci] = t }
            "right" -> if (ci < rows[ri].size - 1) { val t = rows[ri][ci + 1]; rows[ri][ci + 1] = key; rows[ri][ci] = t }
            "join" -> if (ri > 0 && rows[ri - 1].size < 3) { rows[ri].remove(key); rows[ri - 1].add(key) }
            "split" -> if (rows[ri].size > 1) { rows[ri].remove(key); rows.add(ri + 1, mutableListOf(key)) }
        }
        saveLayout(c, rows)
    }

    // store logos (bundled official brand marks); others fall back to a letter badge or an admin-chosen image
    val logos = mapOf("شدات ببجي موبايل" to "pubg", "حسابات نتفلكس" to "netflix", "نجوم تليجرام" to "telegram", "ارقام تليجرام" to "telegram",
        "تفعيل تليجرام مميز" to "telegram", "تمويل اشتراك اجباري" to "telegram", "تليجرام" to "telegram", "تفاعلات تليجرام" to "telegram",
        "تيك توك" to "tiktok", "انستغرام" to "instagram", "فيسبوك" to "facebook", "يوتيوب" to "youtube", "كواي" to "kuaishou",
        "ثريدز" to "threads", "واتساب" to "whatsapp")
    val logoColors = mapOf("pubg" to 0xFFF2A900, "netflix" to 0xFFE50914, "telegram" to 0xFF27A7E7, "tiktok" to 0xFF111111, "instagram" to 0xFFE1306C,
        "facebook" to 0xFF1877F2, "youtube" to 0xFFFF0000, "kuaishou" to 0xFFFF5000, "threads" to 0xFF111111, "whatsapp" to 0xFF25D366)
}

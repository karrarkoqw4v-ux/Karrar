package com.turbo.rashq

/** Texts copied from the bot, with its premium emoji ({e:ID}) and decoration emoji ({c:char}) markers. */
object Tx {
    const val USER = "5190558695515272787"; const val DUCK = "5301259549129325864"; const val MONEY = "5431416823637622117"; const val GIFT = "5823689256366771156"
    const val ARROW = "6039760368014724473"; const val BOX = "5974526806995242353"; const val HAT = "5325547803936572038"; const val COOL = "6006107551198874621"
    const val HASH = "5895288332581082241"; const val CHECK = "5339148771362097161"; const val RASHQ = "5247134066322447995"; const val GAMES = "5359617799515823946"
    const val DAILY = "5251227707026470504"; const val WHEEL = "5850323366476519158"; const val WEEKLY = "5472030678633684592"; const val REF = "5472091817493154825"
    const val SUB = "5197304993920616826"; const val ORDERS = "5935832686416366431"; const val TRACK = "5861877498517654958"; const val CROWN = "4956418939920843885"

    fun tags(t: String, u: User?) = t.replace("#first_name", u?.name ?: "").replace("#username", u?.username ?: "").replace("#id", u?.uid ?: "")
        .replace("#balance", (u?.balance ?: 0).toString()).replace("#points", (u?.balance ?: 0).toString())
    private fun n(v: Long) = "%,d".format(v)
    private fun cfg(k: String, d: Long) = Repo.getLong(k, d)

    fun welcome(u: User): String {
        val c = Repo.get("welcome")
        if (c.isNotBlank()) return tags(c, u)
        return "- مرحبآ بـك في تيربو رشق - Turbo Rashq [ {e:$USER} ]\n- افضل تطبيق خدمات ' الاختيار المناسب لك ' [ {e:$DUCK} ]\n- خدمات مدفوعة {e:$MONEY} مجانية {e:$GIFT}\n\n" +
            "- عدد نقاطك: ${if (Repo.pref(u.uid, "hide_bal", false)) "•••••" else n(u.balance)} [ {e:$MONEY} ]\n- ايديك: ${u.uid} [ {e:$USER} ]"
    }
    fun rashqMenu() = "{e:$RASHQ} خدمات الرشق\n\nاختر المنصة التي تريد طلب الخدمة منها: {e:$ARROW}"
    fun platPage(p: String, n: Int) = if (n > 0) "{e:$RASHQ} $p\n\n{e:$BOX} الخدمات المتاحة: $n\n\nاختر الخدمة: {e:$ARROW}" else "{e:$RASHQ} $p\n\n{c:⚠️} لا توجد خدمات حالياً في هذه المنصة."
    fun games(u: User?): String { val c = Repo.get("games_txt"); return if (c.isNotBlank()) tags(c, u) else "اختر القسم الذي تريده من الأزرار أدناه : {e:$ARROW}" }
    fun gamePage(c: String, n: Int) = if (n > 0) "{e:$GAMES} $c\n\n{e:$BOX} الخدمات المتاحة: $n\n\nاختر الخدمة: {e:$ARROW}" else "{e:$GAMES} $c\n\n{c:⚠️} لا توجد خدمات حالياً في هذا القسم."
    fun points(u: User?): String { val c = Repo.get("points_txt"); if (c.isNotBlank()) return tags(c, u)
        return "( {c:❓} ) أختر طريقة تجميع :\n\n{e:$SUB} الاشتراك بالقنوات — 10 نقطة\n{e:$DAILY} الهدية اليومية — ${cfg("gift_points", 50)} نقطة\n{e:$WHEEL} عجلة الحظ — ${cfg("wheel_min", 30)} ← ${n(cfg("wheel_max", 1000))} نقطة\n{e:$WEEKLY} الهدية الأسبوعية — ${cfg("weekly_gift_points", 100)} نقطة\n{e:$REF} رابط الدعوة — ${n(cfg("referral_points", 200))} نقطة" }
    fun recharge(u: User?): String { val c = Repo.get("recharge_txt"); if (c.isNotBlank()) return tags(c, u)
        return "{e:$MONEY} شحن النقاط {c:⚡️}\n\n" + listOf(1, 2, 3, 5, 10, 20).joinToString("\n") { "{c:💳} $it\$ ➜ ${n(it * 12000L)} نقطة" } }
    fun transfer(u: User) = "{e:5249053508681883137} تحويل النقاط\n\n{e:$MONEY} رصيدك : ${n(u.balance)} نقطة\n{c:📊} أقل تحويل : ${n(cfg("transfer_min", 60000))} نقطة\n{c:📈} العمولة : ${cfg("transfer_fee", 1)}%\n\nاختر كيف تحدّد الشخص اللي تريد التحويل له : {e:$ARROW}"
    fun account(u: User): String {
        val st = Repo.accountStats(u.uid); val ord = Repo.orders(u.uid); val done = ord.count { it.status == "مكتمل" }
        return "{e:$CROWN} حسابي\n--------------------------\n\n{c:👥} الهوية\n▪️ الاسم : ${u.name}\n▪️ اليوزر : @${u.username}\n▪️ الايدي : ${u.uid}\n▪️ العضوية : {c:✅} ${if (u.admin) "مطور" else "عضو"}\n\n" +
            "{e:$MONEY} رصيدك\n▪️ الرصيد الحالي : ${n(u.balance)} نقطة\n▪️ جمعتها مجاناً : ${n(st[0])} نقطة\n▪️ شحنتها : ${n(st[1])} نقطة\n▪️ صرفتها : ${n(st[2])} نقطة\n\n" +
            "{c:📊} نشاطك\n▪️ إجمالي طلباتك : ${ord.size}\n▪️ المكتملة : $done\n▪️ أصدقاء دعوتهم : ${st[3]}\n\n--------------------------\nتيربو رشق — Turbo Rashq"
    }
    fun terms(u: User?): String { val c = Repo.get("terms"); if (c.isNotBlank()) return tags(c, u)
        return "{c:⚠️} الشروط والتعليمات {c:⚠️}\n\n{c:✅} جميع الطلبات يحدث بها تأخر احيانآ\n{c:✅} عند مراسلتك للدعم الفني ارسل رسالتك مختصرآ\n{c:❌} لايجوز تجميع باكثر من حساب\n{c:✅} يجب قراءة وصف الخدمة جيدآ\n{c:❌} اذا طلبت بشكل خاطئ لا يوجد تعويض\n{c:❤️} التزم في كل الكلام وركز\n\nايديك: ${u?.uid ?: ""}" }
    fun tamweel(): String { val c = Repo.get("tamweel_txt"); if (c.isNotBlank()) return c
        return "[ {e:$USER} ] كل 1 عضو مقابل ${cfg("tamweel_price", 16)} نقطة\n[ {c:⚡️} ] الحد الأدنى: ${cfg("tamweel_min", 50)} · الأقصى: ${n(cfg("tamweel_max", 100000))}\n[ {c:✍️} ] أرسل الكمية:" }
    fun track() = "( {e:$TRACK} ) ارسل ايدي الطلب :\n\n{c:❕} تلكَاه في وصل طلبك، أو افتح طلباتي وانسخه من هناك."
    fun orders(n: Int) = "{e:$ORDERS} طلباتي — عدك $n طلب\n\n" + if (n == 0) "{c:⚠️} لا يوجد اي طلب حاليا لديك" else "اضغط على أي طلب لعرض تفاصيله الكاملة."
    fun detail(name: String, price: Long, min: Long, max: Long): String { val c = Repo.get("rashq_detail")
        if (c.isNotBlank()) return c.replace("#service", name).replace("#price", price.toString()).replace("#min", min.toString()).replace("#max", max.toString())
        return "( {e:$BOX} ) اسم الخدمة : $name\n( {e:$MONEY} ) السعر : $price نقطة لكل 1000\n( {e:$HAT} ) اقل طلب : $min\n( {e:$COOL} ) اكبر طلب : $max\n( {e:$HASH} ) ارسل الكمية التي تريد طلبها :" }
}

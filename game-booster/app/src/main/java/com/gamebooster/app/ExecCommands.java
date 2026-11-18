package com.gamebooster.app;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * لوحة «أوامر التنفيذ»: كل أمر shell حقيقي يُنفَّذ عبر Shizuku فعلاً (لا شيء وهمي هنا).
 * المفاتيح تبقى حتى تُلغى، والأزرار تُنفَّذ مرة واحدة وتنتهي.
 */
final class ExecCommands {
    private ExecCommands() {}

    static final int CAT_PERF = 0, CAT_NET = 1, CAT_SYS = 2;
    static final String[] CAT_NAMES = {"الأداء والفريمات", "الشبكة والاتصال", "النظام والطاقة"};

    static final class Cmd {
        final String id, title, desc; final int cat; final boolean action; // action = زر تنفيذ لمرة واحدة، وإلا مفتاح يبقى مفعّلاً
        final Function<String, String> on; final Function<String, String> off;   // pkg -> shell (قد يتجاهل pkg)
        Cmd(String id, String title, String desc, int cat, boolean action, Function<String, String> on, Function<String, String> off) {
            this.id = id; this.title = title; this.desc = desc; this.cat = cat; this.action = action; this.on = on; this.off = off;
        }
    }

    private static String q(String pkg) { return pkg == null ? "" : pkg; }

    static final List<Cmd> ALL = new ArrayList<>();
    static {
        ALL.add(new Cmd("fixedPerf", "وضع الأداء الثابت", "يطلب من النظام تثبيت أداء المعالج بدل خفضه التلقائي (cmd power).", CAT_PERF, false,
                p -> "cmd power set-fixed-performance-mode-enabled true", p -> "cmd power set-fixed-performance-mode-enabled false"));
        ALL.add(new Cmd("gameMode", "Game Mode للّعبة المختارة", "يطلب من النظام وضع الأداء الخاص بهذه اللعبة تحديداً.", CAT_PERF, false,
                p -> "cmd game mode performance " + q(p) + " || cmd game set --mode 2 --user 0 " + q(p), p -> "cmd game mode standard " + q(p)));
        ALL.add(new Cmd("adaptiveSaver", "إيقاف موفّر الطاقة التكيّفي", "لا يسمح للنظام بخفض الأداء تلقائياً حسب الاستخدام.", CAT_PERF, false,
                p -> "cmd power set-adaptive-power-saver-enabled false", p -> "cmd power set-adaptive-power-saver-enabled true"));
        ALL.add(new Cmd("lowPower", "إيقاف توفير البطارية", "يطفئ وضع توفير البطارية الذي يبطّئ كل شيء.", CAT_PERF, false,
                p -> "settings put global low_power 0", p -> null));
        ALL.add(new Cmd("hzMax", "أعلى معدّل تحديث للشاشة", "يثبّت معدّل التحديث على أعلى ما تدعمه شاشتك فعلاً.", CAT_PERF, false,
                p -> "settings put system min_refresh_rate HZMAX; settings put system peak_refresh_rate HZMAX", p -> "settings delete system min_refresh_rate; settings delete system peak_refresh_rate"));
        ALL.add(new Cmd("animScale", "تسريع حركات النظام", "يقلّل مدة حركات فتح النوافذ والقوائم إلى النصف فلا تنتظرها.", CAT_PERF, false,
                p -> "settings put global window_animation_scale 0.5; settings put global transition_animation_scale 0.5; settings put global animator_duration_scale 0.5",
                p -> "settings put global window_animation_scale 1; settings put global transition_animation_scale 1; settings put global animator_duration_scale 1"));
        ALL.add(new Cmd("touchFast", "استجابة لمس أسرع", "يقلّل مهلة الضغط المطوّل ولمسة الأصابع المتعددة.", CAT_PERF, false,
                p -> "settings put secure long_press_timeout 250; settings put secure multi_press_timeout 200",
                p -> "settings put secure long_press_timeout 400; settings put secure multi_press_timeout 300"));
        ALL.add(new Cmd("thermalReset", "إعادة ضبط حالة الحرارة", "يمسح تقييد الأداء الذي وضعه النظام بسبب الحرارة، إن كان إصدارك يدعم الأمر.", CAT_PERF, true,
                p -> "cmd thermalservice override-status 0", null));
        ALL.add(new Cmd("killAll", "إغلاق كل شيء في الخلفية الآن", "ينفّذ am kill-all ويغلق قسرياً كل تطبيق خارجي غير محمي.", CAT_PERF, true,
                p -> "am kill-all", null));
        ALL.add(new Cmd("trimCaches", "تنظيف الذاكرة المؤقتة الآن", "pm trim-caches 999G — يمسح الكاش لكل التطبيقات.", CAT_PERF, true,
                p -> "pm trim-caches 999G", null));
        ALL.add(new Cmd("standbyActive", "إبقاء اللعبة في فئة «نشطة»", "يمنع النظام من تقييد اللعبة في الخلفية (app standby bucket).", CAT_SYS, false,
                p -> "am set-standby-bucket " + q(p) + " active", p -> null));
        ALL.add(new Cmd("setInactiveFalse", "استثناء اللعبة من الخمول", "am set-inactive false — لا يضع اللعبة في وضع خمول تلقائي.", CAT_SYS, false,
                p -> "am set-inactive " + q(p) + " false", p -> "am set-inactive " + q(p) + " true"));
        ALL.add(new Cmd("dozeExit", "الخروج من وضع الخمول القسري", "dumpsys deviceidle unforce — إن كان النظام في وضع خمول دفعه للخروج منه.", CAT_SYS, true,
                p -> "dumpsys deviceidle unforce", null));
        ALL.add(new Cmd("dozeWhitelist", "إضافة اللعبة لقائمة استثناء الخمول", "تبقى اللعبة قادرة على العمل حتى لو دخل الجهاز وضع الخمول.", CAT_SYS, false,
                p -> "dumpsys deviceidle whitelist +" + q(p), p -> "dumpsys deviceidle whitelist -" + q(p)));
        ALL.add(new Cmd("stayOn", "إبقاء الشاشة مضاءة", "svc power stayon true — الشاشة لا تُطفأ أثناء اللعب.", CAT_SYS, false,
                p -> "svc power stayon true", p -> "svc power stayon false"));
        ALL.add(new Cmd("maxBrightness", "أعلى سطوع للشاشة", "يرفع السطوع إلى الحد الأقصى ويوقف التعديل التلقائي.", CAT_SYS, false,
                p -> "settings put system screen_brightness_mode 0; settings put system screen_brightness 255", p -> "settings put system screen_brightness_mode 1"));
        ALL.add(new Cmd("tcpWindow", "توسيع نافذة TCP الابتدائية", "settings put global tcp_default_init_rwnd 60 — قد يسرّع بداية تحميل بيانات اللعبة.", CAT_NET, false,
                p -> "settings put global tcp_default_init_rwnd 60", p -> "settings delete global tcp_default_init_rwnd"));
        ALL.add(new Cmd("mobileAlwaysOn", "إبقاء بيانات الجوال نشطة", "لا يطفئ الراديو الخلوي حتى وأنت على واي فاي، فيتحول الاتصال أسرع عند الحاجة.", CAT_NET, false,
                p -> "settings put global mobile_data_always_on 1", p -> "settings put global mobile_data_always_on 0"));
        ALL.add(new Cmd("wifiScanThrottle", "إيقاف تقييد فحص واي فاي", "settings put global wifi_scan_throttle_enabled 0 — استجابة شبكة أثبت أثناء اللعب.", CAT_NET, false,
                p -> "settings put global wifi_scan_throttle_enabled 0", p -> "settings put global wifi_scan_throttle_enabled 1"));
        ALL.add(new Cmd("bleScanOff", "إيقاف بحث البلوتوث بالخلفية", "ble_scan_always_enabled = 0 — يقلّل عملاً خلفياً لا تحتاجه أثناء اللعب.", CAT_NET, false,
                p -> "settings put global ble_scan_always_enabled 0", p -> "settings put global ble_scan_always_enabled 1"));
        ALL.add(new Cmd("netRestrictOff", "رفع قيود البيانات بالخلفية عن اللعبة", "يمنع تقييد شبكة اللعبة أثناء تشغيلها بالخلفية.", CAT_NET, true,
                p -> "cmd netpolicy add doze-whitelist " + q(p), null));
        ALL.add(new Cmd("forceStopOthers", "إغلاق قسري لكل التطبيقات الأخرى الآن", "يغلق كل تطبيق غير محمي ما عدا اللعبة والمسرّع.", CAT_SYS, true,
                p -> "for x in $(pm list packages -3 | cut -d: -f2); do [ \"$x\" = \"" + q(p) + "\" ] || am force-stop $x; done", null));
        ALL.add(new Cmd("clearLogs", "مسح سجلّات النظام", "logcat -c — يفرّغ ذاكرة السجلّات (لا يؤثر على اللعبة، فقط تنظيف).", CAT_SYS, true,
                p -> "logcat -c", null));
        ALL.add(new Cmd("darkMode", "تفعيل الوضع الداكن للنظام", "بعض الألعاب تحترم ثيم النظام الداكن فتقلّل سطوع واجهتها غير اللعب.", CAT_SYS, false,
                p -> "cmd uimode night yes", p -> "cmd uimode night no"));
        ALL.add(new Cmd("fstrimNow", "تحسين سرعة التخزين (fstrim)", "fstrim -v /data — يحافظ على سرعة الذاكرة الداخلية.", CAT_SYS, true,
                p -> "sm fstrim", null));
        ALL.add(new Cmd("headsUpOff", "إيقاف إشعارات النوافذ المنبثقة", "heads_up_notifications_enabled = 0 — لا تظهر نافذة إشعار فوق اللعبة حتى لو وصل إشعار.", CAT_SYS, false,
                p -> "settings put global heads_up_notifications_enabled 0", p -> "settings put global heads_up_notifications_enabled 1"));
    }
}

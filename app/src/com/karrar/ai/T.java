package com.karrar.ai;

import java.util.HashMap;

/** Tiny string table: Arabic (default) and English. */
public final class T {
    private static final HashMap<String, String> AR = new HashMap<String, String>();
    private static final HashMap<String, String> EN = new HashMap<String, String>();

    private static void a(String k, String ar, String en) { AR.put(k, ar); EN.put(k, en); }

    static {
        a("app", "كرّار AI", "Karrar AI");
        a("hi", "مرحباً، أنا كرّار.", "Hi, I'm Karrar.");
        a("help", "كيف يمكنني مساعدتك اليوم؟", "How can I help you today?");
        a("hint", "اسأل كرّار", "Message Karrar");
        a("deepthink", "تفكير عميق (R1)", "DeepThink (R1)");
        a("search", "بحث", "Search");
        a("new_chat", "محادثة جديدة", "New chat");
        a("thinking", "جارٍ التفكير…", "Thinking…");
        a("thought_for", "تم التفكير لمدة %d ثانية", "Thought for %d seconds");
        a("searching", "جارٍ البحث في الويب…", "Searching the web…");
        a("searched", "تم البحث في %d صفحات ويب", "Searched %d web pages");
        a("copy", "نسخ", "Copy");
        a("copied", "تم النسخ", "Copied");
        a("regen", "إعادة التوليد", "Regenerate");
        a("disclaimer", "المحتوى مُولَّد بالذكاء الاصطناعي وقد يحتوي على أخطاء", "AI-generated content may contain mistakes");
        a("today", "اليوم", "Today");
        a("week", "آخر 7 أيام", "Previous 7 days");
        a("earlier", "أقدم", "Earlier");
        a("no_history", "لا توجد محادثات بعد", "No conversations yet");
        a("settings", "الإعدادات", "Settings");
        a("delete", "حذف", "Delete");
        a("rename", "إعادة تسمية", "Rename");
        a("cancel", "إلغاء", "Cancel");
        a("ok", "موافق", "OK");
        a("save", "حفظ", "Save");
        a("attach_file", "إرفاق ملف نصي", "Attach a text file");
        a("paste", "لصق من الحافظة", "Paste from clipboard");
        a("share", "مشاركة", "Share");
        a("demo_badge", "وضع تجريبي: أضف مفتاح API من الإعدادات للحصول على ردود حقيقية", "Demo mode: add an API key in Settings for real answers");
        a("stopped", "تم إيقاف التوليد", "Generation stopped");
        a("guest", "مستخدم", "User");


        // login
        a("l_welcome", "أهلاً بك في كرّار", "Welcome to Karrar");
        a("l_sub", "سجّل الدخول لمتابعة محادثاتك", "Sign in to continue your chats");
        a("l_sub_up", "أنشئ حسابك في ثوانٍ", "Create your account in seconds");
        a("l_google", "تسجيل الدخول باستخدام Google", "Sign in with Google");
        a("l_or", "أو", "or");
        a("l_email", "البريد الإلكتروني", "Email");
        a("l_pass", "كلمة المرور", "Password");
        a("l_pass2", "تأكيد كلمة المرور", "Confirm password");
        a("l_name", "الاسم", "Name");
        a("l_login", "تسجيل الدخول", "Sign in");
        a("l_signup", "إنشاء حساب", "Create account");
        a("l_to_signup", "ليس لديك حساب؟ أنشئ حساباً", "No account? Create one");
        a("l_to_login", "لديك حساب؟ سجّل الدخول", "Have an account? Sign in");
        a("l_guest", "المتابعة كضيف", "Continue as guest");
        a("l_show", "إظهار", "Show");
        a("l_hide", "إخفاء", "Hide");
        a("l_opening", "جارٍ فتح Google…", "Opening Google…");
        a("l_wait", "أكمل تسجيل الدخول في المتصفح ثم ارجع للتطبيق", "Finish signing in in the browser, then come back");
        a("l_lang", "English", "العربية");
        a("e_name", "اكتب اسماً من حرفين على الأقل", "Enter a name (2+ characters)");
        a("e_email", "البريد الإلكتروني غير صحيح", "Invalid email address");
        a("e_pass_short", "كلمة المرور يجب ألا تقل عن 6 أحرف", "Password must be at least 6 characters");
        a("e_pass_match", "كلمتا المرور غير متطابقتين", "Passwords do not match");
        a("e_exists", "هذا البريد مسجّل مسبقاً", "This email is already registered");
        a("e_nouser", "لا يوجد حساب بهذا البريد", "No account with this email");
        a("e_wrong", "كلمة المرور غير صحيحة", "Wrong password");
        a("e_empty", "اكتب كلمة المرور", "Enter your password");
        a("e_bad", "حدث خطأ، حاول مرة أخرى", "Something went wrong, try again");
        a("s_email", "البريد الإلكتروني", "Email");
        a("s_logout", "تسجيل الخروج", "Sign out");
        a("s_logout_q", "هل تريد تسجيل الخروج؟", "Sign out?");
        a("s_google", "تسجيل الدخول بجوجل", "Google sign-in");
        a("s_gsecret", "Google Client Secret (اختياري)", "Google client secret (optional)");
        a("s_gsecret_sub", "مطلوب إذا رفضت جوجل الطلب بدونه", "Needed if Google rejects the request without it");

        a("tl2", "اسألني أي شيء…", "Ask me anything…");
        a("tl3", "اكتب لي كوداً…", "Write me some code…");
        a("tl4", "ابحث لي في الويب…", "Search the web for me…");
        a("tl5", "فكّر معي بعمق…", "Think deeply with me…");
        a("sg1", "اشرح لي الذكاء الاصطناعي ببساطة", "Explain AI in simple words");
        a("sg2", "اكتب لي رسالة رسمية للعمل", "Write a formal work email");
        a("sg3", "ساعدني بكتابة كود بايثون", "Help me write Python code");
        a("sg4", "لخّص لي أهم أخبار التقنية اليوم", "Summarize today's tech news");

        // settings
        a("s_account", "الحساب", "Account");
        a("s_name", "الاسم", "Name");
        a("s_appearance", "المظهر", "Appearance");
        a("s_theme", "السمة", "Theme");
        a("s_dark", "داكن", "Dark");
        a("s_light", "فاتح", "Light");
        a("s_font", "حجم الخط", "Font size");
        a("s_small", "صغير", "Small");
        a("s_medium", "متوسط", "Medium");
        a("s_large", "كبير", "Large");
        a("s_xl", "كبير جداً", "Extra large");
        a("s_lang", "اللغة", "Language");
        a("s_ai", "الذكاء الاصطناعي", "AI model");
        a("s_provider", "مزوّد الخدمة", "Provider");
        a("s_key", "مفتاح API", "API key");
        a("s_url", "رابط API", "API base URL");
        a("s_model", "النموذج", "Model");
        a("s_think_model", "نموذج التفكير العميق", "DeepThink model");
        a("s_system", "تعليمات النظام", "System prompt");
        a("s_temp", "الإبداعية (Temperature)", "Temperature");
        a("s_def_think", "تفعيل التفكير العميق افتراضياً", "DeepThink on by default");
        a("s_def_search", "تفعيل البحث افتراضياً", "Search on by default");
        a("s_connect", "التطبيقات والخدمات المتصلة", "Connected apps & services");
        a("s_connect_sub", "اختر خدمة الذكاء الاصطناعي التي تريد ربط التطبيق بها", "Choose the AI service to connect to");
        a("s_test", "اختبار الاتصال", "Test connection");
        a("s_testing", "جارٍ الاختبار…", "Testing…");
        a("s_ok", "الاتصال ناجح ✓", "Connection OK ✓");
        a("s_app", "التطبيق", "App");
        a("s_haptic", "الاهتزاز عند الإرسال", "Haptic feedback");
        a("s_save_hist", "حفظ سجل المحادثات", "Save chat history");
        a("s_stream", "عرض الرد تدريجياً", "Stream responses");
        a("s_data", "البيانات", "Data");
        a("s_export", "تصدير كل المحادثات", "Export all chats");
        a("s_clear", "حذف كل المحادثات", "Delete all chats");
        a("s_clear_q", "هل تريد حذف كل المحادثات نهائياً؟", "Delete all conversations permanently?");
        a("s_about", "حول التطبيق", "About");
        a("s_version", "الإصدار", "Version");
        a("s_not_set", "غير محدد", "Not set");
        a("s_set", "تم الضبط", "Configured");
        a("s_none", "بدون", "None");
        a("s_connected", "متصل", "Connected");
        a("s_search_src", "مصدر البحث في الويب", "Web search source");
        a("s_search_src_sub", "DuckDuckGo (بدون مفتاح)", "DuckDuckGo (no key needed)");
        a("s_restart", "سيتم تطبيق التغيير الآن", "Applying change");
        a("s_custom", "مخصّص", "Custom");
        a("s_presets", "الخدمات الجاهزة", "Ready-made services");
        a("s_current", "الاتصال الحالي", "Current connection");
    }

    public static String s(String k) {
        HashMap<String, String> m = Theme.rtl ? AR : EN;
        String v = m.get(k);
        return v == null ? k : v;
    }
}

import 'package:flutter/material.dart';
import 'characters.dart';

class TemplateDef {
  final String id;
  final String name;
  final String category;
  final String desc;
  final String filter;
  final String transition;
  final double imageSec;
  final String title;
  final String titleAnim;
  final int titlePos;
  final String titleFont;
  final List<Color> colors;
  final CharSpec char;
  const TemplateDef(this.id, this.name, this.category, this.desc, this.filter, this.transition, this.imageSec, this.title, this.titleAnim, this.titlePos, this.titleFont, this.colors, this.char);
}

const List<String> kTemplateCats = ['الكل', 'ترند', 'مناسبات', 'ميلاد وزفاف', 'حب', 'سفر', 'رياضة', 'طعام', 'أعمال', 'تعليم'];

const _pal = <List<Color>>[
  [Color(0xFF7C3AED), Color(0xFFEC4899)],
  [Color(0xFFF59E0B), Color(0xFFEF4444)],
  [Color(0xFF0F172A), Color(0xFF475569)],
  [Color(0xFF06B6D4), Color(0xFF8B5CF6)],
  [Color(0xFF22C55E), Color(0xFFF59E0B)],
  [Color(0xFFFDE68A), Color(0xFFF9A8D4)],
  [Color(0xFFEC4899), Color(0xFF60A5FA)],
  [Color(0xFF1D4ED8), Color(0xFF0EA5E9)],
  [Color(0xFFDC2626), Color(0xFFF97316)],
  [Color(0xFF16A34A), Color(0xFF65A30D)],
  [Color(0xFF0EA5E9), Color(0xFF6366F1)],
  [Color(0xFFBE185D), Color(0xFFF43F5E)],
  [Color(0xFF14B8A6), Color(0xFF3B82F6)],
  [Color(0xFF9333EA), Color(0xFF2563EB)],
];

// الاسم | الفئة | فلتر | انتقال | ثواني | عنوان | حركة العنوان | موضع | خط
const _rows = <List<Object>>[
  // ترند
  ['إيقاع سريع', 'ترند', 'vivid', 'zoom', 1.1, 'Trend', 'pop', 1, 'Anton'],
  ['ريمكس نيون', 'ترند', 'neon', 'slide', 1.4, 'NEON', 'slide', 0, 'Orbitron'],
  ['انتقال سحري', 'ترند', 'dramatic', 'radial', 1.5, 'Magic', 'fade', 1, 'Lobster'],
  ['سينمائي', 'ترند', 'dramatic', 'fade', 2.6, 'CINEMA', 'type', 1, 'BebasNeue'],
  ['حركة بطيئة', 'ترند', 'fade', 'dissolve', 2.8, 'Slow', 'fade', 2, 'PlayfairDisplay'],
  ['لقطات متتابعة', 'ترند', 'vivid', 'wipe', 1.2, 'Moments', 'slide', 2, 'Poppins'],
  ['فلوق يومي', 'ترند', 'warm', 'fade', 2.0, 'Vlog', 'pop', 0, 'Pacifico'],
  ['مقطع ريلز', 'ترند', 'vivid', 'zoom', 1.0, 'Reels', 'pop', 1, 'Righteous'],
  ['موجة الترند', 'ترند', 'cool', 'slide', 1.3, 'Wave', 'slide', 2, 'Montserrat'],
  ['بصمة ضوء', 'ترند', 'neon', 'circle', 1.6, 'LIGHT', 'fade', 1, 'Oswald'],
  ['تحوّل سريع', 'ترند', 'vivid', 'wipe', 0.9, 'Switch', 'pop', 1, 'Anton'],
  ['مرآة الذكريات', 'ترند', 'vintage', 'dissolve', 2.2, 'Mirror', 'fade', 2, 'DancingScript'],
  ['بلس', 'ترند', 'vivid', 'circle', 1.2, 'PLUS', 'pop', 0, 'Poppins'],
  ['ستوري', 'ترند', 'warm', 'slide', 1.8, 'Story', 'type', 2, 'Caveat'],
  // مناسبات
  ['عيد الفطر', 'مناسبات', 'vivid', 'circle', 2.0, 'عيدكم مبارك', 'pop', 1, 'ReemKufi'],
  ['عيد الأضحى', 'مناسبات', 'warm', 'dissolve', 2.2, 'عيد أضحى مبارك', 'fade', 1, 'Amiri'],
  ['رمضان كريم', 'مناسبات', 'warm', 'fade', 2.6, 'رمضان كريم', 'type', 1, 'ArefRuqaa'],
  ['ليلة القدر', 'مناسبات', 'dramatic', 'dissolve', 3.0, 'ليلة القدر', 'fade', 1, 'Harmattan'],
  ['يوم الأم', 'مناسبات', 'fade', 'dissolve', 2.6, 'أحبك يا أمي', 'fade', 2, 'ElMessiri'],
  ['يوم الأب', 'مناسبات', 'cool', 'fade', 2.4, 'أبي الغالي', 'slide', 2, 'Cairo'],
  ['رأس السنة', 'مناسبات', 'neon', 'zoom', 1.4, 'Happy New Year', 'pop', 1, 'Lobster'],
  ['اليوم الوطني', 'مناسبات', 'vivid', 'wipe', 1.8, 'وطني الغالي', 'slide', 1, 'NotoKufiArabic'],
  ['يوم المعلم', 'مناسبات', 'warm', 'fade', 2.4, 'شكراً أستاذي', 'fade', 2, 'Almarai'],
  ['شكراً', 'مناسبات', 'warm', 'dissolve', 2.2, 'شكراً لكم', 'pop', 1, 'Changa'],
  ['تهنئة', 'مناسبات', 'vivid', 'circle', 1.8, 'ألف مبروك', 'pop', 1, 'Lalezar'],
  ['الأفراح', 'مناسبات', 'fade', 'dissolve', 2.4, 'فرحة العمر', 'fade', 2, 'Amiri'],
  ['العودة للدراسة', 'مناسبات', 'vivid', 'slide', 1.6, 'عام دراسي جديد', 'slide', 0, 'Cairo'],
  ['يوم المرأة', 'مناسبات', 'fade', 'dissolve', 2.4, 'كل عام وأنتِ بخير', 'fade', 2, 'ElMessiri'],
  // ميلاد وزفاف
  ['عيد ميلاد سعيد', 'ميلاد وزفاف', 'vivid', 'wipe', 1.8, 'عيد ميلاد سعيد', 'pop', 1, 'Lalezar'],
  ['حفلة مفاجأة', 'ميلاد وزفاف', 'vivid', 'zoom', 1.3, 'Surprise!', 'pop', 1, 'PermanentMarker'],
  ['دعوة زفاف', 'ميلاد وزفاف', 'fade', 'dissolve', 2.8, 'دعوة زفاف', 'fade', 1, 'Amiri'],
  ['ليلة الحناء', 'ميلاد وزفاف', 'warm', 'fade', 2.4, 'ليلة الحناء', 'fade', 2, 'ArefRuqaa'],
  ['خطوبة', 'ميلاد وزفاف', 'fade', 'circle', 2.4, 'مبروك الخطوبة', 'pop', 1, 'ElMessiri'],
  ['زفاف رومانسي', 'ميلاد وزفاف', 'fade', 'dissolve', 3.0, 'Forever', 'fade', 2, 'DancingScript'],
  ['ذكرى زواج', 'ميلاد وزفاف', 'vintage', 'dissolve', 2.6, 'ذكرى سنوية', 'fade', 2, 'Amiri'],
  ['مولود جديد', 'ميلاد وزفاف', 'fade', 'fade', 2.6, 'مبروك المولود', 'pop', 1, 'Changa'],
  ['كعكة العيد', 'ميلاد وزفاف', 'vivid', 'circle', 1.8, 'Cake Time', 'pop', 0, 'Pacifico'],
  ['شموع الأمنيات', 'ميلاد وزفاف', 'warm', 'dissolve', 2.4, 'تمنَّ أمنية', 'type', 1, 'Katibeh'],
  // حب
  ['حبيبي', 'حب', 'warm', 'dissolve', 2.4, 'أحبك', 'pop', 1, 'ReemKufi'],
  ['قلبان', 'حب', 'fade', 'circle', 2.2, 'Love', 'fade', 1, 'Satisfy'],
  ['رسالة حب', 'حب', 'vintage', 'fade', 2.6, 'إليك...', 'type', 1, 'ArefRuqaa'],
  ['لقاء', 'حب', 'warm', 'slide', 2.0, 'ملتقانا', 'slide', 2, 'Cairo'],
  ['رومانسي كلاسيك', 'حب', 'vintage', 'dissolve', 2.8, 'Romance', 'fade', 2, 'PlayfairDisplay'],
  ['عشاق', 'حب', 'fade', 'dissolve', 2.4, 'عشق', 'pop', 1, 'Lalezar'],
  ['اعتراف', 'حب', 'warm', 'fade', 2.6, 'أريد أن أقول', 'type', 1, 'Katibeh'],
  ['وعد', 'حب', 'fade', 'dissolve', 2.6, 'وعد', 'fade', 1, 'Amiri'],
  // سفر
  ['رحلة', 'سفر', 'vivid', 'slide', 1.5, 'Trip', 'slide', 2, 'Montserrat'],
  ['شاطئ', 'سفر', 'cool', 'wipe', 1.8, 'Beach', 'fade', 2, 'Pacifico'],
  ['جبال', 'سفر', 'dramatic', 'fade', 2.4, 'Mountains', 'fade', 1, 'Oswald'],
  ['مدينة ليلاً', 'سفر', 'neon', 'zoom', 1.4, 'City Lights', 'pop', 0, 'Orbitron'],
  ['مغامرة', 'سفر', 'vivid', 'radial', 1.3, 'Adventure', 'pop', 1, 'Anton'],
  ['مخيم', 'سفر', 'warm', 'dissolve', 2.2, 'Camp', 'fade', 2, 'Caveat'],
  ['سفاري', 'سفر', 'warm', 'slide', 1.8, 'Safari', 'slide', 1, 'Righteous'],
  ['صيف', 'سفر', 'vivid', 'circle', 1.4, 'Summer', 'pop', 0, 'Lobster'],
  ['شتاء', 'سفر', 'cool', 'dissolve', 2.2, 'Winter', 'fade', 1, 'DancingScript'],
  ['خريف', 'سفر', 'warm', 'fade', 2.4, 'Autumn', 'fade', 2, 'PlayfairDisplay'],
  // رياضة
  ['كمال أجسام', 'رياضة', 'dramatic', 'zoom', 1.1, 'POWER', 'pop', 1, 'Anton'],
  ['كرة قدم', 'رياضة', 'vivid', 'slide', 1.3, 'GOAL', 'pop', 0, 'BebasNeue'],
  ['جري', 'رياضة', 'vivid', 'wipe', 1.2, 'Run', 'slide', 2, 'Oswald'],
  ['يوغا', 'رياضة', 'fade', 'dissolve', 3.0, 'Balance', 'fade', 1, 'Raleway'],
  ['سباق', 'رياضة', 'dramatic', 'wipe', 1.0, 'RACE', 'slide', 1, 'Orbitron'],
  ['تحدي', 'رياضة', 'vivid', 'radial', 1.2, 'تحدي', 'pop', 1, 'Lalezar'],
  ['بطولة', 'رياضة', 'dramatic', 'zoom', 1.4, 'Champion', 'pop', 1, 'AbrilFatface'],
  ['ألعاب فيديو', 'رياضة', 'neon', 'zoom', 1.0, 'GAME ON', 'pop', 0, 'Orbitron'],
  ['فوز', 'رياضة', 'vivid', 'circle', 1.4, 'فزنا!', 'pop', 1, 'Changa'],
  ['تدريب', 'رياضة', 'dramatic', 'wipe', 1.2, 'Train', 'slide', 2, 'Anton'],
  // طعام
  ['وصفة', 'طعام', 'warm', 'dissolve', 2.0, 'وصفة اليوم', 'fade', 2, 'Cairo'],
  ['مطعم', 'طعام', 'warm', 'fade', 2.2, 'Restaurant', 'fade', 1, 'PlayfairDisplay'],
  ['قهوة', 'طعام', 'vintage', 'dissolve', 2.4, 'Coffee', 'fade', 2, 'DancingScript'],
  ['حلويات', 'طعام', 'vivid', 'circle', 1.8, 'حلويات', 'pop', 1, 'Lalezar'],
  ['بيتزا', 'طعام', 'vivid', 'zoom', 1.4, 'PIZZA', 'pop', 0, 'Anton'],
  ['شواء', 'طعام', 'warm', 'wipe', 1.6, 'BBQ', 'slide', 1, 'Righteous'],
  ['إفطار', 'طعام', 'warm', 'fade', 2.2, 'صباح الخير', 'fade', 2, 'Almarai'],
  ['عصائر', 'طعام', 'vivid', 'dissolve', 1.8, 'Fresh', 'pop', 1, 'Pacifico'],
  // أعمال
  ['عرض خاص', 'أعمال', 'vivid', 'wipe', 1.4, 'عرض خاص', 'pop', 0, 'Changa'],
  ['افتتاح متجر', 'أعمال', 'vivid', 'zoom', 1.8, 'افتتاح قريباً', 'type', 1, 'ReemKufi'],
  ['خصومات', 'أعمال', 'dramatic', 'radial', 1.3, 'خصم 50%', 'pop', 1, 'Lalezar'],
  ['منتج جديد', 'أعمال', 'cool', 'circle', 1.6, 'NEW', 'pop', 1, 'Montserrat'],
  ['شركة ناشئة', 'أعمال', 'cool', 'slide', 1.8, 'Startup', 'slide', 2, 'Poppins'],
  ['مقابلة', 'أعمال', 'fade', 'fade', 2.4, 'Interview', 'fade', 2, 'Inter'],
  ['عرض تقديمي', 'أعمال', 'cool', 'slide', 2.0, 'Presentation', 'slide', 0, 'Raleway'],
  ['توظيف', 'أعمال', 'vivid', 'wipe', 1.8, 'نحن نوظّف', 'pop', 1, 'Cairo'],
  ['شكراً للعملاء', 'أعمال', 'warm', 'dissolve', 2.2, 'شكراً لثقتكم', 'fade', 1, 'Almarai'],
  ['تخفيضات', 'أعمال', 'vivid', 'zoom', 1.2, 'SALE', 'pop', 0, 'Anton'],
  // تعليم
  ['تخرّج', 'تعليم', 'cool', 'slide', 2.2, 'مبروك التخرج', 'slide', 2, 'Cairo'],
  ['درس', 'تعليم', 'cool', 'fade', 2.4, 'درس اليوم', 'fade', 0, 'Almarai'],
  ['دورة تدريبية', 'تعليم', 'vivid', 'wipe', 1.8, 'دورة', 'pop', 1, 'Changa'],
  ['امتحانات', 'تعليم', 'dramatic', 'fade', 2.0, 'بالتوفيق', 'fade', 1, 'ElMessiri'],
  ['مدرسة', 'تعليم', 'vivid', 'circle', 1.8, 'School', 'pop', 0, 'Poppins'],
  ['مكتبة', 'تعليم', 'vintage', 'dissolve', 2.4, 'Library', 'fade', 2, 'Merriweather'],
];

final List<TemplateDef> kTemplates = () {
  final out = <TemplateDef>[];
  for (var i = 0; i < _rows.length; i++) {
    final r = _rows[i];
    final cols = _pal[(i * 3 + 1) % _pal.length];
    out.add(TemplateDef('t${i + 1}', r[0] as String, r[1] as String, 'فلتر ${r[2]} • انتقال ${r[3]} • ${r[4]} ث', r[2] as String, r[3] as String, (r[4] as num).toDouble(), r[5] as String, r[6] as String, r[7] as int, r[8] as String, cols, charFor(i + 3, cols)));
  }
  return out;
}();

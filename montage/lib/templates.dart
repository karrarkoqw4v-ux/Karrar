import 'package:flutter/material.dart';

class TemplateDef {
  final String id;
  final String name;
  final String category; // ترند، مناسبات، إعلانات
  final String emoji;
  final List<Color> colors;
  final String filter;
  final String transition;
  final double imageSec;
  final String? title; // نص تلقائي
  final String titleAnim;
  final int titlePos;
  final int minClips;
  const TemplateDef(this.id, this.name, this.category, this.emoji, this.colors, this.filter, this.transition, this.imageSec,
      {this.title, this.titleAnim = 'pop', this.titlePos = 1, this.minClips = 3});
}

const List<String> kTemplateCats = ['الكل', 'ترند', 'مناسبات', 'إعلانات'];

const List<TemplateDef> kTemplates = [
  TemplateDef('t1', 'إيقاع سريع', 'ترند', '⚡', [Color(0xFF7C3AED), Color(0xFFEC4899)], 'vivid', 'zoom', 1.2, title: 'Trend', titleAnim: 'pop'),
  TemplateDef('t2', 'ذكريات دافئة', 'ترند', '🌅', [Color(0xFFF59E0B), Color(0xFFEF4444)], 'warm', 'dissolve', 2.2, title: 'ذكريات', titleAnim: 'fade', titlePos: 2),
  TemplateDef('t3', 'سينمائي', 'ترند', '🎬', [Color(0xFF0F172A), Color(0xFF475569)], 'dramatic', 'fade', 2.6, title: 'CINEMA', titleAnim: 'type', titlePos: 1),
  TemplateDef('t4', 'نيون ليلي', 'ترند', '🌃', [Color(0xFF06B6D4), Color(0xFF8B5CF6)], 'neon', 'slide', 1.6, title: 'NIGHT', titleAnim: 'slide', titlePos: 0),
  TemplateDef('t5', 'عيد سعيد', 'مناسبات', '🎉', [Color(0xFF22C55E), Color(0xFFF59E0B)], 'vivid', 'circle', 2.0, title: 'عيدكم مبارك', titleAnim: 'pop', titlePos: 1),
  TemplateDef('t6', 'حفل زفاف', 'مناسبات', '💍', [Color(0xFFFDE68A), Color(0xFFF9A8D4)], 'fade', 'dissolve', 2.8, title: 'مبروك', titleAnim: 'fade', titlePos: 2),
  TemplateDef('t7', 'يوم ميلاد', 'مناسبات', '🎂', [Color(0xFFEC4899), Color(0xFF60A5FA)], 'vivid', 'wipe', 1.8, title: 'عيد ميلاد سعيد', titleAnim: 'pop', titlePos: 1),
  TemplateDef('t8', 'تخرّج', 'مناسبات', '🎓', [Color(0xFF1D4ED8), Color(0xFF0EA5E9)], 'cool', 'slide', 2.2, title: 'مبروك التخرج', titleAnim: 'slide', titlePos: 2),
  TemplateDef('t9', 'عرض منتج', 'إعلانات', '🛍️', [Color(0xFFEF4444), Color(0xFFF97316)], 'vivid', 'wipe', 1.5, title: 'عرض خاص', titleAnim: 'pop', titlePos: 0),
  TemplateDef('t10', 'افتتاح متجر', 'إعلانات', '🏪', [Color(0xFF16A34A), Color(0xFF65A30D)], 'vivid', 'zoom', 1.8, title: 'افتتاح قريباً', titleAnim: 'type', titlePos: 1),
  TemplateDef('t11', 'خصومات', 'إعلانات', '🏷️', [Color(0xFFDC2626), Color(0xFF7C3AED)], 'dramatic', 'radial', 1.4, title: 'خصم 50%', titleAnim: 'pop', titlePos: 1),
  TemplateDef('t12', 'كلاسيكي', 'ترند', '📽️', [Color(0xFF78350F), Color(0xFFD97706)], 'vintage', 'fade', 2.4, title: 'Memories', titleAnim: 'fade', titlePos: 2),
];

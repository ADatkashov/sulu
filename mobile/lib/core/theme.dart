import 'package:flutter/material.dart';

/// SULU design tokens (same palette as the web app).
class C {
  static const pink = Color(0xFFE94E9B);
  static const primary = Color(0xFFD13B86);
  static const rose = Color(0xFFC7357D);
  static const roseDeep = Color(0xFFA82A68);
  static const bg = Color(0xFFFFFAFC);
  static const bgSoft = Color(0xFFFFF4F8);
  static const lav = Color(0xFFDCCBFA);
  static const lavSoft = Color(0xFFF3EDFF);
  static const text = Color(0xFF29232D);
  static const muted = Color(0xFF6F6573);
  static const border = Color(0xFFF0E5ED);
  static const success = Color(0xFF1E7F5E);
  static const successBg = Color(0xFFE3F6EE);
  static const warning = Color(0xFF9A6410);
  static const warningBg = Color(0xFFFDF1DC);
  static const error = Color(0xFFC93454);
  static const errorBg = Color(0xFFFDE8EC);
  static const grad = LinearGradient(colors: [Color(0xFFF06AAE), primary], begin: Alignment.topLeft, end: Alignment.bottomRight);
  static const gradSoft = LinearGradient(colors: [Color(0xFFFDE4F1), Color(0xFFEADFFD), Color(0xFFDFF3FF)], begin: Alignment.topLeft, end: Alignment.bottomRight);
}

const serif = 'serif';

ThemeData buildTheme() {
  final base = ThemeData(useMaterial3: true, colorScheme: ColorScheme.fromSeed(seedColor: C.primary, primary: C.primary, surface: C.bg));
  return base.copyWith(
    scaffoldBackgroundColor: C.bg,
    appBarTheme: const AppBarTheme(backgroundColor: C.bg, surfaceTintColor: Colors.transparent, elevation: 0, scrolledUnderElevation: 0, foregroundColor: C.text,
        titleTextStyle: TextStyle(fontFamily: serif, fontSize: 20, fontWeight: FontWeight.w700, color: C.text)),
    textTheme: base.textTheme.apply(bodyColor: C.text, displayColor: C.text),
    inputDecorationTheme: InputDecorationTheme(
      filled: true, fillColor: Colors.white, contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      border: OutlineInputBorder(borderRadius: BorderRadius.circular(16), borderSide: const BorderSide(color: C.border, width: 1.5)),
      enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(16), borderSide: const BorderSide(color: C.border, width: 1.5)),
      focusedBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(16), borderSide: const BorderSide(color: C.pink, width: 1.5)),
    ),
    navigationBarTheme: NavigationBarThemeData(backgroundColor: Colors.white, indicatorColor: C.bgSoft, surfaceTintColor: Colors.transparent,
        labelTextStyle: WidgetStateProperty.resolveWith((s) => TextStyle(fontSize: 11, fontWeight: s.contains(WidgetState.selected) ? FontWeight.w700 : FontWeight.w500, color: s.contains(WidgetState.selected) ? C.primary : C.muted)),
        iconTheme: WidgetStateProperty.resolveWith((s) => IconThemeData(color: s.contains(WidgetState.selected) ? C.primary : C.muted))),
  );
}

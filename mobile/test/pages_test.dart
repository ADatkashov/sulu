import 'dart:convert';
import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sulu_app/core/api.dart';
import 'package:sulu_app/core/format.dart';
import 'package:sulu_app/main.dart';
import 'package:sulu_app/state/app_state.dart';

/// Real API responses captured from the running backend (test/fixtures) drive every detail page.
String fx(String n) => File('test/fixtures/$n.json').readAsStringSync();
http.Response ok(String body) => http.Response.bytes(utf8.encode(body), 200, headers: {'content-type': 'application/json'});

Future<AppState> boot() async {
  initTz();
  SharedPreferences.setMockInitialValues({'sulu.lang': 'ru', 'sulu.city': 'Алматы', 'sulu.onboarded': true});
  final prefs = await SharedPreferences.getInstance();
  return AppState(prefs, Api(prefs, client: MockClient((req) async {
    final p = req.url.path;
    if (p == '/api/config') return ok('{"defaultCity":"Алматы","cities":["Алматы"],"simulatedPayments":true}');
    if (p == '/api/reviews') return ok(fx('reviews'));
    if (p == '/api/availability/next') return ok(fx('next'));
    if (p == '/api/availability') return ok(fx('slots'));
    if (p == '/api/services/1') return ok(fx('service'));
    if (p == '/api/businesses/1') return ok(fx('salon'));
    if (p == '/api/professionals/1') return ok(fx('master'));
    return http.Response('{"code":"NOT_FOUND"}', 404);
  })));
}

void main() {
  for (final (route, expected) in [('/service/1', 'Женская стрижка'), ('/salon/1', 'Aru Beauty Studio'), ('/master/1', 'Анастасия Демо'), ('/book/1', 'Запись')]) {
    testWidgets('detail page $route renders real API data without exceptions', (t) async {
      await t.binding.setSurfaceSize(const Size(390, 1400));
      final s = await boot();
      final app = SuluApp(s); await t.pumpWidget(app); await t.pumpAndSettle();
      final router = (t.state(find.byType(SuluApp)) as dynamic).router;
      router.go(route); await t.pumpAndSettle();
      expect(find.text(expected), findsWidgets);
      expect(t.takeException(), isNull);
    });
  }

  testWidgets('primary action buttons keep a normal height inside bottomNavigationBar (regression: button filled the screen)', (t) async {
    await t.binding.setSurfaceSize(const Size(390, 844));
    final s = await boot();
    await t.pumpWidget(SuluApp(s)); await t.pumpAndSettle();
    (t.state(find.byType(SuluApp)) as dynamic).router.go('/service/1'); await t.pumpAndSettle();
    final h = t.getSize(find.widgetWithText(InkWell, 'Записаться').first).height;
    expect(h, inInclusiveRange(44, 64));
  });
}

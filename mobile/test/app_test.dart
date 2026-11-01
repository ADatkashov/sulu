import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sulu_app/core/api.dart';
import 'package:sulu_app/core/format.dart';
import 'package:sulu_app/core/i18n.dart';
import 'package:sulu_app/core/strings.g.dart';
import 'package:sulu_app/main.dart';
import 'package:sulu_app/state/app_state.dart';

http.Response json(Object o, [int status = 200]) => http.Response.bytes(utf8.encode(jsonEncode(o)), status, headers: {'content-type': 'application/json'});

void main() {
  group('i18n', () {
    test('lookup falls back kk → ru → en and fills params', () {
      expect(tr('ru', 'nav.home'), 'Главная');
      expect(tr('kk', 'nav.home'), 'Басты');
      expect(tr('en', 'svc.policyText', {'h': 24}), contains('24'));
      expect(errText('en', 'SLOT_UNAVAILABLE'), 'This time is no longer available.');
      expect(errText('en', 'SOMETHING_NEW'), tr('en', 'err.generic'));
    });
    test('every customer-facing key exists in ru and kk', () {
      for (final k in stringsEn.keys) {
        expect(stringsRu[k], isNotNull, reason: 'ru $k');
        expect(stringsKk[k], isNotNull, reason: 'kk $k');
      }
    });
  });

  group('format', () {
    test('money uses integer minor units', () {
      expect(money(650000, 'KZT', 'ru'), contains('6'));
      expect(money(1999, 'USD', 'en'), contains('19.99'));
    });
    test('dates are computed in the business time zone', () {
      final at = DateTime.utc(2026, 10, 31, 22);
      expect(ymdInZone(0, 'Asia/Almaty', at), '2026-11-01');
      expect(ymdInZone(0, 'America/New_York', at), '2026-10-31');
      expect(fmtTime('2026-11-02T05:00:00Z', 'Asia/Almaty'), '10:00');
    });
    test('qs skips empty values and encodes', () => expect(qs({'a': 1, 'b': '', 'c': null, 'd': 'x y', 'e': false}), '?a=1&d=x+y'));
  });

  group('Api', () {
    test('refreshes an expired access token once and retries', () async {
      SharedPreferences.setMockInitialValues({'sulu.session': jsonEncode({'accessToken': 'old', 'refreshToken': 'r1', 'user': {'id': 1}})});
      final prefs = await SharedPreferences.getInstance(); final calls = <String>[];
      final api = Api(prefs, client: MockClient((req) async {
        calls.add('${req.method} ${req.url.path} ${req.headers['Authorization'] ?? ''}');
        if (req.url.path == '/api/auth/refresh') return json({'accessToken': 'new', 'refreshToken': 'r2', 'user': {'id': 1}});
        return req.headers['Authorization'] == 'Bearer new' ? json({'ok': true}) : json({'code': 'UNAUTHORIZED'}, 401);
      }));
      expect((await api.get('/api/me'))['ok'], true);
      expect(calls.where((c) => c.contains('/refresh')).length, 1);
      expect(api.session!.accessToken, 'new');
    });
    test('maps error envelope to ApiException with code and details', () async {
      SharedPreferences.setMockInitialValues({});
      final api = Api(await SharedPreferences.getInstance(), client: MockClient((_) async => json({'code': 'SLOT_UNAVAILABLE', 'message': 'x', 'details': {'alternatives': []}}, 409)));
      await expectLater(api.post('/api/bookings', {}), throwsA(isA<ApiException>().having((e) => e.code, 'code', 'SLOT_UNAVAILABLE').having((e) => e.status, 'status', 409)));
    });
  });

  group('app', () {
    Future<AppState> boot({bool onboarded = true}) async {
      initTz();
      SharedPreferences.setMockInitialValues({'sulu.lang': 'en', 'sulu.city': 'Алматы', 'sulu.onboarded': onboarded});
      final prefs = await SharedPreferences.getInstance();
      final api = Api(prefs, client: MockClient((req) async {
        final p = req.url.path;
        if (p == '/api/config') return json({'defaultCity': 'Алматы', 'defaultCurrency': 'KZT', 'cities': ['Алматы'], 'simulatedPayments': false});
        if (p == '/api/categories') return json([{'id': 1, 'slug': 'haircut', 'nameKk': 'Шаш қиюы', 'nameRu': 'Стрижки', 'nameEn': 'Haircuts'}]);
        if (p == '/api/businesses') return json({'items': [{'id': 1, 'name': 'Aru Beauty Studio', 'ratingAvg': 4.6, 'ratingCount': 12, 'address': 'Demo st 1', 'isDemo': true, 'minPrice': 450000, 'currency': 'KZT'}], 'page': 0, 'size': 8, 'total': 1});
        if (p == '/api/professionals') return json({'items': [{'id': 1, 'fullName': 'Anna Demo', 'title': 'Stylist', 'ratingAvg': 4.8, 'ratingCount': 4}], 'page': 0, 'size': 8, 'total': 1});
        if (p == '/api/services') return json({'items': [{'id': 1, 'name': 'Haircut', 'businessName': 'Aru Beauty Studio', 'priceMinor': 800000, 'currency': 'KZT', 'durationMin': 60, 'businessRating': 4.6, 'businessRatingCount': 12, 'categorySlug': 'haircut'}], 'page': 0, 'size': 5, 'total': 1});
        return json({'code': 'NOT_FOUND'}, 404);
      }));
      return AppState(prefs, api);
    }
    testWidgets('onboarding is shown first and can be skipped to the home screen', (t) async {
      await t.binding.setSurfaceSize(const Size(390, 844));
      final s = await boot(onboarded: false);
      await t.pumpWidget(SuluApp(s)); await t.pumpAndSettle();
      expect(find.text('Discover beauty nearby'), findsOneWidget);
      await t.tap(find.text('Skip')); await t.pumpAndSettle();
      expect(find.text('Popular salons'), findsOneWidget);
    });
    testWidgets('home renders real API data (salons, masters, services) with a DEMO label', (t) async {
      await t.binding.setSurfaceSize(const Size(390, 1600));
      final s = await boot();
      await t.pumpWidget(SuluApp(s)); await t.pumpAndSettle();
      expect(find.text('Aru Beauty Studio'), findsWidgets);
      expect(find.text('Anna Demo'), findsOneWidget);
      expect(find.text('DEMO'), findsWidgets);
      expect(find.text('Haircuts'), findsOneWidget);
    });
    testWidgets('bookings tab asks guests to sign in instead of calling the API', (t) async {
      await t.binding.setSurfaceSize(const Size(390, 844));
      final s = await boot();
      await t.pumpWidget(SuluApp(s)); await t.pumpAndSettle();
      await t.tap(find.text('Bookings')); await t.pumpAndSettle();
      expect(find.text('Sign in'), findsOneWidget);
    });
    testWidgets('language switch re-renders in Kazakh', (t) async {
      await t.binding.setSurfaceSize(const Size(390, 844));
      final s = await boot();
      await t.pumpWidget(SuluApp(s)); await t.pumpAndSettle();
      await s.setLocale('kk'); await t.pumpAndSettle();
      expect(find.text('Басты'), findsOneWidget);
    });
  });
}

import 'package:flutter/material.dart';
import 'package:flutter/semantics.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:provider/provider.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'core/api.dart';
import 'core/format.dart';
import 'core/theme.dart';
import 'screens/auth.dart';
import 'screens/book.dart';
import 'screens/bookings.dart';
import 'screens/details.dart';
import 'screens/explore.dart';
import 'screens/home.dart';
import 'screens/me.dart';
import 'state/app_state.dart';
import 'widgets/common.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  initTz();
  await initializeDateFormatting(); // locale data for kk_KZ / ru_RU / en_GB date formats
  // E2E/accessibility tooling: --dart-define=SEMANTICS=true keeps the semantics tree always on.
  if (const bool.fromEnvironment('SEMANTICS')) SemanticsBinding.instance.ensureSemantics();
  final prefs = await SharedPreferences.getInstance();
  runApp(SuluApp(AppState(prefs, Api(prefs))));
}

GoRouter buildRouter(AppState s) => GoRouter(
  refreshListenable: s,
  redirect: (ctx, st) => (!s.onboarded && st.uri.path == '/') ? '/onboarding' : null,
  routes: [
    GoRoute(path: '/onboarding', builder: (_, __) => const Onboarding()),
    GoRoute(path: '/login', builder: (_, st) => Login(next: st.uri.queryParameters['next'])),
    GoRoute(path: '/register', builder: (_, st) => Register(next: st.uri.queryParameters['next'])),
    ShellRoute(builder: (ctx, st, child) => MainShell(child: child, path: st.uri.path), routes: [
      GoRoute(path: '/', builder: (_, __) => const Home()),
      GoRoute(path: '/explore', builder: (_, st) => Explore(key: ValueKey(st.uri.toString()), q: st.uri.queryParameters['q'] ?? '', category: st.uri.queryParameters['category'] ?? '', tab: st.uri.queryParameters['tab'] ?? 'services')),
      GoRoute(path: '/bookings', builder: (_, __) => const BookingsPage()),
      GoRoute(path: '/favorites', builder: (_, __) => const FavoritesPage()),
      GoRoute(path: '/profile', builder: (_, __) => const ProfilePage()),
    ]),
    GoRoute(path: '/salon/:id', builder: (_, st) => SalonPage(int.parse(st.pathParameters['id']!))),
    GoRoute(path: '/master/:id', builder: (_, st) => MasterPage(int.parse(st.pathParameters['id']!))),
    GoRoute(path: '/service/:id', builder: (_, st) => ServicePage(int.parse(st.pathParameters['id']!))),
    GoRoute(path: '/book/:id', builder: (_, st) => BookPage(int.parse(st.pathParameters['id']!), pro: int.tryParse(st.uri.queryParameters['pro'] ?? ''))),
    GoRoute(path: '/booked/:id', builder: (_, st) => BookedPage(int.parse(st.pathParameters['id']!), pay: st.uri.queryParameters['pay'] == '1')),
    GoRoute(path: '/bookings/:id', builder: (_, st) => BookingDetail(int.parse(st.pathParameters['id']!))),
    GoRoute(path: '/notifications', builder: (_, __) => const NotificationsPage()),
  ],
);

class SuluApp extends StatefulWidget { const SuluApp(this.state, {super.key}); final AppState state; @override State<SuluApp> createState() => _SuluAppState(); }
class _SuluAppState extends State<SuluApp> {
  late final router = buildRouter(widget.state);
  @override Widget build(BuildContext context) => ChangeNotifierProvider.value(value: widget.state, child: Consumer<AppState>(builder: (_, s, __) => MaterialApp.router(
    title: 'SULU', debugShowCheckedModeBanner: false, theme: buildTheme(), routerConfig: router, locale: Locale(s.locale),
    supportedLocales: const [Locale('kk'), Locale('ru'), Locale('en')],
    localizationsDelegates: GlobalMaterialLocalizations.delegates)));
}

class MainShell extends StatelessWidget {
  const MainShell({super.key, required this.child, required this.path});
  final Widget child; final String path;
  static const tabs = ['/', '/explore', '/bookings', '/favorites', '/profile'];
  @override Widget build(BuildContext context) {
    final a = app(context); final idx = tabs.indexOf(path).clamp(0, 4);
    return Scaffold(body: child, bottomNavigationBar: NavigationBar(selectedIndex: idx, onDestinationSelected: (i) => context.go(tabs[i]), destinations: [
      NavigationDestination(icon: const Icon(Icons.home_outlined), selectedIcon: const Icon(Icons.home), label: a.t('nav.home')),
      NavigationDestination(icon: const Icon(Icons.search), label: a.t('nav.explore')),
      NavigationDestination(icon: const Icon(Icons.calendar_month_outlined), selectedIcon: const Icon(Icons.calendar_month), label: a.t('nav.bookings')),
      NavigationDestination(icon: const Icon(Icons.favorite_border), selectedIcon: const Icon(Icons.favorite), label: a.t('nav.favorites')),
      NavigationDestination(icon: const Icon(Icons.person_outline), selectedIcon: const Icon(Icons.person), label: a.t('nav.profile')),
    ]));
  }
}

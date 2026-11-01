import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../core/api.dart';
import '../core/i18n.dart';

class AppState extends ChangeNotifier {
  AppState(this.prefs, this.api) {
    locale = prefs.getString('sulu.lang') ?? 'ru';
    city = prefs.getString('sulu.city') ?? '';
    onboarded = prefs.getBool('sulu.onboarded') ?? false;
    api.onSession = (_) { notifyListeners(); refreshUnread(); };
    loadConfig(); refreshUnread();
  }
  final SharedPreferences prefs; final Api api;
  late String locale, city; late bool onboarded;
  Map<String, dynamic>? config; int unread = 0;

  Map<String, dynamic>? get user => api.session?.user;
  bool get loggedIn => user != null;
  bool get simulatedPayments => config?['simulatedPayments'] == true;
  List<String> get cities => {if (config?['defaultCity'] != null) config!['defaultCity'] as String, ...((config?['cities'] as List?) ?? const []).cast<String>()}.toList();

  String t(String key, [Map<String, Object>? p]) => tr(locale, key, p);
  String err(Object e) => errText(locale, e is ApiException ? e.code : 'generic');

  Future<void> loadConfig() async {
    try { config = Map<String, dynamic>.from(await api.get('/api/config')); if (city.isEmpty) city = config!['defaultCity']; notifyListeners(); } catch (_) {}
  }
  Future<void> refreshUnread() async {
    if (!loggedIn) { unread = 0; notifyListeners(); return; }
    try { unread = (await api.get('/api/notifications'))['unread'] ?? 0; notifyListeners(); } catch (_) {}
  }
  Future<void> setLocale(String l) async { locale = l; await prefs.setString('sulu.lang', l); notifyListeners(); }
  Future<void> setCity(String c) async { city = c; await prefs.setString('sulu.city', c); notifyListeners(); }
  Future<void> finishOnboarding() async { onboarded = true; await prefs.setBool('sulu.onboarded', true); notifyListeners(); }

  Future<Map<String, dynamic>> _adopt(dynamic j) async {
    final s = Session.fromJson(Map<String, dynamic>.from(j));
    await api.setSession(s);
    final l = s.user['language']; if (supportedLocales.contains(l)) await setLocale(l);
    return s.user;
  }
  Future<Map<String, dynamic>> login(String email, String password) async =>
      _adopt(await api.request('POST', '/api/auth/login', body: {'email': email, 'password': password}, auth: false));
  Future<Map<String, dynamic>> register(Map<String, Object?> b) async =>
      _adopt(await api.request('POST', '/api/auth/register', body: {...b, 'language': locale, if (city.isNotEmpty) 'city': city}, auth: false));
  Future<void> logout() async {
    final s = api.session;
    try { if (s != null) await api.post('/api/auth/logout', {'refreshToken': s.refreshToken}); } catch (_) {}
    await api.setSession(null);
  }
  Future<void> refreshUser() async {
    final u = await api.get('/api/me'); final s = api.session;
    if (s != null) await api.setSession(Session(s.accessToken, s.refreshToken, Map<String, dynamic>.from(u)));
  }
}

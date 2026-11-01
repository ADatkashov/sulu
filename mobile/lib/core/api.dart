import 'dart:convert';
import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

/// Build-time base URL: `--dart-define=API_URL=https://api.example.com`. Web builds served behind the same origin use ''.
const _apiUrl = String.fromEnvironment('API_URL', defaultValue: '__auto__');
String get apiBase => _apiUrl != '__auto__' ? _apiUrl : (kIsWeb ? '' : 'http://10.0.2.2:8080');

class ApiException implements Exception {
  final int status; final String code; final String message; final dynamic details;
  ApiException(this.status, this.code, this.message, [this.details]);
  @override String toString() => 'ApiException($status, $code)';
}

class Session {
  final String accessToken, refreshToken; final Map<String, dynamic> user;
  Session(this.accessToken, this.refreshToken, this.user);
  Map<String, dynamic> toJson() => {'accessToken': accessToken, 'refreshToken': refreshToken, 'user': user};
  factory Session.fromJson(Map<String, dynamic> j) => Session(j['accessToken'], j['refreshToken'], Map<String, dynamic>.from(j['user']));
}

class Api {
  Api(this._prefs, {http.Client? client}) : _client = client ?? http.Client() { _load(); }
  final SharedPreferences _prefs; final http.Client _client;
  Session? session; void Function(Session?)? onSession;
  Future<bool>? _refreshing;

  void _load() { final s = _prefs.getString('sulu.session'); if (s != null) { try { session = Session.fromJson(jsonDecode(s)); } catch (_) {} } }
  Future<void> setSession(Session? s) async {
    session = s;
    if (s == null) { await _prefs.remove('sulu.session'); } else { await _prefs.setString('sulu.session', jsonEncode(s.toJson())); }
    onSession?.call(s);
  }

  Uri _uri(String path) => Uri.parse(apiBase + path);

  Future<bool> _refresh() {
    return _refreshing ??= () async {
      final s = session; if (s == null) return false;
      try {
        final r = await _client.post(_uri('/api/auth/refresh'), headers: {'Content-Type': 'application/json'}, body: jsonEncode({'refreshToken': s.refreshToken}));
        if (r.statusCode != 200) { await setSession(null); return false; }
        await setSession(Session.fromJson(jsonDecode(utf8.decode(r.bodyBytes))));
        return true;
      } catch (_) { return false; } finally { Future.microtask(() => _refreshing = null); }
    }();
  }

  Future<dynamic> request(String method, String path, {Object? body, Map<String, String>? headers, bool auth = true}) async {
    Future<http.Response> send() {
      final h = <String, String>{...?headers};
      if (auth && session != null) h['Authorization'] = 'Bearer ${session!.accessToken}';
      if (body != null) h['Content-Type'] = 'application/json';
      final enc = body == null ? null : jsonEncode(body);
      final u = _uri(path);
      switch (method) {
        case 'POST': return _client.post(u, headers: h, body: enc ?? '{}');
        case 'PUT': return _client.put(u, headers: h, body: enc ?? '{}');
        case 'PATCH': return _client.patch(u, headers: h, body: enc);
        case 'DELETE': return _client.delete(u, headers: h);
        default: return _client.get(u, headers: h);
      }
    }
    http.Response r;
    try { r = await send().timeout(const Duration(seconds: 20)); } catch (_) { throw ApiException(0, 'NETWORK', 'Network error'); }
    if (r.statusCode == 401 && auth && session != null && !path.startsWith('/api/auth/')) { if (await _refresh()) r = await send(); }
    final text = utf8.decode(r.bodyBytes);
    if (r.statusCode >= 400) {
      Map<String, dynamic> j = {}; try { j = jsonDecode(text); } catch (_) {}
      throw ApiException(r.statusCode, (j['code'] ?? 'HTTP_${r.statusCode}').toString(), (j['message'] ?? '').toString(), j['details']);
    }
    return text.isEmpty ? null : jsonDecode(text);
  }

  Future<dynamic> get(String p) => request('GET', p);
  Future<dynamic> post(String p, [Object? body, Map<String, String>? headers]) => request('POST', p, body: body ?? {}, headers: headers);
  Future<dynamic> put(String p) => request('PUT', p);
  Future<dynamic> delete(String p) => request('DELETE', p);

  String? media(String? key) => key == null ? null : '$apiBase/media/$key';
}

String qs(Map<String, Object?> m) {
  final e = m.entries.where((e) => e.value != null && e.value != '' && e.value != false).map((e) => '${e.key}=${Uri.encodeQueryComponent(e.value.toString())}');
  return e.isEmpty ? '' : '?${e.join('&')}';
}

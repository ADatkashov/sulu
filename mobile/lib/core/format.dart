import 'package:intl/intl.dart';
import 'package:timezone/data/latest.dart' as tzdata;
import 'package:timezone/timezone.dart' as tz;

bool _tzReady = false;
void initTz() { if (!_tzReady) { tzdata.initializeTimeZones(); _tzReady = true; } }

String _loc(String l) => l == 'kk' ? 'kk_KZ' : l == 'ru' ? 'ru_RU' : 'en_GB';

/// Money = integer minor units + currency (no floating point arithmetic on money).
String money(num minor, String currency, String locale) {
  final v = minor / 100;
  final f = NumberFormat.currency(locale: _loc(locale), name: currency.trim(), decimalDigits: v % 1 == 0 ? 0 : 2, symbol: '${currency.trim()} ');
  return f.format(v).replaceAll(' ', ' ').trim();
}

tz.TZDateTime inZone(String iso, String? zone) {
  initTz();
  return tz.TZDateTime.from(DateTime.parse(iso), tz.getLocation(zone ?? 'Asia/Almaty'));
}

String fmtTime(String iso, String? zone) => DateFormat('HH:mm').format(inZone(iso, zone));
String fmtDate(String iso, String locale, String? zone, {bool weekday = true}) =>
    DateFormat(weekday ? 'EEE, d MMMM' : 'd MMMM', _loc(locale)).format(inZone(iso, zone));

/// yyyy-MM-dd of "today + n days" in the business time zone.
String ymdInZone(int daysAhead, String zone, [DateTime? now]) {
  initTz();
  final d = tz.TZDateTime.from((now ?? DateTime.now()).toUtc(), tz.getLocation(zone)).add(Duration(days: daysAhead));
  return DateFormat('yyyy-MM-dd').format(d);
}

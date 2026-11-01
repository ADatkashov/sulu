import 'strings.g.dart';

const supportedLocales = ['kk', 'ru', 'en'];
const localeLabels = {'kk': 'Қазақша', 'ru': 'Русский', 'en': 'English'};
const _chain = {'kk': ['kk', 'ru', 'en'], 'ru': ['ru', 'en'], 'en': ['en']};
const _dicts = {'kk': stringsKk, 'ru': stringsRu, 'en': stringsEn};

/// Same lookup rule as the web app: kk → ru → en.
String tr(String locale, String key, [Map<String, Object>? params]) {
  var s = key;
  for (final l in _chain[locale] ?? const ['en']) { final v = _dicts[l]![key]; if (v != null) { s = v; break; } }
  params?.forEach((k, v) => s = s.replaceAll('{$k}', '$v'));
  return s;
}

String errText(String locale, String code) {
  final k = 'err.$code';
  return stringsEn.containsKey(k) ? tr(locale, k) : tr(locale, 'err.generic');
}

String catName(Map c, String locale) => (locale == 'kk' ? c['nameKk'] : locale == 'ru' ? c['nameRu'] : c['nameEn'])?.toString() ?? c['nameRu']?.toString() ?? '';

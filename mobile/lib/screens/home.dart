import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../core/api.dart';
import '../core/i18n.dart';
import '../core/theme.dart';
import '../widgets/cards.dart';
import '../widgets/common.dart';

class Home extends StatelessWidget {
  const Home({super.key});
  @override Widget build(BuildContext context) {
    final a = app(context);
    final cities = a.cities;
    return SafeArea(child: RefreshIndicator(onRefresh: () async { await a.loadConfig(); }, child: ListView(padding: const EdgeInsets.only(bottom: 24), children: [
      Container(padding: const EdgeInsets.fromLTRB(16, 12, 16, 16), decoration: const BoxDecoration(gradient: C.gradSoft, borderRadius: BorderRadius.vertical(bottom: Radius.circular(28))), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [const SuluLogo(), Semantics(button: true, label: a.t('notif.title'), child: IconButton.filled(style: IconButton.styleFrom(backgroundColor: Colors.white, foregroundColor: C.text), onPressed: () => context.push('/notifications'),
          icon: Badge(isLabelVisible: a.unread > 0, label: Text('${a.unread}'), child: const Icon(Icons.notifications_none))))]),
        const SizedBox(height: 8),
        Row(children: [const Icon(Icons.place_outlined, size: 18), const SizedBox(width: 6), if (cities.isNotEmpty) DropdownButton<String>(value: cities.contains(a.city) ? a.city : cities.first, underline: const SizedBox(), borderRadius: BorderRadius.circular(16), items: [for (final c in cities) DropdownMenuItem(value: c, child: Text(c))], onChanged: (v) => a.setCity(v!))]),
        if (a.loggedIn) Text('${a.t('home.greeting')}, ${a.user!['fullName'].toString().split(' ').first}', style: const TextStyle(color: C.muted)),
        const SizedBox(height: 10),
        GestureDetector(onTap: () => context.go('/explore'), child: Container(height: 48, padding: const EdgeInsets.symmetric(horizontal: 16), decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(999), border: Border.all(color: C.border)),
          child: Row(children: [const Icon(Icons.search, color: C.muted), const SizedBox(width: 10), Expanded(child: Text(a.t('home.search'), style: const TextStyle(color: C.muted), overflow: TextOverflow.ellipsis))]))),
      ])),
      Padding(padding: const EdgeInsets.fromLTRB(16, 20, 16, 0), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Container(width: double.infinity, padding: const EdgeInsets.all(22), decoration: BoxDecoration(gradient: C.grad, borderRadius: BorderRadius.circular(28), boxShadow: [BoxShadow(color: C.primary.withValues(alpha: .3), blurRadius: 28, offset: const Offset(0, 12))]),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(a.t('home.promoTitle'), style: const TextStyle(fontFamily: serif, color: Colors.white, fontSize: 22, fontWeight: FontWeight.w700)), const SizedBox(height: 6), Text(a.t('home.promoText'), style: const TextStyle(color: Colors.white))])),
        const SizedBox(height: 24), SectionHead(a.t('home.categories')),
        Load<List>(fn: () async => await a.api.get('/api/categories'), builder: (c, cats, _) => SizedBox(height: 112, child: ListView(scrollDirection: Axis.horizontal, children: [for (final cat in cats) Padding(padding: const EdgeInsets.only(right: 12),
          child: GestureDetector(onTap: () => c.go('/explore?category=${cat['slug']}'), child: SizedBox(width: 88, child: Column(children: [
            Container(width: 60, height: 60, decoration: BoxDecoration(gradient: C.gradSoft, borderRadius: BorderRadius.circular(20)), child: Icon(iconFor(cat['slug']), color: C.rose, size: 28)),
            const SizedBox(height: 6), Text(catName(cat, a.locale), textAlign: TextAlign.center, style: const TextStyle(fontSize: 11), maxLines: 2, overflow: TextOverflow.ellipsis)]))))]))),
        const SizedBox(height: 20), SectionHead(a.t('home.popular'), onMore: () => context.go('/explore?tab=salons')),
        Load<Map>(keys: [a.city], fn: () async => await a.api.get('/api/businesses${qs({'city': a.city, 'size': 8, 'sort': 'rating'})}'), builder: (c, d, _) => (d['items'] as List).isEmpty ? EmptyView(a.t('home.noBusinesses')) :
          SizedBox(height: 308, child: ListView(scrollDirection: Axis.horizontal, children: [for (final b in d['items']) Padding(padding: const EdgeInsets.only(right: 12), child: SalonCard(b))]))),
        const SizedBox(height: 20), SectionHead(a.t('home.top'), onMore: () => context.go('/explore?tab=masters')),
        Load<Map>(keys: [a.city], fn: () async => await a.api.get('/api/professionals${qs({'city': a.city, 'size': 8})}'), builder: (c, d, _) => SizedBox(height: 214, child: ListView(scrollDirection: Axis.horizontal, children: [for (final p in d['items']) Padding(padding: const EdgeInsets.only(right: 12), child: ProCard(p))]))),
        const SizedBox(height: 20), SectionHead(a.t('home.recommended'), onMore: () => context.go('/explore')),
        Load<Map>(keys: [a.city], fn: () async => await a.api.get('/api/services${qs({'city': a.city, 'size': 5, 'sort': 'rating'})}'), builder: (c, d, _) => Column(children: [for (final s in d['items']) Padding(padding: const EdgeInsets.only(bottom: 10), child: ServiceCard(s))])),
      ])),
    ])));
  }
}

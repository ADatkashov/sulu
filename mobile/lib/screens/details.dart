import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../core/format.dart';
import '../core/i18n.dart';
import '../core/theme.dart';
import '../widgets/cards.dart';
import '../widgets/common.dart';

Widget detailScaffold(BuildContext c, String title, Widget body, {Widget? bottom, Widget? action}) => Scaffold(
  appBar: AppBar(title: Text(title, overflow: TextOverflow.ellipsis), leading: BackButton(onPressed: () => c.canPop() ? c.pop() : c.go('/')), actions: [if (action != null) Padding(padding: const EdgeInsets.only(right: 12), child: action)]),
  body: body, bottomNavigationBar: bottom == null ? null : SafeArea(child: Padding(padding: const EdgeInsets.fromLTRB(16, 8, 16, 12), child: bottom)));

class _Next extends StatelessWidget {
  const _Next(this.serviceId, {this.proId});
  final int serviceId; final int? proId;
  @override Widget build(BuildContext c) {
    final a = app(c);
    return Load<Map>(skeleton: const SizedBox(height: 20, width: 140, child: Skel(h: 20)), fn: () async => Map.from(await a.api.get('/api/availability/next?serviceId=$serviceId${proId != null ? '&professionalId=$proId' : ''}')),
      builder: (_, n, __) => n['start'] == null ? Text(a.t('svc.noNext'), style: const TextStyle(color: C.muted, fontSize: 13)) : Row(mainAxisSize: MainAxisSize.min, children: [const Icon(Icons.schedule, size: 14), Text(' ${fmtDate(n['start'], a.locale, null, weekday: false)}, ${fmtTime(n['start'], null)}', style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13))]));
  }
}

class _Reviews extends StatelessWidget {
  const _Reviews(this.query);
  final String query;
  @override Widget build(BuildContext c) {
    final a = app(c);
    return Load<Map>(skeleton: const Skel(h: 100), fn: () async => Map.from(await a.api.get('/api/reviews?$query&size=10')), builder: (_, d, __) {
      if ((d['count'] ?? 0) == 0) return Text(a.t('common.noReviews'), style: const TextStyle(color: C.muted));
      final dist = Map.from(d['distribution']); final mx = dist.values.fold<int>(1, (m, v) => v > m ? v as int : m);
      return Column(children: [
        CardBox(child: Row(children: [Column(children: [Text((d['average'] as num).toStringAsFixed(1), style: const TextStyle(fontSize: 40, fontWeight: FontWeight.w800, fontFamily: serif)), Text('${d['count']} ${a.t('common.reviews')}', style: const TextStyle(color: C.muted, fontSize: 12))]), const SizedBox(width: 16),
          Expanded(child: Column(children: [for (final s in [5, 4, 3, 2, 1]) Padding(padding: const EdgeInsets.symmetric(vertical: 2), child: Row(children: [Text('$s', style: const TextStyle(fontSize: 11)), const SizedBox(width: 8), Expanded(child: ClipRRect(borderRadius: BorderRadius.circular(4), child: LinearProgressIndicator(value: (dist['$s'] ?? 0) / mx, minHeight: 8, color: const Color(0xFFF5A623), backgroundColor: C.border))), const SizedBox(width: 8), Text('${dist['$s'] ?? 0}', style: const TextStyle(fontSize: 11, color: C.muted))]))]))])),
        for (final r in d['items']) Padding(padding: const EdgeInsets.only(top: 10), child: ReviewCard(r, onReport: () async {
          if (!a.loggedIn) { goLogin(c, GoRouterState.of(c).uri.toString()); return; }
          try { await a.api.post('/api/reviews/${r['id']}/report'); if (c.mounted) toast(c, a.t('review.reported')); } catch (e) { if (c.mounted) toast(c, a.err(e)); } }))]);
    });
  }
}

class SalonPage extends StatelessWidget {
  const SalonPage(this.id, {super.key});
  final int id;
  @override Widget build(BuildContext context) {
    final a = app(context);
    return Load<Map>(fn: () async => Map.from(await a.api.get('/api/businesses/$id')), builder: (c, d, _) => detailScaffold(c, d['name'], ListView(padding: const EdgeInsets.all(16), children: [
      Photo(url: a.api.media(d['imageKey']), icon: Icons.spa_outlined, h: 190, radius: 22), const SizedBox(height: 16),
      Row(children: [Flexible(child: Text(d['name'], style: const TextStyle(fontFamily: serif, fontSize: 26, fontWeight: FontWeight.w700))), const SizedBox(width: 8), DemoTag(d['isDemo'] == true)]),
      const SizedBox(height: 6), Stars(num.parse('${d['ratingAvg']}'), count: d['ratingCount']), const SizedBox(height: 6),
      Row(children: [const Icon(Icons.place_outlined, size: 15, color: C.muted), Expanded(child: Text(' ${d['address']}, ${d['city']}', style: const TextStyle(color: C.muted)))]),
      if ((d['description'] ?? '') != '') ...[const SizedBox(height: 20), Text(a.t('biz.about'), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)), const SizedBox(height: 6), Text(d['description'], style: const TextStyle(color: C.muted))],
      const SizedBox(height: 20), SectionHead(a.t('biz.services')),
      for (final s in d['services']) Padding(padding: const EdgeInsets.only(bottom: 10), child: ServiceCard({...s, 'businessName': d['name'], 'businessRating': d['ratingAvg'], 'businessRatingCount': d['ratingCount']})),
      const SizedBox(height: 10), SectionHead(a.t('biz.team')),
      SizedBox(height: 214, child: ListView(scrollDirection: Axis.horizontal, children: [for (final p in d['professionals']) Padding(padding: const EdgeInsets.only(right: 12), child: ProCard(p))])),
      const SizedBox(height: 20), SectionHead(a.t('biz.hours')),
      CardBox(child: Column(children: [for (var w = 1; w <= 7; w++) Padding(padding: const EdgeInsets.symmetric(vertical: 3), child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
        Text(a.t('wd.$w'), style: const TextStyle(fontSize: 13)), Builder(builder: (_) { final h = (d['hours'] as List).cast<Map>().where((x) => x['weekday'] == w).toList(); return Text(h.isEmpty ? a.t('biz.closed') : '${h.first['openTime']}–${h.first['closeTime']}', style: TextStyle(fontWeight: h.isEmpty ? FontWeight.w400 : FontWeight.w700, fontSize: 13, color: h.isEmpty ? C.muted : C.text)); })]))])),
      const SizedBox(height: 20), SectionHead(a.t('biz.reviews')), _Reviews('businessId=$id'),
    ]), action: HeartButton(kind: 'business', id: id, initial: d['favorite'] == true)));
  }
}

class MasterPage extends StatefulWidget { const MasterPage(this.id, {super.key}); final int id; @override State<MasterPage> createState() => _MasterPageState(); }
class _MasterPageState extends State<MasterPage> {
  int? sel;
  @override Widget build(BuildContext context) {
    final a = app(context);
    return Load<Map>(fn: () async => Map.from(await a.api.get('/api/professionals/${widget.id}')), builder: (c, d, _) {
      final services = (d['services'] as List).cast<Map>(); final chosen = sel ?? (services.isEmpty ? null : services.first['id'] as int);
      return detailScaffold(c, d['fullName'], ListView(padding: const EdgeInsets.all(16), children: [
        Container(padding: const EdgeInsets.all(16), decoration: BoxDecoration(gradient: C.gradSoft, borderRadius: BorderRadius.circular(22)), child: Row(children: [Avatar(d['fullName'], size: 88, url: a.api.media(d['avatarKey'])), const SizedBox(width: 16),
          Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(d['fullName'], style: const TextStyle(fontFamily: serif, fontSize: 24, fontWeight: FontWeight.w700)), Text(d['title'] ?? '', style: const TextStyle(color: C.muted)), const SizedBox(height: 4), Stars(num.parse('${d['ratingAvg']}'), count: d['ratingCount']),
            GestureDetector(onTap: () => c.push('/salon/${d['businessId']}'), child: Text(d['businessName'], style: const TextStyle(color: C.primary, fontSize: 13)))]))])),
        const SizedBox(height: 12), Wrap(spacing: 6, runSpacing: 6, children: [for (final s in (d['specialties'] as String).split(',').where((x) => x.trim().isNotEmpty)) Tag(s.trim()), Tag('${a.t('pro.experience')}: ${d['experienceYears']} ${a.t('common.years')}', tone: 'success')]),
        if ((d['bio'] ?? '') != '') ...[const SizedBox(height: 20), Text(a.t('pro.bio'), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)), const SizedBox(height: 6), Text(d['bio'], style: const TextStyle(color: C.muted))],
        const SizedBox(height: 20), Text(a.t('pro.portfolio'), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)), const SizedBox(height: 8),
        (d['portfolio'] as List).isEmpty ? Text(a.t('pro.noPortfolio'), style: const TextStyle(color: C.muted, fontSize: 13)) : GridView.count(crossAxisCount: 3, shrinkWrap: true, physics: const NeverScrollableScrollPhysics(), mainAxisSpacing: 8, crossAxisSpacing: 8, children: [for (final x in d['portfolio']) Photo(url: a.api.media(x['imageKey']), radius: 14)]),
        const SizedBox(height: 20), SectionHead(a.t('pro.services')),
        for (final s in services) Padding(padding: const EdgeInsets.only(bottom: 8), child: InkWell(borderRadius: BorderRadius.circular(16), onTap: () => setState(() => sel = s['id']), child: Container(padding: const EdgeInsets.all(12), decoration: BoxDecoration(color: chosen == s['id'] ? C.bgSoft : Colors.white, borderRadius: BorderRadius.circular(16), border: Border.all(color: chosen == s['id'] ? C.primary : C.border, width: 1.5)),
          child: Row(children: [Icon(chosen == s['id'] ? Icons.radio_button_checked : Icons.radio_button_off, color: chosen == s['id'] ? C.primary : C.border), const SizedBox(width: 10), Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(s['name'], style: const TextStyle(fontWeight: FontWeight.w700)), Text('${s['durationMin']} ${a.t('common.min')}', style: const TextStyle(color: C.muted, fontSize: 13))])), PriceText(s['priceMinor'], s['currency'], from: s['priceFrom'] == true)])))),
        if (chosen != null) Wrap(alignment: WrapAlignment.spaceBetween, crossAxisAlignment: WrapCrossAlignment.center, runSpacing: 6, children: [Text(a.t('pro.next'), style: const TextStyle(color: C.muted, fontSize: 13)), _Next(chosen, proId: widget.id)]),
        const SizedBox(height: 20), SectionHead(a.t('biz.reviews')), _Reviews('professionalId=${widget.id}'),
      ]), bottom: SBtn(a.t('pro.book'), onPressed: chosen == null ? null : () => c.push('/book/$chosen?pro=${widget.id}')), action: HeartButton(kind: 'professional', id: widget.id, initial: d['favorite'] == true));
    });
  }
}

class ServicePage extends StatelessWidget {
  const ServicePage(this.id, {super.key});
  final int id;
  @override Widget build(BuildContext context) {
    final a = app(context);
    return Load<Map>(fn: () async => Map.from(await a.api.get('/api/services/$id')), builder: (c, d, _) => detailScaffold(c, d['name'], ListView(padding: const EdgeInsets.all(16), children: [
      Photo(url: a.api.media(d['imageKey']), icon: iconFor(d['categorySlug']), h: 180, radius: 22), const SizedBox(height: 16),
      Align(alignment: Alignment.centerLeft, child: Tag(catName(d, a.locale))), const SizedBox(height: 6),
      Text(d['name'], style: const TextStyle(fontFamily: serif, fontSize: 26, fontWeight: FontWeight.w700)), const SizedBox(height: 6),
      Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [PriceText(d['priceMinor'], d['currency'], from: d['priceFrom'] == true, size: 18), Row(children: [const Icon(Icons.schedule, size: 15, color: C.muted), Text(' ${d['durationMin']} ${a.t('common.min')}', style: const TextStyle(color: C.muted))])]),
      const SizedBox(height: 6), GestureDetector(onTap: () => c.push('/salon/${d['businessId']}'), child: Row(children: [const Icon(Icons.place_outlined, size: 15, color: C.primary), Expanded(child: Text(' ${d['businessName']} · ${d['address']}', style: const TextStyle(color: C.primary, fontSize: 13))), DemoTag(d['isDemo'] == true)])),
      const SizedBox(height: 6), Stars(num.parse('${d['businessRating']}'), count: d['businessRatingCount']),
      if (d['atHome'] == true) Padding(padding: const EdgeInsets.only(top: 8), child: Align(alignment: Alignment.centerLeft, child: Tag(a.t('svc.atHome'), tone: 'success'))),
      if ((d['description'] ?? '') != '') ...[const SizedBox(height: 20), Text(a.t('svc.about'), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)), const SizedBox(height: 6), Text(d['description'], style: const TextStyle(color: C.muted))],
      const SizedBox(height: 16), CardBox(child: Wrap(alignment: WrapAlignment.spaceBetween, crossAxisAlignment: WrapCrossAlignment.center, runSpacing: 6, children: [Text(a.t('svc.next'), style: const TextStyle(color: C.muted)), _Next(id)])),
      const SizedBox(height: 20), SectionHead(a.t('svc.professionals')), SizedBox(height: 214, child: ListView(scrollDirection: Axis.horizontal, children: [for (final p in d['professionals']) Padding(padding: const EdgeInsets.only(right: 12), child: ProCard(p))])),
      const SizedBox(height: 16), Text(a.t('svc.policy'), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)), const SizedBox(height: 6), Text(a.t('svc.policyText', {'h': d['cancellationHours']}), style: const TextStyle(color: C.muted)),
      const SizedBox(height: 20), SectionHead(a.t('biz.reviews')), _Reviews('businessId=${d['businessId']}'),
    ]), bottom: SBtn(a.t('svc.book'), onPressed: () => c.push('/book/$id')), action: HeartButton(kind: 'service', id: id, initial: d['favorite'] == true)));
  }
}

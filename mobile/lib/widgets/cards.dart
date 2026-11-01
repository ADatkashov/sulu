import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../core/format.dart';
import '../core/theme.dart';
import 'common.dart';

class HeartButton extends StatefulWidget {
  const HeartButton({super.key, required this.kind, required this.id, required this.initial});
  final String kind; final int id; final bool initial;
  @override State<HeartButton> createState() => _HeartButtonState();
}
class _HeartButtonState extends State<HeartButton> {
  late bool on = widget.initial;
  Future<void> _toggle() async {
    final a = appRead(context);
    if (!a.loggedIn) { toast(context, a.t('fav.loginRequired')); goLogin(context, GoRouterState.of(context).uri.toString()); return; }
    try {
      on ? await a.api.delete('/api/favorites/${widget.kind}/${widget.id}') : await a.api.put('/api/favorites/${widget.kind}/${widget.id}');
      if (mounted) setState(() => on = !on);
    } catch (e) { if (mounted) toast(context, a.err(e)); }
  }
  @override Widget build(BuildContext c) => Semantics(button: true, label: 'favorite', toggled: on, child: InkResponse(onTap: _toggle, radius: 24,
    child: Container(width: 40, height: 40, decoration: BoxDecoration(color: Colors.white.withValues(alpha: .92), shape: BoxShape.circle, boxShadow: const [BoxShadow(color: Color(0x14000000), blurRadius: 4)]),
      child: Icon(on ? Icons.favorite : Icons.favorite_border, color: on ? C.primary : C.muted, size: 20))));
}

class DemoTag extends StatelessWidget {
  const DemoTag(this.show, {super.key});
  final bool show;
  @override Widget build(BuildContext c) => show ? Tag(appRead(c).t('common.demo')) : const SizedBox.shrink();
}

class ServiceCard extends StatelessWidget {
  const ServiceCard(this.s, {super.key});
  final Map s;
  @override Widget build(BuildContext c) {
    final a = app(c);
    return CardBox(onTap: () => c.push('/service/${s['id']}'), padding: const EdgeInsets.all(12), child: Row(children: [
      Photo(url: a.api.media(s['imageKey']), icon: iconFor(s['categorySlug']), w: 72, h: 72),
      const SizedBox(width: 12),
      Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(s['name'], style: const TextStyle(fontWeight: FontWeight.w700), maxLines: 2, overflow: TextOverflow.ellipsis),
        const SizedBox(height: 2),
        Row(children: [Flexible(child: Text(s['businessName'] ?? '', style: const TextStyle(color: C.muted, fontSize: 13), overflow: TextOverflow.ellipsis)), const SizedBox(width: 6), DemoTag(s['isDemo'] == true)]),
        const SizedBox(height: 4),
        Wrap(crossAxisAlignment: WrapCrossAlignment.center, spacing: 10, children: [Row(mainAxisSize: MainAxisSize.min, children: [const Icon(Icons.schedule, size: 13, color: C.muted), Text(' ${s['durationMin']} ${a.t('common.min')}', style: const TextStyle(color: C.muted, fontSize: 12))]),
          Stars(num.tryParse('${s['businessRating']}') ?? 0, count: s['businessRatingCount'])]),
      ])),
      const SizedBox(width: 8), ConstrainedBox(constraints: const BoxConstraints(maxWidth: 112), child: FittedBox(fit: BoxFit.scaleDown, alignment: Alignment.centerRight, child: PriceText(s['priceMinor'], s['currency'], from: s['priceFrom'] == true))),
    ]));
  }
}

class SalonCard extends StatelessWidget {
  const SalonCard(this.b, {super.key, this.row = false});
  final Map b; final bool row;
  @override Widget build(BuildContext c) {
    final a = app(c);
    final info = Column(crossAxisAlignment: CrossAxisAlignment.start, mainAxisSize: MainAxisSize.min, children: [
      Text(b['name'], style: const TextStyle(fontWeight: FontWeight.w700), overflow: TextOverflow.ellipsis),
      const SizedBox(height: 3), Stars(num.tryParse('${b['ratingAvg']}') ?? 0, count: b['ratingCount']),
      const SizedBox(height: 3), Row(children: [const Icon(Icons.place_outlined, size: 13, color: C.muted), Expanded(child: Text(' ${b['address']}', style: const TextStyle(color: C.muted, fontSize: 12), overflow: TextOverflow.ellipsis))]),
      if (b['minPrice'] != null) Padding(padding: const EdgeInsets.only(top: 3), child: Wrap(crossAxisAlignment: WrapCrossAlignment.center, children: [Text('${a.t('biz.from')} ', style: const TextStyle(fontSize: 13)), PriceText(b['minPrice'], b['currency'])])),
      if (b['isDemo'] == true) const Padding(padding: EdgeInsets.only(top: 4), child: DemoTag(true)),
    ]);
    final photo = Stack(children: [Photo(url: a.api.media(b['imageKey']), icon: Icons.spa_outlined, w: row ? 96 : 240, h: row ? 96 : 130), Positioned(top: 6, right: 6, child: HeartButton(kind: 'business', id: b['id'], initial: b['favorite'] == true))]);
    return SizedBox(width: row ? null : 240, child: CardBox(onTap: () => c.push('/salon/${b['id']}'), padding: const EdgeInsets.all(10),
      child: row ? Row(children: [photo, const SizedBox(width: 12), Expanded(child: info)]) : Column(crossAxisAlignment: CrossAxisAlignment.start, children: [photo, const SizedBox(height: 8), info])));
  }
}

class ProCard extends StatelessWidget {
  const ProCard(this.p, {super.key});
  final Map p;
  @override Widget build(BuildContext c) => SizedBox(width: 150, child: CardBox(onTap: () => c.push('/master/${p['id']}'), child: Column(mainAxisSize: MainAxisSize.min, children: [
    Align(alignment: Alignment.topRight, child: HeartButton(kind: 'professional', id: p['id'], initial: p['favorite'] == true)),
    Avatar(p['fullName'], size: 72, url: app(c).api.media(p['avatarKey'])), const SizedBox(height: 8),
    Text(p['fullName'], style: const TextStyle(fontWeight: FontWeight.w700), maxLines: 1, overflow: TextOverflow.ellipsis),
    Text(p['title'] ?? '', style: const TextStyle(color: C.muted, fontSize: 12), maxLines: 1, overflow: TextOverflow.ellipsis),
    const SizedBox(height: 4), Stars(num.tryParse('${p['ratingAvg']}') ?? 0, count: p['ratingCount']),
  ])));
}

String statusTone(String s) => s == 'CONFIRMED' ? 'success' : s == 'PENDING' ? 'warning' : s == 'COMPLETED' ? '' : 'error';

class BookingCard extends StatelessWidget {
  const BookingCard(this.b, {super.key});
  final Map b;
  @override Widget build(BuildContext c) {
    final a = app(c);
    return CardBox(onTap: () => c.push('/bookings/${b['id']}'), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [Expanded(child: Text(b['serviceName'], style: const TextStyle(fontWeight: FontWeight.w700))), Tag(a.t('status.${b['status']}'), tone: statusTone(b['status']))]),
      const SizedBox(height: 4), Text('${b['businessName']} · ${b['professionalName']}', style: const TextStyle(color: C.muted, fontSize: 13)),
      const SizedBox(height: 8), Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
        Row(children: [const Icon(Icons.event, size: 14), Text(' ${fmtDate(b['startAt'], a.locale, b['timezone'])}, ${fmtTime(b['startAt'], b['timezone'])}', style: const TextStyle(fontSize: 13))]),
        PriceText(b['totalMinor'], b['currency']),
      ]),
    ]));
  }
}

class ReviewCard extends StatelessWidget {
  const ReviewCard(this.r, {super.key, this.onReport});
  final Map r; final VoidCallback? onReport;
  @override Widget build(BuildContext c) {
    final a = app(c);
    return CardBox(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Row(children: [Avatar(r['author'], size: 36), const SizedBox(width: 10), Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(r['author'], style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13)), Text('${r['serviceName']}', style: const TextStyle(color: C.muted, fontSize: 12))])), Stars(r['rating'])]),
      if ((r['body'] ?? '').toString().isNotEmpty) Padding(padding: const EdgeInsets.only(top: 8), child: Text(r['body'])),
      if (r['reply'] != null) Padding(padding: const EdgeInsets.only(top: 8), child: Alert('${a.t('review.reply')}: ${r['reply']}')),
      if (onReport != null) TextButton(onPressed: onReport, child: Text(a.t('review.report'))),
    ]));
  }
}

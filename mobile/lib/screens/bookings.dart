import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../core/format.dart';
import '../core/theme.dart';
import '../widgets/cards.dart';
import '../widgets/common.dart';
import '../state/app_state.dart';

class BookingsPage extends StatefulWidget { const BookingsPage({super.key}); @override State<BookingsPage> createState() => _BookingsPageState(); }
class _BookingsPageState extends State<BookingsPage> {
  String scope = 'upcoming';
  @override Widget build(BuildContext context) {
    final a = app(context);
    if (!a.loggedIn) return SafeArea(child: Center(child: EmptyView(a.t('bookings.title'), text: a.t('profile.guest'), icon: Icons.calendar_month_outlined, action: SBtn(a.t('auth.login'), onPressed: () => goLogin(context, '/bookings')))));
    return SafeArea(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Padding(padding: const EdgeInsets.fromLTRB(16, 12, 16, 8), child: Text(a.t('bookings.title'), style: const TextStyle(fontFamily: serif, fontSize: 22, fontWeight: FontWeight.w700))),
      Padding(padding: const EdgeInsets.symmetric(horizontal: 16), child: SegmentedButton<String>(showSelectedIcon: false, segments: [for (final s in ['upcoming', 'past', 'cancelled']) ButtonSegment(value: s, label: Text(a.t('bookings.$s'), style: const TextStyle(fontSize: 12)))], selected: {scope}, onSelectionChanged: (s) => setState(() => scope = s.first))),
      const SizedBox(height: 12),
      Expanded(child: Load<List>(keys: [scope, a.user?['id']], fn: () async => await a.api.get('/api/bookings?scope=$scope&size=50'), builder: (c, l, reload) => l.isEmpty
        ? SingleChildScrollView(child: EmptyView(a.t(scope == 'upcoming' ? 'bookings.emptyUpcoming' : scope == 'past' ? 'bookings.emptyPast' : 'bookings.emptyCancelled'), icon: Icons.calendar_month_outlined, action: SBtn(a.t('bookings.findSomething'), onPressed: () => context.go('/explore'))))
        : RefreshIndicator(onRefresh: () async => reload(), child: ListView.separated(padding: const EdgeInsets.all(16), itemCount: l.length, separatorBuilder: (_, __) => const SizedBox(height: 10), itemBuilder: (_, i) => BookingCard(l[i]))))),
    ]));
  }
}

class BookingDetail extends StatelessWidget {
  const BookingDetail(this.id, {super.key});
  final int id;
  @override Widget build(BuildContext context) {
    final a = app(context);
    return Load<Map>(fn: () async => Map.from(await a.api.get('/api/bookings/$id')), builder: (c, b, reload) {
      final active = b['status'] == 'PENDING' || b['status'] == 'CONFIRMED';
      Widget kv(String k, Widget v) => Padding(padding: const EdgeInsets.symmetric(vertical: 5), child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [SizedBox(width: 96, child: Text(k, style: const TextStyle(color: C.muted))), Expanded(child: v)]));
      return Scaffold(appBar: AppBar(title: Text(a.t('bookings.title')), leading: BackButton(onPressed: () => c.canPop() ? c.pop() : c.go('/bookings'))), body: ListView(padding: const EdgeInsets.all(16), children: [
        CardBox(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [Tag('#${b['reference']}'), Tag(a.t('status.${b['status']}'), tone: statusTone(b['status']))]), const SizedBox(height: 10),
          Text(b['serviceName'], style: const TextStyle(fontFamily: serif, fontSize: 24, fontWeight: FontWeight.w700)), const SizedBox(height: 8),
          kv(a.t('book.where'), Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(b['businessName'], style: const TextStyle(color: C.primary, fontWeight: FontWeight.w600)), Text('${b['address']}, ${b['city']}', style: const TextStyle(color: C.muted, fontSize: 13))])),
          kv(a.t('book.master'), Text(b['professionalName'], style: const TextStyle(fontWeight: FontWeight.w600))),
          kv(a.t('book.when'), Text('${fmtDate(b['startAt'], a.locale, b['timezone'])}, ${fmtTime(b['startAt'], b['timezone'])}–${fmtTime(b['endAt'], b['timezone'])}', style: const TextStyle(fontWeight: FontWeight.w600))),
          kv(a.t('book.total'), PriceText(b['totalMinor'], b['currency'])), kv(a.t('book.payment'), Text(a.t('pay.${b['paymentStatus']}'), style: const TextStyle(fontWeight: FontWeight.w600))),
          if (b['customerNote'] != null) kv(a.t('book.notes'), Text(b['customerNote'])),
          Text(a.t('svc.policyText', {'h': b['cancellationHours']}), style: const TextStyle(color: C.muted, fontSize: 12)),
          if (b['paymentStatus'] == 'PENDING') Padding(padding: const EdgeInsets.only(top: 10), child: PayPanel(bookingId: id, onDone: reload)),
        ])),
        const SizedBox(height: 12),
        if (active) ...[
          if (b['canReschedule'] == true) SBtn(a.t('bookings.reschedule'), kind: 'secondary', onPressed: () => _reschedule(c, b, reload)) else Alert(a.t('bookings.windowPassed')),
          const SizedBox(height: 8), if (b['canCancel'] == true) SBtn(a.t('bookings.cancel'), kind: 'danger', onPressed: () => _cancel(c, a, reload)),
        ],
        if (b['canReview'] == true) SBtn(a.t('bookings.review'), onPressed: () => _review(c, a, reload)),
        if (!active) Padding(padding: const EdgeInsets.only(top: 8), child: SBtn(a.t('bookings.again'), kind: 'secondary', onPressed: () => c.push('/book/${b['serviceId']}?pro=${b['professionalId']}'))),
        const SizedBox(height: 20), SectionHead(a.t('bookings.history')),
        for (final h in b['history']) Padding(padding: const EdgeInsets.symmetric(vertical: 3), child: Row(children: [const Icon(Icons.check, size: 14), const SizedBox(width: 6), Expanded(child: Text('${a.t('status.${h['toStatus']}')}${h['note'] != null ? ' — ${h['note']}' : ''}', style: const TextStyle(fontSize: 13)))])),
      ]));
    });
  }

  Future<void> _cancel(BuildContext c, AppState a, VoidCallback reload) async {
    final reason = TextEditingController();
    final ok = await showDialog<bool>(context: c, builder: (ctx) => AlertDialog(title: Text(a.t('bookings.cancelConfirm')), content: TextField(controller: reason, decoration: InputDecoration(labelText: a.t('bookings.cancelReason'))),
      actions: [TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text(a.t('common.cancel'))), TextButton(onPressed: () => Navigator.pop(ctx, true), child: Text(a.t('bookings.cancel'), style: const TextStyle(color: C.error)))]));
    if (ok != true) return;
    try { await a.api.post('/api/bookings/$id/cancel', {if (reason.text.isNotEmpty) 'reason': reason.text}); if (c.mounted) toast(c, a.t('bookings.cancelled.ok')); reload(); } catch (e) { if (c.mounted) toast(c, a.err(e)); }
  }
  Future<void> _review(BuildContext c, AppState a, VoidCallback reload) async {
    var rating = 5; final text = TextEditingController();
    final ok = await showModalBottomSheet<bool>(context: c, isScrollControlled: true, builder: (ctx) => StatefulBuilder(builder: (ctx, setM) => Padding(padding: EdgeInsets.fromLTRB(16, 20, 16, MediaQuery.of(ctx).viewInsets.bottom + 20), child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
      Text(a.t('review.title'), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)), const SizedBox(height: 12),
      Row(children: [for (var i = 1; i <= 5; i++) IconButton(tooltip: '$i', onPressed: () => setM(() => rating = i), icon: Icon(i <= rating ? Icons.star_rounded : Icons.star_outline_rounded, color: const Color(0xFFF5A623), size: 34))]),
      TextField(controller: text, maxLines: 3, maxLength: 2000, decoration: InputDecoration(labelText: a.t('review.text'))), const SizedBox(height: 8), SBtn(a.t('review.submit'), onPressed: () => Navigator.pop(ctx, true))]))));
    if (ok != true) return;
    try { await a.api.post('/api/bookings/$id/review', {'rating': rating, 'body': text.text}); if (c.mounted) toast(c, a.t('review.thanks')); reload(); } catch (e) { if (c.mounted) toast(c, a.err(e)); }
  }
  Future<void> _reschedule(BuildContext c, Map b, VoidCallback reload) async {
    final a = appRead(c); final tz = b['timezone'] as String; var date = ymdInZone(0, tz); List? slots; String err = '';
    await showModalBottomSheet(context: c, isScrollControlled: true, builder: (ctx) => StatefulBuilder(builder: (ctx, setM) {
      Future<void> load() async { setM(() => slots = null); try { final r = await a.api.get('/api/availability?serviceId=${b['serviceId']}&professionalId=${b['professionalId']}&date=$date'); setM(() => slots = r['slots']); } catch (_) { setM(() => slots = []); } }
      if (slots == null && err.isEmpty) { slots = const []; WidgetsBinding.instance.addPostFrameCallback((_) => load()); }
      return Padding(padding: const EdgeInsets.all(16), child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(a.t('bookings.pickNew'), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)), const SizedBox(height: 12),
        SizedBox(height: 56, child: ListView(scrollDirection: Axis.horizontal, children: [for (var i = 0; i < 14; i++) Builder(builder: (_) { final d = ymdInZone(i, tz); return Padding(padding: const EdgeInsets.only(right: 8), child: ChoiceChip(label: Text(d.substring(5)), selected: date == d, onSelected: (_) { date = d; load(); })); })])),
        const SizedBox(height: 8), if (err.isNotEmpty) Alert(err, tone: 'error'),
        Wrap(spacing: 8, runSpacing: 8, children: [for (final s in (slots ?? const []).cast<Map>()) ActionChip(label: Text(fmtTime(s['start'], tz)), onPressed: () async {
          try { await a.api.post('/api/bookings/$id/reschedule', {'startAt': s['start']}); if (ctx.mounted) Navigator.pop(ctx); if (c.mounted) toast(c, a.t('bookings.moved.ok')); reload(); } catch (e) { setM(() => err = a.err(e)); } })]),
      ]));
    }));
  }
}

/// DEV payment panel: resolves a simulated payment through the same event path a real provider webhook uses.
class PayPanel extends StatelessWidget {
  const PayPanel({super.key, required this.bookingId, required this.onDone});
  final int bookingId; final VoidCallback onDone;
  @override Widget build(BuildContext context) {
    final a = app(context);
    return Load<List>(skeleton: const SizedBox(), fn: () async => await a.api.get('/api/bookings/$bookingId/payments'), builder: (c, pays, _) {
      final open = pays.cast<Map>().where((p) => p['status'] == 'PENDING').firstOrNull;
      if (open == null) return const SizedBox();
      if (open['simulated'] != true || !a.simulatedPayments) return Alert(a.t('pay.PENDING'));
      Future<void> sim(bool ok) async { try { await a.api.post('/api/payments/${open['id']}/simulate', {'success': ok}); onDone(); } catch (e) { if (c.mounted) toast(c, a.err(e)); } }
      return Container(padding: const EdgeInsets.all(12), decoration: BoxDecoration(color: C.warningBg, borderRadius: BorderRadius.circular(16)), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(a.t('dev.pay'), style: const TextStyle(fontWeight: FontWeight.w700, color: C.warning)), const SizedBox(height: 8),
        Row(children: [Expanded(child: SBtn(a.t('dev.paySuccess'), onPressed: () => sim(true))), const SizedBox(width: 8), Expanded(child: SBtn(a.t('dev.payFail'), kind: 'danger', onPressed: () => sim(false)))])]));
    });
  }
}

class BookedPage extends StatelessWidget {
  const BookedPage(this.id, {super.key, this.pay = false});
  final int id; final bool pay;
  @override Widget build(BuildContext context) {
    final a = app(context);
    return Load<Map>(fn: () async => Map.from(await a.api.get('/api/bookings/$id')), builder: (c, b, reload) {
      final pending = b['status'] == 'PENDING';
      return Scaffold(body: SafeArea(child: ListView(padding: const EdgeInsets.all(24), children: [
        const SizedBox(height: 24), Center(child: Container(width: 110, height: 110, decoration: const BoxDecoration(gradient: C.grad, shape: BoxShape.circle), child: const Icon(Icons.check, color: Colors.white, size: 60))), const SizedBox(height: 20),
        Text(pending ? a.t('done.pendingTitle') : a.t('done.title'), textAlign: TextAlign.center, style: const TextStyle(fontFamily: serif, fontSize: 26, fontWeight: FontWeight.w700)), const SizedBox(height: 8),
        Text(pending ? a.t('done.pendingText') : a.t('done.text'), textAlign: TextAlign.center, style: const TextStyle(color: C.muted)), const SizedBox(height: 20),
        CardBox(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(b['serviceName'], style: const TextStyle(fontWeight: FontWeight.w700)), Text('${b['businessName']} · ${b['professionalName']}', style: const TextStyle(color: C.muted, fontSize: 13)),
          const SizedBox(height: 6), Row(children: [const Icon(Icons.event, size: 14), Text(' ${fmtDate(b['startAt'], a.locale, b['timezone'])}, ${fmtTime(b['startAt'], b['timezone'])}', style: const TextStyle(fontSize: 13))]),
          const SizedBox(height: 6), Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [Text(a.t('done.ref'), style: const TextStyle(color: C.muted, fontSize: 13)), Text(b['reference'], style: const TextStyle(fontWeight: FontWeight.w800))])])),
        if (pay && b['paymentStatus'] == 'PENDING') Padding(padding: const EdgeInsets.only(top: 12), child: PayPanel(bookingId: id, onDone: reload)),
        if (b['paymentStatus'] == 'PAID') Padding(padding: const EdgeInsets.only(top: 12), child: Alert(a.t('pay.PAID'), tone: 'success')),
        const SizedBox(height: 20), SBtn(a.t('done.view'), onPressed: () => c.go('/bookings/${b['id']}')), const SizedBox(height: 8), SBtn(a.t('done.home'), kind: 'secondary', onPressed: () => c.go('/')),
      ])));
    });
  }
}

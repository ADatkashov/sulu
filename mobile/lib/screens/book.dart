import 'dart:math';
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../core/api.dart';
import '../core/format.dart';
import '../core/theme.dart';
import '../widgets/common.dart';

String _newKey() => '${DateTime.now().microsecondsSinceEpoch}-${Random().nextInt(1 << 32)}';

class BookPage extends StatefulWidget { const BookPage(this.serviceId, {super.key, this.pro}); final int serviceId; final int? pro; @override State<BookPage> createState() => _BookPageState(); }
class _BookPageState extends State<BookPage> {
  late int step = widget.pro != null ? 1 : 0; late int? proId = widget.pro; // null == any master
  String? date; List<Map>? slots; Object? slotsErr; Map? slot; List alts = [];
  final note = TextEditingController(), promo = TextEditingController(); bool online = false, busy = false; String error = ''; String idem = _newKey();

  Future<void> loadSlots(Map s) async {
    final a = appRead(context); setState(() { slots = null; slotsErr = null; slot = null; });
    try {
      final r = await a.api.get('/api/availability?serviceId=${s['id']}${proId == null ? '' : '&professionalId=$proId'}&date=$date');
      if (mounted) setState(() => slots = (r['slots'] as List).cast<Map>());
    } catch (e) { if (mounted) setState(() => slotsErr = e); }
  }
  Future<void> confirm(Map s) async {
    final a = appRead(context);
    if (!a.loggedIn) { goLogin(context, '/book/${widget.serviceId}${proId != null ? '?pro=$proId' : ''}'); return; }
    setState(() { busy = true; error = ''; alts = []; });
    try {
      final b = await a.api.post('/api/bookings', {'serviceId': s['id'], if (proId != null) 'professionalId': proId, 'startAt': slot!['start'], if (note.text.isNotEmpty) 'note': note.text, if (promo.text.isNotEmpty) 'promoCode': promo.text}, {'Idempotency-Key': idem});
      var pay = false;
      if (online) { try { await a.api.post('/api/bookings/${b['id']}/payments', {'method': 'CARD'}, {'Idempotency-Key': 'pay-$idem'}); pay = true; } catch (e) { if (mounted) toast(context, a.err(e)); } }
      if (mounted) context.go('/booked/${b['id']}${pay ? '?pay=1' : ''}');
    } catch (e) {
      if (!mounted) return;
      if (e is ApiException && e.code == 'SLOT_UNAVAILABLE') { setState(() { alts = (e.details?['alternatives'] as List?) ?? []; error = a.t('book.conflict'); idem = _newKey(); slot = null; step = 1; }); }
      else { setState(() => error = a.err(e)); }
    } finally { if (mounted) setState(() => busy = false); }
  }
  @override Widget build(BuildContext context) {
    final a = app(context);
    return Load<Map>(fn: () async => Map.from(await a.api.get('/api/services/${widget.serviceId}')), builder: (c, s, _) {
      final tz = s['timezone'] ?? 'Asia/Almaty'; final days = [for (var i = 0; i < 21; i++) ymdInZone(i, tz)];
      if (date == null) { date = days.first; WidgetsBinding.instance.addPostFrameCallback((_) => loadSlots(s)); }
      final pros = (s['professionals'] as List).cast<Map>();
      final assigned = slot == null ? null : pros.where((p) => p['id'] == slot!['professionalId']).firstOrNull;
      final pro = proId == null ? null : pros.where((p) => p['id'] == proId).firstOrNull;
      return Scaffold(appBar: AppBar(title: Text(a.t('book.title')), leading: BackButton(onPressed: () => step == 0 || widget.pro == null && step == 0 ? (context.canPop() ? context.pop() : context.go('/')) : setState(() => step--))),
        body: ListView(padding: const EdgeInsets.all(16), children: [
          Row(children: [for (var i = 0; i < 3; i++) Expanded(child: Container(height: 4, margin: const EdgeInsets.symmetric(horizontal: 3), decoration: BoxDecoration(color: i <= step ? C.primary : C.border, borderRadius: BorderRadius.circular(2))))]), const SizedBox(height: 16),
          CardBox(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(s['name'], style: const TextStyle(fontWeight: FontWeight.w700)), const SizedBox(height: 4), Row(children: [Flexible(child: Text('${s['businessName']} · ${s['durationMin']} ${a.t('common.min')} · ', style: const TextStyle(color: C.muted, fontSize: 13))), PriceText(s['priceMinor'], s['currency'], from: s['priceFrom'] == true)])])),
          const SizedBox(height: 16),
          if (step == 0) ...[
            Text(a.t('book.step.master'), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)), const SizedBox(height: 10),
            _opt(proId == null, () => setState(() => proId = null), const CircleAvatar(backgroundColor: C.primary, child: Icon(Icons.groups_outlined, color: Colors.white)), a.t('book.anyMaster'), a.t('book.anyMasterHint')),
            for (final p in pros) _opt(proId == p['id'], () => setState(() => proId = p['id']), Avatar(p['fullName'], size: 40, url: a.api.media(p['avatarKey'])), p['fullName'], p['title'] ?? '', trailing: Stars(num.parse('${p['ratingAvg']}'))),
            const SizedBox(height: 8), SBtn(a.t('common.continue'), onPressed: () { setState(() => step = 1); loadSlots(s); }),
          ],
          if (step == 1) ...[
            if (error.isNotEmpty) Padding(padding: const EdgeInsets.only(bottom: 10), child: Alert(error, tone: 'warning')),
            if (alts.isNotEmpty) Padding(padding: const EdgeInsets.only(bottom: 12), child: Wrap(spacing: 8, runSpacing: 8, children: [for (final x in alts.take(8)) ActionChip(label: Text(fmtTime(x['start'], tz)), onPressed: () => setState(() { date = ymdInZone(0, tz, DateTime.parse(x['start'])); slot = {...x, 'end': x['start']}; error = ''; alts = []; }))])),
            Text(a.t('book.chooseDate'), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)), const SizedBox(height: 10),
            SizedBox(height: 74, child: ListView(scrollDirection: Axis.horizontal, children: [for (final d in days) Padding(padding: const EdgeInsets.only(right: 8), child: InkWell(borderRadius: BorderRadius.circular(16), onTap: () { setState(() => date = d); loadSlots(s); },
              child: Container(width: 58, decoration: BoxDecoration(color: date == d ? C.primary : Colors.white, borderRadius: BorderRadius.circular(16), border: Border.all(color: date == d ? C.primary : C.border, width: 1.5)), child: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
                Text(fmtDate('${d}T12:00:00Z', a.locale, 'UTC').split(',').first, style: TextStyle(fontSize: 11, color: date == d ? Colors.white70 : C.muted)), Text('${int.parse(d.substring(8))}', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800, color: date == d ? Colors.white : C.text))]))))])),
            const SizedBox(height: 16), Text(a.t('book.chooseTime'), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)), const SizedBox(height: 10),
            if (slotsErr != null) EmptyView(a.t('error.title'), text: a.err(slotsErr!), icon: Icons.error_outline, action: SBtn(a.t('common.retry'), kind: 'secondary', onPressed: () => loadSlots(s)))
            else if (slots == null) const Skel(h: 120)
            else if (slots!.isEmpty) Alert(a.t('book.noSlots'))
            else GridView.count(crossAxisCount: 4, shrinkWrap: true, physics: const NeverScrollableScrollPhysics(), mainAxisSpacing: 8, crossAxisSpacing: 8, childAspectRatio: 1.9, children: [for (final x in slots!) InkWell(borderRadius: BorderRadius.circular(16), onTap: () => setState(() => slot = x),
              child: Container(alignment: Alignment.center, decoration: BoxDecoration(color: slot?['start'] == x['start'] ? C.primary : Colors.white, borderRadius: BorderRadius.circular(16), border: Border.all(color: slot?['start'] == x['start'] ? C.primary : C.border, width: 1.5)),
                child: Text(fmtTime(x['start'], tz), style: TextStyle(fontWeight: FontWeight.w700, color: slot?['start'] == x['start'] ? Colors.white : C.text))))]),
            const SizedBox(height: 8), Text(a.t('book.timezone', {'tz': tz}), style: const TextStyle(color: C.muted, fontSize: 12)), const SizedBox(height: 12),
            SBtn(a.t('common.continue'), onPressed: slot == null ? null : () => setState(() => step = 2)),
          ],
          if (step == 2 && slot != null) ...[
            Text(a.t('book.summary'), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)), const SizedBox(height: 10),
            CardBox(child: Column(children: [_kv(a.t('book.master'), (assigned ?? pro)?['fullName'] ?? '—'), _kv(a.t('book.when'), '${fmtDate(slot!['start'], a.locale, tz)}, ${fmtTime(slot!['start'], tz)}'), _kv(a.t('book.where'), '${s['businessName']}, ${s['address']}')])),
            const SizedBox(height: 12), TextField(controller: note, maxLength: 500, maxLines: 3, decoration: InputDecoration(labelText: '${a.t('book.notes')} (${a.t('common.optional')})')),
            const SizedBox(height: 4), TextField(controller: promo, textCapitalization: TextCapitalization.characters, decoration: InputDecoration(labelText: '${a.t('book.promo')} (${a.t('common.optional')})')),
            const SizedBox(height: 16), Text(a.t('book.payment'), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)), const SizedBox(height: 10),
            _opt(!online, () => setState(() => online = false), const SizedBox(), a.t('book.payAtSalon'), ''),
            if (a.simulatedPayments) _opt(online, () => setState(() => online = true), const SizedBox(), a.t('book.payOnline'), '', trailing: const Tag('DEV')),
            const SizedBox(height: 8), CardBox(child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [Text(a.t('book.total'), style: const TextStyle(fontWeight: FontWeight.w800)), PriceText(s['priceMinor'], s['currency'], size: 20)])),
            if (promo.text.isNotEmpty) Padding(padding: const EdgeInsets.only(top: 6), child: Text(a.t('book.promoNote'), style: const TextStyle(color: C.muted, fontSize: 12))),
            if (error.isNotEmpty) Padding(padding: const EdgeInsets.only(top: 12), child: Alert(error, tone: 'error')),
            if (!a.loggedIn) Padding(padding: const EdgeInsets.only(top: 12), child: Alert(a.t('book.loginRequired'))),
            const SizedBox(height: 12), SBtn(a.loggedIn ? a.t('book.confirm') : a.t('auth.login'), loading: busy, onPressed: () => confirm(s)),
          ],
        ]));
    });
  }
  Widget _kv(String k, String v) => Padding(padding: const EdgeInsets.symmetric(vertical: 4), child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [SizedBox(width: 90, child: Text(k, style: const TextStyle(color: C.muted))), Expanded(child: Text(v, style: const TextStyle(fontWeight: FontWeight.w600)))]));
  Widget _opt(bool on, VoidCallback tap, Widget lead, String title, String sub, {Widget? trailing}) => Padding(padding: const EdgeInsets.only(bottom: 8), child: InkWell(borderRadius: BorderRadius.circular(16), onTap: tap,
    child: Container(padding: const EdgeInsets.all(12), decoration: BoxDecoration(color: on ? C.bgSoft : Colors.white, borderRadius: BorderRadius.circular(16), border: Border.all(color: on ? C.primary : C.border, width: 1.5)),
      child: Row(children: [Icon(on ? Icons.radio_button_checked : Icons.radio_button_off, color: on ? C.primary : C.border), const SizedBox(width: 10), lead, if (lead is! SizedBox) const SizedBox(width: 10),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(title, style: const TextStyle(fontWeight: FontWeight.w700)), if (sub.isNotEmpty) Text(sub, style: const TextStyle(color: C.muted, fontSize: 13))])), if (trailing != null) trailing]))));
}

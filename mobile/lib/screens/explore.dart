import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../core/api.dart';
import '../core/i18n.dart';
import '../core/theme.dart';
import '../widgets/cards.dart';
import '../widgets/common.dart';
import '../state/app_state.dart';

class Explore extends StatefulWidget {
  const Explore({super.key, this.q = '', this.category = '', this.tab = 'services'});
  final String q, category, tab;
  @override State<Explore> createState() => _ExploreState();
}
class _ExploreState extends State<Explore> {
  late final TextEditingController ctrl = TextEditingController(text: widget.q);
  late String q = widget.q, category = widget.category, tab = widget.tab; String sort = 'rating', gender = ''; String maxPrice = ''; bool atHome = false;

  Future<Map> fetch(AppState a) async {
    final mp = maxPrice.isEmpty ? null : (num.tryParse(maxPrice) ?? 0) * 100;
    if (tab == 'services') return await a.api.get('/api/services${qs({'q': q, 'city': a.city, 'category': category, 'maxPriceMinor': mp, 'atHome': atHome, 'gender': gender, 'sort': sort == 'distance' ? 'rating' : sort, 'size': 30})}');
    if (tab == 'salons') return await a.api.get('/api/businesses${qs({'q': q, 'city': a.city, 'category': category, 'maxPriceMinor': mp, 'sort': sort, 'size': 30})}');
    return await a.api.get('/api/professionals${qs({'q': q, 'city': a.city, 'category': category, 'gender': gender, 'size': 30})}');
  }
  void filters(AppState a) {
    final sorts = tab == 'services' ? ['rating', 'price_asc', 'price_desc', 'duration'] : tab == 'salons' ? ['rating', 'price_asc', 'price_desc'] : ['rating'];
    showModalBottomSheet(context: context, isScrollControlled: true, backgroundColor: Colors.white, shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(28))), builder: (ctx) => StatefulBuilder(builder: (ctx, setM) => Padding(
      padding: EdgeInsets.fromLTRB(16, 20, 16, MediaQuery.of(ctx).viewInsets.bottom + 20), child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(a.t('explore.filters'), style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)), const SizedBox(height: 12),
        DropdownButtonFormField<String>(initialValue: sorts.contains(sort) ? sort : 'rating', decoration: InputDecoration(labelText: a.t('explore.sort')), items: [for (final s in sorts) DropdownMenuItem(value: s, child: Text(a.t('sort.$s')))], onChanged: (v) => setM(() => sort = v!)),
        if (tab != 'masters') ...[const SizedBox(height: 12), TextFormField(initialValue: maxPrice, keyboardType: TextInputType.number, decoration: InputDecoration(labelText: '${a.t('filter.maxPrice')} (${a.t('common.optional')})'), onChanged: (v) => maxPrice = v)],
        if (tab != 'salons') ...[const SizedBox(height: 12), DropdownButtonFormField<String>(initialValue: gender, decoration: InputDecoration(labelText: a.t('filter.gender')), items: [DropdownMenuItem(value: '', child: Text(a.t('gender.any'))), DropdownMenuItem(value: 'FEMALE', child: Text(a.t('gender.FEMALE'))), DropdownMenuItem(value: 'MALE', child: Text(a.t('gender.MALE')))], onChanged: (v) => setM(() => gender = v!))],
        if (tab == 'services') SwitchListTile(contentPadding: EdgeInsets.zero, title: Text(a.t('filter.atHome')), value: atHome, onChanged: (v) => setM(() => atHome = v)),
        const SizedBox(height: 12), Row(children: [Expanded(child: SBtn(a.t('filter.reset'), kind: 'soft', onPressed: () { setState(() { sort = 'rating'; gender = ''; maxPrice = ''; atHome = false; }); Navigator.pop(ctx); })), const SizedBox(width: 12), Expanded(child: SBtn(a.t('common.apply'), onPressed: () { setState(() {}); Navigator.pop(ctx); }))]),
      ]))));
  }
  @override Widget build(BuildContext context) {
    final a = app(context);
    return SafeArea(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Padding(padding: const EdgeInsets.fromLTRB(16, 12, 16, 8), child: Text(a.t('explore.title'), style: const TextStyle(fontFamily: serif, fontSize: 22, fontWeight: FontWeight.w700))),
      Padding(padding: const EdgeInsets.symmetric(horizontal: 16), child: Row(children: [
        Expanded(child: TextField(controller: ctrl, textInputAction: TextInputAction.search, onSubmitted: (v) => setState(() => q = v), decoration: InputDecoration(hintText: a.t('home.search'), prefixIcon: const Icon(Icons.search), suffixIcon: q.isEmpty ? null : IconButton(icon: const Icon(Icons.close), onPressed: () { ctrl.clear(); setState(() => q = ''); })))),
        const SizedBox(width: 8), IconButton.outlined(tooltip: a.t('explore.filters'), onPressed: () => filters(a), icon: const Icon(Icons.tune))])),
      Padding(padding: const EdgeInsets.all(16), child: SegmentedButton<String>(showSelectedIcon: false, segments: [for (final t in ['services', 'salons', 'masters']) ButtonSegment(value: t, label: Text(a.t('explore.$t')))], selected: {tab}, onSelectionChanged: (s) => setState(() => tab = s.first))),
      Load<List>(fn: () async => await a.api.get('/api/categories'), skeleton: const SizedBox(height: 44), builder: (c, cats, _) => SizedBox(height: 44, child: ListView(scrollDirection: Axis.horizontal, padding: const EdgeInsets.symmetric(horizontal: 16), children: [
        Padding(padding: const EdgeInsets.only(right: 8), child: ChoiceChip(label: Text(a.t('common.all')), selected: category.isEmpty, onSelected: (_) => setState(() => category = ''), showCheckmark: false, selectedColor: C.primary, labelStyle: TextStyle(color: category.isEmpty ? Colors.white : C.text))),
        for (final cat in cats) Padding(padding: const EdgeInsets.only(right: 8), child: ChoiceChip(avatar: Icon(iconFor(cat['slug']), size: 16, color: category == cat['slug'] ? Colors.white : C.text), label: Text(catName(cat, a.locale)), selected: category == cat['slug'], onSelected: (_) => setState(() => category = cat['slug']), showCheckmark: false, selectedColor: C.primary, labelStyle: TextStyle(color: category == cat['slug'] ? Colors.white : C.text)))]))),
      Expanded(child: Load<Map>(keys: [q, category, tab, sort, gender, maxPrice, atHome, a.city], fn: () => fetch(a), builder: (c, d, _) {
        final items = d['items'] as List;
        if (items.isEmpty) return SingleChildScrollView(child: EmptyView(a.t('empty.title'), text: a.t('empty.text'), icon: Icons.search, action: SBtn(a.t('filter.reset'), kind: 'soft', onPressed: () => context.go('/explore'))));
        if (tab == 'masters') return GridView.count(crossAxisCount: 2, padding: const EdgeInsets.all(16), mainAxisSpacing: 12, crossAxisSpacing: 12, childAspectRatio: .78, children: [for (final p in items) ProCard(p)]);
        return ListView.separated(padding: const EdgeInsets.all(16), itemCount: items.length, separatorBuilder: (_, __) => const SizedBox(height: 10), itemBuilder: (_, i) => tab == 'salons' ? SalonCard(items[i], row: true) : ServiceCard(items[i]));
      })),
    ]));
  }
}

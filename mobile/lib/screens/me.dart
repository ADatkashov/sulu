import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../core/format.dart';
import '../core/i18n.dart';
import '../core/theme.dart';
import '../widgets/cards.dart';
import '../widgets/common.dart';

class FavoritesPage extends StatelessWidget {
  const FavoritesPage({super.key});
  @override Widget build(BuildContext context) {
    final a = app(context);
    if (!a.loggedIn) return SafeArea(child: Center(child: EmptyView(a.t('fav.title'), text: a.t('fav.loginRequired'), icon: Icons.favorite_border, action: SBtn(a.t('auth.login'), onPressed: () => goLogin(context, '/favorites')))));
    return SafeArea(child: Load<Map>(keys: [a.user?['id']], fn: () async => Map.from(await a.api.get('/api/favorites')), builder: (c, d, _) {
      final none = (d['businesses'] as List).length + (d['professionals'] as List).length + (d['services'] as List).length == 0;
      return ListView(padding: const EdgeInsets.all(16), children: [
        Text(a.t('fav.title'), style: const TextStyle(fontFamily: serif, fontSize: 22, fontWeight: FontWeight.w700)), const SizedBox(height: 12),
        if (none) EmptyView(a.t('fav.title'), text: a.t('fav.empty'), icon: Icons.favorite_border, action: SBtn(a.t('bookings.findSomething'), onPressed: () => c.go('/explore'))),
        if ((d['businesses'] as List).isNotEmpty) ...[SectionHead(a.t('fav.salons')), for (final b in d['businesses']) Padding(padding: const EdgeInsets.only(bottom: 10), child: SalonCard(b, row: true))],
        if ((d['professionals'] as List).isNotEmpty) ...[SectionHead(a.t('fav.masters')), SizedBox(height: 214, child: ListView(scrollDirection: Axis.horizontal, children: [for (final p in d['professionals']) Padding(padding: const EdgeInsets.only(right: 12), child: ProCard(p))]))],
        if ((d['services'] as List).isNotEmpty) ...[const SizedBox(height: 12), SectionHead(a.t('fav.services')), for (final s in d['services']) Padding(padding: const EdgeInsets.only(bottom: 10), child: ServiceCard(s))],
      ]);
    }));
  }
}

class NotificationsPage extends StatelessWidget {
  const NotificationsPage({super.key});
  @override Widget build(BuildContext context) {
    final a = app(context);
    return Scaffold(appBar: AppBar(title: Text(a.t('notif.title')), leading: BackButton(onPressed: () => context.canPop() ? context.pop() : context.go('/'))), body: !a.loggedIn
      ? EmptyView(a.t('notif.title'), text: a.t('profile.guest'), icon: Icons.notifications_none, action: SBtn(a.t('auth.login'), onPressed: () => goLogin(context, '/notifications')))
      : Load<Map>(fn: () async => Map.from(await a.api.get('/api/notifications')), builder: (c, d, reload) => (d['items'] as List).isEmpty ? EmptyView(a.t('notif.empty'), icon: Icons.notifications_none) : ListView(padding: const EdgeInsets.all(16), children: [
        for (final n in d['items']) Padding(padding: const EdgeInsets.only(bottom: 10), child: CardBox(onTap: () async {
          if (n['read'] != true) { try { await a.api.post('/api/notifications/${n['id']}/read'); } catch (_) {} a.refreshUnread(); }
          final l = n['link']?.toString(); if (l != null && l.startsWith('/bookings') && c.mounted) { c.push(l); } else { reload(); } },
          child: Row(children: [Container(width: 44, height: 44, decoration: BoxDecoration(gradient: C.gradSoft, borderRadius: BorderRadius.circular(14)), child: const Icon(Icons.notifications_none, color: C.rose)), const SizedBox(width: 12),
            Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(n['title'], style: TextStyle(fontWeight: n['read'] == true ? FontWeight.w500 : FontWeight.w800)), if ((n['body'] ?? '').toString().isNotEmpty) Text(n['body'], style: const TextStyle(color: C.muted, fontSize: 13)),
              Text('${fmtDate(n['createdAt'], a.locale, null, weekday: false)} ${fmtTime(n['createdAt'], null)}', style: const TextStyle(color: C.muted, fontSize: 11))]))])))])));
  }
}

class ProfilePage extends StatefulWidget { const ProfilePage({super.key}); @override State<ProfilePage> createState() => _ProfilePageState(); }
class _ProfilePageState extends State<ProfilePage> {
  late final name = TextEditingController(text: appRead(context).user?['fullName']), phone = TextEditingController(text: appRead(context).user?['phone']);
  Future<void> patch(Map body, {bool ok = true}) async { final a = appRead(context); try { await a.api.request('PATCH', '/api/me', body: body); await a.refreshUser(); if (ok && mounted) toast(context, a.t('profile.saved')); } catch (e) { if (mounted) toast(context, a.err(e)); } }
  @override Widget build(BuildContext context) {
    final a = app(context); final u = a.user;
    final langCity = Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      DropdownButtonFormField<String>(initialValue: a.locale, decoration: InputDecoration(labelText: a.t('common.language')), items: [for (final l in supportedLocales) DropdownMenuItem(value: l, child: Text(localeLabels[l]!))], onChanged: (v) { a.setLocale(v!); if (a.loggedIn) patch({'language': v}, ok: false); }), const SizedBox(height: 12),
      if (a.cities.isNotEmpty) DropdownButtonFormField<String>(initialValue: a.cities.contains(a.city) ? a.city : a.cities.first, decoration: InputDecoration(labelText: a.t('common.city')), items: [for (final c in a.cities) DropdownMenuItem(value: c, child: Text(c))], onChanged: (v) { a.setCity(v!); if (a.loggedIn) patch({'city': v}, ok: false); }),
    ]);
    if (u == null) return SafeArea(child: ListView(padding: const EdgeInsets.all(16), children: [EmptyView(a.t('profile.title'), text: a.t('profile.guest'), icon: Icons.person_outline, action: Column(children: [SBtn(a.t('auth.login'), onPressed: () => context.push('/login')), const SizedBox(height: 8), SBtn(a.t('auth.register'), kind: 'secondary', onPressed: () => context.push('/register'))])), langCity]));
    return SafeArea(child: ListView(padding: const EdgeInsets.all(16), children: [
      Row(children: [Avatar(u['fullName'], size: 64), const SizedBox(width: 14), Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(u['fullName'], style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800)), Text(u['email'], style: const TextStyle(color: C.muted))]))]),
      if (u['role'] != 'CUSTOMER') Padding(padding: const EdgeInsets.only(top: 12), child: Alert(a.t('profile.forBusiness'))),
      const SizedBox(height: 16), TextField(controller: name, decoration: InputDecoration(labelText: a.t('auth.fullName'))), const SizedBox(height: 12),
      TextField(controller: phone, keyboardType: TextInputType.phone, decoration: InputDecoration(labelText: a.t('auth.phone'))), const SizedBox(height: 12),
      SBtn(a.t('common.save'), kind: 'secondary', onPressed: () => patch({'fullName': name.text, if (phone.text.isNotEmpty) 'phone': phone.text})), const SizedBox(height: 16), langCity,
      const SizedBox(height: 8), SectionHead(a.t('profile.notifications')),
      SwitchListTile(contentPadding: EdgeInsets.zero, title: Text(a.t('profile.notifyEmail')), value: u['notifyEmail'] == true, onChanged: (v) => patch({'notifyEmail': v})),
      SwitchListTile(contentPadding: EdgeInsets.zero, title: Text(a.t('profile.notifyPush')), value: u['notifyPush'] == true, onChanged: (v) => patch({'notifyPush': v})),
      const SizedBox(height: 8), SectionHead(a.t('profile.privacy')),
      SBtn(a.t('profile.delete'), kind: 'danger', icon: Icons.delete_outline, onPressed: () async {
        final ok = await showDialog<bool>(context: context, builder: (ctx) => AlertDialog(title: Text(a.t('profile.delete')), content: Text(a.t('profile.deleteConfirm')), actions: [TextButton(onPressed: () => Navigator.pop(ctx, false), child: Text(a.t('common.cancel'))), TextButton(onPressed: () => Navigator.pop(ctx, true), child: Text(a.t('common.delete'), style: const TextStyle(color: C.error)))]));
        if (ok != true) return;
        try { await a.api.delete('/api/me'); await a.logout(); if (context.mounted) context.go('/'); } catch (e) { if (context.mounted) toast(context, a.err(e)); } }),
      const SizedBox(height: 10), SBtn(a.t('auth.logout'), kind: 'soft', icon: Icons.logout, onPressed: () async { await a.logout(); if (context.mounted) context.go('/'); }),
    ]));
  }
}

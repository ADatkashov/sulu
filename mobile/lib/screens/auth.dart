import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import '../core/api.dart';
import '../core/i18n.dart';
import '../core/theme.dart';
import '../widgets/common.dart';

class Onboarding extends StatefulWidget { const Onboarding({super.key}); @override State<Onboarding> createState() => _OnboardingState(); }
class _OnboardingState extends State<Onboarding> {
  int step = 0;
  static const icons = [Icons.place_outlined, Icons.star_outline, Icons.schedule, Icons.calendar_month_outlined];
  @override Widget build(BuildContext context) {
    final a = app(context); final last = step == 3;
    Future<void> finish(String to) async { await a.finishOnboarding(); if (context.mounted) context.go(to); }
    final cities = a.cities.isEmpty ? [a.city.isEmpty ? 'Алматы' : a.city] : a.cities;
    return Scaffold(body: Container(decoration: const BoxDecoration(gradient: C.gradSoft), child: SafeArea(child: Padding(padding: const EdgeInsets.all(24), child: Column(children: [
      Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [const SuluLogo(), TextButton(onPressed: () => finish('/'), child: Text(a.t('common.skip')))]),
      const SizedBox(height: 8),
      Wrap(spacing: 8, children: [for (final l in supportedLocales) ChoiceChip(label: Text(localeLabels[l]!), selected: a.locale == l, onSelected: (_) => a.setLocale(l), selectedColor: C.primary, labelStyle: TextStyle(color: a.locale == l ? Colors.white : C.text), showCheckmark: false)]),
      Expanded(child: Center(child: Container(width: 190, height: 190, decoration: BoxDecoration(gradient: C.grad, borderRadius: BorderRadius.circular(80), boxShadow: [BoxShadow(color: C.primary.withValues(alpha: .35), blurRadius: 40, offset: const Offset(0, 20))]), child: Icon(icons[step], size: 84, color: Colors.white)))),
      Text(a.t('ob.${step + 1}.title'), textAlign: TextAlign.center, style: const TextStyle(fontFamily: serif, fontSize: 26, fontWeight: FontWeight.w700)),
      const SizedBox(height: 8), Text(a.t('ob.${step + 1}.text'), textAlign: TextAlign.center, style: const TextStyle(color: C.muted)),
      const SizedBox(height: 16), Row(children: [for (var i = 0; i < 4; i++) Expanded(child: Container(height: 4, margin: const EdgeInsets.symmetric(horizontal: 3), decoration: BoxDecoration(color: i <= step ? C.primary : C.border, borderRadius: BorderRadius.circular(2))))]),
      const SizedBox(height: 16),
      if (last) ...[
        DropdownButtonFormField<String>(initialValue: cities.contains(a.city) ? a.city : cities.first, decoration: InputDecoration(labelText: a.t('ob.chooseCity')), items: [for (final c in cities) DropdownMenuItem(value: c, child: Text(c))], onChanged: (v) => a.setCity(v!)),
        const SizedBox(height: 6), Text(a.t('ob.noLocation'), style: const TextStyle(color: C.muted, fontSize: 12)), const SizedBox(height: 12),
        SizedBox(width: double.infinity, child: SBtn(a.t('ob.guest'), onPressed: () => finish('/'))), const SizedBox(height: 8),
        SizedBox(width: double.infinity, child: SBtn(a.t('ob.signin'), kind: 'secondary', onPressed: () => finish('/login'))),
      ] else SizedBox(width: double.infinity, child: SBtn(a.t('common.continue'), onPressed: () => setState(() => step++))),
    ])))));
  }
}

String homeFor(String role) => role == 'BUSINESS_OWNER' || role == 'PLATFORM_ADMIN' || role == 'PROFESSIONAL' ? '/profile' : '/';

class Login extends StatefulWidget { const Login({super.key, this.next}); final String? next; @override State<Login> createState() => _LoginState(); }
class _LoginState extends State<Login> {
  final email = TextEditingController(), pw = TextEditingController(); String error = ''; bool busy = false;
  Future<void> submit() async {
    final a = appRead(context); setState(() { busy = true; error = ''; });
    try { final u = await a.login(email.text.trim(), pw.text); if (mounted) context.go(widget.next != null && u['role'] == 'CUSTOMER' ? widget.next! : homeFor(u['role'])); }
    catch (e) { if (mounted) setState(() => error = a.err(e)); } finally { if (mounted) setState(() => busy = false); }
  }
  @override Widget build(BuildContext context) {
    final a = app(context);
    return Scaffold(appBar: AppBar(leading: BackButton(onPressed: () => context.canPop() ? context.pop() : context.go('/'))), body: ListView(padding: const EdgeInsets.all(24), children: [
      const Center(child: SuluLogo(size: 44)), const SizedBox(height: 16),
      Center(child: Text(a.t('auth.login'), style: const TextStyle(fontFamily: serif, fontSize: 26, fontWeight: FontWeight.w700))), const SizedBox(height: 20),
      TextField(controller: email, keyboardType: TextInputType.emailAddress, autofillHints: const [AutofillHints.email], decoration: InputDecoration(labelText: a.t('auth.email'))), const SizedBox(height: 12),
      TextField(controller: pw, obscureText: true, autofillHints: const [AutofillHints.password], decoration: InputDecoration(labelText: a.t('auth.password')), onSubmitted: (_) => submit()),
      if (error.isNotEmpty) Padding(padding: const EdgeInsets.only(top: 12), child: Alert(error, tone: 'error')),
      const SizedBox(height: 16), SBtn(a.t('auth.login'), loading: busy, onPressed: submit), const SizedBox(height: 8),
      Row(mainAxisAlignment: MainAxisAlignment.center, children: [Text('${a.t('auth.noAccount')} ', style: const TextStyle(color: C.muted)), GestureDetector(onTap: () => context.push('/register${widget.next != null ? '?next=${Uri.encodeComponent(widget.next!)}' : ''}'), child: Text(a.t('auth.register'), style: const TextStyle(color: C.primary, fontWeight: FontWeight.w700)))]),
      const SizedBox(height: 16), Alert(a.t('auth.demoHint')),
    ]));
  }
}

class Register extends StatefulWidget { const Register({super.key, this.next}); final String? next; @override State<Register> createState() => _RegisterState(); }
class _RegisterState extends State<Register> {
  final name = TextEditingController(), email = TextEditingController(), phone = TextEditingController(), pw = TextEditingController(); String error = ''; bool busy = false; Map fields = {};
  Future<void> submit() async {
    final a = appRead(context); setState(() { busy = true; error = ''; fields = {}; });
    try {
      final u = await a.register({'fullName': name.text.trim(), 'email': email.text.trim(), 'password': pw.text, if (phone.text.isNotEmpty) 'phone': phone.text.trim(), 'role': 'CUSTOMER'});
      if (mounted) context.go(widget.next ?? homeFor(u['role']));
    } catch (e) { if (mounted) setState(() { error = a.err(e); if (e is ApiException && e.details is Map) fields = e.details; }); } finally { if (mounted) setState(() => busy = false); }
  }
  @override Widget build(BuildContext context) {
    final a = app(context);
    return Scaffold(appBar: AppBar(), body: ListView(padding: const EdgeInsets.all(24), children: [
      const Center(child: SuluLogo(size: 44)), const SizedBox(height: 16),
      Center(child: Text(a.t('auth.register'), style: const TextStyle(fontFamily: serif, fontSize: 26, fontWeight: FontWeight.w700))), const SizedBox(height: 20),
      TextField(controller: name, decoration: InputDecoration(labelText: a.t('auth.fullName'), errorText: fields['fullName'])), const SizedBox(height: 12),
      TextField(controller: email, keyboardType: TextInputType.emailAddress, decoration: InputDecoration(labelText: a.t('auth.email'), errorText: fields['email'])), const SizedBox(height: 12),
      TextField(controller: phone, keyboardType: TextInputType.phone, decoration: InputDecoration(labelText: '${a.t('auth.phone')} (${a.t('common.optional')})')), const SizedBox(height: 12),
      TextField(controller: pw, obscureText: true, decoration: InputDecoration(labelText: a.t('auth.password'), helperText: a.t('auth.passwordHint'))),
      if (error.isNotEmpty) Padding(padding: const EdgeInsets.only(top: 12), child: Alert(error, tone: 'error')),
      const SizedBox(height: 16), SBtn(a.t('auth.register'), loading: busy, onPressed: submit),
    ]));
  }
}

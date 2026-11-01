import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';
import '../core/format.dart';
import '../core/theme.dart';
import '../state/app_state.dart';

AppState app(BuildContext c) => c.watch<AppState>();
AppState appRead(BuildContext c) => c.read<AppState>();

const catIcons = {
  'haircut': Icons.content_cut, 'coloring': Icons.palette_outlined, 'manicure': Icons.back_hand_outlined, 'pedicure': Icons.spa_outlined,
  'makeup': Icons.brush_outlined, 'brows': Icons.visibility_outlined, 'lashes': Icons.remove_red_eye_outlined, 'skincare': Icons.water_drop_outlined,
  'cosmetology': Icons.science_outlined, 'barber': Icons.face_6_outlined, 'massage': Icons.self_improvement, 'spa': Icons.hot_tub_outlined,
};
IconData iconFor(String? slug) => catIcons[slug] ?? Icons.auto_awesome;

void toast(BuildContext c, String m) => ScaffoldMessenger.of(c)..hideCurrentSnackBar()..showSnackBar(SnackBar(content: Text(m), behavior: SnackBarBehavior.floating, backgroundColor: C.text));

class SBtn extends StatelessWidget {
  const SBtn(this.label, {super.key, this.onPressed, this.kind = 'primary', this.loading = false, this.icon});
  final String label; final VoidCallback? onPressed; final String kind; final bool loading; final IconData? icon;
  @override Widget build(BuildContext context) {
    final off = onPressed == null || loading;
    final fg = kind == 'primary' ? Colors.white : kind == 'danger' ? C.error : C.primary;
    final child = Row(mainAxisAlignment: MainAxisAlignment.center, mainAxisSize: MainAxisSize.min, children: [
      if (loading) const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
      else ...[if (icon != null) ...[Icon(icon, size: 18, color: fg), const SizedBox(width: 8)], Flexible(child: Text(label, textAlign: TextAlign.center, style: TextStyle(color: fg, fontWeight: FontWeight.w700, fontSize: 15)))],
    ]);
    return Semantics(button: true, enabled: !off, label: label, child: Opacity(opacity: off && !loading ? .55 : 1, child: Material(
      color: Colors.transparent,
      child: Ink(
        decoration: BoxDecoration(
          gradient: kind == 'primary' ? C.grad : null, color: kind == 'soft' ? C.bgSoft : kind == 'danger' ? C.errorBg : kind == 'secondary' ? Colors.white : null,
          border: kind == 'secondary' ? Border.all(color: C.primary, width: 1.5) : null, borderRadius: BorderRadius.circular(999),
          boxShadow: kind == 'primary' ? [BoxShadow(color: C.primary.withValues(alpha: .28), blurRadius: 18, offset: const Offset(0, 8))] : null),
        child: InkWell(borderRadius: BorderRadius.circular(999), onTap: off ? null : onPressed,
            child: ConstrainedBox(constraints: const BoxConstraints(minHeight: 48), child: Center(heightFactor: 1, child: Padding(padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 8), child: child)))),
      ),
    )));
  }
}

class Tag extends StatelessWidget {
  const Tag(this.text, {super.key, this.tone = ''});
  final String text; final String tone;
  @override Widget build(BuildContext c) {
    final (bg, fg) = switch (tone) { 'success' => (C.successBg, C.success), 'warning' => (C.warningBg, C.warning), 'error' => (C.errorBg, C.error), _ => (C.lavSoft, const Color(0xFF5A3FA0)) };
    return Container(padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 3), decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(999)),
        child: Text(text, style: TextStyle(color: fg, fontSize: 12, fontWeight: FontWeight.w700)));
  }
}

class Stars extends StatelessWidget {
  const Stars(this.value, {super.key, this.count});
  final num value; final int? count;
  @override Widget build(BuildContext c) => Row(mainAxisSize: MainAxisSize.min, children: [
    const Icon(Icons.star_rounded, size: 16, color: Color(0xFFF5A623)), const SizedBox(width: 3),
    Flexible(child: Text(count == 0 ? appRead(c).t('common.noReviews') : value.toStringAsFixed(1), overflow: TextOverflow.ellipsis, style: TextStyle(fontWeight: FontWeight.w700, fontSize: 13, color: count == 0 ? C.muted : C.text))),
    if (count != null && count! > 0) Text(' ($count)', style: const TextStyle(color: C.muted, fontSize: 13)),
  ]);
}

class PriceText extends StatelessWidget {
  const PriceText(this.minor, this.currency, {super.key, this.from = false, this.size = 14});
  final num minor; final String currency; final bool from; final double size;
  @override Widget build(BuildContext c) {
    final a = app(c);
    return Text('${from ? '${a.t('common.from')} ' : ''}${money(minor, currency, a.locale)}', style: TextStyle(fontWeight: FontWeight.w800, color: C.roseDeep, fontSize: size));
  }
}

class Photo extends StatelessWidget {
  const Photo({super.key, this.url, this.icon = Icons.auto_awesome, this.w, this.h, this.radius = 16});
  final String? url; final IconData icon; final double? w, h; final double radius;
  @override Widget build(BuildContext c) => ClipRRect(borderRadius: BorderRadius.circular(radius), child: Container(
    width: w, height: h, decoration: const BoxDecoration(gradient: C.gradSoft),
    child: url != null ? Image.network(url!, fit: BoxFit.cover, errorBuilder: (_, __, ___) => Icon(icon, size: 34, color: C.primary)) : Icon(icon, size: 34, color: C.primary)));
}

class Avatar extends StatelessWidget {
  const Avatar(this.name, {super.key, this.size = 48, this.url});
  final String name; final double size; final String? url;
  @override Widget build(BuildContext c) {
    final initials = name.split(RegExp(r'\s+')).where((w) => w.isNotEmpty).take(2).map((w) => w[0]).join().toUpperCase();
    final hue = name.codeUnits.fold<int>(0, (a, b) => a + b) % 40;
    return Container(width: size, height: size, alignment: Alignment.center, clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(shape: BoxShape.circle, gradient: LinearGradient(colors: [HSLColor.fromAHSL(1, 320.0 + hue, .75, .7).toColor(), HSLColor.fromAHSL(1, 300.0 + hue, .6, .5).toColor()])),
      child: url != null ? Image.network(url!, fit: BoxFit.cover, width: size, height: size, errorBuilder: (_, __, ___) => _i(initials)) : _i(initials));
  }
  Widget _i(String s) => Text(s, style: TextStyle(color: Colors.white, fontWeight: FontWeight.w800, fontSize: size / 3));
}

class CardBox extends StatelessWidget {
  const CardBox({super.key, required this.child, this.onTap, this.padding = const EdgeInsets.all(14)});
  final Widget child; final VoidCallback? onTap; final EdgeInsets padding;
  @override Widget build(BuildContext c) => Material(color: Colors.white, borderRadius: BorderRadius.circular(22), child: InkWell(borderRadius: BorderRadius.circular(22), onTap: onTap,
    child: Ink(decoration: BoxDecoration(borderRadius: BorderRadius.circular(22), border: Border.all(color: C.border)), child: Padding(padding: padding, child: child))));
}

class Skel extends StatelessWidget {
  const Skel({super.key, this.h = 80});
  final double h;
  @override Widget build(BuildContext c) => Container(height: h, decoration: BoxDecoration(color: const Color(0xFFF6ECF1), borderRadius: BorderRadius.circular(16)));
}

class EmptyView extends StatelessWidget {
  const EmptyView(this.title, {super.key, this.text, this.icon = Icons.auto_awesome, this.action});
  final String title; final String? text; final IconData icon; final Widget? action;
  @override Widget build(BuildContext c) => Padding(padding: const EdgeInsets.all(32), child: Column(mainAxisSize: MainAxisSize.min, children: [
    Icon(icon, size: 40, color: C.muted), const SizedBox(height: 12), Text(title, textAlign: TextAlign.center, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16)),
    if (text != null) ...[const SizedBox(height: 6), Text(text!, textAlign: TextAlign.center, style: const TextStyle(color: C.muted))],
    if (action != null) ...[const SizedBox(height: 16), action!],
  ]));
}

/// Loading / error(with retry) / data wrapper — the mobile twin of the web `useLoad` hook.
class Load<T> extends StatefulWidget {
  const Load({super.key, required this.fn, required this.builder, this.keys = const [], this.skeleton});
  final Future<T> Function() fn; final Widget Function(BuildContext, T, VoidCallback reload) builder; final List<Object?> keys; final Widget? skeleton;
  @override State<Load<T>> createState() => _LoadState<T>();
}
class _LoadState<T> extends State<Load<T>> {
  late Future<T> f;
  @override void initState() { super.initState(); f = widget.fn(); }
  @override void didUpdateWidget(Load<T> o) { super.didUpdateWidget(o); if (!_same(o.keys, widget.keys)) setState(() => f = widget.fn()); }
  bool _same(List a, List b) { if (a.length != b.length) return false; for (var i = 0; i < a.length; i++) { if (a[i] != b[i]) return false; } return true; }
  void reload() => setState(() => f = widget.fn());
  @override Widget build(BuildContext c) => FutureBuilder<T>(future: f, builder: (ctx, s) {
    if (s.connectionState != ConnectionState.done) return widget.skeleton ?? const Padding(padding: EdgeInsets.all(16), child: Column(children: [Skel(), SizedBox(height: 12), Skel(), SizedBox(height: 12), Skel()]));
    if (s.hasError) { final a = appRead(c); return EmptyView(a.t('error.title'), text: a.err(s.error!), icon: Icons.error_outline, action: SBtn(a.t('common.retry'), kind: 'secondary', onPressed: reload)); }
    return widget.builder(ctx, s.data as T, reload);
  });
}

class SectionHead extends StatelessWidget {
  const SectionHead(this.title, {super.key, this.onMore});
  final String title; final VoidCallback? onMore;
  @override Widget build(BuildContext c) => Padding(padding: const EdgeInsets.only(bottom: 12), child: Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
    Flexible(child: Text(title, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800), overflow: TextOverflow.ellipsis)),
    if (onMore != null) GestureDetector(onTap: onMore, child: Text(appRead(c).t('common.seeAll'), style: const TextStyle(color: C.primary, fontSize: 13))),
  ]));
}

class Alert extends StatelessWidget {
  const Alert(this.text, {super.key, this.tone = 'info'});
  final String text; final String tone;
  @override Widget build(BuildContext c) {
    final (bg, fg) = switch (tone) { 'error' => (C.errorBg, C.error), 'warning' => (C.warningBg, C.warning), 'success' => (C.successBg, C.success), _ => (C.lavSoft, const Color(0xFF4B3490)) };
    return Container(width: double.infinity, padding: const EdgeInsets.all(14), decoration: BoxDecoration(color: bg, borderRadius: BorderRadius.circular(16)), child: Text(text, style: TextStyle(color: fg, fontSize: 14)));
  }
}

class SuluLogo extends StatelessWidget {
  const SuluLogo({super.key, this.size = 28});
  final double size;
  @override Widget build(BuildContext c) => Row(mainAxisSize: MainAxisSize.min, children: [
    Container(width: size, height: size, decoration: BoxDecoration(gradient: C.grad, borderRadius: BorderRadius.circular(size * .28)), alignment: Alignment.center,
        child: Text('S', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w900, fontSize: size * .6, fontFamily: serif))),
    const SizedBox(width: 10), Text('SULU', style: TextStyle(fontFamily: serif, fontWeight: FontWeight.w800, letterSpacing: 4, fontSize: size * .8, color: C.roseDeep)),
  ]);
}

void goLogin(BuildContext c, String next) => c.push('/login?next=${Uri.encodeComponent(next)}');

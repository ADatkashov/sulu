import { useState, type FormEvent } from 'react'
import { Link, NavLink, Navigate, Outlet, Route, Routes, useNavigate } from 'react-router-dom'
import { Icon, Logo } from '../../components/Icon'
import { Button, Empty, ErrorState, Field, Input, Select, Skeleton, SkeletonList, useLoad, useToast } from '../../components/ui'
import { useI18n } from '../../i18n'
import { ApiError, get, post, put } from '../../lib/api'
import { useApp } from '../../lib/app'
import { fmtDateTime, money } from '../../lib/format'
import { LangSwitch } from '../business/BusinessApp'

function useErr() { const { err } = useI18n(); const toast = useToast(); return (e: unknown) => toast(err(e instanceof ApiError ? e.code : undefined)) }
const tone = (s: string) => (['APPROVED', 'ACTIVE', 'VISIBLE', 'SUCCEEDED', 'CONFIRMED', 'COMPLETED'].includes(s) ? 'success' : ['PENDING_VERIFICATION', 'PENDING', 'PARTIALLY_REFUNDED'].includes(s) ? 'warning' : ['REJECTED', 'BLOCKED', 'SUSPENDED', 'HIDDEN', 'FAILED', 'CANCELLED'].includes(s) ? 'error' : '')

function Shell() {
  const { t } = useI18n(); const { user, logout } = useApp(); const nav = useNavigate()
  const items = [['overview', 'home'], ['users', 'users'], ['businesses', 'shield'], ['categories', 'tag'], ['reviews', 'star'], ['bookings', 'calendar'], ['payments', 'chart'], ['promos', 'sparkles'], ['settings', 'gear'], ['audit', 'list']] as const
  return (
    <div className="cab">
      <aside className="cab-side">
        <div className="row between" style={{ width: '100%' }}><Logo size={30} /><span className="tag">Admin</span></div>
        <nav className="cab-nav" aria-label="admin">{items.map(([to, ic]) => <NavLink key={to} to={`/admin/${to}`} className={({ isActive }) => (isActive ? 'active' : '')}><Icon name={ic} size={20} />{t(`a.nav.${to}` as any)}</NavLink>)}</nav>
        <div className="col" style={{ marginTop: 'auto', width: '100%' }}><LangSwitch /><Button small variant="ghost" onClick={async () => { await logout(); nav('/login') }}><Icon name="logout" size={16} />{user?.email}</Button></div>
      </aside>
      <main className="cab-main"><Outlet /></main>
    </div>
  )
}

function Overview() {
  const { t, locale } = useI18n(); const q = useLoad(() => get<any>('/api/admin/overview'), [])
  if (q.loading) return <SkeletonList n={3} h={110} />
  if (q.error) return <ErrorState error={q.error} onRetry={q.reload} />
  const d = q.data
  return <>
    <h1>{t('a.nav.overview')}</h1>
    <div className="grid g4">
      <div className="card stat"><b>{d.pendingBusinesses}</b><span>{t('a.pendingVerification')}</span></div><div className="card stat"><b>{d.bookingsLast30Days}</b><span>Bookings · 30d</span></div>
      <div className="card stat"><b>{d.reportedReviews}</b><span>{t('a.reported')}</span></div>
      {d.gmvCompletedMinor.map((g: any) => <div key={g.currency} className="card stat"><b>{money(g.totalMinor, g.currency, locale)}</b><span>{t('a.gmv')} · commission {money(g.commissionMinor, g.currency, locale)}</span></div>)}</div>
    <div className="grid g3">
      <section className="card card-pad"><h2>Users</h2><table className="table mt2"><tbody>{d.users.map((u: any) => <tr key={u.role}><td>{u.role}</td><td>{u.n}</td></tr>)}</tbody></table></section>
      <section className="card card-pad"><h2>{t('a.nav.businesses')}</h2><table className="table mt2"><tbody>{d.businesses.map((u: any) => <tr key={u.status}><td>{u.status}</td><td>{u.n}</td></tr>)}</tbody></table></section>
      <section className="card card-pad"><h2>{t('a.nav.bookings')}</h2><table className="table mt2"><tbody>{d.bookings.map((u: any) => <tr key={u.status}><td>{u.status}</td><td>{u.n}</td></tr>)}</tbody></table></section></div></>
}

function Users() {
  const { t, locale } = useI18n(); const fail = useErr(); const [q, setQ] = useState(''); const [role, setRole] = useState('')
  const list = useLoad(() => get<any[]>(`/api/admin/users?q=${encodeURIComponent(q)}${role ? '&role=' + role : ''}`), [q, role])
  const setStatus = async (u: any, status: string) => { try { await post(`/api/admin/users/${u.id}/status`, { status, reason: 'admin action' }); list.reload() } catch (e) { fail(e) } }
  return <>
    <div className="row between wrap"><h1>{t('a.nav.users')}</h1><div className="row"><Input aria-label={t('common.search')} placeholder={t('common.search')} value={q} onChange={(e) => setQ(e.target.value)} style={{ width: 220 }} />
      <Select aria-label="role" value={role} onChange={(e) => setRole(e.target.value)} style={{ width: 180 }}><option value="">{t('a.all')}</option>{['CUSTOMER', 'PROFESSIONAL', 'BUSINESS_OWNER', 'PLATFORM_ADMIN'].map((r) => <option key={r}>{r}</option>)}</Select></div></div>
    {list.loading ? <SkeletonList /> : list.error ? <ErrorState error={list.error} onRetry={list.reload} /> : <div className="card tablewrap"><table className="table"><thead><tr><th>#</th><th>Email</th><th>{t('common.name')}</th><th>Role</th><th>{t('common.status')}</th><th>Created</th><th /></tr></thead>
      <tbody>{list.data!.map((u) => <tr key={u.id}><td>{u.id}</td><td>{u.email}</td><td>{u.fullName}</td><td>{u.role}</td><td><span className={`tag ${tone(u.status)}`}>{u.status}</span></td><td>{fmtDateTime(u.createdAt, locale)}</td>
        <td>{u.status === 'ACTIVE' && u.role !== 'PLATFORM_ADMIN' && <Button small variant="danger" onClick={() => setStatus(u, 'BLOCKED')}>{t('a.block')}</Button>}{u.status === 'BLOCKED' && <Button small variant="soft" onClick={() => setStatus(u, 'ACTIVE')}>{t('a.unblock')}</Button>}</td></tr>)}</tbody></table></div>}</>
}

function Businesses() {
  const { t } = useI18n(); const fail = useErr(); const [status, setStatus] = useState('PENDING_VERIFICATION')
  const list = useLoad(() => get<any[]>(`/api/admin/businesses?status=${status}`), [status])
  const set = async (b: any, s: string) => { try { await post(`/api/admin/businesses/${b.id}/status`, { status: s }); list.reload() } catch (e) { fail(e) } }
  return <>
    <div className="row between wrap"><h1>{t('a.nav.businesses')}</h1><Select aria-label="status" value={status} onChange={(e) => setStatus(e.target.value)} style={{ width: 240 }}><option value="">{t('a.all')}</option>{['PENDING_VERIFICATION', 'APPROVED', 'REJECTED', 'SUSPENDED'].map((r) => <option key={r}>{r}</option>)}</Select></div>
    {list.loading ? <SkeletonList /> : list.error ? <ErrorState error={list.error} onRetry={list.reload} /> : list.data!.length === 0 ? <Empty icon="shield" title={t('b.empty')} /> : <div className="card tablewrap"><table className="table"><thead><tr><th>#</th><th>{t('common.name')}</th><th>{t('common.city')}</th><th>Owner</th><th>{t('common.status')}</th><th /></tr></thead>
      <tbody>{list.data!.map((b) => <tr key={b.id}><td>{b.id}</td><td><b>{b.name}</b> {b.isDemo && <span className="tag demo">DEMO</span>}<div className="muted small">{b.address}</div></td><td>{b.city}</td><td>{b.ownerEmail}</td><td><span className={`tag ${tone(b.status)}`}>{b.status}</span></td>
        <td><div className="row gap2 wrap">{b.status !== 'APPROVED' && <Button small onClick={() => set(b, 'APPROVED')}>{t('a.approve')}</Button>}{b.status === 'PENDING_VERIFICATION' && <Button small variant="danger" onClick={() => set(b, 'REJECTED')}>{t('a.reject')}</Button>}{b.status === 'APPROVED' && <Button small variant="soft" onClick={() => set(b, 'SUSPENDED')}>{t('a.suspend')}</Button>}</div></td></tr>)}</tbody></table></div>}</>
}

function Categories() {
  const { t } = useI18n(); const fail = useErr(); const toast = useToast(); const list = useLoad(() => get<any[]>('/api/admin/categories'), [])
  const [f, setF] = useState({ slug: '', nameKk: '', nameRu: '', nameEn: '' })
  const add = async (e: FormEvent) => { e.preventDefault(); try { await post('/api/admin/categories', { ...f, sortOrder: 100, active: true }); setF({ slug: '', nameKk: '', nameRu: '', nameEn: '' }); toast(t('b.saved')); list.reload() } catch (x) { fail(x) } }
  const toggle = async (c: any) => { try { await put(`/api/admin/categories/${c.id}`, { slug: c.slug, nameKk: c.nameKk, nameRu: c.nameRu, nameEn: c.nameEn, nameTr: c.nameTr, icon: c.icon, sortOrder: c.sortOrder, active: !c.active }); list.reload() } catch (x) { fail(x) } }
  return <>
    <h1>{t('a.nav.categories')}</h1>
    <form className="card card-pad grid g4" onSubmit={add} style={{ alignItems: 'end' }}>
      {(['slug', 'nameKk', 'nameRu', 'nameEn'] as const).map((k) => <Field key={k} label={k} id={'c' + k}><Input id={'c' + k} required value={f[k]} onChange={(e) => setF({ ...f, [k]: e.target.value })} /></Field>)}
      <Button type="submit">{t('common.add')}</Button></form>
    {list.loading ? <SkeletonList /> : list.error ? <ErrorState error={list.error} onRetry={list.reload} /> : <div className="card tablewrap"><table className="table"><thead><tr><th>Slug</th><th>KK</th><th>RU</th><th>EN</th><th /></tr></thead><tbody>{list.data!.map((c) => <tr key={c.id} style={{ opacity: c.active ? 1 : .5 }}><td>{c.slug}</td><td>{c.nameKk}</td><td>{c.nameRu}</td><td>{c.nameEn}</td><td><Button small variant="ghost" onClick={() => toggle(c)}>{c.active ? t('a.hide') : t('a.show')}</Button></td></tr>)}</tbody></table></div>}</>
}

function Reviews() {
  const { t } = useI18n(); const fail = useErr(); const [filter, setFilter] = useState('reported'); const list = useLoad(() => get<any[]>(`/api/admin/reviews?filter=${filter}`), [filter])
  const set = async (r: any, s: string) => { try { await post(`/api/admin/reviews/${r.id}/status`, { status: s }); list.reload() } catch (e) { fail(e) } }
  return <>
    <div className="row between"><h1>{t('a.nav.reviews')}</h1><div className="tabs"><button className={filter === 'reported' ? 'on' : ''} onClick={() => setFilter('reported')}>{t('a.reported')}</button><button className={filter === 'all' ? 'on' : ''} onClick={() => setFilter('all')}>{t('a.all')}</button></div></div>
    {list.loading ? <SkeletonList /> : list.error ? <ErrorState error={list.error} onRetry={list.reload} /> : list.data!.length === 0 ? <Empty icon="star" title={t('b.empty')} /> : <div className="col">{list.data!.map((r) => <div key={r.id} className="card card-pad row">
      <div className="grow"><b>{'★'.repeat(r.rating)}</b> · {r.author} → {r.businessName} <span className={`tag ${tone(r.status)}`}>{r.status}</span>{r.reportedCount > 0 && <span className="tag error" style={{ marginLeft: 6 }}>⚑ {r.reportedCount}</span>}<p className="muted">{r.body}</p></div>
      {r.status === 'VISIBLE' ? <Button small variant="danger" onClick={() => set(r, 'HIDDEN')}>{t('a.hide')}</Button> : <Button small variant="soft" onClick={() => set(r, 'VISIBLE')}>{t('a.show')}</Button>}</div>)}</div>}</>
}

function Bookings() {
  const { t, locale } = useI18n(); const [ref, setRef] = useState(''); const [status, setStatus] = useState(''); const [open, setOpen] = useState<any>(null)
  const list = useLoad(() => get<any[]>(`/api/admin/bookings?status=${status}&reference=${encodeURIComponent(ref)}`), [status, ref])
  return <>
    <div className="row between wrap"><h1>{t('a.nav.bookings')}</h1><div className="row"><Input aria-label="reference" placeholder="SL-XXXXXXXX" value={ref} onChange={(e) => setRef(e.target.value)} style={{ width: 170 }} /><Select aria-label="status" value={status} onChange={(e) => setStatus(e.target.value)} style={{ width: 170 }}><option value="">{t('a.all')}</option>{['PENDING', 'CONFIRMED', 'COMPLETED', 'CANCELLED', 'REJECTED', 'NO_SHOW'].map((r) => <option key={r}>{r}</option>)}</Select></div></div>
    {list.loading ? <SkeletonList /> : list.error ? <ErrorState error={list.error} onRetry={list.reload} /> : <div className="card tablewrap"><table className="table"><thead><tr><th>Ref</th><th>Service</th><th>Business</th><th>Customer</th><th>When</th><th>{t('common.status')}</th><th>Pay</th></tr></thead>
      <tbody>{list.data!.map((b) => <tr key={b.id} onClick={async () => setOpen(await get(`/api/admin/bookings/${b.id}`))} style={{ cursor: 'pointer' }}><td><b>{b.reference}</b></td><td>{b.serviceName}</td><td>{b.businessName}</td><td>{b.customerEmail}</td><td>{fmtDateTime(b.startAt, locale)}</td><td><span className={`tag ${tone(b.status)}`}>{b.status}</span></td><td>{b.paymentStatus}</td></tr>)}</tbody></table></div>}
    {open && <div className="card card-pad col"><div className="row between"><h2>#{open.reference}</h2><Button small variant="ghost" onClick={() => setOpen(null)}>×</Button></div>
      <div className="grid g2"><div><h3>History</h3>{open.history.map((h: any, i: number) => <div key={i} className="small">{fmtDateTime(h.createdAt, locale)} · {h.fromStatus ?? '∅'} → {h.toStatus} {h.note && `(${h.note})`}</div>)}</div>
        <div><h3>Payments</h3>{open.payments.length === 0 ? <span className="muted small">—</span> : open.payments.map((p: any) => <div key={p.id} className="small">{p.provider} · {p.method} · {money(p.amountMinor, p.currency, locale)} · {p.status} {p.simulated && <span className="tag demo">{t('a.simulated')}</span>}</div>)}</div></div></div>}</>
}

function Payments() {
  const { t, locale } = useI18n(); const fail = useErr(); const toast = useToast(); const list = useLoad(() => get<any[]>('/api/admin/payments'), [])
  const refund = async (p: any) => { try { await post(`/api/admin/payments/${p.id}/refund`, {}); toast(t('b.saved')); list.reload() } catch (e) { fail(e) } }
  return <>
    <h1>{t('a.nav.payments')}</h1>
    {list.loading ? <SkeletonList /> : list.error ? <ErrorState error={list.error} onRetry={list.reload} /> : list.data!.length === 0 ? <Empty icon="chart" title={t('b.empty')} /> : <div className="card tablewrap"><table className="table"><thead><tr><th>#</th><th>Booking</th><th>Provider</th><th>Amount</th><th>Refunded</th><th>{t('common.status')}</th><th /></tr></thead>
      <tbody>{list.data!.map((p) => <tr key={p.id}><td>{p.id}</td><td>{p.reference}</td><td>{p.provider} {p.simulated && <span className="tag demo">{t('a.simulated')}</span>}</td><td>{money(p.amountMinor, p.currency, locale)}</td><td>{money(p.refundedMinor, p.currency, locale)}</td><td><span className={`tag ${tone(p.status)}`}>{p.status}</span></td>
        <td>{(p.status === 'SUCCEEDED' || p.status === 'PARTIALLY_REFUNDED') && <Button small variant="danger" onClick={() => refund(p)}>{t('a.refund')}</Button>}</td></tr>)}</tbody></table></div>}</>
}

function Promos() {
  const { t, locale } = useI18n(); const fail = useErr(); const list = useLoad(() => get<any[]>('/api/admin/promotions'), []); const [f, setF] = useState({ code: '', percentOff: '10' })
  const add = async (e: FormEvent) => { e.preventDefault(); try { await post('/api/admin/promotions', { code: f.code, percentOff: Number(f.percentOff) }); setF({ code: '', percentOff: '10' }); list.reload() } catch (x) { fail(x) } }
  return <>
    <h1>{t('a.nav.promos')}</h1>
    <form className="card card-pad grid g3" onSubmit={add} style={{ alignItems: 'end' }}><Field label={t('b.promo.code')} id="apc"><Input id="apc" required value={f.code} onChange={(e) => setF({ ...f, code: e.target.value.toUpperCase() })} /></Field><Field label={t('b.promo.percent')} id="app"><Input id="app" type="number" min={1} max={100} value={f.percentOff} onChange={(e) => setF({ ...f, percentOff: e.target.value })} /></Field><Button type="submit">{t('b.promo.new')}</Button></form>
    {list.loading ? <SkeletonList n={2} /> : list.error ? <ErrorState error={list.error} onRetry={list.reload} /> : <div className="card tablewrap"><table className="table"><thead><tr><th>{t('b.promo.code')}</th><th>Scope</th><th>%</th><th>{t('b.promo.used')}</th><th /></tr></thead><tbody>{list.data!.map((p) => <tr key={p.id}><td><b>{p.code}</b></td><td>{p.businessId ? `business #${p.businessId}` : 'platform'}</td><td>{p.percentOff ?? money(p.amountOffMinor, p.currency, locale)}</td><td>{p.redemptions}{p.maxRedemptions ? `/${p.maxRedemptions}` : ''}</td>
      <td><Button small variant="ghost" onClick={async () => { await post(`/api/admin/promotions/${p.id}/active?value=${!p.active}`); list.reload() }}>{p.active ? '✓ on' : 'off'}</Button></td></tr>)}</tbody></table></div>}</>
}

function Settings() {
  const { t } = useI18n(); const fail = useErr(); const toast = useToast(); const s = useLoad(() => get<any>('/api/admin/settings'), []); const plans = useLoad(() => get<any[]>('/api/admin/plans'), []); const [v, setV] = useState<any>({})
  const save = async (key: string) => { try { await put(`/api/admin/settings/${key}`, { value: String(v[key] ?? s.data[key]) }); toast(t('b.saved')); s.reload() } catch (e) { fail(e) } }
  if (s.loading) return <Skeleton h={200} />
  if (s.error) return <ErrorState error={s.error} onRetry={s.reload} />
  return <>
    <h1>{t('a.nav.settings')}</h1>
    <div className="card card-pad col">{Object.keys(s.data).map((k) => <div key={k} className="row"><Field label={k === 'commission_bps' ? t('a.commission') : k} id={'s' + k}><Input id={'s' + k} value={v[k] ?? s.data[k]} onChange={(e) => setV({ ...v, [k]: e.target.value })} /></Field><Button small variant="secondary" onClick={() => save(k)} style={{ alignSelf: 'flex-end' }}>{t('common.save')}</Button></div>)}</div>
    <section className="card card-pad"><h2>{t('a.plans')}</h2><div className="alert info small mt2">{t('b.settings.billing')}</div>{plans.data && <table className="table mt2"><thead><tr><th>Code</th><th>{t('a.planPrice')}</th><th>Features</th></tr></thead><tbody>{plans.data.map((p) => <tr key={p.id}><td><b>{p.code}</b></td><td>{p.priceMinor}</td><td>{p.features}</td></tr>)}</tbody></table>}</section></>
}

function Audit() {
  const { t, locale } = useI18n(); const list = useLoad(() => get<any[]>('/api/admin/audit?size=100'), [])
  return <><h1>{t('a.nav.audit')}</h1>{list.loading ? <SkeletonList /> : list.error ? <ErrorState error={list.error} onRetry={list.reload} /> : <div className="card tablewrap"><table className="table"><thead><tr><th>When</th><th>Actor</th><th>Action</th><th>Target</th><th>Detail</th></tr></thead><tbody>{list.data!.map((a) => <tr key={a.id}><td>{fmtDateTime(a.createdAt, locale)}</td><td>{a.actorEmail ?? 'system'}</td><td><b>{a.action}</b></td><td>{a.targetType} {a.targetId}</td><td className="muted">{a.detail}</td></tr>)}</tbody></table></div>}</>
}

export function AdminApp() {
  const { t } = useI18n(); const { user } = useApp()
  if (!user) return <Navigate to="/login?next=/admin" replace />
  if (user.role !== 'PLATFORM_ADMIN') return <div className="cab-main"><Empty icon="shield" title={t('err.FORBIDDEN')}><Link to="/" className="btn btn-primary">SULU</Link></Empty></div>
  return (
    <Routes><Route element={<Shell />}>
      <Route index element={<Navigate to="overview" replace />} /><Route path="overview" element={<Overview />} /><Route path="users" element={<Users />} /><Route path="businesses" element={<Businesses />} /><Route path="categories" element={<Categories />} />
      <Route path="reviews" element={<Reviews />} /><Route path="bookings" element={<Bookings />} /><Route path="payments" element={<Payments />} /><Route path="promos" element={<Promos />} /><Route path="settings" element={<Settings />} /><Route path="audit" element={<Audit />} />
    </Route></Routes>
  )
}

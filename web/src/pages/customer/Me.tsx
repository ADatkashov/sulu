import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Icon } from '../../components/Icon'
import { ProCard, SalonCard, ServiceCard } from '../../components/cards'
import { Avatar, Button, Confirm, Empty, ErrorState, Field, Input, Select, SkeletonList, TopBar, useLoad, useToast } from '../../components/ui'
import { LOCALES, useI18n, type Locale } from '../../i18n'
import { ApiError, api, del, get, post } from '../../lib/api'
import { useApp } from '../../lib/app'
import { fmtDate, fmtTime } from '../../lib/format'

export function Favorites() {
  const { t } = useI18n(); const { user } = useApp()
  const q = useLoad(() => (user ? get<any>('/api/favorites') : Promise.resolve(null)), [user?.id])
  if (!user) return <><TopBar title={t('fav.title')} /><div className="page"><Empty icon="heart" title={t('fav.title')} text={t('fav.loginRequired')}><Link to="/login?next=/favorites" className="btn btn-primary">{t('auth.login')}</Link></Empty></div></>
  const d = q.data
  const none = d && d.businesses.length + d.professionals.length + d.services.length === 0
  return (
    <>
      <TopBar title={t('fav.title')} />
      <div className="page">
        {q.loading ? <SkeletonList /> : q.error ? <ErrorState error={q.error} onRetry={q.reload} /> : none ? <Empty icon="heart" title={t('fav.title')} text={t('fav.empty')}><Link to="/explore" className="btn btn-primary">{t('bookings.findSomething')}</Link></Empty> : <>
          {d.businesses.length > 0 && <section><h2>{t('fav.salons')}</h2><div className="col mt3">{d.businesses.map((b: any) => <SalonCard key={b.id} b={b} row />)}</div></section>}
          {d.professionals.length > 0 && <section><h2>{t('fav.masters')}</h2><div className="hscroll mt3">{d.professionals.map((p: any) => <ProCard key={p.id} p={p} />)}</div></section>}
          {d.services.length > 0 && <section><h2>{t('fav.services')}</h2><div className="col mt3">{d.services.map((s: any) => <ServiceCard key={s.id} s={s} />)}</div></section>}
        </>}
      </div>
    </>
  )
}

export function Notifications() {
  const { t, locale } = useI18n(); const { user, refreshUnread } = useApp(); const nav = useNavigate()
  const q = useLoad(() => (user ? get<any>('/api/notifications') : Promise.resolve({ items: [], unread: 0 })), [user?.id])
  useEffect(() => { refreshUnread() }, [q.data, refreshUnread])
  const open = async (n: any) => { if (!n.read) { try { await post(`/api/notifications/${n.id}/read`) } catch { /* ignore */ } } refreshUnread(); if (n.link && n.link.startsWith('/')) nav(n.link.startsWith('/business') && user?.role !== 'BUSINESS_OWNER' ? '/bookings' : n.link) }
  return (
    <>
      <TopBar title={t('notif.title')} back="/" right={user && <button className="btn-ghost small" onClick={async () => { await post('/api/notifications/read-all'); q.reload(); refreshUnread() }}>{t('notif.markAll')}</button>} />
      <div className="page">
        {!user ? <Empty icon="bell" title={t('notif.title')} text={t('profile.guest')}><Link to="/login" className="btn btn-primary">{t('auth.login')}</Link></Empty> : q.loading ? <SkeletonList /> : q.error ? <ErrorState error={q.error} onRetry={q.reload} /> : q.data.items.length === 0 ? <Empty icon="bell" title={t('notif.empty')} /> :
          <div className="col">{q.data.items.map((n: any) => (
            <button key={n.id} className="card card-pad row" style={{ textAlign: 'left', borderColor: n.read ? undefined : 'var(--pink)' }} onClick={() => open(n)}>
              <span className="cat-ic" style={{ width: 44, height: 44, borderRadius: 14 }}><Icon name="bell" size={20} /></span>
              <div className="grow"><div className="bold">{n.title}</div>{n.body && <div className="muted small">{n.body}</div>}<div className="muted tiny">{fmtDate(n.createdAt, locale, undefined, { day: 'numeric', month: 'short' })} {fmtTime(n.createdAt, locale)}</div></div>
            </button>))}</div>}
      </div>
    </>
  )
}

export function Profile() {
  const { t, err } = useI18n(); const { user, logout, refreshUser } = useApp(); const toast = useToast(); const nav = useNavigate()
  const [f, setF] = useState({ fullName: user?.fullName ?? '', phone: user?.phone ?? '' }); const [cur, setCur] = useState(''); const [np, setNp] = useState(''); const [dlg, setDlg] = useState(false); const [msg, setMsg] = useState('')
  useEffect(() => setF({ fullName: user?.fullName ?? '', phone: user?.phone ?? '' }), [user?.id]) // eslint-disable-line
  if (!user) return (
    <><TopBar title={t('profile.title')} /><div className="page">
      <Empty icon="user" title={t('profile.title')} text={t('profile.guest')}><Link to="/login" className="btn btn-primary">{t('auth.login')}</Link><Link to="/register" className="btn btn-secondary">{t('auth.register')}</Link></Empty>
      <LanguageCity /></div></>
  )
  const patch = async (body: any, ok = true) => { try { await api('/api/me', { method: 'PATCH', body }); await refreshUser(); if (ok) toast(t('profile.saved')) } catch (e) { toast(err(e instanceof ApiError ? e.code : undefined)) } }
  const exportData = async () => { const d = await get('/api/me/export'); const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([JSON.stringify(d, null, 2)], { type: 'application/json' })); a.download = 'sulu-my-data.json'; a.click() }
  const changePw = async () => { try { await post('/api/me/password', { currentPassword: cur, newPassword: np }); setCur(''); setNp(''); setMsg(t('profile.saved')) } catch (e) { setMsg(err(e instanceof ApiError ? e.code : undefined)) } }
  const doDelete = async () => { try { await del('/api/me'); await logout(); nav('/') } catch (e) { toast(err(e instanceof ApiError ? e.code : undefined)); setDlg(false) } }
  const home = user.role === 'BUSINESS_OWNER' ? '/business' : user.role === 'PLATFORM_ADMIN' ? '/admin' : user.role === 'PROFESSIONAL' ? '/pro' : null
  return (
    <>
      <TopBar title={t('profile.title')} />
      <div className="page">
        <div className="row"><Avatar name={user.fullName} size={64} /><div><h2>{user.fullName}</h2><div className="muted small">{user.email}</div></div></div>
        {home && <Link to={home} className="btn btn-soft">{t('profile.forBusiness')}</Link>}
        <section className="col"><Field label={t('auth.fullName')} id="fn"><Input id="fn" value={f.fullName} onChange={(e) => setF({ ...f, fullName: e.target.value })} /></Field>
          <Field label={t('auth.phone')} id="ph"><Input id="ph" type="tel" value={f.phone} onChange={(e) => setF({ ...f, phone: e.target.value })} /></Field>
          <Button variant="secondary" onClick={() => patch({ fullName: f.fullName, phone: f.phone || undefined })}>{t('common.save')}</Button></section>
        <LanguageCity onLang={(l) => patch({ language: l }, false)} onCity={(c) => patch({ city: c }, false)} />
        <section className="col"><h2>{t('profile.notifications')}</h2>
          <label className="row"><input type="checkbox" style={{ width: 20, height: 20 }} checked={user.notifyEmail} onChange={(e) => patch({ notifyEmail: e.target.checked })} /> {t('profile.notifyEmail')}</label>
          <label className="row"><input type="checkbox" style={{ width: 20, height: 20 }} checked={user.notifyPush} onChange={(e) => patch({ notifyPush: e.target.checked })} /> {t('profile.notifyPush')}</label></section>
        <section className="col"><h2>{t('profile.changePassword')}</h2>
          <Field label={t('profile.currentPassword')} id="cp"><Input id="cp" type="password" autoComplete="current-password" value={cur} onChange={(e) => setCur(e.target.value)} /></Field>
          <Field label={t('auth.newPassword')} id="npw"><Input id="npw" type="password" autoComplete="new-password" value={np} onChange={(e) => setNp(e.target.value)} /></Field>
          {msg && <div className="alert info">{msg}</div>}<Button variant="secondary" onClick={changePw} disabled={!cur || !np}>{t('common.save')}</Button></section>
        <section className="col"><h2>{t('profile.privacy')}</h2>
          <Button variant="soft" onClick={exportData}><Icon name="download" size={18} />{t('profile.export')}</Button>
          <Button variant="danger" onClick={() => setDlg(true)}><Icon name="trash" size={18} />{t('profile.delete')}</Button></section>
        <Button variant="soft" onClick={async () => { await logout(); nav('/') }}><Icon name="logout" size={18} />{t('auth.logout')}</Button>
      </div>
      {dlg && <Confirm title={t('profile.delete')} text={t('profile.deleteConfirm')} confirmLabel={t('common.delete')} danger onConfirm={doDelete} onClose={() => setDlg(false)} />}
    </>
  )
}

function LanguageCity({ onLang, onCity }: { onLang?: (l: Locale) => void; onCity?: (c: string) => void }) {
  const { t, locale, setLocale } = useI18n(); const { config, city, setCity } = useApp()
  const cities = Array.from(new Set([config?.defaultCity ?? city, ...(config?.cities ?? [])].filter(Boolean)))
  return (
    <section className="col">
      <Field label={t('common.language')} id="lang"><Select id="lang" value={locale} onChange={(e) => { setLocale(e.target.value as Locale); onLang?.(e.target.value as Locale) }}>{LOCALES.map((l) => <option key={l.code} value={l.code}>{l.label}</option>)}</Select></Field>
      <Field label={t('common.city')} id="cty"><Select id="cty" value={city} onChange={(e) => { setCity(e.target.value); onCity?.(e.target.value) }}>{cities.map((c) => <option key={c}>{c}</option>)}</Select></Field>
    </section>
  )
}

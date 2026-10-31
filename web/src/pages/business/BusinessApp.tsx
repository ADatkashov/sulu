import { createContext, useContext, useState, type FormEvent } from 'react'
import { Link, NavLink, Navigate, Outlet, Route, Routes, useNavigate } from 'react-router-dom'
import { Icon, Logo } from '../../components/Icon'
import { Button, Empty, ErrorState, Field, Input, Select, Skeleton, Textarea, useLoad } from '../../components/ui'
import { LOCALES, useI18n, type Locale } from '../../i18n'
import { ApiError, get, post } from '../../lib/api'
import { useApp } from '../../lib/app'
import { Overview, CalendarPage, ServicesPage, StaffPage, CustomersPage, AnalyticsPage, ReviewsPage, PromosPage, SettingsPage } from './Sections'

type Biz = { id: number; name: string; city: string; status: string; isDemo: boolean }
const Ctx = createContext<{ bid: number; biz: any; reload: () => void }>(null as any)
export const useBiz = () => useContext(Ctx)

export function LangSwitch() {
  const { locale, setLocale } = useI18n()
  return <select aria-label="language" className="select" style={{ minHeight: 38 }} value={locale} onChange={(e) => setLocale(e.target.value as Locale)}>{LOCALES.map((l) => <option key={l.code} value={l.code}>{l.label}</option>)}</select>
}

function Shell({ bizList, bid, setBid }: { bizList: Biz[]; bid: number; setBid: (n: number) => void }) {
  const { t } = useI18n(); const { user, logout } = useApp(); const nav = useNavigate()
  const nav2 = [['overview', 'home', 'b.nav.overview'], ['calendar', 'calendar', 'b.nav.calendar'], ['services', 'tag', 'b.nav.services'], ['staff', 'users', 'b.nav.staff'], ['customers', 'user', 'b.nav.customers'],
    ['analytics', 'chart', 'b.nav.analytics'], ['reviews', 'star', 'b.nav.reviews'], ['promos', 'sparkles', 'b.nav.promos'], ['settings', 'gear', 'b.nav.settings']] as const
  return (
    <div className="cab">
      <aside className="cab-side">
        <div className="row between" style={{ width: '100%' }}><Logo size={30} /><span className="tag">{t('b.title').replace('SULU ', '')}</span></div>
        {bizList.length > 1 && <Select aria-label="business" value={bid} onChange={(e) => setBid(Number(e.target.value))}>{bizList.map((b) => <option key={b.id} value={b.id}>{b.name}</option>)}</Select>}
        <nav className="cab-nav" aria-label="business">{nav2.map(([to, ic, k]) => <NavLink key={to} to={`/business/${to}`} className={({ isActive }) => (isActive ? 'active' : '')}><Icon name={ic} size={20} />{t(k)}</NavLink>)}</nav>
        <div className="col" style={{ marginTop: 'auto', width: '100%' }}>
          <LangSwitch /><Link to="/" className="btn btn-soft btn-sm">SULU</Link>
          <Button small variant="ghost" onClick={async () => { await logout(); nav('/login') }}><Icon name="logout" size={16} />{user?.email}</Button>
        </div>
      </aside>
      <main className="cab-main"><Outlet /></main>
    </div>
  )
}

function CreateBusiness({ onDone }: { onDone: () => void }) {
  const { t, err } = useI18n(); const { city, config } = useApp()
  const [f, setF] = useState({ name: '', kind: 'SALON', description: '', city: city || config?.defaultCity || 'Алматы', address: '', phone: '' }); const [error, setError] = useState(''); const [busy, setBusy] = useState(false)
  const submit = async (e: FormEvent) => { e.preventDefault(); setBusy(true); setError(''); try { await post('/api/business', f); onDone() } catch (x) { setError(err(x instanceof ApiError ? x.code : undefined)) } finally { setBusy(false) } }
  const set = (k: keyof typeof f) => (e: any) => setF({ ...f, [k]: e.target.value })
  return (
    <div className="cab-main" style={{ margin: '0 auto', maxWidth: 640 }}>
      <Logo size={36} /><h1>{t('b.create.title')}</h1><p className="muted">{t('b.create.text')}</p>
      <form className="col card card-pad" onSubmit={submit}>
        <Field label={t('common.name')} id="bn"><Input id="bn" required value={f.name} onChange={set('name')} /></Field>
        <Field label="Type" id="bk"><Select id="bk" value={f.kind} onChange={set('kind')}>{['SALON', 'STUDIO', 'BARBERSHOP', 'INDEPENDENT'].map((k) => <option key={k}>{k}</option>)}</Select></Field>
        <div className="grid g2"><Field label={t('common.city')} id="bc"><Input id="bc" required value={f.city} onChange={set('city')} /></Field><Field label={t('auth.phone')} id="bp"><Input id="bp" type="tel" value={f.phone} onChange={set('phone')} /></Field></div>
        <Field label={t('biz.address')} id="ba"><Input id="ba" required value={f.address} onChange={set('address')} /></Field>
        <Field label={t('b.field.description')} id="bd"><Textarea id="bd" value={f.description} onChange={set('description')} /></Field>
        {error && <div className="alert error" role="alert">{error}</div>}
        <Button type="submit" loading={busy}>{t('b.create.submit')}</Button>
      </form>
    </div>
  )
}

export function BusinessApp() {
  const { t } = useI18n(); const { user } = useApp()
  const list = useLoad(() => (user?.role === 'BUSINESS_OWNER' ? get<Biz[]>('/api/business') : Promise.resolve([] as Biz[])), [user?.id])
  const [sel, setSel] = useState<number>(() => Number(localStorage.getItem('sulu.biz') ?? 0))
  const bid = list.data?.find((b) => b.id === sel)?.id ?? list.data?.[0]?.id
  const biz = useLoad(() => (bid ? get<any>(`/api/biz/${bid}`) : Promise.resolve(null)), [bid])
  if (!user) return <Navigate to="/login?next=/business" replace />
  if (user.role !== 'BUSINESS_OWNER') return <div className="cab-main"><Empty icon="shield" title={t('err.FORBIDDEN')}><Link to="/" className="btn btn-primary">SULU</Link></Empty></div>
  if (list.loading) return <div className="cab-main"><Skeleton h={300} /></div>
  if (list.error) return <div className="cab-main"><ErrorState error={list.error} onRetry={list.reload} /></div>
  if (!bid) return <CreateBusiness onDone={list.reload} />
  return (
    <Ctx.Provider value={{ bid, biz: biz.data ?? {}, reload: biz.reload }}>
      <Routes>
        <Route element={<Shell bizList={list.data!} bid={bid} setBid={(n) => { setSel(n); localStorage.setItem('sulu.biz', String(n)) }} />}>
          <Route index element={<Navigate to="overview" replace />} />
          <Route path="overview" element={<Overview />} /><Route path="calendar" element={<CalendarPage />} /><Route path="services" element={<ServicesPage />} />
          <Route path="staff" element={<StaffPage />} /><Route path="customers" element={<CustomersPage />} /><Route path="analytics" element={<AnalyticsPage />} />
          <Route path="reviews" element={<ReviewsPage />} /><Route path="promos" element={<PromosPage />} /><Route path="settings" element={<SettingsPage />} />
        </Route>
      </Routes>
    </Ctx.Provider>
  )
}

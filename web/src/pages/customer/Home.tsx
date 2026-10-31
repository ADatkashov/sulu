import { useEffect, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { Icon, Logo } from '../../components/Icon'
import { ErrorState, Select, Skeleton, useLoad, Empty } from '../../components/ui'
import { ProCard, SalonCard, ServiceCard, iconFor } from '../../components/cards'
import { catName, useI18n } from '../../i18n'
import { get, qs } from '../../lib/api'
import { useApp } from '../../lib/app'
import { ONBOARDED } from './Onboarding'

const RECENT = 'sulu.recent'
export function pushRecent(kind: 'master' | 'salon', id: number) {
  try {
    const cur: string[] = JSON.parse(localStorage.getItem(RECENT) ?? '[]')
    const next = [`${kind}:${id}`, ...cur.filter((x) => x !== `${kind}:${id}`)].slice(0, 8)
    localStorage.setItem(RECENT, JSON.stringify(next))
  } catch { /* ignore */ }
}

export function Home() {
  const { t, locale } = useI18n(); const { city, setCity, config, user, unread } = useApp(); const nav = useNavigate()
  const [q, setQ] = useState('')
  const onboarded = (() => { try { return !!localStorage.getItem(ONBOARDED) } catch { return true } })()
  const cats = useLoad(() => get<any[]>('/api/categories'), [])
  const salons = useLoad(() => get<any>('/api/businesses' + qs({ city, size: 8, sort: 'rating' })), [city])
  const pros = useLoad(() => get<any>('/api/professionals' + qs({ city, size: 8 })), [city])
  const svcs = useLoad(() => get<any>('/api/services' + qs({ city, size: 5, sort: 'rating' })), [city])
  const favs = useLoad(() => (user ? get<any>('/api/favorites') : Promise.resolve(null)), [user?.id])
  const [recent, setRecent] = useState<any[]>([])
  useEffect(() => {
    let alive = true
    try {
      const ids: string[] = JSON.parse(localStorage.getItem(RECENT) ?? '[]')
      Promise.all(ids.slice(0, 6).map((k) => { const [kind, id] = k.split(':'); return get<any>(`/api/${kind === 'salon' ? 'businesses' : 'professionals'}/${id}`).then((d) => ({ kind, d })).catch(() => null) }))
        .then((r) => alive && setRecent(r.filter(Boolean) as any[]))
    } catch { /* ignore */ }
    return () => { alive = false }
  }, [])
  if (!onboarded) return <Navigate to="/onboarding" replace />
  const cities = Array.from(new Set([config?.defaultCity ?? city, ...(config?.cities ?? [])].filter(Boolean)))
  const go = (e: React.FormEvent) => { e.preventDefault(); nav('/explore' + qs({ q })) }
  return (
    <div className="page" style={{ paddingTop: 0 }}>
      <div className="hero col" style={{ margin: '0 calc(-1 * var(--s4))', gap: 14 }}>
        <div className="row between"><Logo />
          <div className="row gap2">
            <Link to="/notifications" className="iconbtn" aria-label={t('notif.title')}><Icon name="bell" size={22} />{unread > 0 && <span className="badge">{unread}</span>}</Link>
          </div></div>
        <div className="row gap2"><Icon name="pin" size={18} /><label className="sr-only" htmlFor="city">{t('common.city')}</label>
          <Select id="city" value={city} onChange={(e) => setCity(e.target.value)} style={{ minHeight: 40, width: 'auto', borderRadius: 999 }}>{cities.map((c) => <option key={c}>{c}</option>)}</Select></div>
        {user && <div className="muted small">{t('home.greeting')}, {user.fullName.split(' ')[0]}</div>}
        <form className="search" onSubmit={go} role="search"><Icon name="search" size={20} /><input aria-label={t('home.search')} placeholder={t('home.search')} value={q} onChange={(e) => setQ(e.target.value)} /></form>
      </div>

      <div className="promo"><h2>{t('home.promoTitle')}</h2><p>{t('home.promoText')}</p></div>

      <section>
        <div className="section-head"><h2>{t('home.categories')}</h2></div>
        {cats.loading ? <Skeleton h={90} /> : cats.error ? <ErrorState error={cats.error} onRetry={cats.reload} /> : (
          <div className="hscroll">{cats.data!.map((c) => (
            <Link key={c.id} to={`/explore?category=${c.slug}`} className="cat-tile"><span className="cat-ic"><Icon name={iconFor(c.slug)} size={28} /></span><span>{catName(c, locale)}</span></Link>
          ))}</div>
        )}
      </section>

      <section>
        <div className="section-head"><h2>{t('home.popular')}</h2><Link to="/explore?tab=salons" className="small" style={{ color: 'var(--primary)' }}>{t('common.seeAll')}</Link></div>
        {salons.loading ? <Skeleton h={220} /> : salons.error ? <ErrorState error={salons.error} onRetry={salons.reload} /> : salons.data!.items.length === 0 ? <Empty title={t('home.noBusinesses')} /> :
          <div className="hscroll">{salons.data!.items.map((b: any) => <SalonCard key={b.id} b={b} />)}</div>}
      </section>

      <section>
        <div className="section-head"><h2>{t('home.top')}</h2><Link to="/explore?tab=masters" className="small" style={{ color: 'var(--primary)' }}>{t('common.seeAll')}</Link></div>
        {pros.loading ? <Skeleton h={160} /> : pros.error ? <ErrorState error={pros.error} onRetry={pros.reload} /> : <div className="hscroll">{pros.data!.items.map((p: any) => <ProCard key={p.id} p={p} />)}</div>}
      </section>

      <section>
        <div className="section-head"><h2>{t('home.recommended')}</h2><Link to="/explore" className="small" style={{ color: 'var(--primary)' }}>{t('common.seeAll')}</Link></div>
        {svcs.loading ? <Skeleton h={200} /> : svcs.error ? <ErrorState error={svcs.error} onRetry={svcs.reload} /> : <div className="col">{svcs.data!.items.map((s: any) => <ServiceCard key={s.id} s={s} />)}</div>}
      </section>

      {recent.length > 0 && (
        <section>
          <div className="section-head"><h2>{t('home.recent')}</h2></div>
          <div className="hscroll">{recent.map(({ kind, d }) => kind === 'salon' ? <SalonCard key={'s' + d.id} b={d} /> : <ProCard key={'m' + d.id} p={d} />)}</div>
        </section>
      )}

      {favs.data && (favs.data.businesses.length + favs.data.professionals.length > 0) && (
        <section>
          <div className="section-head"><h2>{t('home.favorites')}</h2><Link to="/favorites" className="small" style={{ color: 'var(--primary)' }}>{t('common.seeAll')}</Link></div>
          <div className="hscroll">{favs.data.businesses.map((b: any) => <SalonCard key={'fb' + b.id} b={b} />)}{favs.data.professionals.map((p: any) => <ProCard key={'fp' + p.id} p={p} />)}</div>
        </section>
      )}
    </div>
  )
}

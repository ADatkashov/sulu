import { useEffect, useState } from 'react'
import { useSearchParams, Link } from 'react-router-dom'
import { Icon } from '../../components/Icon'
import { Button, Empty, ErrorState, Field, Input, Select, Sheet, SkeletonList, TopBar, useLoad } from '../../components/ui'
import { ProCard, SalonCard, ServiceCard, iconFor } from '../../components/cards'
import { catName, useI18n } from '../../i18n'
import { get, qs } from '../../lib/api'
import { useApp } from '../../lib/app'

type Tab = 'services' | 'salons' | 'masters'

export function Explore() {
  const { t, locale } = useI18n(); const { city } = useApp()
  const [sp, setSp] = useSearchParams()
  const q = sp.get('q') ?? ''; const category = sp.get('category') ?? ''; const tab = (sp.get('tab') as Tab) || 'services'
  const sort = sp.get('sort') ?? 'rating'; const maxPrice = sp.get('maxPrice') ?? ''; const minRating = sp.get('minRating') ?? ''; const atHome = sp.get('atHome') ?? ''; const gender = sp.get('gender') ?? ''
  const [draft, setDraft] = useState(q); const [open, setOpen] = useState(false)
  const [geo, setGeo] = useState<{ lat: number; lng: number } | null>(null); const [geoMsg, setGeoMsg] = useState('')
  useEffect(() => setDraft(q), [q])
  const set = (patch: Record<string, string>) => { const n = new URLSearchParams(sp); Object.entries(patch).forEach(([k, v]) => (v ? n.set(k, v) : n.delete(k))); setSp(n, { replace: true }) }

  // debounce typing → URL
  useEffect(() => { if (draft === q) return; const id = setTimeout(() => set({ q: draft }), 300); return () => clearTimeout(id) }, [draft]) // eslint-disable-line

  const cats = useLoad(() => get<any[]>('/api/categories'), [])
  const maxMinor = maxPrice ? Number(maxPrice) * 100 : undefined
  const data = useLoad(() => {
    if (tab === 'services') return get<any>('/api/services' + qs({ q, city, category, maxPriceMinor: maxMinor, atHome, gender, sort: sort === 'distance' ? 'rating' : sort, size: 30 }))
    if (tab === 'salons') return get<any>('/api/businesses' + qs({ q, city, category, maxPriceMinor: maxMinor, minRating, sort, lat: geo?.lat, lng: geo?.lng, size: 30 }))
    return get<any>('/api/professionals' + qs({ q, city, category, gender, size: 30 }))
  }, [tab, q, city, category, maxMinor, minRating, atHome, gender, sort, geo?.lat, geo?.lng])

  const useLocation = () => {
    if (!navigator.geolocation) { setGeoMsg(t('filter.locationDenied')); return }
    navigator.geolocation.getCurrentPosition((p) => { setGeo({ lat: p.coords.latitude, lng: p.coords.longitude }); set({ sort: 'distance' }); setGeoMsg('') }, () => { setGeoMsg(t('filter.locationDenied')); set({ sort: 'rating' }) }, { timeout: 8000 })
  }
  const active = [maxPrice, minRating, atHome, gender].filter(Boolean).length
  const sorts = tab === 'services' ? ['rating', 'price_asc', 'price_desc', 'duration'] : tab === 'salons' ? ['rating', 'price_asc', 'price_desc', 'distance'] : ['rating']

  return (
    <>
      <TopBar title={t('explore.title')} />
      <div className="page">
        <div className="row"><form className="search grow" role="search" onSubmit={(e) => { e.preventDefault(); set({ q: draft }) }}><Icon name="search" size={20} /><input aria-label={t('common.search')} placeholder={t('home.search')} value={draft} onChange={(e) => setDraft(e.target.value)} />
          {draft && <button type="button" aria-label="clear" onClick={() => { setDraft(''); set({ q: '' }) }}><Icon name="x" size={18} /></button>}</form>
          <button className="iconbtn" aria-label={t('explore.filters')} onClick={() => setOpen(true)} style={{ position: 'relative' }}><Icon name="filter" size={20} />{active > 0 && <span className="badge">{active}</span>}</button></div>
        <div className="tabs" role="tablist">{(['services', 'salons', 'masters'] as Tab[]).map((x) => <button key={x} role="tab" aria-selected={tab === x} className={tab === x ? 'on' : ''} onClick={() => set({ tab: x === 'services' ? '' : x })}>{t(`explore.${x}` as any)}</button>)}</div>
        <div className="chips">
          <button className={`chip${!category ? ' active' : ''}`} onClick={() => set({ category: '' })}>{t('common.all')}</button>
          {cats.data?.map((c) => <button key={c.id} className={`chip${category === c.slug ? ' active' : ''}`} onClick={() => set({ category: c.slug })}><Icon name={iconFor(c.slug)} size={16} />{catName(c, locale)}</button>)}
        </div>
        {geoMsg && <div className="alert warning small">{geoMsg}</div>}
        {data.loading ? <SkeletonList n={4} /> : data.error ? <ErrorState error={data.error} onRetry={data.reload} /> : data.data.items.length === 0 ? (
          <Empty icon="search" title={t('empty.title')} text={t('empty.text')}>
            <div className="row wrap gap2" style={{ justifyContent: 'center' }}>{cats.data?.slice(0, 6).map((c) => <Link key={c.id} to={`/explore?category=${c.slug}`} className="chip">{catName(c, locale)}</Link>)}</div>
            {(q || category || active > 0) && <Button variant="soft" small onClick={() => setSp({}, { replace: true })}>{t('filter.reset')}</Button>}
          </Empty>
        ) : (
          <div className="col" aria-live="polite">
            {tab === 'services' && data.data.items.map((s: any) => <ServiceCard key={s.id} s={s} />)}
            {tab === 'salons' && data.data.items.map((b: any) => <SalonCard key={b.id} b={b} row />)}
            {tab === 'masters' && <div className="grid g2" style={{ justifyItems: 'center' }}>{data.data.items.map((p: any) => <ProCard key={p.id} p={p} />)}</div>}
            <p className="muted small center">{data.data.total}</p>
          </div>
        )}
      </div>
      {open && (
        <Sheet title={t('explore.filters')} onClose={() => setOpen(false)}>
          <Field label={t('explore.sort')} id="sort"><Select id="sort" value={sorts.includes(sort) ? sort : 'rating'} onChange={(e) => set({ sort: e.target.value === 'rating' ? '' : e.target.value })}>{sorts.map((s) => <option key={s} value={s}>{t(`sort.${s}` as any)}</option>)}</Select></Field>
          {tab === 'salons' && <Button variant="soft" onClick={useLocation}><Icon name="pin" size={18} /> {t('filter.useLocation')}</Button>}
          {tab !== 'masters' && <Field label={`${t('filter.maxPrice')} (${t('common.optional')})`} id="mp"><Input id="mp" type="number" inputMode="numeric" min={0} value={maxPrice} onChange={(e) => set({ maxPrice: e.target.value })} /></Field>}
          {tab === 'salons' && <Field label={t('filter.minRating')} id="mr"><Select id="mr" value={minRating} onChange={(e) => set({ minRating: e.target.value })}><option value="">—</option>{[3, 4, 4.5].map((r) => <option key={r} value={r}>{r}+</option>)}</Select></Field>}
          {tab !== 'salons' && <Field label={t('filter.gender')} id="g"><Select id="g" value={gender} onChange={(e) => set({ gender: e.target.value })}><option value="">{t('gender.any')}</option><option value="FEMALE">{t('gender.FEMALE')}</option><option value="MALE">{t('gender.MALE')}</option></Select></Field>}
          {tab === 'services' && <label className="row"><input type="checkbox" style={{ width: 20, height: 20 }} checked={!!atHome} onChange={(e) => set({ atHome: e.target.checked ? '1' : '' })} /> {t('filter.atHome')}</label>}
          <div className="row"><Button variant="soft" block onClick={() => { setSp({ ...(q ? { q } : {}), ...(category ? { category } : {}), ...(tab !== 'services' ? { tab } : {}) }, { replace: true }); setOpen(false) }}>{t('filter.reset')}</Button><Button block onClick={() => setOpen(false)}>{t('common.apply')}</Button></div>
        </Sheet>
      )}
    </>
  )
}

import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Icon } from '../../components/Icon'
import { Avatar, Button, DemoTag, Empty, ErrorState, Heart, Photo, Price, Skeleton, SkeletonList, Stars, TopBar, useLoad, useToast } from '../../components/ui'
import { ProCard, ReviewCard, ServiceCard, iconFor, useFavToggle } from '../../components/cards'
import { catName, useI18n } from '../../i18n'
import { get, post } from '../../lib/api'
import { fmtDate, fmtTime } from '../../lib/format'
import { pushRecent } from './Home'

function NextSlot({ serviceId, professionalId }: { serviceId: number; professionalId?: number }) {
  const { t, locale } = useI18n()
  const [n, setN] = useState<any>(undefined)
  useEffect(() => { get<any>(`/api/availability/next?serviceId=${serviceId}${professionalId ? '&professionalId=' + professionalId : ''}`).then(setN).catch(() => setN(null)) }, [serviceId, professionalId])
  if (n === undefined) return <Skeleton h={20} w={160} />
  if (!n?.start) return <span className="muted small">{t('svc.noNext')}</span>
  return <span className="small"><Icon name="clock" size={14} /> <b>{fmtDate(n.start, locale, undefined, { day: 'numeric', month: 'short' })}, {fmtTime(n.start, locale)}</b></span>
}

function Reviews({ kind, id }: { kind: 'businessId' | 'professionalId'; id: number }) {
  const { t } = useI18n(); const toast = useToast(); const nav = useNavigate()
  const r = useLoad(() => get<any>(`/api/reviews?${kind}=${id}&size=10`), [kind, id])
  const report = async (rid: number) => { try { await post(`/api/reviews/${rid}/report`, {}); toast(t('review.reported')) } catch (e: any) { if (e.status === 401) nav('/login?next=' + encodeURIComponent(location.pathname)); else toast(t('err.generic')) } }
  if (r.loading) return <SkeletonList n={2} h={100} />
  if (r.error) return <ErrorState error={r.error} onRetry={r.reload} />
  const d = r.data
  if (!d.count) return <p className="muted">{t('common.noReviews')}</p>
  const max = Math.max(...Object.values<number>(d.distribution))
  return (
    <div className="col">
      <div className="card card-pad row gap4"><div className="center"><div style={{ fontSize: 40, fontWeight: 700, fontFamily: 'var(--serif)' }}>{Number(d.average).toFixed(1)}</div><div className="muted tiny">{d.count} {t('common.reviews')}</div></div>
        <div className="col grow" style={{ gap: 4 }}>{[5, 4, 3, 2, 1].map((s) => <div key={s} className="row" style={{ gap: 8 }}><span className="tiny" style={{ width: 8 }}>{s}</span><div className="dist-bar"><i style={{ width: `${max ? (d.distribution[s] / max) * 100 : 0}%` }} /></div><span className="tiny muted" style={{ width: 22, textAlign: 'right' }}>{d.distribution[s]}</span></div>)}</div></div>
      {d.items.map((x: any) => <ReviewCard key={x.id} r={x} onReport={() => report(x.id)} />)}
    </div>
  )
}

export function BusinessPage() {
  const { id } = useParams(); const { t } = useI18n()
  const b = useLoad(() => get<any>(`/api/businesses/${id}`), [id])
  const fav = useFavToggle('business', Number(id), !!b.data?.favorite)
  useEffect(() => { if (id) pushRecent('salon', Number(id)) }, [id])
  if (b.loading) return <><TopBar title="…" back="/" /><div className="page"><Skeleton h={180} /><SkeletonList /></div></>
  if (b.error) return <><TopBar title="SULU" back="/" /><ErrorState error={b.error} onRetry={b.reload} /></>
  const d = b.data
  return (
    <>
      <TopBar title={d.name} back right={<Heart on={fav.on} onToggle={fav.toggle} label="favorite" />} />
      <div className="page">
        <Photo imageKey={d.imageKey} icon="lotus" style={{ width: '100%', height: 190, borderRadius: 22 }} />
        <div className="col" style={{ gap: 6 }}>
          <div className="row gap2 wrap"><h1>{d.name}</h1><DemoTag show={d.isDemo} /></div>
          <Stars value={Number(d.ratingAvg)} count={d.ratingCount} />
          <div className="muted"><Icon name="pin" size={15} /> {d.address}, {d.city}</div>
          <div className="row gap2 wrap mt2">
            {d.phone && <a className="btn btn-soft btn-sm" href={`tel:${d.phone}`}><Icon name="phone" size={16} />{t('biz.call')}</a>}
            {d.lat != null && <a className="btn btn-soft btn-sm" href={`https://www.openstreetmap.org/?mlat=${d.lat}&mlon=${d.lng}#map=17/${d.lat}/${d.lng}`} target="_blank" rel="noreferrer"><Icon name="map" size={16} />{t('biz.directions')}</a>}
          </div>
        </div>
        {d.description && <section><h2 className="mt2">{t('biz.about')}</h2><p className="mt2 muted">{d.description}</p></section>}
        <section><h2>{t('biz.services')}</h2><div className="col mt3">{d.services.length ? d.services.map((s: any) => <ServiceCard key={s.id} s={{ ...s, businessName: d.name, businessRating: d.ratingAvg, businessRatingCount: d.ratingCount }} />) : <Empty title={t('empty.generic')} />}</div></section>
        <section><h2>{t('biz.team')}</h2><div className="hscroll mt3">{d.professionals.map((p: any) => <ProCard key={p.id} p={p} />)}</div></section>
        <section><h2>{t('biz.hours')}</h2>
          <div className="card card-pad mt3 col" style={{ gap: 6 }}>{[1, 2, 3, 4, 5, 6, 7].map((w) => { const h = d.hours.find((x: any) => x.weekday === w); return <div key={w} className="row between small"><span>{t(`wd.${w}` as any)}</span><span className={h ? 'bold' : 'muted'}>{h ? `${h.openTime}–${h.closeTime}` : t('biz.closed')}</span></div> })}</div></section>
        <section><h2 className="mb2">{t('biz.reviews')}</h2><div className="mt3"><Reviews kind="businessId" id={Number(id)} /></div></section>
      </div>
    </>
  )
}

export function ProPage() {
  const { id } = useParams(); const { t } = useI18n(); const nav = useNavigate()
  const p = useLoad(() => get<any>(`/api/professionals/${id}`), [id])
  const fav = useFavToggle('professional', Number(id), !!p.data?.favorite)
  const [sel, setSel] = useState<number | null>(null)
  useEffect(() => { if (id) pushRecent('master', Number(id)) }, [id])
  if (p.loading) return <><TopBar title="…" back /><div className="page"><Skeleton h={120} /><SkeletonList /></div></>
  if (p.error) return <><TopBar title="SULU" back /><ErrorState error={p.error} onRetry={p.reload} /></>
  const d = p.data
  const chosen = sel ?? d.services[0]?.id
  return (
    <>
      <TopBar title={d.fullName} back right={<Heart on={fav.on} onToggle={fav.toggle} label="favorite" />} />
      <div className="page">
        <div className="hero" style={{ margin: '-16px -16px 0' }}>
          <div className="pro-hero"><Avatar name={d.fullName} imageKey={d.avatarKey} size={92} />
            <div className="col" style={{ gap: 4 }}><h1>{d.fullName}</h1><div className="muted">{d.title}</div><Stars value={Number(d.ratingAvg)} count={d.ratingCount} />
              <Link to={`/salon/${d.businessId}`} className="small" style={{ color: 'var(--primary)' }}>{d.businessName} <DemoTag show={d.isDemo} /></Link></div></div>
        </div>
        <div className="row gap2 wrap">{d.specialties.split(',').filter(Boolean).map((s: string) => <span key={s} className="tag">{s.trim()}</span>)}<span className="tag success">{t('pro.experience')}: {d.experienceYears} {t('common.years')}</span></div>
        {d.bio && <section><h2>{t('pro.bio')}</h2><p className="muted mt2">{d.bio}</p></section>}
        <section><h2>{t('pro.portfolio')}</h2>
          {d.portfolio.length ? <div className="grid g3 mt3" style={{ gap: 8 }}>{d.portfolio.map((x: any) => <Photo key={x.id} imageKey={x.imageKey} alt={x.caption ?? ''} style={{ aspectRatio: '1', borderRadius: 14 }} />)}</div> : <p className="muted mt2 small">{t('pro.noPortfolio')}</p>}</section>
        <section><h2>{t('pro.services')}</h2>
          <div className="col mt3">{d.services.map((s: any) => (
            <button key={s.id} className={`opt${chosen === s.id ? ' on' : ''}`} onClick={() => setSel(s.id)} aria-pressed={chosen === s.id}><span className="radio" /><div className="grow"><div className="bold">{s.name}</div><div className="muted small">{s.durationMin} {t('common.min')}</div></div><Price minor={s.priceMinor} currency={s.currency} from={s.priceFrom} /></button>))}</div>
          {chosen && <div className="row between mt3"><span className="muted small">{t('pro.next')}</span><NextSlot serviceId={chosen} professionalId={d.id} /></div>}
        </section>
        <section><h2>{t('biz.reviews')}</h2><div className="mt3"><Reviews kind="professionalId" id={Number(id)} /></div></section>
        <div style={{ position: 'sticky', bottom: 88 }}><Button block disabled={!chosen} onClick={() => nav(`/book/${chosen}?pro=${d.id}`)}>{t('pro.book')}</Button></div>
      </div>
    </>
  )
}

export function ServicePage() {
  const { id } = useParams(); const { t, locale } = useI18n(); const nav = useNavigate()
  const s = useLoad(() => get<any>(`/api/services/${id}`), [id])
  const fav = useFavToggle('service', Number(id), !!s.data?.favorite)
  if (s.loading) return <><TopBar title="…" back /><div className="page"><Skeleton h={180} /><SkeletonList /></div></>
  if (s.error) return <><TopBar title="SULU" back /><ErrorState error={s.error} onRetry={s.reload} /></>
  const d = s.data
  return (
    <>
      <TopBar title={d.name} back right={<Heart on={fav.on} onToggle={fav.toggle} label="favorite" />} />
      <div className="page">
        <Photo imageKey={d.imageKey} icon={iconFor(d.categorySlug)} style={{ width: '100%', height: 180, borderRadius: 22 }} />
        <div className="col" style={{ gap: 6 }}>
          <span className="tag" style={{ alignSelf: 'flex-start' }}>{catName(d, locale)}</span>
          <h1>{d.name}</h1>
          <div className="row between"><Price minor={d.priceMinor} currency={d.currency} from={d.priceFrom} /><span className="muted"><Icon name="clock" size={15} /> {d.durationMin} {t('common.min')}</span></div>
          <Link to={`/salon/${d.businessId}`} className="row small" style={{ color: 'var(--primary)' }}><Icon name="pin" size={15} /> {d.businessName} · {d.address} <DemoTag show={d.isDemo} /></Link>
          <Stars value={Number(d.businessRating)} count={d.businessRatingCount} />
          {d.atHome && <span className="tag success" style={{ alignSelf: 'flex-start' }}>{t('svc.atHome')}</span>}
        </div>
        {d.description && <section><h2>{t('svc.about')}</h2><p className="muted mt2">{d.description}</p></section>}
        <div className="card card-pad row between"><span className="muted">{t('svc.next')}</span><NextSlot serviceId={d.id} /></div>
        <section><h2>{t('svc.professionals')}</h2><div className="hscroll mt3">{d.professionals.map((p: any) => <ProCard key={p.id} p={p} />)}</div></section>
        <section><h2>{t('svc.policy')}</h2><p className="muted mt2">{t('svc.policyText', { h: d.cancellationHours })}</p></section>
        <section><h2>{t('biz.reviews')}</h2><div className="mt3"><Reviews kind="businessId" id={d.businessId} /></div></section>
        <div style={{ position: 'sticky', bottom: 88 }}><Button block onClick={() => nav(`/book/${d.id}`)}>{t('svc.book')}</Button></div>
      </div>
    </>
  )
}

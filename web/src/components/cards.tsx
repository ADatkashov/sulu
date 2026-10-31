import { Link } from 'react-router-dom'
import { useNavigate } from 'react-router-dom'
import { useState } from 'react'
import { Avatar, DemoTag, Heart, Photo, Price, Stars, useToast } from './ui'
import { Icon } from './Icon'
import { useI18n } from '../i18n'
import { useFavorites } from '../lib/app'
import { fmtDate, fmtTime, km } from '../lib/format'

const catIcon: Record<string, string> = { haircut: 'scissors', coloring: 'palette', manicure: 'hand', pedicure: 'foot', makeup: 'brush', brows: 'eye', lashes: 'eye', skincare: 'droplet', cosmetology: 'flask', barber: 'razor', massage: 'leaf', spa: 'lotus' }
export const iconFor = (slug?: string) => catIcon[slug ?? ''] ?? 'sparkles'

export function useFavToggle(kind: 'business' | 'professional' | 'service', id: number, initial: boolean) {
  const nav = useNavigate(); const { t } = useI18n(); const toast = useToast()
  const [on, setOn] = useState(initial)
  const toggle = useFavorites(() => { toast(t('fav.loginRequired')); nav('/login?next=' + encodeURIComponent(location.pathname)) })
  return { on, toggle: async () => { try { setOn(await toggle(kind, id, on)) } catch { toast(t('err.generic')) } } }
}

export function ServiceCard({ s }: { s: any }) {
  const { t } = useI18n()
  return (
    <Link to={`/service/${s.id}`} className="card card-link svc-card">
      <Photo imageKey={s.imageKey} icon={iconFor(s.categorySlug)} alt="" />
      <div className="grow col" style={{ gap: 2 }}>
        <div className="bold">{s.name}</div>
        <div className="muted small ellipsis">{s.businessName} <DemoTag show={s.isDemo} /></div>
        <div className="row small muted" style={{ gap: 10 }}><span><Icon name="clock" size={13} /> {s.durationMin} {t('common.min')}</span><Stars value={Number(s.businessRating)} count={s.businessRatingCount} /></div>
      </div>
      <Price minor={s.priceMinor} currency={s.currency} from={s.priceFrom} />
    </Link>
  )
}

export function SalonCard({ b, row }: { b: any; row?: boolean }) {
  const { t } = useI18n()
  const fav = useFavToggle('business', b.id, !!b.favorite)
  const body = (
    <div className="body grow">
      <div className="row between" style={{ alignItems: 'flex-start' }}><span className="bold ellipsis">{b.name}</span>{!row && <span />}</div>
      <Stars value={Number(b.ratingAvg)} count={b.ratingCount} />
      <div className="muted small ellipsis"><Icon name="pin" size={13} /> {b.address}{b.distanceKm != null && ` · ${km(b.distanceKm)}`}</div>
      {b.minPrice != null && <div className="small">{t('biz.from')} <Price minor={b.minPrice} currency={b.currency} /></div>}
      <DemoTag show={b.isDemo} />
    </div>
  )
  return (
    <Link to={`/salon/${b.id}`} className={`card card-link ${row ? 'salon-row' : 'salon-card'}`}>
      <div style={{ position: 'relative' }}>
        <Photo imageKey={b.imageKey} icon="lotus" className={row ? '' : ''} style={row ? { width: 96, height: 96, borderRadius: 16 } : { width: '100%', height: 130 }} />
        <div style={{ position: 'absolute', top: 6, right: 6 }}><Heart on={fav.on} onToggle={fav.toggle} label="favorite" /></div>
      </div>
      {body}
    </Link>
  )
}

export function ProCard({ p }: { p: any }) {
  const fav = useFavToggle('professional', p.id, !!p.favorite)
  return (
    <Link to={`/master/${p.id}`} className="card card-link pro-card" style={{ position: 'relative' }}>
      <div style={{ position: 'absolute', top: 4, right: 4 }}><Heart on={fav.on} onToggle={fav.toggle} label="favorite" /></div>
      <Avatar name={p.fullName} imageKey={p.avatarKey} size={72} />
      <div className="bold ellipsis" style={{ maxWidth: '100%' }}>{p.fullName}</div>
      <div className="muted tiny ellipsis" style={{ maxWidth: '100%' }}>{p.title}</div>
      <Stars value={Number(p.ratingAvg)} count={p.ratingCount} />
    </Link>
  )
}

export function BookingCard({ b }: { b: any }) {
  const { t, locale } = useI18n()
  const tone = b.status === 'CONFIRMED' ? 'success' : b.status === 'PENDING' ? 'warning' : b.status === 'COMPLETED' ? '' : 'error'
  return (
    <Link to={`/bookings/${b.id}`} className="card card-link card-pad col" style={{ gap: 8 }}>
      <div className="row between"><span className="bold">{b.serviceName}</span><span className={`tag ${tone}`}>{t(`status.${b.status}` as any)}</span></div>
      <div className="muted small">{b.businessName} · {b.professionalName}</div>
      <div className="row between small"><span><Icon name="calendar" size={14} /> {fmtDate(b.startAt, locale, b.timezone)}, {fmtTime(b.startAt, locale, b.timezone)}</span><Price minor={b.totalMinor} currency={b.currency} /></div>
    </Link>
  )
}

export function ReviewCard({ r, onReport }: { r: any; onReport?: () => void }) {
  const { t, locale } = useI18n()
  return (
    <div className="card card-pad col" style={{ gap: 8 }}>
      <div className="row between"><div className="row"><Avatar name={r.author} size={36} /><div><div className="bold small">{r.author}</div><div className="muted tiny">{r.serviceName} · {fmtDate(r.createdAt, locale, undefined, { day: 'numeric', month: 'short', year: 'numeric' })}</div></div></div><Stars value={r.rating} /></div>
      {r.body && <p>{r.body}</p>}
      {r.reply && <div className="alert info small"><b>{t('review.reply')}:</b> {r.reply}</div>}
      {onReport && <button className="btn-ghost small" style={{ alignSelf: 'flex-start' }} onClick={onReport}>{t('review.report')}</button>}
    </div>
  )
}

import { useEffect, useMemo, useRef, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { Icon } from '../../components/Icon'
import { Avatar, Button, ErrorState, Field, Input, Price, Skeleton, Textarea, TopBar, useLoad, useToast } from '../../components/ui'
import { useI18n } from '../../i18n'
import { ApiError, get, newKey, post } from '../../lib/api'
import { useApp } from '../../lib/app'
import { fmtDate, fmtTime, money, ymdInZone, ymdToDate } from '../../lib/format'

type Slot = { start: string; end: string; professionalId: number }

export function Book() {
  const { serviceId } = useParams(); const [sp] = useSearchParams(); const nav = useNavigate()
  const { t, locale, err } = useI18n(); const { user, config } = useApp(); const toast = useToast()
  const svc = useLoad(() => get<any>(`/api/services/${serviceId}`), [serviceId])
  const [step, setStep] = useState(sp.get('pro') ? 1 : 0)
  const [proId, setProId] = useState<number | 'any'>(sp.get('pro') ? Number(sp.get('pro')) : 'any')
  const tz: string = svc.data?.timezone ?? 'Asia/Almaty'
  const days = useMemo(() => Array.from({ length: 21 }, (_, i) => ymdInZone(i, tz)), [tz])
  const [date, setDate] = useState<string>('')
  useEffect(() => { if (!date && days.length) setDate(days[0]) }, [days, date])
  const [slots, setSlots] = useState<Slot[] | null>(null); const [slotsErr, setSlotsErr] = useState<unknown>(null)
  const [slot, setSlot] = useState<Slot | null>(null)
  const [alts, setAlts] = useState<Slot[]>([])
  const [note, setNote] = useState(''); const [promo, setPromo] = useState(''); const [method, setMethod] = useState<'SALON' | 'ONLINE'>('SALON')
  const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [nonce, setNonce] = useState(0)
  const idem = useRef(newKey())
  const need = !user

  useEffect(() => {
    if (!svc.data || !date) return
    let alive = true
    setSlots(null); setSlotsErr(null); setSlot(null)
    get<any>(`/api/availability?serviceId=${serviceId}${proId === 'any' ? '' : '&professionalId=' + proId}&date=${date}`)
      .then((r) => alive && setSlots(r.slots)).catch((e) => alive && setSlotsErr(e))
    return () => { alive = false }
  }, [svc.data, date, proId, serviceId, nonce])

  if (svc.loading) return <><TopBar title={t('book.title')} back /><div className="page"><Skeleton h={120} /><Skeleton h={200} /></div></>
  if (svc.error) return <><TopBar title={t('book.title')} back /><ErrorState error={svc.error} onRetry={svc.reload} /></>
  const s = svc.data
  const pro = proId === 'any' ? null : s.professionals.find((p: any) => p.id === proId)
  const assigned = slot && (s.professionals.find((p: any) => p.id === slot.professionalId))
  const dayLabel = (ymd: string) => ({ wd: fmtDate(ymdToDate(ymd), locale, 'UTC', { weekday: 'short' }), d: Number(ymd.slice(8)) })

  const confirm = async () => {
    if (!slot) return
    if (!user) { nav(`/login?next=${encodeURIComponent(location.pathname + location.search)}`); return }
    setBusy(true); setError(''); setAlts([])
    try {
      const b = await post<any>('/api/bookings', { serviceId: s.id, professionalId: proId === 'any' ? undefined : proId, startAt: slot.start, note: note || undefined, promoCode: promo || undefined }, { 'Idempotency-Key': idem.current })
      let payment = false
      if (method === 'ONLINE') {
        try { await post(`/api/bookings/${b.id}/payments`, { method: 'CARD' }, { 'Idempotency-Key': 'pay-' + idem.current }); payment = true } catch (e) { toast(err(e instanceof ApiError ? e.code : undefined)) }
      }
      nav(`/booked/${b.id}${payment ? '?pay=1' : ''}`, { replace: true })
    } catch (e) {
      if (e instanceof ApiError) {
        if (e.code === 'SLOT_UNAVAILABLE') { setAlts(e.details?.alternatives ?? []); setError(t('book.conflict')); idem.current = newKey(); setSlot(null); setStep(1) }
        else setError(err(e.code))
      } else setError(err())
    } finally { setBusy(false) }
  }
  const stepTitles = [t('book.step.master'), t('book.step.time'), t('book.step.confirm')]
  return (
    <>
      <TopBar title={t('book.title')} back={step === 0 ? true : undefined} right={step > 0 ? <button className="btn-ghost" onClick={() => setStep(step - 1)}>{t('common.back')}</button> : undefined} />
      <div className="page nopad-bottom">
        <div className="stepper" aria-label={stepTitles[step]}>{[0, 1, 2].map((i) => <i key={i} className={i <= step ? 'on' : ''} />)}</div>
        <div className="card card-pad col" style={{ gap: 4 }}><div className="bold">{s.name}</div><div className="muted small">{s.businessName} · {s.durationMin} {t('common.min')} · <Price minor={s.priceMinor} currency={s.currency} from={s.priceFrom} /></div></div>

        {step === 0 && (
          <section className="col">
            <h2>{t('book.step.master')}</h2>
            <button className={`opt${proId === 'any' ? ' on' : ''}`} onClick={() => setProId('any')} aria-pressed={proId === 'any'}><span className="radio" /><span className="avatar"><Icon name="users" size={22} /></span><div className="grow"><div className="bold">{t('book.anyMaster')}</div><div className="muted small">{t('book.anyMasterHint')}</div></div></button>
            {s.professionals.map((p: any) => <button key={p.id} className={`opt${proId === p.id ? ' on' : ''}`} onClick={() => setProId(p.id)} aria-pressed={proId === p.id}><span className="radio" /><Avatar name={p.fullName} imageKey={p.avatarKey} /><div className="grow"><div className="bold">{p.fullName}</div><div className="muted small">{p.title}</div></div><span className="stars small">★ {Number(p.ratingAvg).toFixed(1)}</span></button>)}
            <Button block onClick={() => setStep(1)}>{t('common.continue')}</Button>
          </section>
        )}

        {step === 1 && (
          <section className="col">
            {error && <div className="alert warning" role="alert">{error}</div>}
            {alts.length > 0 && <div className="slots">{alts.slice(0, 8).map((a) => <button key={a.start} className="slot" onClick={() => { setDate(ymdInZone(0, tz, new Date(a.start))); setSlot({ ...a, end: a.start }); setError(''); setAlts([]) }}>{fmtTime(a.start, locale, tz)}</button>)}</div>}
            <h2>{t('book.chooseDate')}</h2>
            <div className="daystrip" role="listbox" aria-label={t('book.chooseDate')}>{days.map((d) => { const l = dayLabel(d); return <button key={d} role="option" aria-selected={date === d} className={`day${date === d ? ' on' : ''}`} onClick={() => setDate(d)}><small>{l.wd}</small><b>{l.d}</b></button> })}</div>
            <h2>{t('book.chooseTime')}</h2>
            {slotsErr ? <ErrorState error={slotsErr} onRetry={() => setNonce((n) => n + 1)} /> : slots === null ? <Skeleton h={120} /> : slots.length === 0 ? <div className="alert info">{t('book.noSlots')}</div> : (
              <div className="slots" role="listbox" aria-label={t('book.chooseTime')}>{slots.map((x) => <button key={x.start} role="option" aria-selected={slot?.start === x.start} className={`slot${slot?.start === x.start ? ' on' : ''}`} onClick={() => setSlot(x)}>{fmtTime(x.start, locale, tz)}</button>)}</div>
            )}
            <p className="tiny muted">{t('book.timezone', { tz })}</p>
            <Button block disabled={!slot} onClick={() => setStep(2)}>{t('common.continue')}</Button>
          </section>
        )}

        {step === 2 && slot && (
          <section className="col">
            <h2>{t('book.summary')}</h2>
            <dl className="card card-pad kv">
              <dt>{t('book.master')}</dt><dd>{assigned?.fullName ?? pro?.fullName ?? '—'}</dd>
              <dt>{t('book.when')}</dt><dd>{fmtDate(slot.start, locale, tz)}, {fmtTime(slot.start, locale, tz)}</dd>
              <dt>{t('book.where')}</dt><dd>{s.businessName}, {s.address}</dd>
            </dl>
            <Field label={`${t('book.notes')} (${t('common.optional')})`} id="note"><Textarea id="note" maxLength={500} value={note} onChange={(e) => setNote(e.target.value)} /></Field>
            <Field label={`${t('book.promo')} (${t('common.optional')})`} id="promo"><Input id="promo" value={promo} onChange={(e) => setPromo(e.target.value.toUpperCase())} autoCapitalize="characters" /></Field>
            <h2>{t('book.payment')}</h2>
            <button className={`opt${method === 'SALON' ? ' on' : ''}`} onClick={() => setMethod('SALON')} aria-pressed={method === 'SALON'}><span className="radio" /><span className="grow bold">{t('book.payAtSalon')}</span></button>
            {config?.simulatedPayments && <button className={`opt${method === 'ONLINE' ? ' on' : ''}`} onClick={() => setMethod('ONLINE')} aria-pressed={method === 'ONLINE'}><span className="radio" /><span className="grow bold">{t('book.payOnline')}</span><span className="tag demo">DEV</span></button>}
            <div className="row between card card-pad"><span className="bold">{t('book.total')}</span><b style={{ fontSize: 20 }}>{money(s.priceMinor, s.currency, locale)}</b></div>
            {promo && <p className="tiny muted">{t('book.promoNote')}</p>}
            {error && <div className="alert error" role="alert">{error}</div>}
            {need && <div className="alert info">{t('book.loginRequired')}</div>}
            <Button block loading={busy} onClick={confirm}>{need ? t('auth.login') : t('book.confirm')}</Button>
            {need && <Link to={`/register?next=${encodeURIComponent(location.pathname + location.search)}`} className="center small" style={{ color: 'var(--primary)' }}>{t('auth.register')}</Link>}
          </section>
        )}
      </div>
    </>
  )
}

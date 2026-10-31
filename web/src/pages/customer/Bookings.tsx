import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { Icon } from '../../components/Icon'
import { BookingCard } from '../../components/cards'
import { Button, Confirm, DemoTag, Empty, ErrorState, Field, Price, RatingInput, Sheet, Skeleton, SkeletonList, Textarea, TopBar, useLoad, useToast } from '../../components/ui'
import { useI18n } from '../../i18n'
import { ApiError, get, post } from '../../lib/api'
import { useApp } from '../../lib/app'
import { fmtDate, fmtTime, money, ymdInZone, ymdToDate } from '../../lib/format'

export function Bookings() {
  const { t } = useI18n(); const { user } = useApp(); const [sp, setSp] = useSearchParams()
  const scope = sp.get('scope') ?? 'upcoming'
  const list = useLoad(() => (user ? get<any[]>(`/api/bookings?scope=${scope}&size=50`) : Promise.resolve([])), [scope, user?.id])
  if (!user) return <><TopBar title={t('bookings.title')} /><div className="page"><Empty icon="calendar" title={t('bookings.title')} text={t('profile.guest')}><Link to="/login?next=/bookings" className="btn btn-primary">{t('auth.login')}</Link></Empty></div></>
  return (
    <>
      <TopBar title={t('bookings.title')} />
      <div className="page">
        <div className="tabs" role="tablist">{(['upcoming', 'past', 'cancelled'] as const).map((s) => <button key={s} role="tab" aria-selected={scope === s} className={scope === s ? 'on' : ''} onClick={() => setSp({ scope: s }, { replace: true })}>{t(`bookings.${s}` as any)}</button>)}</div>
        {list.loading ? <SkeletonList /> : list.error ? <ErrorState error={list.error} onRetry={list.reload} /> : list.data!.length === 0 ? (
          <Empty icon="calendar" title={t(scope === 'upcoming' ? 'bookings.emptyUpcoming' : scope === 'past' ? 'bookings.emptyPast' : 'bookings.emptyCancelled')}><Link to="/explore" className="btn btn-primary">{t('bookings.findSomething')}</Link></Empty>
        ) : <div className="col">{list.data!.map((b) => <BookingCard key={b.id} b={b} />)}</div>}
      </div>
    </>
  )
}

export function BookingDetail() {
  const { id } = useParams(); const { t, locale, err } = useI18n(); const toast = useToast(); const nav = useNavigate()
  const q = useLoad(() => get<any>(`/api/bookings/${id}`), [id])
  const [dlg, setDlg] = useState<'cancel' | 'move' | 'review' | null>(null)
  const [reason, setReason] = useState('')
  if (q.loading) return <><TopBar title="…" back="/bookings" /><div className="page"><Skeleton h={200} /></div></>
  if (q.error) return <><TopBar title={t('bookings.title')} back="/bookings" /><ErrorState error={q.error} onRetry={q.reload} /></>
  const b = q.data
  const tone = b.status === 'CONFIRMED' ? 'success' : b.status === 'PENDING' ? 'warning' : b.status === 'COMPLETED' ? '' : 'error'
  const doCancel = async () => { try { await post(`/api/bookings/${id}/cancel`, { reason: reason || undefined }); toast(t('bookings.cancelled.ok')); setDlg(null); q.reload() } catch (e) { toast(err(e instanceof ApiError ? e.code : undefined)); setDlg(null) } }
  const active = b.status === 'PENDING' || b.status === 'CONFIRMED'
  return (
    <>
      <TopBar title={t('bookings.title')} back="/bookings" />
      <div className="page">
        <div className="card card-pad col">
          <div className="row between"><span className="tag">#{b.reference}</span><span className={`tag ${tone}`}>{t(`status.${b.status}` as any)}</span></div>
          <h1>{b.serviceName}</h1>
          <dl className="kv">
            <dt>{t('book.where')}</dt><dd><Link to={`/salon/${b.businessId}`} style={{ color: 'var(--primary)' }}>{b.businessName}</Link> <DemoTag show={b.isDemo} /><div className="muted small">{b.address}, {b.city}</div></dd>
            <dt>{t('book.master')}</dt><dd><Link to={`/master/${b.professionalId}`} style={{ color: 'var(--primary)' }}>{b.professionalName}</Link></dd>
            <dt>{t('book.when')}</dt><dd>{fmtDate(b.startAt, locale, b.timezone)}, {fmtTime(b.startAt, locale, b.timezone)}–{fmtTime(b.endAt, locale, b.timezone)}</dd>
            <dt>{t('svc.duration')}</dt><dd>{Math.round((+new Date(b.endAt) - +new Date(b.startAt)) / 60000)} {t('common.min')}</dd>
            <dt>{t('book.total')}</dt><dd><Price minor={b.totalMinor} currency={b.currency} />{b.discountMinor > 0 && <span className="muted small"> (−{money(b.discountMinor, b.currency, locale)})</span>}</dd>
            <dt>{t('book.payment')}</dt><dd>{t(`pay.${b.paymentStatus}` as any)}</dd>
            {b.customerNote && <><dt>{t('book.notes')}</dt><dd>{b.customerNote}</dd></>}
          </dl>
          <p className="tiny muted">{t('svc.policyText', { h: b.cancellationHours })}</p>
          {b.paymentStatus === 'PENDING' && <PayPanel bookingId={b.id} onDone={q.reload} />}
        </div>
        <div className="row wrap">
          {b.lat != null && <a className="btn btn-soft btn-sm" href={`https://www.openstreetmap.org/?mlat=${b.lat}&mlon=${b.lng}#map=17/${b.lat}/${b.lng}`} target="_blank" rel="noreferrer"><Icon name="map" size={16} />{t('biz.directions')}</a>}
          {b.businessPhone && <a className="btn btn-soft btn-sm" href={`tel:${b.businessPhone}`}><Icon name="phone" size={16} />{t('biz.call')}</a>}
        </div>
        {active && <div className="col">
          {b.canReschedule ? <Button variant="secondary" block onClick={() => setDlg('move')}>{t('bookings.reschedule')}</Button> : <div className="alert info small">{t('bookings.windowPassed')}</div>}
          {b.canCancel && <Button variant="danger" block onClick={() => setDlg('cancel')}>{t('bookings.cancel')}</Button>}
        </div>}
        {b.canReview && <Button block onClick={() => setDlg('review')}>{t('bookings.review')}</Button>}
        {b.status !== 'PENDING' && b.status !== 'CONFIRMED' && <Button variant="secondary" block onClick={() => nav(`/book/${b.serviceId}?pro=${b.professionalId}`)}>{t('bookings.again')}</Button>}
        <section><h2>{t('bookings.history')}</h2><div className="col mt3" style={{ gap: 6 }}>{b.history.map((h: any, i: number) => <div key={i} className="row small"><Icon name="check" size={14} /><span className="grow">{t(`status.${h.toStatus}` as any)}{h.note ? ` — ${h.note}` : ''}</span><span className="muted tiny">{fmtDate(h.createdAt, locale, b.timezone, { day: 'numeric', month: 'short' })} {fmtTime(h.createdAt, locale, b.timezone)}</span></div>)}</div></section>
      </div>
      {dlg === 'cancel' && <Confirm title={t('bookings.cancelConfirm')} confirmLabel={t('bookings.cancel')} danger onConfirm={doCancel} onClose={() => setDlg(null)}><Field label={t('bookings.cancelReason')} id="cr"><Textarea id="cr" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={200} /></Field></Confirm>}
      {dlg === 'move' && <Reschedule booking={b} onClose={() => setDlg(null)} onDone={() => { setDlg(null); toast(t('bookings.moved.ok')); q.reload() }} />}
      {dlg === 'review' && <ReviewDialog bookingId={b.id} onClose={() => setDlg(null)} onDone={() => { setDlg(null); toast(t('review.thanks')); q.reload() }} />}
    </>
  )
}

/** DEV payment panel: resolves a simulated payment via the same event path a real provider webhook uses. */
function PayPanel({ bookingId, onDone }: { bookingId: number; onDone: () => void }) {
  const { t, err } = useI18n(); const toast = useToast(); const { config } = useApp()
  const pays = useLoad(() => get<any[]>(`/api/bookings/${bookingId}/payments`), [bookingId])
  const open = pays.data?.find((p) => p.status === 'PENDING')
  if (!open) return null
  if (!open.simulated || !config?.simulatedPayments) return <div className="alert info small">{t('pay.PENDING')}</div>
  const sim = async (success: boolean) => { try { await post(`/api/payments/${open.id}/simulate`, { success }); onDone() } catch (e) { toast(err(e instanceof ApiError ? e.code : undefined)) } }
  return (
    <div className="alert warning col" style={{ gap: 8 }}>
      <b>{t('dev.pay')}</b>
      <div className="row"><Button small onClick={() => sim(true)}>{t('dev.paySuccess')}</Button><Button small variant="danger" onClick={() => sim(false)}>{t('dev.payFail')}</Button></div>
    </div>
  )
}

function Reschedule({ booking, onClose, onDone }: { booking: any; onClose: () => void; onDone: () => void }) {
  const { t, locale, err } = useI18n(); const tz = booking.timezone
  const days = Array.from({ length: 14 }, (_, i) => ymdInZone(i, tz))
  const [date, setDate] = useState(days[0]); const [slots, setSlots] = useState<any[] | null>(null); const [slot, setSlot] = useState<string>(''); const [error, setError] = useState(''); const [busy, setBusy] = useState(false)
  useEffect(() => { setSlots(null); setSlot(''); get<any>(`/api/availability?serviceId=${booking.serviceId}&professionalId=${booking.professionalId}&date=${date}`).then((r) => setSlots(r.slots)).catch(() => setSlots([])) }, [date, booking])
  const go = async () => { setBusy(true); setError(''); try { await post(`/api/bookings/${booking.id}/reschedule`, { startAt: slot }); onDone() } catch (e) { setError(err(e instanceof ApiError ? e.code : undefined)) } finally { setBusy(false) } }
  return (
    <Sheet title={t('bookings.pickNew')} onClose={onClose}>
      <div className="daystrip">{days.map((d) => <button key={d} className={`day${date === d ? ' on' : ''}`} onClick={() => setDate(d)}><small>{fmtDate(ymdToDate(d), locale, 'UTC', { weekday: 'short' })}</small><b>{Number(d.slice(8))}</b></button>)}</div>
      {slots === null ? <Skeleton h={100} /> : slots.length === 0 ? <div className="alert info">{t('book.noSlots')}</div> : <div className="slots">{slots.map((s) => <button key={s.start} className={`slot${slot === s.start ? ' on' : ''}`} onClick={() => setSlot(s.start)}>{fmtTime(s.start, locale, tz)}</button>)}</div>}
      {error && <div className="alert error" role="alert">{error}</div>}
      <Button block disabled={!slot} loading={busy} onClick={go}>{t('common.confirm')}</Button>
    </Sheet>
  )
}

function ReviewDialog({ bookingId, onClose, onDone }: { bookingId: number; onClose: () => void; onDone: () => void }) {
  const { t, err } = useI18n(); const [rating, setRating] = useState(5); const [text, setText] = useState(''); const [error, setError] = useState(''); const [busy, setBusy] = useState(false)
  const go = async () => { setBusy(true); setError(''); try { await post(`/api/bookings/${bookingId}/review`, { rating, body: text }); onDone() } catch (e) { setError(err(e instanceof ApiError ? e.code : undefined)) } finally { setBusy(false) } }
  return (
    <Sheet title={t('review.title')} onClose={onClose}>
      <Field label={t('review.rating')}><RatingInput value={rating} onChange={setRating} /></Field>
      <Field label={t('review.text')} id="rv"><Textarea id="rv" value={text} onChange={(e) => setText(e.target.value)} maxLength={2000} /></Field>
      {error && <div className="alert error" role="alert">{error}</div>}
      <Button block loading={busy} onClick={go}>{t('review.submit')}</Button>
    </Sheet>
  )
}

export function Booked() {
  const { id } = useParams(); const { t, locale } = useI18n(); const [sp] = useSearchParams(); const nav = useNavigate()
  const q = useLoad(() => get<any>(`/api/bookings/${id}`), [id])
  if (q.loading) return <div className="page"><Skeleton h={240} /></div>
  if (q.error) return <ErrorState error={q.error} onRetry={q.reload} />
  const b = q.data; const pending = b.status === 'PENDING'
  const ics = () => {
    const f = (d: string) => new Date(d).toISOString().replace(/[-:]/g, '').replace(/\.\d{3}/, '')
    const body = `BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//SULU//EN\r\nBEGIN:VEVENT\r\nUID:${b.reference}@sulu\r\nDTSTAMP:${f(new Date().toISOString())}\r\nDTSTART:${f(b.startAt)}\r\nDTEND:${f(b.endAt)}\r\nSUMMARY:${b.serviceName} — ${b.businessName}\r\nLOCATION:${b.address}\r\nEND:VEVENT\r\nEND:VCALENDAR`
    const a = document.createElement('a'); a.href = URL.createObjectURL(new Blob([body], { type: 'text/calendar' })); a.download = `sulu-${b.reference}.ics`; a.click()
  }
  return (
    <div className="page" style={{ justifyContent: 'center', textAlign: 'center' }}>
      <div className="ob-art" style={{ minHeight: 140 }}><div className="blob" style={{ width: 120, height: 120 }}><Icon name="check" size={64} /></div></div>
      <h1>{pending ? t('done.pendingTitle') : t('done.title')}</h1>
      <p className="muted">{pending ? t('done.pendingText') : t('done.text')}</p>
      <div className="card card-pad col" style={{ textAlign: 'left', gap: 4 }}>
        <div className="bold">{b.serviceName}</div><div className="muted small">{b.businessName} · {b.professionalName}</div>
        <div className="small"><Icon name="calendar" size={14} /> {fmtDate(b.startAt, locale, b.timezone)}, {fmtTime(b.startAt, locale, b.timezone)}</div>
        <div className="row between"><span className="muted small">{t('done.ref')}</span><b>{b.reference}</b></div>
      </div>
      {sp.get('pay') && b.paymentStatus === 'PENDING' && <PayPanel bookingId={b.id} onDone={q.reload} />}
      {b.paymentStatus === 'PAID' && <div className="alert success">{t('pay.PAID')}</div>}
      <Button block onClick={() => nav(`/bookings/${b.id}`, { replace: true })}>{t('done.view')}</Button>
      <Button block variant="secondary" onClick={ics}>{t('done.addCalendar')}</Button>
      <Link to="/" className="small" style={{ color: 'var(--primary)' }}>{t('done.home')}</Link>
    </div>
  )
}

import { useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { Icon, Logo } from '../../components/Icon'
import { Button, Empty, ErrorState, Skeleton, useLoad, useToast } from '../../components/ui'
import { useI18n } from '../../i18n'
import { ApiError, get, post } from '../../lib/api'
import { useApp } from '../../lib/app'
import { fmtDate, fmtTime, ymdInZone } from '../../lib/format'
import { HoursEditor, TimeOff } from './Sections'
import { LangSwitch } from './BusinessApp'

/** Self-service view for a PROFESSIONAL: own appointments (accept / reject / complete), working hours and days off. */
export function ProApp() {
  const { t, locale, err } = useI18n(); const { user, logout } = useApp(); const toast = useToast(); const nav = useNavigate()
  const me = useLoad(() => (user?.role === 'PROFESSIONAL' ? get<any>('/api/pro/me') : Promise.resolve(null)), [user?.id])
  const tz = me.data?.timezone ?? 'Asia/Almaty'
  const bookings = useLoad(() => (me.data ? get<any[]>(`/api/pro/bookings?from=${ymdInZone(0, tz)}&to=${ymdInZone(14, tz)}`) : Promise.resolve([])), [me.data?.id])
  const [dlg, setDlg] = useState<'hours' | 'off' | null>(null)
  if (!user) return <Navigate to="/login?next=/pro" replace />
  if (user.role !== 'PROFESSIONAL') return <div className="cab-main"><Empty icon="shield" title={t('err.FORBIDDEN')}><Link to="/" className="btn btn-primary">SULU</Link></Empty></div>
  if (me.loading) return <div className="cab-main"><Skeleton h={200} /></div>
  if (me.error) return <div className="cab-main">{me.error instanceof ApiError && me.error.status === 404 ? <Empty icon="user" title={t('p.noProfile')} /> : <ErrorState error={me.error} onRetry={me.reload} />}</div>
  const act = async (id: number, a: string) => { try { await post(`/api/pro/bookings/${id}/${a}`); toast(t('b.saved')); bookings.reload() } catch (e) { toast(err(e instanceof ApiError ? e.code : undefined)) } }
  return (
    <div className="cab-main" style={{ margin: '0 auto' }}>
      <div className="row between wrap"><Logo size={32} /><div className="row"><LangSwitch /><Button small variant="ghost" onClick={async () => { await logout(); nav('/login') }}><Icon name="logout" size={16} />{user.email}</Button></div></div>
      <div className="row between wrap"><div><h1>{t('p.title')}</h1><div className="muted">{me.data.fullName} · {me.data.businessName}</div></div><div className="row"><Button small variant="soft" onClick={() => setDlg('hours')}>{t('b.staff.hours')}</Button><Button small variant="soft" onClick={() => setDlg('off')}>{t('b.staff.timeOff')}</Button></div></div>
      {bookings.loading ? <Skeleton h={200} /> : bookings.error ? <ErrorState error={bookings.error} onRetry={bookings.reload} /> : bookings.data!.length === 0 ? <Empty icon="calendar" title={t('b.noToday')} /> : (
        <div className="col">{bookings.data!.map((b) => (
          <div key={b.id} className="card card-pad row wrap">
            <div style={{ width: 150 }}><b>{fmtDate(b.startAt, locale, tz, { day: 'numeric', month: 'short', weekday: 'short' })}</b><div>{fmtTime(b.startAt, locale, tz)}–{fmtTime(b.endAt, locale, tz)}</div></div>
            <div className="grow"><div className="bold">{b.serviceName}</div><div className="muted small">{b.customerName}{b.customerNote ? ` · “${b.customerNote}”` : ''}</div></div>
            <span className="tag">{t(`status.${b.status}` as any)}</span>
            {b.status === 'PENDING' && <><Button small onClick={() => act(b.id, 'confirm')}>{t('b.confirm')}</Button><Button small variant="danger" onClick={() => act(b.id, 'reject')}>{t('b.reject')}</Button></>}
            {b.status === 'CONFIRMED' && new Date(b.startAt) <= new Date() && <><Button small onClick={() => act(b.id, 'complete')}>{t('b.complete')}</Button><Button small variant="soft" onClick={() => act(b.id, 'no-show')}>{t('b.noShow')}</Button></>}
          </div>))}</div>)}
      {dlg === 'hours' && <HoursEditor p={me.data} url="/api/pro/me/hours" onClose={() => setDlg(null)} onSaved={() => { setDlg(null); toast(t('b.saved')); me.reload() }} />}
      {dlg === 'off' && <TimeOff p={me.data} base="/api/pro/me/time-off" onClose={() => setDlg(null)} onChanged={me.reload} />}
    </div>
  )
}

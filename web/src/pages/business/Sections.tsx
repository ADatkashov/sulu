import { useEffect, useState, type FormEvent } from 'react'
import { Button, Empty, ErrorState, Field, Input, Price, Select, Sheet, Skeleton, SkeletonList, Textarea, useLoad, useToast } from '../../components/ui'
import { Icon } from '../../components/Icon'
import { catName, useI18n } from '../../i18n'
import { ApiError, api, del, get, mediaUrl, post, put } from '../../lib/api'
import { fmtDate, fmtTime, money, ymdInZone, ymdToDate } from '../../lib/format'
import { useBiz } from './BusinessApp'

const addDays = (ymd: string, n: number) => { const d = ymdToDate(ymd); d.setUTCDate(d.getUTCDate() + n); return d.toISOString().slice(0, 10) }
const monday = (ymd: string) => { const d = ymdToDate(ymd); const wd = (d.getUTCDay() + 6) % 7; return addDays(ymd, -wd) }
const toMinor = (s: string) => Math.round(Number(s.replace(',', '.')) * 100)
const toMajor = (m: number) => String(m / 100)
const statusTone = (s: string) => (s === 'CONFIRMED' ? 'success' : s === 'PENDING' ? 'warning' : s === 'COMPLETED' ? '' : 'error')
function useErr() { const { err } = useI18n(); const toast = useToast(); return (e: unknown) => toast(err(e instanceof ApiError ? e.code : undefined)) }

// ---------------------------------------------------------------- booking dialog
export function BookingDialog({ b, onClose, onChanged }: { b: any; onClose: () => void; onChanged: () => void }) {
  const { t, locale } = useI18n(); const { bid, biz } = useBiz(); const toast = useToast(); const fail = useErr()
  const [mode, setMode] = useState<'view' | 'move' | 'cancel'>('view'); const [reason, setReason] = useState('')
  const tz = b.timezone ?? biz.timezone
  const act = async (a: string, body?: any) => { try { await post(`/api/biz/${bid}/bookings/${b.id}/${a}`, body ?? {}); toast(t('b.saved')); onChanged(); onClose() } catch (e) { fail(e) } }
  const started = new Date(b.startAt) <= new Date()
  return (
    <Sheet title={`#${b.reference}`} onClose={onClose}>
      {mode === 'view' && <>
        <div className="row between"><b>{b.serviceName}</b><span className={`tag ${statusTone(b.status)}`}>{t(`status.${b.status}` as any)}</span></div>
        <dl className="kv"><dt>{t('b.client')}</dt><dd>{b.customerName}{b.customerPhone && <> · <a href={`tel:${b.customerPhone}`}>{b.customerPhone}</a></>}</dd>
          <dt>{t('book.master')}</dt><dd>{b.professionalName}</dd><dt>{t('book.when')}</dt><dd>{fmtDate(b.startAt, locale, tz)}, {fmtTime(b.startAt, locale, tz)}–{fmtTime(b.endAt, locale, tz)}</dd>
          <dt>{t('book.total')}</dt><dd>{money(b.totalMinor, b.currency, locale)} · {t(`pay.${b.paymentStatus}` as any)}</dd>{b.customerNote && <><dt>{t('book.notes')}</dt><dd>{b.customerNote}</dd></>}</dl>
        <div className="row wrap">
          {b.status === 'PENDING' && <><Button small onClick={() => act('confirm')}>{t('b.confirm')}</Button><Button small variant="danger" onClick={() => act('reject')}>{t('b.reject')}</Button></>}
          {b.status === 'CONFIRMED' && started && <><Button small onClick={() => act('complete')}>{t('b.complete')}</Button><Button small variant="soft" onClick={() => act('no-show')}>{t('b.noShow')}</Button></>}
          {(b.status === 'PENDING' || b.status === 'CONFIRMED') && <><Button small variant="secondary" onClick={() => setMode('move')}>{t('b.reschedule')}</Button><Button small variant="danger" onClick={() => setMode('cancel')}>{t('b.cancelBooking')}</Button></>}
        </div></>}
      {mode === 'cancel' && <><Field label={t('bookings.cancelReason')} id="cr"><Textarea id="cr" value={reason} onChange={(e) => setReason(e.target.value)} /></Field><Button variant="danger" onClick={() => act('cancel', { reason })}>{t('b.cancelBooking')}</Button></>}
      {mode === 'move' && <MovePicker b={b} tz={tz} onPick={(start) => act('reschedule', { startAt: start })} />}
    </Sheet>
  )
}
function MovePicker({ b, tz, onPick }: { b: any; tz: string; onPick: (s: string) => void }) {
  const { t, locale } = useI18n(); const [date, setDate] = useState(ymdInZone(0, tz)); const [slots, setSlots] = useState<any[] | null>(null)
  useEffect(() => { setSlots(null); get<any>(`/api/availability?serviceId=${b.serviceId}&professionalId=${b.professionalId}&date=${date}`).then((r) => setSlots(r.slots)).catch(() => setSlots([])) }, [date, b])
  return <><Field label={t('book.chooseDate')} id="md"><Input id="md" type="date" value={date} min={ymdInZone(0, tz)} onChange={(e) => setDate(e.target.value)} /></Field>
    {slots === null ? <Skeleton h={80} /> : slots.length === 0 ? <div className="alert info">{t('book.noSlots')}</div> : <div className="slots">{slots.map((s) => <button key={s.start} className="slot" onClick={() => onPick(s.start)}>{fmtTime(s.start, locale, tz)}</button>)}</div>}</>
}

// ---------------------------------------------------------------- overview
export function Overview() {
  const { t, locale } = useI18n(); const { bid, biz } = useBiz()
  const o = useLoad(() => get<any>(`/api/biz/${bid}/overview`), [bid]); const [open, setOpen] = useState<any>(null)
  if (o.loading) return <SkeletonList n={3} h={120} />
  if (o.error) return <ErrorState error={o.error} onRetry={o.reload} />
  const d = o.data; const cur = d.currency
  return (
    <>
      <div className="row between wrap"><h1>{biz.name}</h1><span className={`tag ${biz.status === 'APPROVED' ? 'success' : 'warning'}`}>{biz.status}</span></div>
      {biz.status === 'PENDING_VERIFICATION' && <div className="alert warning">{t('b.pending')}</div>}
      {biz.status === 'REJECTED' && <div className="alert error">{t('b.rejected')}</div>}
      {biz.status === 'SUSPENDED' && <div className="alert error">{t('b.suspended')}</div>}
      <div className="grid g4">
        <div className="card stat"><b>{d.today.length}</b><span>{t('b.today')}</span></div>
        <div className="card stat"><b>{d.upcomingCount}</b><span>{t('b.upcoming')}</span></div>
        <div className="card stat"><b>{d.pendingCount}</b><span>{t('b.needsConfirm')}</span></div>
        <div className="card stat"><b>{Number(d.rating.ratingAvg).toFixed(1)} ★</b><span>{t('b.rating')} ({d.rating.ratingCount})</span></div>
        <div className="card stat"><b>{d.completedThisMonth}</b><span>{t('b.completedMonth')}</span></div>
        <div className="card stat"><b>{d.cancelledThisMonth}</b><span>{t('b.cancelledMonth')}</span></div>
        <div className="card stat" style={{ gridColumn: 'span 2' }}><b>{money(d.revenueThisMonthMinor, cur, locale)}</b><span>{t('b.revenueMonth')}</span></div>
      </div>
      <p className="muted small">{t('b.revenueNote')}{d.onlinePaymentsSimulatedOnly ? ' ' + t('b.simNote') : ''}</p>
      <section><h2>{t('b.today')}</h2>
        {d.today.length === 0 ? <Empty icon="calendar" title={t('b.noToday')} /> : <div className="col mt3">{d.today.map((b: any) => (
          <button key={b.id} className="card card-pad row" style={{ textAlign: 'left' }} onClick={() => setOpen(b)}>
            <b style={{ width: 54 }}>{fmtTime(b.startAt, locale, b.timezone)}</b><div className="grow"><div className="bold">{b.serviceName}</div><div className="muted small">{b.customerName} · {b.professionalName}</div></div><span className={`tag ${statusTone(b.status)}`}>{t(`status.${b.status}` as any)}</span></button>))}</div>}
      </section>
      {open && <BookingDialog b={open} onClose={() => setOpen(null)} onChanged={o.reload} />}
    </>
  )
}

// ---------------------------------------------------------------- calendar
export function CalendarPage() {
  const { t, locale } = useI18n(); const { bid, biz } = useBiz(); const tz = biz.timezone ?? 'Asia/Almaty'
  const [view, setView] = useState<'day' | 'week'>('week'); const [date, setDate] = useState(ymdInZone(0, tz)); const [pro, setPro] = useState(''); const [open, setOpen] = useState<any>(null)
  const from = view === 'week' ? monday(date) : date; const to = view === 'week' ? addDays(from, 6) : date
  const pros = useLoad(() => get<any[]>(`/api/biz/${bid}/professionals`), [bid])
  const q = useLoad(() => get<any[]>(`/api/biz/${bid}/bookings?from=${from}&to=${to}${pro ? '&professionalId=' + pro : ''}`), [bid, from, to, pro])
  const days = Array.from({ length: view === 'week' ? 7 : 1 }, (_, i) => addDays(from, i))
  const byDay = (d: string) => (q.data ?? []).filter((b) => ymdInZone(0, tz, new Date(b.startAt)) === d)
  return (
    <>
      <div className="row between wrap"><h1>{t('b.nav.calendar')}</h1>
        <div className="row wrap">
          <div className="tabs"><button className={view === 'day' ? 'on' : ''} onClick={() => setView('day')}>{t('b.day')}</button><button className={view === 'week' ? 'on' : ''} onClick={() => setView('week')}>{t('b.week')}</button></div>
          <button className="iconbtn" aria-label="prev" onClick={() => setDate(addDays(date, view === 'week' ? -7 : -1))}><Icon name="chevronL" size={18} /></button>
          <Input type="date" aria-label="date" value={date} onChange={(e) => setDate(e.target.value)} style={{ width: 160 }} />
          <button className="iconbtn" aria-label="next" onClick={() => setDate(addDays(date, view === 'week' ? 7 : 1))}><Icon name="chevron" size={18} /></button>
          <Select aria-label={t('b.allMasters')} value={pro} onChange={(e) => setPro(e.target.value)} style={{ width: 200 }}><option value="">{t('b.allMasters')}</option>{pros.data?.map((p) => <option key={p.id} value={p.id}>{p.fullName}</option>)}</Select>
        </div></div>
      {q.error ? <ErrorState error={q.error} onRetry={q.reload} /> : q.loading ? <Skeleton h={300} /> : (
        <div className="cal" style={{ gridTemplateColumns: `repeat(${days.length}, minmax(${view === 'week' ? 150 : 280}px, 1fr))` }}>
          {days.map((d) => <div key={d} className="cal-col"><h4>{fmtDate(ymdToDate(d), locale, 'UTC', { weekday: 'short', day: 'numeric', month: 'short' })}</h4>
            {byDay(d).map((b) => <button key={b.id} className={`appt ${b.status}`} onClick={() => setOpen(b)}><b>{fmtTime(b.startAt, locale, tz)}</b> {b.serviceName}<div className="tiny">{b.customerName} · {b.professionalName}</div></button>)}</div>)}
        </div>)}
      {open && <BookingDialog b={open} onClose={() => setOpen(null)} onChanged={q.reload} />}
    </>
  )
}

// ---------------------------------------------------------------- services
export function ServicesPage() {
  const { t, locale } = useI18n(); const { bid, biz } = useBiz(); const toast = useToast(); const fail = useErr()
  const list = useLoad(() => get<any[]>(`/api/biz/${bid}/services`), [bid]); const cats = useLoad(() => get<any[]>('/api/categories'), []); const pros = useLoad(() => get<any[]>(`/api/biz/${bid}/professionals`), [bid])
  const [edit, setEdit] = useState<any | null>(null)
  const cur = biz.currency ?? 'KZT'
  const blank = { categoryId: cats.data?.[0]?.id ?? 0, name: '', description: '', price: '', priceFrom: false, durationMin: 60, atHome: false, tags: '', professionalIds: [] as number[] }
  const save = async (f: any) => {
    const body = { categoryId: Number(f.categoryId), name: f.name, description: f.description, priceMinor: toMinor(String(f.price)), priceFrom: f.priceFrom, durationMin: Number(f.durationMin), atHome: f.atHome, tags: f.tags, professionalIds: f.professionalIds }
    try { f.id ? await put(`/api/biz/${bid}/services/${f.id}`, body) : await post(`/api/biz/${bid}/services`, body); setEdit(null); toast(t('b.saved')); list.reload() } catch (e) { fail(e) }
  }
  const setActive = async (s: any, v: boolean) => { try { await post(`/api/biz/${bid}/services/${s.id}/active?value=${v}`); list.reload() } catch (e) { fail(e) } }
  return (
    <>
      <div className="row between"><h1>{t('b.nav.services')}</h1><Button small onClick={() => setEdit({ ...blank })}><Icon name="plus" size={16} />{t('b.services.new')}</Button></div>
      {list.loading ? <SkeletonList /> : list.error ? <ErrorState error={list.error} onRetry={list.reload} /> : list.data!.length === 0 ? <Empty icon="tag" title={t('b.empty')} /> : (
        <div className="card tablewrap"><table className="table"><thead><tr><th>{t('b.field.name')}</th><th>{t('b.field.category')}</th><th>{t('b.field.price')}</th><th>{t('b.field.duration')}</th><th>{t('b.field.masters')}</th><th /></tr></thead>
          <tbody>{list.data!.map((s) => <tr key={s.id} style={{ opacity: s.active ? 1 : .55 }}><td><b>{s.name}</b>{!s.active && <span className="tag" style={{ marginLeft: 8 }}>{t('b.services.archived')}</span>}</td><td>{s.categoryName}</td><td><Price minor={s.priceMinor} currency={s.currency} from={s.priceFrom} /></td><td>{s.durationMin}</td><td>{s.professionalIds.length}</td>
            <td><div className="row gap2"><Button small variant="soft" onClick={() => setEdit({ ...s, price: toMajor(s.priceMinor) })}>{t('common.edit')}</Button><Button small variant="ghost" onClick={() => setActive(s, !s.active)}>{s.active ? t('b.services.archive') : t('b.services.restore')}</Button></div></td></tr>)}</tbody></table></div>)}
      {edit && <ServiceForm init={edit} cats={cats.data ?? []} pros={pros.data ?? []} cur={cur} locale={locale} onSave={save} onClose={() => setEdit(null)} />}
    </>
  )
}
function ServiceForm({ init, cats, pros, cur, locale, onSave, onClose }: any) {
  const { t } = useI18n(); const [f, setF] = useState(init); const [busy, setBusy] = useState(false); const [err, setErr] = useState('')
  const set = (k: string, v: any) => setF({ ...f, [k]: v })
  const submit = async (e: FormEvent) => { e.preventDefault(); if (!f.name.trim() || !(toMinor(String(f.price)) >= 0)) { setErr(t('err.VALIDATION_ERROR')); return } setBusy(true); await onSave(f); setBusy(false) }
  return (
    <Sheet title={f.id ? t('common.edit') : t('b.services.new')} onClose={onClose}>
      <form className="col" onSubmit={submit}>
        <Field label={t('b.field.name')} id="sn"><Input id="sn" value={f.name} onChange={(e) => set('name', e.target.value)} required maxLength={120} /></Field>
        <Field label={t('b.field.category')} id="sc"><Select id="sc" value={f.categoryId} onChange={(e) => set('categoryId', e.target.value)}>{cats.map((c: any) => <option key={c.id} value={c.id}>{catName(c, locale)}</option>)}</Select></Field>
        <div className="grid g2"><Field label={`${t('b.field.price')} (${cur})`} id="spr"><Input id="spr" inputMode="decimal" value={f.price} onChange={(e) => set('price', e.target.value)} required /></Field>
          <Field label={t('b.field.duration')} id="sd"><Input id="sd" type="number" min={5} max={720} step={5} value={f.durationMin} onChange={(e) => set('durationMin', e.target.value)} required /></Field></div>
        <label className="row"><input type="checkbox" checked={f.priceFrom} onChange={(e) => set('priceFrom', e.target.checked)} style={{ width: 20, height: 20 }} />{t('b.field.priceFrom')}</label>
        <label className="row"><input type="checkbox" checked={f.atHome} onChange={(e) => set('atHome', e.target.checked)} style={{ width: 20, height: 20 }} />{t('b.field.atHome')}</label>
        <Field label={t('b.field.description')} id="sdesc"><Textarea id="sdesc" value={f.description} onChange={(e) => set('description', e.target.value)} /></Field>
        <Field label={t('b.field.tags')} id="stg"><Input id="stg" value={f.tags} onChange={(e) => set('tags', e.target.value)} /></Field>
        <Field label={t('b.field.masters')}><div className="col" style={{ gap: 6 }}>{pros.filter((p: any) => p.active).map((p: any) => <label key={p.id} className="row"><input type="checkbox" style={{ width: 20, height: 20 }} checked={f.professionalIds.includes(p.id)} onChange={(e) => set('professionalIds', e.target.checked ? [...f.professionalIds, p.id] : f.professionalIds.filter((x: number) => x !== p.id))} />{p.fullName}</label>)}</div></Field>
        {err && <div className="alert error">{err}</div>}<Button type="submit" loading={busy}>{t('common.save')}</Button>
      </form>
    </Sheet>
  )
}

// ---------------------------------------------------------------- staff
export function StaffPage() {
  const { t } = useI18n(); const { bid } = useBiz(); const toast = useToast(); const fail = useErr()
  const list = useLoad(() => get<any[]>(`/api/biz/${bid}/professionals`), [bid]); const svcs = useLoad(() => get<any[]>(`/api/biz/${bid}/services`), [bid])
  const [dlg, setDlg] = useState<{ kind: 'edit' | 'hours' | 'timeoff' | 'invite'; p: any } | null>(null)
  const saveNew = async (f: any) => { try { await post(`/api/biz/${bid}/professionals`, f); setDlg(null); toast(t('b.saved')); list.reload() } catch (e) { fail(e) } }
  const toggle = async (p: any) => { try { await post(`/api/biz/${bid}/professionals/${p.id}/active?value=${!p.active}`); list.reload() } catch (e) { fail(e) } }
  const upload = async (p: any, file: File | undefined) => { if (!file) return; const fd = new FormData(); fd.append('file', file); try { await api(`/api/biz/${bid}/professionals/${p.id}/portfolio`, { method: 'POST', form: fd }); list.reload() } catch (e) { fail(e) } }
  return (
    <>
      <div className="row between"><h1>{t('b.nav.staff')}</h1><Button small onClick={() => setDlg({ kind: 'edit', p: { fullName: '', title: '', bio: '', experienceYears: 0, specialties: '', gender: '', serviceIds: [] } })}><Icon name="plus" size={16} />{t('b.staff.new')}</Button></div>
      {list.loading ? <SkeletonList /> : list.error ? <ErrorState error={list.error} onRetry={list.reload} /> : (
        <div className="grid g2">{list.data!.map((p) => (
          <div key={p.id} className="card card-pad col" style={{ opacity: p.active ? 1 : .6 }}>
            <div className="row"><div className="avatar">{p.fullName[0]}</div><div className="grow"><b>{p.fullName}</b><div className="muted small">{p.title}</div></div><span className="tag">★ {Number(p.ratingAvg).toFixed(1)}</span></div>
            <div className="small muted">{p.serviceIds.length} {t('b.nav.services').toLowerCase()} · {p.hasAccount ? '✓ account' : '—'}</div>
            <div className="row gap2 wrap"><Button small variant="soft" onClick={() => setDlg({ kind: 'edit', p })}>{t('common.edit')}</Button><Button small variant="soft" onClick={() => setDlg({ kind: 'hours', p })}>{t('b.staff.hours')}</Button><Button small variant="soft" onClick={() => setDlg({ kind: 'timeoff', p })}>{t('b.staff.timeOff')}</Button><Button small variant="soft" onClick={() => setDlg({ kind: 'invite', p })}>{t('b.staff.invite')}</Button><Button small variant="ghost" onClick={() => toggle(p)}>{p.active ? t('b.staff.deactivate') : t('b.staff.activate')}</Button></div>
            <div><div className="small bold">{t('b.staff.portfolio')} ({p.portfolio.length})</div><div className="row wrap gap2 mt2">{p.portfolio.map((x: any) => <div key={x.id} style={{ position: 'relative' }}><img src={mediaUrl(x.imageKey)} alt="" width={56} height={56} style={{ borderRadius: 10, objectFit: 'cover' }} /><button aria-label="remove" className="badge" style={{ cursor: 'pointer' }} onClick={async () => { await del(`/api/biz/${bid}/professionals/${p.id}/portfolio/${x.id}`); list.reload() }}>×</button></div>)}
              <label className="btn btn-soft btn-sm" style={{ cursor: 'pointer' }}><Icon name="plus" size={14} />{t('b.staff.upload')}<input type="file" accept="image/jpeg,image/png,image/webp" hidden onChange={(e) => upload(p, e.target.files?.[0])} /></label></div></div>
          </div>))}</div>)}
      {dlg?.kind === 'edit' && <ProForm p={dlg.p} svcs={svcs.data ?? []} onClose={() => setDlg(null)} onSave={async (f: any) => { if (dlg.p.id) { try { await put(`/api/biz/${bid}/professionals/${dlg.p.id}`, f); setDlg(null); list.reload() } catch (e) { fail(e) } } else await saveNew(f) }} />}
      {dlg?.kind === 'hours' && <HoursEditor p={dlg.p} onClose={() => setDlg(null)} onSaved={() => { setDlg(null); toast(t('b.saved')); list.reload() }} />}
      {dlg?.kind === 'timeoff' && <TimeOff p={dlg.p} onClose={() => setDlg(null)} onChanged={list.reload} />}
      {dlg?.kind === 'invite' && <Invite p={dlg.p} onClose={() => setDlg(null)} />}
    </>
  )
}
function ProForm({ p, svcs, onSave, onClose }: any) {
  const { t } = useI18n(); const [f, setF] = useState({ ...p, gender: p.gender ?? '' }); const [busy, setBusy] = useState(false); const set = (k: string, v: any) => setF({ ...f, [k]: v })
  return (
    <Sheet title={p.id ? t('common.edit') : t('b.staff.new')} onClose={onClose}>
      <form className="col" onSubmit={async (e) => { e.preventDefault(); setBusy(true); await onSave({ fullName: f.fullName, title: f.title, bio: f.bio, experienceYears: Number(f.experienceYears), specialties: f.specialties, gender: f.gender || undefined, serviceIds: f.serviceIds }); setBusy(false) }}>
        <Field label={t('b.field.name')} id="pn"><Input id="pn" required value={f.fullName} onChange={(e) => set('fullName', e.target.value)} /></Field>
        <Field label="Title" id="pt"><Input id="pt" value={f.title} onChange={(e) => set('title', e.target.value)} /></Field>
        <div className="grid g2"><Field label={t('pro.experience')} id="pe"><Input id="pe" type="number" min={0} max={80} value={f.experienceYears} onChange={(e) => set('experienceYears', e.target.value)} /></Field>
          <Field label={t('filter.gender')} id="pg"><Select id="pg" value={f.gender} onChange={(e) => set('gender', e.target.value)}><option value="">—</option><option value="FEMALE">{t('gender.FEMALE')}</option><option value="MALE">{t('gender.MALE')}</option></Select></Field></div>
        <Field label="Specialties" id="ps"><Input id="ps" value={f.specialties} onChange={(e) => set('specialties', e.target.value)} /></Field>
        <Field label={t('pro.bio')} id="pb"><Textarea id="pb" value={f.bio} onChange={(e) => set('bio', e.target.value)} /></Field>
        <Field label={t('b.nav.services')}><div className="col" style={{ gap: 6 }}>{svcs.filter((s: any) => s.active).map((s: any) => <label key={s.id} className="row"><input type="checkbox" style={{ width: 20, height: 20 }} checked={f.serviceIds.includes(s.id)} onChange={(e) => set('serviceIds', e.target.checked ? [...f.serviceIds, s.id] : f.serviceIds.filter((x: number) => x !== s.id))} />{s.name}</label>)}</div></Field>
        <Button type="submit" loading={busy}>{t('common.save')}</Button>
      </form>
    </Sheet>
  )
}
export function HoursEditor({ p, onClose, onSaved, url }: { p: any; onClose: () => void; onSaved: () => void; url?: string }) {
  const { t } = useI18n(); const { bid } = useBiz?.() ?? ({} as any); const fail = useErr()
  const init = [1, 2, 3, 4, 5, 6, 7].map((w) => { const h = (p.hours ?? []).find((x: any) => x.weekday === w); return { weekday: w, on: !!h, startTime: h?.startTime ?? '10:00', endTime: h?.endTime ?? '19:00', brk: !!h?.breakStart, breakStart: h?.breakStart ?? '13:00', breakEnd: h?.breakEnd ?? '14:00' } })
  const [rows, setRows] = useState(init)
  const upd = (i: number, patch: any) => setRows(rows.map((r, j) => (j === i ? { ...r, ...patch } : r)))
  const save = async () => { try { await put(url ?? `/api/biz/${bid}/professionals/${p.id}/hours`, rows.filter((r) => r.on).map((r) => ({ weekday: r.weekday, startTime: r.startTime, endTime: r.endTime, breakStart: r.brk ? r.breakStart : null, breakEnd: r.brk ? r.breakEnd : null }))); onSaved() } catch (e) { fail(e) } }
  return (
    <Sheet title={t('b.staff.hours')} onClose={onClose}>
      <div className="col">{rows.map((r, i) => (
        <div key={r.weekday} className="card card-pad col" style={{ gap: 8 }}>
          <label className="row between"><b>{t(`wd.${r.weekday}` as any)}</b><span className="row gap2"><span className="muted small">{r.on ? '' : t('b.staff.off')}</span><input type="checkbox" checked={r.on} style={{ width: 20, height: 20 }} onChange={(e) => upd(i, { on: e.target.checked })} aria-label={t(`wd.${r.weekday}` as any)} /></span></label>
          {r.on && <><div className="grid g2"><Field label={t('b.staff.start')}><Input type="time" value={r.startTime} onChange={(e) => upd(i, { startTime: e.target.value })} /></Field><Field label={t('b.staff.end')}><Input type="time" value={r.endTime} onChange={(e) => upd(i, { endTime: e.target.value })} /></Field></div>
            <label className="row small"><input type="checkbox" checked={r.brk} style={{ width: 18, height: 18 }} onChange={(e) => upd(i, { brk: e.target.checked })} />{t('b.staff.break')}</label>
            {r.brk && <div className="grid g2"><Input type="time" aria-label="break start" value={r.breakStart} onChange={(e) => upd(i, { breakStart: e.target.value })} /><Input type="time" aria-label="break end" value={r.breakEnd} onChange={(e) => upd(i, { breakEnd: e.target.value })} /></div>}</>}
        </div>))}</div>
      <Button onClick={save}>{t('common.save')}</Button>
    </Sheet>
  )
}
export function TimeOff({ p, onClose, onChanged, base }: { p: any; onClose: () => void; onChanged: () => void; base?: string }) {
  const { t, locale } = useI18n(); const { bid, biz } = useBiz?.() ?? ({ biz: {} } as any); const fail = useErr(); const tz = biz.timezone ?? 'Asia/Almaty'
  const prefix = base ?? `/api/biz/${bid}/professionals/${p.id}/time-off`
  const [from, setFrom] = useState(ymdInZone(1, tz)); const [to, setTo] = useState(ymdInZone(1, tz)); const [msg, setMsg] = useState(''); const [list, setList] = useState<any[]>(p.timeOff ?? [])
  const toIso = (ymd: string, end: boolean) => { const [y, m, d] = ymd.split('-').map(Number); const guess = Date.UTC(y, m - 1, d + (end ? 1 : 0)); const offset = tzOffsetMs(tz, new Date(guess)); return new Date(guess - offset).toISOString() }
  const add = async () => { try { const r = await post(prefix, { from: toIso(from, false), to: toIso(to, true), reason: '' }); setMsg(r.conflictingBookings ? t('b.staff.conflicts', { n: r.conflictingBookings }) : ''); onChanged(); setList((l) => [...l, { id: r.id, startsAt: toIso(from, false), endsAt: toIso(to, true) }]) } catch (e) { fail(e) } }
  return (
    <Sheet title={t('b.staff.timeOff')} onClose={onClose}>
      <div className="grid g2"><Field label={t('b.staff.from')}><Input type="date" value={from} onChange={(e) => setFrom(e.target.value)} /></Field><Field label={t('b.staff.to')}><Input type="date" value={to} min={from} onChange={(e) => setTo(e.target.value)} /></Field></div>
      <Button onClick={add}>{t('b.staff.addTimeOff')}</Button>{msg && <div className="alert warning">{msg}</div>}
      {list.map((x) => <div key={x.id} className="row between small card card-pad"><span>{fmtDate(x.startsAt, locale, tz, { day: 'numeric', month: 'short' })} → {fmtDate(new Date(+new Date(x.endsAt) - 1), locale, tz, { day: 'numeric', month: 'short' })}</span><button className="btn-ghost" aria-label="remove" onClick={async () => { await del(`${prefix}/${x.id}`); setList(list.filter((y) => y.id !== x.id)); onChanged() }}><Icon name="trash" size={16} /></button></div>)}
    </Sheet>
  )
}
function tzOffsetMs(tz: string, at: Date) { const f = new Intl.DateTimeFormat('en-US', { timeZone: tz, hourCycle: 'h23', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit' }).formatToParts(at); const g = (t: string) => Number(f.find((p) => p.type === t)!.value); return Date.UTC(g('year'), g('month') - 1, g('day'), g('hour'), g('minute'), g('second')) - Math.floor(at.getTime() / 1000) * 1000 }
function Invite({ p, onClose }: { p: any; onClose: () => void }) {
  const { t } = useI18n(); const { bid } = useBiz(); const fail = useErr(); const [email, setEmail] = useState(''); const [res, setRes] = useState<any>(null)
  return (
    <Sheet title={t('b.staff.invite')} onClose={onClose}>
      <Field label={t('auth.email')} id="ie"><Input id="ie" type="email" value={email} onChange={(e) => setEmail(e.target.value)} /></Field>
      <Button onClick={async () => { try { setRes(await post(`/api/biz/${bid}/professionals/${p.id}/invite`, { email })) } catch (e) { fail(e) } }} disabled={!email}>{t('b.staff.invite')}</Button>
      {res && <div className="alert info col"><span>{res.note}</span>{res.devToken && <><b>{t('b.staff.inviteDev')}</b><code style={{ wordBreak: 'break-all' }}>{res.devToken}</code></>}</div>}
    </Sheet>
  )
}

// ---------------------------------------------------------------- customers
export function CustomersPage() {
  const { t, locale } = useI18n(); const { bid, biz } = useBiz(); const toast = useToast(); const fail = useErr(); const [q, setQ] = useState(''); const [note, setNote] = useState<any>(null)
  const list = useLoad(() => get<any[]>(`/api/biz/${bid}/customers${q ? '?q=' + encodeURIComponent(q) : ''}`), [bid, q])
  return (
    <>
      <div className="row between wrap"><h1>{t('b.nav.customers')}</h1><Input aria-label={t('common.search')} placeholder={t('common.search')} style={{ width: 240 }} value={q} onChange={(e) => setQ(e.target.value)} /></div>
      {list.loading ? <SkeletonList /> : list.error ? <ErrorState error={list.error} onRetry={list.reload} /> : list.data!.length === 0 ? <Empty icon="users" title={t('b.customers.empty')} /> :
        <div className="card tablewrap"><table className="table"><thead><tr><th>{t('b.client')}</th><th>{t('b.customers.visits')}</th><th>{t('b.customers.spent')}</th><th>{t('b.customers.last')}</th><th>{t('b.customers.note')}</th></tr></thead>
          <tbody>{list.data!.map((c) => <tr key={c.id}><td><b>{c.fullName}</b><div className="muted small">{c.phone}</div></td><td>{c.completedCount}/{c.bookingsCount}</td><td>{money(c.spentMinor, biz.currency ?? 'KZT', locale)}</td><td>{fmtDate(c.lastVisit, locale, biz.timezone, { day: 'numeric', month: 'short', year: 'numeric' })}</td>
            <td><button className="btn-ghost small" onClick={() => setNote({ ...c, text: c.note ?? '' })}>{c.note ? c.note.slice(0, 30) : t('common.edit')}</button></td></tr>)}</tbody></table></div>}
      {note && <Sheet title={note.fullName} onClose={() => setNote(null)}><Field label={t('b.customers.note')} id="cn"><Textarea id="cn" value={note.text} maxLength={1000} onChange={(e) => setNote({ ...note, text: e.target.value })} /></Field><Button onClick={async () => { try { await put(`/api/biz/${bid}/customers/${note.id}/note`, { note: note.text }); setNote(null); toast(t('b.saved')); list.reload() } catch (e) { fail(e) } }}>{t('common.save')}</Button></Sheet>}
    </>
  )
}

// ---------------------------------------------------------------- analytics
export function AnalyticsPage() {
  const { t, locale } = useI18n(); const { bid, biz } = useBiz(); const tz = biz.timezone ?? 'Asia/Almaty'
  const [to, setTo] = useState(ymdInZone(0, tz)); const [from, setFrom] = useState(ymdInZone(-29, tz))
  const q = useLoad(() => get<any>(`/api/biz/${bid}/analytics?from=${from}&to=${to}`), [bid, from, to])
  const d = q.data; const cur = d?.currency ?? 'KZT'; const max = d ? Math.max(1, ...d.byDay.map((x: any) => x.bookings)) : 1
  return (
    <>
      <div className="row between wrap"><h1>{t('b.nav.analytics')}</h1><div className="row"><Input type="date" aria-label={t('b.staff.from')} value={from} onChange={(e) => setFrom(e.target.value)} style={{ width: 160 }} /><Input type="date" aria-label={t('b.staff.to')} value={to} onChange={(e) => setTo(e.target.value)} style={{ width: 160 }} /></div></div>
      {q.loading ? <SkeletonList n={3} h={110} /> : q.error ? <ErrorState error={q.error} onRetry={q.reload} /> : <>
        <div className="grid g4">
          <div className="card stat"><b>{d.totalBookings}</b><span>{t('b.analytics.bookings')}</span></div><div className="card stat"><b>{money(d.revenueMinor, cur, locale)}</b><span>{t('b.analytics.revenue')}</span></div>
          <div className="card stat"><b>{money(d.averageBookingValueMinor, cur, locale)}</b><span>{t('b.analytics.avg')}</span></div><div className="card stat"><b>{d.cancellationRate}%</b><span>{t('b.analytics.cancelRate')}</span></div>
          <div className="card stat"><b>{d.repeatCustomerRate}%</b><span>{t('b.analytics.repeat')}</span></div></div>
        <section className="card card-pad"><h2>{t('b.analytics.perDay')}</h2>{d.byDay.length === 0 ? <p className="muted mt2">{t('b.analytics.none')}</p> : <div className="bars mt3" role="img" aria-label={t('b.analytics.perDay')}>{d.byDay.map((x: any) => <i key={x.day} title={`${x.day}: ${x.bookings}`} style={{ height: `${(x.bookings / max) * 100}%` }} />)}</div>}</section>
        <div className="grid g2">
          <section className="card card-pad"><h2>{t('b.analytics.popular')}</h2>{d.popularServices.length === 0 ? <p className="muted mt2">{t('b.analytics.none')}</p> : <table className="table mt2"><tbody>{d.popularServices.map((s: any) => <tr key={s.name}><td>{s.name}</td><td>{s.bookings}</td><td>{money(s.revenueMinor, cur, locale)}</td></tr>)}</tbody></table>}</section>
          <section className="card card-pad"><h2>{t('b.analytics.utilization')}</h2><table className="table mt2"><tbody>{d.staffUtilization.map((s: any) => <tr key={s.professionalId}><td>{s.name}</td><td style={{ width: '45%' }}><div className="dist-bar"><i style={{ width: `${Math.min(100, s.utilization)}%` }} /></div></td><td>{s.utilization}%</td></tr>)}</tbody></table></section>
        </div>
        <section className="card card-pad"><h2>{t('b.analytics.trend')}</h2>{d.ratingTrend.length === 0 ? <p className="muted mt2">{t('b.analytics.none')}</p> : <table className="table mt2"><tbody>{d.ratingTrend.map((r: any) => <tr key={r.month}><td>{r.month}</td><td>★ {Number(r.average).toFixed(2)}</td><td>{r.reviews}</td></tr>)}</tbody></table>}</section>
      </>}
    </>
  )
}

// ---------------------------------------------------------------- reviews
export function ReviewsPage() {
  const { t, locale } = useI18n(); const { bid } = useBiz(); const toast = useToast(); const fail = useErr()
  const q = useLoad(() => get<any[]>(`/api/biz/${bid}/reviews`), [bid]); const [reply, setReply] = useState<Record<number, string>>({})
  return (
    <>
      <h1>{t('b.nav.reviews')}</h1>
      {q.loading ? <SkeletonList /> : q.error ? <ErrorState error={q.error} onRetry={q.reload} /> : q.data!.length === 0 ? <Empty icon="star" title={t('common.noReviews')} /> : <div className="col">{q.data!.map((r) => (
        <div key={r.id} className="card card-pad col">
          <div className="row between"><b>{r.author} · {'★'.repeat(r.rating)}</b><span className="muted tiny">{fmtDate(r.createdAt, locale, undefined, { day: 'numeric', month: 'short', year: 'numeric' })}{r.status === 'HIDDEN' ? ' · hidden' : ''}</span></div>
          <div className="muted small">{r.serviceName} · {r.professionalName}</div>{r.body && <p>{r.body}</p>}
          {r.reply ? <div className="alert info small"><b>{t('review.reply')}:</b> {r.reply}</div> : <div className="row"><Input aria-label={t('b.reviews.reply')} placeholder={t('b.reviews.reply')} value={reply[r.id] ?? ''} onChange={(e) => setReply({ ...reply, [r.id]: e.target.value })} /><Button small disabled={!reply[r.id]} onClick={async () => { try { await post(`/api/reviews/${r.id}/reply`, { reply: reply[r.id] }); toast(t('b.saved')); q.reload() } catch (e) { fail(e) } }}>{t('b.reviews.send')}</Button></div>}
        </div>))}</div>}
    </>
  )
}

// ---------------------------------------------------------------- promotions
export function PromosPage() {
  const { t, locale } = useI18n(); const { bid } = useBiz(); const toast = useToast(); const fail = useErr()
  const q = useLoad(() => get<any[]>(`/api/biz/${bid}/promotions`), [bid]); const [f, setF] = useState({ code: '', percentOff: '10', maxRedemptions: '' })
  const add = async (e: FormEvent) => { e.preventDefault(); try { await post(`/api/biz/${bid}/promotions`, { code: f.code, percentOff: Number(f.percentOff), maxRedemptions: f.maxRedemptions ? Number(f.maxRedemptions) : undefined }); setF({ code: '', percentOff: '10', maxRedemptions: '' }); toast(t('b.saved')); q.reload() } catch (x) { fail(x) } }
  return (
    <>
      <h1>{t('b.nav.promos')}</h1>
      <form className="card card-pad grid g4" onSubmit={add} style={{ alignItems: 'end' }}>
        <Field label={t('b.promo.code')} id="pc"><Input id="pc" required value={f.code} pattern="[A-Za-z0-9_\-]{3,40}" onChange={(e) => setF({ ...f, code: e.target.value.toUpperCase() })} /></Field>
        <Field label={t('b.promo.percent')} id="pp"><Input id="pp" type="number" min={1} max={100} required value={f.percentOff} onChange={(e) => setF({ ...f, percentOff: e.target.value })} /></Field>
        <Field label={t('b.promo.max')} id="pm"><Input id="pm" type="number" min={1} value={f.maxRedemptions} onChange={(e) => setF({ ...f, maxRedemptions: e.target.value })} /></Field>
        <Button type="submit">{t('b.promo.new')}</Button>
      </form>
      {q.loading ? <SkeletonList n={2} /> : q.error ? <ErrorState error={q.error} onRetry={q.reload} /> : q.data!.length === 0 ? <Empty icon="sparkles" title={t('b.empty')} /> :
        <div className="card tablewrap"><table className="table"><thead><tr><th>{t('b.promo.code')}</th><th>%</th><th>{t('b.promo.used')}</th><th>{t('common.status')}</th></tr></thead><tbody>{q.data!.map((p) => <tr key={p.id}><td><b>{p.code}</b></td><td>{p.percentOff ?? money(p.amountOffMinor, p.currency, locale)}</td><td>{p.redemptions}{p.maxRedemptions ? `/${p.maxRedemptions}` : ''}</td>
          <td><Button small variant="ghost" onClick={async () => { await post(`/api/biz/${bid}/promotions/${p.id}/active?value=${!p.active}`); q.reload() }}>{p.active ? '✓ on' : 'off'}</Button></td></tr>)}</tbody></table></div>}
    </>
  )
}

// ---------------------------------------------------------------- settings
export function SettingsPage() {
  const { t } = useI18n(); const { bid, biz, reload } = useBiz(); const toast = useToast(); const fail = useErr()
  const [f, setF] = useState<any>(null); const [hours, setHours] = useState<any[]>([]); const [closure, setClosure] = useState('')
  useEffect(() => { if (biz.id) { setF({ ...biz }); setHours([1, 2, 3, 4, 5, 6, 7].map((w) => { const h = biz.hours?.find((x: any) => x.weekday === w); return { weekday: w, on: !!h, openTime: h?.openTime ?? '10:00', closeTime: h?.closeTime ?? '20:00' } })) } }, [biz.id, biz.hours?.length, biz.name]) // eslint-disable-line
  const plans = useLoad(() => Promise.resolve(['FREE', 'PRO', 'PREMIUM']), [])
  if (!f) return <Skeleton h={300} />
  const set = (k: string, v: any) => setF({ ...f, [k]: v })
  const save = async (e: FormEvent) => { e.preventDefault(); try { await put(`/api/biz/${bid}`, { name: f.name, description: f.description, address: f.address, phone: f.phone, lat: f.lat, lng: f.lng, autoConfirm: !!f.autoConfirm, minLeadMinutes: Number(f.minLeadMinutes), maxAdvanceDays: Number(f.maxAdvanceDays), bufferMinutes: Number(f.bufferMinutes), cancellationHours: Number(f.cancellationHours) }); toast(t('b.saved')); reload() } catch (x) { fail(x) } }
  const saveHours = async () => { try { await put(`/api/biz/${bid}/hours`, hours.filter((h) => h.on).map((h) => ({ weekday: h.weekday, openTime: h.openTime, closeTime: h.closeTime }))); toast(t('b.saved')); reload() } catch (x) { fail(x) } }
  return (
    <>
      <h1>{t('b.nav.settings')}</h1>
      <form className="card card-pad col" onSubmit={save}>
        <h2>{t('b.settings.profile')}</h2>
        <Field label={t('common.name')} id="sname"><Input id="sname" required value={f.name} onChange={(e) => set('name', e.target.value)} /></Field>
        <div className="grid g2"><Field label={t('biz.address')} id="sadr"><Input id="sadr" required value={f.address} onChange={(e) => set('address', e.target.value)} /></Field><Field label={t('auth.phone')} id="sph"><Input id="sph" value={f.phone ?? ''} onChange={(e) => set('phone', e.target.value)} /></Field></div>
        <Field label={t('b.field.description')} id="sdesc2"><Textarea id="sdesc2" value={f.description ?? ''} onChange={(e) => set('description', e.target.value)} /></Field>
        <h2>{t('b.settings.rules')}</h2>
        <label className="row"><input type="checkbox" style={{ width: 20, height: 20 }} checked={!!f.autoConfirm} onChange={(e) => set('autoConfirm', e.target.checked)} />{t('b.settings.autoConfirm')}</label>
        <div className="grid g2">
          <Field label={t('b.settings.lead')} id="sl"><Input id="sl" type="number" min={0} value={f.minLeadMinutes} onChange={(e) => set('minLeadMinutes', e.target.value)} /></Field>
          <Field label={t('b.settings.advance')} id="sa"><Input id="sa" type="number" min={1} max={365} value={f.maxAdvanceDays} onChange={(e) => set('maxAdvanceDays', e.target.value)} /></Field>
          <Field label={t('b.settings.buffer')} id="sb"><Input id="sb" type="number" min={0} max={120} value={f.bufferMinutes} onChange={(e) => set('bufferMinutes', e.target.value)} /></Field>
          <Field label={t('b.settings.cancelH')} id="sc2"><Input id="sc2" type="number" min={0} value={f.cancellationHours} onChange={(e) => set('cancellationHours', e.target.value)} /></Field></div>
        <Button type="submit" style={{ alignSelf: 'flex-start' }}>{t('common.save')}</Button>
      </form>
      <section className="card card-pad col"><h2>{t('b.settings.hours')}</h2>
        {hours.map((h, i) => <div key={h.weekday} className="row wrap"><label className="row" style={{ width: 170 }}><input type="checkbox" style={{ width: 20, height: 20 }} checked={h.on} onChange={(e) => setHours(hours.map((x, j) => (j === i ? { ...x, on: e.target.checked } : x)))} />{t(`wd.${h.weekday}` as any)}</label>
          {h.on ? <><Input type="time" aria-label="open" style={{ width: 130 }} value={h.openTime} onChange={(e) => setHours(hours.map((x, j) => (j === i ? { ...x, openTime: e.target.value } : x)))} /><Input type="time" aria-label="close" style={{ width: 130 }} value={h.closeTime} onChange={(e) => setHours(hours.map((x, j) => (j === i ? { ...x, closeTime: e.target.value } : x)))} /></> : <span className="muted small">{t('biz.closed')}</span>}</div>)}
        <Button variant="secondary" onClick={saveHours} style={{ alignSelf: 'flex-start' }}>{t('common.save')}</Button></section>
      <section className="card card-pad col"><h2>{t('b.settings.closures')}</h2>
        <div className="row"><Input type="date" aria-label={t('b.settings.addClosure')} value={closure} onChange={(e) => setClosure(e.target.value)} style={{ width: 170 }} /><Button small disabled={!closure} onClick={async () => { try { await post(`/api/biz/${bid}/closures`, { date: closure }); setClosure(''); reload() } catch (x) { fail(x) } }}>{t('b.settings.addClosure')}</Button></div>
        <div className="row wrap gap2">{biz.closures?.map((c: any) => <span key={c.id} className="tag">{c.closedOn} <button aria-label="remove" onClick={async () => { await del(`/api/biz/${bid}/closures/${c.id}`); reload() }}>×</button></span>)}</div></section>
      <section className="card card-pad col"><h2>{t('b.settings.subscription')}</h2>
        <div className="row wrap">{plans.data?.map((p) => <Button key={p} small variant={biz.subscription?.code === p ? 'primary' : 'soft'} onClick={async () => { try { await put(`/api/biz/${bid}/subscription`, { planCode: p }); reload() } catch (x) { fail(x) } }}>{p}</Button>)}</div>
        <div className="alert info small">{t('b.settings.billing')}</div></section>
    </>
  )
}

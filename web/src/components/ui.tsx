import { createContext, useCallback, useContext, useEffect, useRef, useState, type ButtonHTMLAttributes, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react'
import { Link } from 'react-router-dom'
import { Icon } from './Icon'
import { useI18n, catName } from '../i18n'
import { mediaUrl, ApiError } from '../lib/api'
import { money } from '../lib/format'

export function Button({ variant = 'primary', block, small, loading, children, ...p }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: 'primary' | 'secondary' | 'soft' | 'ghost' | 'danger'; block?: boolean; small?: boolean; loading?: boolean }) {
  return (
    <button {...p} disabled={p.disabled || loading} className={`btn btn-${variant}${block ? ' btn-block' : ''}${small ? ' btn-sm' : ''} ${p.className ?? ''}`}>
      {loading ? '…' : children}
    </button>
  )
}

export function Field({ label, error, children, id }: { label?: string; error?: string; children: ReactNode; id?: string }) {
  return (
    <div className="field">
      {label && <label htmlFor={id}>{label}</label>}
      {children}
      {error && <span className="field-error" role="alert">{error}</span>}
    </div>
  )
}
export const Input = (p: InputHTMLAttributes<HTMLInputElement>) => <input {...p} className={`input ${p.className ?? ''}`} />
export const Select = (p: SelectHTMLAttributes<HTMLSelectElement>) => <select {...p} className={`select ${p.className ?? ''}`} />
export const Textarea = (p: TextareaHTMLAttributes<HTMLTextAreaElement>) => <textarea {...p} className={`textarea ${p.className ?? ''}`} />

export function Stars({ value, count }: { value: number; count?: number }) {
  const { t } = useI18n()
  return (
    <span className="stars" aria-label={`${value} / 5`}>
      <Icon name="star" /> {count === 0 ? <span className="muted small">{t('common.noReviews')}</span> : <>{Number(value).toFixed(1)}{count !== undefined && <span className="muted"> ({count})</span>}</>}
    </span>
  )
}

export function Price({ minor, currency, from }: { minor: number; currency: string; from?: boolean }) {
  const { locale, t } = useI18n()
  return <span className="price">{from ? t('common.from') + ' ' : ''}{money(minor, currency.trim(), locale)}</span>
}

export function Avatar({ name, imageKey, size = 48 }: { name: string; imageKey?: string | null; size?: number }) {
  const [bad, setBad] = useState(false)
  const initials = name.split(/\s+/).map((w) => w[0]).slice(0, 2).join('').toUpperCase()
  const url = mediaUrl(imageKey)
  const hue = [...name].reduce((a, c) => a + c.charCodeAt(0), 0) % 40
  return (
    <span className="avatar" style={{ width: size, height: size, fontSize: size / 3, background: url && !bad ? undefined : `linear-gradient(135deg, hsl(${320 + hue} 75% 70%), hsl(${300 + hue} 60% 50%))` }} aria-hidden="true">
      {url && !bad ? <img src={url} alt="" loading="lazy" onError={() => setBad(true)} /> : initials}
    </span>
  )
}

/** Photo with graceful placeholder (category icon on gradient) for missing/broken images. */
export function Photo({ imageKey, icon = 'sparkles', alt = '', style, className = '' }: { imageKey?: string | null; icon?: string; alt?: string; style?: React.CSSProperties; className?: string }) {
  const [bad, setBad] = useState(false)
  const url = mediaUrl(imageKey)
  return (
    <div className={`photo ${className}`} style={style} role={url && !bad ? 'img' : undefined} aria-label={alt || undefined}>
      {url && !bad ? <img src={url} alt={alt} loading="lazy" onError={() => setBad(true)} /> : <Icon name={icon} size={34} />}
    </div>
  )
}

export function Skeleton({ h = 80, w = '100%', r }: { h?: number; w?: number | string; r?: number }) {
  return <div className="skeleton" style={{ height: h, width: w, borderRadius: r }} aria-hidden="true" />
}
export function SkeletonList({ n = 3, h = 92 }: { n?: number; h?: number }) {
  return <div className="col" role="status" aria-label="loading">{Array.from({ length: n }, (_, i) => <Skeleton key={i} h={h} />)}</div>
}

export function Empty({ icon = 'sparkles', title, text, children }: { icon?: string; title: string; text?: string; children?: ReactNode }) {
  return <div className="empty"><Icon name={icon} size={40} /><h3>{title}</h3>{text && <p>{text}</p>}{children}</div>
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const { t, err } = useI18n()
  const code = error instanceof ApiError ? error.code : 'generic'
  return (
    <div className="empty" role="alert">
      <Icon name="shield" size={40} /><h3>{t('error.title')}</h3><p>{err(code)}</p>
      {onRetry && <Button variant="secondary" small onClick={onRetry}>{t('common.retry')}</Button>}
    </div>
  )
}

export function Heart({ on, onToggle, label }: { on: boolean; onToggle: () => void; label: string }) {
  return (
    <button className={`heart${on ? ' on' : ''}`} aria-pressed={on} aria-label={label} onClick={(e) => { e.preventDefault(); e.stopPropagation(); onToggle() }}>
      <Icon name="heart" size={20} />
    </button>
  )
}

export function DemoTag({ show }: { show?: boolean }) {
  const { t } = useI18n()
  return show ? <span className="tag demo">{t('common.demo')}</span> : null
}

export function Sheet({ title, onClose, children, center }: { title?: string; onClose: () => void; children: ReactNode; center?: boolean }) {
  const ref = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const prev = document.activeElement as HTMLElement | null
    ref.current?.focus()
    const k = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose() }
    document.addEventListener('keydown', k)
    return () => { document.removeEventListener('keydown', k); prev?.focus?.() }
  }, [onClose])
  return (
    <div className={`overlay${center ? ' center' : ''}`} onMouseDown={(e) => { if (e.target === e.currentTarget) onClose() }}>
      <div className={`sheet${center ? ' center' : ''}`} role="dialog" aria-modal="true" aria-label={title} tabIndex={-1} ref={ref}>
        {title && <div className="row between"><h2>{title}</h2><button className="iconbtn" onClick={onClose} aria-label="close"><Icon name="x" size={20} /></button></div>}
        {children}
      </div>
    </div>
  )
}

export function Confirm({ title, text, confirmLabel, danger, onConfirm, onClose, children }: { title: string; text?: string; confirmLabel: string; danger?: boolean; onConfirm: () => Promise<void> | void; onClose: () => void; children?: ReactNode }) {
  const { t } = useI18n()
  const [busy, setBusy] = useState(false)
  return (
    <Sheet title={title} onClose={onClose} center>
      {text && <p className="muted">{text}</p>}
      {children}
      <div className="row">
        <Button variant="soft" block onClick={onClose}>{t('common.cancel')}</Button>
        <Button variant={danger ? 'danger' : 'primary'} block loading={busy} onClick={async () => { setBusy(true); try { await onConfirm() } finally { setBusy(false) } }}>{confirmLabel}</Button>
      </div>
    </Sheet>
  )
}

// ---- toast
const ToastCtx = createContext<(m: string) => void>(() => {})
export const useToast = () => useContext(ToastCtx)
export function ToastProvider({ children }: { children: ReactNode }) {
  const [msg, setMsg] = useState<string | null>(null)
  const show = useCallback((m: string) => { setMsg(m); setTimeout(() => setMsg(null), 3200) }, [])
  return <ToastCtx.Provider value={show}>{children}{msg && <div className="toast" role="status">{msg}</div>}</ToastCtx.Provider>
}

// ---- data loading hook (loading / error / retry)
export function useLoad<T>(fn: () => Promise<T>, deps: unknown[]) {
  const [state, setState] = useState<{ data?: T; error?: unknown; loading: boolean }>({ loading: true })
  const [n, setN] = useState(0)
  useEffect(() => {
    let alive = true
    setState((s) => ({ ...s, loading: true, error: undefined }))
    fn().then((data) => alive && setState({ data, loading: false })).catch((error) => alive && setState({ error, loading: false }))
    return () => { alive = false }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, n])
  return { ...state, reload: () => setN((x) => x + 1) }
}

export function TopBar({ title, back, right }: { title: string; back?: string | boolean; right?: ReactNode }) {
  const { t } = useI18n()
  return (
    <header className="topbar">
      {back && <Link to={typeof back === 'string' ? back : '..'} relative="path" className="iconbtn" aria-label={t('common.back')} onClick={(e) => { if (back === true) { e.preventDefault(); history.back() } }}><Icon name="chevronL" size={20} /></Link>}
      <h1>{title}</h1>{right}
    </header>
  )
}

export function RatingInput({ value, onChange }: { value: number; onChange: (n: number) => void }) {
  return (
    <div className="stars-input" role="radiogroup" aria-label="rating">
      {[1, 2, 3, 4, 5].map((n) => <button key={n} type="button" role="radio" aria-checked={value === n} aria-label={String(n)} className={n <= value ? 'on' : ''} onClick={() => onChange(n)}><Icon name="star" /></button>)}
    </div>
  )
}

export { catName }

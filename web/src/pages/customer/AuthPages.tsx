import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Logo } from '../../components/Icon'
import { Button, Field, Input, TopBar, useToast } from '../../components/ui'
import { useI18n } from '../../i18n'
import { ApiError, post } from '../../lib/api'
import { useApp } from '../../lib/app'

function homeFor(role: string) { return role === 'PLATFORM_ADMIN' ? '/admin' : role === 'BUSINESS_OWNER' ? '/business' : role === 'PROFESSIONAL' ? '/pro' : '/' }

export function Login() {
  const { t, err } = useI18n(); const { login } = useApp(); const nav = useNavigate(); const [q] = useSearchParams()
  const [email, setEmail] = useState(''); const [password, setPassword] = useState(''); const [error, setError] = useState(''); const [busy, setBusy] = useState(false)
  const submit = async (e: FormEvent) => {
    e.preventDefault(); setBusy(true); setError('')
    try { const u = await login(email, password); nav(q.get('next') && u.role === 'CUSTOMER' ? q.get('next')! : homeFor(u.role), { replace: true }) }
    catch (x) { setError(err(x instanceof ApiError ? x.code : undefined)) } finally { setBusy(false) }
  }
  return (
    <div className="page nopad-bottom" style={{ justifyContent: 'center' }}>
      <div className="center"><Logo size={44} /></div>
      <h1 className="center">{t('auth.login')}</h1>
      <form className="col" onSubmit={submit} noValidate>
        <Field label={t('auth.email')} id="email"><Input id="email" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required /></Field>
        <Field label={t('auth.password')} id="pw"><Input id="pw" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required /></Field>
        {error && <div className="alert error" role="alert">{error}</div>}
        <Button type="submit" block loading={busy}>{t('auth.login')}</Button>
        <Link to="/reset" className="center small" style={{ color: 'var(--primary)' }}>{t('auth.forgot')}</Link>
      </form>
      <p className="center small muted">{t('auth.noAccount')} <Link to={'/register' + (q.get('next') ? '?next=' + encodeURIComponent(q.get('next')!) : '')} className="bold" style={{ color: 'var(--primary)' }}>{t('auth.register')}</Link></p>
      <p className="alert info tiny">{t('auth.demoHint')}</p>
      <Link to="/" className="center small muted">{t('ob.guest')}</Link>
    </div>
  )
}

export function Register() {
  const { t, err } = useI18n(); const { register } = useApp(); const nav = useNavigate(); const [q] = useSearchParams()
  const [f, setF] = useState({ fullName: '', email: '', phone: '', password: '' }); const [business, setBusiness] = useState(false)
  const [error, setError] = useState(''); const [fields, setFields] = useState<Record<string, string>>({}); const [busy, setBusy] = useState(false)
  const set = (k: keyof typeof f) => (e: React.ChangeEvent<HTMLInputElement>) => setF({ ...f, [k]: e.target.value })
  const submit = async (e: FormEvent) => {
    e.preventDefault(); setBusy(true); setError(''); setFields({})
    try { const u = await register({ ...f, phone: f.phone || undefined, role: business ? 'BUSINESS_OWNER' : 'CUSTOMER' }); nav(q.get('next') && u.role === 'CUSTOMER' ? q.get('next')! : homeFor(u.role), { replace: true }) }
    catch (x) { if (x instanceof ApiError) { setError(err(x.code)); if (x.details && typeof x.details === 'object') setFields(x.details) } else setError(err()) } finally { setBusy(false) }
  }
  return (
    <div className="page nopad-bottom">
      <div className="center"><Logo size={44} /></div>
      <h1 className="center">{t('auth.register')}</h1>
      <form className="col" onSubmit={submit} noValidate>
        <Field label={t('auth.fullName')} id="n" error={fields.fullName}><Input id="n" autoComplete="name" value={f.fullName} onChange={set('fullName')} aria-invalid={!!fields.fullName} required /></Field>
        <Field label={t('auth.email')} id="e" error={fields.email}><Input id="e" type="email" autoComplete="email" value={f.email} onChange={set('email')} aria-invalid={!!fields.email} required /></Field>
        <Field label={`${t('auth.phone')} (${t('common.optional')})`} id="p"><Input id="p" type="tel" autoComplete="tel" value={f.phone} onChange={set('phone')} /></Field>
        <Field label={t('auth.password')} id="pw"><Input id="pw" type="password" autoComplete="new-password" value={f.password} onChange={set('password')} required /><span className="tiny muted">{t('auth.passwordHint')}</span></Field>
        <label className="row small"><input type="checkbox" checked={business} onChange={(e) => setBusiness(e.target.checked)} style={{ width: 20, height: 20 }} /> {t('auth.asBusiness')}</label>
        {error && <div className="alert error" role="alert">{error}</div>}
        <Button type="submit" block loading={busy}>{t('auth.register')}</Button>
      </form>
      <p className="center small muted">{t('auth.hasAccount')} <Link to="/login" className="bold" style={{ color: 'var(--primary)' }}>{t('auth.login')}</Link></p>
    </div>
  )
}

export function Reset() {
  const { t, err } = useI18n(); const toast = useToast(); const nav = useNavigate()
  const [email, setEmail] = useState(''); const [sent, setSent] = useState(false); const [token, setToken] = useState(''); const [pw, setPw] = useState(''); const [error, setError] = useState('')
  const request = async (e: FormEvent) => { e.preventDefault(); try { await post('/api/auth/password-reset/request', { email }); setSent(true) } catch (x) { setError(err(x instanceof ApiError ? x.code : undefined)) } }
  const confirm = async (e: FormEvent) => { e.preventDefault(); setError(''); try { await post('/api/auth/password-reset/confirm', { token, password: pw }); toast(t('auth.resetDone')); nav('/login') } catch (x) { setError(err(x instanceof ApiError ? x.code : undefined)) } }
  return (
    <>
      <TopBar title={t('auth.resetTitle')} back="/login" />
      <div className="page">
        <form className="col" onSubmit={request}>
          <Field label={t('auth.email')} id="re"><Input id="re" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required /></Field>
          <Button type="submit" block>{t('auth.resetSend')}</Button>
        </form>
        {sent && <div className="alert info">{t('auth.resetSent')}</div>}
        <form className="col" onSubmit={confirm}>
          <Field label={t('auth.token')} id="tk"><Input id="tk" value={token} onChange={(e) => setToken(e.target.value)} required /></Field>
          <Field label={t('auth.newPassword')} id="np"><Input id="np" type="password" autoComplete="new-password" value={pw} onChange={(e) => setPw(e.target.value)} required /></Field>
          {error && <div className="alert error" role="alert">{error}</div>}
          <Button type="submit" variant="secondary" block>{t('common.confirm')}</Button>
        </form>
      </div>
    </>
  )
}

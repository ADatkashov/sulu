import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Icon, Logo } from '../../components/Icon'
import { Button, Select } from '../../components/ui'
import { LOCALES, useI18n, type Locale } from '../../i18n'
import { useApp } from '../../lib/app'

export const ONBOARDED = 'sulu.onboarded'
const icons = ['pin', 'star', 'clock', 'calendar']

export function Onboarding() {
  const { t, locale, setLocale } = useI18n()
  const { config, city, setCity } = useApp()
  const nav = useNavigate()
  const [step, setStep] = useState(0)
  const last = step === 3
  const finish = (to: string) => { try { localStorage.setItem(ONBOARDED, '1') } catch { /* ignore */ } nav(to, { replace: true }) }
  const cities = Array.from(new Set([config?.defaultCity ?? 'Алматы', ...(config?.cities ?? [])]))
  return (
    <div className="onboard">
      <div className="row between"><Logo />
        <button className="btn-ghost small" onClick={() => finish('/')}>{t('common.skip')}</button></div>
      <div className="row gap2" role="group" aria-label={t('ob.chooseLanguage')}>
        {LOCALES.map((l) => <button key={l.code} className={`chip${locale === l.code ? ' active' : ''}`} aria-pressed={locale === l.code} onClick={() => setLocale(l.code as Locale)}>{l.label}</button>)}
      </div>
      <div className="ob-art"><div className="blob"><Icon name={icons[step]} size={90} /></div></div>
      <div className="col" style={{ gap: 8, textAlign: 'center' }}>
        <h1>{t(`ob.${step + 1}.title` as any)}</h1>
        <p className="muted">{t(`ob.${step + 1}.text` as any)}</p>
      </div>
      <div className="stepper" aria-hidden="true">{[0, 1, 2, 3].map((i) => <i key={i} className={i <= step ? 'on' : ''} />)}</div>
      {last && (
        <div className="col" style={{ gap: 6 }}>
          <label className="small bold muted" htmlFor="ob-city">{t('ob.chooseCity')}</label>
          <Select id="ob-city" value={city} onChange={(e) => setCity(e.target.value)}>{cities.map((c) => <option key={c}>{c}</option>)}</Select>
          <p className="tiny muted">{t('ob.noLocation')}</p>
        </div>
      )}
      <div className="col">
        {last ? <>
          <Button block onClick={() => finish('/')}>{t('ob.guest')}</Button>
          <Button block variant="secondary" onClick={() => finish('/login')}>{t('ob.signin')}</Button>
        </> : <Button block onClick={() => setStep(step + 1)}>{t('common.continue')}</Button>}
      </div>
    </div>
  )
}

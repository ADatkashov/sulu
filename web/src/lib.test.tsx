import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { describe, expect, it, vi } from 'vitest'
import { I18nProvider, translate } from './i18n'
import { en } from './i18n/en'
import { kk } from './i18n/kk'
import { ru } from './i18n/ru'
import { ApiError, qs } from './lib/api'
import { money, ymdInZone } from './lib/format'
import { ErrorState, Confirm } from './components/ui'
import { SalonCard } from './components/cards'
import { AppProvider } from './lib/app'

describe('i18n', () => {
  it('translates and falls back kk → ru → en', () => {
    expect(translate('ru', 'nav.home')).toBe('Главная')
    expect(translate('kk', 'nav.home')).toBe('Басты')
    expect(translate('kk', 'b.nav.overview')).toBe(ru['b.nav.overview']) // not in kk → Russian
    expect(translate('en', 'svc.policyText', { h: 24 })).toContain('24')
  })
  it('every customer-facing key exists in all three languages', () => {
    const business = (k: string) => k.startsWith('b.') || k.startsWith('a.') || k.startsWith('p.')
    for (const k of Object.keys(en).filter((k) => !business(k))) {
      expect(ru[k as keyof typeof ru], `ru ${k}`).toBeTruthy()
      expect(kk[k as keyof typeof kk], `kk ${k}`).toBeTruthy()
    }
  })
})

describe('format helpers', () => {
  it('formats minor units without floating-point drift', () => {
    expect(money(650000, 'KZT', 'ru').replace(/\s/g, ' ')).toContain('6')
    expect(money(1999, 'USD', 'en')).toContain('19.99')
  })
  it('computes dates in the business time zone', () => {
    // 2026-10-31T22:00Z is already 11-01 in Almaty (UTC+5) but still 10-31 in New York
    const at = new Date('2026-10-31T22:00:00Z')
    expect(ymdInZone(0, 'Asia/Almaty', at)).toBe('2026-11-01')
    expect(ymdInZone(0, 'America/New_York', at)).toBe('2026-10-31')
  })
  it('builds query strings skipping empty values', () => { expect(qs({ a: 1, b: '', c: undefined, d: 'x y' })).toBe('?a=1&d=x+y') })
})

describe('components', () => {
  it('shows a localized, actionable error state', async () => {
    const retry = vi.fn()
    render(<I18nProvider defaultLocale="en"><ErrorState error={new ApiError(409, 'SLOT_UNAVAILABLE', 'x')} onRetry={retry} /></I18nProvider>)
    expect(screen.getByRole('alert')).toHaveTextContent('This time is no longer available.')
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }))
    expect(retry).toHaveBeenCalled()
  })
  it('confirm dialog is accessible, closes on Escape and prevents double submit', async () => {
    const onConfirm = vi.fn(() => new Promise<void>((r) => setTimeout(r, 50))); const onClose = vi.fn()
    render(<I18nProvider defaultLocale="en"><Confirm title="Sure?" confirmLabel="Yes do it" onConfirm={onConfirm} onClose={onClose} /></I18nProvider>)
    expect(screen.getByRole('dialog', { name: 'Sure?' })).toBeInTheDocument()
    const btn = screen.getByRole('button', { name: 'Yes do it' })
    await userEvent.click(btn); await userEvent.click(btn)
    expect(onConfirm).toHaveBeenCalledTimes(1)
    await userEvent.keyboard('{Escape}')
    expect(onClose).toHaveBeenCalled()
  })
  it('salon card links to the salon and labels demo data', () => {
    render(<MemoryRouter><I18nProvider defaultLocale="ru"><AppProvider><SalonCard b={{ id: 7, name: 'Aru', ratingAvg: 4.5, ratingCount: 3, address: 'x', isDemo: true, minPrice: 400000, currency: 'KZT' }} /></AppProvider></I18nProvider></MemoryRouter>)
    expect(screen.getByRole('link')).toHaveAttribute('href', '/salon/7')
    expect(screen.getByText('ДЕМО')).toBeInTheDocument()
  })
})

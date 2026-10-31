import type { Locale } from '../i18n'

const loc = (l: Locale) => (l === 'kk' ? 'kk-KZ' : l === 'ru' ? 'ru-RU' : 'en-GB')

/** Money is integer minor units + currency; never floats in logic, Intl only for display. */
export function money(minor: number, currency: string, l: Locale): string {
  const v = minor / 100
  try {
    return new Intl.NumberFormat(loc(l), { style: 'currency', currency, maximumFractionDigits: v % 1 === 0 ? 0 : 2 }).format(v)
  } catch { return `${v} ${currency}` }
}
export const fmtDate = (iso: string | Date, l: Locale, tz?: string, opts: Intl.DateTimeFormatOptions = { day: 'numeric', month: 'long', weekday: 'short' }) =>
  new Intl.DateTimeFormat(loc(l), { ...opts, timeZone: tz }).format(typeof iso === 'string' ? new Date(iso) : iso)
export const fmtTime = (iso: string | Date, l: Locale, tz?: string) =>
  new Intl.DateTimeFormat(loc(l), { hour: '2-digit', minute: '2-digit', hour12: false, timeZone: tz }).format(typeof iso === 'string' ? new Date(iso) : iso)
export const fmtDateTime = (iso: string, l: Locale, tz?: string) => `${fmtDate(iso, l, tz)}, ${fmtTime(iso, l, tz)}`

/** yyyy-MM-dd of "today + n days" in a given IANA zone. */
export function ymdInZone(daysAhead: number, tz: string, now = new Date()): string {
  const d = new Date(now.getTime() + daysAhead * 86400000)
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: tz, year: 'numeric', month: '2-digit', day: '2-digit' }).format(d)
  return parts
}
export const ymdToDate = (ymd: string) => new Date(ymd + 'T12:00:00Z')
export const km = (v?: number | null) => (v == null ? '' : v < 1 ? `${Math.round(v * 1000)} m` : `${v.toFixed(1)} km`)

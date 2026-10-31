import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { en, type Key } from './en'
import { ru } from './ru'
import { kk } from './kk'

export type Locale = 'kk' | 'ru' | 'en'
export const LOCALES: { code: Locale; label: string }[] = [{ code: 'kk', label: 'Қазақша' }, { code: 'ru', label: 'Русский' }, { code: 'en', label: 'English' }]
export type { Key }

const dict: Record<Locale, Partial<Record<Key, string>>> = { en, ru, kk }
const chain: Record<Locale, Locale[]> = { kk: ['kk', 'ru', 'en'], ru: ['ru', 'en'], en: ['en'] }

export function translate(locale: Locale, key: Key, params?: Record<string, string | number>): string {
  let s: string = key
  for (const l of chain[locale]) { const v = dict[l][key]; if (v) { s = v; break } }
  if (params) for (const [k, v] of Object.entries(params)) s = s.replaceAll(`{${k}}`, String(v))
  return s
}

const STORE = 'sulu.lang'
function initial(): Locale {
  try { const s = localStorage.getItem(STORE); if (s === 'kk' || s === 'ru' || s === 'en') return s } catch { /* ignore */ }
  const n = (typeof navigator !== 'undefined' ? navigator.language : 'ru').slice(0, 2)
  return n === 'kk' || n === 'en' ? (n as Locale) : 'ru'
}

type Ctx = { locale: Locale; setLocale: (l: Locale) => void; t: (k: Key, p?: Record<string, string | number>) => string; err: (code?: string) => string }
const I18n = createContext<Ctx>({ locale: 'ru', setLocale: () => {}, t: (k) => k, err: () => '' })

export function I18nProvider({ children, defaultLocale }: { children: ReactNode; defaultLocale?: Locale }) {
  const [locale, setLoc] = useState<Locale>(defaultLocale ?? initial())
  const setLocale = useCallback((l: Locale) => { setLoc(l); try { localStorage.setItem(STORE, l) } catch { /* ignore */ } }, [])
  useEffect(() => { document.documentElement.lang = locale }, [locale])
  const value = useMemo<Ctx>(() => ({
    locale, setLocale,
    t: (k, p) => translate(locale, k, p),
    err: (code) => { const k = ('err.' + code) as Key; return dict.en[k] ? translate(locale, k) : translate(locale, 'err.generic') },
  }), [locale, setLocale])
  return <I18n.Provider value={value}>{children}</I18n.Provider>
}
export const useI18n = () => useContext(I18n)

/** Localized category name from API row (name_kk/name_ru/name_en → nameKk/nameRu/nameEn). */
export function catName(c: { nameKk?: string; nameRu?: string; nameEn?: string; categoryName?: string }, l: Locale): string {
  return (l === 'kk' ? c.nameKk : l === 'ru' ? c.nameRu : c.nameEn) ?? c.nameRu ?? c.nameEn ?? c.categoryName ?? ''
}

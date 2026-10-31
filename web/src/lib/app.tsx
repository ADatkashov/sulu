import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, get, loadSession, onSession, post, saveSession, type Session, type User } from './api'
import { useI18n, type Locale } from '../i18n'

type Config = { defaultCity: string; defaultCurrency: string; cities: string[]; simulatedPayments: boolean }
type Ctx = {
  user: User | null; config: Config | null; city: string; setCity: (c: string) => void
  login: (email: string, password: string) => Promise<User>
  register: (b: { email: string; password: string; fullName: string; phone?: string; role: string }) => Promise<User>
  logout: () => Promise<void>; refreshUser: () => Promise<void>; unread: number; refreshUnread: () => void
}
const App = createContext<Ctx>(null as unknown as Ctx)
export const useApp = () => useContext(App)

const CITY = 'sulu.city'
export function AppProvider({ children }: { children: ReactNode }) {
  const { locale, setLocale } = useI18n()
  const [session, setSession] = useState<Session | null>(loadSession())
  const [config, setConfig] = useState<Config | null>(null)
  const [city, setCityState] = useState<string>(() => { try { return localStorage.getItem(CITY) ?? '' } catch { return '' } })
  const [unread, setUnread] = useState(0)

  useEffect(() => onSession(setSession), [])
  useEffect(() => { get<Config>('/api/config').then((c) => { setConfig(c); setCityState((x) => x || c.defaultCity) }).catch(() => {}) }, [])
  const setCity = useCallback((c: string) => { setCityState(c); try { localStorage.setItem(CITY, c) } catch { /* ignore */ } }, [])

  const refreshUnread = useCallback(() => {
    if (!loadSession()) { setUnread(0); return }
    get<{ unread: number }>('/api/notifications').then((r) => setUnread(r.unread)).catch(() => {})
  }, [])
  useEffect(() => { refreshUnread() }, [session?.user.id, refreshUnread])

  const adopt = useCallback((s: Session) => {
    saveSession(s)
    if (s.user.language === 'kk' || s.user.language === 'ru' || s.user.language === 'en') setLocale(s.user.language as Locale)
    return s.user
  }, [setLocale])

  const value = useMemo<Ctx>(() => ({
    user: session?.user ?? null, config, city, setCity, unread, refreshUnread,
    login: async (email, password) => adopt(await api<Session>('/api/auth/login', { method: 'POST', body: { email, password }, auth: false })),
    register: async (b) => adopt(await api<Session>('/api/auth/register', { method: 'POST', body: { ...b, language: locale, city: city || undefined }, auth: false })),
    logout: async () => { const s = loadSession(); try { if (s) await post('/api/auth/logout', { refreshToken: s.refreshToken }) } catch { /* ignore */ } saveSession(null) },
    refreshUser: async () => { const u = await get<User>('/api/me'); const s = loadSession(); if (s) saveSession({ ...s, user: u }) },
  }), [session, config, city, setCity, unread, refreshUnread, adopt, locale])
  return <App.Provider value={value}>{children}</App.Provider>
}

/** Favorites toggle with login redirect handled by the caller via `needLogin`. */
export function useFavorites(onNeedLogin: () => void) {
  const { user } = useApp()
  return useCallback(async (kind: 'business' | 'professional' | 'service', id: number, on: boolean) => {
    if (!user) { onNeedLogin(); return false }
    await api(`/api/favorites/${kind}/${id}`, { method: on ? 'DELETE' : 'PUT' })
    return !on
  }, [user, onNeedLogin])
}

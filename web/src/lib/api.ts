// Thin fetch wrapper: JSON, bearer token, single-flight refresh on 401, typed ApiError with a stable `code`.
const BASE = (import.meta.env.VITE_API_URL as string | undefined) ?? ''

export class ApiError extends Error {
  status: number
  code: string
  details?: any
  constructor(status: number, code: string, message: string, details?: any) {
    super(message)
    this.status = status
    this.code = code
    this.details = details
  }
}

const KEY = 'sulu.session'
export type Session = { accessToken: string; refreshToken: string; user: User }
export type User = {
  id: number; email: string; fullName: string; phone?: string; role: 'CUSTOMER' | 'PROFESSIONAL' | 'BUSINESS_OWNER' | 'PLATFORM_ADMIN'
  language: 'kk' | 'ru' | 'en' | 'tr'; city?: string; notifyEmail: boolean; notifyPush: boolean
}

export function loadSession(): Session | null {
  try { const s = localStorage.getItem(KEY); return s ? JSON.parse(s) : null } catch { return null }
}
export function saveSession(s: Session | null) {
  try { if (s) localStorage.setItem(KEY, JSON.stringify(s)); else localStorage.removeItem(KEY) } catch { /* storage unavailable */ }
  listeners.forEach((l) => l(s))
}
const listeners = new Set<(s: Session | null) => void>()
export function onSession(fn: (s: Session | null) => void) { listeners.add(fn); return () => { listeners.delete(fn) } }

let refreshing: Promise<boolean> | null = null
async function refresh(): Promise<boolean> {
  const s = loadSession()
  if (!s) return false
  refreshing ??= (async () => {
    try {
      const r = await fetch(BASE + '/api/auth/refresh', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ refreshToken: s.refreshToken }) })
      if (!r.ok) { saveSession(null); return false }
      saveSession(await r.json())
      return true
    } catch { return false } finally { setTimeout(() => { refreshing = null }, 0) }
  })()
  return refreshing
}

type Opts = { method?: string; body?: unknown; form?: FormData; headers?: Record<string, string>; auth?: boolean }

export async function api<T = any>(path: string, opts: Opts = {}): Promise<T> {
  const doFetch = async () => {
    const h: Record<string, string> = { ...(opts.headers ?? {}) }
    const s = loadSession()
    if (s && opts.auth !== false) h.Authorization = 'Bearer ' + s.accessToken
    let body: BodyInit | undefined
    if (opts.form) body = opts.form
    else if (opts.body !== undefined) { h['Content-Type'] = 'application/json'; body = JSON.stringify(opts.body) }
    return fetch(BASE + path, { method: opts.method ?? (body ? 'POST' : 'GET'), headers: h, body })
  }
  let r: Response
  try { r = await doFetch() } catch { throw new ApiError(0, 'NETWORK', 'Network error') }
  if (r.status === 401 && opts.auth !== false && loadSession() && !path.startsWith('/api/auth/')) {
    if (await refresh()) r = await doFetch()
  }
  if (!r.ok) {
    let j: any = {}
    try { j = await r.json() } catch { /* not json */ }
    throw new ApiError(r.status, j.code ?? 'HTTP_' + r.status, j.message ?? r.statusText, j.details)
  }
  if (r.status === 204) return undefined as T
  const text = await r.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export const get = <T = any>(p: string) => api<T>(p)
export const post = <T = any>(p: string, body?: unknown, headers?: Record<string, string>) => api<T>(p, { method: 'POST', body: body ?? {}, headers })
export const put = <T = any>(p: string, body?: unknown) => api<T>(p, { method: 'PUT', body: body ?? {} })
export const del = <T = any>(p: string) => api<T>(p, { method: 'DELETE' })

export const mediaUrl = (key?: string | null) => (key ? `${BASE}/media/${key}` : undefined)
export const qs = (o: Record<string, unknown>) => {
  const p = new URLSearchParams()
  Object.entries(o).forEach(([k, v]) => { if (v !== undefined && v !== null && v !== '' && v !== false) p.set(k, String(v)) })
  const s = p.toString()
  return s ? '?' + s : ''
}
export const newKey = () => (globalThis.crypto?.randomUUID?.() ?? String(Date.now()) + Math.random())

// Captures every main screen into docs/screenshots. Needs backend (dev profile, demo data) + vite running.
import { createRequire } from 'node:module'
import { mkdirSync } from 'node:fs'
const { chromium } = createRequire(import.meta.url)('playwright')
const BASE = process.env.SULU_WEB ?? 'http://127.0.0.1:5173'
const OUT = process.env.OUT ?? '../docs/screenshots'
mkdirSync(OUT, { recursive: true })
const b = await chromium.launch()
const login = async (page, email, pw) => {
  await page.goto(BASE + '/login'); await page.getByLabel('Email').fill(email); await page.getByLabel('Пароль').fill(pw)
  await page.getByRole('button', { name: 'Войти' }).first().click(); await page.waitForLoadState('networkidle')
}
const snap = async (page, name, url, { wait = 700, full = false, act } = {}) => {
  if (url) await page.goto(BASE + url)
  await page.waitForLoadState('networkidle'); await page.waitForTimeout(wait)
  if (act) await act(page)
  await page.screenshot({ path: `${OUT}/${name}.png`, fullPage: full }); console.log('ok', name)
}
// ---------- customer (mobile)
const m = await b.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, locale: 'ru-RU' })
const p = await m.newPage()
await p.addInitScript(() => { try { localStorage.removeItem('sulu.onboarded') } catch {} })
await snap(p, 'c01-onboarding', '/onboarding')
await p.evaluate(() => localStorage.setItem('sulu.onboarded', '1'))
await snap(p, 'c02-login', '/login'); await snap(p, 'c03-register', '/register'); await snap(p, 'c04-reset', '/reset')
await snap(p, 'c05-home-guest', '/', { wait: 1200 })
await snap(p, 'c06-home-full', '/', { full: true })
await snap(p, 'c07-explore-services', '/explore'); await snap(p, 'c08-explore-salons', '/explore?tab=salons'); await snap(p, 'c09-explore-masters', '/explore?tab=masters')
await snap(p, 'c10-explore-filters', '/explore?tab=salons', { act: async (pg) => { await pg.getByRole('button', { name: 'Фильтры' }).click(); await pg.waitForTimeout(300) } })
await snap(p, 'c11-explore-empty', '/explore?q=zzzzqqq')
await snap(p, 'c12-salon', '/salon/1', { full: true }); await snap(p, 'c13-master', '/master/1', { full: true }); await snap(p, 'c14-service', '/service/1', { full: true })
await login(p, 'anna@demo.sulu', 'Demo12345!')
await snap(p, 'c15-book-master', '/book/1')
await snap(p, 'c16-book-time', '/book/1?pro=1', { wait: 1200 })
await snap(p, 'c17-book-confirm', '/book/1?pro=1', { wait: 1000, act: async (pg) => {
  const days = pg.locator('.day'); for (let i = 0; i < await days.count(); i++) { await days.nth(i).click(); if (await pg.locator('.slots button').first().waitFor({ timeout: 1500 }).then(() => true).catch(() => false)) break }
  await pg.locator('.slots button').first().click(); await pg.getByRole('button', { name: 'Продолжить' }).click(); await pg.getByText('Ваша запись').waitFor() } })
await snap(p, 'c18-bookings-upcoming', '/bookings'); await snap(p, 'c19-bookings-past', '/bookings?scope=past'); await snap(p, 'c20-bookings-cancelled', '/bookings?scope=cancelled')
await p.goto(BASE + '/bookings'); const href = await p.locator('a[href^="/bookings/"]').first().getAttribute('href')
await snap(p, 'c21-booking-detail', href, { full: true })
await p.goto(BASE + '/bookings?scope=past'); const past = await p.locator('a[href^="/bookings/"]').first().getAttribute('href')
await snap(p, 'c22-booking-review-dialog', past, { act: async (pg) => { const bt = pg.getByRole('button', { name: 'Оставить отзыв' }); if (await bt.count()) { await bt.click(); await pg.waitForTimeout(300) } } })
await snap(p, 'c23-favorites', '/favorites'); await snap(p, 'c24-notifications', '/notifications'); await snap(p, 'c25-profile', '/profile', { full: true })
await snap(p, 'c26-home-kazakh', '/', { act: async (pg) => { await pg.evaluate(() => localStorage.setItem('sulu.lang', 'kk')); await pg.reload(); await pg.waitForTimeout(1200) } })
await snap(p, 'c27-home-english', '/', { act: async (pg) => { await pg.evaluate(() => localStorage.setItem('sulu.lang', 'en')); await pg.reload(); await pg.waitForTimeout(1200) } })
await p.evaluate(() => localStorage.setItem('sulu.lang', 'ru'))
// ---------- business (desktop)
const d = await b.newContext({ viewport: { width: 1360, height: 860 }, locale: 'ru-RU' })
const o = await d.newPage(); await o.addInitScript(() => localStorage.setItem('sulu.lang', 'ru'))
await login(o, 'owner@demo.sulu', 'Demo12345!')
for (const [n, u] of [['overview', 'overview'], ['calendar-week', 'calendar'], ['services', 'services'], ['staff', 'staff'], ['customers', 'customers'], ['analytics', 'analytics'], ['reviews', 'reviews'], ['promotions', 'promos'], ['settings', 'settings']])
  await snap(o, `b-${n}`, `/business/${u}`, { wait: 900, full: n === 'settings' })
await snap(o, 'b-staff-hours-dialog', '/business/staff', { act: async (pg) => { await pg.getByRole('button', { name: 'Рабочие часы' }).first().click(); await pg.waitForTimeout(400) } })
await snap(o, 'b-service-form', '/business/services', { act: async (pg) => { await pg.getByRole('button', { name: /Новая услуга/ }).click(); await pg.waitForTimeout(400) } })
// ---------- pro
const pr = await d.newPage(); await pr.addInitScript(() => localStorage.setItem('sulu.lang', 'ru'))
await pr.goto(BASE + '/login'); await pr.evaluate(() => localStorage.clear()); await pr.evaluate(() => localStorage.setItem('sulu.lang', 'ru'))
await login(pr, 'master@demo.sulu', 'Demo12345!'); await snap(pr, 'p-schedule', '/pro', { wait: 1200 })
// ---------- admin
const a = await b.newContext({ viewport: { width: 1360, height: 860 }, locale: 'ru-RU' }); const ap = await a.newPage(); await ap.addInitScript(() => localStorage.setItem('sulu.lang', 'ru'))
await login(ap, 'admin@sulu.local', 'Admin12345!')
for (const n of ['overview', 'users', 'businesses', 'categories', 'reviews', 'bookings', 'payments', 'promos', 'settings', 'audit']) await snap(ap, `a-${n}`, `/admin/${n}`, { wait: 900 })
await b.close()

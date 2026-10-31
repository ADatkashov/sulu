// End-to-end journey against a running stack (backend :8080 dev profile + vite :5173). Run: node e2e/journey.mjs
import { createRequire } from 'node:module'
const require = createRequire(import.meta.url)
const { chromium } = require('playwright')
const BASE = process.env.SULU_WEB ?? 'http://127.0.0.1:5173'
const SHOTS = process.env.SHOTS ?? ''
const results = []
const check = (name, ok, extra = '') => { results.push({ name, ok }); console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${extra ? ' — ' + extra : ''}`) }
const shot = async (page, n) => { if (SHOTS) await page.screenshot({ path: `${SHOTS}/${n}.png`, fullPage: false }) }

const browser = await chromium.launch()
const ctx = await browser.newContext({ viewport: { width: 390, height: 844 }, locale: 'ru-RU', deviceScaleFactor: 2 })
const page = await ctx.newPage()
const errors = []
page.on('pageerror', (e) => errors.push(String(e)))
page.on('console', (m) => { if (m.type() === 'error' && !/favicon|Failed to load resource/.test(m.text())) errors.push(m.text()) })

try {
  // onboarding
  await page.goto(BASE + '/')
  await page.waitForURL('**/onboarding')
  await shot(page, '01-onboarding')
  for (let i = 0; i < 3; i++) await page.getByRole('button', { name: /Продолжить/ }).click()
  await page.getByRole('button', { name: /Смотреть без регистрации/ }).click()
  await page.waitForURL(BASE + '/')
  await page.getByText('Популярные салоны').waitFor()
  check('onboarding → home', true)
  await shot(page, '02-home')

  // search
  await page.getByRole('searchbox').or(page.getByPlaceholder('Услуги, салоны, мастера')).first().fill('маникюр')
  await page.keyboard.press('Enter')
  await page.waitForURL('**/explore?q=*')
  await page.getByText('Маникюр с покрытием').first().waitFor()
  check('search returns matching services', true)
  await shot(page, '03-explore')
  const count = await page.locator('a[href^="/service/"]').count()
  check('search filtered results', count >= 1 && count < 6, `${count} results`)

  // service → booking (guest) → login redirect
  await page.getByText('Маникюр с покрытием').first().click()
  await page.getByRole('button', { name: 'Записаться' }).waitFor()
  await shot(page, '04-service')
  await page.getByRole('button', { name: 'Записаться' }).click()
  await page.getByRole('button', { name: /Любой свободный мастер/ }).waitFor()
  await page.getByRole('button', { name: 'Продолжить' }).click()
  // slots: pick the first day that actually has free time
  await page.getByRole('option').first().waitFor()
  const pickFirstDayWithSlots = async (skip = 0) => {
    const days = page.locator('.day'); const total = await days.count()
    let seen = 0
    for (let i = 0; i < total; i++) {
      await days.nth(i).click()
      const ok = await page.locator('.slots button').first().waitFor({ timeout: 2500 }).then(() => true).catch(() => false)
      if (ok && seen++ >= skip) return
    }
    throw new Error('no day with slots')
  }
  await pickFirstDayWithSlots()
  await shot(page, '05-booking-time')
  await page.locator('.slots button').first().click()
  await page.getByRole('button', { name: 'Продолжить' }).click()
  await page.getByText('Ваша запись').waitFor()
  await shot(page, '06-booking-confirm')
  await page.getByRole('button', { name: 'Войти' }).click()
  await page.waitForURL('**/login?next=*')
  check('guest is sent to login with return path', true)

  // register a brand-new customer through the UI (also covers registration), then continue the booking
  await page.getByRole('link', { name: 'Создать аккаунт' }).click()
  const email = `e2e${Date.now()}@test.local`
  await page.getByLabel('Имя и фамилия').fill('E2E Клиент')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Пароль').fill('Passw0rd!x')
  await page.getByRole('button', { name: 'Создать аккаунт' }).click()
  await page.waitForURL('**/book/*')
  await page.getByRole('button', { name: /Любой свободный мастер/ }).click()
  await page.getByRole('button', { name: 'Продолжить' }).click()
  await pickFirstDayWithSlots(2)
  const n = await page.locator('.slots button').count()
  await page.locator('.slots button').nth(Math.min(n - 1, 1 + Math.floor(Math.random() * Math.max(1, n - 2)))).click()
  await page.getByRole('button', { name: 'Продолжить' }).click()
  await page.getByLabel(/Промокод/).fill('WELCOME10')
  await page.getByLabel(/Комментарий мастеру/).fill('e2e test')
  await page.getByRole('button', { name: /онлайн/i }).click()
  await page.getByRole('button', { name: 'Подтвердить запись' }).click()
  await page.waitForURL('**/booked/*', { timeout: 10000 })
  await page.getByText(/Запись подтверждена|Заявка отправлена/).waitFor()
  check('booking created end-to-end', true)
  await shot(page, '07-booked')
  const bookedUrl = page.url()
  const bookingId = bookedUrl.match(/booked\/(\d+)/)[1]

  // dev payment simulation
  await page.getByText(/Симуляция оплаты/).waitFor({ timeout: 5000 })
  await page.getByRole('button', { name: 'Симулировать успех' }).click()
  await page.getByText('Оплачено').waitFor({ timeout: 5000 })
  check('simulated payment resolves through event path (labelled dev)', true)

  // appointments list + details
  await page.getByRole('button', { name: 'Открыть запись' }).click()
  await page.getByText('Условия отмены').or(page.getByText(/Бесплатная отмена/)).first().waitFor()
  await shot(page, '08-booking-detail')
  await page.goto(BASE + '/bookings')
  await page.locator(`a[href="/bookings/${bookingId}"]`).waitFor()
  check('booking visible in upcoming list', true)
  await shot(page, '09-bookings')

  // favorites
  await page.goto(BASE + '/explore?tab=salons')
  await page.locator('button.heart').first().click()
  await page.goto(BASE + '/favorites')
  await page.locator('a[href^="/salon/"]').first().waitFor()
  check('favorites persist (reload via API)', true)
  await shot(page, '10-favorites')

  // cancel
  await page.goto(BASE + '/bookings/' + bookingId)
  await page.getByRole('button', { name: 'Отменить запись' }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Отменить запись' }).click()
  await page.getByText('Отменена').first().waitFor({ timeout: 5000 })
  await page.getByText('Возврат').first().waitFor({ timeout: 5000 })
  check('cancel + automatic refund of simulated payment', true)

  // language switch (kk)
  await page.goto(BASE + '/profile')
  await page.locator('#lang').selectOption('kk')
  await page.getByText('Профиль').first().waitFor().catch(() => {})
  await page.goto(BASE + '/')
  await page.getByText('Таңдаулы').or(page.getByText('Үздік шеберлер')).first().waitFor({ timeout: 5000 })
  check('Kazakh localization renders', true)
  await shot(page, '11-home-kk')
  await page.goto(BASE + '/profile'); await page.locator('#lang').selectOption('ru')
} catch (e) {
  check('journey aborted: ' + String(e).split('\n')[0], false)
  if (SHOTS) await page.screenshot({ path: `${SHOTS}/zz-failure.png` })
}
check('no console/page errors in customer journey', errors.length === 0, errors.slice(0, 2).join(' | '))

// business owner journey
const bctx = await browser.newContext({ viewport: { width: 1280, height: 800 }, locale: 'ru-RU' })
const bp = await bctx.newPage()
try {
  await bp.goto(BASE + '/login')
  await bp.getByLabel('Email').fill('owner@demo.sulu'); await bp.getByLabel('Пароль').fill('Demo12345!')
  await bp.getByRole('button', { name: 'Войти' }).first().click()
  await bp.waitForURL('**/business/overview')
  await bp.getByText('Ближайшие 7 дней').waitFor()
  check('business owner lands on overview', true)
  if (SHOTS) await bp.screenshot({ path: `${SHOTS}/20-business-overview.png` })
  await bp.getByRole('link', { name: 'Календарь' }).click()
  await bp.locator('.cal').waitFor()
  if (SHOTS) await bp.screenshot({ path: `${SHOTS}/21-business-calendar.png` })
  await bp.getByRole('link', { name: 'Услуги' }).click()
  await bp.getByRole('button', { name: /Новая услуга/ }).click()
  const name = 'E2E услуга ' + Date.now()
  await bp.getByLabel('Название').fill(name)
  await bp.getByLabel(/Цена/).first().fill('5500')
  await bp.getByRole('dialog').getByRole('button', { name: 'Сохранить' }).click()
  await bp.getByText(name).waitFor({ timeout: 5000 })
  check('business creates a service (persisted)', true)
  if (SHOTS) await bp.screenshot({ path: `${SHOTS}/22-business-services.png` })
  await bp.getByRole('link', { name: 'Сотрудники' }).click()
  await bp.getByText('Анастасия Демо').waitFor()
  await bp.getByRole('link', { name: 'Аналитика' }).click()
  await bp.getByText('Популярные услуги').waitFor()
  if (SHOTS) await bp.screenshot({ path: `${SHOTS}/23-business-analytics.png` })
  check('staff and analytics pages render with real data', true)
} catch (e) { check('business journey: ' + String(e).split('\n')[0], false); if (SHOTS) await bp.screenshot({ path: `${SHOTS}/zz-biz-failure.png` }) }

// authorization boundary in the UI/API: customer cannot open /admin
const actx = await browser.newContext({ viewport: { width: 1280, height: 800 }, locale: 'ru-RU' })
const ap = await actx.newPage()
try {
  await ap.goto(BASE + '/login'); await ap.getByLabel('Email').fill('admin@sulu.local'); await ap.getByLabel('Пароль').fill('Admin12345!')
  await ap.getByRole('button', { name: 'Войти' }).first().click(); await ap.waitForURL('**/admin')
  await ap.getByRole('heading', { name: 'Обзор' }).waitFor()
  if (SHOTS) await ap.screenshot({ path: `${SHOTS}/30-admin-overview.png` })
  await ap.getByRole('link', { name: 'Бизнесы' }).click()
  await ap.getByRole('heading', { name: 'Бизнесы' }).waitFor()
  check('admin console renders', true)
  const cp = await ctx.newPage()
  await cp.goto(BASE + '/login'); await cp.getByLabel('Email').fill('anna@demo.sulu'); await cp.getByLabel('Пароль').fill('Demo12345!')
  await cp.getByRole('button', { name: 'Войти' }).first().click(); await cp.waitForURL(BASE + '/')
  await cp.goto(BASE + '/admin'); await cp.getByText('Нет доступа.').waitFor()
  check('customer cannot use the admin UI', true)
} catch (e) { check('admin journey: ' + String(e).split('\n')[0], false); if (SHOTS) await ap.screenshot({ path: `${SHOTS}/zz-admin-failure.png` }) }

await browser.close()
const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} checks passed`)
process.exit(failed.length ? 1 : 0)

// Flutter Web build (flutter build web --no-web-resources-cdn --dart-define=SEMANTICS=true) served by tool/serve.mjs on :5174,
// backend (dev profile) on :8080. Drives the app through the semantics tree. Run: node e2e/journey.mjs
import { createRequire } from 'node:module'
import { mkdirSync } from 'node:fs'
const { chromium } = createRequire(new URL('../../web/package.json', import.meta.url))('playwright') /* reuse web/ devDependency */
const BASE = process.env.SULU_APP ?? 'http://127.0.0.1:5174'
const SHOTS = process.env.SHOTS ?? ''
if (SHOTS) mkdirSync(SHOTS, { recursive: true })
const results = []
const check = (n, ok, x = '') => { results.push(ok); console.log(`${ok ? 'PASS' : 'FAIL'}  ${n}${x ? ' — ' + x : ''}`) }
const b = await chromium.launch(); const ctx = await b.newContext({ viewport: { width: 390, height: 844 }, locale: 'ru-RU', deviceScaleFactor: 2 }); const p = await ctx.newPage()
const errs = []; p.on('pageerror', (e) => errs.push(String(e).slice(0, 200)))
const shot = async (n) => { if (SHOTS) await p.screenshot({ path: `${SHOTS}/${n}.png` }) }
// Flutter semantics expose text either as DOM text or as aria-label — match both.
const txt = (t) => p.locator(`flt-semantics:has-text("${t}"), [aria-label*="${t}"]`).last()
const btn = (n) => p.getByRole('button', { name: n }).first()
const see = async (t, ms = 15000) => { await txt(t).waitFor({ state: 'attached', timeout: ms }) }
const type = async (label, value) => { await p.getByRole('textbox', { name: label }).first().click(); await p.keyboard.type(value, { delay: 10 }) }
try {
  await p.goto(BASE + '/'); await see('Красота рядом', 25000)
  for (let i = 0; i < 3; i++) await btn('Продолжить').click()
  await btn('Смотреть без регистрации').click(); await see('Популярные салоны'); await p.waitForTimeout(1500)
  check('onboarding → home with API data', (await txt('Aru Beauty Studio').count()) > 0)
  await shot('m01-home')
  // explore + search
  await p.getByRole('tab', { name: /Поиск/ }).click(); await see('Услуги')
  await p.goto(BASE + '/#/explore?category=manicure')
  await see('Маникюр с покрытием'); check('category filter returns matching services', (await p.locator('[aria-label*="Стрижка"]').count()) === 0); await shot('m02-explore')
  await p.goto(BASE + '/#/explore?q=%D0%BC%D0%B0%D0%BD%D0%B8%D0%BA%D1%8E%D1%80'); await see('Маникюр с покрытием'); check('text search via deep link', true)
  await txt('Маникюр с покрытием').click(); await see('Записаться'); await shot('m03-service')
  await btn('Записаться').click(); await see('Любой свободный мастер'); await btn('Продолжить').click()
  // first day that has free time
  await see('Выберите дату')
  const pickDay = async (skip = 0) => {
    let seen = 0
    for (let i = 0; i < 14; i++) {
      const days = p.getByRole('button', { name: /^(Пн|Вт|Ср|Чт|Пт|Сб|Вс|\w{2})\s*\d{1,2}$/ })
      const n = await days.count(); if (i >= n) break
      await days.nth(i).click(); await p.waitForTimeout(700)
      if (await p.getByRole('button', { name: /^\d{2}:\d{2}$/ }).count()) { if (seen++ >= skip) return }
    }
    throw new Error('no day with slots')
  }
  await pickDay(); await shot('m04-time')
  await p.getByRole('button', { name: /^\d{2}:\d{2}$/ }).nth(1).click(); await btn('Продолжить').click()
  await see('Ваша запись'); await shot('m05-confirm')
  await btn('Войти').click(); await see('Нет аккаунта?')
  check('guest is sent to login before confirming', true)
  await txt('Создать аккаунт').click(); await see('Имя и фамилия')
  const email = `fl${Date.now()}@test.local`
  await type('Имя и фамилия', 'Flutter Клиент'); await type('Email', email); await type('Пароль', 'Passw0rd!x'); await shot('m06-register')
  await btn('Создать аккаунт').last().click(); await see('Ваша запись', 20000)
  check('registered and returned to the booking', true)
  await p.getByRole('button', { name: /онлайн/i }).click()
  await btn('Подтвердить запись').click(); await see('Запись подтверждена!', 20000)
  check('booking created end-to-end', true); await shot('m07-booked')
  await see('Симуляция оплаты'); await btn('Симулировать успех').click(); await see('Оплачено', 10000)
  check('simulated payment resolves (labelled dev)', true)
  await btn('Открыть запись').click(); await see('Отменить запись'); await shot('m08-detail')
  await btn('Отменить запись').click(); await p.getByRole('dialog').getByRole('button', { name: 'Отменить запись' }).click()
  await see('Запись отменена', 10000); await see('Возврат', 10000)
  check('cancel + automatic refund', true)
  await p.getByRole('tab', { name: /Профиль/ }).click(); await see('Выйти'); await shot('m09-profile')
  check('profile tab', true)
} catch (e) { check('journey aborted: ' + String(e).split('\n')[0], false); await shot('zz-failure') }
check('no uncaught page errors', errs.length === 0, errs[0] ?? '')
await b.close(); const bad = results.filter((x) => !x).length; console.log(`\n${results.length - bad}/${results.length} checks passed`); process.exit(bad ? 1 : 0)

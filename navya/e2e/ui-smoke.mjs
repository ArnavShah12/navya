// Drives the real UI in headless Chrome against a running Navya seeded with SEED=true.
// Usage: BASE_URL=http://localhost:8089 NV_PASSWORD=<seed password> CHROME=google-chrome SHOTS=./shots node ui-smoke.mjs
import puppeteer from 'puppeteer-core';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';

const BASE = process.env.BASE_URL || 'http://localhost:8089';
const PASSWORD = process.env.NV_PASSWORD;
const CHROME = process.env.CHROME || 'google-chrome';
const SHOTS = process.env.SHOTS || './shots';
if (!PASSWORD) { console.error('Set NV_PASSWORD to the seed password'); process.exit(2); }
fs.mkdirSync(SHOTS, { recursive: true });

const errors = [];
let step = 0, checks = 0;
function check(cond, msg) {
  checks += 1;
  console.log((cond ? 'PASS ' : 'FAIL ') + msg);
  if (!cond) process.exitCode = 1;
}
const pause = (ms) => new Promise((r) => setTimeout(r, ms));

const browser = await puppeteer.launch({ executablePath: CHROME, headless: true, args: ['--no-sandbox', '--disable-gpu'] });

async function newSession(width = 1280, height = 900) {
  const ctx = await browser.createBrowserContext(); // separate storage per person
  const page = await ctx.newPage();
  await page.setViewport({ width, height });
  page.on('pageerror', (e) => errors.push('pageerror: ' + e.message));
  page.on('console', (m) => { if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) errors.push('console: ' + m.text()); });
  page.on('dialog', (d) => d.accept('Looks like a counterfeit'));
  return page;
}
const shot = async (page, name) => { step += 1; await page.screenshot({ path: `${SHOTS}/${String(step).padStart(2, '0')}-${name}.png`, fullPage: true }); };
const go = async (page, hash) => { await page.goto(BASE + '/#/' + hash, { waitUntil: 'networkidle0' }); await pause(200); };
const count = (page, sel) => page.$$eval(sel, (e) => e.length);
const set = (page, sel, v) => page.$eval(sel, (el, val) => { el.value = val; el.dispatchEvent(new Event('input', { bubbles: true })); }, v);
const login = async (page, email) => {
  await go(page, 'login');
  await page.type('#loginForm [name=email]', email);
  await page.type('#loginForm [name=password]', PASSWORD);
  await page.click('#loginForm button.btn');
  await page.waitForFunction(() => localStorage.getItem('navya.token'), { timeout: 8000 });
  await pause(400);
};
const clickByText = async (page, sel, label) => {
  const ok = await page.evaluate((s, l) => { const el = [...document.querySelectorAll(s)].find((e) => e.textContent.trim().startsWith(l)); if (el) { el.click(); return true; } return false; }, sel, label);
  if (!ok) throw new Error(`No ${sel} starting with "${label}"`);
};
const toast = (page) => page.$eval('#toast', (e) => (e.hidden ? '' : e.textContent));

try {
  // ---- browse, search, filter ----
  const anon = await newSession();
  await go(anon, '');
  await anon.waitForSelector('.card');
  check((await count(anon, '.card')) === 8, 'home page lists the 8 seeded items');
  check((await anon.$eval('#count', (e) => e.textContent)) === '8 items', 'the count says 8 items');
  await shot(anon, 'home');
  await anon.click('#searchBtn').catch(() => {}); // only exists on phone-width layouts
  await anon.type('#q', 'denim');
  await pause(700);
  check((await count(anon, '.card')) === 1, 'search "denim" narrows to 1 item');
  await set(anon, '#q', '');
  await pause(500);
  await anon.click('#chips [data-cat=Shoes]');
  await pause(600);
  check((await count(anon, '.card')) === 2, 'the Shoes chip shows 2 items');
  await anon.click('#chips [data-cat=""]');
  await anon.click('#filterBtn');
  await set(anon, '[name=min]', '1000');
  await pause(600);
  check((await count(anon, '.card')) === 2, 'minimum price 1000 shows 2 items (1,200 and 1,500)');
  await set(anon, '[name=min]', '99999');
  await pause(600);
  check(await anon.$eval('#empty', (e) => !e.hidden), 'an impossible price shows the empty state');
  await anon.click('#empty [data-clear]');
  await pause(500);
  check((await count(anon, '.card')) === 8, 'Clear filters brings all 8 back');
  await set(anon, '#q', '<img src=x onerror=alert(1)>');
  await pause(500);
  check((await count(anon, '.card')) === 0 && errors.length === 0, 'script text in the search box does nothing');
  await set(anon, '#q', '');

  // ---- detail page, login wall ----
  await pause(400);
  await anon.click('.card a');
  await anon.waitForSelector('.detail');
  check((await anon.$eval('.detail h1', (e) => e.textContent.length)) > 0, 'a listing opens with its title');
  await shot(anon, 'detail');
  await anon.click('[data-chat]');
  await pause(300);
  check(anon.url().endsWith('#/login'), 'Message seller sends a logged-out visitor to the login page');
  await go(anon, 'mine');
  check(anon.url().endsWith('#/login'), 'My listings is behind login too');

  // ---- sign up and a wrong login ----
  await go(anon, 'signup');
  await anon.type('#signupForm [name=name]', 'Test Buyer');
  await anon.type('#signupForm [name=email]', `buyer${Date.now()}@example.com`);
  await anon.type('#signupForm [name=city]', 'Pune');
  await anon.type('#signupForm [name=password]', 'short');
  await anon.click('#signupForm button.btn');
  await pause(300);
  check((await anon.evaluate(() => localStorage.getItem('navya.token'))) === null, 'a 5-character password is not accepted');
  await set(anon, '#signupForm [name=password]', 'long-enough-1');
  await anon.click('#signupForm button.btn');
  await pause(300);
  check((await anon.evaluate(() => localStorage.getItem('navya.token'))) === null, 'sign-up needs the 18+ and terms box ticked');
  await anon.click('#signupForm [name=consent]');
  await anon.click('#signupForm button.btn');
  await anon.waitForFunction(() => localStorage.getItem('navya.token'), { timeout: 8000 });
  check(true, 'a valid sign-up logs the new member in');

  // ---- seller: post, edit, mark sold ----
  const aanya = await newSession();
  await go(aanya, 'login');
  await aanya.type('#loginForm [name=email]', 'aanya@navya.test');
  await aanya.type('#loginForm [name=password]', 'wrong-password');
  await aanya.click('#loginForm button.btn');
  await pause(800);
  check((await aanya.$eval('#loginErr', (e) => e.textContent)).includes('wrong'), 'a wrong password shows an error');
  await aanya.$eval('#loginForm', (f) => f.reset());
  await login(aanya, 'aanya@navya.test');
  check(await aanya.$eval('[data-auth=in]', (e) => !e.hidden), 'the nav shows logged-in links');

  // a real photo: take one the app already serves and upload it back
  const imgUrl = await anon.evaluate(() => document.querySelector('.main-img img')?.getAttribute('src') || '');
  const photo = path.join(os.tmpdir(), 'navya-e2e-photo.jpg');
  fs.writeFileSync(photo, Buffer.from(await (await fetch(BASE + imgUrl)).arrayBuffer()));

  await go(aanya, 'post');
  await aanya.type('#postForm [name=title]', 'Leather boots');
  await aanya.type('#postForm [name=price]', '2200');
  await aanya.select('#postForm [name=category]', 'Shoes');
  await aanya.select('#postForm [name=condition]', 'Good');
  await aanya.type('#postForm [name=size]', 'UK 7');
  await aanya.type('#postForm [name=description]', 'Brown leather, resoled last year.');
  await (await aanya.$('#photos')).uploadFile(photo);
  await aanya.waitForFunction(() => document.querySelectorAll('#preview img').length === 1);
  await shot(aanya, 'sell-form');
  await aanya.click('#postForm button.btn');
  await aanya.waitForFunction(() => location.hash === '#/mine', { timeout: 8000 });
  await aanya.waitForSelector('#mineList .row');
  check(await aanya.$$eval('#mineList .row strong', (e) => e.some((x) => x.textContent === 'Leather boots')), 'the new listing appears under My listings');
  check((await aanya.$eval('#mineList .row img.thumb', (e) => e.naturalWidth)) > 0, 'its photo loads');
  await shot(aanya, 'my-listings');

  await go(aanya, '');
  await aanya.waitForFunction(() => document.querySelector('#count').textContent === '9 items', { timeout: 6000 }).catch(() => {});
  check((await count(aanya, '.card')) === 9, 'it shows on the home page for everyone');

  // ---- buyer chats, live ----
  const rohan = await newSession();
  await login(rohan, 'rohan@navya.test');
  await go(rohan, '');
  await rohan.waitForSelector('.card');
  await set(rohan, '#q', 'boots');
  await pause(700);
  await rohan.click('.card a');
  await rohan.waitForSelector('[data-chat]');
  await rohan.click('[data-chat]');
  await rohan.waitForSelector('#chatForm', { visible: true });
  await rohan.waitForFunction(() => document.querySelector('#chatForm').dataset.id);
  const chatId = await rohan.$eval('#chatForm', (f) => f.dataset.id);
  await rohan.type('#chatForm [name=text]', 'Is the size true to fit?');
  await rohan.click('#chatForm button');
  await rohan.waitForSelector('.msg.me');
  check(true, 'the buyer sends a message and sees it');

  await go(aanya, 'chat/' + chatId);
  await aanya.waitForSelector('.msg');
  check((await aanya.$eval('.msg p', (e) => e.textContent)) === 'Is the size true to fit?', 'the seller sees it in the chat');
  await pause(800); // let the WebSocket connect
  await rohan.type('#chatForm [name=text]', 'Also, can you do 2000?');
  await rohan.click('#chatForm button');
  await aanya.waitForFunction(() => document.querySelectorAll('.msg').length === 2, { timeout: 6000 });
  check(true, 'a second message reaches the open chat live, without a reload');
  await aanya.type('#chatForm [name=text]', 'Yes, 2000 works.');
  await aanya.click('#chatForm button');
  await rohan.waitForFunction(() => document.querySelectorAll('.msg').length === 3, { timeout: 6000 });
  check(true, 'and the reply reaches the buyer live');
  await shot(rohan, 'chat');
  await go(aanya, 'chats');
  await aanya.waitForSelector('#chatList .row');
  check((await aanya.$eval('#chatList .row small', (e) => e.textContent)) === 'Yes, 2000 works.', 'the inbox shows the last message');
  await rohan.evaluate(() => { document.querySelector('#chatForm [name=text]').value = '<b>bold</b>'; });
  await rohan.click('#chatForm button');
  await pause(600);
  check((await rohan.$$eval('.msg p', (e) => e[e.length - 1].textContent)) === '<b>bold</b>' && (await count(rohan, '.msg p b')) === 0, 'HTML typed in a message is shown as text, not run');

  // ---- save a listing ----
  await go(rohan, '');
  await rohan.waitForSelector('.card');
  await rohan.click('.card .heart');
  await pause(500);
  check((await count(rohan, '.card .heart.on')) === 1, 'the heart saves an item');
  await rohan.click('#filterBtn');
  await rohan.click('#filterPanel [name=favs]');
  await pause(700);
  check((await count(rohan, '.card')) === 1, '"Saved only" shows just that item');

  // ---- boost: a declined card, then a good one ----
  await go(aanya, 'mine');
  await aanya.waitForSelector('#mineList .row');
  await aanya.evaluate(() => [...document.querySelectorAll('#mineList .row')].find((r) => r.textContent.includes('Leather boots')).querySelector('a[href^="#/boost"]').click());
  await aanya.waitForFunction(() => location.hash.startsWith('#/boost/'));
  await aanya.waitForFunction(() => document.querySelector('#boostInfo').textContent.length > 0);
  await aanya.select('#boostForm [name=token]', 'tok_declined');
  await aanya.click('#boostPay');
  await aanya.waitForFunction(() => document.querySelector('#boostErr').textContent.length > 0);
  check((await aanya.$eval('#boostErr', (e) => e.textContent)).includes('declined'), 'a declined test card shows an error');
  await aanya.select('#boostForm [name=token]', 'tok_success');
  await aanya.click('#boostPay');
  await aanya.waitForFunction(() => location.hash === '#/orders', { timeout: 8000 });
  await aanya.waitForSelector('#orderList .row');
  check((await aanya.$$eval('#orderList .row .pill', (e) => e.map((x) => x.textContent))).join() === 'Paid,Failed', 'orders show one paid and one failed boost');
  await shot(aanya, 'orders');
  await go(aanya, '');
  await aanya.waitForSelector('.card .badge.feat', { timeout: 6000 }).catch(() => {}); // the list refreshes a moment after the page shows
  check((await aanya.$eval('.card .body strong', (e) => e.textContent)) === 'Leather boots', 'the boosted listing is first on the home page');
  check((await count(aanya, '.card .badge.feat')) === 1, 'and carries the Featured badge');
  await shot(aanya, 'home-boosted');

  // ---- report and moderation ----
  const meera = await newSession();
  await login(meera, 'meera@navya.test');
  await go(meera, '');
  await meera.waitForSelector('.card');
  await meera.click('.card a');
  await meera.waitForSelector('[data-report]');
  await meera.click('[data-report]');
  await meera.waitForFunction(() => /review/.test(document.querySelector('#toast').textContent), { timeout: 5000 });
  check(true, 'a member can report a listing');
  const admin = await newSession();
  await login(admin, 'admin@navya.test');
  check(await admin.$eval('a[href="#/admin"]', (e) => !e.hidden), 'the admin sees the Reports link');
  await go(admin, 'admin');
  await admin.waitForSelector('#reportList .row');
  check((await count(admin, '#reportList .row')) === 1, 'the report is in the moderation queue');
  const reportedTitle = await admin.$eval('#reportList .row strong', (e) => e.textContent);
  await shot(admin, 'admin');
  await admin.click('[data-resolve][data-action=remove]');
  await pause(800);
  check((await count(admin, '#reportList .row')) === 0, 'taking it down clears the queue');
  await go(anon, '');
  await anon.waitForSelector('.card');
  check(!(await anon.$$eval('.card strong', (e) => e.map((x) => x.textContent))).includes(reportedTitle), 'the removed listing is gone from the home page');
  check(await rohan.evaluate(() => !document.querySelector('a[href="#/admin"]') || document.querySelector('a[href="#/admin"]').hidden), 'a normal member does not see the admin link');
  await go(rohan, 'admin');
  await pause(800);
  check(!rohan.url().endsWith('#/admin') || (await rohan.$eval('#reportList', (e) => e.children.length)) === 0, 'a normal member cannot open the admin page');

  // ---- phone width ----
  const phone = await newSession(390, 800);
  await go(phone, '');
  await phone.waitForSelector('.card');
  const overflow = await phone.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 1);
  check(!overflow, 'at 390 px wide the page does not scroll sideways');
  await phone.click('#menuBtn');
  await pause(500);
  await shot(phone, 'phone-menu');

  check(errors.length === 0, 'no console or page errors during the whole run' + (errors.length ? ': ' + errors.join(' / ') : ''));
} catch (e) {
  console.error('FAIL (test stopped) ' + e.message);
  process.exitCode = 1;
} finally {
  await browser.close();
}
console.log(`\n${checks} checks, ${process.exitCode ? 'some FAILED' : 'all passed'}`);

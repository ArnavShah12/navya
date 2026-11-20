// Simulated pilot: 40 invented members use a running Navya for a simulated week. The members and their choices are
// random (fixed seed), the app, its answers and its numbers are real. Results are printed and written to
// load/pilot-results.json. Run against a fresh app:  E2E_SCRIPT=../load/simulated-pilot.mjs e2e/run.sh
// (or BASE_URL=http://localhost:8080 node load/simulated-pilot.mjs).
import fs from 'node:fs';

const BASE = (process.env.BASE_URL || 'http://localhost:8089').replace(/\/$/, '');
let seed = 20261125;
const rnd = () => { seed |= 0; seed = (seed + 0x6D2B79F5) | 0; let t = Math.imul(seed ^ (seed >>> 15), 1 | seed); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
const pick = (a) => a[Math.floor(rnd() * a.length)];

async function call(method, path, { token, body, ip } = {}) {
  const res = await fetch(BASE + path, { method, headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: 'Bearer ' + token } : {}), ...(ip ? { 'X-Forwarded-For': ip } : {}) }, body: body ? JSON.stringify(body) : undefined });
  let json = null;
  if ((res.headers.get('content-type') || '').includes('json')) { try { json = await res.json(); } catch { /* empty */ } }
  return { status: res.status, json };
}

const CITIES = ['Pune', 'Mumbai', 'Bengaluru', 'Delhi'];
const ITEMS = [['Denim jacket', 'Clothes', 900], ['Kurta', 'Clothes', 450], ['Hoodie', 'Clothes', 700], ['Sneakers', 'Shoes', 1200], ['Sandals', 'Shoes', 350], ['Backpack', 'Bags', 800], ['Tote bag', 'Bags', 500], ['Scarf', 'Accessories', 250], ['Belt', 'Accessories', 300]];
const CONDS = ['New with tags', 'Like new', 'Good', 'Fair'];
const stats = { members: 0, listings: 0, chatsStarted: 0, messages: 0, markedSold: 0, boostsPaid: 0, boostsDeclined: 0, boostsPending: 0, reports: 0, errors: 0, statusCounts: {} };
const count = (r) => { stats.statusCounts[r.status] = (stats.statusCounts[r.status] || 0) + 1; if (r.status >= 500) stats.errors += 1; return r; };

const members = [];
for (let i = 1; i <= 40; i += 1) {
  const city = pick(CITIES);
  const r = count(await call('POST', '/api/auth/signup', { ip: `10.1.${i}.1`, body: { consent: true, email: `pilot${i}@example.com`, name: `Member ${i}`, city, password: 'pilot-password-' + i } }));
  if (r.status === 201) { members.push({ token: r.json.token, id: r.json.user.id, city, listings: [] }); stats.members += 1; }
}

// day 1 to 3: members list items, 60% of members list one to three
for (const m of members) {
  if (rnd() < 0.4) continue;
  for (let k = 0; k < 1 + Math.floor(rnd() * 3); k += 1) {
    const [title, category, price] = pick(ITEMS);
    const r = count(await call('POST', '/api/listings', { token: m.token, body: { title: `${title} ${Math.floor(rnd() * 1000)}`, price: Math.round(price * (0.7 + rnd() * 0.6)), category, condition: pick(CONDS), size: pick(['S', 'M', 'L', '']), city: m.city, description: 'Pilot listing, simulated member.', images: [] } }));
    if (r.status === 201) { m.listings.push(r.json.id); stats.listings += 1; }
  }
}

// day 3 to 7: buyers browse, chat and sometimes the seller marks the item sold; some sellers boost; a few report
const all = members.flatMap((m) => m.listings.map((id) => ({ id, seller: m })));
for (const buyer of members) {
  for (let k = 0; k < 3 && all.length; k += 1) {
    const l = pick(all);
    count(await call('GET', '/api/listings/' + l.id));
    if (l.seller === buyer || rnd() > 0.35) continue;
    const c = count(await call('POST', '/api/conversations', { token: buyer.token, body: { listingId: l.id } }));
    if (c.status !== 200) continue;
    stats.chatsStarted += 1;
    const send = async (who, text) => { const r = count(await call('POST', `/api/conversations/${c.json.id}/messages`, { token: who.token, body: { body: text } })); if (r.status === 201) stats.messages += 1; };
    await send(buyer, 'Is this still available?');
    if (rnd() < 0.8) await send(l.seller, 'Yes, it is.');
    if (rnd() < 0.3) { const r = count(await call('POST', `/api/listings/${l.id}/status`, { token: l.seller.token, body: { status: 'sold' } })); if (r.status === 200) stats.markedSold += 1; }
  }
}
for (const m of members) {
  if (!m.listings.length || rnd() > 0.2) continue;
  const token = pick(['tok_success', 'tok_success', 'tok_success', 'tok_declined', 'tok_timeout']);
  const r = count(await call('POST', `/api/listings/${m.listings[0]}/boost`, { token: m.token, body: { token, key: `pilot-${m.id}` } }));
  if (r.status === 200) stats.boostsPaid += 1; else if (r.status === 402) stats.boostsDeclined += 1; else if (r.status === 202) stats.boostsPending += 1;
}
for (let k = 0; k < 4 && all.length; k += 1) {
  const l = pick(all), who = pick(members);
  if (l.seller === who) continue;
  const r = count(await call('POST', '/api/reports', { token: who.token, body: { listingId: l.id, reason: 'Simulated report' } }));
  if (r.status === 201) stats.reports += 1;
}

await new Promise((r) => setTimeout(r, 7000)); // let the slow bank settle and the background check finish
const q = await call('GET', '/api/listings');
const pending = (await Promise.all(members.filter((m) => m.listings.length).map((m) => call('GET', '/api/orders', { token: m.token })))).flatMap((r) => r.json || []);
const orderStatus = pending.reduce((a, o) => ({ ...a, [o.status]: (a[o.status] || 0) + 1 }), {});
const out = { simulated: true, note: 'Members and choices are random (seed 20261125). The server and its answers are real.', ...stats, availableListingsVisible: q.json?.total, orderStatusAfterSettling: orderStatus };
console.log(JSON.stringify(out, null, 2));
fs.writeFileSync(new URL('./pilot-results.json', import.meta.url), JSON.stringify(out, null, 2));
process.exit(stats.errors ? 1 : 0);

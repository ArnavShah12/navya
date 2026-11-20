// Smoke test for a running Navya, used after every deploy and before demos.
//   BASE_URL=https://<id>.cloudfront.net node load/smoke.mjs
// Needs no seed data: it signs up two throw-away members, runs the main flow, then deletes what it made.

const BASE = (process.env.BASE_URL || '').replace(/\/$/, '');
if (!BASE) { console.error('Set BASE_URL'); process.exit(2); }

let failed = 0;
const started = Date.now();
function check(ok, msg) { console.log((ok ? 'PASS ' : 'FAIL ') + msg); if (!ok) failed += 1; return ok; }

async function call(method, path, { token, body } = {}) {
  const res = await fetch(BASE + path, {
    method, redirect: 'manual',
    headers: { ...(body ? { 'Content-Type': 'application/json' } : {}), ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  let json = null;
  if ((res.headers.get('content-type') || '').includes('json')) { try { json = await res.json(); } catch { /* empty */ } }
  return { status: res.status, json, res };
}

// the service may be starting after a scale-up: allow time for the first answer
async function waitForHealth() {
  for (let i = 0; i < 40; i += 1) {
    try { const r = await call('GET', '/health'); if (r.status === 200) return r; } catch { /* not up yet */ }
    await new Promise((r) => setTimeout(r, 3000));
  }
  return null;
}

const health = await waitForHealth();
if (!check(health && health.json?.status === 'ok', `GET /health is ok (storage: ${health ? health.json?.storage : 'no answer'})`)) process.exit(1);

if (BASE.startsWith('https://')) {
  const plain = await fetch(BASE.replace('https://', 'http://') + '/health', { redirect: 'manual' }).catch(() => null);
  check(plain && [301, 302, 307, 308].includes(plain.status), 'plain HTTP is redirected to HTTPS');
}

const page = await fetch(BASE + '/');
check(page.status === 200 && (await page.text()).includes('Navya'), 'home page is served');
check((page.headers.get('content-security-policy') || '').includes("default-src 'self'"), 'Content-Security-Policy header is present');
check(page.headers.get('x-content-type-options') === 'nosniff', 'X-Content-Type-Options header is present');

const list = await call('GET', '/api/listings');
check(list.status === 200 && Array.isArray(list.json?.items), `listing search answers (${list.json?.total ?? '?'} listings)`);

const run = Date.now();
const password = crypto.randomUUID(); // random per run: nothing fixed is left in the live database
const reg = async (n) => call('POST', '/api/auth/signup', { body: { consent: true, email: `smoke${n}${run}@example.com`, name: 'Smoke ' + n, city: 'Pune', password } });
const a = await reg('a'), b = await reg('b');
check(a.status === 201 && b.status === 201 && a.json?.token, 'two new members can sign up');
const bad = await call('POST', '/api/auth/login', { body: { email: `smokea${run}@example.com`, password: 'wrong-password' } });
check(bad.status === 401, 'a wrong password is refused');
check((await call('GET', '/api/admin/stats', { token: a.json?.token })).status === 403, 'a member cannot open admin stats');

let listingId;
try {
  const l = await call('POST', '/api/listings', { token: a.json.token, body: { title: 'Smoke test jacket', price: 100, category: 'Clothes', condition: 'Good', size: 'M', city: 'Pune', description: 'Created by the smoke test, deleted right after', images: [] } });
  listingId = l.json?.id;
  check(l.status === 201 && listingId, 'member A posts a listing');
  const found = await call('GET', '/api/listings?q=smoke%20test%20jacket');
  check(found.json?.items?.some((x) => x.id === listingId), 'it shows up in search');

  const jpeg = Buffer.from('/9j/4AAQSkZJRgABAQEASABIAAD/2wBDAP//////////////////////////////////////////////////////////////////////////////////////wAALCAABAAEBAREA/8QAFAABAAAAAAAAAAAAAAAAAAAAA//EABQQAQAAAAAAAAAAAAAAAAAAAAD/2gAIAQEAAD8AKp//2Q==', 'base64');
  const up = await fetch(BASE + '/api/images', { method: 'POST', headers: { Authorization: 'Bearer ' + a.json.token, 'Content-Type': 'image/jpeg' }, body: jpeg });
  const upJson = await up.json().catch(() => ({}));
  check(up.status === 201 && upJson.url, 'a photo uploads to storage');
  const back = upJson.url ? await fetch(BASE + upJson.url) : null;
  check(back && back.status === 200 && (back.headers.get('content-type') || '').includes('image/jpeg'), 'and comes back from storage');

  const conv = await call('POST', '/api/conversations', { token: b.json.token, body: { listingId } });
  check(conv.status === 200 && conv.json?.id, 'member B starts a chat');
  const msg = await call('POST', `/api/conversations/${conv.json?.id}/messages`, { token: b.json.token, body: { body: 'Is this available?' } });
  check(msg.status === 201, 'and sends a message');
  check((await call('GET', `/api/conversations/${conv.json?.id}/messages`, { token: a.json.token })).json?.length === 1, 'member A reads it');

  // live chat over WebSocket (Node 22+ has WebSocket built in)
  const ws = new WebSocket(BASE.replace(/^http/, 'ws') + '/ws/chat/' + conv.json?.id);
  const got = await new Promise((resolve) => {
    const seen = [];
    const t = setTimeout(() => resolve(seen), 8000);
    ws.onopen = () => ws.send(JSON.stringify({ token: a.json.token }));
    ws.onmessage = async (e) => {
      seen.push(JSON.parse(e.data).type);
      if (seen[0] === 'ready' && seen.length === 1) await call('POST', `/api/conversations/${conv.json?.id}/messages`, { token: b.json.token, body: { body: 'ping' } });
      if (seen.length === 2) { clearTimeout(t); resolve(seen); }
    };
    ws.onerror = () => resolve(seen);
  });
  ws.close();
  check(got.join() === 'ready,new', 'live chat: a new message is pushed over the WebSocket (got: ' + got.join() + ')');

  const boost = await call('POST', `/api/listings/${listingId}/boost`, { token: a.json.token, body: { token: 'tok_success', key: 'smoke-' + run } });
  check(boost.status === 200 && boost.json?.status === 'paid', 'a boost is paid with the test card');
  const declined = await call('POST', `/api/listings/${listingId}/boost`, { token: a.json.token, body: { token: 'tok_declined', key: 'smoke-d-' + run } });
  check(declined.status === 402, 'a declined test card is refused');
  check((await call('GET', '/api/orders', { token: a.json.token })).json?.length === 2, 'order history shows both attempts');
  check((await call('POST', `/api/listings/${listingId}/boost`, { token: b.json.token, body: { token: 'tok_success', key: 'x-' + run } })).status === 403, 'member B cannot boost A\'s listing');
} finally {
  if (listingId) check((await call('DELETE', `/api/listings/${listingId}`, { token: a.json.token })).status === 204, 'cleanup: the listing is deleted');
}

console.log(`\n${failed === 0 ? 'Smoke test passed' : failed + ' check(s) failed'} in ${((Date.now() - started) / 1000).toFixed(1)} s against ${BASE}`);
process.exit(failed ? 1 : 0);

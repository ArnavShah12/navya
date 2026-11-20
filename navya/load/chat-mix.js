// Logged-in traffic: the three seeded members open chats and send messages while others browse.
// Each member is limited to 30 messages a minute by the app, so 429 is an expected answer and is counted, not failed.
//   k6 run -e BASE_URL=http://localhost:8080 -e SEED_PASSWORD=<seed password> load/chat-mix.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';
const PW = __ENV.SEED_PASSWORD;
const limited = new Counter('messages_rate_limited');
const sent = new Counter('messages_sent');

export const options = {
  vus: Number(__ENV.VUS || 30),
  duration: '60s',
  thresholds: { http_req_failed: ['rate<0.01'], 'http_req_duration{kind:chat}': ['p(95)<500'] },
};

http.setResponseCallback(http.expectedStatuses(200, 201, 429)); // 429 is the app's own rate limit working

const json = { headers: { 'Content-Type': 'application/json' } };

export function setup() {
  const who = ['aanya', 'rohan', 'meera'].map((n) => {
    const r = http.post(`${BASE}/api/auth/login`, JSON.stringify({ email: `${n}@navya.test`, password: PW }), json);
    return { token: r.json('token'), id: r.json('user.id') };
  });
  // rohan chats on one of aanya's listings, once
  const auth = (t) => ({ headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + t } });
  const mine = http.get(`${BASE}/api/listings/mine`, auth(who[0].token)).json();
  const conv = http.post(`${BASE}/api/conversations`, JSON.stringify({ listingId: mine[0].id }), auth(who[1].token)).json('id');
  return { who, conv };
}

export default function (d) {
  const me = d.who[(__VU % 2)]; // aanya or rohan, both are in the chat
  const h = { headers: { 'Content-Type': 'application/json', Authorization: 'Bearer ' + me.token }, tags: { kind: 'chat' } };
  const r = http.post(`${BASE}/api/conversations/${d.conv}/messages`, JSON.stringify({ body: 'load test ' + Date.now() }), h);
  if (r.status === 429) limited.add(1);
  else { check(r, { 'sent': (x) => x.status === 201 }); sent.add(1); }
  check(http.get(`${BASE}/api/conversations/${d.conv}/messages`, h), { 'read is 200': (x) => x.status === 200 });
  check(http.get(`${BASE}/api/conversations`, h), { 'inbox is 200': (x) => x.status === 200 });
  sleep(1);
}

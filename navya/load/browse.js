// Read-heavy load: many people browsing, searching and opening listings. Needs a seeded app (SEED=true).
//   k6 run -e BASE_URL=http://localhost:8080 load/browse.js
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  stages: [
    { duration: '20s', target: 50 },
    { duration: '40s', target: Number(__ENV.VUS || 200) },
    { duration: '20s', target: 0 },
  ],
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
  },
};

const searches = ['', 'denim', 'shoes', 'pune', 'bag', 'kurta'];

export default function () {
  const q = searches[Math.floor(Math.random() * searches.length)];
  const list = http.get(`${BASE}/api/listings?q=${q}&sort=${Math.random() < 0.5 ? 'new' : 'low'}`);
  check(list, { 'list is 200': (r) => r.status === 200 });
  const items = list.json('items') || [];
  if (items.length) {
    const l = items[Math.floor(Math.random() * items.length)];
    check(http.get(`${BASE}/api/listings/${l.id}`), { 'detail is 200': (r) => r.status === 200 });
    if (l.images && l.images.length) check(http.get(BASE + l.images[0].url), { 'photo is 200': (r) => r.status === 200 });
  }
  sleep(Number(__ENV.SLEEP ?? 1)); // SLEEP=0 removes the think time to find the limit of one instance
}

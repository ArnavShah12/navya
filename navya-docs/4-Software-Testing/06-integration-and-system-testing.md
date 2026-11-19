# Integration and system testing

> Subject: Software Testing | Deliverable: end-to-end tests of the main user flows, including error paths | Data: real, 2026-11-25

## Layers

| Layer | What runs | Real parts | Replaced parts |
|---|---|---|---|
| Integration | Services and repositories | PostgreSQL 14, SQL, constraints, threads | Local file storage instead of S3, mock payment gateway, a movable clock |
| System over HTTP (`ApiTest`) | Javalin server on a free port | Everything above plus HTTP, JSON, filters, WebSocket, a second app instance on the same database | Same replaced parts |
| System in a browser (`navya/e2e/ui-smoke.mjs`) | Built jar, seeded data, headless Chrome | The real front end on a real server | Same replaced parts, embedded PostgreSQL |
| Smoke on a running site (`navya/load/smoke.mjs`) | Any URL | Whatever is deployed. Run on 2026-11-25 against the demo on AWS: real HTTPS (Let's Encrypt through Caddy), a managed PostgreSQL, a real S3 bucket | none |

## Main flows (from the brief) and where they are tested

| Flow | Happy path | Error paths |
|---|---|---|
| Register | TC-14, TC-54, browser (sign-up logs in) | Duplicate email TC-15, TC-20; short password TC-17, browser |
| Login | TC-14, browser | Wrong password TC-16, browser; rate limit TC-62; expired or forged token TC-18, TC-56 |
| Search | TC-30, TC-32, TC-33, browser (search, chip, price, clear) | Hostile text TC-31, TC-60; bad number TC-60; nothing found (browser empty state) |
| Post a listing with photos | TC-21, TC-24, browser (post with a photo) | Invalid fields TC-22; 5 photos TC-24; wrong file TC-25; someone else's photo TC-26 |
| Save an item | TC-34, browser | Saved filter without login TC-34 |
| Chat | TC-36, TC-38, TC-41, TC-65, browser (live, both directions) | Own listing and sold listing TC-37; stranger TC-39, TC-59; 2001 characters TC-40; double tap TC-42 |
| Pay (boost) | TC-43, TC-54, browser | Declined TC-44, TC-55, browser; slow bank TC-46, TC-55; double click TC-45, TC-50; not the owner TC-47 |
| Order history | TC-54, TC-55, browser (Orders shows Paid and Failed) | |
| Report and moderate | TC-51, TC-52, browser | Own listing, blank reason TC-51; member on admin routes TC-52, TC-58 |

The brief's examples mention out-of-stock items and failed payments. Navya has no stock count, so the matching error paths are "listing sold" (chat and boost refused) and "payment declined".

## Browser test: 40 checks, all pass (run R13)

Home lists 8 seeded items. Search, category chip, minimum price, empty state, clear, script text in the search box. Detail page opens. Message and My listings are behind login. Short password refused, valid sign-up works. Wrong password shows an error. Seller posts "Leather boots" with a photo and sees it in My listings, with a loading photo. Buyer chats, messages arrive live in both directions without reload, inbox shows the last message, typed HTML is shown as text. Save and "Saved only". Declined card shows an error, good card pays, Orders shows Paid and Failed, the boosted listing is first and carries the Featured badge. A member reports, the admin sees the queue, takes it down, it disappears from the home page. A normal member sees no admin link and cannot use the admin page. At 390 px wide the page does not scroll sideways. No console or page errors in the whole run.

Screenshots: `navya/e2e/shots/` (home, detail, sell form, my listings, chat, orders, boosted home, admin, phone menu).

## Not covered

Browsers other than Chrome. A real photo from a phone camera (the test re-uploads a seed photo). CloudFront (never deployed). More than two app instances. The browser test was not run against the cloud demo. Real HTTPS, S3 and a managed database are covered by the smoke test on the demo, see `10-post-deployment-smoke-test.md`.

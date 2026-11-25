# Post-deployment smoke test

> Subject: Software Testing | Deliverable: smoke test of the application running on the cloud | Data: the script is real. It was run against a local copy and, on 2026-11-25, against the live AWS demo (22 of 22). Screenshots of a run against the cloud URL are still to be added.

## Script

`navya/load/smoke.mjs`, started with `BASE_URL=<site url> node load/smoke.mjs`. It needs no seed data: it signs up two throw-away members with random passwords, runs the main flow, and deletes the listing it made. The deploy pipeline runs it after every roll-out and rolls back if it fails (`.github/workflows/deploy.yml`).

Checks (22 on an https URL, 21 on a plain http one): health is ok and reports the storage type; HTTP redirects to HTTPS (only when the URL is `https://`); home page served; CSP and nosniff headers; listing search answers; two members sign up; wrong password refused; member cannot open admin stats; post a listing; find it in search; upload a photo; photo comes back from storage as a JPEG; start a chat; send a message; read it as the seller; **live chat: a message is pushed over the WebSocket**; boost with the test card is paid; declined card is refused with 402; order history shows both attempts; another member cannot boost it; cleanup deletes the listing.

## Run against a local copy (real, 2026-11-25)

App on localhost with local file storage and embedded PostgreSQL:

```
Smoke test passed in 0.4 s against http://localhost:8091 (21 checks)
```

All 21 checks passed (the HTTPS check is skipped for an `http://` URL). The first attempt hung on the home page because the jar under the running app had been rebuilt; restarting from a copy of the jar fixed it, so this was a dev-setup slip, not an app defect.

## Run against the demo VM (real, 2026-11-25)

```
BASE_URL=http://15.135.194.233 node load/smoke.mjs      # first run, plain HTTP, local photo storage
Smoke test passed in 10.5 s (21 checks)
BASE_URL=https://32-236-220-241.sslip.io node load/smoke.mjs      # after HTTPS and S3
Smoke test passed in 12.6 s (22 checks)
BASE_URL=https://32-236-220-241.sslip.io node load/smoke.mjs      # after the move to the managed database
Smoke test passed in 12.4 s (22 checks)
BASE_URL=https://32-236-220-241.sslip.io node load/smoke.mjs      # after the consent box and legal pages were deployed
Smoke test passed in 12.9 s (22 checks)
```

The second run is the current deployment: HTTPS through Caddy, photos in S3 (`/health` reports `storage: s3`), the plain-HTTP redirect check included. The first run was on plain HTTP and local storage, 21 of 21: health with `storage: local`, home page, security headers, search (8 seeded listings), sign-up, wrong password refused, member cannot open admin stats, post a listing, photo upload and download, chat message read back, WebSocket push (`ready,new`), boost with the test card, declined card, order history, boost by a non-owner refused, cleanup. The 10.5 s against 0.4 s locally comes from the network distance to Sydney (0.39 s per round trip from the development machine). The HTTPS redirect check was skipped in the first run (plain HTTP) and passed in the second. Details: `../5-Cloud-Computing/11-demo-deployment-record.md`.

**Side effects of the smoke test (found 2026-11-25).** Each run signs up two members, pays two boosts and uploads one photo, and only deletes the listing. After eight runs against the demo: 16 extra users, 16 orders and 8 orphan photos. Run it against a database you can reset, or clean up afterwards. The demo database and bucket were cleaned on 2026-11-25 and the smoke test was not run again after that.

## Run against AWS (the designed stack, not run)

| Step | Expected |
|---|---|
| `BASE_URL=https://<id>.cloudfront.net node navya/load/smoke.mjs` | All 22 checks pass, including the HTTPS redirect, the photo round trip through S3, and the WebSocket check |
| Open the URL on a phone | Home page loads, search works |
| Browser dev tools: response headers | CSP and `Strict-Transport-Security` present |
| Direct request to the load balancer's address | 403 (it refuses requests without the CloudFront header) |
| Stop one task in the ECS console | The other keeps serving, ECS starts a replacement |
| CloudWatch dashboard | Requests and the boost counter move during the test |
| Alarm email | The SNS confirmation email was accepted |

Things that only the cloud run can show: whether CloudFront passes the WebSocket upgrade on its default domain, whether the task role can read and write the bucket (a missing `s3:ListBucket` would turn a missing key into a 403), whether the task can read its three SSM secrets, and whether the database accepts the `sslmode=require` connection.

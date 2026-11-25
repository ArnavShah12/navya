# Navya

Classifieds for pre-loved clothes, shoes, bags and accessories. Members post listings with photos, search by words, category, city and price, save items, and chat live with sellers without sharing a phone number. Members can report listings, and the Navya team takes them down. Sellers can pay a small fee to pin a listing to the top for seven days.

Java 21, Javalin, PostgreSQL, Amazon S3 (or a local folder). Plain HTML, CSS and JavaScript front end. Payments are simulated: there is no real gateway and no real money.

Course project (B.Tech 5th semester, integrated assignment). Team: Arnav Shah, Dipanshu Parmar. College: JG University. Mentor: Deep Paun. Ported from a single-page prototype that used Supabase.

## Status

Works and is tested on a laptop. A demo runs on one AWS Lightsail VM in Sydney, with a Lightsail managed PostgreSQL and S3 photos (`../navya-docs/5-Cloud-Computing/11-demo-deployment-record.md`, `infra/lightsail-demo.sh`). The full AWS stack (ECS, RDS, ALB, CloudFront) is **not** deployed: it stopped at CloudFront account verification. The Docker image was built on that VM, not on the development machine, and the S3 test has not been run against a real store (it runs in CI). Research figures in the documents are simulated and labelled. Details: `../navya-docs/4-Software-Testing/11-test-summary-report.md`.

## Documents

Every document the assignment asks for is in the sibling folder `../navya-docs/`, grouped per subject. Start at `../navya-docs/README.md`. The scripts that generated the simulated research files and the test-case tables are in `tools/`. Paths in those documents such as `navya/src/` and `navya/infra/` are inside this repository.

## Run it locally

Needs Java 21 and a PostgreSQL database (13 or later). Maven is included as a wrapper (`./mvnw`).

```
./mvnw package
DEV=true DB_URL='jdbc:postgresql://localhost:5432/navya?user=navya&password=...' \
  SEED=true SEED_PASSWORD='choose-a-password' java -jar target/navya.jar
```

Open http://localhost:8080. `SEED=true` creates demo data once: three members (`aanya@navya.test`, `rohan@navya.test`, `meera@navya.test`), a moderator (`admin@navya.test`) and 8 listings, all with `SEED_PASSWORD`. Without `SEED_PASSWORD` a random one is printed in the log once.

`DEV=true` lets the app invent its signing secret. Without it the app refuses to start unless `JWT_SECRET` (32+ characters) is set.

No PostgreSQL installed? `src/test/java/com/navya/DevPostgres.java` starts a throwaway one from a Maven artifact. `e2e/run.sh` does this for you.

### With Docker

```
cp .env.example .env     # fill in DB_PASSWORD and JWT_SECRET (openssl rand -hex 24)
docker compose up --build
```

Starts the app, PostgreSQL 16 and MinIO (S3 look-alike). Add `-f docker-compose.limits.yml` to run at the planned cloud task size (1 GiB, one core). `docker-compose.demo.yml` is the stack that runs on the demo VM (the app and Caddy for HTTPS, with a managed PostgreSQL and S3), started by `infra/lightsail-demo.sh`.

## Try the main flows

1. Log in as `aanya@navya.test`, open "Sell", post an item with a photo.
2. In a private window log in as `rohan@navya.test`, find it, "Message seller". Both windows update live.
3. As Aanya: My listings, Boost. Pay with "Test card: decline" (error), then "approve". Orders shows both. The listing is first on the home page with a Featured badge.
4. As Rohan or Meera: open a listing, Report. Log in as `admin@navya.test`, open Reports, Take down.

Payment test tokens: `tok_success` approves, `tok_declined` declines, `tok_timeout` answers "pending" and approves about 5 seconds later (exercises the slow-payment path). Anything else is declined.

## Tests

```
./mvnw verify                 # 84 tests, coverage report in target/site/jacoco/index.html
e2e/run.sh                    # fresh app and database, drives the UI in Chrome (40 checks)
E2E_SCRIPT=../load/simulated-pilot.mjs e2e/run.sh   # simulated pilot: 40 invented members on the real app
k6 run -e BASE_URL=http://localhost:8080 -e VUS=100 -e SLEEP=0 load/browse.js
k6 run -e BASE_URL=http://localhost:8080 -e SEED_PASSWORD=... load/chat-mix.js
BASE_URL=https://<site> node load/smoke.mjs        # post-deploy smoke test
```

`mvn verify` starts its own PostgreSQL, so it needs no setup. `S3StorageTest` runs only when `S3_TEST_ENDPOINT` is set (CI starts MinIO).

## Configuration

All settings are environment variables. No file in the repository holds a secret.

| Variable | Default | Meaning |
|---|---|---|
| `PORT` | 8080 | HTTP port |
| `DEV` | false | `true` invents the signing secret for local runs |
| `JWT_SECRET` | none | Signs login tokens, 32+ characters. Required unless `DEV=true` |
| `DB_URL` | none, required | `jdbc:postgresql://host:5432/db?sslmode=require` |
| `DB_USER`, `DB_PASSWORD` | none | Database login |
| `STORAGE` | local | `local` (folder under `DATA_DIR`) or `s3` |
| `S3_BUCKET`, `S3_REGION` | none, `ap-south-1` | Bucket for photos and exports. In the full ECS design the task role signs in, no keys. The demo uses a bucket-only IAM user whose key is in the VM's `.env`. The demo sets `ap-southeast-2` |
| `S3_ENDPOINT` | none | Only for MinIO or another S3-compatible store |
| `DATA_DIR` | ./data | Folder for local files, audit log and outbox |
| `BOOST_FEE_PAISE` | 4900 | Boost price in paise (4900 = Rs 49) |
| `BOOST_DAYS` | 7 | How long a boost lasts |
| `LISTING_LIFE_DAYS` | 60 | Listings older than this expire |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | none | Create a moderator account on start |
| `SEED`, `SEED_PASSWORD` | false | Create demo data on start |

## Project layout

```
src/main/java/com/navya/
  App, AppContext, Seeder     start-up, wiring, demo data
  config/                     environment settings
  domain/                     User (Member, Admin), Listing, Order, Conversation, Message, Report, Money
  exception/                  NavyaException and 11 specific errors, each with an HTTP status and code
  repo/                       Db (pool, schema, JDBC helpers) and one repository per area
  service/                    the rules: auth, listings, chat, boost, pricing rules, reports, export, audit, rate limits
  payment/                    PaymentGateway interface and MockGateway
  storage/                    FileStorage interface, local folder, S3
  worker/                     Notifier (queue thread), ChatHub (LISTEN/NOTIFY thread)
  web/Api.java                all HTTP and WebSocket routes
src/main/resources/public/    the web UI (index.html, script.js, style.css)
src/main/resources/db/        schema.sql
src/test/java/                84 tests
e2e/                          browser test
load/                         k6 load tests, smoke test
infra/                        CloudFormation, deploy, OIDC and teardown scripts, lightsail-demo.sh (the running demo)
tools/                        generators for the simulated research files and the test-case tables
Dockerfile, docker-compose*.yml; the CI workflows are in `../.github/workflows/` (repository root)
```

Documents: `../navya-docs/`.

## Known limits

- Payments are simulated. No payout, no real money.
- No email or SMS: notices go to `outbox.log` and the app log.
- Rate limits are per app instance and trust the first `X-Forwarded-For` value.
- Search reads every matching row. Fine at 5,000 listings (p95 136 ms under 100 users with no pause), not at hundreds of thousands.
- The app's database user can change the schema (migrations run at start-up).
- Terms of Use, Privacy Notice, an 18+ and consent box at sign-up, and a grievance page exist (`../navya-docs/2-E-Commerce/legal/`). Account deletion and data export are done by hand by the Grievance Officer, not in the app. Not reviewed by a lawyer.
- Two known HIGH findings in Jetty 11 (inside Javalin 6) are accepted in `.trivyignore`, inherited from the FestPass project and not re-scanned here.
- Tested in Chrome only. No accessibility audit beyond labels and focus styles.

## Credits

| Library | Use | Licence |
|---|---|---|
| Javalin 6 (Jetty) | HTTP and WebSocket server | Apache 2.0 |
| Jackson | JSON | Apache 2.0 |
| jBCrypt | password hashing | ISC |
| java-jwt (Auth0) | signed login tokens | MIT |
| HikariCP | database connection pool | Apache 2.0 |
| PostgreSQL JDBC | database driver | BSD-2-Clause |
| AWS SDK for Java v2 (S3) | object storage | Apache 2.0 |
| Logback and SLF4J | logging | EPL / LGPL, MIT |
| JUnit 5, AssertJ | tests | EPL 2.0, Apache 2.0 |
| zonky embedded-postgres | PostgreSQL for tests | Apache 2.0 |
| Puppeteer Core | browser test | Apache 2.0 |
| k6 | load tests | AGPL-3.0 (used as a tool, not linked) |
| MinIO | S3 look-alike for local runs and CI | AGPL-3.0 (used as a service, not linked) |
| Caddy | HTTPS reverse proxy on the demo VM | Apache 2.0 (run as a container, not linked) |
| PostgreSQL | database (Docker image for local runs and the embedded test server) and Lightsail managed PostgreSQL | PostgreSQL Licence |
| Let's Encrypt | free certificate for the demo's HTTPS | free public service |
| sslip.io | free public DNS names that resolve to an IP address, used for the demo hostname | free public service |

The UI uses the system font stack and inline SVG. Demo photos are plain generated shapes. Project layout, Docker files, CI patterns and several service patterns come from the earlier FestPass team project.

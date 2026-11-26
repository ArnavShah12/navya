# Navya

**A website to buy and sell pre-owned clothes, shoes, bags and accessories.**

Sellers post items with photos. Buyers search by name, category, city and price, save items they like, and chat live with sellers without sharing a phone number. Anyone can report a bad item and the Navya team removes it.

Built as the **B.Tech 5th semester integrated assignment** at JG University (Java, E-Commerce, Software Engineering/Testing, Cloud Computing, Startup Policies, Presentation Skills).

| | |
|---|---|
| **Team** | Arnav Shah, Dipanshu Parmar |
| **College** | JG University |
| **Mentor** | Deep Paun |
| **Live demo** | https://32-236-220-241.sslip.io |

> The payment system is still in progress and is not live. For now, buyer and seller meet and settle it themselves. Research figures (interviews, survey, usability session, pilot behaviour) are **simulated and labelled** as such in the documents.

---

## Tech stack

| Layer | Technology |
|---|---|
| Language / runtime | Java 21 |
| Web framework | Javalin 6 (Jetty), HTTP + WebSocket |
| Database | PostgreSQL (plain JDBC, HikariCP pool) |
| File storage | Amazon S3, or a local folder for development |
| Front end | Plain HTML, CSS and JavaScript (no framework) |
| Auth | bcrypt password hashing, signed JWT login tokens |
| Build / test | Maven (wrapper included), JUnit 5, AssertJ, JaCoCo, embedded PostgreSQL |
| Browser / load tests | Puppeteer Core, k6 |
| Packaging | Docker, Docker Compose |
| Cloud | AWS (Lightsail demo running; ECS/RDS/ALB/CloudFront stack written as CloudFormation) |
| CI/CD | GitHub Actions |

The site started as a single-page prototype that used Supabase and was ported to this Java service.

---

## Repository layout

```
.
├── .github/workflows/     CI (build, tests, browser test, secret scan, lint, image build) and Deploy
├── navya/                 The application: code, tests, Docker files, infra scripts
│   ├── src/main/java/     Java source (com.navya.*)
│   ├── src/main/resources/public/   Web UI (index.html, script.js, style.css, legal pages)
│   ├── src/main/resources/db/       schema.sql
│   ├── src/test/java/     84 automated tests
│   ├── e2e/               Browser test (Chrome) and screenshots
│   ├── load/              k6 load tests, smoke test, simulated pilot
│   ├── infra/             CloudFormation, deploy / teardown scripts, Lightsail demo script
│   ├── tools/             Generators for simulated research files and test-case tables
│   ├── Dockerfile, docker-compose*.yml, .env.example, pom.xml
│   └── README.md          Detailed developer README for the app
└── navya-docs/            All assignment documents, one folder per subject
```

### Documents (`navya-docs/`)

Start at [`navya-docs/README.md`](navya-docs/README.md).

| Folder | Subject | Contents |
|---|---|---|
| [`1-Startup-Policies/`](navya-docs/1-Startup-Policies) | Startup Policies | Topic proposal and startup brief, Lean Canvas and Business Model Canvas, SWOT and PESTEL, MVP plan, funding plan, DPIIT readiness, IP and compliance note, `research-evidence/` |
| [`2-E-Commerce/`](navya-docs/2-E-Commerce) | E-Commerce | Design document, business model and revenue, store flow, logistics, digital marketing, legal and security checklist, operations and metrics, `legal/` (Terms of Use, Privacy Notice, Grievance policy) |
| [`3-Java/`](navya-docs/3-Java) | Java | UML and design diagrams, object-oriented design notes, source pointer |
| [`4-Software-Testing/`](navya-docs/4-Software-Testing) | Software Testing | Test plan, 72 designed test cases, decision tables, execution report, coverage, integration/system tests, performance/security/usability testing, defect log, regression log, smoke test, test summary report |
| [`5-Cloud-Computing/`](navya-docs/5-Cloud-Computing) | Cloud Computing | Architecture, deployment, containerisation, storage and database, security, scalability, monitoring, CI/CD, cost estimate and script, operations and limits, demo deployment record |
| [`6-Final-Submission/`](navya-docs/6-Final-Submission) | Final report | [Final project report](navya-docs/6-Final-Submission/01-final-project-report.md) with the requirements traceability table |

---

## Features

- Sign up and log in (18+ and consent checkbox at sign-up)
- Post, edit, mark as sold and delete items, with photos
- Search and filter by name, category, city and price; save favourites
- Live chat between buyer and seller (WebSocket, works even with several app servers running)
- Report an item; the admin reviews reports and removes items
- Admin stats, activity log and CSV/JSON export
- Terms of Use, Privacy Notice and Grievance Officer pages

## In progress and future plans (not live yet)

- **Payment system** (in progress).
- **Boost:** sellers pay a small fee to show their item at the top of the page.
- **Ads** on the site.
- **Commission** on each deal made through the site.

## Architecture

```
Browser (HTML/CSS/JS)
      │  HTTP + WebSocket
      ▼
web/Api  ──►  service/  ──►  repo/  ──►  PostgreSQL
                 │
                 └──►  storage/   FileStorage (local folder | S3)

worker/   Notifier (queue thread), ChatHub (PostgreSQL LISTEN/NOTIFY thread)
```

**Object-oriented design.** `User` is the base class, with two types: `Member` (a normal buyer or seller) and `Admin`. File storage sits behind an interface, `FileStorage`, with a local-folder version and an S3 version. `NavyaException` has 11 specific subclasses, each with an HTTP status and code, handled in one place.

**Concurrency.** A background thread that expires old items, a notification queue, and a chat listener on `LISTEN/NOTIFY`. Races are settled by database constraints and covered by tests (12 simultaneous sign-ups with one email, 40 simultaneous chat starts, two app instances on one database).

**Database tables:** `users`, `listings`, `listing_images`, `favourites`, `conversations`, `messages`, `reports`, `orders` (see `navya/src/main/resources/db/schema.sql`).

---

## Quick start

You need **Java 21** and a **PostgreSQL 13+** database. Maven is included as a wrapper.

```bash
cd navya
./mvnw package

DEV=true \
DB_URL='jdbc:postgresql://localhost:5432/navya?user=navya&password=...' \
SEED=true SEED_PASSWORD='choose-a-password' \
java -jar target/navya.jar
```

Open http://localhost:8080.

- `SEED=true` creates demo data once: users `aanya@navya.test`, `rohan@navya.test`, `meera@navya.test`, an admin `admin@navya.test`, and 8 items, all using `SEED_PASSWORD`.
- `DEV=true` lets the app invent its signing secret. Without it, set `JWT_SECRET` (32+ characters).
- No PostgreSQL installed? `e2e/run.sh` starts a throwaway one for you.

### With Docker

```bash
cd navya
cp .env.example .env        # fill in DB_PASSWORD and JWT_SECRET (openssl rand -hex 24)
docker compose up --build
```

Starts the app, PostgreSQL 16 and MinIO (an S3 look-alike). Add `-f docker-compose.limits.yml` to run at the planned cloud task size (1 GiB, one core).

### Try the main flows

1. Log in as `aanya@navya.test`, open **Sell**, post an item with a photo.
2. In a private window log in as `rohan@navya.test`, find it, click **Message seller**. Both windows update live.
3. As Rohan or Meera: open an item and **Report**. Log in as `admin@navya.test`, open **Reports**, **Take down**.

---

## Configuration

All settings are environment variables. No file in the repository holds a secret.

| Variable | Default | Meaning |
|---|---|---|
| `PORT` | `8080` | HTTP port |
| `DEV` | `false` | `true` invents the signing secret for local runs |
| `JWT_SECRET` | none | Signs login tokens, 32+ characters. Required unless `DEV=true` |
| `DB_URL` | none, required | `jdbc:postgresql://host:5432/db?sslmode=require` |
| `DB_USER`, `DB_PASSWORD` | none | Database login |
| `STORAGE` | `local` | `local` (folder under `DATA_DIR`) or `s3` |
| `S3_BUCKET`, `S3_REGION` | none, `ap-south-1` | Bucket for photos and exports |
| `S3_ENDPOINT` | none | Only for MinIO or another S3-compatible store |
| `DATA_DIR` | `./data` | Local files, audit log and outbox |
| `LISTING_LIFE_DAYS` | `60` | Items older than this many days expire |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | none | Create an admin account on start |
| `SEED`, `SEED_PASSWORD` | `false` | Create demo data on start |

## API overview

| Area | Routes |
|---|---|
| Health | `GET /health` |
| Auth | `POST /api/auth/signup`, `POST /api/auth/login`, `GET /api/me` |
| Listings | `GET/POST /api/listings`, `GET /api/listings/mine`, `GET/PUT/DELETE /api/listings/{id}`, `POST /api/listings/{id}/status` |
| Sellers, photos | `GET /api/sellers/{id}`, `POST /api/images`, `GET /img/{owner}/{file}` |
| Favourites | `GET /api/favourites`, `POST /api/favourites/{id}` |
| Chat | `GET/POST /api/conversations`, `GET /api/conversations/{id}`, `GET/POST /api/conversations/{id}/messages`, WebSocket `/ws/chat/{id}` |
| Reports | `POST /api/reports` |
| Admin | `GET /api/admin/reports`, `POST /api/admin/reports/{id}/{action}`, `GET /api/admin/audit`, `GET /api/admin/stats`, `GET /api/admin/export.csv`, `POST /api/admin/export` |

---

## Testing

```bash
cd navya
./mvnw verify          # 84 tests; coverage report in target/site/jacoco/index.html
e2e/run.sh             # fresh app + database, drives the UI in Chrome (40 checks)
k6 run -e BASE_URL=http://localhost:8080 -e VUS=100 -e SLEEP=0 load/browse.js
BASE_URL=https://<site> node load/smoke.mjs     # post-deployment smoke test
```

`mvn verify` starts its own PostgreSQL, so it needs no setup.

| Measure | Result |
|---|---|
| Automated tests | 84 (83 pass, 1 skipped, 0 fail) |
| Designed test cases | 72 (71 pass, 1 needs MinIO) |
| Coverage | 83.3% lines, 72.5% branches |
| Browser checks | 40 of 40 |
| Smoke test, live AWS demo | 22 of 22 |
| Load, half CPU and 1 GiB, 5,008 items, 100 users | 1,079 req/s, p95 136 ms, 0 failures |
| Defects | 13 found, 12 fixed or worked around, 1 open |

Details: [`navya-docs/4-Software-Testing/11-test-summary-report.md`](navya-docs/4-Software-Testing/11-test-summary-report.md).

## Cloud and CI/CD

- **What is deployed:** a demo on one AWS Lightsail VM in Sydney with a managed PostgreSQL, S3 photos, and HTTPS through Caddy and Let's Encrypt. Script: `navya/infra/lightsail-demo.sh`. Record: [`11-demo-deployment-record.md`](navya-docs/5-Cloud-Computing/11-demo-deployment-record.md).
- **What is designed but not deployed:** the full AWS stack (ECS Fargate, RDS, ALB, CloudFront, IAM, auto scaling, alarms) in `navya/infra/main.yaml`. It lints clean with cfn-lint but stopped at CloudFront account verification.
- **GitHub Actions:**
  - `ci.yml` builds, runs unit, integration and browser tests, scans for secrets (gitleaks), lints CloudFormation and the Dockerfile, and builds the container image.
  - `deploy.yml` tests, builds and pushes to ECR, rolls out on ECS, smoke-tests, and rolls back on failure. It signs in to AWS with OpenID Connect (no stored AWS key) and is skipped until `AWS_DEPLOY_ROLE_ARN` is set.

Infra guide: [`navya/infra/README.md`](navya/infra/README.md). Running the full stack always-on costs about USD 95 a month, so read the operations document before deploying.

## Known limits

- The payment system is still in progress. There are no live payments, no payouts and no real money.
- No email or SMS: notices go to `outbox.log` and the app log.
- Search reads every matching row. Fine at 5,000 items, not at hundreds of thousands.
- Rate limits are per app instance.
- Account deletion and data export are handled by hand by the Grievance Officer, not in the app.
- Legal pages have not been reviewed by a lawyer.
- Tested in Chrome only.

Full list: [`navya-docs/4-Software-Testing/08-defect-log.md`](navya-docs/4-Software-Testing/08-defect-log.md).

## Credits

Javalin, Jackson, jBCrypt, java-jwt, HikariCP, PostgreSQL JDBC, AWS SDK for Java v2, Logback/SLF4J, JUnit 5, AssertJ, zonky embedded-postgres, Puppeteer Core, k6, MinIO, Caddy, Let's Encrypt and sslip.io. Licences are listed in [`navya/README.md`](navya/README.md#credits). Project layout, Docker files and CI patterns come from the earlier FestPass team project.

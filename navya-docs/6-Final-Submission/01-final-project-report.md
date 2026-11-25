# Final project report

B.Tech 5th semester integrated assignment: Java, E-Commerce, Software Engineering (Testing), Cloud Computing, Startup Policies, Presentation Skills.

Team: Arnav Shah, Dipanshu Parmar. College: JG University. Mentor: Deep Paun. Live URL: `https://32-236-220-241.sslip.io` (demo VM in Sydney, HTTPS through Caddy and Let's Encrypt, may be offline after the demo). Code repository: `navya/`.

## 1. Summary

Navya is a classifieds site for pre-loved clothes, shoes, bags and accessories in Indian cities. Members post listings with photos, search by words, category, city and price, save items, chat live with sellers without sharing a phone number, and report listings for the team to review. Sellers can pay a small fee (Rs 49 for seven days, test mode) to pin a listing to the top. It is written in Java 21 with a plain HTML, CSS and JavaScript front end, stores data in PostgreSQL and photos in S3, and is packaged as one container. It runs as a demo on AWS (one Lightsail VM with a managed PostgreSQL and S3, HTTPS, in Sydney). The fuller design for AWS (ECS Fargate behind CloudFront and a load balancer) is written as code and lints clean but did not deploy.

The site started as a single-page prototype that used Supabase. It was ported to a Java service in this project.

What exists and was tested: 2,754 lines of Java, 1,411 lines of tests and 1,054 lines of UI; 84 automated tests (83.3% of lines, 72.5% of branches); a 40-check browser test; load tests (1,079 requests per second at p95 136 ms on half a CPU and 1 GiB with 5,008 listings); a smoke test that passes 22 of 22 on the live AWS demo; a 50-user load run on the demo with 0 failures; a restore drill on the demo database; a simulated pilot of 40 members on the real app; 13 defects found, 12 fixed or worked around and 1 open; infrastructure code for AWS that passes cfn-lint; a cost estimate from real AWS list prices. Payments run through a mock gateway in test mode.

## 2. Problem and users

Students and early-career members aged 18 to 26 resell items on OLX, Instagram and WhatsApp groups. The simulated survey (60 respondents) names the pain points: few offers, sharing a phone number, searching by city. Published market estimates for second-hand apparel in India are a few billion dollars and growing about 13% a year, with vendors disagreeing on scope. India has 4.33 crore students in 45,473 colleges. Sources and trust levels: `1-Startup-Policies/research-evidence/real-world-benchmarks.md`. The user figures are simulated and labelled (`1-Startup-Policies/research-evidence/README.md`) and must be replaced with real interviews. A simulated pilot on the running app is in `1-Startup-Policies/research-evidence/pilot.md`.

## 3. Startup analysis

Brief, MVP plan: `1-Startup-Policies/00-topic-proposal-and-startup-brief.md`, `1-Startup-Policies/03-mvp-plan.md`. Lean Canvas and Business Model Canvas: `1-Startup-Policies/01-lean-canvas-and-business-model-canvas.md`. SWOT and PESTEL: `1-Startup-Policies/02-swot-and-pestel-analysis.md`. Funding plan with break-even arithmetic: `1-Startup-Policies/04-funding-plan.md`. DPIIT readiness: `1-Startup-Policies/05-dpiit-recognition-readiness.md`. IP and compliance note: `1-Startup-Policies/06-ip-and-compliance-note.md`. Headline from the funding plan: running always on costs about USD 95 a month before traffic matters, so a pilot runs one task or demo windows only (about USD 9 a month in demo windows).

## 4. E-commerce design

Revenue streams, platform flow, payment approach, logistics, marketing plan, legal and security checklist with dark patterns: `2-E-Commerce/`. Key points: the only revenue is a boost fee (a hypothesis to test), the price is shown before paying, there are no timers or pre-ticked options, handover is in person in a public place. Legal documents written and published on the site (`2-E-Commerce/legal/`): Terms of Use, Privacy Notice, Grievance Officer and Redressal Policy (the officer is required by the Consumer Protection (E-Commerce) Rules 2020 and the IT Intermediary Guidelines 2021), plus an 18+ and consent box at sign-up that is enforced by the API and recorded. Gaps stated in the checklist: real gateway, self-service account deletion and export (done by hand on request), lawyer review.

## 5. Software design (Java)

### Architecture

Layers: `web/Api` (routes, error mapping, WebSocket) calls services (`service/`), which use repositories (`repo/`), a payment gateway (`payment/`) and file storage (`storage/`). Domain classes in `domain/`, background threads in `worker/`. Diagrams: `3-Java/01-uml-and-design-diagrams.md`.

### Object-oriented design

`User` is abstract with `Member` and `Admin`. Behaviour that changes is behind an interface: `PricingRule` (weekend discount, whole-rupee rounding), `PaymentGateway`, `FileStorage` (local folder, S3). Money is whole paise, no floating point.

### Exceptions

`NavyaException` (status, code, message) has 11 subclasses that name real problems: invalid input, not found, forbidden, duplicate email, listing not available, self chat, too many images, payment failed, rate limited. One handler turns them into JSON answers. Unknown errors become a generic 500 with no details.

### Multithreading

A payment pool with a 3-second timeout, a reaper thread (expires old listings and settles slow payments), a notification queue with one worker, and a chat listener thread on PostgreSQL `LISTEN/NOTIFY` that lets chat work across several app instances. Races are settled by database constraints, not Java locks. Tests: 12 simultaneous sign-ups with one email, 40 simultaneous chat starts, 10 identical payment requests, two app instances sharing a database.

### Files

Local file storage and S3 behind one interface, append-only audit log and outbox log, CSV and JSON export, photo upload.

### Design decisions and what we considered

- Plain JDBC with a typed schema instead of an ORM: short, visible SQL. Cost: more hand-written mapping.
- Photos upload through the app instead of straight to S3 with presigned URLs: the server checks size and file type. Cost: image bytes pass through the app.
- Search with `ILIKE` instead of a full-text index: simple and correct for thousands of listings. Cost: it reads every matching row, so it will not scale to hundreds of thousands (measured, open issue OI-5).
- Chat across instances through PostgreSQL notifications instead of Redis: one fewer system. Cost: one pooled connection held per instance, and no connection pooler in transaction mode.
- A strict Content-Security-Policy with no inline script or style. Cost: the card background is set from script.

## 6. Testing

Plan, cases, execution, coverage, load, security, usability, defects, regression, smoke and the summary: `4-Software-Testing/`. Numbers:

| Measure | Result |
|---|---|
| Designed cases | 72, each with an automated test and a result (71 pass, 1 needs MinIO) |
| Automated tests | 84 (83 pass, 1 skipped, 0 fail) |
| Coverage | 83.3% lines, 72.5% branches, 85.2% instructions; services 92.7% of lines |
| Browser checks | 40 of 40 |
| Smoke test (local) | 21 of 21 |
| Smoke test (live AWS demo, HTTPS, S3, managed database) | 22 of 22 |
| Defects | 13 found, 12 fixed or worked around, 1 open (DEF-009, CloudFront account verification), 11 open issues listed with their impact |
| Load, 0.5 CPU and 1 GiB, 5,008 listings, 100 users with no pause (laptop) | 1,079 requests per second, p95 136 ms, 0 failures |
| Load, live AWS demo, 8 listings, 50 users with a 1 s pause | 36.3 requests per second, p95 422 ms (about 390 ms is the network to Sydney), 0 failures of 2,928 |
| Restore drill, demo database | dump 0.62 s, restore 1.38 s, counts identical |
| Concurrency | 12 sign-ups, 40 chat starts, 10 payments, each settled by the database |
| Static checks (re-run 2026-11-25) | cfn-lint 1.61.0, actionlint, hadolint, shell and JavaScript syntax: clean |

Defects worth reading in detail: DEF-003 (security headers missing on the home page because static files skip the filter), DEF-007 (creation times from the database clock compared with the app clock, found by a test written for uncovered code), DEF-005 (a database version that new RDS instances refuse, found by cfn-lint before any spend). Honest stories about measurement: the first load figure of 12,000 requests per second was an artefact of 8 listings, and three apparent failures came from test set-up (a replaced jar, a wrong data folder, a stale page read).

## 7. Cloud

Architecture, service choices, deployment, containers, storage and database, security, scaling, monitoring, CI/CD, cost, clean-up: `5-Cloud-Computing/00-cloud-architecture-document.md`. Deployment steps and scripts: `navya/infra/README.md`. The app runs at `https://32-236-220-241.sslip.io` (demo VM, may be offline after the demo). `navya/load/smoke.mjs` was run against it by hand (22 of 22). The GitHub pipeline would run it after every release, but the pipeline has never run and its deploy job targets ECS. The demo deployment record (address, credentials locations, measurements, the problems met) is `5-Cloud-Computing/11-demo-deployment-record.md`.

## 8. Results

### Requirements traceability

| Brief requirement | Where | Evidence |
|---|---|---|
| Startup: Lean Canvas, BMC | `1-Startup-Policies/01-lean-canvas-and-business-model-canvas.md` | canvases |
| Startup: SWOT, PESTEL | `1-Startup-Policies/02-swot-and-pestel-analysis.md` | tables |
| Startup: MVP plan | `1-Startup-Policies/03-mvp-plan.md` | scope table, simulated pilot |
| Startup: funding plan | `1-Startup-Policies/04-funding-plan.md` | computed from list prices |
| Startup: DPIIT readiness | `1-Startup-Policies/05-dpiit-recognition-readiness.md` | checklist |
| Startup: IP and compliance note | `1-Startup-Policies/06-ip-and-compliance-note.md` | rules with sources, licences |
| Startup: pitch deck, funding and compliance note | `1-Startup-Policies/07-pitch-deck-and-funding-compliance-note/` | note. The deck is prepared separately |
| E-commerce: business model and revenue | `2-E-Commerce/01-business-model-and-revenue-streams.md` | boost fee, unit economics |
| E-commerce: store flow | `2-E-Commerce/02-store-platform-flow.md` | running app |
| E-commerce: payment approach | `2-E-Commerce/03-payment-approach.md` | `MockGateway`, idempotency tests |
| E-commerce: logistics or delivery plan | `2-E-Commerce/04-logistics-and-delivery-plan.md` | in-person handover |
| E-commerce: digital marketing plan | `2-E-Commerce/05-digital-marketing-plan.md` | channels and metrics |
| E-commerce: legal and security checklist | `2-E-Commerce/06-legal-and-security-checklist.md` | privacy, consent, fake listings, dark patterns |
| E-commerce: terms, privacy notice, grievance policy | `2-E-Commerce/legal/` | published at `/terms.html`, `/privacy.html`, `/grievance.html`; consent tests TC-70 to TC-72 |
| Java: classes and objects | `navya/src/`, `3-Java/01-uml-and-design-diagrams.md` | UML |
| Java: inheritance and interfaces | `User`, 3 interfaces | `3-Java/02-java-design-notes.md` |
| Java: custom exceptions | `exception/` | 11 classes, tests |
| Java: multithreading | `BoostService`, `AppContext`, `Notifier`, `ChatHub` | concurrency tests |
| Java: file management | `LocalFileStorage`, `S3FileStorage`, `AuditLog`, `ExportService` | storage and export tests |
| Testing: plan and strategy | `4-Software-Testing/01-test-plan-and-strategy.md` | |
| Testing: designed test cases | `4-Software-Testing/02-designed-test-cases.csv` | 72 |
| Testing: unit tests and coverage reflection | `navya/src/test`, `4-Software-Testing/05-code-coverage-and-reflection.md` | 84 tests |
| Testing: integration and system tests | `4-Software-Testing/06-integration-and-system-testing.md` | `ApiTest`, browser test |
| Testing: non-functional (load, security, usability) | `4-Software-Testing/07-non-functional-testing/` | k6 results, security checks, usability session (simulated) |
| Testing: defect log | `4-Software-Testing/08-defect-log.csv` | 13 defects, 1 open (DEF-009) |
| Testing: regression | `4-Software-Testing/09-regression-testing-log.md` | 12 runs |
| Testing: post-deployment smoke | `4-Software-Testing/10-post-deployment-smoke-test.md` | `navya/load/smoke.mjs`, 21 checks on HTTP and 22 on HTTPS, passed on the live demo |
| Testing: summary report | `4-Software-Testing/11-test-summary-report.md` | |
| Cloud: architecture diagram and justification | `5-Cloud-Computing/01-cloud-architecture-design.md` | |
| Cloud: deployment on a cloud platform | `5-Cloud-Computing/02-deployment-on-a-cloud-platform.md`, `5-Cloud-Computing/11-demo-deployment-record.md`, `navya/infra/` | Running demo (`lightsail-demo.sh`); CloudFormation stack did not deploy |
| Cloud: containerisation | `5-Cloud-Computing/03-containerisation.md`; `navya/Dockerfile`, compose files | hadolint clean |
| Cloud: storage and database | `5-Cloud-Computing/04-cloud-storage-and-database.md` | Lightsail managed PostgreSQL and S3 on the demo, tests on PostgreSQL 14 |
| Cloud: security (IAM, no keys, HTTPS, encryption note) | `5-Cloud-Computing/05-cloud-security.md` | Demo: Let's Encrypt HTTPS, bucket-only IAM user, private database. Design: task and CI roles, SSM |
| Cloud: scaling and reliability plan | `5-Cloud-Computing/06-scalability-and-reliability-plan.md` | auto scaling, backups |
| Cloud: monitoring and logging | `5-Cloud-Computing/07-monitoring-and-logging.md` | Demo: `/health`, `/api/admin/stats`, `docker compose logs`, Lightsail graphs. Design: CloudWatch logs, dashboard, alarms |
| Cloud: CI/CD | `5-Cloud-Computing/08-ci-cd-pipeline.md`; `.github/workflows` | actionlint clean |
| Cloud: cost estimate | `5-Cloud-Computing/09-cost-estimate.md` | computed from list prices |

### Status of items that depend on outside access

| Item | Status |
|---|---|
| Deployment on AWS and the live URL | Done as a one-VM demo (Lightsail, managed PostgreSQL, S3, HTTPS) with smoke test 22 of 22. The full ECS, RDS, ALB and CloudFront stack did not deploy (CloudFront account verification). `5-Cloud-Computing/11-demo-deployment-record.md` |
| Docker image build and compose run | The image was built and run on the demo VM. Not on the development machine (no Docker access there). |
| S3 test against a real store | JUnit test skipped without MinIO (runs in CI). Upload and download checked on real S3 by the demo smoke test. |
| Real interviews, survey, usability session | Simulated data in place, labelled |
| Dependency, secret and misconfiguration scan | trivy 0.76.2 run 2026-11-25: 2 accepted HIGH Jetty findings with no fix on Maven Central, no secret in git, 8 CRITICAL or HIGH findings in the undeployed ECS template. The container image was not scanned here (no Docker access), and gitleaks is not installed. |
| GitHub CI pipeline | Written, `actionlint` clean, never run: the repository has no remote yet |

## 9. What did not work and what we would change

- The token verifier checked expiry against the system clock while tokens were issued with the app clock (DEF-001). Found by the first full run of the authentication tests.
- Static files skipped the filter that sets security headers (DEF-003). We had tested the API, never the home page.
- Malformed JSON gave a 500 (DEF-002). We tested bad values, not bad syntax.
- Creation times came from two clocks (DEF-007). Found only when we wrote a test for code the earlier tests never reached.
- Infrastructure defects (DEF-005, DEF-006) were caught by a linter, not by us.
- The first load result was meaningless because the data was tiny. With 5,008 listings the same code did a tenth of the throughput. We would have loaded realistic data before the first measurement.
- Two of our own load and browser tests were wrong (counting rate-limit answers as failures, reading a stale page). Three apparent failures were test set-up mistakes: a rebuilt jar under a running app, a wrong data folder, a stale read.
- The AWS account sat in an organization that allowed only three regions and denied most services outside Sydney (DEF-008). We designed for Mumbai first and probed what the account allowed only after writing the templates. We would probe first.
- CloudFront refused a new distribution until AWS Support verified the account (DEF-009), and the failed stack could not roll back because its database was still being created (DEF-010). The full design never deployed. We built a smaller demo on one VM instead and documented the difference.
- A generated database password with shell characters broke a deployment script and was printed into a log (DEF-013). We set our own hex password.
- Our smoke test leaves data behind (users, orders, photos), which we only noticed when we looked at the live database (OI-11).
- A search of the web gave market sizes that differ several times over because vendors define "second-hand apparel" differently, so we quote a range and name the source.
- The first design assumed a presigned upload would be simpler. Checking and storing the photo in the app turned out to be simpler and safer.

## 10. Future work

Real gateway and payouts, self-service account deletion and export, lawyer review of the legal documents, full-text search, a shared rate limiter keyed on the load balancer's view of the address, a custom domain with HTTPS to the load balancer, email notices, a real pilot at one campus.

## 11. Team contributions

| Member | Areas |
|---|---|
| Dipanshu Parmar | Product and startup lead, Java lead and cloud, QA lead: startup policies documents, research, funding plan; application code, AWS infrastructure, CI/CD; review and sign-off of the test documents |
| Arnav Shah | E-commerce and UI lead, testing, operations: e-commerce design document, web UI, usability sessions; test plan, cases, execution, defect log, summary report; day-to-day operations |

## 12. Credits

Libraries and licences: `navya/README.md`. The first prototype (HTML, CSS, JavaScript with Supabase) was written by a team member. Project layout, Docker files, CI patterns and several service patterns come from the earlier FestPass team project. AWS price data and the market and rule figures are public sources listed in `1-Startup-Policies/research-evidence/real-world-benchmarks.md`. Demo pictures are generated by `Seeder`; no images or fonts were copied.

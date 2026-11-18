# Test plan and strategy

> Subject: Software Testing | Deliverable: test plan and strategy | Data: plan, with the actual outcome of each level in the other files

## Scope

In scope: the Java service (all routes, services, repositories, workers), the browser UI, the container and infrastructure files (linted), the CI pipelines (linted), and the deployed system once it exists.

Out of scope: the payment gateway's own behaviour (it is simulated), AWS services themselves, browsers other than Chrome, assistive-technology testing, penetration testing by a third party.

## Levels

| Level | What | Tools | Where results are |
|---|---|---|---|
| Unit | Validation, money, pricing rules, rate limiter, mock gateway, storage, config | JUnit 5, AssertJ | `04-automated-unit-tests-execution-report.md` |
| Integration | Services and repositories on a real PostgreSQL, two app instances sharing one database | JUnit 5, zonky embedded PostgreSQL 14 | same |
| System | Every route over real HTTP, WebSocket chat, then the UI in Chrome | `java.net.http`, Puppeteer | `06-integration-and-system-testing.md` |
| Acceptance | The main user flows in the brief: register, login, search, post, chat, pay, order history | Browser test, smoke script | `06-...`, `10-...` |
| Non-functional | Load, security, usability | k6, JUnit security cases, a usability session | `07-non-functional-testing/` |
| Post-deployment | Smoke test of the running cloud system | `navya/load/smoke.mjs` | `10-post-deployment-smoke-test.md` |
| Static checks | cfn-lint, hadolint, actionlint, shell syntax | those tools | `11-test-summary-report.md` |

## Techniques

Black box: equivalence partitioning, boundary value analysis, decision tables (`03-decision-tables.md`). White box: statement and branch coverage with JaCoCo and a written reflection (`05-code-coverage-and-reflection.md`). Experience-based: error guessing for security cases (SQL text, script tags, forged tokens, other members' ids).

## Test environment

| Environment | Setup |
|---|---|
| Developer laptop | Java 21, Maven wrapper, embedded PostgreSQL 14 started by the tests, local file storage |
| CI | GitHub Actions, Ubuntu, Java 21, MinIO as an S3 look-alike service |
| Local stack | `docker compose`: app, PostgreSQL 16, MinIO (written, not run here) |
| Cloud | Demo VM on AWS Lightsail in `ap-southeast-2` (`infra/lightsail-demo.sh`). The `infra/main.yaml` stack (ap-south-1 design) did not complete: CloudFront account verification |

Tests use a fresh, emptied database for each test and a controllable clock, so time-based rules (token expiry, listing expiry, boost end, weekend price) run without waiting.

## Roles

| Role | Who |
|---|---|
| Test lead | Dipanshu Parmar (QA lead) |
| Test authors | Arnav Shah and Dipanshu Parmar, each for the area they built |
| Usability participants | 5 people outside the team |

## Entry criteria

Code compiles. Previous level passes. For system tests, the stack starts and `/health` is ok.

## Exit criteria

All designed test cases executed. No open defect of severity High. Line coverage of services and repositories at least 80% (measured: 91.7% and 89.7%). Every open defect listed with its impact. Load test run at the target size without errors. Smoke test passes on the cloud stack.

## Defect handling

Each defect gets an ID, severity, priority, steps, status and the fix in `08-defect-log.csv`. After a fix the full suite is re-run, see `09-regression-testing-log.md`. Failures are never deleted from the logs.

## Risks to the test effort

Cloud behaviour (HTTPS, WebSocket through CloudFront, S3 permissions) cannot be fully tested before deployment. Mitigation: smoke test in the pipeline with automatic roll-back, and a manual checklist in `10-post-deployment-smoke-test.md`.

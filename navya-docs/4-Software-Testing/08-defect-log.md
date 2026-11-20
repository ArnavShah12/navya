# Defect log

> Subject: Software Testing | Deliverable: defect log | Data: real. Every defect below was found by a test, a linter, a review or a deployment attempt during this project. All are fixed or worked around except DEF-009. Raw table: `08-defect-log.csv`.

| ID | Severity | Found by | Summary | Status |
|---|---|---|---|---|
| DEF-001 | Medium | TC-14, TC-18 | Token verifier ignored the app clock | Fixed |
| DEF-002 | Medium | TC-60 | Malformed JSON gave HTTP 500 | Fixed |
| DEF-003 | High (security) | TC-61 | No security headers on the home page and static files | Fixed |
| DEF-004 | Low | Screenshot review | Featured badge covered the save heart | Fixed |
| DEF-005 | High | cfn-lint | Template pinned a PostgreSQL version that new instances cannot use | Fixed, confirmed on AWS (RDS created with the looked-up version) |
| DEF-006 | Medium | cfn-lint | Invalid YAML in the budget template | Fixed |
| DEF-007 | Medium | TC-68 | Creation times came from the database clock but were compared with the app clock | Fixed |
| DEF-008 | High | First AWS deploy | The default region `ap-south-1` is denied by the organization (only `us-east-1`, `us-west-2`, `ap-southeast-2` allowed) | Fixed: default is `ap-southeast-2` |
| DEF-009 | High | First AWS deploy | CloudFront refuses new distributions until AWS Support verifies the account, so the full stack cannot be created | Open (needs Support). Demo runs without CloudFront |
| DEF-010 | Medium | First AWS deploy | Stack rollback failed: RDS was still being created when CloudFormation tried its delete-time snapshot | Worked around (waited, deleted the stack). Template unchanged |
| DEF-011 | Medium | First AWS deploy | `deploy.sh` needs a reachable Docker daemon even when no task will run | Fixed: `INFRA_ONLY=1` stops before the image build |
| DEF-012 | Medium | First Lightsail deploy | Demo script used a bundle id that does not exist in Sydney, a `small` plan the account may not create, a key pair named like the instance, and `rsync` | Fixed: `micro_3_2`, `navya-demo-key`, `tar` over ssh |
| DEF-013 | Medium | Managed database setup | The generated master password contains shell characters, which broke sourcing `.env` on the VM and printed fragments of it into a log | Fixed: own hex password, `.env` read with grep. The leaked password was replaced |

Steps to reproduce, the fix and dates are in the CSV.

## Found while testing but not product defects

- A load-test script counted the app's own 429 rate-limit answers as failures. Script fixed (`navya/load/chat-mix.js`).
- A load-test run showed 2.65% failures at half a CPU. All were 404s for photos because the test instance pointed at a different data folder from the database. Setup error, run repeated with 0 failures (`07-non-functional-testing/results/load-browse-100vu-nosleep-half-cpu.txt`).
- Two browser-test checks failed once because they read the old list while the new one was loading. Test now waits for the fresh list. The page behaviour is listed as open issue OI-4.

## Open issues

| ID | Severity | Description |
|---|---|---|
| OI-1 | Medium | The JUnit test for `S3FileStorage` (TC-67) has not been run against an S3-compatible store on this machine (CI runs it against MinIO). Upload and download were exercised on real S3 by the smoke test on the demo VM (2026-11-25). Statement coverage of that class is 28%. |
| OI-2 | Medium | The Docker image was built and run on the demo VM, not on the development machine (this session has no access to the Docker daemon). The local compose stack with MinIO (`docker-compose.yml`) was never started. The Dockerfile passes hadolint. |
| OI-3 | High | The full CloudFormation stack has not completed on AWS: it stopped at CloudFront account verification (DEF-009). The budget stack deployed and RDS was created. The one-VM demo runs. |
| OI-4 | Low | The home page keeps showing the previous results for about a quarter of a second while the new search loads. |
| OI-5 | Low | Search reads every matching row with `ILIKE`. With 5,008 listings the p95 under load was 136 to 160 ms. It will not scale to hundreds of thousands of rows. |
| OI-6 | Medium | Rate limits are per instance and trust the first `X-Forwarded-For` value. The simulated pilot signed up 40 members from one machine by setting a different header on each request. |
| OI-7 | Medium | The runtime database user can change the schema (migrations run at start-up). |
| OI-8 | Medium | Two known HIGH vulnerabilities in Jetty 11 (inside Javalin 6) are accepted in `.trivyignore`, inherited from the FestPass project. Re-scanned on 2026-11-25 with trivy 0.76.2: both are still reported, and the fixed Jetty releases are not on Maven Central, so the only fix is moving to Javalin 7. The container image scan was not run here (no Docker access). |
| OI-9 | Low | No browser other than Chrome was tested. No accessibility audit beyond labels and focus styles. |
| OI-10 | Low | Account deletion and data export are manual: the Grievance Officer runs a tested procedure on request (`../2-E-Commerce/legal/data-requests-runbook.md`). Terms, Privacy Notice, consent and 18+ box, and the grievance page exist since 2026-11-25 (closed from the earlier version of this issue). The documents are not reviewed by a lawyer. |
| OI-11 | Low | Photos uploaded through `/api/images` and never attached to a listing are never removed. The smoke test leaves one per run (also on a laptop); in S3 they cost almost nothing but add up. Needs a periodic sweep or an upload that is attached in the same request. |

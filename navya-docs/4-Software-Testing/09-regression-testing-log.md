# Regression testing log

> Subject: Software Testing | Deliverable: regression testing evidence | Data: real. Dates are 2026-11-25. Counts are from the surefire reports of each run.

| Run | Trigger | What was re-run | Result |
|---|---|---|---|
| R1 | First full unit and service suite | `mvn test`, 31 tests | 2 errors (DEF-001), 29 pass |
| R2 | After the DEF-001 fix | `mvn test`, 31 tests | All pass |
| R3 | Added service and repository tests | `mvn test`, 65 tests | All pass |
| R4 | Added HTTP tests | `mvn test -Dtest=ApiTest`, 13 tests | 3 failures: DEF-002, DEF-003 and a test setup error (second instance had its own signing key) |
| R5 | After DEF-002, DEF-003 and the test fix | `mvn test`, 78 tests | All pass (full suite, everything earlier re-run) |
| R6 | Added browser test | `e2e/run.sh`, 39 checks | 2 failures, both reading stale results (test timing) |
| R7 | After the test fix | `e2e/run.sh` | 39 of 39 pass |
| R8 | After `/health` gained a `storage` field and the Featured badge moved (DEF-004) | `mvn verify`, 79 tests (1 skipped) | All pass. Coverage report produced. |
| R9 | Added TC-68, TC-69 for uncovered code paths | `mvn verify`, 81 tests | 1 failure (DEF-007), then a test-setup slip (my raw SQL insert used the database clock), then all pass |
| R10 | Final run, after the last code change (DEF-007) and the last front-end change (DEF-004) | `mvn verify`: 81 tests, and `e2e/run.sh`: 39 checks | 80 pass, 1 skipped, 0 fail. 39 of 39 pass. Coverage 85.0% instructions, 72.3% branches, 83.2% lines. cfn-lint, actionlint, hadolint, shell and JavaScript syntax checks all clean. |
| R11 | Documentation review, 2026-11-25 (no code change since R10) | `mvn verify`; `e2e/run.sh` (browser test); hadolint, actionlint, cfn-lint 1.61.0 on `infra/*.yaml`, `bash -n` on the four scripts, `node --check` on the load and browser scripts | 81 tests, 0 failures, 1 skipped. Browser test 39 of 39 pass. Coverage 83.2% lines, 72.3% branches, as in R10. All static checks clean. The generated test-case and research tables reproduce byte for byte from their scripts (`navya/tools/`). |
| R12 | Demo on AWS, 2026-11-25 (several runs as the setup changed) | `load/smoke.mjs` against the demo: plain HTTP with local photos, then HTTPS with S3 photos, then HTTPS with the managed database; k6 `browse.js`, 50 users | Smoke: 21 of 21, then 22 of 22 on each later run, including after each change. k6: 0 failures of 2,928. Details: `10-post-deployment-smoke-test.md`, `07-non-functional-testing/performance-testing.md`. The browser test was not re-run on the cloud. |
| R13 | Sign-up consent and legal pages added (consent columns, API check, form checkbox, three pages), 2026-11-25 | `mvn verify`; `e2e/run.sh` | 84 tests, 0 failures, 1 skipped (new: TC-70 to TC-72). Browser test 40 of 40 (new check: sign-up needs the box ticked). Coverage 83.3% lines, 72.5% branches, 85.2% instructions. Live demo redeployed and `load/smoke.mjs` run against it: 22 of 22 (12.9 s), consent stored on the two new accounts. trivy re-scan: see `07-non-functional-testing/security-testing.md`. |

Rule used: after any fix, the whole unit and service suite is re-run, not just the test that failed. The browser test is re-run after any front-end change. Failed runs are kept in this table.

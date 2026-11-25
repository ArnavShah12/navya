# Test summary report

> Subject: Software Testing | Deliverable: test summary report | Data: real, 2026-11-25. Numbers are from the last full run recorded in `09-regression-testing-log.md`.

## Planned versus executed

| Planned (`01-test-plan-and-strategy.md`) | Executed | Where |
|---|---|---|
| Unit tests | Yes | 84 JUnit tests in total, 83 run |
| Integration tests on a real database | Yes | same |
| System tests over HTTP, including two instances | Yes | `ApiTest`, 17 tests |
| Browser test of the main flows | Yes | 40 checks |
| At least 30 designed test cases | Yes | 72 (TC-01 to TC-72) |
| Load test | Yes. On a laptop, and once against the live AWS demo | 5 k6 runs on the laptop, 1 on the demo (50 users, 0 failures of 2,928, p95 422 ms including about 390 ms of network) |
| Security check | Partly | Automated cases yes. No scanner. |
| Usability check with real users | **No** | Data is simulated, plan written |
| Regression after fixes | Yes | `09-regression-testing-log.md` |
| Static checks of infrastructure and pipelines | Yes, re-run 2026-11-25 | cfn-lint 1.61.0, actionlint, hadolint, `bash -n`, `node --check`: all clean (R11) |
| Post-deployment smoke test on AWS | **Yes, on the demo VM** | 22 of 22 on `https://32-236-220-241.sslip.io`, 2026-11-25 (HTTPS, S3 photos) |
| Container image build, compose stack | **Partly** | Image built and run on the demo VM (522 MB). The local compose stack with MinIO was never started. No Docker daemon access on the development machine. |
| S3 storage against a real store | **Partly** | The JUnit test (TC-67) is still skipped without MinIO. The smoke test on the demo put and read back a photo through a real S3 bucket (passes). Deleting a photo was not covered. |

## Results

| Measure | Value |
|---|---|
| Automated tests | 84 (83 pass, 0 fail, 1 skipped) |
| Designed test cases | 72: 71 pass, 1 skipped (TC-67, needs MinIO) |
| Browser checks | 40 of 40 pass |
| Smoke test (local) | 21 of 21 pass |
| Smoke test (AWS demo, HTTPS, S3, managed database) | 22 of 22 pass, 11.5 to 12.6 s from the development machine |
| Line coverage | 83.3% (services 92.7%, repositories 89.9%, routes 92.8%, S3 storage 27.5%) |
| Branch coverage | 72.5% |
| Instruction coverage | 85.2% |
| Load, half CPU and 1 GiB, 5,008 listings, 100 users no pause (laptop) | 1,079 requests per second, p95 136 ms, 0 failures |
| Load, live AWS demo, 8 listings, 50 users with 1 s think time | 36.3 requests per second, p95 422 ms (about 390 ms is the network to Sydney), 0 failures of 2,928 |
| Restore drill on the demo database | dump 0.62 s, restore 1.38 s, row counts identical |
| Defects found | 13 (6 of them from the first AWS deployments), plus open issue OI-11 |
| Defects fixed or worked around | 12 (DEF-009 is open) |
| Open issues | 11 (`08-defect-log.md`) |

## Defects

| Severity | Found | Fixed |
|---|---|---|
| High | 4 (DEF-003 security headers, DEF-005 database version, DEF-008 region denied, DEF-009 CloudFront account verification) | 3 (DEF-009 open) |
| Medium | 8 (DEF-001, DEF-002, DEF-006, DEF-007, DEF-010 to DEF-013) | 8 |
| Low | 1 (DEF-004) | 1 |

Found by: automated tests 4, linter 2, screenshot review 1, the first deployments 6. DEF-005 is confirmed on AWS. DEF-009 is still open.

## Known open issues that matter most

1. The designed AWS stack was not tested because it did not deploy (OI-3). The one-VM demo passed the smoke test (22 of 22 over HTTPS), a 50-user load run and a restore drill. Not run against it: the browser test, a load run that finds its limit, a failover or a VM-loss test.
2. The JUnit test for the S3 storage class has not run against a store (OI-1). It was exercised for upload and download through real S3 by the demo smoke test, not for delete.
3. Search reads every matching row. Fine at 5,000 listings, poor at hundreds of thousands (OI-5).
4. Account deletion and data export are done by hand through the Grievance Officer, not in the app, and the legal documents have not been reviewed by a lawyer (OI-10).

## Lessons learned

- **Move the clock, do not wait.** A single controllable clock made token expiry, listing expiry, boost end and weekend pricing testable in milliseconds. It also exposed that two clocks were mixed (DEF-007).
- **Let the database decide races.** Unique constraints and conditional updates made the three concurrency tests pass without locks.
- **Defaults hide gaps.** Static files bypassed the filter that sets security headers (DEF-003). Only a test that asked for the headers on `/` found it.
- **Lint infrastructure like code.** cfn-lint caught a database version that new instances cannot use and a broken YAML file before any cloud spend.
- **Measure the right thing.** The first load numbers (12,000 requests per second) came from 8 listings and meant little. With 5,008 listings the same code did about 1,100. Data size, not instance size, set the limit.
- **The cloud account sets the design, not the other way round.** The organization that owns the AWS account allowed only three regions and denied most services outside Sydney, and CloudFront refused a new distribution until AWS Support verified the account. A deployment plan needs a read-only probe of what the account allows before anything is written (DEF-008, DEF-009).
- **Test failure paths of the deploy, not only the success path.** The failed stack could not roll back because the database was still being created (DEF-010). A generated password with shell characters broke a script and leaked into a log (DEF-013).
- **Smoke tests leave data behind.** Eight runs against the live demo left 18 users, 18 orders and 9 orphan photos that had to be removed by hand (OI-11).
- **Check the test setup before blaming the app.** Three apparent failures (a hung home page, 2.65% failed requests, two flaky browser checks) came from a replaced jar, a wrong data folder and stale DOM reads.

## Sign-off

Prepared by Arnav Shah (testing) and Dipanshu Parmar (QA lead). Reviewed by Deep Paun (mentor). Updated after the first cloud deployment on 2026-11-25. It must be updated again after a real usability session.

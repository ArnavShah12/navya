# Automated tests, execution report

> Subject: Software Testing | Deliverable: automated unit tests with execution report | Data: real, from `./mvnw verify` on 2026-11-25, re-run after the consent and legal-page changes (surefire XML)

Command: `./mvnw verify`. Java 21, JUnit 5.14, AssertJ, zonky embedded PostgreSQL 14.25. Total test time about 15 seconds.

| Test class | Level | Tests | Failures | Skipped |
|---|---|---|---|---|
| `ValidateTest` | Unit | 15 | 0 | 0 |
| `PricingAndGatewayTest` | Unit | 6 | 0 | 0 |
| `StorageTest` | Unit | 3 | 0 | 0 |
| `AuthServiceTest` | Integration (database) | 7 | 0 | 0 |
| `ListingServiceTest` | Integration | 16 | 0 | 0 |
| `ChatServiceTest` | Integration | 7 | 0 | 0 |
| `BoostAndReportTest` | Integration | 12 | 0 | 0 |
| `ApiTest` | System (HTTP and WebSocket, two instances) | 17 | 0 | 0 |
| `S3StorageTest` | Integration (needs MinIO) | 1 | 0 | 1 |
| **Total** | | **84** | **0** | **1** |

Not counted above: the 40-check browser test (`e2e/run.sh`) and the load and smoke scripts.

## What the tests cover

Validation and boundaries, password hashing, token forgery and expiry, duplicate sign-ups under concurrency, listing rules and ownership, search safety, expiry with a moved clock, chat privacy and the one-chat-per-buyer rule under concurrency, payment outcomes including idempotency, slow bank and the double click, moderation and audit, CSV formula guard, every route's status code for good and bad input, security headers, upload checks, rate limiting, and live chat between two instances.

## The skipped test

`S3StorageTest` runs only when `S3_TEST_ENDPOINT` is set. It was not run on the development machine. The CI pipeline starts MinIO and runs it. Until a CI run is recorded, the S3 code path is untested (open issue OI-1).

## Reproduce

```
cd navya && ./mvnw verify
python3 navya/tools/make_test_case_docs.py     # rebuilds 02-designed-test-cases.* from the same run
```

Coverage report: `target/site/jacoco/index.html`. Reflection: `05-code-coverage-and-reflection.md`.

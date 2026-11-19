# Code coverage and reflection

> Subject: Software Testing | Deliverable: coverage report and a short reflection on statement and branch coverage | Data: real, JaCoCo 0.8.16 on the `./mvnw verify` run of 2026-11-25. HTML report: `target/site/jacoco/index.html`.

## Numbers

| Measure | Result |
|---|---|
| Instructions | 85.2% |
| Branches | 72.5% |
| Lines | 83.3% |

| Package | Instructions | Branches | Lines |
|---|---|---|---|
| `service` (rules) | 93.2% | 78.7% | 92.7% |
| `repo` (SQL) | 94.5% | 77.9% | 89.9% |
| `web` (routes) | 88.9% | 68.0% | 92.8% |
| `payment` | 100% | 100% | 100% |
| `domain` | 98.5% | 96.4% | 93.5% |
| `config` | 95.9% | 80.8% | 96.9% |
| `worker` | 84.9% | 69.2% | 77.1% |
| `util` | 78.0% | 70.6% | 85.2% |
| `storage` | 26.2% | 18.2% | 27.5% |
| `App`, `AppContext`, `Seeder` (top package) | 42.9% | 13.6% | 47.5% |

The tests do not start `App.main`, so start-up wiring is exercised only by the browser test and the smoke script, which JaCoCo does not see. The browser run uses `Seeder` and `AppContext` for real, so the low top-package figure understates what is exercised.

## What the numbers say

**Statement coverage is high where it matters.** The rules (`service`) and the SQL (`repo`) are above 89% of lines. Almost all of what is missing is error handling that is hard to trigger: a `catch` for a failed disk write in `AuditLog` and `Notifier`, `InterruptedException` handling in `BoostService.boost`, a lost database connection in `ChatHub`, and the generic 500 handler.

**Branch coverage is lower than statement coverage, as expected.** One line can hold several conditions. The weakest spots:

- `Validate` (70.6%): some null-or-blank combinations for optional text are never sent.
- `Api` (66.7%): optional query parameters are tested present or absent, not every combination.
- `ListingService` has many branches in input checks. Several are covered only on one side (for example `images == null` is always false because the tests always send a list).

**Coverage does not show quality.** A line can be covered by a test that does not check its result. The tests here assert outcomes, and the strongest evidence of value is the defect log: coverage-driven tests found DEF-007 (clock mixing in `OrderRepo` and `ListingRepo`) by adding a test for the uncovered "gateway never saw this order" path in `BoostService.reconcile`. Before that test, that code had never run.

**The biggest honest gap is `S3FileStorage`: 28% of lines.** Its test needs an S3-compatible store and was skipped here. This is the class that holds every photo in the cloud, so it matters more than its size suggests. CI runs it against MinIO. On 2026-11-25 the upload and download paths were also exercised through a real S3 bucket by the smoke test on the demo (a photo is uploaded and read back), which JaCoCo does not see, so the 28% understates what has run. Deleting a photo was not covered there.

## What was not done

- No mutation testing, so we do not know how many covered lines would survive a changed operator.
- No coverage of the JavaScript front end. The 40 browser checks cover its main paths.
- The coverage report is from a laptop run (re-run 2026-11-25 after the consent change: 84 tests, 0 failures, 1 skipped, 83.3% lines, 72.5% branches). The same suite in CI will also include `S3StorageTest`.

## Next steps if time allows

Add `S3StorageTest` results from a real CI run, test the optional-text branches in `Validate`, and add a test that starts `App.main` against the embedded database.

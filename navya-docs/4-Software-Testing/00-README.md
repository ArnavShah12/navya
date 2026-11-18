# Testing package

| Brief item | File |
|---|---|
| Test plan and strategy | `01-test-plan-and-strategy.md` |
| At least 30 designed test cases (69, with actual results) | `02-designed-test-cases.md`, `.csv` (rebuilt by `navya/tools/make_test_case_docs.py`) |
| Black-box design: decision tables | `03-decision-tables.md` |
| Automated unit tests and execution report | `04-automated-unit-tests-execution-report.md`, test code in `navya/src/test/java` |
| Coverage report and reflection | `05-code-coverage-and-reflection.md` |
| Integration and system testing, error paths | `06-integration-and-system-testing.md`, `navya/e2e/` |
| Non-functional: performance, security, usability | `07-non-functional-testing/` |
| Defect log | `08-defect-log.md`, `08-defect-log.csv` |
| Regression testing | `09-regression-testing-log.md` |
| Post-deployment smoke test | `10-post-deployment-smoke-test.md`, `navya/load/smoke.mjs` |
| Test summary report | `11-test-summary-report.md` |
| Raw load-test output | `07-non-functional-testing/results/` |

Honest status in one line: everything runs and passes on a laptop (84 tests, 0 failures, 1 skipped, and 40 of 40 browser checks, re-run on 2026-11-25), and the one-VM demo on AWS passes the smoke test (22 of 22, HTTPS, S3 photos, managed database) and a restore drill. The full AWS stack, the compose stack on the development machine, the JUnit S3 test against a real store, the real-user usability session and an external security scan have **not** been done. Each is listed with its impact in `08-defect-log.md` (open issues) and `11-test-summary-report.md`.

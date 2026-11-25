# Navya: documents

Documents for the integrated assignment, grouped per subject. The code is in the sibling folder `../navya/` in the same repository. Paths in the documents such as `navya/src/`, `navya/infra/`, `navya/load/`, `navya/e2e/`, `navya/Dockerfile` and `.github/` are inside it. The scripts that generated the simulated research files and the test-case tables are in `navya/tools/`.

| Folder | Contents |
|---|---|
| `1-Startup-Policies/` | Topic proposal and startup brief, Lean Canvas and Business Model Canvas, SWOT and PESTEL, MVP plan, funding plan, DPIIT readiness, IP and compliance note, funding and compliance note, `research-evidence/` |
| `2-E-Commerce/` | E-commerce design document: business model and revenue, store flow, payment approach, logistics, digital marketing, legal and security checklist, operations and metrics, and `legal/` (Terms of Use, Privacy Notice, Grievance policy, data-requests runbook) |
| `3-Java/` | UML diagrams, object-oriented design notes, source code pointer and README |
| `4-Software-Testing/` | Test plan, designed test cases, decision tables, execution report, coverage, integration and system tests, non-functional tests (performance, security, usability) with raw load-test output, defect log, regression log, smoke test, test summary report |
| `5-Cloud-Computing/` | Cloud architecture document: design, deployment, containerisation, storage and database, security, scalability, monitoring, CI/CD, cost estimate and script, operations and limits |
| `6-Final-Submission/` | Final project report with the requirements traceability table |

## What is real and what is not

- **Real:** the code, test results, coverage, defects, load numbers (laptop, and a run against the live AWS demo), the live demo deployment record, AWS and Lightsail list prices (read 2026-11-25), the exchange rate, the market and rule figures with their sources.
- **Simulated, labelled in each file:** interviews, survey, usability session, the behaviour of the members in the pilot (`1-Startup-Policies/research-evidence/`).
- **Placeholders left:** the mentor approval date. Team (Arnav Shah, Dipanshu Parmar), college (JG University) and mentor (Deep Paun) are filled in. The live URL is in the final report.
- **Pending:** the Docker image build on the development machine, the JUnit S3 test run, the full AWS stack (blocked by CloudFront account verification), the pitch slides and the demo video. Done: the one-VM AWS demo, `5-Cloud-Computing/11-demo-deployment-record.md`.

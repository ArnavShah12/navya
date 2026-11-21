# Legal documents

The documents Navya publishes to its members, and the one internal procedure behind them. Written by the team from public rules, not by a lawyer: have a lawyer review them before a real launch.

| Document | For | Served on the site |
|---|---|---|
| `terms-of-use.md` | Members | `/terms.html` |
| `privacy-notice.md` | Members (Digital Personal Data Protection Act, 2023) | `/privacy.html` |
| `grievance-redressal-policy.md` | Members and anyone reporting content | `/grievance.html` |
| `data-requests-runbook.md` | The Grievance Officer (internal) | No |

## How they reach the site

The Markdown files here are the only source. `navya/tools/make_legal_pages.py` turns the three public ones into `navya/src/main/resources/public/terms.html`, `privacy.html` and `grievance.html`, which the app serves. After editing a document, run `python3 navya/tools/make_legal_pages.py`, change `AuthService.TERMS_VERSION` and the "Version" line in each document, and redeploy.

## How consent is captured

The sign-up form has one required box: "I am 18 or older. I accept the Terms of Use and have read the Privacy Notice", with links to both. The API refuses a sign-up without `consent: true` (HTTP 400, tests TC-70). It stores the time and the version on the account (`users.consented_at`, `users.terms_version`, test TC-71). Footer links to all three pages are on every page (TC-72). Accounts made by the demo-data seeder have no consent record.

## Sources behind the numbers

- Acknowledge in 24 hours and resolve in 15 days: Information Technology (Intermediary Guidelines and Digital Media Ethics Code) Rules, 2021, rule 3(2), [SFLC.in analysis](https://sflc.in/analysis-information-technology-intermediary-guidelines-and-digital-media-ethics-code-rules-2021).
- Acknowledge in 48 hours and resolve in one month: Consumer Protection (E-Commerce) Rules, 2020, see `../../1-Startup-Policies/research-evidence/real-world-benchmarks.md`.
- Data subject rights and the 18-year threshold: Digital Personal Data Protection Act, 2023 and Rules, 2025, same file.

## Not covered

No lawyer review. No registered entity, so no registered address, GST or company details. No self-service account deletion or data export: both go through the Grievance Officer. No trademark search for the name.

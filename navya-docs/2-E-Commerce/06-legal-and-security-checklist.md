# Legal and security checklist

> Subject: E-Commerce | Deliverable: legal and security checklist (privacy, consent, fake reviews, dark patterns) | Data: status is checked against the code on 2026-11-25. Legal points are not legal advice.

Status: Done, Partly, Not done, Not applicable.

## Privacy and consent

| Item | Status | Note |
|---|---|---|
| Collect only what is needed | Done | Email, name, city, password hash, listings, messages. Nothing else. No analytics, no ad trackers, no third-party scripts (the old Supabase CDN script was removed). |
| Passwords | Done | bcrypt (cost 10), 8 to 64 characters. Never logged. |
| Consent text at sign-up | Done | Required box with links to the Terms of Use and Privacy Notice. The API refuses a sign-up without it and records the time and version (TC-70, TC-71). `legal/` |
| 18+ confirmation | Done | Part of the same required box. |
| Delete my account and data | Partly | Members delete their own listings in the app. Account erasure is done by the Grievance Officer on request within 15 days, with a tested procedure (`legal/data-requests-runbook.md`). Not self-service. |
| Export my data | Partly | By request, as JSON, with a tested query (`legal/data-requests-runbook.md`). Not self-service. |
| Phone number never shared by the app | Done | Chat is in-site. Members can still type their number in a message. |
| Data location | Partly | The design uses AWS Mumbai (`ap-south-1`). The demo that runs is in AWS Sydney (`ap-southeast-2`) because the organization that owns the AWS account only allows three regions. Personal data of Indian members would therefore be stored outside India until the full stack or Mumbai is available. Stated in the Privacy Notice (section 3). |
| Messages readable by staff | Partly | Admin pages do not show messages, but a person with database access can read them. State this in the privacy notice. |

| Terms of use | Done | `legal/terms-of-use.md`, served at `/terms.html`, linked from the footer and the sign-up form. |
| Privacy notice | Done | `legal/privacy-notice.md`, served at `/privacy.html`. States what is collected, why, who sees it, that data is kept in Sydney, retention, rights, children. |
| Grievance officer and process | Done | Arnav Shah, with the contact on every page footer and at `/grievance.html`. Acknowledge in 24 hours, resolve in 15 days. `legal/grievance-redressal-policy.md` |
| Lawyer review | Not done | The documents are the team's reading of public rules. |

## Fake listings and reviews

| Item | Status | Note |
|---|---|---|
| Reviews and ratings | Not applicable | The MVP has none. If added, only after a completed chat with that seller, one per buyer, with a report button. |
| Report button | Done | One report per member per listing. |
| Moderation | Done | The team can take a listing down or dismiss. Every action goes in the audit log. |
| Seller identity | Not done | Anyone with an email can sell. |
| Counterfeit and prohibited items | Partly | Fixed categories only, reports for the rest. No keyword check. |
| Duplicate and spam listings | Partly | Write rate limit of 30 per minute per member. No duplicate detection. |

## Dark patterns

| Pattern | Status |
|---|---|
| Hidden fees: price shown before paying | Done. The boost page shows the price and days before the Pay button. |
| Pre-ticked options | Done. There are none. |
| Fake urgency (timers, "only 2 left") | Done. None. The weekend discount is real and shown as the price. |
| Confirm-shaming wording | Done. None. |
| Hard to cancel or leave | Partly. Boosts are one-off, there is no subscription. Leaving (account deletion) is not built. |
| Forced sign-up to browse | Done. Browsing, search and detail pages need no login. |
| Trick questions or misleading buttons | Done. The two report actions are labelled "Take down" and "Dismiss". |

## Security

Details and test evidence: `../4-Software-Testing/07-non-functional-testing/security-testing.md`. Summary: input validation at every route and in the database, parameterised SQL, escaped output, CSP without inline script or style, signed tokens with expiry, ownership checks on every write, a stranger gets 404 for another member's chat, rate limits, photos checked by file signature and size, CSV formula guard, secrets only in environment variables (a `.env` file with mode 600 on the demo VM, SSM Parameter Store in the full design).

## Payments

Test mode only. No card data is handled. See `03-payment-approach.md`.

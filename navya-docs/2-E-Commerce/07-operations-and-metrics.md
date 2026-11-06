# Operations and metrics

> Subject: E-Commerce | Deliverable: operations plan and metrics | Data: the metrics exist in the code. No values yet from real use.

## Who does what

| Task | Owner | How often |
|---|---|---|
| Review reports | Arnav Shah | Daily |
| Check the dashboard and alarm emails | Arnav Shah | Daily during a pilot |
| Answer member emails | Arnav Shah | Daily |
| Data requests (see, correct, erase) and complaints | Arnav Shah, Grievance Officer. Second reviewer Dipanshu Parmar | Acknowledge in 24 hours, finish in 15 days. Procedure: `legal/data-requests-runbook.md` |
| Deploy | `navya/infra/lightsail-demo.sh push` by hand (the demo). CI on merge to `main` is written for the full stack and has never run | Per change |
| Restore drill | Dipanshu Parmar (cloud) | Once before Review 2. Done on the demo database 2026-11-25 |

## Metrics the app produces

`/api/admin/stats` (team only) shows the counters below. The CloudWatch dashboard shows them in the full design only (not deployed). They are: requests, server errors, sign-ups, listings created, listings expired, messages sent, boosts paid, payments failed, open chat sockets, dropped notices. Log lines carry `method`, `path`, `status`, `ms` and `user`, so slow routes and error rates can be read from the log (`docker compose logs` on the demo, CloudWatch Logs Insights in the full design).

## Business numbers to track in the pilot

Listings per week, share marked sold, chats per listing, boosts per 100 listings, reports per 100 listings and how many were removed, weekly active members, members who posted and then came back.

## Moderation rule of thumb

Remove: counterfeit claims, items outside the four categories, personal data in photos, abuse. Dismiss: disagreement about price. Every removal writes an audit line with the admin's id.

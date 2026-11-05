# MVP plan

> Subject: Startup Policies | Deliverable: MVP plan | Data: scope is what the repository contains

## Goal

Show that students will post items, find them and talk to each other, and that sellers will pay to boost. Everything else waits.

## In the MVP (built)

| Feature | Where |
|---|---|
| Sign up and log in with email and password | `AuthService` |
| Post, edit, delete, mark sold, up to 4 photos | `ListingService`, sell form |
| Search by words, category, city, price, sort, paging | `ListingRepo.search` |
| Save items | favourites |
| Chat between buyer and seller, live | `ChatService`, `ChatHub` |
| Report a listing, team reviews | `ReportService`, admin page |
| Boost a listing, pay with a test card, order history | `BoostService` |
| Listings expire after 60 days | `ListingReaper` in `AppContext` |
| CSV and JSON export for the team | `ExportService` |

## Out of the MVP

Real payments, payouts, reviews and ratings, seller identity checks, notifications by email or SMS, mobile apps, delivery, in-app offers, multiple languages.

## Build order and status

| Step | Status |
|---|---|
| Static prototype with Supabase | Done (the starting point) |
| Java service with PostgreSQL | Done |
| Tests, load tests, browser test | Done on a laptop |
| Container image, compose file | Image built and run on the demo VM (522 MB). The local compose stack with MinIO was not run. |
| AWS infrastructure and pipeline | The full stack (ECS, RDS, ALB, CloudFront) is written and lints clean but did not deploy (CloudFront account verification). The demo runs on one Lightsail VM with a managed PostgreSQL and S3 (`../5-Cloud-Computing/11-demo-deployment-record.md`). The pipeline has never run on GitHub. |

## Success measures for a pilot (targets, not results)

20 listings posted in the first week at one campus. 30% of listings with a chat started. 3 boost purchases in the first month.

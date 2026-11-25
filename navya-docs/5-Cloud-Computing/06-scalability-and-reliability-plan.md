# Scalability and reliability plan

> Subject: Cloud Computing | Deliverable: plan for handling more users, backup and recovery | Data: the plan is real. What is implemented in the template is marked. Measured figures are from a laptop.

## What the demo does (2026-11-25)

One Lightsail VM, one single-zone Lightsail database. More users means a bigger plan (the account refused `small` for the VM, `micro` runs now) or the ECS design in this document, which was not deployed. The app holds no state on the VM except logs: photos are in S3 and data is in the managed database.

Load on the demo: 50 users browsing with a 1 s pause, 36.3 requests per second, 0 failures of 2,928, p95 422 ms of which about 390 ms is the network round trip (`../4-Software-Testing/07-non-functional-testing/performance-testing.md`). The limit of the VM was not found.

Restore drill on the demo database, done 2026-11-25 with the documented `pg_dump` command: dump 0.62 s (22,531 bytes), restore into a scratch database 1.38 s, row counts identical (20 users, 8 listings, 16 orders), scratch database dropped. Not tested: restoring from a Lightsail automatic backup or snapshot, a VM loss, a database failover (single zone).

## Why the app can run as several copies

The app keeps no member state in memory except two things: rate-limit counters (per instance) and the mock gateway's payment memory. Everything else is in PostgreSQL or S3. Races are settled by database constraints, not Java locks. Chat reaches the right browser on any instance through `LISTEN/NOTIFY`. Proven on a laptop with two instances on one database (TC-65). The reaper is safe to run on every instance at once, because its two jobs are single idempotent statements (TC-35, TC-46).

## Scaling plan

| Layer | Now (in the template) | When load grows |
|---|---|---|
| App tasks | ECS service, minimum 1 and maximum 4 (`MaxCount`). Target tracking on average CPU 60% and on requests per task (placeholder 300, to be set from a measurement on Fargate). | Raise `MaxCount`. Use 1 vCPU tasks. |
| Capacity estimate | Measured on a laptop at 0.5 CPU and 1 GiB: 1,079 requests per second at p95 136 ms with 5,008 listings. Derated to half for the cloud. One task covers the peak at 10,000 members (about 167 requests per second), a second is for availability. | Re-measure on Fargate with RDS. |
| Load balancer | Spreads over healthy tasks in 2 zones, 15 s health checks on `/health` | |
| Static files and photos | `/img/*` cached at CloudFront for up to a year (names are random, never reused) | Cache the static page too |
| Database | `db.t4g.micro`, single zone | Larger class first. Then read replica for reports. Then Multi-AZ. The cost sheet's 10,000-member column uses `db.t4g.small` Multi-AZ. |
| Search | `ILIKE` over a joined string. p95 136 to 160 ms at 5,008 listings under 100 users with no pause. | Trigram or full-text index (OI-5) before 100,000 listings |
| Rate limits | In memory, per task | Shared store (ElastiCache) when more than one task serves the same members, or move limits to the edge (WAF) |
| Notices | In-process queue and a log file | SQS and a worker when email or SMS exist |
| Scheduled scale-up | Not in the template | Before a known peak (start of semester) set the minimum to 2 with an Application Auto Scaling schedule |
| Scale to zero | `DesiredCount=0` between demos (manual) | Not automatic: the load balancer and database still bill |

Cold start: a new task needs to pull the image and start the JVM (0.6 s on a laptop, longer on Fargate with an image pull and the 90 second health-check grace period).

## Reliability

| Failure | What happens |
|---|---|
| A task dies or fails `/health` | The load balancer stops sending traffic, ECS starts a replacement. With 2 tasks members see nothing. |
| A bad deploy | ECS deployment circuit breaker rolls back automatically. The pipeline's smoke test rolls back the task definition if the live site fails. |
| A zone fails | Tasks run in two zones. The database is single-zone unless `DbMultiAz=true`, so a database zone failure is an outage until restore. |
| Database slow or full | Alarms on free storage and (via the dashboard) CPU and connections |
| Photo store problem | S3 is regional and durable. The app returns 500 for that photo and logs it. |
| Slow or lost payment answer | Order stays pending, background check settles it, orphaned pending orders fail after 10 minutes (TC-46, TC-68) |
| Task stopped during a deploy | Stop timeout 30 s, load balancer deregistration delay 30 s, `app.stop()` and `AppContext.close()` finish payments and drain the notice queue |

## Backup and recovery

| Data | Protection | Target (not measured) |
|---|---|---|
| Database | RDS automated backups (`DbBackupDays`, default 1). Final snapshot when the stack is deleted. Manual snapshot before every demo and release. | Recovery point: up to 24 hours with automated backups (about 5 minutes with point-in-time restore). Recovery time: 1 to 2 hours. |
| Photos | S3, durable. Deleting a listing deletes its photos on purpose. No versioning. | If a photo is lost the listing shows a missing image. |
| Catalogue | Admin export to S3 (CSV and JSON, kept 30 days). The export does not include members, chats or orders. | |
| Code and infrastructure | Git repository. The whole stack can be rebuilt with `navya/infra/deploy.sh`. | Rebuild in about the time CloudFormation needs (not measured) |
| Secrets | Regenerated by `navya/infra/deploy.sh` if missing. A new database password needs the snapshot restore path (`DbSnapshotId` sets it on restore). | |

### Restore drill (to do once, record the times)

1. Take a manual snapshot: `aws rds create-db-snapshot`.
2. Delete the stack with `navya/infra/teardown.sh`.
3. Redeploy with `DB_SNAPSHOT=<snapshot> infra/deploy.sh`.
4. Check that listings, members and chats are back (count rows through the admin stats and by logging in).
5. Record the time taken and attach screenshots to this file.

The times and row counts from the drill go in the evidence screenshots, see `02-deployment-on-a-cloud-platform.md`.

## Summary

Implemented in the template: two zones, load balancer health checks, ECS auto scaling, circuit breaker, backups, encryption, alarms. Not implemented: Multi-AZ by default, scheduled scaling, shared rate limits, photo versioning, a backup copy of members and chats outside RDS, a cross-region copy.

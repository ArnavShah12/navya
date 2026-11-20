# Performance and load testing

> Subject: Software Testing | Deliverable: at least one performance or load test | Data: real k6 runs on a laptop, 2026-11-25. Raw output: `results/load-*.txt`. The table below is **not** a cloud measurement. One run against the live AWS demo is in its own section.

## Setup

- Tool: k6. Scripts: `navya/load/browse.js` (search, detail, photo) and `navya/load/chat-mix.js` (logged-in chat traffic).
- The app ran from `target/navya.jar`, PostgreSQL 14 (embedded) and k6 on the same laptop, so there is no network between them. Real cloud numbers will be worse by at least the network round trip to RDS.
- Two app sizes: pinned to one CPU core with a 600 MB heap (`taskset`), and limited to 0.5 CPU and 1 GiB (`systemd CPUQuota=50%`, `MemoryMax=1G`), the planned Fargate task size. A laptop core is not a Fargate vCPU, so the second run is a guide, not a promise.

## Results

| Run | Data | Users | Think time | Requests per second | p95 latency | Failed |
|---|---|---|---|---|---|---|
| Browse, one core | 8 listings | 200 | 1 s | 279 | 0.65 ms | 0 of 22,485 |
| Browse, one core, no think time | 8 listings | 100 | none | 12,077 | 8.6 ms | 0 of 966,140 |
| Browse, one core, no think time | **5,008 listings** | 100 | none | 1,093 | 160 ms | 0 of 87,448 |
| Browse, **half CPU, 1 GiB**, no think time | **5,008 listings** | 100 | none | **1,079** | **136 ms** | 0 of 86,327 |
| Chat, one core | seeded | 30 on one chat | 1 s | 89 | 3.6 ms | 0 of 5,405 |

## Run against the live demo on AWS (2026-11-25)

k6 ran on the development machine against the live demo (Lightsail `micro_3_2` VM, managed PostgreSQL, S3 photos, Sydney), with `browse.js`: ramp to 50 users over 20 s, hold, ramp down, 1 s think time, 80 s in all. Raw output: `results/load-browse-50vu-cloud-demo.txt`. The database holds 8 listings.

| Measure | Result |
|---|---|
| Requests | 2,928 (976 iterations of list, detail and photo), 36.3 per second |
| Failed | 0 of 2,928, all checks passed (list, detail, photo from S3) |
| Latency | average 399 ms, median 393 ms, p95 422 ms, maximum 475 ms |
| Data | 7.4 MB received |

About 390 ms of every request is the round trip from this machine to Sydney (the TCP connect alone takes 0.39 s), so the app and database added only about 10 ms. This run shows the demo handles 50 users browsing without errors. It does not find the limit: no run without think time was done on the cloud VM, and the demo has 8 listings, not 5,000, so the search cost seen in the laptop runs is not in this number. Not tested: WebSocket load, uploads, the VM's burst CPU credits under sustained load.

## What this shows

1. **Data size matters more than app size.** Going from 8 to 5,008 listings cut throughput from 12,077 to 1,093 requests per second. Each list request runs a `count(*)` and a page query with `ILIKE` over a joined string, which reads every row. The app tier is not the bottleneck at this size, the search query is. A trigram or full-text index is the next step (open issue OI-5).
2. **Half a CPU gave about the same throughput as a full core** (1,079 vs 1,093 per second). The shared PostgreSQL on the same laptop is probably the limit. Re-measure on Fargate against RDS before trusting any capacity number.
3. **The rate limiter works under load.** In the chat run 1,682 sends were answered 429 and 118 were accepted: each of the two members is allowed a burst of 30 plus 30 per minute. Those 429s are expected and are not counted as failures (`http.expectedStatuses`).
4. **Start-up:** the app answered `/health` 0.6 seconds after launch (limited to 0.5 CPU, 1 GiB, database already running). A Fargate task also needs to pull the image and register with the load balancer, so expect much longer.
5. **One slow outlier.** The one-core no-think run had a maximum of 3.3 s on a few requests while the median was 30 ms. The cause was not investigated (garbage collection or the 10-connection database pool are candidates).

## Limits of this test

No test through CloudFront and the load balancer. No test with WebSocket load (the load script uses REST only). No test with many simultaneous photo uploads. The 100-user, no-think-time run is a stress run, not a forecast of real traffic: the cost model assumes about 167 requests per second at 10,000 members (`../../5-Cloud-Computing/09-cost-estimate-computed-sheet.md`), about one sixth of the measured 1,079.

## Concurrency correctness (also a non-functional check)

TC-20 (12 simultaneous sign-ups), TC-42 (40 simultaneous chat starts) and TC-50 (10 identical payments) pass: the database constraints, not application locks, decide races.

## Re-run

```
k6 run -e BASE_URL=http://localhost:8080 -e VUS=100 -e SLEEP=0 load/browse.js
k6 run -e BASE_URL=http://localhost:8080 -e SEED_PASSWORD=<seed password> load/chat-mix.js
```

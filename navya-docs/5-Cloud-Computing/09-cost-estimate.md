# Cost estimate

> Subject: Cloud Computing | Deliverable: estimated monthly cloud cost at 100, 1,000 and 10,000 users, linked to the funding plan | Data: unit prices are real AWS list prices for Mumbai read on 2026-11-25. Usage figures are assumptions. Capacity is measured on a laptop. Full table: `09-cost-estimate-computed-sheet.md`. Script: `09-cost-estimate-script/cost_sheet.py`.

## Result (always on, two tasks, USD per month)

| | 100 users | 1,000 users | 10,000 users |
|---|---:|---:|---:|
| Total | **95.41** | **95.92** | **157.55** |
| Per user | 0.954 | 0.096 | 0.016 |
| Database | `db.t4g.micro` | `db.t4g.micro` | `db.t4g.small`, Multi-AZ, 50 GB |

| Other scenario | USD per month |
|---|---:|
| Demo only: the stack up 40 hours a month, deleted between, database kept as a snapshot | **9.04** |
| One task instead of two at 100 users | saves 22.58 |

## What is actually deployed

The demo runs on one Lightsail `micro_3_2` VM in Sydney (2 vCPU, 1 GB, 40 GB, 1 TB transfer): **USD 7 a month**, about USD 0.0096 an hour, plus a Lightsail managed PostgreSQL `micro_2_0` at **USD 15 a month** (prices returned by the Lightsail API on 2026-11-25), about **USD 22 in all**. That is about 23% of the table's 100-user total. It has no load balancer, no ECS and no CloudFront, and one VM, so it is a demo and not the design in the table. Measured memory use is 518 MB of 909 MB (`11-demo-deployment-record.md`). The table above still prices the designed stack at Mumbai list prices, because that is the design the document argues for. Sydney prices differ and were not read.

Re-check on 2026-11-25: the cost script reproduces `09-cost-estimate-computed-sheet.md` exactly, and six of its Mumbai unit prices (Fargate vCPU and GB hour, ALB hour and capacity unit, public IPv4 hour, RDS `db.t4g.micro`) match the AWS Pricing API. The other unit prices (S3, CloudFront, CloudWatch, ECR, RDS storage) were not re-checked.

## What dominates

At 100 and 1,000 members the cost is almost all fixed: two Fargate tasks (37.87), their public IPv4 addresses (7.30), the load balancer (17.45 plus 5.84 for capacity units plus 7.30 for its two public IPv4 addresses) and the database (about 18). That is about 94 of the 95 dollars. Traffic-based items (CloudFront, S3, logs) add under 1 dollar at 1,000 members and under 6 at 10,000. So **the first members cost the most per head**, and going from 1,000 to 10,000 members mostly buys a bigger, Multi-AZ database.

## Assumptions (change them in the script)

| Item | Value | Basis |
|---|---|---|
| Sessions per member per month | 3 | assumption |
| Requests per session | 20 | assumption |
| Share of members in the busiest 10 minutes | 10%, with a 5x peak factor | assumption |
| Data out of CloudFront per session | 1.5 MB, photos included | assumption |
| Listings per member, photos per listing, size per photo | 0.3, 3, 150 KB | assumption |
| Load balancer capacity units | 1 on average | assumption (upper-bound guess) |
| Log bytes per request | 165 | measured in the load test log |
| Capacity per task | 1,079 requests per second, used at 50% | measured at 0.5 CPU and 1 GiB on a laptop, then halved for the cloud |
| Tasks | at least 2 (availability), more if the peak needs them | rule |
| Hours per month | 730 | |

Unit prices used (USD, Mumbai): Fargate 0.04256 per vCPU-hour and 0.004655 per GB-hour; ALB 0.0239 per hour and 0.008 per capacity-unit hour; public IPv4 0.005 per hour; RDS `db.t4g.micro` 0.021, `small` 0.042, `medium` 0.084 per hour (Multi-AZ double); RDS gp3 storage 0.131 per GB-month (0.262 Multi-AZ); RDS backups above the database size 0.095 per GB-month; S3 0.025 per GB-month; CloudFront India 0.109 per GB and 0.0000012 per HTTPS request; CloudWatch logs 0.67 per GB ingested and 0.03 per GB-month stored, 0.30 per custom metric, 0.10 per alarm; ECR 0.10 per GB-month.

## Prices in the region that was deployed

The table uses Mumbai prices because the design targets Mumbai. The deployed region is Sydney. One Sydney price was read from the AWS Pricing API on 2026-11-25: RDS `db.t4g.micro`, PostgreSQL, single-AZ, USD 0.025 an hour, against 0.021 in Mumbai, so the database line would be about 19% higher there. The other Sydney prices were not read. The demo that runs uses Lightsail prices instead (USD 7 and USD 15 a month).

## Not included

Tax (GST), data transfer from AWS to the internet outside CloudFront, payment gateway fees, domain name, the free allowances AWS may give (CloudFront, CloudWatch and S3 have monthly free amounts, and new accounts may get credits, so the real bill can be lower), the CloudWatch dashboard fee beyond the free ones, SNS, SSM standard parameters (free), the budget alert (free for the first two budgets), NAT gateway (not used).

## Link to the funding plan

`../1-Startup-Policies/04-funding-plan.md` uses these totals. The always-on cost is about USD 95 a month before any traffic cost matters, so a pilot should run one task or demo windows only. Break-even in boosts depends on the exchange rate on the day and on demand nobody has measured.

## Refresh before relying on it

Prices change. Re-read the AWS Price List files (`https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/<Service>/current/ap-south-1/index.json`) or use the AWS Pricing Calculator, update the constants at the top of the script, and re-run it.

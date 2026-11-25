#!/usr/bin/env python3
"""Monthly AWS cost for Navya at 100, 1,000 and 10,000 users. Prints a Markdown table.

Unit prices are AWS list prices for Asia Pacific (Mumbai) read from the public AWS Price List files
(https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/<Service>/current/ap-south-1/index.json) on 2026-11-25.
Publication dates of those files are in PRICE_SOURCES. Prices change: re-read them before relying on the result.
Everything else is an ASSUMPTION and is labelled as such. Capacity per task is MEASURED (docs/evidence).
Run:  python3 cost_sheet.py
"""
import math

PRICE_SOURCES = {
    "AmazonECS (Fargate)": "published 2026-11-11",
    "AWSELB (ALB)": "published 2026-11-19",
    "AmazonRDS": "published 2026-11-20",
    "AmazonVPC (public IPv4)": "AmazonVPC file, read 2026-11-25",
    "AmazonS3": "AmazonS3 file, read 2026-11-25",
    "AmazonCloudFront": "published 2026-10-19 (India rates)",
    "AmazonCloudWatch": "AmazonCloudWatch file, read 2026-11-25",
    "AmazonECR": "AmazonECR file, read 2026-11-25",
}

HOURS = 730  # hours in an average month

# ---- unit prices, USD, Mumbai (CloudFront: India edge) ----
P = {
    "fargate_vcpu_hr": 0.04256, "fargate_gb_hr": 0.004655,
    "alb_hr": 0.0239, "alb_lcu_hr": 0.008,
    "ipv4_hr": 0.005,
    "rds_hr": {("micro", False): 0.021, ("small", False): 0.042, ("medium", False): 0.084,
               ("micro", True): 0.042, ("small", True): 0.084, ("medium", True): 0.167},
    "rds_gb_month": {False: 0.131, True: 0.262},   # gp3 storage, single-AZ / Multi-AZ
    "rds_backup_gb_month": 0.095,                  # only for backup storage above the size of the database
    "s3_gb_month": 0.025, "s3_put_each": 0.000005, "s3_get_each": 0.0000004,
    "cf_gb": 0.109, "cf_https_req": 0.0000012,
    "cw_log_gb": 0.67, "cw_log_store_gb_month": 0.03, "cw_metric_month": 0.30, "cw_alarm_month": 0.10,
    "ecr_gb_month": 0.10,
}

# ---- ASSUMPTIONS (change them, the table follows) ----
SESSIONS_PER_USER = 3          # per month
REQUESTS_PER_SESSION = 20      # API and page requests per session
PEAK_SHARE = 0.10              # share of monthly users active in the busiest 10 minutes
PEAK_FACTOR = 5                # busiest second versus the average of that window
MB_PER_SESSION = 1.5           # bytes leaving CloudFront per session, photos included
LISTINGS_PER_USER = 0.3
PHOTOS_PER_LISTING = 3
KB_PER_PHOTO = 150             # after the browser resizes to 1000 px at JPEG quality 0.75
LOG_BYTES_PER_REQUEST = 165    # MEASURED: average request log line in the load-test log was 164.8 bytes
LCU_AVERAGE = 1.0              # load balancer capacity units billed on average (upper-bound guess for light traffic)
CUSTOM_METRICS, ALARMS = 4, 4  # from infra/main.yaml
# ---- MEASURED ----
MEASURED_RPS_PER_TASK = 1079   # k6, 100 users, no think time, 5,008 listings, app limited to 0.5 CPU / 1 GiB, DB and k6 on the same laptop
DERATE = 0.5                   # take half of it for the cloud: network hops to RDS, burstable CPU credits on db.t4g
TASK_VCPU, TASK_GB = 0.5, 1.0

DEMO_HOURS = 40                # assumption: reviews, rehearsals and the recorded demo

TIERS = [  # users, task floor, db size, multi-AZ, db GB
    (100, 2, "micro", False, 20),
    (1000, 2, "micro", False, 20),
    (10000, 2, "small", True, 50),
]


def run():
    rows = []
    for users, min_tasks, db, multi, db_gb in TIERS:
        req = users * SESSIONS_PER_USER * REQUESTS_PER_SESSION
        peak = users * PEAK_SHARE * REQUESTS_PER_SESSION / 600 * PEAK_FACTOR
        need = math.ceil(peak / (MEASURED_RPS_PER_TASK * DERATE)) + 1
        tasks = max(min_tasks, need)
        gb_out = users * SESSIONS_PER_USER * MB_PER_SESSION / 1024
        photos_gb = users * LISTINGS_PER_USER * PHOTOS_PER_LISTING * KB_PER_PHOTO / 1024 / 1024
        puts = users * LISTINGS_PER_USER * PHOTOS_PER_LISTING
        log_gb = req * LOG_BYTES_PER_REQUEST / 1024**3
        c = {
            "Fargate tasks": tasks * HOURS * (TASK_VCPU * P["fargate_vcpu_hr"] + TASK_GB * P["fargate_gb_hr"]),
            "Public IPv4, tasks": tasks * HOURS * P["ipv4_hr"],
            "Load balancer hours": HOURS * P["alb_hr"],
            "Load balancer capacity units": HOURS * LCU_AVERAGE * P["alb_lcu_hr"],
            "Public IPv4, load balancer (2)": 2 * HOURS * P["ipv4_hr"],
            "RDS instance": HOURS * P["rds_hr"][(db, multi)],
            "RDS storage": db_gb * P["rds_gb_month"][multi],
            "S3 storage and requests": photos_gb * P["s3_gb_month"] + puts * P["s3_put_each"] + req * 0.3 * P["s3_get_each"],
            "CloudFront data and requests": gb_out * P["cf_gb"] + req * P["cf_https_req"],
            "CloudWatch logs": log_gb * (P["cw_log_gb"] + P["cw_log_store_gb_month"]),
            "CloudWatch metrics and alarms": CUSTOM_METRICS * P["cw_metric_month"] + ALARMS * P["cw_alarm_month"],
            "ECR storage (0.5 GB)": 0.5 * P["ecr_gb_month"],
        }
        rows.append((users, req, peak, need, tasks, c))
    return rows


def main():
    rows = run()
    print("| Quantity | " + " | ".join(f"{r[0]:,} users" for r in rows) + " |")
    print("|---|" + "---:|" * len(rows))
    print("| Requests per month (assumption) | " + " | ".join(f"{r[1]:,}" for r in rows) + " |")
    print("| Peak requests per second (assumption) | " + " | ".join(f"{r[2]:.1f}" for r in rows) + " |")
    print(f"| Tasks needed at peak, +1 spare (measured {MEASURED_RPS_PER_TASK} req/s per task, derated to {int(DERATE*100)}%) | " + " | ".join(str(r[3]) for r in rows) + " |")
    print("| Tasks run (never fewer than 2 for availability) | " + " | ".join(str(r[4]) for r in rows) + " |")
    print("| Database | " + " | ".join(f"db.t4g.{t[2]}{' Multi-AZ' if t[3] else ''}, {t[4]} GB" for t in TIERS) + " |")
    print()
    names = []
    for r in rows:
        for k in r[5]:
            if k not in names:
                names.append(k)
    print("| USD per month | " + " | ".join(f"{r[0]:,} users" for r in rows) + " |")
    print("|---|" + "---:|" * len(rows))
    for n in names:
        print(f"| {n} | " + " | ".join(f"{r[5][n]:.2f}" if n in r[5] else "-" for r in rows) + " |")
    print("| **Total** | " + " | ".join(f"**{sum(r[5].values()):.2f}**" for r in rows) + " |")
    print("| Per user per month | " + " | ".join(f"{sum(r[5].values())/r[0]:.3f}" for r in rows) + " |")
    print()
    one = rows[0]
    saved = HOURS * (TASK_VCPU * P["fargate_vcpu_hr"] + TASK_GB * P["fargate_gb_hr"]) + HOURS * P["ipv4_hr"]
    # Demo-only scenario: the stack exists only during reviews and rehearsals (infra/teardown.sh in between).
    h = DEMO_HOURS
    demo = {
        "Fargate, 2 tasks": 2 * h * (TASK_VCPU * P["fargate_vcpu_hr"] + TASK_GB * P["fargate_gb_hr"]),
        "Public IPv4, tasks": 2 * h * P["ipv4_hr"],
        "Load balancer hours and capacity": h * (P["alb_hr"] + LCU_AVERAGE * P["alb_lcu_hr"]) + 2 * h * P["ipv4_hr"],
        "RDS db.t4g.micro, running only in those hours": h * P["rds_hr"][("micro", False)],
        "RDS snapshot kept between demos (20 GB)": 20 * P["rds_backup_gb_month"],
        "S3, CloudFront, CloudWatch, ECR (small)": 0.5 + 1.60 + 0.05,
    }
    print()
    print(f"Demo-only scenario: the stack is up {h} hours a month, deleted in between, database restored from its snapshot each time.")
    print()
    print("| USD per month | Demo only |")
    print("|---|---:|")
    for k, v in demo.items():
        print(f"| {k} | {v:.2f} |")
    print(f"| **Total** | **{sum(demo.values()):.2f}** |")
    print()
    print(f"Running one task instead of two at 100 users saves {saved:.2f} USD a month and removes the failover.")
    print("Price file dates: " + "; ".join(f"{k} {v}" for k, v in PRICE_SOURCES.items()))


if __name__ == "__main__":
    main()

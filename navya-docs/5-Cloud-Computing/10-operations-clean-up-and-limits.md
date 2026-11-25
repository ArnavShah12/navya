# Operations, clean-up and limits

> Subject: Cloud Computing | Deliverable: budget alert, clean-up of unused resources, what is free and what is not | Data: prices are from the AWS Price List (2026-11-25). Free-plan terms are from the earlier FestPass research notes (`free-tier-research.md`, 2026-11-24, web research whose sources conflicted in places) and **must be checked in your own account**.

## Which parts cost money, and which do not

| Part | Free? |
|---|---|
| Everything on a laptop: the app, tests, embedded PostgreSQL, load tests, browser test, local file storage | Free (open-source tools) |
| `docker compose` stack (PostgreSQL, MinIO, the app) | Free software, but needs a Docker daemon and your machine's resources |
| GitHub repository and Actions | Free for public repositories. Private repositories have a monthly allowance (check the current number). |
| AWS IAM, VPC, subnets, security groups, S3 gateway endpoint | No charge |
| AWS Budgets (first two budgets), SNS email, SSM standard parameters | No charge or negligible |
| CloudWatch logs, metrics, alarms, dashboard | A small free allowance exists. The cost sheet counts about USD 1.60 a month for 4 metrics and 4 alarms as a safe upper bound. |
| CloudFront | AWS documents a monthly free allowance (verify). The estimate does not subtract it. |
| S3, ECR | Cents at this size. Small free allowances may apply for new accounts. |
| **Fargate tasks** | **Not free.** USD 18.93 per task-month plus USD 3.65 for its public IPv4 address, if run all month. |
| **Application Load Balancer** | **Not free.** About USD 30 a month with capacity units and its two public IPv4 addresses. It bills while it exists, even with no tasks. |
| **RDS PostgreSQL** | **Not free beyond credits.** About USD 18 a month for the micro instance and 20 GB. |
| **Public IPv4 addresses** | **Not free** (USD 0.005 per hour each). |
| **Lightsail VM and database (the running demo)** | **Not free.** USD 7 a month for the VM and USD 15 for the managed database, about USD 22 (static IP free while attached, S3 for the photos is cents) for `micro_3_2`, billed while the VM exists, stopped or not (AWS documents this). Delete it to stop the charge. |

Always on, the stack costs about USD 95 a month (`09-cost-estimate.md`). Per the earlier research notes, the AWS Free Plan for new accounts gives up to USD 200 in credits for six months (USD 100 at sign-up, USD 100 more for using some services), without a card in some sign-up flows, and stops instead of billing when credits run out. Terms change, and Fargate and ALB eligibility was marked unconfirmed there. At USD 95 a month, USD 200 of credit lasts about two months if left running, and many months if only run in demo windows (about USD 9 a month).

**Therefore:** develop and test on the laptop, deploy to AWS only for reviews, rehearsals and the recorded demo, and tear down in between.

## Budget alert

Set it before creating anything. `navya/infra/deploy.sh` deploys `navya/infra/budget.yaml` first (default limit USD 100 a month, set `BUDGET_USD`, counted before credits are applied so the alert can fire). Emails go at 50% and 80% of actual spend and when the forecast passes 100%. A budget only emails you: it does not stop spending. Keep the limit the mentor allows.

## Rules from the brief and how they are met

| Rule | How |
|---|---|
| Use free tier, student credits or an approved account | See above. Limit set by the mentor, Deep Paun: stay under the AWS free tier or the free credit, and spend no money beyond it. The account has a USD 100 credit and a payment method, per the owner (not checked in the Billing console). The running demo costs about USD 22 a month. The budget alert is set at USD 100 a month counted before credits, so it warns at USD 50 and USD 80 of actual use and at a forecast over USD 100 |
| Set a budget alert | `navya/infra/budget.yaml` is the first thing deployed |
| Never commit passwords or keys | Secrets only in SSM and environment. gitleaks in CI. |
| Shut down or delete unused resources after reviews and the final demo | `navya/infra/teardown.sh`, below |
| Use payment gateways in test mode only | The only gateway is `MockGateway` |

## Daily and demo-day routine

1. Before a demo: `navya/infra/deploy.sh` (with `DB_SNAPSHOT=` to restore data), run `load/smoke.mjs`, open the dashboard.
2. During: watch the dashboard and the alarm email.
3. After: `navya/infra/teardown.sh`, then check the console for leftovers.

## Clean-up checklist

Demo: `navya/infra/lightsail-demo.sh down` deletes the instance, the managed database (without a final snapshot), key pair, static IP, S3 bucket (emptied first) and the `navya-demo-s3` IAM user with its key. Snapshots you took stay and bill: delete them by hand. After the 2026-11-25 failed stack, the stack, RDS instance, VPC, final snapshot and SSM secrets were deleted. One automated RDS backup (`rds:navya-demo-...`) cannot be deleted by hand and expires on its own.

Full stack:

| Resource | Removed by teardown? |
|---|---|
| ECS service, cluster, task definitions (old revisions stay as inactive records) | Yes, except the revisions |
| Load balancer, target group, listener | Yes |
| CloudFront distribution | Yes (disabling and deleting takes a while) |
| RDS instance | Yes, with a final snapshot |
| **RDS final snapshot** | **No, on purpose.** Costs money while it exists: delete it when done. |
| S3 bucket and contents | Yes (emptied first) |
| ECR repository and images | Yes |
| Log group | Yes |
| VPC, subnets, security groups, gateway endpoint | Yes |
| SSM parameters | Yes (the script deletes them) |
| Budget stack, OIDC stack | **No, on purpose** (free, and the deploy role is needed next time) |
| SNS subscription | Removed with the topic |

After teardown, open the Billing page next day and check that the daily cost is zero.

## Limits of this setup

- Default `*.cloudfront.net` address only. No custom domain, so no certificate under our name and plain HTTP from CloudFront to the load balancer.
- Single-zone database by default. No scheduled scaling. No WAF.
- Rate limits are per task and the first `X-Forwarded-For` value is trusted.
- Audit and outbox files live on the task's temporary disk. The same lines are in CloudWatch Logs.
- First start after scaling up takes longer than the laptop numbers.
- The full stack (ECS, RDS, ALB, CloudFront) has not run to completion on AWS. Only the one-VM demo is running, see `11-demo-deployment-record.md`. Statements about the designed stack are still design, not observation, except: the budget stack deployed, the RDS instance was created with the looked-up engine version, and the stack stopped at CloudFront account verification.
- The account sits in an AWS Organization that allows only `us-east-1`, `us-west-2` and `ap-southeast-2`. Mumbai is denied.
- New-account limits: no CloudFront distribution until AWS Support verifies the account, and no Lightsail plan above `micro` (the `small` plan was refused).

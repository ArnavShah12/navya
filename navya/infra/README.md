# Infrastructure

CloudFormation for Navya on AWS (default region `ap-southeast-2`, Sydney: the AWS Organization only allows us-east-1, us-west-2 and ap-southeast-2). **The stack did not complete on its first run (2026-11-25).** The budget stack deployed and RDS was created, then CloudFront refused the distribution ("account must be verified", needs AWS Support) and the rollback failed (DEF-009, DEF-010 in `../../navya-docs/4-Software-Testing/08-defect-log.md`). The demo that runs is `lightsail-demo.sh` below.

| File | What it does |
|---|---|
| `budget.yaml` | Monthly cost budget with email alerts. Deployed first. |
| `main.yaml` | The application stack: network, RDS, S3, ECR, ECS Fargate, ALB, CloudFront, IAM roles, auto scaling, logs, alarms, dashboard. |
| `oidc.yaml` | Lets one GitHub repository's `main` branch deploy, with no stored AWS key. |
| `deploy.sh` | Budget, secrets in SSM, stack, image build and push, roll-out, wait. |
| `oidc.sh` | One-off: deploys `oidc.yaml` and prints the role ARN. |
| `teardown.sh` | Deletes the stack and secrets so nothing keeps billing. |
| `lightsail-demo.sh` | The running demo: one Lightsail VM with `docker-compose.demo.yml` (app and Caddy), a Lightsail managed PostgreSQL and S3. `up`, `push`, `db`, `https`, `s3`, `down`. |
| `.env.deploy`, `.env.demo-access` | Local, gitignored: region, alert email, budget, the demo address and password. Not in the repository. |

Read `../../navya-docs/5-Cloud-Computing/10-operations-clean-up-and-limits.md` before the first run: always on this costs about USD 95 a month.

## Demo on one VM (what is running)

```
infra/lightsail-demo.sh up       # about 10 minutes, bills about USD 22 a month from now (VM 7, database 15). Static IP, HTTPS (sslip.io name)
infra/lightsail-demo.sh db       # Lightsail managed PostgreSQL (USD 15 a month), part of up
infra/lightsail-demo.sh s3       # private bucket, bucket-only IAM user, photos in S3, part of up
infra/lightsail-demo.sh push     # new code
infra/lightsail-demo.sh down     # delete the VM, data is lost
```

Needs `aws`, `ssh`, `tar`. No Docker on your machine. Address, credentials, measurements and problems: `../../navya-docs/5-Cloud-Computing/11-demo-deployment-record.md`.

## First deployment (the full stack, CloudFront must be verified first)

```
aws sso login                      # or set AWS_PROFILE
aws sts get-caller-identity        # check you are in the account you mean
ALERT_EMAIL=you@example.com infra/deploy.sh
```

Optional variables: `ENV_NAME` (default `demo`), `AWS_REGION` (default `ap-southeast-2`), `DESIRED` (task count, default 1), `BUDGET_USD` (default 100), `ADMIN_EMAIL` (creates a moderator), `IMAGE_TAG`, `DB_SNAPSHOT`.

Then:

```
BASE_URL=https://<id>.cloudfront.net node load/smoke.mjs
```

and confirm the SNS email.

## Deploy from GitHub Actions

```
GITHUB_REPO=owner/repo infra/oidc.sh
```

Save the printed ARN as the repository variable `AWS_DEPLOY_ROLE_ARN`. Add the `production` environment in GitHub. Merges to `main` then deploy (`.github/workflows/deploy.yml`).

## Stop the bill

```
infra/teardown.sh
```

It snapshots the database, empties and deletes the bucket, deletes the stack and the SSM secrets. The final snapshot and the budget and OIDC stacks stay on purpose. Delete the snapshot when you no longer need it.

## Secrets

`deploy.sh` creates these SecureString parameters with random values, once: `/navya/<env>/db-password`, `jwt-secret`, `admin-password`, `origin-secret`. Nothing is printed or saved to a file. Read one with `aws ssm get-parameter --name /navya/demo/admin-password --with-decryption`.

## Why some choices

- Tasks in public subnets with a public IP and no NAT gateway: a NAT gateway bills by the hour. The task security group allows only the load balancer.
- CloudFront in front: free HTTPS on its own domain without buying one, edge cache for photos, WebSocket support. The load balancer accepts only CloudFront addresses that send a secret header.
- SSM Parameter Store instead of Secrets Manager: standard parameters are free.
- The PostgreSQL version is read from AWS at deploy time, because versions that are too old are refused for new instances.

# Deployment on a cloud platform

> Subject: Cloud Computing | Deliverable: the application running on a cloud platform, reachable by URL | Data: steps and scripts are real and linted (cfn-lint, `bash -n`). Live URL: `https://32-236-220-241.sslip.io` (demo VM, see `11-demo-deployment-record.md`). **The full stack below failed on CloudFront account verification and was removed. The running demo is the one-VM deployment in `11-demo-deployment-record.md`.**

## The demo deployment (what runs)

Needs: the AWS CLI signed in with a user that may create Lightsail, S3 and IAM resources, `ssh` and `tar`. No Docker on the machine running the script.

```
navya/infra/lightsail-demo.sh up       # creates and starts everything, prints the URL and the demo password
BASE_URL=https://<host> node navya/load/smoke.mjs
navya/infra/lightsail-demo.sh push     # later: send new code and rebuild
navya/infra/lightsail-demo.sh down     # delete everything
```

What `up` does, in order: (1) key pair and a Lightsail Ubuntu 24.04 VM (`micro_3_2`) with a 2 GB swap file and Docker installed at first boot; (2) a Lightsail PostgreSQL 16 (`micro_2_0`) with a random hex password sent straight to the VM's `.env`; (3) a static IP, port 443 opened, the hostname `<ip with dashes>.sslip.io`; (4) the code copied with `tar` over `ssh`, the image built on the VM, `docker compose -f docker-compose.demo.yml up` (Caddy and the app; Caddy gets the Let's Encrypt certificate); (5) a private S3 bucket and a bucket-only IAM user whose key goes straight into the VM's `.env`, the existing photos copied, the app switched to `STORAGE=s3`. Secrets are made on the VM or sent over stdin, never printed or written on the operator's machine, except the demo password printed once.

Result on 2026-11-25: smoke test 22 of 22. The record of the run, with the problems met, is `11-demo-deployment-record.md`. The sections below describe the full ECS design, which did not deploy.

## What gets deployed (the full ECS design)

`navya/infra/main.yaml` creates everything in one stack named `navya-<env>`: VPC and subnets, security groups, S3 bucket, RDS instance, ECR repository, ECS cluster, task definition and service, load balancer, CloudFront distribution, IAM roles, auto scaling, log group, metric filters, alarms, dashboard and an SNS topic. `navya/infra/budget.yaml` (budget alert) and `navya/infra/oidc.yaml` (GitHub deploy role) are separate stacks.

## Before you start

1. An AWS account and a signed-in CLI: `aws sso login` or `AWS_PROFILE`. Check `aws sts get-caller-identity`.
2. Docker, `openssl`, Node 22 (for the smoke test). The CLI must be v2.
3. Know your cost limit. Read `10-operations-clean-up-and-limits.md` first.
4. An email address for alarms and the budget.

## Steps (manual, what `navya/infra/deploy.sh` does)

```
ALERT_EMAIL=you@example.com infra/deploy.sh
```

| # | What happens | Where in the script |
|---|---|---|
| 1 | Deploy the budget stack first, so a cost alert exists before anything that costs money | `navya-budget` |
| 2 | Create SecureString parameters `/navya/<env>/db-password`, `jwt-secret`, `admin-password`, `origin-secret` with random values from `openssl`. Existing values are never overwritten. Nothing is printed or written to a file. | `put_secret` |
| 3 | Look up the CloudFront prefix list id and the current default PostgreSQL version for the region | `describe-managed-prefix-lists`, `describe-db-engine-versions` |
| 4 | Create the stack with zero tasks (the image repository is still empty) | first `deploy_stack` |
| 5 | Build the image, tag it with the git commit, push it to ECR | `docker build`, `docker push` |
| 6 | Update the stack with that image and the wanted task count, wait until the service is stable | second `deploy_stack`, `ecs wait services-stable` |
| 7 | Print the HTTPS address, the smoke-test command and the dashboard link | |

Then: confirm the SNS subscription email, run the smoke test, open the dashboard.

```
BASE_URL=https://<id>.cloudfront.net node load/smoke.mjs
```

Create the first moderator: set `ADMIN_EMAIL` when deploying. Its password is the SSM parameter `/navya/<env>/admin-password` (read it with `aws ssm get-parameter --with-decryption`).

Demo data: `SEED=true` is not set in the cloud template. To load demo members and listings, run the task once with `SEED=true` and `SEED_PASSWORD`, or sign up by hand. Seeding is not part of the smoke test, which needs no seed.

## Continuous deployment

`navya/infra/oidc.sh` once (it prints the role ARN, save it as the repository variable `AWS_DEPLOY_ROLE_ARN`). After that every merge to `main` runs `.github/workflows/deploy.yml`, see `08-ci-cd-pipeline.md`.

## Stop paying

`navya/infra/teardown.sh` deletes the stack and secrets. The database is snapshotted first. To bring the data back: `DB_SNAPSHOT=<name printed> ALERT_EMAIL=... infra/deploy.sh`. See `10-operations-clean-up-and-limits.md`.

## Evidence for the submission (demo)

Take these screenshots, account ids and emails hidden: the Lightsail instance page (running, static IP), the Lightsail database page (available, backups on, not public), the S3 bucket (block public access on, encryption), the budget with its alerts, the browser with the padlock on the app URL, the terminal with the smoke test result and `/health`, the admin stats output, and the Billing page the day after. The list below is for the designed full stack.

Screenshots, with account ids and emails hidden:

- Billing page with the budget alert
- CloudFormation stack with all resources `CREATE_COMPLETE`
- ECS service: running tasks, desired count 2, healthy targets
- CloudFront distribution and the browser address bar with the padlock on the app URL
- Load balancer listener rule (the `X-Origin-Verify` condition) and the security groups
- RDS instance: version, class, encryption, backup retention, not publicly accessible
- S3 bucket: block public access on, default encryption, lifecycle rule
- SSM parameter list (names only) and the task role and execution role policies
- ECR repository with a scanned image
- CloudWatch Logs query result and the dashboard during a k6 run, task count rising
- An alarm and the SNS subscription
- GitHub Actions: a green deploy run with the smoke test output
- Restore drill steps with timestamps
- Teardown and the next-day billing page

## What went wrong on the first run (2026-11-25)

- The organization limits regions and services (only `us-east-1`, `us-west-2`, `ap-southeast-2`). Mumbai was denied.
- CloudFront refused the distribution: "Your account must be verified before you can add new CloudFront resources". Everything else in the stack was cancelled and the rollback failed because the RDS instance was still being created. The engine version lookup worked: RDS created the instance.
- `deploy.sh` needs a reachable Docker daemon. `INFRA_ONLY=1` stops after the stack, before the image build.

Details and fixes: `11-demo-deployment-record.md`.

## Things most likely to go wrong on the first run

- The account may not allow Fargate, an ALB or RDS on its plan (check before spending). Here they were allowed in Sydney for reads, and RDS created.
- CloudFormation may reject the database version if the default lookup returns one the template cannot use.
- A security group rule that uses the CloudFront prefix list counts as many rules against the group's quota. If the stack fails on that, ask for a quota increase or restrict the listener by header only.
- The ECS service will fail health checks if the task cannot read an SSM parameter (the execution role is scoped to `/navya/<env>/*`) or cannot reach the database (check the security group and `sslmode=require`).
- The first request after scaling up pays the JVM start time.

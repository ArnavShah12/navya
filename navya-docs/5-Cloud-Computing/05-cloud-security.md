# Cloud security

> Subject: Cloud Computing | Deliverable: IAM, no hard-coded secrets, HTTPS, encryption notes | Data: design is real (template and code). Application-level security checks and their results are in `../4-Software-Testing/07-non-functional-testing/security-testing.md`.

## Demo VM (what runs now)

HTTPS comes from Caddy and a Let's Encrypt certificate for a `<ip>.sslip.io` name, with HSTS from the app. Photos are in a private S3 bucket (public access blocked, SSE-S3), reached by an IAM user that can only touch that bucket. The database is private (not publicly accessible) and reached over TLS, with the server certificate not verified. The VM disk is not separately encrypted. Secrets, including the database password, live in `~/navya/.env` on the VM. Port 22 is open to the world, key login only. Details: `11-demo-deployment-record.md`.

## Identity and access (IAM)

| Principal | Can | Cannot |
|---|---|---|
| ECS **task role** (the running app) | `GetObject`, `PutObject`, `DeleteObject` on the one bucket's objects, `ListBucket` on that bucket | Anything else in the account |
| ECS **execution role** (starts the task) | Pull the image, write logs (AWS managed policy), read `ssm:GetParameters` on `/navya/<env>/*`, `kms:Decrypt` only through SSM | Read other parameters or any bucket |
| **GitHub deploy role** (CI) | Log in to ECR, push images to the one repository, register task definitions, update the one service, pass the two Navya roles to ECS | Be assumed from any branch other than `main` of the named repository |
| **Auto scaling role** | Describe and update the one service, manage its scaling alarms | |
| People | Use their own IAM or SSO user with MFA. The root user has MFA and no access keys. No shared logins. | |

The CI role trusts GitHub's OIDC provider with two conditions: audience `sts.amazonaws.com` and subject `repo:<owner>/<repo>:ref:refs/heads/main`. No AWS key is stored in GitHub.

Within the app, role-based access has two roles, `MEMBER` and `ADMIN`, carried in a signed token and checked on every route. Ownership is checked on every write.

## No secrets in the repository

| Secret | Where it lives | How it reaches the app |
|---|---|---|
| Database password | SSM SecureString `/navya/<env>/db-password` | ECS `secrets` entry, injected as `DB_PASSWORD` at task start |
| Token signing key | SSM `/navya/<env>/jwt-secret` | `JWT_SECRET` |
| Admin password | SSM `/navya/<env>/admin-password` | `ADMIN_PASSWORD` |
| Origin secret (CloudFront to load balancer) | SSM `/navya/<env>/origin-secret`, also a `NoEcho` stack parameter | Header on the listener rule and the CloudFront origin |
| AWS credentials for S3 | None. The SDK uses the task role. | |

`navya/infra/deploy.sh` makes the values with `openssl rand`, never prints them and never writes a file. The app refuses to start without `JWT_SECRET` of at least 32 characters unless `DEV=true`. `.env` files are git-ignored and docker-ignored. gitleaks scans the whole history in CI. A repository search for key-like strings found nothing (TC list, security testing doc).

**Limit:** the origin secret is also visible in the ALB listener rule and CloudFront origin configuration to anyone who can read those resources. It is a gate against casual direct access, not a strong secret. Rotate by changing the parameter and redeploying.

**Rotation:** change the SSM value, then restart the service (`aws ecs update-service --force-new-deployment`). Rotating `jwt-secret` logs everyone out. Rotating the database password needs the new value set on the RDS instance first. Do this once as a drill and record it.

## Encryption

| Data | At rest | In transit |
|---|---|---|
| Browser to CloudFront | n/a | TLS, HTTP redirected to HTTPS, `Strict-Transport-Security` added by a CloudFront response-headers policy on every response |
| CloudFront to load balancer | n/a | **Plain HTTP** (limit, see architecture doc) |
| Load balancer to task | n/a | Plain HTTP inside the VPC |
| App to RDS | RDS storage encrypted (KMS-managed key) | `sslmode=require` (encrypts, does not verify the certificate) |
| App to S3 | SSE-S3 (AES-256) | HTTPS, and the bucket policy denies non-TLS requests |
| SSM parameters | SecureString (KMS) | HTTPS |
| Passwords in the database | bcrypt hashes | |
| ECR images | Encrypted by default | HTTPS |

## Network

Database in private subnets with no internet route. Security groups as listed in the architecture doc. Load balancer reachable only from CloudFront addresses, and only with the secret header. No SSH anywhere (Fargate). S3 reached through a gateway endpoint.

## Supply chain and audit

Image scan on push (ECR) and in CI (trivy, with two accepted Jetty findings in `.trivyignore`). trivy was run by hand on 2026-11-25 on the dependencies, the project files and the infrastructure files (`../4-Software-Testing/07-non-functional-testing/security-testing.md`). Dependency versions are pinned in `pom.xml`. CloudTrail's default 90-day event history is available in every account; no trail was configured.

## Not done

No WAF in front of CloudFront. No custom domain, so no certificate under our own name. No secret rotation drill yet. No GuardDuty or Config.

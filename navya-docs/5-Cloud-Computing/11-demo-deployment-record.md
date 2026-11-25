# Demo deployment record

> Subject: Cloud Computing | Deliverable: the application running on a cloud platform, with how it was done, what it cost and what went wrong | Data: real, from the deployment on 2026-11-25. Prices are what the Lightsail API returned that day.

## What is running

Navya runs on one Amazon Lightsail virtual machine in `ap-southeast-2` (Sydney). Docker Compose starts two containers on it: Caddy (HTTPS) and the app. The database is a Lightsail managed PostgreSQL 16 (`micro_2_0`), not on the VM. Photos are stored in a private S3 bucket in the same region (`STORAGE=s3`).

| Item | Value |
|---|---|
| Address | `https://32-236-220-241.sslip.io` (a free sslip.io name that resolves to the static IP `32.236.220.241`) |
| HTTPS | Caddy gets and renews a Let's Encrypt certificate for that name (issued 2026-11-25, valid to 2027-02-23). HTTP redirects to HTTPS (308), `Strict-Transport-Security` is set. |
| Static IP | Lightsail static IP `navya-demo-ip`, attached to the VM. The address survives a stop and start. |
| Database | Lightsail managed PostgreSQL 16.15, plan `micro_2_0` (1 GB RAM, 40 GB disk), database `navya`, user `navya`. Not publicly accessible: only Lightsail resources in the same region reach its private endpoint. Automatic backups on (window 15:32 to 16:02 UTC). The connection uses TLS (`sslmode=require`), the server certificate is not verified. |
| Photos | S3 bucket `navya-demo-<account id>-ap-southeast-2`: all public access blocked, SSE-S3 encryption. Eight seeded photos were copied from the local volume. |
| VM | Lightsail `micro_3_2`: 2 vCPU, 1 GB RAM, 40 GB disk, 1 TB transfer a month, Ubuntu 24.04 |
| Swap | 2 GB swap file, added at first boot (the Java build does not fit in 1 GB otherwise) |
| Files | `navya/docker-compose.demo.yml`, `navya/infra/lightsail-demo.sh` (`up`, `push`, `https`, `s3`, `down`) |
| Region | Sydney, not Mumbai (see below) |
| Price | VM USD 7 a month plus database USD 15 a month, about USD 22 in all (about USD 0.03 an hour), billed while they exist |

## Why Sydney and why not the full stack

The AWS Organization that owns the account has service control policies. `AdvancedModeRegionRestrictionSecurityControlPolicy` allows only `us-east-1`, `us-west-2` and `ap-southeast-2`, and in the first two it allows only a short list of global-style services. Only Sydney allows ordinary services (EC2, RDS, ECS, Lambda, Lightsail and the rest). Mumbai is denied. The earlier design used Mumbai.

The full CloudFormation stack (`infra/main.yaml`) was tried in Sydney and failed: CloudFront refused to create the distribution with "Your account must be verified before you can add new CloudFront resources". That check needs AWS Support. Everything else in the stack was cancelled. The demo path avoids CloudFront, the load balancer, ECS and RDS.

## Credentials and where they are

No secret is in the repository or in these documents.

| What | Where | Notes |
|---|---|---|
| AWS CLI sign-in | `~/.aws/credentials` on the development machine (IAM user with `AdministratorAccess`) | Region is set by `infra/.env.deploy` and `~/.aws/config` |
| SSH key to the VM | `navya/infra/demo-key.pem` (gitignored, mode 600) | Made by `lightsail-demo.sh up`, deleted by `down` |
| Database | Host and password in `~/navya/.env` on the VM (`DB_HOST`, `DB_PASSWORD`), mode 600. User `navya`. Password is 32 random hex characters made on the development machine and sent straight to the VM and to AWS | Rotate: `lightsail-demo.sh db` again (sets a new password, restarts the app) |
| JWT secret, demo password | `~/navya/.env` on the VM, mode 600 | Random, made on the VM, never printed |
| S3 access | IAM user `navya-demo-s3`, inline policy `bucket-only` (get, put and delete objects, list, in the one bucket). Its access key is in `~/navya/.env` on the VM only | The key was piped from AWS to the VM and never written on the development machine. Rotate it with `aws iam create-access-key` and `delete-access-key`. |
| Demo accounts | `aanya@navya.test`, `rohan@navya.test`, `meera@navya.test`, and the moderator `admin@navya.test` | One shared password, the `SEED_PASSWORD` line in `~/navya/.env` |
| Where to read them | `navya/infra/.env.demo-access` (gitignored) has the address, the ssh command and the demo password | |
| Certificate | Held by Caddy in the `caddydata` Docker volume, renewed by Caddy | No private key outside the VM |
| Budget alert emails | The address in `navya/infra/.env.deploy` | |

Read the demo password again: `ssh -i navya/infra/demo-key.pem ubuntu@<ip> 'grep SEED_PASSWORD navya/.env'`.

The demo password protects fake data only. Do not reuse it anywhere else.

## Operate it

```
navya/infra/lightsail-demo.sh up      create the VM, static IP, HTTPS, copy the code, build, start (once)
navya/infra/lightsail-demo.sh push    send new code and rebuild on the running VM
navya/infra/lightsail-demo.sh db      managed database: create, copy data, point the app at it (part of up). Run again to rotate the password
navya/infra/lightsail-demo.sh https   static IP, port 443 and the certificate (part of up)
navya/infra/lightsail-demo.sh s3      bucket, IAM user, copy photos, switch the app to S3 (run once after up)
navya/infra/lightsail-demo.sh down    delete the VM, database (no final snapshot), key, static IP, bucket and S3 user. The data is lost.
BASE_URL=https://32-236-220-241.sslip.io node navya/load/smoke.mjs        smoke test
ssh -i navya/infra/demo-key.pem ubuntu@<ip> 'cd navya && docker compose -f docker-compose.demo.yml logs --tail 50 app'
```

The containers restart by themselves after a reboot (`restart: unless-stopped`). `push` keeps the existing `.env`, so logins survive an update. Seed data is created once, on the first start. Photos for new listings go to S3.

The managed database also takes automatic backups (retention is on, the number of days was not read). Dump it by hand before anything risky:
```
ssh -i navya/infra/demo-key.pem ubuntu@<ip> 'cd navya && docker run --rm -e PGPASSWORD="$(grep ^DB_PASSWORD= .env | cut -d= -f2)" postgres:16 pg_dump -h "$(grep ^DB_HOST= .env | cut -d= -f2)" -U navya navya' > navya-backup.sql
```
A Lightsail snapshot (`aws lightsail create-instance-snapshot`) also works and bills by size while it exists (not checked in this account). Photos are in S3 and are not in the `pg_dump`.

## Measured on the live VM (2026-11-25, a few minutes after start)

| Measure | Result |
|---|---|
| Smoke test (`load/smoke.mjs`, HTTPS, 22 checks incl. HTTP redirect, photo round trip through S3, WebSocket push) | 22 of 22 passed on the final setup (managed database): 12.4 s. Earlier runs: 12.6 s with the local database and S3, 21 of 21 in 10.5 s on plain HTTP with local photos. |
| Load (k6 `browse.js`, 50 peak users, 1 s pause, 80 s, from the development machine) | 2,928 requests, 36.3 a second, 0 failed, p95 422 ms (about 390 ms of it network). Raw output: `../4-Software-Testing/07-non-functional-testing/results/load-browse-50vu-cloud-demo.txt` |
| Request time from the development machine (`/health`, `/`, `/api/listings`) | 0.77 to 0.79 s each, of which about 0.39 s is the TCP connect. The distance to Sydney, not the app, dominates. |
| App container memory | 147 MiB (110 MiB in the first run) |
| Caddy container memory | 15 MiB |
| VM memory | 528 MB used of 909 MB, swap 73 MB of 2 GB used (40 minutes after boot, final setup). With PostgreSQL on the VM it was 518 MB, so the database move freed little: the JVM and page cache fill the space. |
| Load average | 0.17 (1 min) |
| Disk | 7.7 GB of 38 GB used. App image 522 MB. Old PostgreSQL image and volume `navya_dbdata` still on disk. |

Not measured: load under many users (the k6 runs were done on the laptop only), restart time, and anything after the first hour.

## What went wrong, in order

| # | What happened | Fix |
|---|---|---|
| 1 | `sso:CreateInstance` denied by an SCP, so IAM Identity Center could not be set up | Used an IAM user with an access key |
| 2 | All regions except three denied, and `us-east-1` allowed only reads on regional services | Found the region policy in the Organizations console, used `ap-southeast-2` |
| 3 | The CloudFormation stack failed: CloudFront needs account verification | Not fixed. Asked for AWS Support. Used a Lightsail VM instead. |
| 4 | The stack rollback itself failed (`ROLLBACK_FAILED`): the RDS instance was still being created, so the snapshot taken on delete was refused | Waited until the database was `available`, then deleted the stack. Deleted the snapshot and the four SSM secrets by hand. |
| 5 | `deploy.sh` needs a Docker daemon the user could reach, and a login session started before the user joined the `docker` group | Added `INFRA_ONLY=1` to `deploy.sh`. Not needed on the demo path: the image is built on the VM. |
| 6 | Lightsail bundle ids differ per region (`small_3_0` in `us-east-1`, `small_3_2` in Sydney) | Script uses `micro_3_2` |
| 7 | A new account cannot create a `small` plan ("can not create an instance using this Lightsail plan size") | Used `micro_3_2` with swap |
| 8 | A key pair and an instance cannot share the name `navya-demo` | Key pair is `navya-demo-key` |
| 9 | `rsync` is not installed on the development machine | The script uses `tar` over ssh |
| 10 | The managed database's generated master password contains shell characters. Sourcing `.env` on the VM broke on it, so the data copy never ran, and fragments of the password were printed into the session log | The script now sets its own hex password, reads `.env` with `grep` instead of sourcing it, and the leaked password was replaced by the second run |

Items 1 to 10 are in the defect log (DEF-008 to DEF-013). Then HTTPS and S3 were added the same day: the static IP changed the VM's address (the old plain-HTTP URL stopped working), and both worked on the first try. The managed database was added last, with item 10 on the way. The 8 seeded listings were copied from the local container and the old container was removed. Its volume `navya_dbdata` is still on the VM as a fallback.

## Full check (2026-11-25, after all changes)

Read-only checks against the live system and the account.

| Check | Result |
|---|---|
| `/health` | `{"status":"ok","storage":"s3"}` |
| HTTP to HTTPS | 308 redirect, also on the bare IP |
| Certificate | Let's Encrypt, valid to 2027-02-23, obtained by Caddy at first start |
| Headers | CSP, `X-Content-Type-Options`, `Strict-Transport-Security` (seen on a GET, not on a HEAD request) |
| Smoke test | 22 of 22, 11.5 s |
| Old plain-HTTP address | Dead, as expected |
| Containers | `navya-app-1` healthy, `navya-caddy-1` up, no app errors in the log |
| VM | 517 of 909 MB used, swap 72 MB, disk 21% used, no reboot pending, `unattended-upgrades` active, `.env` mode 600 |
| Lightsail | one instance (running, `micro_3_2`), one static IP (attached), one database (available, 16.15, not public, backups on, single zone), one key pair, no snapshots, no extra disks |
| Firewall | ports 22, 80 and 443 open to `0.0.0.0/0` |
| S3 | one bucket, all four public-access blocks on, AES256 default encryption, 16 objects (40 KB) |
| IAM | two users. `navya-demo-s3` has one key and a policy limited to that bucket (get, put, delete objects, list). |
| Leftovers from the failed full stack | none: no CloudFormation stack besides `navya-budget`, no RDS instance or snapshot, no ECR, no SSM parameter, no log group, no EC2 instance, no Elastic IP, no extra VPC or NAT gateway |
| Budget | `navya-monthly`, USD 100, credits excluded from the count, alerts at 50% and 80% actual and 100% forecast, one email subscriber |
| Cost Explorer | USD 0.00 for 24 and 25 November, but the data lags up to a day, so this proves nothing yet |
| Backup command in this document | Works: a 22 KB dump with the users, listings, orders and messages tables |
| Credits | Not readable through the API. Check the Billing console. |

Found:

- **(Cleaned, see above.) Test data in the live database.** 20 users (4 demo, 16 from eight smoke runs), 8 listings, 16 orders (the smoke test's boosts) and 0 messages. The smoke test signs up two members and pays two boosts every run and does not remove them. Cleaned before the demo, see below.
- **(Cleaned, see above.) Orphan photos in S3.** 16 objects, 8 of them referenced by listings. The smoke test uploads a photo through `/api/images` but creates its listing with no images, so each run leaves one file. The app also never removes a photo that was uploaded and not attached. Open issue OI-11.
- **SSH is open to the world** on port 22, protected by the key only. Not changed.
- **(Removed.) Old local database volume** `navya_dbdata`.

## Consent and legal pages on the live demo (2026-11-25)

Deployed with `lightsail-demo.sh push`. The app added the two consent columns to the managed database on start (`ALTER TABLE ... IF NOT EXISTS`), with no manual step. Checked on the live site:

| Check | Result |
|---|---|
| `/terms.html`, `/privacy.html`, `/grievance.html` | 200 each, "Version 2026-11-25", the grievance page names Arnav Shah with a mailto link |
| Home page | The three links in the footer, the required consent box on the sign-up form |
| Sign-up without `consent` or with `false` | 400 "Please confirm you are 18 or older and accept the Terms of Use and Privacy Notice" |
| Existing demo logins (`aanya`, `admin`) | 200, so accounts without a consent record still work |
| Smoke test | 22 of 22, 12.9 s. The two accounts it created have `consented_at` set and `terms_version` 2026-11-25. |
| Data-requests runbook (`2-E-Commerce/legal/data-requests-runbook.md`) | Tested on a scratch copy of the live database, then dropped: the erasure removed the member's 3 listings, 2 chats with 3 messages, 1 favourite and 1 report, kept the 1 boost order with the listing cleared, left another member's 3 listings untouched, replaced the name, email and city and left a 60-character bcrypt hash; the export returned a 6 KB JSON with no password hash |

## Clean-up before the demo (2026-11-25, done)

| What | Before | After |
|---|---|---|
| Users in the database | 22 (4 demo, 18 from smoke runs) | 4 demo users (`aanya`, `rohan`, `meera`, `admin` at `@navya.test`) |
| Orders | 18 (all from smoke runs) | 0 |
| Listings | 8 | 8 (the seed listings, untouched) |
| Objects in S3 | 17 | 8, exactly the photos the 8 listings reference |
| Old local database volume on the VM | present | removed (its data is in the managed database) |
| App usage counters (`/api/admin/stats`) | 77 requests and test traffic | reset by an app restart |
| Second clean-up after the consent deploy | 8 users, 1 chat, 1 message, 2 orders, 9 photos in S3 | 4 users, 8 listings, 0 orders, 0 chats, 8 photos (the ones the listings reference) |

After the clean-up all four demo logins returned 200, all 8 listing photos came back from S3 with 200, `/health` was ok, the VM and database were running. The smoke test was not run after the clean-up on purpose: each run adds 2 users, 2 orders and 1 photo. If you run it again, remove those afterwards (OI-11).

## Does it need to be stopped?

No. It costs about USD 22 a month (VM USD 7, database USD 15), about 22% of the USD 100 credit. The static IP is free while attached, and S3 holds 39 KB of photos. Three more days is about USD 2.2 (USD 0.69 for the VM and USD 1.48 for the database, from list prices, not a bill). Stopping it saves nothing: AWS documents that Lightsail bills a stopped instance. Only deleting the VM stops the charge, and that loses the data. Leave it running until the final demo, then run `lightsail-demo.sh down`.

Check before relying on this:
- Whether the credits apply to Lightsail, and when they expire. The Free Plan terms in `10-operations-clean-up-and-limits.md` were not confirmed in this account.
- The Billing page the next day. Nothing in this document has been checked against a real bill yet.

## Brief checklist

| Brief item | Demo |
|---|---|
| Public cloud, reachable by URL | Yes, the address above |
| Docker, Dockerfile in the repository | Yes |
| Object storage | Yes, S3 |
| Managed database or equivalent | Yes. Lightsail managed PostgreSQL 16 with automatic backups, private endpoint, TLS. |
| IAM / RBAC | An IAM admin user for the operator, and a bucket-only IAM user for the app. App roles are member and admin. |
| No hard-coded secrets | Yes. Secrets are in the VM's `.env` and the gitignored local files. |
| HTTPS | Yes, Let's Encrypt through Caddy. |
| Encryption at rest | S3 bucket SSE-S3. The VM disk is not separately encrypted by us. Whether the Lightsail database disk is encrypted was not checked. |
| Scaling and backup plan | Design only (`06-scalability-and-reliability-plan.md`). One VM and a single-zone database. The database has automatic backups, the VM does not. |
| Logs and a metric | App logs through `docker compose logs`. `/health` is the health check. No CloudWatch. |
| CI/CD or documented manual deployment | The manual process is `lightsail-demo.sh`. The GitHub workflow targets ECS and has never run. |

## Limits

- The certificate is for an sslip.io name, a free public service. If sslip.io is down the name does not resolve, though the IP still works over HTTP.
- The VM is one machine with no snapshots: a deleted VM loses the code copy and the secrets file (the database and photos live outside it). `down` deletes the database without a final snapshot.
- Port 22 is open to the world and protected by the key only.
- Memory is tight: 1 GB with swap. The app was idle when measured.
- Latency from the development machine is about 0.4 s per round trip.
- The S3 access key sits in a file on the VM. Anyone with shell access to the VM can read it.
- The full design (ECS, RDS, ALB, CloudFront) has still not run to completion. The RDS instance in it did reach `available`, which confirms the default engine version lookup works.

# Cloud storage and database

> Subject: Cloud Computing | Deliverable: at least one object storage service and one managed database | Data: design is real (template and code).

## Demo VM (what runs now)

Photos: S3 bucket `navya-demo-<account>-ap-southeast-2`, private, SSE-S3. Database: Lightsail managed PostgreSQL 16 (`micro_2_0`, 40 GB, private endpoint, TLS, automatic backups), not RDS. One zone, no standby. See `11-demo-deployment-record.md`.

## Database: Amazon RDS for PostgreSQL

| Setting | Value | Why |
|---|---|---|
| Engine | PostgreSQL, version = the default AWS offers on the deploy day | Older versions stop being allowed for new instances (DEF-005). The app needs only features from PostgreSQL 13 and later. Local tests use 14, the compose file uses 16. |
| Class | `db.t4g.micro` (parameter) | Smallest burstable class, enough for the measured load. Its CPU credits are the thing to watch under sustained load. |
| Storage | 20 GB gp3, encrypted | |
| Placement | Private subnets, not publicly accessible, security group allows only the app tasks | |
| Connection | `sslmode=require` | Encrypted in transit. This does not verify the server certificate (limit below). |
| Availability | Single zone by default. `DbMultiAz=true` for failover. | Cost: the Multi-AZ price is double. |
| Backups | Automated, `DbBackupDays` (default 1). Final snapshot when the stack is deleted. | Check what the account type allows before raising it. |
| Credentials | Master password is a SecureString in SSM, read through a CloudFormation dynamic reference. The task reads the same parameter at start. | No password in the repository or the template |

Schema: `src/main/resources/db/schema.sql`, run at every start under an advisory lock, so two tasks starting together take turns. Tables: users, listings, listing_images, favourites, conversations, messages, reports, orders. Money is whole paise. Integrity is enforced by the database: unique email, one chat per buyer and listing, value checks, a unique idempotency key.

Why PostgreSQL and not NoSQL: the data is relational, races are settled by constraints, and chat uses `LISTEN/NOTIFY` between app instances.

Limits to state: the app's database user can change the schema (migrations run at start-up). `sslmode=require` encrypts but does not check the certificate. `LISTEN/NOTIFY` does not work through a connection pooler in transaction mode, so RDS Proxy would need a direct connection for the chat listener.

## Object storage: Amazon S3

One bucket, `navya-<env>-<account>-<region>`:

| Prefix | Content | Rule |
|---|---|---|
| `<member id>/<uuid>.jpg` | Listing photos | Kept until the listing is deleted (the app deletes the files) |
| `exports/` | CSV and JSON catalogue exports | Lifecycle rule deletes after 30 days |

Settings: all public access blocked, default encryption (SSE-S3, AES-256), a bucket policy that denies any request not using TLS, incomplete multipart uploads aborted after 2 days. Access is only through the ECS task role (get, put, delete on the objects, list on the bucket). The browser never talks to S3: the app receives the photo, checks it, stores it, and serves it back at `/img/<key>`, where CloudFront caches it.

Why through the app: the app checks the size (600 KB) and the JPEG file signature, picks the key and the folder, and applies per-member limits. A direct-to-S3 upload would need presigned URLs and CORS rules, and the server would not see the bytes.

`FileStorage` has two implementations. `S3FileStorage` is used in the cloud (`STORAGE=s3`) and `LocalFileStorage` locally. With `S3_ENDPOINT` set it talks to MinIO.

## What happens on delete

Deleting a listing deletes its row (images, chats, favourites and reports go with it through `ON DELETE CASCADE`, orders keep their row with the listing set to null) and then its photo files. A failed photo delete is ignored: an orphan photo costs a few KB, and a failed delete must not fail the request.

## Backup and restore, summary

See `06-scalability-and-reliability-plan.md`. In short: RDS automated backups and a final snapshot, S3 as the second copy of the catalogue through the admin export, and a restore drill to be done once.

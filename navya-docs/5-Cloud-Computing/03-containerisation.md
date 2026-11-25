# Containerisation

> Subject: Cloud Computing | Deliverable: the application packaged with Docker, Dockerfile in the repository | Data: `Dockerfile` passes hadolint. **The image was built and run on the demo VM (2026-11-25): app image 522 MB, PostgreSQL 667 MB, both containers running, `/health` answered 200. It was not built on the development machine (no Docker access there).** CI builds and scans it.

## Dockerfile (`Dockerfile`)

- **Two stages.** Stage 1 is `maven:3.9-eclipse-temurin-21`: copies `pom.xml` alone first so the dependency download is cached until the pom changes, then compiles and packages with `-DskipTests` (tests run in CI before the image is built). Stage 2 is `eclipse-temurin:21-jre`: only the runtime and `navya.jar`.
- **Non-root user** (uid 10001). `/data` is its only writable folder, for `audit.log` and `outbox.log`.
- **JVM flags** `-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError`: the heap follows the container's memory limit, and an out-of-memory error ends the process so ECS replaces the task.
- **Health check** is a TCP connect (the base image has no `curl`). The load balancer and ECS probe `GET /health` over HTTP.
- **No secrets** in the image or in build arguments. `JWT_SECRET`, `DB_PASSWORD` and the rest arrive as environment variables at run time, from SSM in AWS.
- **Tag** is the git commit (`${GITHUB_SHA::12}` in CI). `latest` is used only for the first empty stack.
- `.dockerignore` keeps `.git`, `target`, tests, docs, `e2e`, `load`, `infra`, `.env` files out of the build context.

## Local stack (`docker-compose.yml`)

```
cp .env.example .env        # fill DB_PASSWORD and JWT_SECRET: openssl rand -hex 24
docker compose up --build
```

| Service | Image | Stands in for |
|---|---|---|
| `db` | `postgres:16` | RDS |
| `minio` + `minio-init` | `minio/minio`, `minio/mc` | S3 (creates the bucket `navya`) |
| `app` | built from `Dockerfile` | the ECS task |

The app gets the same environment variable names it gets in AWS, with `S3_ENDPOINT` pointing at MinIO. `docker-compose.limits.yml` adds 1 GiB of memory and one core, to run at roughly the planned task size: `docker compose -f docker-compose.yml -f docker-compose.limits.yml up`. The limits must exist when the container starts, because the JVM sizes its heap from them.

Compose refuses to start with an empty `DB_PASSWORD` or `JWT_SECRET`. The MinIO login `minioadmin` is the emulator's public default and is allowed through gitleaks in `.gitleaks.toml`.

## Run without Docker (what was actually run here)

```
./mvnw package
DEV=true DB_URL='jdbc:postgresql://localhost:5432/navya?user=...&password=...' SEED=true SEED_PASSWORD=choose-a-password java -jar target/navya.jar
```

The tests and `e2e/run.sh` start an embedded PostgreSQL from a Maven artifact, so neither needs Docker.

## Image size and start time

Measured on the demo VM, 2026-11-25: the app image is 522 MB (the PostgreSQL image pulled for backups is 667 MB). Javalin logged "started in 345ms" on the VM, and the container reported healthy within a minute of a restart. On a laptop the jar answers `/health` in 0.6 seconds when limited to half a core and 1 GiB, with the database already running (`../4-Software-Testing/07-non-functional-testing/performance-testing.md`). The image build time on the VM was not recorded.

## Demo stack (`docker-compose.demo.yml`)

Two services: `caddy` (HTTPS, reverse proxy to the app) and `app` (the Dockerfile image). The database is a managed Lightsail PostgreSQL and photos go to S3, so neither is a container. Started by `infra/lightsail-demo.sh` on the VM. Secrets come from a `.env` file with mode 600 on the VM.

## Not verified

That `docker compose up` with the standard `docker-compose.yml` brings the stack up healthy, and that the app talks to MinIO with the checksum settings in that file (`AWS_REQUEST_CHECKSUM_CALCULATION=when_required`, needed by some S3-compatible stores). The image itself builds and runs (demo VM), and the app talks to real S3 (smoke test).

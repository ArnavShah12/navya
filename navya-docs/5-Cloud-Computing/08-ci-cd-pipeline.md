# CI/CD pipeline

> Subject: Cloud Computing | Deliverable: a CI/CD pipeline that builds, runs automated tests and deploys, or a documented manual process | Data: the workflow files pass `actionlint`. The manual process in `02-deployment-on-a-cloud-platform.md` is the fallback. Workflow files: `.github/workflows/`.

## Status (2026-11-25)

The repository has four local commits and no remote, so neither workflow has run on GitHub yet. The workflows are in `.github/workflows/` at the repository root (GitHub reads them only there) and run their steps in `navya/` (`defaults.run.working-directory`). `actionlint` is clean. `ci.yml` (build, tests, scans) is ready. `deploy.yml` deploys to ECS, which this account cannot run (see `11-demo-deployment-record.md`), and its deploy job stays skipped until the variable `AWS_DEPLOY_ROLE_ARN` is set. The documented manual process that was used for the running demo is `navya/infra/lightsail-demo.sh up|push|down`, with the same build and test steps run by hand (`./mvnw verify`, then `lightsail-demo.sh push`, then the smoke test).

## `.github/workflows/ci.yml` (every pull request and every push to `main`)

| Job | Does |
|---|---|
| `test` | Java 21, Maven cache, `./mvnw verify` with MinIO as a service container (so `S3StorageTest` runs). Uploads surefire and JaCoCo reports and the jar. |
| `browser` | Builds the jar, starts PostgreSQL and the app through `e2e/run.sh`, drives Chrome, uploads the screenshots. |
| `secrets` | gitleaks over the whole git history. |
| `templates` | `cfn-lint` on `navya/infra/*.yaml` and hadolint on the `Dockerfile`. |
| `image` | Builds the Docker image without pushing, scans it with trivy for HIGH and CRITICAL findings that have a fix, honouring `.trivyignore`. |

## `.github/workflows/deploy.yml` (every push to `main`, or by hand)

1. `test`: `./mvnw verify`.
2. `deploy` (skipped until the repository variable `AWS_DEPLOY_ROLE_ARN` is set; runs in the `production` environment so a reviewer can be required):
   1. Assume the AWS role with GitHub's OIDC token (no stored key).
   2. Log in to ECR, build the image, tag it with the commit, push it.
   3. Save the running task definition (to roll back to).
   4. Put the new image into a copy of that task definition and deploy it to the ECS service, waiting until the service is stable. The ECS circuit breaker already rolls back a deployment whose tasks never become healthy.
   5. Read the public URL from the stack outputs.
   6. Run `load/smoke.mjs` against it.
   7. If the smoke test fails, point the service back at the saved task definition and wait until stable.

Only one deploy runs at a time (`concurrency`). The job needs `id-token: write` and nothing else beyond reading the repository.

## One-time set-up

```
GITHUB_REPO=owner/repo navya/infra/oidc.sh          # prints the role ARN
# GitHub: Settings, Secrets and variables, Variables: AWS_DEPLOY_ROLE_ARN (the ARN), AWS_REGION, NAVYA_ENV
# GitHub: Settings, Environments: add "production" and, if wanted, required reviewers
```

## Known limits

- The task definition is changed by the pipeline outside CloudFormation. If someone then runs `navya/infra/deploy.sh` with an older `IMAGE_TAG`, it will roll the image back. Always deploy through one path at a time, or pass the running tag.
- The deploy role can register any task definition and pass the two Navya roles. It cannot change the stack, the database or the bucket.
- Pull requests from forks do not get the deploy role (the role trusts only `main`).
- trivy is pinned to version 0.28.0 of its action, copied from the earlier project. Update it.

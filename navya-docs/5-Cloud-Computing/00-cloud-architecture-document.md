# Cloud architecture document

Dockerfile, docker-compose files, CI/CD workflows and the infrastructure templates are in the repository root: `Dockerfile`, `docker-compose*.yml`, `.github/workflows/`, `navya/infra/`.

| Deliverable (from the assignment) | File | Status |
|---|---|---|
| Cloud architecture design and justification | `01-cloud-architecture-design.md` | Designed (ECS design, templates lint clean). The deployed demo architecture is in the same file, with its own diagram |
| Deployment on a cloud platform | `02-deployment-on-a-cloud-platform.md` (scripts in `navya/infra/`) | **Demo deployed** on a Lightsail VM in Sydney (`11-demo-deployment-record.md`). The full stack failed on CloudFront account verification |
| Containerisation | `03-containerisation.md` | Dockerfile linted. Image built and run on the demo VM (522 MB), compose stack ran there |
| Cloud storage and database | `04-cloud-storage-and-database.md` | Designed |
| Security (IAM, secrets, HTTPS, encryption) | `05-cloud-security.md` | Designed |
| Scalability and reliability | `06-scalability-and-reliability-plan.md` | Planned, partly in the template |
| Monitoring and logging | `07-monitoring-and-logging.md` | Logs and counters real, CloudWatch side in the template |
| CI/CD | `08-ci-cd-pipeline.md` | Workflows lint clean, never run on GitHub |
| Cost estimate | `09-cost-estimate.md`, `09-cost-estimate-computed-sheet.md`, `09-cost-estimate-script/` | Real prices, assumed usage, measured capacity |
| Operations, clean-up, free versus paid | `10-operations-clean-up-and-limits.md` | |
| Demo deployment record: address, credentials, measurements, problems | `11-demo-deployment-record.md` | Real, 2026-11-25. Includes HTTPS, S3 photos and a managed database |

The application runs on a public cloud (AWS Lightsail, Sydney) as a one-VM demo: `11-demo-deployment-record.md`. The designed stack (ECS, RDS, ALB, CloudFront) was tried and stopped at CloudFront account verification, so the documents above describe it as a design. Screenshots (budget, VM, logs) are to be added to the folders.

# Monitoring and logging

> Subject: Cloud Computing | Deliverable: basic logs and at least one health or usage metric to show in the demo | Data: the log format and counters are real (seen in the load tests). The CloudWatch side is defined in `navya/infra/main.yaml`.

## What the running demo shows (2026-11-25)

The demo VM has no CloudWatch. What exists and works there:

| What | How to show it |
|---|---|
| Health | `curl https://<host>/health` returns `{"status":"ok","storage":"s3"}` |
| Usage counters | Log in as the moderator, then `GET /api/admin/stats` with the token. Real output after the smoke runs: `{"requests":77,"serverErrors":0,"signups":6,"listingsCreated":3,"listingsExpired":0,"messagesSent":6,"boostsPaid":3,"paymentsFailed":3,"chatSessions":0,"noticesDropped":0}`. The counters reset when the app restarts. |
| Logs | `ssh ... 'cd navya && docker compose -f docker-compose.demo.yml logs --tail 50 app'` |
| Infrastructure graphs | The Lightsail console shows CPU, network and status-check graphs for the VM, and CPU and connections for the database (screenshots still to take) |
| Cost | The `navya-monthly` budget and the Billing page |

The CloudWatch logs, dashboard and alarms below are the design in `infra/main.yaml` and were not deployed.

## Logs

The app writes one line per event to standard output, and ECS sends it to CloudWatch Logs (`/navya/<env>/app`, kept 14 days). Format:

```
2026-11-24T19:00:36.419Z INFO  [navya-chat-listener] rid=- c.n.worker.ChatHub - Chat listener is running
2026-11-24T19:01:25.404Z INFO  [qtp747158012-28] rid=be276cef c.n.web.Api - request method=POST path=/api/conversations/<id>/messages status=201 ms=1 user=<member id>
```

Every request has a request id (`rid`, also returned as the `X-Request-Id` header), method, path, status, milliseconds and member id. An average request line is 165 bytes (measured), which is the figure the cost sheet uses. Server errors are logged with a stack trace at ERROR. Privileged actions are logged as `audit actor=... action=... target=...`. Passwords, tokens and message text are never logged.

## Health

`GET /health` answers `{"status":"ok","storage":"s3"}` with 200, or 503 `{"status":"down"}` if the database cannot be reached. The load balancer checks it every 15 seconds and ECS uses it to replace tasks.

## Metrics

| Source | Metrics |
|---|---|
| Built in | ALB request count, 5xx count, target response time, healthy host count. ECS CPU and memory. RDS CPU, connections, free storage. |
| Counters in the app (`Metrics`, `/api/admin/stats` for the team) | requests, serverErrors, signups, listingsCreated, listingsExpired, messagesSent, boostsPaid, paymentsFailed, chatSessions, noticesDropped |
| From the log with metric filters (namespace `Navya/<env>`) | `AppRequests` (lines with `request method=`), `AppErrors` (lines with `ERROR`), `BoostsPaid` (`action=boost.paid`), `SlowPayments` (`payment slow`) |

**Metric to show in the demo:** `BoostsPaid` and `AppRequests` per minute on the dashboard while the smoke test or a k6 run is going. Backup: `chatSessions` and `messagesSent` on the admin stats page.

## Dashboard `navya-<env>`

Six widgets: requests and 5xx; response time p95; app CPU and memory; database connections; database CPU; app events from the log.

## Alarms (email through SNS)

| Alarm | Fires when |
|---|---|
| Target 5xx | 5 or more server errors in 5 minutes |
| No healthy task | Fewer than 1 healthy target for 3 minutes (missing data counts as breaching) |
| Database storage | Less than 2 GB free |
| App errors | 5 or more `ERROR` log lines in 5 minutes |
| Budget (separate stack) | 50% and 80% of the monthly limit spent, or a forecast of over 100% |

Confirm the SNS email after the first deploy, or no alarm reaches anyone.

## Useful CloudWatch Logs Insights queries

```
fields @timestamp, @message | filter @message like /ERROR/ | sort @timestamp desc | limit 50
filter @message like /request method=/ | parse @message "path=* status=* ms=*" as path, status, ms | stats avg(ms), max(ms), count() by path | sort count() desc
filter @message like /action=boost/ | stats count() by bin(1h)
```

## Evidence for the submission

Dashboard during a k6 run, a log query result, an alarm firing and clearing (stop all tasks for 3 minutes), the budget page.

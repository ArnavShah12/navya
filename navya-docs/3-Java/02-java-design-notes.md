# Java design notes

> Subject: Java | Deliverable: Java design notes (how the brief's Java requirements are met) | Data: real, every claim points at code

## Requirements

| Requirement (brief section 4) | Where it is met |
|---|---|
| Well-structured classes and objects | One job per class. `domain/` holds data (records and `User`), `repo/` holds SQL, `service/` holds rules, `web/Api` holds routes, `worker/` holds background threads. Services never touch HTTP and `Api` never touches SQL. |
| Inheritance | `User` is abstract with `Member` and `Admin` (`role()` and `badge()` differ). `NavyaException` has 11 subclasses, each fixing an HTTP status and a code. |
| Interfaces | `PricingRule` (2 implementations), `PaymentGateway` (1 now, a real gateway later), `FileStorage` (local folder and S3). Each is a place where the implementation changes between local, test and cloud. |
| Custom exceptions | `exception/`. `Api.errors` turns any `NavyaException` into `{code, message}` with its status, so no handler repeats this. Unexpected errors become a generic 500 and are logged with a request id. |
| Multithreading, real tasks | Payment pool with timeout, reaper, notifier queue, chat listener, concurrent request handling over a shared connection pool. Details below. |
| Files | Local file storage, audit log, outbox log, CSV and JSON export, photo upload. Details below. |

## Multithreading

1. **Payments with a timeout** (`BoostService.boost`). The charge is submitted to an 8-thread pool and waited on for 3 seconds. After that the order stays pending and the caller gets 202. The gateway call is cancelled on our side only, the gateway may still approve, so `reconcile()` asks again. Tests: TC-46, TC-55.
2. **Reaper** (`AppContext.startBackground`). One scheduled thread runs every 30 seconds: `expireOld` marks listings older than 60 days, `reconcile` finishes slow payments. Both are single SQL statements that are safe to run on several instances at once. Test: TC-35.
3. **Notifier** (`worker/Notifier`). A bounded queue and one worker. Request threads call `send` and return at once. If the queue is full the notice is dropped and counted, so a slow disk cannot slow requests.
4. **Chat listener** (`worker/ChatHub`). One thread holds a PostgreSQL `LISTEN` connection. When any instance saves a message it runs `pg_notify`, every instance's listener wakes and pings its own WebSocket sessions. Test: TC-65 runs two instances on one database.
5. **Concurrency control in the database, not in Java locks.** Unique constraints and conditional updates decide races: two sign-ups with one email (TC-20), 40 simultaneous chat starts (TC-42), 10 identical boost requests (TC-50). The app has no `synchronized` on shared business state, so it behaves the same on one instance or four.

## Files

| Use | Class | Notes |
|---|---|---|
| Photos | `LocalFileStorage`, `S3FileStorage` | Keys are `<member id>/<uuid>.jpg`. A key cannot contain `..` or start with `/`. |
| Audit log | `AuditLog` | Append-only `audit.log`: time, actor, action, target. Tabs and newlines in values are replaced. Also written to the application log. |
| Outbox | `Notifier` | Append-only `outbox.log` for notices. |
| Export | `ExportService` | CSV (spreadsheet formulas defused) and JSON written to storage under `exports/`. |
| Seed | `Seeder` | Writes generated demo photos through the normal upload path. |

In the full ECS design the container's own disk is temporary, so `audit.log` and `outbox.log` are lost when a task stops, and the same lines go to CloudWatch Logs. On the demo VM they sit in the Docker volume `navya_appdata` and in `docker compose logs`. Exports go to S3.

## Choices worth explaining in the viva

- **No ORM.** Plain JDBC with parameter binding in `Db`. Each repository is short and the SQL is visible.
- **Money in paise as `long`.** No floating point. The UI shows rupees.
- **Chat strangers get 404, not 403.** A 403 would confirm that a chat id exists.
- **Search is `ILIKE` on a joined string.** Simple and correct for thousands of listings (measured, see `../4-Software-Testing/07-non-functional-testing/performance-testing.md`). A full-text or trigram index is the next step.
- **Image upload goes through the app.** The app checks size and the JPEG file signature and picks the key. A direct-to-S3 upload would save bandwidth on the app but needs presigned URLs and CORS rules, and then the server would not see the bytes.
- **Login token in `localStorage`.** Simple, and the strict CSP blocks injected script, but a cookie with `HttpOnly` would resist script theft better. Listed as a trade-off.
- **Schema migration at start-up.** `schema.sql` is idempotent and runs under an advisory lock. The app's database user therefore has DDL rights, which a stricter set-up would split from the runtime user.

## Known limits

Rate limits are per instance and keyed on the first `X-Forwarded-For` address, which a client can set. The mock gateway forgets results on restart. The chat listener takes one pool connection for good and does not work through a pooler in transaction mode (such as RDS Proxy).

# Security testing

> Subject: Software Testing | Deliverable: basic security check (input validation and common vulnerabilities) | Data: real. Results come from the automated tests named in each row. No external scanner was run (see "Not done").

## Checks and results

| Area | Check | Test | Result |
|---|---|---|---|
| Injection | Search text `'; DROP TABLE listings; --`, `%`, `_` | TC-31, TC-60 | Treated as text, no error, table intact. All SQL uses bound parameters (`Db`), `%` and `_` are escaped for `LIKE`. |
| Injection | Malformed ids in URLs (`1 OR 1=1`, `nope`) | TC-37, TC-60, TC-69 | 404. Ids are checked as UUIDs before use. |
| XSS | `<script>` in a title; `<b>` in a chat message; `<img onerror>` in the search box | TC-60, browser test | Stored as text. The page escapes every value (`esc()`), the chat check proves `<b>` is shown as text. Console stays free of errors. |
| XSS | Content-Security-Policy | TC-61 | `script-src 'self'`, `style-src 'self'`, no `unsafe-inline`. The page uses no inline script or style (the card background is set from script). |
| Clickjacking, sniffing | `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy: no-referrer` | TC-61 | Present on the home page and API (found missing on static files, DEF-003, fixed). |
| Authentication | No token, garbage token, forged ADMIN payload, `alg=none` token, expired token | TC-18, TC-56 | 401. |
| Authentication | Same error for wrong password and unknown email | TC-16 | Same message. |
| Authentication | Brute force | TC-62 | 429 after 5 tries per address and email. |
| Passwords | Stored as bcrypt | TC-14 | Hash starts with `$2`, not the password. |
| Authorisation | Edit, delete, status, boost on another member's listing | TC-27 to TC-29, TC-47, TC-57 | 403. |
| Authorisation | Admin routes as a member | TC-52, TC-58 | 403. |
| IDOR | Another member's chat by id | TC-39, TC-59 | 404 (not 403), so ids reveal nothing. |
| Data exposure | Seller profile | TC-69 | No email in the response. |
| File upload | Text sent as a photo, 600 KB+ file, path in the key, someone else's key on a listing | TC-25, TC-26, TC-63, TC-12 | Refused. Only a JPEG signature up to 600 KB is stored, under the member's own folder. The browser also re-encodes through a canvas. |
| CSV injection | Title `=HYPERLINK("http://evil")` in the export | TC-53 | Cell starts with `'`. |
| Business logic | Double click on pay; replay of a request key; paying for someone else's listing | TC-45, TC-47, TC-50 | One charge, same order returned, refused. |
| Denial of service | Large bodies; request flood | TC-63, load tests | Body limit 700 KB. The thread pool is bounded (64 threads, 500 queued) so overload is refused, not fatal. Rate limits on writes. |
| Secrets | Hard-coded keys in the repo | gitleaks in CI (not run here), manual search | The old Supabase URL and key were removed with the original `script.js`. `grep -ri "secret\|password\|key"` finds only variable names. `.env` files are ignored by git and docker. |
| Privacy | Sign-up without the 18+ and terms confirmation | TC-70, TC-71, browser test | 400 for a missing, false or non-boolean `consent`, no account created. With consent, the time and terms version are stored. The form's box is required. |
| Privacy | Legal pages public and linked | TC-72 | `/terms.html`, `/privacy.html`, `/grievance.html` answer 200 with the version, name the officer, and the home page links to all three. |
| Dependency scan | trivy 0.76.2 on `pom.xml`, 2026-11-25, HIGH and CRITICAL, no ignore file | trivy | 2 findings, both in Jetty 11.0.25 (inside Javalin 6.7): CVE-2026-2332 in jetty-http and CVE-2026-10050 in jetty-security. Fixed versions exist (11.0.29, 11.0.31) but are not on Maven Central, which stops at 11.0.26, so a pin fails to build. Accepted in `.trivyignore`. The fix is Javalin 7 (Jetty 12). |
| Secret scan | trivy 0.76.2 on the project folder, 2026-11-25 | trivy | One finding: the SSH key `infra/demo-key.pem` for the demo VM. It is not in git (ignored, 0 tracked files) and is deleted by `lightsail-demo.sh down`. No other secret. |
| Misconfiguration scan | trivy 0.76.2 on `Dockerfile`, the three compose files and `infra/*.yaml` | trivy | Dockerfile and compose files: no findings. `infra/budget.yaml`: none. `infra/main.yaml` (the ECS design, never deployed): 21 findings, 1 CRITICAL and 7 HIGH. They are trade-offs already stated in the security documents: plain HTTP from CloudFront to the load balancer, no WAF, the default CloudFront TLS floor, public load balancer behind CloudFront, mutable ECR tags, unencrypted SNS topic, S3 with SSE-S3 not a customer key, invalid headers not dropped. Not fixed because the stack did not deploy. |
| Transport | HTTPS | smoke test (`load/smoke.mjs`) on the demo, 2026-11-25, plus a manual check of the certificate and headers | HTTP redirects to HTTPS (308). Let's Encrypt certificate valid to 2027-02-23. `Strict-Transport-Security`, CSP and `X-Content-Type-Options` present on the live site. Not tested: TLS version and cipher scan, certificate renewal. |

## Not done

- On the demo VM: SSH (port 22) is open to the internet, protected by the key only. The managed database is private and its TLS connection does not verify the server certificate. The S3 access key sits in a file on the VM. None of this was tested beyond reading the configuration.

- No OWASP ZAP or other web scanner run. trivy scanned the dependencies, the project files and the infrastructure files (above), but not the built container image: the image exists only on the demo VM and this machine has no Docker access. CI is set up to scan the image. gitleaks is not installed here, so the git history was checked with trivy and a pattern search, not gitleaks.
- The two HIGH Jetty 11 vulnerabilities are accepted in `.trivyignore`, re-verified with trivy on 2026-11-25 (see the dependency row above).
- No test of CSRF: the API uses a bearer token in a header, not cookies, so classic CSRF does not apply.
- The token lives in `localStorage`. The strict CSP lowers the risk of script theft, but an `HttpOnly` cookie would be safer.
- Rate limits trust the first `X-Forwarded-For` value and are per instance (OI-6).
- No penetration test by anyone outside the team.

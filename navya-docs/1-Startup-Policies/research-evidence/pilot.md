# Simulated pilot

> Subject: Startup Policies | Deliverable: evidence from a pilot on the running app | Data: **members and their choices are simulated** (random, fixed seed 20261125). The server, its answers and the numbers below are real. Script: `navya/load/simulated-pilot.mjs`. Raw output: `pilot-results.json`.

## What it did

Against a fresh Navya with demo data, 40 invented members signed up, listed items, browsed, started chats, replied, marked items sold, bought boosts with a mix of test cards, and reported listings. Run with `E2E_SCRIPT=../load/simulated-pilot.mjs e2e/run.sh`.

## Result

| Measure | Value |
|---|---|
| Members signed up | 40 of 40 |
| Listings posted | 46 |
| Chats started | 29 |
| Messages sent | 49 |
| Listings marked sold | 7 |
| Boosts paid, declined, pending | 3, 1, 0 (after settling: 3 paid, 1 failed) |
| Reports filed | 4 |
| Server errors (HTTP 5xx) | 0 |
| Responses by status | 200: 159, 201: 139, 402: 1, 409: 9 |

The nine 409 answers are the app refusing chats and boosts on listings that were already sold: the rule working, not a fault.

## What it showed about the system

- The per-address sign-up limit (10 a minute) is bypassed by a client that sets a different `X-Forwarded-For` for each request. The pilot needed that to sign up 40 members from one machine. This confirms open issue OI-6: the limit must key on the address the load balancer saw, not the header the client sent, before launch.
- Everything else behaved as the tests expect under a mixed flow of about 300 requests.

## What it does not show

Anything about real behaviour. Chat rates, sale rates and boost uptake are set by the script's probabilities, not measured. A real pilot at one campus replaces this.

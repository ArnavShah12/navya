# Funding plan

> Subject: Startup Policies | Deliverable: funding plan | Data: cloud prices are real list prices from 2026-11-25, everything else is an assumption

## What the money is for

| Item | Amount | Source of the number |
|---|---|---|
| Cloud, always on, 2 tasks, 100 to 1,000 members | about USD 95 a month | `../5-Cloud-Computing/09-cost-estimate-computed-sheet.md` |
| Cloud, 10,000 members, database Multi-AZ | about USD 158 a month | same |
| Cloud, only during reviews and demos (40 hours a month) | about USD 9 a month | same |
| Cloud, the demo that actually runs (one Lightsail VM at USD 7, one Lightsail PostgreSQL at USD 15, S3) | about USD 22 a month | Lightsail API list prices, 2026-11-25. `../5-Cloud-Computing/11-demo-deployment-record.md` |
| Payment gateway fee when live | Razorpay standard: 2% plus 18% GST on the fee, about 2.36% in all. On a Rs 49 boost that is about Rs 1.16 (Rs 0.98 plus Rs 0.18 GST), so Navya keeps about Rs 47.8. Not a quote for Navya: no account was opened | Razorpay pricing page and blog, read 2026-11-25 (`../research-evidence/real-world-benchmarks.md`) |
| Domain name | to be priced | to get from a registrar |
| Marketing, three-month one-campus pilot | Rs 3,000 (about USD 32 at Rs 92.93): posters for two colleges Rs 1,000, perks for 10 campus ambassadors at Rs 150 each Rs 1,500, about 10 referral boosts at Rs 49 each Rs 490 | Assumption, sized from the channels in `../2-E-Commerce/05-digital-marketing-plan.md`. Instagram, WhatsApp groups and search are free |

## Plan

1. **Now to Review 2:** use the AWS credit on the account (USD 100 and a payment method, as stated by the account owner; the balance and expiry date were not read from the Billing console, see `../5-Cloud-Computing/10-operations-clean-up-and-limits.md`). The demo that runs costs about USD 22 a month, so the credit covers it for several months if nothing else is added. Run the larger design in demo windows only. No personal money beyond a budget alert.
2. **Pilot (one campus):** run one task during the day. Expect about USD 55 to 75 a month (one task instead of two saves USD 22.58, see cost sheet). Pay from a college grant or the team.
3. **Revenue needed to cover the always-on cost at 1,000 members:** USD 95.92 a month is about 182 boosts a month, about 6 a day, at Rs 49 with 1 USD = Rs 92.93 (open.er-api.com, read 2026-11-25; it changes daily). The demo that actually runs costs about USD 22 a month, about 42 boosts a month at the same rate. The survey (simulated) says 13 of 60 would pay and 20 more might.
4. **Possible outside money:** college incubator support, Startup India Seed Fund (needs DPIIT recognition, which needs a registered company, see `05-dpiit-recognition-readiness.md`), cloud credits for start-ups.

## Break-even sketch

Cost per month at 1,000 members: USD 95.92. Each boost brings Rs 49 before gateway fees and tax. Break-even boosts per month = cost in rupees divided by Rs 49. With USD 1 = Rs 92.93: 95.92 x 92.93 / 49 = about 182 boosts a month. This is arithmetic, not a forecast.

## Risks

Boost demand is unproven. The load balancer and IPv4 addresses cost the same at 100 and at 1,000 members, so the first members are the expensive ones.

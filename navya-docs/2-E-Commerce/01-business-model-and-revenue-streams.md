# Business model and revenue streams

> Subject: E-Commerce | Deliverable: business model and revenue streams | Data: the fee level is an assumption to test.

## Revenue now (built)

**Boost.** A seller pays Rs 49 (`BOOST_FEE_PAISE=4900`) to pin an available listing to the top of the default home order for 7 days (`BOOST_DAYS`). A second boost extends from the end of the first. On Saturday and Sunday (Indian time) the price is 20% lower, rounded to a whole rupee, so Rs 39. The price comes from `PricingService` and its rules, so a new offer is one more `PricingRule`.

Why this model: sellers who want a quick sale for a higher-priced item have a reason to pay. Buyers see more listings, not fewer. No payout or delivery is needed.

## Revenue ideas, not built

| Idea | Needs | Risk |
|---|---|---|
| Verified-seller badge, yearly fee | Identity check, support | Cost of checking exceeds fee |
| Local brand ads on the home page | Enough traffic | Looks like clutter, hurts trust |
| Commission on in-app payment | Licensed gateway, escrow, delivery, disputes | Large scope, legal load |
| Campus partnership fees | Pilot success | Slow sales cycle |

## Unit economics, sketch

Using `../5-Cloud-Computing/09-cost-estimate-computed-sheet.md`: always-on cost at 1,000 members is about USD 96 a month, at 100 members the same. Revenue per boost is Rs 49 (Rs 39 at weekends) before gateway fee and GST. The business needs about six boosts a day (182 a month at Rs 49 and Rs 92.93 to the dollar) before it covers always-on hosting, or it runs only in demo windows at about USD 9 a month. Figures depend on member numbers nobody has measured yet.

# Usability results

> Subject: Software Testing | Deliverable: usability check with users | Data: **SIMULATED** (same generator, seed 2026).

Method: each participant did 5 tasks on a phone-sized screen, thinking aloud. Time in seconds, finished or not, and the number of wrong taps or dead ends.

| Task | Finished | Median time (s) | Wrong taps (total) |
|---|---|---|---|
| Find a denim jacket under Rs 1,000 | 5/5 | 34 | 3 |
| Sign up and post a listing with a photo | 5/5 | 159 | 0 |
| Message a seller | 5/5 | 30 | 1 |
| Boost a listing (test card) | 3/5 | 44 | 3 |
| Mark your own listing as sold | 5/5 | 18 | 1 |

Overall task completion: 23/25 (92%).

System Usability Scale per participant: 80.0, 77.5, 72.5, 75.0, 82.5. Mean 77.5. The published average is about 68 (see `real-world-benchmarks.md`). With 5 people the interval is wide, so this shows nothing about real users.

Findings written before the data existed, to be confirmed or removed after a real session (they come from our own use of the app):

- The 'Featured' badge sat on top of the save heart on the card. Fixed (DEF-004).
- The list shows the previous results for a moment while the new ones load. Not fixed, listed as an open issue.
- The sign-up form asks for a city. It is used to fill the city on listings, so it stays.

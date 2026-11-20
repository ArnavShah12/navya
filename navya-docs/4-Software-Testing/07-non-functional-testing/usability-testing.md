# Usability testing

> Subject: Software Testing | Deliverable: short usability check with users | Data: the session results are **simulated** (generated, see `../../1-Startup-Policies/research-evidence/README.md`). The method, tasks, and the team's own checks are real.

## Method

5 participants, each on a phone-sized screen, thinking aloud, on the running site. Five tasks. For each task: finished or not, time in seconds, wrong taps or dead ends. Afterwards the 10-question System Usability Scale (SUS, scored 0 to 100, published average about 68).

| # | Task |
|---|---|
| 1 | Find a denim jacket under Rs 1,000 |
| 2 | Sign up and post a listing with a photo |
| 3 | Message a seller |
| 4 | Boost your listing with the test card |
| 5 | Mark your listing as sold |

Consent: participants are told it is a student project, no real money moves, notes are anonymous, and they may stop at any time. No names are stored.

Pass mark: every participant finishes tasks 1, 3 and 5, at least 4 of 5 finish tasks 2 and 4, and mean SUS is at least 68.

## Results (simulated)

Full table and per-participant SUS: `../../1-Startup-Policies/research-evidence/usability-results.md`. Summary: 23 of 25 tasks finished, the boost task was the weakest (3 of 5), mean SUS 77.5. With 5 simulated participants these figures show the shape of a report, not how real people behave.

## Checks the team did on the real build

- The browser test runs the main flows at 1280 px and checks that a 390 px wide page does not scroll sideways (`navya/e2e/ui-smoke.mjs`). Screenshots: `navya/e2e/shots/`.
- A screenshot review found the Featured badge covering the save heart. Fixed (DEF-004).
- Forms have labels, errors appear next to the form, the focus outline is visible.
- The home page keeps the previous results on screen for a moment while a new search loads (open issue OI-4).

## Not done

Colour-contrast measurement, screen-reader test, Safari and Firefox, a slow-network test, and a session with real participants.

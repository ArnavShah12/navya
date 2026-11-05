# Research evidence

**The interview, survey, usability and pilot-behaviour data in this folder is simulated.** It was generated for the course, with the mentor's approval on 2026-11-24, the same way as in the FestPass project. It is not a measurement of real demand. Each file says at the top whether it is simulated or real.

| File | What it is | Real or simulated |
|---|---|---|
| `real-world-benchmarks.md` | Published market, rule and price figures with sources | Real |
| `research-instruments-and-results.md` | Interview guide and survey questions | Real |
| `survey-responses.csv`, `survey-summary.md` | 60 respondents | Simulated |
| `interviews.md` | 6 short interviews | Simulated |
| `usability-results.md` | 5 participants, 5 tasks, SUS | Simulated |
| `pilot.md`, `pilot-results.json` | 40 invented members on the real app | Behaviour simulated, system numbers real |

Generator for the survey and usability files: `navya/tools/simulate_survey_and_usability.py` (fixed seed 2026). Load test output is in `../../4-Software-Testing/07-non-functional-testing/results/`.

# Topic proposal and startup brief

> Subject: Startup Policies | Deliverable: approved one-page proposal | Data: market figures are published estimates with sources, user figures are simulated (see `research-evidence/`) | Team: Arnav Shah, Dipanshu Parmar | College: JG University | Mentor: Deep Paun | Mentor approval: 2026-11-24

## Problem

Students and young professionals in Indian cities own clothes, shoes and bags they no longer wear. They sell them on OLX, Instagram or WhatsApp groups. The people we asked (simulated survey, 60 respondents) named three problems: nobody makes an offer, strangers get their phone number, and listings are hard to find by city.

## Solution

Navya is a classifieds site for pre-loved clothes, shoes, bags and accessories. A seller posts photos, a price and a city. A buyer searches, saves items and chats with the seller inside the site, so no phone number is shared. Members can report a listing, and the Navya team takes it down. Payment and handover happen in person.

## Market

India has 4.33 crore students in 45,473 colleges (AISHE 2021-22). Published estimates put India's second-hand apparel market at roughly USD 3 to 3.5 billion and growing about 13% a year, but the vendors define it differently and disagree (`research-evidence/real-world-benchmarks.md`). We treat this as "large and growing", not as a number to put on a slide without its source.

## Users

Students and early-career members aged 18 to 26 in Indian cities. First target: JG University and the three neighbouring colleges.

## How it earns

Sellers pay to boost a listing. A boost pins the listing to the top of the home page for 7 days. Price: Rs 49, 20% off on Saturday and Sunday. Payments run through a test-mode gateway in this project. Other revenue ideas are in `../2-E-Commerce/01-business-model-and-revenue-streams.md`.

## Why the cloud

- Traffic is uneven: weekend evenings and the start of a semester. Containers on a managed platform can add capacity for the peak and drop it after.
- Many colleges reach one URL with no installs.
- Photos need durable storage, and the database needs backups.

## What exists now

A working application (Java 21, PostgreSQL, S3) with sign-up, listings with photos, search and filters, saved items, live chat, reports and moderation, and paid boosts in test mode. It is containerised, has infrastructure code for AWS, and runs as a demo on AWS (one Lightsail VM with a managed PostgreSQL and S3, in Sydney, HTTPS): `../5-Cloud-Computing/11-demo-deployment-record.md`. See `../3-Java/` and `../5-Cloud-Computing/`. A simulated pilot on the running app is in `research-evidence/pilot.md`.

## What is not done

No real payment gateway, no email or SMS, no mobile app, no identity checks on sellers.

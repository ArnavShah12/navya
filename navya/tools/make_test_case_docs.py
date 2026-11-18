#!/usr/bin/env python3
"""Builds 02-designed-test-cases.csv and .md. The design columns (technique, input, expected) are written here by hand.
The actual result and status come from the JUnit XML that `./mvnw verify` leaves in target/surefire-reports, so they are real.
Run after `./mvnw verify` in navya/:  python3 tools/make_test_case_docs.py (from the repository root: python3 navya/tools/make_test_case_docs.py)
"""
import csv, glob, re, xml.etree.ElementTree as ET, datetime, pathlib

ROOT = pathlib.Path(__file__).resolve().parents[2]
CODE = ROOT / "navya"
DOCS = ROOT / "navya-docs" / "4-Software-Testing"

CASES = {  # id: (technique, input, expected)
 1: ("EP valid", "a@b.co; '  Mixed.Case@Example.COM '; x+tag@sub.domain.in", "Accepted, trimmed and lower-cased"),
 2: ("EP invalid", "'', '   ', no-at-sign, a@b, 'a b@c.com', @x.com", "InvalidInputException for each"),
 3: ("BVA", "Password of 7, 8, 64, 65 characters and null", "7 rejected, 8 accepted, 64 accepted, 65 rejected, null rejected"),
 4: ("BVA", "Title of 0, 1, 80, 81 characters", "0 rejected, 1 and 80 accepted, 81 rejected"),
 5: ("BVA", "Price -1, 0, 10,000,000, 10,000,001 rupees", "-1 rejected, 0 and 10,000,000 accepted (as paise), 10,000,001 rejected"),
 6: ("EP", "Category ' shoes ', condition 'LIKE NEW'; category 'Cars', condition null", "First two parsed, last two rejected"),
 7: ("Decision table", "Boost price on a Wednesday", "4,900 paise (full fee)"),
 8: ("Decision table", "Boost price on Saturday, Friday 20:00 UTC (Saturday in India), Sunday 20:00 UTC (Monday in India)", "3,900, 3,900, 4,900 paise (20% off then rounded to a whole rupee)"),
 9: ("Decision table", "Gateway tokens tok_success, tok_declined, tok_timeout, unknown, null", "Success, declined, pending, declined, declined"),
 10: ("BVA", "Rate limiter with burst 3: four calls, another key, a call 2 s later", "First 3 allowed, 4th refused, other key allowed, refilled after 2 s"),
 11: ("Positive path", "put, get, exists, delete, delete again on local storage", "Round trip works, missing delete is not an error"),
 12: ("Invalid input", "Storage keys '../secret.txt' and '../../evil'", "InvalidInputException, nothing outside the folder is touched"),
 13: ("EP invalid", "Missing JWT secret, missing DB_URL, wrong DB_URL scheme, STORAGE=s3 without bucket, PORT=abc; valid dev config", "Each bad config refused with a message naming the setting; valid one starts"),
 14: ("Positive path", "Sign up 'Aanya@Example.com', then log in, then verify the token", "Member created with lower-case email and bcrypt hash, token identifies the member"),
 15: ("Invalid input", "Sign up a@example.com, then A@EXAMPLE.COM", "DuplicateEmailException on the second"),
 16: ("Security", "Wrong password for a real email; unknown email", "Same exception and same message for both"),
 17: ("EP invalid", "Password 'short'; email 'not-an-email'; name ' '", "InvalidInputException for each"),
 18: ("Security", "Payload swapped to role ADMIN with old signature; 'garbage'; null; valid token; valid token 13 h later", "First three and the expired one refused, valid one accepted"),
 19: ("Positive path", "ensureAdmin twice, then log in", "One admin, repeat is harmless, login role is ADMIN"),
 20: ("Concurrency", "12 threads sign up the same email at once", "Exactly 1 succeeds, 11 get DuplicateEmail, no other error"),
 21: ("Positive path", "Create 'Denim jacket' at Rs 900 and read it back", "Stored as 90,000 paise, status available, readable without login"),
 22: ("EP invalid", "Empty title, 81-char title, price -1, null price, category 'Cars', condition 'Broken', empty city, null body", "InvalidInputException for each"),
 23: ("BVA", "Price 0 and an 80-character title", "Accepted"),
 24: ("BVA", "4 photos, then 5 photos", "4 accepted, 5 raise TooManyImagesException"),
 25: ("EP", "A JPEG; an HTML text; empty bytes; 600,001-byte JPEG header", "Only the JPEG is stored, under the member's own folder"),
 26: ("Security", "Anna attaches Ben's photo key to her listing", "InvalidInputException"),
 27: ("Authorisation", "Ben edits Anna's listing; Anna edits her own and drops a photo", "Ben gets ForbiddenException; Anna's edit applies and the dropped photo is deleted from storage"),
 28: ("Authorisation", "Ben marks Anna's listing sold; Anna marks sold, available, then 'removed'", "Ben refused; sold and available work; 'removed' is refused"),
 29: ("Authorisation", "Ben deletes Anna's listing; Anna deletes it", "Ben refused; Anna's delete removes the row and the photo file"),
 30: ("EP", "Search 'denim', 'DENIM jacket', 'mumbai', 'nothing-like-this'", "Every word must match in any field, case-insensitive"),
 31: ("Security", "Search '%', '_', \"'; DROP TABLE listings; --\"", "Treated as plain text: 0 results, table still exists"),
 32: ("BVA", "Category Shoes; city 'pun'; price 100 to 500; price 501 to 899", "Inclusive bounds: A and B at 100 and 500; none between 501 and 899"),
 33: ("Positive path", "30 listings: sort low, high, default, page 2", "Cheapest first, dearest first, 24 per page, page 2 has 6"),
 34: ("Positive path", "Save, list saved, filter saved, filter saved without login, unsave", "Works for a member, ForbiddenException without login"),
 35: ("State", "Listing created, clock moved 61 days, expireOld(60) twice", "Expired once, second run changes nothing, hidden from others, still visible to seller, not editable"),
 36: ("Positive path", "Ben starts a chat on Anna's listing twice; Cara once", "Same chat for Ben both times, different chat for Cara"),
 37: ("Invalid input", "Anna chats on her own listing; chat on a sold listing; random id; malformed id", "SelfChat, ListingNotAvailable, NotFound, NotFound"),
 38: ("Positive path", "Three messages, then messages after the first id", "Arrive in order for both people, 'after' returns only newer ones"),
 39: ("Security", "Cara reads and writes Ben and Anna's chat", "NotFoundException (not Forbidden), her inbox stays empty"),
 40: ("BVA", "Message of 0 and 3 spaces, 2000 and 2001 characters", "Empty and blank rejected, 2000 accepted, 2001 rejected"),
 41: ("Positive path", "Two chats with one message each", "Inbox newest first, shows other person's name and last message"),
 42: ("Concurrency", "40 threads press 'Message seller' for the same buyer and listing", "No error, exactly 1 conversation"),
 43: ("Decision table", "Boost with tok_success on a Wednesday", "Paid, 4,900 paise, listing featured for 7 days then not"),
 44: ("Decision table", "Boost with tok_declined, then tok_success with a new key", "PaymentFailedException, order failed, nothing pinned; the retry succeeds"),
 45: ("Idempotency", "Same key twice", "Same order returned, one order, boost extended once"),
 46: ("Decision table", "tok_timeout, reconcile before and after the bank settles", "Pending, then paid after settle, reconcile twice does not boost twice"),
 47: ("Authorisation", "Ben boosts Anna's listing; blank key; boost a sold listing", "Forbidden, InvalidInput, ListingNotAvailable"),
 48: ("State", "Boost on day 0, again on day 3, check on day 9 and 15", "Still featured on day 9, not on day 15"),
 49: ("State", "Boosted older listing vs newer unboosted", "Boosted comes first in the default order"),
 50: ("Concurrency", "10 identical boost requests at once", "All answered, one order, one boost"),
 51: ("EP", "Report twice from one member; report own listing; blank reason", "One report stored, own refused, blank refused"),
 52: ("Authorisation", "Member opens reports; admin removes one and dismisses another", "Member refused; remove hides listing from search but not from its seller; dismiss keeps it; audit line written"),
 53: ("Security", "Listing titled =HYPERLINK(\"http://evil\") exported", "CSV cell starts with an apostrophe; CSV and JSON files written to storage"),
 54: ("System flow", "Sign up, post, search, save, chat, report, boost, order history over HTTP", "Every step answers with the expected status and data"),
 55: ("System flow", "Boost with declined card, then slow card, then wait and reconcile", "402, then 202 pending, then order becomes paid"),
 56: ("Security", "No token, 'not.a.token', unsigned alg=none token with ADMIN claim", "401 for each"),
 57: ("Authorisation", "Ben edits, deletes, marks sold, boosts Anna's listing over HTTP", "403 for each, listing unchanged"),
 58: ("Authorisation", "Member and admin call admin routes", "403 for member on 5 routes, 200 for admin, CSV content type"),
 59: ("Security", "Cara asks for Ben and Anna's chat, and for a random id", "404 for each"),
 60: ("Security", "SQL text in search, min=abc, bad id, bad JSON, '[]', empty body, <script> title", "200, 400, 404, 400, 400, 400, stored as text; zero 500 errors"),
 61: ("Security", "Headers on the home page and on an API response", "nosniff, DENY, CSP without unsafe-inline, no-store on the API"),
 62: ("Security", "8 wrong logins in a row", "429 once the limit of 5 is passed"),
 63: ("EP", "Upload JPEG, fetch it; path traversal; text body; no token; 800 KB body", "201 and served with immutable cache header; traversal 400/404; text 400; 401; 400 or 413"),
 64: ("Positive path", "GET /health", "200 and status ok"),
 65: ("System flow", "Message sent through instance A while a browser socket is open on instance B", "Instance B pushes a 'new' ping within 5 s (PostgreSQL NOTIFY between instances)"),
 66: ("Security", "Socket opened with a garbage token, and with a stranger's token", "Never gets 'ready', closed, no session kept"),
 68: ("State", "A pending order the gateway has no record of; reconcile at 5 and at 11 minutes", "Left alone at 5 minutes, failed at 11, nothing pinned"),
 69: ("Positive path", "Seller profile, quote, me, own listings, status, admin export, audit, stats, admin delete, bad seller ids", "Correct data, profile has no email, bad ids 404, admin can delete and export"),
 70: ("Invalid input", "Sign-up body with consent missing, false, and the string 'yes'", "400 each and no account created; the same email then signs up with consent"),
 71: ("Positive path", "Sign up with consent, then read the account", "Consent time equals the sign-up time and the terms version is the current one"),
 72: ("Positive path", "GET /terms.html, /privacy.html, /grievance.html and the home page", "Pages are public (200) and carry the version; the grievance page names the officer with a mailto link; the home page links to all three"),
 67: ("Positive path", "S3 storage against MinIO: put, get, exists, delete, bad keys", "Works. Runs only when S3_TEST_ENDPOINT is set (CI)"),
}

# surefire writes method names, so map "TC-nn" to (class, method) by reading the @DisplayName lines in the test sources
ids = {}
descs = {}  # TC number -> the test's own @DisplayName text without the "TC-nn" prefix
for src in glob.glob(str(CODE / "src/test/java/com/navya/*.java")):
    text = open(src).read()
    cls = src.split("/")[-1][:-5]
    for m in re.finditer(r'@DisplayName\("TC-(\d+)\s*([^"]*)"\)\s*(?:@\w+\s*)*void (\w+)\(', text):
        ids[(cls, m.group(3))] = int(m.group(1))
        descs.setdefault(int(m.group(1)), m.group(2).strip())

results = {}
for f in glob.glob(str(CODE / "target/surefire-reports/TEST-*.xml")):
    for tc in ET.parse(f).getroot().iter("testcase"):
        n = ids.get((tc.get("classname").split(".")[-1], re.sub(r"[(\[].*$", "", tc.get("name"))))  # parameterised tests are named method(Type)[1]
        if n is None:
            continue
        status = "Fail" if (tc.find("failure") is not None or tc.find("error") is not None) else ("Skipped" if tc.find("skipped") is not None else "Pass")
        old = results.get(n)
        if old and old[0] == "Fail":
            continue  # a case with several runs fails if any run fails
        results[n] = (status, tc.get("classname").split(".")[-1], float(tc.get("time", 0)))

today = datetime.date.today().isoformat()
rows = []
for n in sorted(CASES):
    tech, inp, exp = CASES[n]
    status, cls, t = results.get(n, ("Not run", "", 0))
    actual = {"Pass": "As expected", "Fail": "Did not match, see test report", "Skipped": "Not executed here (needs MinIO, runs in CI)", "Not run": "No result found"}[status]
    rows.append({"ID": f"TC-{n:02d}", "Description": descs.get(n, ""), "Technique": tech, "Input": inp, "Expected": exp, "Actual": actual, "Status": status, "Automated test": cls, "Date": today})
with open(DOCS / "02-designed-test-cases.csv", "w", newline="") as f:
    w = csv.DictWriter(f, fieldnames=list(rows[0])); w.writeheader(); w.writerows(rows)

counts = {k: sum(1 for r in rows if r["Status"] == k) for k in ("Pass", "Fail", "Skipped", "Not run")}
md = ["# Designed test cases", "",
      "> Subject: Software Testing | Deliverable: at least 30 designed test cases | Data: real (results come from `./mvnw verify`, JUnit XML) | Generated by `navya/tools/make_test_case_docs.py`", "",
      f"{len(rows)} cases, designed with equivalence partitioning (EP), boundary value analysis (BVA) and decision tables, plus security, authorisation, concurrency, state and system-flow cases. Valid and invalid inputs are both covered. Each case is one automated JUnit test, named in the last column. Run on {today}.", "",
      f"Result: {counts['Pass']} pass, {counts['Fail']} fail, {counts['Skipped']} skipped, {counts['Not run']} not run.", "",
      "| ID | Description | Technique | Input | Expected | Actual | Status | Test class |", "|---|---|---|---|---|---|---|---|"]
for r in rows:
    md.append("| " + " | ".join(r[k].replace("|", "/") for k in ("ID", "Description", "Technique", "Input", "Expected", "Actual", "Status", "Automated test")) + " |")
md += ["", "The decision tables behind TC-07 to TC-09, TC-43 to TC-47 are in `03-decision-tables.md`. TC-67 is skipped on a laptop without an S3-compatible store and runs in the CI pipeline against MinIO.", ""]
open(DOCS / "02-designed-test-cases.md", "w").write("\n".join(md))
print(counts)

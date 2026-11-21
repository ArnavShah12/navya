#!/usr/bin/env python3
"""Builds the three legal pages the app serves from the Markdown documents in navya-docs/2-E-Commerce/legal/.
The Markdown is the single source. Run from the repository root:  python3 navya/tools/make_legal_pages.py
Handles only what those documents use: # and ## headings, paragraphs, '- ' lists, **bold** and email addresses."""
import html, pathlib, re

ROOT = pathlib.Path(__file__).resolve().parents[2]
SRC = ROOT / "navya-docs" / "2-E-Commerce" / "legal"
OUT = ROOT / "navya" / "src" / "main" / "resources" / "public"
PAGES = {"terms-of-use.md": "terms.html", "privacy-notice.md": "privacy.html", "grievance-redressal-policy.md": "grievance.html"}

def inline(text):
    t = html.escape(text, quote=False)
    t = re.sub(r"\*\*(.+?)\*\*", r"<strong>\1</strong>", t)
    return re.sub(r"([\w.+-]+@[\w-]+\.[\w.]+)", r'<a href="mailto:\1">\1</a>', t)

def convert(md):
    out, items = [], []
    def flush():
        if items:
            out.append("<ul>" + "".join(f"<li>{inline(i)}</li>" for i in items) + "</ul>"); items.clear()
    for line in md.splitlines():
        if line.startswith("- "):
            items.append(line[2:]); continue
        flush()
        if line.startswith("## "): out.append(f"<h2>{inline(line[3:])}</h2>")
        elif line.startswith("# "): out.append(f"<h1>{inline(line[2:])}</h1>")
        elif line.strip(): out.append(f"<p>{inline(line)}</p>")
    flush()
    return "\n".join(out)

TEMPLATE = """<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>{title} · Navya</title>
<link rel="stylesheet" href="/style.css">
</head>
<body>
<main class="legal">
<p><a href="/">&larr; Back to Navya</a></p>
{body}
</main>
<footer class="site-footer">
<p class="muted">&copy; Navya &middot; <a href="/terms.html">Terms of Use</a> &middot; <a href="/privacy.html">Privacy Notice</a> &middot; <a href="/grievance.html">Grievance Officer</a></p>
</footer>
</body>
</html>
"""

for src, dst in PAGES.items():
    md = (SRC / src).read_text()
    title = md.splitlines()[0].lstrip("# ").strip()
    (OUT / dst).write_text(TEMPLATE.format(title=html.escape(title), body=convert(md)))
    print("wrote", dst)

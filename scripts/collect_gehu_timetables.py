"""Collect explicitly linked CSE timetable candidates without publishing them."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import ssl
from urllib.error import URLError
from urllib.parse import urlparse
from urllib.request import Request, HTTPRedirectHandler, HTTPSHandler, build_opener
from datetime import datetime, timezone

HOST = "btechcsegehu.in"
PAGE = f"https://{HOST}/"
OUTPUT = Path(__file__).resolve().parents[1] / "data/sources/gehu-dehradun"
LIMIT = 20 * 1024 * 1024


class DepartmentRedirectHandler(HTTPRedirectHandler):
    def redirect_request(self, request, response, code, message, headers, new_url):
        if urlparse(new_url).hostname != HOST or urlparse(new_url).scheme != "https":
            raise ValueError("Redirect left the reviewed HTTPS host")
        return super().redirect_request(request, response, code, message, headers, new_url)


def fetch(url, allow_unverified):
    if urlparse(url).hostname != HOST:
        raise ValueError("Only the reviewed department host is allowed")
    request = Request(url, headers={"User-Agent": "QuickSlot-Research/1.0"})
    verified = True
    try:
        response = build_opener(DepartmentRedirectHandler()).open(request, timeout=30)
    except URLError as error:
        if not allow_unverified or not isinstance(error.reason, ssl.SSLCertVerificationError):
            raise
        verified = False
        response = build_opener(DepartmentRedirectHandler(), HTTPSHandler(context=ssl._create_unverified_context())).open(request, timeout=30)
    with response:
        if urlparse(response.url).hostname != HOST:
            raise ValueError("Redirect left the reviewed host")
        content = response.read(LIMIT + 1)
        if len(content) > LIMIT:
            raise ValueError("Source exceeds the 20 MiB limit")
        return content, verified, response.url


def collect(allow_unverified):
    OUTPUT.mkdir(parents=True, exist_ok=True)
    html, verified, final_url = fetch(PAGE, allow_unverified)
    (OUTPUT / "department-index.html").write_bytes(html)
    links = sorted(set(re.findall(r'href=[\"\x27]([^\"\x27]+)', html.decode())))
    candidates = [url for url in links if re.search(r'/2026/10/[^/]*TT[^/]*\.pdf$', url)]
    records = []
    for source_url in candidates[:12]:
        url = source_url.replace("http://", "https://", 1)
        record = {"source_url": source_url, "requested_url": url, "discovered_on": PAGE,
                  "category": "class_timetable_candidate", "review_status": "unreviewed",
                  "scope": "CSE; program and semester require document review"}
        try:
            content, tls_verified, resolved = fetch(url, allow_unverified)
            if not content.startswith(b"%PDF-"):
                raise ValueError("Response is not a PDF")
            filename = urlparse(url).path.rsplit("/", 1)[1]
            (OUTPUT / filename).write_bytes(content)
            record.update(file=filename, bytes=len(content), sha256=hashlib.sha256(content).hexdigest(),
                          tls_verified=tls_verified, resolved_url=resolved)
        except Exception as error:
            record["error"] = str(error)
        records.append(record)
    manifest = {"collected_at_utc": datetime.now(timezone.utc).isoformat(),
                "campus_claim": "Graphic Era Hill University, Dehradun",
                "affiliation": "Department-branded host; no inbound gehu.ac.in link verified. Administrator must confirm.",
                "index": {"url": final_url, "tls_verified": verified, "sha256": hashlib.sha256(html).hexdigest()},
                "notes": ["No records are published to the application.",
                          "Upload month is not an effective date. Inspect document contents.",
                          "TLS bypass is per request, explicitly enabled, and recorded; no global TLS settings change.",
                          "This collection does not establish campus-wide room coverage."],
                "documents": records}
    (OUTPUT / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")
    print(f"Downloaded {sum('file' in record for record in records)} of {len(records)} candidates")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--allow-unverified-department-tls", action="store_true")
    collect(parser.parse_args().allow_unverified_department_tls)

# GEHU Dehradun timetable sources

Collected on 20 October 2026 for a prototype. Nothing in this folder is automatically imported or published.

Six class timetable PDFs (64 pages total) were linked on https://btechcsegehu.in/: B.Tech CSE III, V and VII; M.Tech CSE I and III; Diploma CS III and V. The October URL folder is an upload location, not an effective date: the first pages state 13 July 2026 except M.Tech I, which states 17 August 2026. Each page and section still needs administrator review.

The host brands itself as the CSE department. We did not establish an inbound link from gehu.ac.in, so administrator confirmation of ownership and authority is required. GEHU's main Student Area and public site search did not expose class schedules for other departments in this bounded search. This is partial CSE coverage, not a campus-wide inventory. Exam schedules and academic calendars were excluded from class occupancy inputs. csitgeu.in is a different institution's department and was excluded.

Normal TLS verification failed with `unable to get local issuer certificate`. Expiry was not established. Collection used the user's authorized, per-request TLS bypass for this host only; the manifest records `tls_verified: false`. No system TLS setting was changed. The collector validates redirects before following them and uses a 20 MiB response limit.

`manifest.json` records source links, HTTPS fetch links, hashes, byte counts, first-page excerpts, page counts and review state. `department-index.html` preserves the discovery page. The PDF text reader reported malformed object offsets but extracted first-page text; visual import review is still required.

To refresh the six October candidates explicitly:

```powershell
python scripts/collect_gehu_timetables.py --allow-unverified-department-tls
```

The collector uses Python's standard library. Refreshing regenerates the manifest and does not preserve the optional PDF inspection annotations. No third-party scraping framework or downloaded GitHub skill was needed.

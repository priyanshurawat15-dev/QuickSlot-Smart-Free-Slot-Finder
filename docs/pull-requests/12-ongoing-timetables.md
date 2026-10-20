# PR 12 Ongoing timetables and PDF room matching

Base: codex/11-interface-refinement
Branch: codex/12-ongoing-timetables

Timetables now have an optional end date and default to no expiry. An administrator can publish a replacement within the same scope or remove a revision from a chosen date. Earlier publication dates, original sources and audit evidence remain available. Unbounded date ranges still participate in timetable and approved-booking conflict checks.

PDF upload matches exact CR/LT labels to one active room in the same campus. It preserves printed minute timings and source evidence. Unknown mappings stay unresolved. Matching alone never confirms whole-room coverage or publishes an unchecked source.

The administration screen shows effective publication windows and supports dated removal. Session review uses 25/50-row pages, keeps edits across pages and disables editing during writes. Repeated extraction issues are grouped so the review table remains reachable.

Validation: 40 Java tests passed against PostgreSQL, with no failures or skips. They cover exact-source elective extraction, shared-class deduplication, active publication, retirement, replacement and restart behavior. The Spring Boot guide and system workflow diagrams cover nullable validity and removal; the generated guide was rendered and inspected. Desktop/mobile checks cover no-end uploads and pagination with the actual supplied PDF. Full browser results are recorded in the published PR.

For the current demonstration, the user selected the supplied CSE III and V PDFs as the availability dataset. The demo profile publishes two source-specific room schedules effective 13 July 2026 without expiry. A deterministic extractor resolves elective rooms from same-page legends and deduplicates shared classes: 189 distinct classroom/LT sessions for III and 204 for V before adjacent occupancy consolidation. Out-of-scope lab/auditorium cells retain audit evidence. Original PDFs are unchanged; faculty identities are not invented. Retirement and replacement survive restarts. Production coverage still requires all courses using each room.

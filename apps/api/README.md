# Spring Boot API

Java 21 with Spring Boot 3.5, Spring JDBC, Flyway and PostgreSQL. All API routes use /api. Feature packages are identity, inventory, availability, requests and timetables; shared contains error handling, audit and scheduling coordination.

## Local accounts

Run the application with the demo profile and DEMO_SEED=true only on a local database. Each account uses QuickSlot-demo-2026! as the password:

| Role | Email |
| --- | --- |
| Faculty | faculty@demo.quickslot.test |
| Club head | club@demo.quickslot.test |
| Coordinator | coordinator@demo.quickslot.test |
| HOD | hod@demo.quickslot.test |
| Administrator | admin@demo.quickslot.test |
| Student | student@demo.quickslot.test |

The seed extracts 12 CR/LT room identities from the supplied CSE III and V PDFs and records filename, SHA-256 and printed label in room_sources. Buildings and capacities remain blank. It assigns the coordinator but does not publish timetable coverage. Disable the demo profile and seed flag before using real accounts. Users and allocations currently require controlled database administration; no public registration is exposed.

## Security and correctness

Session tokens are random opaque cookies; only their hashes are stored. Authenticated writes require X-CSRF-Token. The API interceptor requires authentication by default except explicit public search and login routes. Services independently enforce room/department authority and prohibit self-approval. An administrator role does not imply allocation authority.

Booking decisions and timetable publication share a PostgreSQL transaction advisory lock. Row locks protect decisions and revision updates; the occupancy exclusion constraint independently rejects overlapping booking records. Published timetable rules are checked under the same writer protocol. Public multi-query searches use a repeatable-read snapshot. Pending requests do not hold rooms.

Imports use Java PDFBox and Apache POI. Extraction is size/page bounded and runs in the upload request; a durable asynchronous job system is a later operational extension. Source bytes, hashes and evidence remain in PostgreSQL. Review is explicit; PDF room/faculty IDs are never guessed. Administrator room-coverage attestations must account for every course using that room.

Booking requests must start and end on whole hours. The interface provides one-hour slots. Timetable classes retain printed minute timings. Unknown room capacity blocks approval until verified.

## Validation

Run ./mvnw verify (Windows: .\mvnw.cmd verify). Set TEST_DATABASE_URL to a disposable JDBC PostgreSQL URL, TEST_DATABASE_USER and TEST_DATABASE_PASSWORD to run integration checks in randomly named schemas. Database-dependent tests are skipped without that URL; the PDF/DOCX adapter tests still run.

See the root README for startup and frontend commands and docs/learning/spring-boot-roadmap.md for a guided reading order.

## Clubs and membership

Club profiles and leadership are public; application lists and membership status require login. Only same-campus students can apply. Assigned club heads or same-campus administrators review requests; club_managers is maintained through controlled administration. The demo seed assigns its local club-head account to Co-Dev only in the demo profile. Membership verification does not change users.role or grant booking permission. Decisions lock membership rows and record audit events.

Co-Dev content is sourced from the supplied Aarambh 4.0 frontend data/organizers.js, pages/AboutPage.js and common/Footer.js. No real organizing-committee member is automatically given a login or reviewer permission.

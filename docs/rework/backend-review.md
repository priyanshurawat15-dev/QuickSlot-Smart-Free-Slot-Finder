# Backend review

Reviewed 20 October 2026. Static inspection only; no application or database tests run. No AGENTS.md found by repository file search. Existing code is a useful scaffold, not verified university-ready software.

## What is worth retaining

FastAPI, SQLAlchemy and PostgreSQL are suitable. Keep draft/published timetable concepts, half-open interval calculations, transactional booking approval, database occupancy exclusion, audit records and an outbox. Keep the worker separate from API processes. Valkey should remain optional for search performance and never decide booking correctness.

## Concrete gaps

| Priority | Evidence | Required change |
| --- | --- | --- |
| Critical | `backend/app/api/auth.py:71` exposes unauthenticated role switching, including ADMIN. | Remove from normal application builds; isolate any demo mechanism behind an explicit local-only demo deployment. |
| Critical | `backend/app/api/requests.py:16` authenticates but does not restrict requester roles; `backend/app/services/booking_service.py:48` also lacks that check. | Deny student booking submissions server-side; allow faculty and verified club heads. |
| Critical | `backend/app/api/timetable.py:230` archives every other active version in the campus; `backend/app/services/availability_engine.py:136` and `backend/app/services/booking_service.py:229` select only the first active version. | Replace only the same schedule scope; union every applicable course/section revision when checking rooms. Publishing third semester must preserve fifth semester and other courses. |
| Critical | `backend/app/services/availability_engine.py:143` treats existence of any campus timetable as complete room coverage. | Add explicit coverage declarations and unresolved-import effects. One CSE document cannot establish campus-wide availability. Return UNKNOWN where coverage is incomplete. |
| High | Worker reads `sess['room_candidate']` at `backend/worker/job_runner.py:100`, but parser emits `venue_name` in `backend/app/services/timetable_parser.py`. | Define a typed canonical extraction contract and test parser-to-worker integration. Nonempty imports currently risk KeyError. Never silently substitute the first venue (`job_runner.py:104`). |
| High | Upload accepts PDF/JSON/XLSX at `backend/app/api/timetable.py:31`; worker always invokes PDF parsing at `backend/worker/job_runner.py:58`. | Implement real PDF and DOCX adapters; advertise other formats only when supported end-to-end. Use bounded streaming and generated storage keys, never client filenames as storage paths. |
| High | Roles in `backend/app/models/identity.py:9` lack HOD and CLUB_HEAD; statuses in `backend/app/models/booking.py:12` lack escalation. | Add HOD authority, club membership verification, scoped coordinator assignment and a FORWARDED_TO_HOD state with reason/history. Admin maintenance authority must not automatically confer booking approval. |
| High | `backend/app/core/security.py:34` stores sessions per process; `require_roles` at line 103 trusts the saved role and grants broad admin bypass. `backend/app/api/auth.py:37` uses insecure cookies and returns session token in login JSON. | Durable sessions, configurable secure cookies, account revocation/permission refresh, explicit permission policies, login throttling and protected account lifecycle. |
| High | `backend/app/services/availability_engine.py:192` computes free intervals even when coverage is unknown; availability and approval do not query closures despite importing ResourceOccupancy. | Keep candidate gaps distinct from verified availability. Include room closures and operating hours in the same decision service. Restrict student search to classrooms/LTs and show “Confirm the room is empty when you arrive.” |
| High | Publication conflict preview in `backend/app/api/timetable.py:151` checks future bookings without limiting them to the revision's effective dates; publication does not populate class occupancies. | Validate date-bounded collisions, unresolved issues and inter-course clashes in the publication transaction. Decide consistent occupancy materialization; do not claim the exclusion constraint currently protects timetable classes. |
| Medium | `backend/worker/job_runner.py:88` hardcodes semester 5; uploader is replaced by first admin at line 62. | Derive validated programme/semester/scope metadata; retain the actual uploader and immutable source provenance. |
| Medium | First creation of resource/day row is select-then-insert (`backend/app/services/booking_service.py:206`); approval idempotency parameter is unused; submit payload hash at line 79 omits attendance/equipment/group. | Upsert before locking, support complete idempotency semantics, return retry-safe conflicts, test real concurrent transactions. |
| Medium | Worker lease has no heartbeat/fencing; notifications are marked SENT without dispatch (`backend/worker/job_runner.py:161`). | Bound retries, renew/fence jobs, make import output unique by job, and persist in-app notifications before reporting delivered. |
| Medium | Import job details, draft versions and entries are public in `backend/app/api/timetable.py`; public availability exposes requester names and purpose. | Publish a minimal public availability projection; keep import evidence, draft details and private application text administrator/reviewer-only. |

## Recommended modules

Use a modular monolith: `identity`, `campus`, `timetables`, `availability`, `requests`, `notifications`, and `audit`. Within each feature use routes, schemas, service and persistence only where each has meaningful work. Parser adapters and external notification providers sit behind narrow interfaces. Shared infrastructure holds database, configuration, sessions, storage and worker execution. Remove the OS demonstration routes from the main product. Do not split into microservices.

## Data model changes

- Campus, department, programme, academic term, cohort/section and room with canonical textual code, building and aliases. Room codes such as `017` remain strings.
- User plus scoped role assignments; club and club-head membership. Faculty schedule identities should be separate from login accounts so importing a teacher does not create credentials.
- Schedule scope identifies the programme/cohort/term or other verified replacement unit. Immutable source document and import job produce extracted candidates and review issues. A revision owns resolved recurring sessions and valid-from/valid-to dates. A publication closes only superseded coverage for its own scope. Historical date queries remain reproducible.
- Session links to multiple cohorts/faculty where necessary. Deduplicate shared elective sessions explicitly; several sections attending one shared event is not several competing physical occupancies.
- Coverage declarations identify room/scope/date ranges and whether verification is complete. Retain unresolved locations, rather than assigning guessed rooms.
- Room closures and calendar exceptions represent maintenance, holidays, rescheduling and one-off classes.
- Request plus append-only decision events: SUBMITTED → APPROVED / REJECTED / FORWARDED_TO_HOD; forwarded → HOD APPROVED / REJECTED; requester can withdraw while pending. Approved requests produce separate bookings. Forwarding never reserves the room.
- Audit events, durable notifications and outbox rows commit with the action. Booking approval locks and rechecks all applicable schedules, closures and bookings before inserting occupancy.

## Meaningful validation before a pilot

1. API authorization matrix: anonymous can view the permitted public projection; students cannot submit; club heads/faculty can; coordinator is scope-bound; HOD can decide; administrator can publish but does not gain unrelated approval privileges.
2. Import real third/fifth-semester fixtures and DOCX through the worker; compare selected cells, merged labs, aliases and unresolved issues against manually checked source evidence. No guessed fallback rooms.
3. Publish two course schedules sharing a room; both block availability. Replace one at a future effective date; verify the other and historical dates remain correct. A missing third course keeps affected coverage unknown.
4. Concurrent approvals for one room allow exactly one success; adjacent intervals both succeed. Retry the same request/idempotency key and verify one booking, one decision and one notification.
5. Race timetable publication against approval; no booking may silently overlap the resulting published classes. Cover closures, cancellations and effective-date boundaries in Asia/Kolkata.
6. Crash/restart import and notification workers; confirm no duplicate revisions or false SENT records. Verify permission/account changes invalidate existing access.
7. Use a dedicated disposable PostgreSQL test database. Existing `backend/tests/conftest.py` uses configured engine and existing users without rollback isolation; do not run against university or developer data.

## Decisions still worth confirming

Who creates/verifies accounts, how club head succession works, whether HOD authority covers all campus rooms or CS only, operating hours/booking lead limits, who may cancel approved bookings, and whether a revised class timetable takes precedence over an approved event. Recommended initial policy: require an explicit conflict resolution before publication; never silently cancel either event.

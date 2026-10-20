# QuickSlot

Room availability and allocation for Graphic Era Hill University, Dehradun.

Java 21, Spring Boot 3.5, PostgreSQL, React and TypeScript. The backend was rebuilt from scratch; the previous implementation is retained in Git history before codex/02-clean-foundation.

## What works

- Public classroom and lecture-theatre search with clear verification status, confirmed floors and weekly room schedules.
- Co-Dev profile sourced from the supplied Aarambh 4.0 project; student membership applications verified by assigned club heads or managed by administrators.
- Faculty and club-head requests; coordinator approval, rejection or forwarding; department HOD decisions.
- Durable cookie sessions, CSRF checks, room-scoped permissions, decision history and in-app notifications.
- PDF and structured DOCX extraction with source evidence, faculty/room mapping review and stale-edit protection.
- Dated timetable replacement that retains historical windows and unrelated scopes.
- PostgreSQL migrations, transaction coordination and a constraint preventing overlapping active bookings.

Verified club members may request a room for their club, subject to coordinator/HOD approval. Membership does not upgrade their student role or grant faculty access. Club manager assignments require controlled administration; the local demo seed assigns its club-head account to Co-Dev.

The supplied timetable sources cover CSE only. Unknown coverage remains visible; no campus-wide availability is claimed.

## Run locally

Install a JDK 21 or newer and PostgreSQL 17. Enable the btree_gist extension or use a database owner permitted to create it during the initial migration. The Maven wrapper downloads the build tool on first use.

From apps/api in PowerShell:

```powershell
$env:DATABASE_URL='jdbc:postgresql://127.0.0.1:5432/quickslot'
$env:DATABASE_USER='quickslot'
$env:DATABASE_PASSWORD='your-local-database-password'
$env:COOKIE_SECURE='false'
$env:PORT='8081'
.\mvnw.cmd spring-boot:run
```

The API defaults to port 8000. The web development proxy defaults to 8081 to avoid another application using 8000 on this workspace. Set PORT=8081 for the backend, or set API_PROXY_TARGET=http://127.0.0.1:8000 when starting Vite. API routes use /api. Flyway applies migrations on startup.

For local role accounts, additionally set SPRING_PROFILES_ACTIVE=demo and DEMO_SEED=true before startup. Credentials are documented in apps/api/README.md. Room identities come from the two supplied CSE PDFs, with source hashes recorded. Their 12 CR/LT labels retain leading zeroes. Buildings and capacities remain unverified; the seed does not publish timetable coverage. Production does not seed accounts.

From apps/web:

```powershell
npm ci
npm run dev
```

Open http://127.0.0.1:5173. The Spring application does not automatically load .env; export local environment variables. Docker Compose loads the explicitly supplied environment file.

## Checks

```powershell
# apps/api: requires a disposable PostgreSQL database
$env:TEST_DATABASE_URL='jdbc:postgresql://127.0.0.1:5432/quickslot_test'
$env:TEST_DATABASE_USER='quickslot'
$env:TEST_DATABASE_PASSWORD='your-test-password'
.\mvnw.cmd verify

# apps/web
npm run build
npm run format:check
# With the API running and the sample accounts seeded:
npx playwright install chromium
npm run test:e2e
```

Integration tests use isolated schemas; do not configure university databases for tests. Without TEST_DATABASE_URL, PostgreSQL-dependent tests are skipped. Review test output before claiming those checks passed.

## Documentation

- docs/rework/architecture-plan.md: architecture, workflows and release gates.
- docs/learning/spring-boot-roadmap.md: maintained Java-to-Spring-Boot learning plan.
- output/pdf/QuickSlot_Spring_Boot_Learning_Guide.pdf: printable learning guide.
- docs/pull-requests/README.md: numbered local branches and merge order.
- infra/README.md: deployment and recovery instructions.
- data/sources/gehu-dehradun: downloaded timetable evidence and provenance.

## Before university deployment

Configure approved account provisioning and club-head verification, confirm room inventory and complete timetable coverage, agree calendar/cancellation rules, and test backup restoration. Import extraction currently runs within the upload request with size/page limits; it is not yet a durable background job. Notifications are in-app only. Writes use one database advisory lock initially; measure contention before narrowing lock scope. Accounts currently have one role each. No email delivery, password reset or college SSO is integrated.

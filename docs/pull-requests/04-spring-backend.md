# PR 04 Spring Boot identity and allocation workflows

Base: codex/03-spring-learning-guide
Branch: codex/04-spring-backend

Rebuilds the API in Java 21 and Spring Boot with feature packages, Spring JDBC and Flyway. Adds durable session authentication, CSRF checks, public room discovery, faculty/club requests, scoped coordinator decisions, HOD forwarding, audit history and in-app notifications. PostgreSQL locks and exclusion constraints protect allocations. Students cannot submit requests and administrators do not gain implicit approval authority.

The timetable lookup is an optional interface; absent verified coverage remains unknown. The following PR supplies its importer and publication implementation.

Validation: seven core PostgreSQL integration tests passed, including competing approvals, independent database exclusion checks, adjacent intervals, scope/role checks and request retries. Tested locally on PostgreSQL 18.1 with JDK 23 targeting Java 21. Production/CI configuration targets PostgreSQL 17 to match the pinned Flyway support matrix.

Limits: one account role, one shared scheduling write lock, in-app notifications, controlled database account provisioning. No university rollout is implied.

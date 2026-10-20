# PR 05 Reviewed timetable imports and dated publication

Base: codex/04-spring-backend
Branch: codex/05-timetable-imports

Adds Java PDFBox and Apache POI adapters, immutable source retention, page/cell evidence, explicit room/faculty review, revision checks, teacher scheduled-venue lookup and scoped timetable publication. Replacements preserve historical windows and unrelated scopes. Conflicting bookings and stale administrator edits block publication.

Validation: five adapter checks and four real PostgreSQL timetable integration checks passed, including the supplied 18-page PDF, marked DOCX merged periods, scoped replacement, stale revisions and competing publication. Combined backend run: 16 tests passed with no skips. JAR packaging succeeded after stopping the local server holding the Windows output file.

Limits: extraction is bounded foreground work, not a durable worker. PDF candidates require administrator mapping; source completeness and room coverage require verification before publication.

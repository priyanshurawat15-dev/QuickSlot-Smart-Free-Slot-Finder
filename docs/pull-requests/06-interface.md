# PR 06 GEHU room and allocation interface

Base: codex/05-timetable-imports
Branch: codex/06-campus-interface

Adds the React/TypeScript interface for public room search, weekly room schedules, role-based requests, coordinator/HOD decisions, teacher schedules and administrator timetable review. The updated theme uses the official black wordmark, a short light glass header, glass footer, compact room cards, darker text and balanced grids. Doodles are removed. The shared booking selector offers one-hour intervals; timetable review preserves printed minute timings.

Validation: production build and formatting checks; desktop and mobile browser workflows scroll every route to its footer, check overflow and exercise API-backed requests, forwarding and rejection. Backend rejects minute boundaries in the following source-inventory milestone. These branches are local review packages; no remote PR was created.

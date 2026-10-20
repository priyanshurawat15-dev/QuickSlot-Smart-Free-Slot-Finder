# PR 10 Co-Dev profiles and verified student membership

Base: codex/09-room-presentation
Branch: codex/10-club-membership

Adds public club directory/profile screens with Co-Dev officials, faculty mentor and contact links from the supplied Aarambh 4.0 project. Students apply while signed in; assigned club heads verify or decline, and same-campus administrators can manage requests. Unique membership records, row locks, CSRF checks, scoped reviewer authority and audit events protect the workflow. Verification never changes the student account role or enables room booking.

The interface follows Aarambh's Outfit typography and restrained paper/charcoal/purple palette. Blue text and blue-tinted panels are removed. Membership instructions and the source-backed club profile fill balanced columns on desktop, with a single-column mobile flow. The updated 11-page Spring Boot guide explains relationship-level authority and its tests.

Validation: clean Maven verify passed 24 tests with zero skips; production frontend build passed; all 12 desktop/mobile browser checks passed after the font/theme changes. The student verification check inherits real mobile device settings. The learning PDF was rendered and visually inspected. No remote writes were attempted.

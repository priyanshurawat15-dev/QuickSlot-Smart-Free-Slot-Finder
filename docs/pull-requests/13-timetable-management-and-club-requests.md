# PR 13 Timetable management and club requests

Base: codex/12-ongoing-timetables
Branch: codex/13-timetable-management-and-club-requests

Reworks timetable administration around centered drag-and-drop upload, visible current files and a room-mapping review. Automatic flags identify unmatched rooms, invalid timings and overlapping entries. Logs, historical uploads and detailed editing stay collapsed until needed.

Deletion archives one revision and ends its current/future timetable allocation and coverage. It preserves original sources, previous dates, other timetables and approved bookings. Deleted versions cannot be republished. Timetable lists omit editable grids and queries avoid loading PDF bytes unnecessarily.

Faculty can choose extra classes, workshops, hackathons, club meetings or Other with manual details. Verified students can request for their own active campus club; the request retains the club identity and still needs allocation approval. Submission and approval check current membership and account status without upgrading the student role.

The later approval-automation discussion is recorded separately. No automatic approval policy is enabled.

Validation: 48 PostgreSQL-backed Java tests passed with no skips. Five targeted tests also passed after narrowing timetable list responses. Desktop/mobile checks cover the real source files, drag-and-drop, file deletion/history, mapping pagination and verified-member submissions. The learning guide was regenerated and visually inspected.

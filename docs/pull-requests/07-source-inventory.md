# PR 07 PDF-derived room inventory and whole-hour bookings

Base: codex/06-campus-interface
Branch: codex/07-source-inventory

Replaces illustrative seed rooms with the 12 classroom/lecture-theatre labels extracted from the two supplied CSE PDFs. Retains leading zeroes, CR/LT types, source filenames and SHA-256 evidence. Buildings and capacities remain unknown. Old illustrative records are retired without deleting their history. Null capacity is represented accurately in the public API.

Booking submission rejects minute/second offsets. Unknown capacity allows submission but blocks approval until verified; timetable coverage and conflict checks remain required. Timetable class timings keep their printed minute precision.

Validation: 18 Java tests passed against isolated schemas on local PostgreSQL 18.1, with zero failures or skips, including exact source labels, minute rejection, unknown capacity, competing allocations, PDF/DOCX extraction and scoped replacements. CI and deployment target PostgreSQL 17. The seed does not publish incomplete CSE schedules as campus-wide availability.

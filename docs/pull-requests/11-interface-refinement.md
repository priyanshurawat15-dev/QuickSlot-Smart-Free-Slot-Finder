# PR 11 Interface refinement and club presentation

Base: codex/10-club-membership
Branch: codex/11-interface-refinement

Removes public Needs verification badges/counts/filter without declaring incomplete schedules free. Guest requests retain their selected room and whole-hour slot through sign-in. Enlarges dark text and buttons, strengthens the Aarambh purple accent, and replaces native dropdowns with a consistent accessible selection control across search, booking, rooms, requests, teachers and timetable review. Multi-faculty assignment uses touch-friendly checkboxes.

Co-Dev uses the exact supplied logo file with its white background preserved in a shared circular frame used by every club card and profile. The faculty coordinator is separate from the 15-member core team. The committee attribution and its duplicate description sentence are removed from the profile; source provenance stays in documentation.

Validation: production build passed; 24 desktop/mobile browser checks passed, including keyboard selection, required form values, real API submission, role workflows, guest sign-in continuation and staff-only lookup. All 25 Java tests passed with no failures or skips. Logo bytes match the supplied file; the shared circular frame was visually inspected.

Faculty timetable lookup is restricted to faculty, coordinators, HODs and administrators at both API and navigation boundaries. Public room schedules expose generic occupancy labels. The redundant total Rooms summary is removed.

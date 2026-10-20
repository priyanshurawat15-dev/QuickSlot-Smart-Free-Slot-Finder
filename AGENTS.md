# QuickSlot working agreements

## Product

Build for Graphic Era Hill University, Dehradun. Students and public visitors can inspect classroom and lecture-theatre availability, but students cannot book. Students may request club membership; assigned club heads verify it and administrators can manage it. Membership does not change booking permission. Faculty and verified club heads can request. The responsible allocation coordinator can decide or forward to the scoped HOD. Forwarded requests require HOD decision. Administrators manage inventory, accounts and timetable publication. Timetables have no default end date: they remain effective until an administrator removes or replaces them. Never infer room coverage from one course timetable, and retain old timetable versions as historical evidence when publishing replacements.

## Implementation

The user selected Java with Spring Boot for the backend and React with TypeScript for the frontend. Implement the rebuild from scratch. Keep feature modules focused, use readable formatting, and avoid unnecessary abstractions. Comments explain reasons; do not add separator banners or comments that repeat code. Use database transactions and constraints for allocation correctness. Do not declare incomplete timetable data verified free.

## Design

Use the official GEHU website as the institutional theme reference. Keep glass headers and footers, consistent alignment, balanced cards and clear dropdowns. Remove doodles. No emojis in the UI or documentation, no gradients or decorative bright text. A strong Aarambh-purple accent is explicitly requested for primary buttons and active controls. Use charcoal glass headers and footers, crisp vector GEHU emblem and live Graphic Era Hill University text without campus names in the brand. Use Aarambh's Outfit typeface with warm paper surfaces, larger dark neutral text and strong purple controls; no blue text or blue-tinted panels. Keep the header short. Use dark readable text and compact colored status cards: green free, red in use/booked, neutral cards when schedule completeness is unconfirmed. Do not display a Needs verification badge/count/filter publicly. Keep internal coverage and conflict guards in the backend; guests sign in when requesting. Do not claim official university endorsement. Use the provided PDFs for room identities and their CR/LT conventions; leave unverified buildings and capacities blank. The user confirmed floor convention: 017 ground floor, 1xx first floor, 2xx second floor. Booking choices use whole hours; preserve the printed minute timings of timetable classes for conflict checks.

## Delivery

After the thinking is settled, use subagents for independent tasks with clear file ownership. Commit each validated small milestone. Prepare numbered PR descriptions and stacked local branches in merge order. Publish a GitHub PR when explicitly requested by the user. If upstream access is read-only, use an authenticated fork. Do not merge without a request. Call local-only descriptions PR review packages until real PRs exist. Keep workflow and system diagrams current. Use the exact user-supplied Co-Dev logo file unchanged, preserving its white background; use one shared circular white bordered frame for all clubs, fitting each image without cropping. Show only Free and In use/booked summary cards, not a total Rooms card. Faculty timetable lookup is staff-only and absent from public/student/club-head navigation; room schedules remain public with generic occupancy labels.

## Learning documentation

The user knows Java and is learning Spring Boot. Maintain docs/learning/spring-boot-roadmap.md and regenerate output/pdf/QuickSlot_Spring_Boot_Learning_Guide.pdf after meaningful backend architecture changes. Explain what to learn, how it relates to QuickSlot, a practical exercise and a completion check. Inspect the rendered PDF before delivering it.

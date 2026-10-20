# PR 09 Charcoal branding, room status colors and floors

Base: codex/08-integration-and-delivery
Branch: codex/09-room-presentation

Replaces the blurry wordmark with the original vector GEHU emblem and live Graphic Era Hill University text, without campus names in the brand. Adds compact charcoal glass navigation and a structured footer based on the supplied Aarambh reference. Green, red and amber room cards distinguish free, in use/booked and needs-verification states. Status totals and filters use the displayed room data.

Adds floor metadata using the user-confirmed 017 ground / 1xx first / 2xx second convention. Administrators can verify or correct room floor, building and capacity; unknown details remain blank. Metadata writes share the booking transaction lock. The clearer verification label never treats missing timetable sources as free.

Validation: 24 Java tests passed with no skips across the full stack, including new metadata permission and floor-convention tests. Production web build and the 12-check desktop/mobile browser suite passed. Club screens are supplied by the following stacked milestone.

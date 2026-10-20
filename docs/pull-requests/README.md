# Ordered local pull requests

These are local review packages, not published GitHub pull requests. Branches are stacked and should merge in this order. Labels are merge sequence numbers, not assigned GitHub PR numbers.

| Order | Branch | Base | Description |
| --- | --- | --- | --- |
| 01 | codex/01-rebuild-plan | main | [Architecture](01-architecture.md) |
| 02 | codex/02-clean-foundation | codex/01-rebuild-plan | [Foundation](02-foundation.md) |
| 03 | codex/03-spring-learning-guide | codex/02-clean-foundation | [Learning guide](03-learning-guide.md) |
| 04 | codex/04-spring-backend | codex/03-spring-learning-guide | [Spring backend](04-spring-backend.md) |
| 05 | codex/05-timetable-imports | codex/04-spring-backend | [Timetables](05-timetables.md) |
| 06 | codex/06-campus-interface | codex/05-timetable-imports | [Interface](06-interface.md) |
| 07 | codex/07-source-inventory | codex/06-campus-interface | [Source inventory](07-source-inventory.md) |
| 08 | codex/08-integration-and-delivery | codex/07-source-inventory | [Delivery](08-delivery.md) |
| 09 | codex/09-room-presentation | codex/08-integration-and-delivery | [Room presentation](09-room-presentation.md) |
| 10 | codex/10-club-membership | codex/09-room-presentation | [Clubs and membership](10-club-membership.md) |
| 11 | codex/11-interface-refinement | codex/10-club-membership | [Interface refinement](11-interface-refinement.md) |
| 12 | codex/12-ongoing-timetables | codex/11-interface-refinement | [Ongoing timetables](12-ongoing-timetables.md) |
| 13 | codex/13-timetable-management-and-club-requests | codex/12-ongoing-timetables | [Timetable management and club requests](13-timetable-management-and-club-requests.md) |

Use [01], [02] and so on as title prefixes when a maintainer can push these branches. Keep the immediate predecessor as the base during review. After merging a predecessor, retarget its successor to main. Preserve predecessor commits or coordinate a rebase; squash-merging a stacked branch without rebasing successors can duplicate diffs.

The user authorized upstream pushes using Ixotic27. The original fork [PR #1](https://github.com/priyanshurawat15-dev/QuickSlot-Smart-Free-Slot-Finder/pull/1) is closed as superseded; its history remains available. Current published PRs are stacked directly in the repository:

1. [PR #2: milestones 01-11](https://github.com/priyanshurawat15-dev/QuickSlot-Smart-Free-Slot-Finder/pull/2), based on main.
2. [PR #3: ongoing timetables](https://github.com/priyanshurawat15-dev/QuickSlot-Smart-Free-Slot-Finder/pull/3), based on codex/11-interface-refinement.
3. [PR #4: timetable management and club requests](https://github.com/priyanshurawat15-dev/QuickSlot-Smart-Free-Slot-Finder/pull/4), based on codex/12-ongoing-timetables.

Merge in that order. After each predecessor is merged, retarget the next PR to main. No PR has been merged by this task. The numbered local descriptions remain milestone review packages rather than separate published PRs.

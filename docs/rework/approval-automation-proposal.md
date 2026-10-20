# Approval automation for discussion

This is a proposal for the coordinator and HOD. Requests currently require their normal decision.

A later policy could automatically approve routine faculty extra classes when every required check passes: authorized faculty and room scope, current published timetable, no conflicting class or booking, confirmed capacity, attendance within capacity and permitted whole-hour timings. The policy owner must choose which rooms, reasons and time limits qualify. Student club requests can remain manual unless the university approves a separate policy.

```mermaid
flowchart TD
    A[Request received] --> B{Approved automation policy applies?}
    B -->|No| C[Coordinator review]
    B -->|Yes| D[Recheck authority, timetable, capacity and conflicts]
    D -->|Any check fails or is incomplete| C
    D -->|Every check passes| E[Allocate in the normal transaction]
    E --> F[Record policy version and notify requester and coordinator]
    C --> G[Approve, reject or forward to HOD]
```

The coordinator should see a queue of exceptions rather than receive a phone alert for every routine request. A digest and escalation reminders could support that queue. Forwarded requests must still wait for the HOD.

Before implementation, agree on policy ownership, qualifying reasons and rooms, advance-notice limits, cancellations, daily limits, exceptional closures and who can suspend automation. Use the same allocation transaction and database overlap constraint; a cached green card must never authorize a booking.

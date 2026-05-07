# Step 10 — Calc UI

**Status:** Pending.
**Prereqs:** Step 9 green.

## Scope

Frontend exposes the calculation flow.

- Submission form: react-hook-form + zod, AntD Form components.
- Jobs list: AntD Table with status Tag.
- Job detail: polled with TanStack Query `refetchInterval`, shows steps + final result.
- Vitest + MSW for the new components.

## First questions

1. What does the calc input actually look like? (Free text? JSON? A structured form?) — needs to align with whatever the Calculation Service does AI-wise.
2. Job result rendering: code block? Markdown? Custom renderer?

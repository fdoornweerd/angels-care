# NNN: Feature name

**Status:** Draft · **Branch:** `feature/NNN-short-name`
<!-- Draft → Approved (date) → Implemented (date). Only Finn moves a spec to Approved. -->

## Goal
One paragraph: what the user can do afterwards that they cannot do now, and why it matters to the
school.

## Current state
What exists today that this touches (tables, screens, services). "Nothing" is a valid answer.

## Behaviour
What the user sees and does, step by step. Written from the bookkeeper's point of view, not the
code's.

## Changes
- **Schema:** new Flyway migration(s). New synced tables have `id TEXT` UUID,
  `created_at`/`updated_at`/`deleted_at`. Amounts are `INTEGER` whole UGX.
- **Code:** services, repositories, controllers, FXML views.
- **Other:** README, CLAUDE.md, CI — only if they change.

## Acceptance criteria
Each one becomes at least one test named `AC-n: …`. Cover the empty, zero and boundary cases,
not just the happy path.
- **AC-1** Given …, when …, then ….
- **AC-2** …

## Open decisions
Genuine forks only, each with a recommendation.
1. **Question?** Options… *Recommend:* … because ….

## Out of scope
What this spec deliberately does not do, so it is not built "while we're here".

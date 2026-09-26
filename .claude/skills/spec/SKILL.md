---
name: spec
description: Write a feature spec for Angels Care (docs/specs/NNN-name.md) by interviewing Finn. Use when starting any non-trivial feature, before any code or tests are written.
argument-hint: <feature idea>
---

# /spec: write a feature spec

The output is a **Draft** spec. Do not write tests or production code in this skill; that is `/tdd`.

## 1. Understand before asking
- Re-read the rules in `CLAUDE.md` (money, schema, start-up); the spec must not contradict them.
- Read existing specs in `docs/specs/` for decisions already made, and read the code this feature
  touches. Don't ask Finn anything the code or an earlier spec already answers.
- Next number = highest existing `NNN` + 1, zero-padded to three digits.

## 2. Interview
Use AskUserQuestion, in rounds of up to 4 questions, until the behaviour is unambiguous.
- Only ask questions whose answer changes what gets built. Put the recommended option first and
  say why.
- Always settle: who does this and how often; what is on screen; what counts as invalid input;
  what happens to existing data; how it behaves when there is nothing yet (no students, no
  payments, a zero balance).
- Money and dates: amounts are whole UGX; business dates are `LocalDate` in Africa/Kampala.
  Ask about school terms or the calendar if the feature depends on them.
- If an answer conflicts with a CLAUDE.md rule (e.g. hard deletes), point out the consequence
  and propose the compliant version rather than silently overriding either.

## 3. Write the spec
- Copy `docs/specs/TEMPLATE.md` to `docs/specs/NNN-short-name.md` and fill every section.
- Acceptance criteria are **Given/When/Then** and each can be checked by an automated test. A
  criterion that can only be checked by eye (layout, Windows packaging) says so explicitly.
- Keep deferred ideas in "Out of scope", not half-specified in the body.
- Leave `Status: Draft`.

## 4. Hand back
Summarise the spec in a few lines, list the open decisions with your recommendations, and stop.
When Finn approves, set `Status: Approved (YYYY-MM-DD)`, move resolved open decisions into a
"Decisions" section, and point him to `/tdd NNN`.

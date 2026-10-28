---
name: spec
description: Step 1 of every Angels Care feature - read Finn's description, ask clarifying questions, and write a spec (docs/specs/NNN-name.md) for his approval. Use whenever Finn describes a feature or change he wants, before any code is written.
argument-hint: <feature description>
---

# /spec: step 1, from description to approved spec

Finn describes what he wants; this skill turns it into a **Draft** spec and stops for his
approval. Do not write production code here; that is step 2 (`/implement`).

## 1. Understand before asking
- Read Finn's description carefully and restate it to yourself as concrete behaviour.
- Re-read the rules in `CLAUDE.md` (money, schema, start-up); the spec must not contradict them.
- Read existing specs in `docs/specs/` for decisions already made, and read the code this feature
  touches. Don't ask Finn anything the code or an earlier spec already answers.
- Next number = highest existing `NNN` + 1, zero-padded to three digits.

## 2. Ask clarifying questions
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
- Acceptance criteria are **Given/When/Then**, concrete enough that Finn can check each one in
  the app. Cover the empty, zero and boundary cases.
- Keep deferred ideas in "Out of scope", not half-specified in the body.
- Leave `Status: Draft`.

## 4. Hand back for approval
Summarise the spec in a few lines, list any remaining open decisions with your recommendations,
and **stop**. Don't start implementing in the same turn.

When Finn approves (in words, e.g. "approved", "go ahead"): set `Status: Approved (YYYY-MM-DD)`,
move resolved open decisions into a "Decisions" section, and continue straight into step 2 by
following `/implement NNN`. If he asks for changes instead, revise the spec and stop again.

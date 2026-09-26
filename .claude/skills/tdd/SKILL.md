---
name: tdd
description: Implement an approved Angels Care spec test-first - failing tests, stop for review, then implement and verify. Use when Finn says to build or implement a spec.
argument-hint: <spec number, e.g. 004>
---

# /tdd: implement an approved spec, red → review → green

## 0. Preconditions
- Find `docs/specs/<NNN>-*.md`. If its status is not `Approved`, **stop** and say so; offer `/spec`.
- Be on branch `feature/NNN-short-name` (create it from an up-to-date `main` if needed). Carry
  over any unrelated uncommitted changes untouched.

## 1. Red: failing tests
- At least one test per acceptance criterion, named `@DisplayName("AC-n: …")`, plus tests for
  the empty/zero/boundary cases the spec lists.
- Where tests go:
  - repositories and services: extend `support/DatabaseTest` (a real temp SQLite DB with every
    migration applied). No Mockito for our own classes; use real objects or hand-written fakes.
  - pure logic (money, calculations): plain unit tests.
  - anything using the time: inject a fixed `java.time.Clock`.
- Production code may only get **stubs** so the tests compile: signatures that
  `throw new UnsupportedOperationException("not implemented")`. No behaviour, no migrations.
- Run `./gradlew test`. Check that every new test fails **for the intended reason** (a stub
  throwing or an assertion), not because of setup, and that existing tests still pass.
- `mkdir -p .claude/state && touch .claude/state/red-phase`. This stops the Stop hook from
  sending the expected failures back to you.
- Show Finn a table: AC → test → how it fails. Then **stop and wait for his review.** Don't
  start implementing in the same turn.

## 2. Green: implement
- Once Finn approves: `rm -f .claude/state/red-phase`.
- Write the minimum code to pass, then refactor with the tests green. Follow the CLAUDE.md rules
  (UGX `long`, UUID ids, soft deletes, sync columns, new migrations only).
- If implementation turns up a case the spec missed: write a failing test for it first, then fix
  it, and add a note to the spec.

## 3. Verify: tests are not the whole proof
- Run the full `./gradlew test` and read the result counts.
- Run the app (`./gradlew run`) and exercise the feature. Check the fresh start-up log in
  `~/AngelsCareData/angels-care-startup.log`; check it is today's run, not an old one.
- Back up `~/AngelsCareData/angels-care.db` first, then check the feature against Finn's real
  database with `sqlite3`, especially the empty, zero and boundary cases.
- If packaging or start-up changed, also run `./gradlew jpackageImage` and launch
  `app/build/jpackage/AngelsCare.app`. Batch anything that needs a Windows test into one round.

## 4. Finish
- Set the spec to `Status: Implemented (YYYY-MM-DD)`, noting any AC still waiting on a manual or
  Windows check.
- Update README / CLAUDE.md only if a convention or command changed.
- Report what was verified vs. what is assumed. Commit or open a PR only when Finn asks.

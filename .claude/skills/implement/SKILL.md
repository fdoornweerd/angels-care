---
name: implement
description: Step 2 of every Angels Care feature - implement an approved spec, then verify it. Use when Finn approves a spec or says to build or implement one.
argument-hint: <spec number, e.g. 004>
---

# /implement: step 2, build an approved spec

## 0. Preconditions
- Find `docs/specs/<NNN>-*.md`. If its status is not `Approved` and Finn hasn't just approved it
  in chat, **stop** and say so; offer `/spec`.
- Be on branch `feature/NNN-short-name` (create it from an up-to-date `main` if needed). Carry
  over any unrelated uncommitted changes untouched.

## 1. Implement
- Build what the spec describes, following the CLAUDE.md rules (UGX `long`/`Ugx`, UUID ids, soft
  deletes through `SyncedTable`/`DeletionGuard`, sync columns, new migrations only) and the
  feature/layer architecture.
- Keep existing tests passing. Add or update tests where they protect a business rule (money
  calculations, migrations that rewrite data, service logic), using the conventions in CLAUDE.md:
  `support/DatabaseTest` for repositories and services, a fixed `Clock` for anything time-based.
- If implementation turns up a case the spec missed, decide it the way the spec's intent
  suggests when it is minor, and note it in the spec; ask Finn when it changes behaviour he would
  notice.

## 2. Verify: tests are not the whole proof
- Run the full `./gradlew test` and read the result counts.
- Run the app (`./gradlew run`) and exercise the feature against the acceptance criteria. Check
  the fresh start-up log in `~/AngelsCareData/angels-care-startup.log`; check it is today's run,
  not an old one.
- Back up `~/AngelsCareData/angels-care.db` first, then check the feature against Finn's real
  database with `sqlite3`, especially the empty, zero and boundary cases.
- If packaging or start-up changed, also run `./gradlew jpackageImage` and launch
  `app/build/jpackage/AngelsCare.app`. Batch anything that needs a Windows test into one round.

## 3. Finish
- Set the spec to `Status: Implemented (YYYY-MM-DD)`, noting any AC still waiting on a manual or
  Windows check.
- Update README / CLAUDE.md only if a convention or command changed.
- Report, per acceptance criterion, what was verified vs. what is assumed. Commit or open a PR
  only when Finn asks.

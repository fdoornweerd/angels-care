# CLAUDE.md

## About this project
Desktop finance system for **Angels Care School** in Uganda. Current scope is **financial tracking
only**: revenue is student fees; expenses are teacher salaries, supplies, etc. School administration
(enrolment, attendance, …) comes later: do not build for it yet.

Used by 2–3 staff, each on their own Windows PC (such as HP Lightbook), who rarely edit the same records. Each PC has a
local SQLite database; syncing with a cloud database is planned (host not chosen yet). Developed
on macOS; the Windows installer is built by CI. `README.md` covers packaging and deployment.

## Stack
Java 21 · Spring Boot (no web server, just DI/config/transactions) · JavaFX 21 with FXML ·
SQLite via `JdbcTemplate` · Flyway migrations · JUnit 5 + AssertJ · Gradle (Kotlin DSL) ·
`org.beryx.runtime` + jpackage for the Windows installer. **Not JPMS**: there is no
`module-info.java`, on purpose (Spring Boot isn't modular). Don't add one back.

## Commands
```bash
./gradlew run          # run the app from source
./gradlew test         # all tests — run before claiming anything works
./gradlew test --tests '*FeeServiceTest'   # one class
./gradlew jpackageImage  # AngelsCare.app on the Mac — check packaging before a Windows round
./gradlew jpackage     # native installer for THIS OS only; Windows comes from CI
```

## Architecture
Package by feature under `org.angelscare.management`, layered inside each feature:

```
<feature>/
  ui/          FXML controllers (thin: bind, validate input, call a service)
  service/     business rules, @Transactional, no JavaFX imports
  repository/  JdbcTemplate SQL, one class per table/aggregate
  model/       immutable records
```
- FXML files live in `src/main/resources/fxml/<feature>/`. Controllers are Spring beans, loaded
  through the `FXMLLoader` controller factory (`context::getBean`).
- Dependencies point downward only: ui → service → repository. Services never touch JavaFX; that
  keeps them unit-testable without a UI thread.
- Shared code (money, clock, IDs) goes in `common/`; nothing feature-specific goes there.
- Page logic goes in view models under `<feature>/ui/` (plain Java + JavaFX properties), tested
  without a UI thread; FXML controllers only bind. Pages are opened through `shell/ui/Navigator`.

## Rules that must not be broken
**Money**
- Currency is UGX, **whole shillings only**. Amounts are `long` in Java, `INTEGER` in SQL.
  Never `double`/`float`, never `BigDecimal`, never decimal places in the UI.
- Use the `Ugx` value type in models and services, not raw `long`s.

**Schema (so that cloud sync is possible later)**
- Primary keys are UUID strings (`id TEXT PRIMARY KEY`), generated in Java. No autoincrement IDs.
- Every synced table has `created_at`, `updated_at` (UTC, ISO-8601 text) and `deleted_at`.
- Users may edit and delete, but **deletes are soft**: set `deleted_at`, never `DELETE FROM`.
  Every read query filters `deleted_at IS NULL`. Every write sets `updated_at`.
- Business dates (payment date, term dates) are `LocalDate`; timestamps are `Instant` in UTC.
  Display in Africa/Kampala time.
- Enums are stored as their `name()` in a `TEXT` column with a `CHECK (col IN (...))`.
- Write through `common/SyncedTable` (stamps, soft delete) and delete through
  `common/DeletionGuard` (blocks while referenced, found from foreign keys), so every table
  follows these rules the same way.
- Schema changes are **only** new Flyway migrations (`src/main/resources/db/migration/V<n>__*.sql`).
  Never edit a migration that has been committed to main; real data depends on it.

**Start-up robustness** (the app runs on machines we can't debug)
- `Diagnostics` writes `%USERPROFILE%\AngelsCareData\angels-care-startup.log` from the first line
  of `main`. Keep it that way; failures before the window appears must still be logged.
- Nothing on the start-up path may throw uncaught: a thrown exception means a blank screen with
  no message. Catch, log, and show the error in a window.
- All data lives in `~/AngelsCareData/`, outside the install directory. Don't move it.

## Workflow: two steps, spec then implement
Finn describes what he wants; every non-trivial feature then goes through two steps. Small fixes
can skip the spec.
1. **Spec** (`/spec`): read the description, ask clarifying questions until the behaviour is
   unambiguous, then write `docs/specs/NNN-name.md` (goal, behaviour, acceptance criteria `AC-1…`
   as Given/When/Then, open decisions, out of scope). **Stop for Finn's approval.**
2. **Implement** (`/implement NNN`): once Finn approves, build it, then verify: full
   `./gradlew test`, run the app and exercise the feature, including empty, zero and boundary
   cases. Say what was verified vs. assumed.

Finn's approval in chat is enough to start step 2; he doesn't need to type `/implement`.
Tests are not written first, but keep the suite green and add tests where they protect a business
rule.

Testing conventions:
- Repository tests run against a **real temp-file SQLite DB with Flyway applied**, not mocks.
- Service tests use real repositories on a temp DB, or hand-written fakes. Avoid Mockito for
  things we own.
- Inject `java.time.Clock` wherever time matters; tests use a fixed clock.

## Git
- One branch per spec: `feature/NNN-short-name`, merged by PR into `main`.
- Every push to `main` builds the Windows installer, so `main` must always be shippable.
- Commit only when asked. Don't commit `*.db` files.

## Gotchas
- Windows testing goes through a remote tester and takes **about a day per round**. Batch anything
  packaging-related into one build, and rely on the start-up log rather than live debugging.
- `jpackage` builds only for the OS it runs on; `--win-*` flags are applied only on Windows
  (see `app/build.gradle.kts`).
- SQLite: foreign keys are off unless enabled per connection (done in `SqliteDataSources`); the
  pool has a single connection, since SQLite allows only one writer at a time.
- Databases from before Flyway (Finn's Mac, the Windows PC) are baselined at version 0, so `V1`
  onwards runs on them too (`spring.flyway.baseline-*` in `application.properties`).
- JavaFX runs from the classpath, so the main class is `Launcher`, not `FxApp`; the "Unsupported
  JavaFX configuration" warning at start-up is expected.
- The Stop hook (`.claude/hooks/run-tests.sh`) runs the tests when a turn ends and sends failures
  back to Claude.

# 000: Project scaffold

**Status:** Implemented (2026-09-26); AC-6 awaiting the Windows test round · **Branch:** `feature/000-project-scaffold`

## Goal
Turn the "Hello, Angels Care" skeleton into the foundation that every finance feature builds on:
Spring Boot wired into JavaFX, Flyway-managed SQLite, the sync-ready schema conventions, the test
harness, and the Claude Code tooling. **No finance features.** The app still opens a single window,
which now shows the database status from the Spring context.

## Current state
- JPMS module + `org.beryx.jlink`; `Main`, `Database` (hand-rolled JDBC plus a test `students`
  table), `Diagnostics`.
- JUnit 4 and Guava declared, neither used. No tests.
- `app/angels-care.db` is committed to git (stray dev file).
- CI builds the installer only on pushes to `main`.

## Changes

### Build & packaging
- Remove `module-info.java`. Replace `org.beryx.jlink` with `org.beryx.runtime`, keeping the same
  `jpackage` options (`--win-shortcut`, `--win-menu`, per-user install…).
- Add: Spring Boot (current stable, pinned in `libs.versions.toml`), `spring-boot-starter-jdbc`,
  `flyway-core`, `sqlite-jdbc`, JavaFX `controls` + `fxml`.
- Tests: JUnit 5, AssertJ, `spring-boot-starter-test`. Remove JUnit 4 and Guava.
- Move all versions into `gradle/libs.versions.toml`.

### App bootstrap
- `Launcher.main` → `Diagnostics.startLogFile()` → `Application.launch(FxApp)`.
- `FxApp.init()` starts Spring (`WebApplicationType.NONE`, `headless(false)`); `start()` loads
  `main.fxml` using `context::getBean` as the controller factory; `stop()` closes the context.
- If Spring fails to start, `start()` shows an error window with `Diagnostics.describe(...)` and
  the log path instead of crashing. This keeps the no-blank-screen guarantee.

### Persistence
- DataSource: `jdbc:sqlite:~/AngelsCareData/angels-care.db`, Hikari `maximumPoolSize=1`,
  `connection-init-sql: PRAGMA foreign_keys = ON`, WAL journal mode.
- Keep the `org.sqlite.tmpdir` redirect from `Database`'s static block (as insurance; see
  its comment), set before the DataSource is created.
- `V1__baseline.sql`: an `app_meta` table (`key`, `value`) holding `schema_created_at` and a
  generated `device_id` (sync will need to know which machine made a change).
- Delete `Database.java` and the test `students` table.

### Common building blocks (`common/`)
- `Ugx`: a record wrapping a `long`, with `plus`/`minus`/`isNegative`, and `format()` giving
  `UGX 1,250,000`. Rejects nothing: negative values are valid (e.g. a balance).
- `Ids.newId()`: a UUID string.
- A `Clock` bean (system UTC), which tests override with a fixed clock.

### Tests
- A test base class that gives each test its own temp-file SQLite DB with Flyway applied.
- The acceptance criteria below, as tests.

### Claude Code tooling
- `.claude/skills/spec/SKILL.md`: interview Finn, write `docs/specs/NNN-name.md` from
  `docs/specs/TEMPLATE.md`, and leave it at `Draft`.
- `.claude/skills/tdd/SKILL.md`: refuse unless the spec is `Approved`; write failing tests per AC;
  run them and show the failures; **stop**; after approval, implement, refactor, and verify.
- `.claude/settings.json`: allow `./gradlew test|build|run|compileJava`, read-only `git`
  (`status`, `diff`, `log`, `show`, `branch`); Stop hook runs `./gradlew test -q` and feeds
  failures back to Claude.
- `docs/specs/TEMPLATE.md`.

### Housekeeping
- `git rm --cached app/angels-care.db`; add `*.db` to `.gitignore`.
- CI: also run `./gradlew test` on pull requests (no installer build), so PRs are checked before
  they reach `main`.
- Update the README's project layout section.

## Acceptance criteria
- **AC-1** Given a fresh data folder, when the app starts, then Flyway creates the schema and
  `app_meta` contains a `device_id`.
- **AC-2** Given an existing DB, when the app starts again, then `device_id` is unchanged and no
  migration re-runs.
- **AC-3** Given a DB connection, then `PRAGMA foreign_keys` returns 1.
- **AC-4** `Ugx.of(1250000).format()` is `UGX 1,250,000`; `Ugx.of(0)` is `UGX 0`; negatives format
  as `-UGX 5,000`.
- **AC-5** Given Spring fails to start (e.g. an unwritable DB path), then the app shows an error
  window naming the log file rather than a blank screen, and the log records the cause.
- **AC-6** On Windows (one remote test round), the CI-built installer creates shortcuts and the app
  opens showing "Database OK".

## Decisions (resolved at approval)
1. **Splash window**: skipped. Revisit only if the Windows test shows a noticeable start-up delay.
2. **Stop hook**: added now; restrict to "only when `src/` changed" once the suite exceeds ~15 s.
3. **Negative amounts** format as `-UGX 5,000` for now.

## Found during implementation
- **Pre-Flyway databases.** The old app left a DB with a `students` test table and no
  `flyway_schema_history` on every machine it ran on; Flyway refuses such a DB by default. Fixed
  with `baseline-on-migrate` at version 0 (so V1 still runs) and `DROP TABLE IF EXISTS students`
  in V1. Covered by `BootstrapTest.preFlywayDatabase`; verified on Finn's real DB.
- CI runs the tests on `windows-latest` (not Linux), for SQLite-on-Windows coverage on every PR.

## Out of scope
Any finance feature (fees, expenses, students), cloud sync and its host, login/users, TestFX UI
tests, code signing.

# 001: Income and expenses (setup data)

**Status:** Implemented (2026-09-26) · **Branch:** `feature/001-income-and-expenses`
<!-- Draft → Approved (date) → Implemented (date). Only Finn moves a spec to Approved. -->

## Goal
Give the backend everything the bookkeeper needs to *describe* the school's finances before any
money is recorded: the school calendar (years → 3 terms → 3 months), students, student groups,
user-defined income categories and items (fees), who pays which fee at what price, user-defined
expense categories and items, and monthly expense budgets. Nothing about the school's fees or
expenses is hard-coded. The user creates every category, item, group and price, so the school
can change its fee structure without a new release. Charges, payments and actual expenses build on
this in later specs.

No UI in this spec: services and repositories only, tested through the service layer.

## Current state
- Tables: `app_meta` only (V1). No finance tables.
- `common/`: `Ugx`, `Ids`, `Clock` bean. Test base `DatabaseTest` (temp SQLite + Flyway).
- Source: `docs/features/001-income-and-expenses.md`.

## Behaviour
All operations are service calls. Every validation failure throws a `ValidationException` whose
message can be shown to the user unchanged (e.g. *"An expense category named 'Feeding' already
exists."*).

**Names** (categories, items, groups, student names) are trimmed, must be non-blank and at most 100
characters. Names are unique, case-insensitively, among non-deleted rows in the same scope:
categories among categories of the same kind, items within their category, groups among groups.
A soft-deleted name can be reused.

### School calendar
1. The bookkeeper creates a **school year** by its calendar year (e.g. 2026) and gives the start and
   end dates of its **Term 1, Term 2 and Term 3**. All three terms are created together.
2. Validation: year unique; each term's start ≤ end; terms in order and non-overlapping
   (T1 ends before T2 starts, and so on); every date falls within that calendar year.
3. Term dates can be edited later, under the same validation.
4. Each term has **months 1, 2 and 3**. In this spec they are labels (`TermMonth` 1–3) with no
   dates of their own.
5. Terms are ordered across years: 2026 T3 comes before 2027 T1. Term ranges (below) use this
   order.

### Students
1. Create/edit a student with first name, last name, optional admission number, **class**,
   **residency** and **status**.
2. Class, level, residency and status are Java enums stored as `TEXT` (enum name) with a `CHECK`
   constraint:
   - `Level`: `NURSERY`, `PRIMARY`.
   - `SchoolClass`: `BABY`, `MIDDLE`, `TOP` (Nursery) and `P1` … `P7` (Primary), in that order.
     Each class knows its level; the level is **derived, not stored**, so it can't contradict
     the class.
   - `Residency`: `NATIONAL`, `REFUGEE`.
   - `StudentStatus`: `ACTIVE` (default), `LEFT`.
3. Admission number: optional free text; if given, unique (case-insensitive, trimmed) among
   non-deleted students.
4. List students (non-deleted), optionally filtered by class and/or status, sorted by last name,
   then first name.

### Student groups
1. Create/rename a group (name + optional description). No groups exist until the user makes
   them.
2. Add a student to a group **from a start term**, with an optional end term (inclusive). End ≥
   start. A student can't have two memberships of the same group with overlapping term ranges.
3. End a membership by setting its end term.
4. "Members of group G in term T" = students whose membership range contains T.

### Income categories, income items and fee assignments
1. Create/rename **income categories** (e.g. "Student Fees", "Donations") and **income items**
   within them (e.g. "Tuition", "Boarding", "Medical", "Uniform").
2. Assign an income item to a **target**: one student, one group, or one class. The
   **fee assignment** holds:
   - amount (`Ugx`, > 0),
   - frequency: `PER_TERM` or `PER_MONTH` (charged in each of the term's 3 months),
   - start term and optional end term (inclusive, end ≥ start).
   Prices change by ending the old assignment and starting a new one, which keeps history. Amount,
   frequency and end term may be edited (for correcting mistakes); target, item and start term may
   not.
3. **No overlaps:** an income item may reach a given student through only one assignment in any
   term. Creating or editing an assignment is rejected if, in some term of its range, a student
   would be covered by it *and* by another non-deleted assignment of the same item. A student is
   covered by:
   - a STUDENT assignment: if it names them;
   - a GROUP assignment: if they are a member of that group in that term;
   - a CLASS assignment: if their **current** class is that class.

   The same check runs, and rejects, when a student's class changes, when a membership is
   added, or when a membership's end term is extended. The error names the student, the item
   and the conflicting assignment. The check considers all non-deleted students, including LEFT
   ones.
4. **Fees for a student in a term** returns, for each covering assignment: item, category,
   amount, frequency, **term total** (`PER_TERM` → amount; `PER_MONTH` → amount × 3), and the
   route (student / group name / class). Empty for a LEFT student or when nothing applies.

### Expense categories, expense items and budgets
1. Create/rename **expense categories** (e.g. "Staff Salaries", "Administrative Costs") and
   **expense items** within them (e.g. "Airtime bundles", "Data bundles").
2. Set an expense item's **monthly budget for a school year** (`Ugx`, ≥ 0). One budget per item
   per year; setting it again updates it. An item without a budget for a year simply has none.
   This is not treated as zero.
3. List the budgets for a school year: category, item, monthly amount, sorted by category then
   item name.

### Deleting
All deletes are soft (`deleted_at`). A delete is **blocked** with a message saying what still uses
the row:

| Deleting…        | Blocked while it has non-deleted…                                    |
|------------------|----------------------------------------------------------------------|
| income category  | income items                                                         |
| income item      | fee assignments                                                      |
| expense category | expense items                                                        |
| expense item     | budgets                                                              |
| student group    | memberships or fee assignments                                       |
| student          | memberships or fee assignments                                       |
| school year      | memberships, fee assignments (via its terms) or budgets              |

Deleting a school year also soft-deletes its three terms, because they are part of the year.
Memberships, fee assignments and budgets can always be deleted.

## Changes

### Schema: `V2__income_and_expenses.sql`
Every table: `id TEXT PRIMARY KEY` (UUID from `Ids`), `created_at`, `updated_at`, `deleted_at`
(ISO-8601 UTC text). Dates are ISO `yyyy-MM-dd` text. Amounts are `INTEGER`. Enums are `TEXT` +
`CHECK (col IN (...))`. Uniqueness uses partial unique indexes `WHERE deleted_at IS NULL` on
`lower(name)`, with the service check giving the friendly message and the index as a backstop.

| Table              | Columns (besides the sync columns)                                                       |
|--------------------|-------------------------------------------------------------------------------------------|
| `school_year`      | `year INTEGER`                                                                            |
| `term`             | `school_year_id` FK, `number` (1–3), `start_date`, `end_date`                             |
| `student`          | `first_name`, `last_name`, `admission_no` NULL, `school_class`, `residency`, `status`     |
| `student_group`    | `name`, `description` NULL                                                                |
| `group_membership` | `group_id` FK, `student_id` FK, `start_term_id` FK, `end_term_id` FK NULL                 |
| `income_category`  | `name`                                                                                    |
| `income_item`      | `category_id` FK, `name`                                                                  |
| `fee_assignment`   | `income_item_id` FK, `target_type` (STUDENT/GROUP/CLASS), `student_id` NULL, `group_id` NULL, `school_class` NULL, `amount` (> 0), `frequency`, `start_term_id`, `end_term_id` NULL. A `CHECK` ensures exactly the column for `target_type` is set |
| `expense_category` | `name`                                                                                    |
| `expense_item`     | `category_id` FK, `name`                                                                  |
| `expense_budget`   | `expense_item_id` FK, `school_year_id` FK, `monthly_amount` (≥ 0); unique (item, year) among non-deleted |

### Code (package by feature, per CLAUDE.md)
- `calendar/`: `SchoolYear`, `Term`, `TermMonth`, `TermRange` (start, optional end; `contains`,
  `overlaps`), `CalendarService`, repositories.
- `student/`: enums above, `Student`, `StudentGroup`, `GroupMembership`, `StudentService`,
  `GroupService`, repositories.
- `income/`: `IncomeCategory`, `IncomeItem`, `FeeAssignment`, `FeeTarget` (sealed: `StudentTarget`,
  `GroupTarget`, `ClassTarget`), `BillingFrequency`, `ApplicableFee`, `IncomeCatalogService`,
  `FeeAssignmentService` (holds the overlap rule and "fees for student in term"), repositories.
- `expense/`: `ExpenseCategory`, `ExpenseItem`, `ExpenseBudget`, `ExpenseCatalogService`,
  `ExpenseBudgetService`, repositories.
- `common/` (generic only, per CLAUDE.md), reused by every feature above:
  - `ValidationException`: user-facing message.
  - `Names`: trim/non-blank/length validation.
  - `SyncedTable` (persistence helper): stamps `created_at`/`updated_at` from the `Clock`, does
    soft delete, and supplies the standard `deleted_at IS NULL` filter. Every repository uses it,
    so the sync rules live in one place instead of being repeated in each repository.
  - `UsageCheck`: small helper for the "blocked while in use" rule, so each service declares
    *what* blocks a delete rather than re-implementing the check.
- Class ↔ student and membership changes call back into the overlap check through an interface
  owned by `income/` (e.g. `FeeCoverageGuard`), so `student/` doesn't depend on `income/`
  internals.

### Other
- CLAUDE.md: add "enums are stored as their `name()` in a `TEXT` column with a `CHECK`" to the
  schema rules.

## Acceptance criteria
Service-level tests on a temp DB (`DatabaseTest`) with a fixed `Clock`.

**Schema**
- **AC-1** Given a database at V1 (including the baselined pre-Flyway case), when the app
  migrates, then all V2 tables exist and existing `app_meta` rows are unchanged.
- **AC-2** Given any row created by these services, then `id` is a UUID, `created_at` =
  `updated_at` = the fixed clock time and `deleted_at` is null; after an edit, `updated_at` moves
  to the new clock time and `created_at` stays the same.

**Names (checked for every named entity)**
- **AC-3** Given a blank or whitespace-only name, or one longer than 100 characters, when it is
  saved, then a `ValidationException` is thrown; a 100-character name is accepted and
  surrounding spaces are trimmed.
- **AC-4** Given an expense category "Feeding", when another called " feeding " is created, then
  it is rejected; after "Feeding" is deleted, a new "Feeding" is accepted.
- **AC-5** Given item "Airtime" in category A, when "Airtime" is created in category B, then it is
  accepted (uniqueness is per category).

**Calendar**
- **AC-6** Given valid dates, when school year 2026 is created, then it has exactly Terms 1–3 with
  those dates.
- **AC-7** Given year 2026 exists, when 2026 is created again, then it is rejected.
- **AC-8** Given term dates where a start is after its end, terms overlap or are out of order,
  or a date falls outside the calendar year, when the year is created or a term is edited, then
  it is rejected. Adjacent terms (T1 ends the day before T2 starts) are accepted.
- **AC-9** Given years 2026 and 2027, then 2026 T3 is ordered before 2027 T1, and a `TermRange`
  with no end contains every later term.

**Students and groups**
- **AC-10** Given a student created in class `P3`, then their level is `PRIMARY`; in `TOP`, it
  is `NURSERY`. A new student's status is `ACTIVE`.
- **AC-11** Given a student with admission number "A-12", when another is saved with " a-12 ",
  then it is rejected; two students without an admission number are both accepted.
- **AC-12** Given no students, then listing returns an empty list; given students, filtering by
  class and status returns only matches, sorted by last then first name.
- **AC-13** Given a membership from 2026 T1 to 2026 T2, then the student is a member in T1 and T2
  but not in T3; a membership whose end is before its start is rejected; a second overlapping
  membership of the same group is rejected, and a non-overlapping one is accepted.

**Fees**
- **AC-14** Given an income item, when it is assigned with amount 0 or a negative amount, then it
  is rejected; with 1 it is accepted.
- **AC-15** Given no school year exists, when a fee is assigned or a membership created, then it
  is rejected with a message saying a school year must be set up first.
- **AC-16** Given Tuition assigned to class `P7` from 2026 T1, when Tuition is assigned to a `P7`
  student (or a group containing one) for an overlapping range, then it is rejected, naming the
  student. Given the class assignment ends at 2026 T1, a student assignment from 2026 T2 is
  accepted.
- **AC-17** Given Tuition assigned to group Boarders and to class `P5`, when a `P5` student joins
  Boarders for an overlapping range, or a Boarders member is moved to `P5`, then it is rejected
  and nothing is saved.
- **AC-18** Given Tuition assigned to class `P7` and a different item, Boarding, assigned to a P7
  student, then both are accepted (overlap is per item).
- **AC-19** Given an assignment of 100,000 `PER_MONTH` and another of 250,000 `PER_TERM` covering
  a student in a term, then fees for that student and term list both, with term totals 300,000
  and 250,000 and the route of each.
- **AC-20** Given a student with no covering assignments, or a `LEFT` student, then fees for them
  in any term are an empty list.
- **AC-21** Given an assignment's amount is edited, then the new amount is returned and
  `updated_at` changes; when its end term is extended into a range that would overlap, then the
  edit is rejected.

**Expenses**
- **AC-22** Given an expense item, when its 2026 monthly budget is set to 0, then it is stored
  as 0; a negative budget is rejected; setting it again replaces the value without adding a
  second row.
- **AC-23** Given a school year with no budgets, then listing its budgets returns an empty list;
  items without a budget are not listed as zero.

**Deleting**
- **AC-24** For each row of the *Deleting* table: given the blocking child exists, when the
  parent is deleted, then it is rejected with a message naming what uses it; after the child is
  deleted, the parent's delete succeeds and sets `deleted_at`.
- **AC-25** Given any deleted row, then it is excluded from every list, lookup, overlap check and
  fee calculation, and no SQL `DELETE` statement was issued (the row is still in the table).
- **AC-26** Given a school year with no dependants, when it is deleted, then its three terms are
  soft-deleted too.

## Decisions (resolved at approval)
1. **Discounts and bursaries** are not possible through fee assignments, because overlaps are
   rejected. Spec 002 adds per-student **adjustments** (discount/waiver against a charge); the
   overlap rule stays strict.
2. **Class history**: a student's class is stored as current only. "Fees for student in a past
   term" uses the current class; spec 002 fixes history by snapshotting charges per term when
   they are raised.
3. **LEFT students** are included in overlap checks (simpler rule; no surprise conflict if a
   student returns).
4. **Annual budget total**: not computed in 001; decided in the actual-expenses spec (003).

## Found during implementation
- **Terms are passed as `TermRef` ("2026, Term 2"), not as ids.** Services resolve them through
  `CalendarService.requireTerm`, which gives the AC-15 message ("No school year has been set up
  yet…" / "School year 2027 has not been set up yet."). Storage still uses `term.id` foreign keys.
- **`StudentChangeGuard` is declared in `student/`** and implemented by `income/FeeCoverageGuard`
  (not an interface owned by `income/`, as the Code section first said), so `student/` has no
  dependency on `income/` at all.
- **`common/DeletionGuard` replaces the planned `UsageCheck`.** It reads the referring tables from
  SQLite's foreign keys (`pragma_foreign_key_list`), so every new table with a foreign key is
  protected from deletion automatically. A school year checks its terms, then ignores `term` for
  itself, because the terms are part of the year.
- **`common/SyncedTable`** does inserts, updates, soft deletes and case-insensitive name clash
  lookups for a table. Timestamps are stored at millisecond precision, like V1.
- **Income and expense catalogs are separate, as decided**, but have the same shape (the expense
  classes started as copies of the income ones). If a third catalog appears, extract a shared
  base rather than a third copy.
- **`FinanceWiringTest`** runs the services through the real Spring context. The other tests wire
  them by hand, so this is what covers the bean wiring and the `@Transactional` proxies on
  SQLite.
- **Verified on Finn's real database** (backed up first to
  `~/AngelsCareData/angels-care.db.backup-before-001-20260926-183338`): V2 applied, `app_meta`
  unchanged, all 11 tables empty, `foreign_key_check` and `integrity_check` clean. The packaged
  Mac app (`jpackageImage`) starts on the migrated database. No Windows round has run yet; V2 will
  run on the Windows PC with the next installer.

## Out of scope
Any UI or FXML; student photos; charges, payments and balances (spec 002); recording actual
expenses and budget-vs-actual (spec 003); discounts/adjustments; bulk promotion of classes
between years; dates for term months; reports and exports; user-editable levels/classes (they
are enums); cloud sync; users and permissions.

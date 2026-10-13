# 004: Student amounts typed in, no class Day/Boarding fees

**Status:** Implemented (2026-10-12); AC-12 checked on the Mac from screenshots of the real page driven by a program (not clicked by hand), AC-13 awaiting the Windows test round · **Branch:** `feature/004-update-students`
<!-- Draft → Approved (date) → Implemented (date). Only Finn moves a spec to Approved. -->

## Goal
On page 5, the bookkeeper types each student's fee (Amount) when adding them, instead of the
school keeping a Day fee and a Boarding fee per class. The class headings lose those two fees and
keep only Ream, which defaults to UGX 5,000. Day/Boarding stays as a column, but it no longer
changes what a student owes. The source is `docs/features/004-update-students.md`.

## Current state
- **Class fees** (spec 002, extended in 003, `class_fee`): each class heading on page 5 has
  **Day fee**, **Boarding fee** and **Ream**, carried forward from the term they are set in. A fee
  never set is 0.
- **A student's Amount** (`student_term.amount`) is null unless typed. While null, it is the
  class's Day or Boarding fee in force, depending on the line's Day/Boarding. Clearing the cell
  goes back to the class fee. Switching Day/Boarding changes the Amount unless one was typed.
- **Ream** works the same way from the class's Ream, and is the same for Day and Boarding.
- **Add student** asks for names, National/Refugee and Day/Boarding.
- **Finn's real database** (checked with `sqlite3` on 2026-10-12): 50 live lines, of which 43
  take their Amount from a class fee and 50 take their Ream from one. Every class has fees from
  2026-2027 Term 1 (nursery Ream 5,000, primary 10,000), and P7 has a new Day fee in Term 2. The
  2025-2026 Term 1 lines are earlier than any fee, so their Amount and Ream are 0.

## Behaviour

### Class headings
1. Each class heading on page 5 shows only **Ream (UGX)**, editable and carried forward as now
   (once set in a term, it applies to that term and every later term until changed). The Day fee
   and Boarding fee fields are gone.
2. A class whose Ream was **never set** gives **5,000** (before: 0).

### Amount
3. **Add student** gets an **Amount (UGX)** field, after Day/Boarding. It is required: blank is
   refused with *"Type the amount the student pays (0 for none)."*, and nothing is added. 0 is
   allowed. It follows the usual money rules: whole UGX, not negative, commas allowed. A refused
   add keeps what was typed.
4. A student's Amount is **their own on each term**. It is stored on the line and changed only
   by typing in the Amount cell, which changes that term only.
5. **Clearing the Amount cell is refused** with the same message as (3), and the cell keeps its
   amount.
6. **When a student is put on another term** (opening a term's page 5, *Marking Active* again,
   or moving Joined earlier), the new line's Amount is **copied once** from the nearest term the
   student is on: the latest earlier one, or, if there is none, the earliest later one. It is 0
   if they are on no other term (only students from before 003 who were never on a term). After
   that it is independent: correcting the earlier term doesn't change it.
7. **Day/Boarding** stays as a column and as a choice in Add student, recorded per term as in
   003. It **no longer changes the Amount**.

### Existing data
8. **Nothing a term already shows changes.** A line whose Amount came from a class fee keeps
   that amount, now stored on the line. A line whose Ream came from "never set" (0) keeps 0. Its
   totals, balances, the Debt carried into later terms and the page-2 Students rows stay the same.
9. Lines whose Ream comes from a class Ream that was set keep following it, as now. Changing a
   class's Ream on a term still changes that term and later terms only.

## Changes
- **Schema: `V5__typed_amounts.sql`.** No committed migration is edited.
  - For each live `student_term` line with a null `amount`, set `amount` to the class fee in
    force for its class and term (`boarding_amount` for Boarding lines, else `amount`), or 0 if
    no fee is in force.
  - For each live line with a null `ream` and **no class fee in force**, set `ream` to 0, so it
    doesn't change to the new 5,000 default.
  - Changed rows get a new `updated_at`, as in V3 and V4.
  - `class_fee.amount` and `class_fee.boarding_amount` **stay as columns but are no longer read
    or written** (new rows store 0). Dropping columns isn't worth the risk to sync and to the
    baselined databases. `student_term.amount` stays nullable in the schema; the service always
    sets it.
  - **Back up the real database before V5 runs on it**, as for V3 and V4.
- **Model:** `ClassFee` keeps only the class and the Ream (`feeFor` and the Day/Boarding fees
  go).
- **Service (`StudentAccountService`, `Register`):**
  - `setClassFee` becomes `setClassReam(schoolClass, term, ream)`;
  - `Register.classFee` defaults the Ream to 5,000;
  - a line's Amount is the stored one (a null left over is shown as 0);
  - `setAmount` refuses null;
  - `addStudent` takes the Amount, which is required;
  - `openTerm` and `editStudent` copy the Amount from the nearest line when they add a line;
  - `setBoarding` no longer affects the Amount.
- **UI:**
  - `StudentsViewModel` loses `editClassFee` and `editClassBoardingFee`;
  - `addStudent` takes the Amount text;
  - the class heading shows Ream only;
  - the Add student row gets the Amount field.
- **Tests:** the earlier tests that set Day/Boarding fees are rewritten to type amounts. The
  003 behaviour they test still holds.
- **Docs:** spec 003 gets a "Changed later" note: Day/Boarding fees are removed by 004.

## Acceptance criteria
Service and view-model tests run on a temp DB with a fixed clock (today = 2 March 2026). The year
2026-2027 has T1, T2 and T3. **(by eye)** criteria are checked by running the app.

**Migration**
- **AC-1** Given a V4 database with:
  - P7 fees set in T1 (Day 300,000, Boarding 500,000, Ream 10,000);
  - P7's Day fee changed in T2 to 320,000;
  - P3 with no fees;
  - a Day and a Boarding P7 student on T1 and T2;
  - one P7 line with a typed Amount of 250,000;
  - a P3 student on T1;
  - a deleted line;

  when it migrates to V5, then:
  - the P7 Day student's Amounts are 300,000 (T1) and 320,000 (T2);
  - the Boarding student's are 500,000 and 500,000;
  - the typed 250,000 is unchanged;
  - the P3 line has Amount 0 and Ream 0 stored;
  - the P7 lines' Ream stays null and resolves to 10,000;
  - the deleted line is untouched;
  - only changed rows have a new `updated_at`;
  - every line's Total, Balance and Debt, and each term's Students totals, equal what they were
    before.

**Class headings and Ream**
- **AC-2** Given P7's Ream set to 10,000 in T1, then T1, T2 and 2027-2028 T1 give 10,000. Setting
  it to 12,000 in T3 changes T3 onwards only. A class whose Ream was never set gives 5,000 to a
  student added after V5.
- **AC-3** Given the page-5 view model, then each class section's fee exposes only the Ream, and
  there is no way to set a Day or Boarding fee (`editClassFee` and `editClassBoardingFee` are
  gone; checked by compiling).

**Amount**
- **AC-4** Given *Add student* in P3 on T1 with Amount `250,000`, then the student is on T1 with
  Amount 250,000, Day unless Boarding is chosen. With Amount `0`, they are added with 0.
- **AC-5** Given *Add student* with Amount blank, `-5`, `12.5` or `abc`, then:
  - no student is created;
  - the message says what's wrong (blank: *"Type the amount the student pays (0 for none)."*);
  - the add row keeps what was typed (view model returns false).
- **AC-6** Given a student on T1 with Amount 250,000, when the Amount cell is cleared, then it is
  refused with the message from AC-5, and the Amount stays 250,000.
- **AC-7** Given Ann, added on T1 with 250,000, when T2's page 5 is opened, then her T2 Amount is
  250,000. Changing T1's Amount to 270,000 afterwards leaves T2 at 250,000. Changing T2's to
  300,000 leaves T1 as it was.
- **AC-8** Given Cara, added on T3 with 300,000, when her Joined is moved to T1 and T1 is opened,
  then her T1 Amount is 300,000 (copied from the earliest later term). Given a student from before
  003 who is on no term, when a term is opened, then they are added with Amount 0.
- **AC-9** Given Ben, Left after T1 (Amount 200,000) and taken off T2, when he's marked Active
  while viewing T2, then his T2 Amount is 200,000.
- **AC-10** Given a Day student on T1 and T2 with Amount 250,000, when they're switched to
  Boarding on T2, then T2 is Boarding with Amount still 250,000, and T1 is unchanged.

**Earlier terms**
- **AC-11** Given T1 with two students, typed amounts, payments and a class Ream, and T1's lines,
  totals and page-2 Students row noted, when on T2 and T3:
  - a class Ream is changed;
  - a student is added;
  - a student is switched to Boarding;
  - an Amount is typed and a clear is refused;
  - a student is marked Left and then Active again;

  then T1's lines, totals and page-2 Students row are unchanged.

**By eye**
- **AC-12 (by eye, Mac)** On page 5 at 1366×768:
  - each class heading shows only Ream;
  - the Add student row fits with the Amount field;
  - adding with an amount shows it at once in the student's line, the class totals row and the
    page totals.

  On a migrated copy of Finn's database, every term shows the same totals as before V5.
- **AC-13 (by eye, Windows)** In the next test round, V5 migrates the school's data, the totals
  are as before, and AC-12 holds.

## Decisions (from the interview)
1. **Existing lines keep their current Amount**, written onto the line by V5 (no term changes).
2. **A new term copies the Amount from the student's nearest term**, once; afterwards each term
   is independent.
3. **Add student's Amount is required; 0 is allowed.**
4. **Ream stays a class heading field, carried forward; never set = 5,000.** Lines that today get
   0 because no Ream was ever set keep 0 (written by V5), so earlier terms don't change.
5. **Clearing an Amount cell is refused.**
6. **The class fee columns stay in the database, unused** (proposed in the draft, approved).
7. **Lines with Ream 0 from "never set" get 0 written by V5**, and a student on no other term
   gets Amount 0 (proposed in the draft, approved).

## Found during implementation
- **`StudentTermLine.amountOverridden` is gone.** Every amount is now the student's own, so the
  flag meant nothing.
- **The old API is removed, not kept beside the new one:** `setClassFee` (now `setClassReam`),
  the `addStudent` overloads without an amount, and the view model's `editClassFee` and
  `editClassBoardingFee`. Tests that used them now type amounts; five tests of behaviour 004
  removes were deleted (003's three Day/Boarding fee tests and "typed amount stays", and the
  page-5 Boarding fee heading test). Count: 309 tests during red, 304 now, all passing.
- **003's V3 → V4 migration test now migrates to V4 only**, since it compares whole
  `student_term` rows and V5 writes amounts into them (as 002's test did for V4).
- **A refused amount in Add student is shown as** *"Amount: Enter a whole amount in shillings,
  e.g. 300,000."*, so the bookkeeper knows which field is wrong. Blank shows AC-5's message.
- **Verified on Finn's real data** (backup:
  `~/AngelsCareData/angels-care.db.backup-before-004-20261012-200743`):
  - with a throwaway program (not committed), every line and total of all six terms was printed
    from one copy of the database by `main`'s code (V4) and from another by this branch (after
    V5): **identical**, 50 lines;
  - `./gradlew run` applied V5 to the real database and showed the window (start-up log of
    2026-10-12 20:09). All 50 live lines have a stored amount; 16 lines (2025-2026 Term 1, no
    class fee in force) got Ream 0; 34 still follow their class Ream; integrity and foreign-key
    checks are clean.
- **By eye (AC-12, Mac only)**, a throwaway program drove the real page 5 at 1366×728 on a copy
  of the real database and saved screenshots:
  - each class heading shows only Ream;
  - the add row (with Amount) is 775 px wide in a 1,314 px panel;
  - blank and `12.5` were refused with their messages and kept the typed names;
  - adding "Test Amount" as Boarding with 275,000 showed it on P7's line and in the class
    totals row, and moved the page totals from 5,062,000 to 5,347,000.

## Out of scope
- A per-student "standard fee" stored on the student (the nearest term's Amount stands in for it).
- An "apply this amount to everyone in the class" option.
- Ream that differs for Day and Boarding, or Ream typed per student by default.
- Dropping the unused `class_fee` columns.
- Bulk-changing amounts for a new school year.
- Repairing the 2025-2026 Term 1 lines that 002's bug added (still as decided in 003).

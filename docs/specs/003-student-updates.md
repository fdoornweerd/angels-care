# 003: Student updates

**Status:** Implemented (2026-10-03); AC-17 checked by eye on the Mac from screenshots (cells driven by a program, not clicked by hand), AC-18 awaiting the Windows test round · **Branch:** `feature/003-student-updates`
<!-- Draft → Approved (date) → Implemented (date). Only Finn moves a spec to Approved. -->

## Goal
Make the Students page (page 5) match how the school charges and how its register changes over
time:
- students are **Day** or **Boarding**, and each class has a Day fee and a Boarding fee;
- a student can be **edited**: their names, National/Refugee, the term they joined, and marking
  them **Left**;
- **earlier terms stay as they were.** A term lists only the students who were there, and nothing
  done in a later term changes an earlier one.

It also lets an item be added on pages 3/4 with its quantity and rate in one step, and fixes the
class totals row on page 5, which lags one edit behind. The source is
`docs/features/003-student-updates.md`. Its third point (a base fee per class that each student
can override) was already built in 002.

## Current state
- **Students** (spec 001, `student`): names, class, National/Refugee, Active/Left. Nothing records
  when a student joined or left, and there is no screen to edit a student or mark them Left. The
  "Show students who left" switch on page 5 therefore never shows anyone.
- **Page 5** (spec 002, `student_term`, `class_fee`): one line per student per term, with the
  class recorded per term. Each class has one fee and one ream charge, carried forward from the
  term they're set in. A student's Amount and Ream default to these and can be overridden. Debt
  carries the balance of their previous line.
- **Pages 3/4:** *Add item* asks for a name and a unit only.
- **Found while writing this spec**, verified on a scratch database with a throwaway harness that
  drives the real pages (not committed):
  - **Opening page 5 for an earlier term adds students who joined later.** `openTerm` puts every
    Active student who was never on the term onto it. A student added in Term 3 lands on Term 1
    when Term 1's page 5 is viewed. Term 1 then shows them owing the class fee, and their Term 3
    Debt goes from 0 to 310,000.
  - **Page 5's class totals row lags one edit behind.** `StudentsViewModel.replace` changes the
    student's line before it recalculates the class totals. The controller rebuilds the totals row
    when the line changes, so the row shows the totals from before the edit. The student's own
    Total/Balance, the page totals at the top, page 3/4's totals and page 2 after Back all update
    correctly with Enter, Tab and clicking elsewhere, including clicking empty page space.
  - **Page 5's totals skip hidden Left students; page 2's Students row includes them**, so the two
    would disagree once students can be marked Left.

## Behaviour

### Day and Boarding
1. Every student is **Day** or **Boarding**. Page 5 has a **Day/Boarding** column after Name: a
   drop-down on each student's line, blank on the totals rows.
2. **Add student** gets a Day/Boarding choice next to National/Refugee. Day is the default.
3. Each class heading on page 5 shows **Day fee**, **Boarding fee** and **Ream**, all editable.
   As in 002, they carry forward: once set in a term, they apply to that term and every later term
   (into later years too) until changed in a later term. A fee never set is 0.
4. A student's **Amount** defaults to their class's Day fee or Boarding fee for the term,
   matching their Day/Boarding on that term. As before, it can be typed over, and clearing it goes
   back to the class fee. Ream is the same for Day and Boarding.
5. Day/Boarding is **recorded per term**, like class. Changing it on a term's page 5 changes that
   term and any later terms the student is already on. It also becomes the student's own value,
   which is used when they are put on further terms. **Earlier terms keep what they had.** An
   Amount typed in for the student stays when they switch.

### Who is on a term
6. Every student records the term they **joined**. *Add student* sets it to the term being
   viewed, even if that term is in the past (for entering history).
7. A Left student also records their **last term** (see 12).
8. Opening a term's page 5 adds a student only if they **belong to that term**: they joined in it
   or before, and they have no last term or it is that term or later. They must also never have
   been on the term (as in 002, *Remove from this term* sticks). Students who joined later, or
   left earlier, are never added. Earlier terms therefore show only the students who were there.
9. A student with no joined term (only possible for students from before this change who were on
   no term) is added to the first term whose page 5 is opened, and that term becomes their joined
   term.

### Edit student
10. Each student's line on page 5 has an **Edit** button. It opens a dialog with:
    - **First name** and **Last name**: 001's rules (trimmed, required, at most 100 characters);
    - **National / Refugee**;
    - **Joined**: a list of every term ("2026-2027 Term 1");
    - **Active / Left**.

    **Save** applies everything at once, or nothing if anything is refused, and shows the reason
    in the dialog. **Cancel** changes nothing. Class is not edited here: a wrong class is fixed
    with Remove + Add, as now. Day/Boarding is changed in its column.
11. **Changing Joined:**
    - **To an earlier term:** the student is added to the earlier terms when their page 5 is
      opened. This is how history is entered for students first typed in during a later term.
      Their Debt then carries from the earliest of those lines as usual.
    - **To a later term:** they're taken off the terms before it, if they have no payments there.
      If they have payments in any term before it, the change is refused, naming the term.
    - Joined can't be after a Left student's last term.
12. **Marking Left** while viewing term T makes T their **last term**:
    - they stay on T and every earlier term, unchanged;
    - on T their name shows as *"Ben Okello (Left)"*;
    - they are taken off any later term they're already on that has no payments, and stay on, tagged
      Left, any later term that has payments;
    - they're never added to terms after T.
13. **Marking Active again** while viewing term V clears their last term and puts them on V if
    they're not on it, under their current class and Day/Boarding. Later terms add them as usual.
14. **Left students are always listed** on the terms they're on, and count in every total, so page
    5's totals always equal page 2's Students row. The **"Show students who left" switch is
    removed** (a change to 002, recorded there under "Changed later").

### Earlier terms are not changed
15. Nothing done while viewing a term changes the lines, totals or page-2 figures of an **earlier**
    term. That covers opening a term, adding a student, changing class fees, Day/Boarding, an
    Amount, Debt, Ream, payment or remark, marking Left or Active, and removing a student from a
    term. Two things are not tied to a term:
    - names and National/Refugee belong to the student, so a corrected name shows on every term;
    - changing **Joined** deliberately adds the student to earlier terms (see 11).

    Changes still flow **forward**, as in 002: a payment in Term 1 changes the Debt carried into
    Term 2, and a class fee carries into later terms until a term with its own fee.

### Add item with quantity and rate (pages 3 and 4)
16. The *Add item* row has **Item**, **Unit**, **Quantity** (optional), **Rate** (optional) and
    the **Add item** button. Quantity and rate go into the **selected month**; the other months
    stay blank. The item, as before, belongs to every term and month of the year.
17. Quantity and rate follow 002's rules: up to 2 decimals for quantity, whole non-negative UGX for
    rate. If either can't be read, **nothing is created**: the field turns red, the message under
    the tables names it, and the typed text stays. With only one of them filled in, that one is
    stored and the amount stays blank, as when typing into the row.

### Totals follow every edit
18. On page 5, after any edit (a student's Amount, Debt, Ream, payment, Day/Boarding; a class fee;
    adding, editing or removing a student), the student's Total and Balance, the class totals row
    and the page totals all show the new figures as soon as the cell is left. Pages 2–4 already
    do; they keep doing so.

## Changes
- **Schema: `V4__student_updates.sql`.** No committed migration is edited (V3 has run on real
  data).
  - `student`:
    - `boarding TEXT NOT NULL DEFAULT 'DAY' CHECK (boarding IN ('DAY', 'BOARDING'))`;
    - `joined_term_id TEXT REFERENCES term (id)`, set to the term of the student's earliest live
      `student_term` line, or left null if they have none;
    - `left_term_id TEXT REFERENCES term (id)`, set for students already `LEFT` to the term of
      their latest live line, otherwise null.

    The service keeps `status = 'LEFT'` exactly when `left_term_id` is set.
  - `student_term`: `boarding` with the same type, default and CHECK. Every existing line is Day.
  - `class_fee`: `boarding_amount INTEGER NOT NULL DEFAULT 0 CHECK (boarding_amount >= 0)`. The
    existing `amount` is the Day fee.
  - Rows given a joined or last term get a new `updated_at`, as in V3. Columns filled only by their
    default don't, because every device gets the same value.
  - Existing lines that the old behaviour put on earlier terms can't be told apart, so they are not
    repaired. They can be removed with *Remove from this term* (when without payments) and the
    joined term corrected in Edit student. **Back up the real database before V4 runs on it**, as
    for V3.
- **Model:**
  - `Boarding` enum (`DAY`, `BOARDING`) in `student/model`;
  - `Student` and `StudentTermLine` carry it, plus the joined and last terms;
  - `ClassFee` gets the Boarding fee.
- **Service (`StudentAccountService`, `Register`):**
  - `openTerm` adds only students who belong to the term (8, 9);
  - the Amount default picks the Day or Boarding fee;
  - `setClassFee` takes the Boarding fee;
  - `setBoarding(studentId, term, boarding)`;
  - `addStudent` takes Boarding and sets the joined term;
  - `editStudent(studentId, term, details)` covers 10–13 in one transaction;
  - `lines(term)` loses the `includeLeft` flag.
- **Catalogue / ledger:** one `@Transactional` call that creates an item and records its quantity
  and rate for a term and month. Quantity and rate are parsed before anything is written.
- **UI:**
  - `StudentsViewModel` builds each class table's rows, **totals row included**, and updates the
    totals before it publishes the rows. `StudentsController` only binds them. The `showLeft`
    property, the check box and their tests go.
  - New: the Day/Boarding column, the class heading's Boarding fee, Add student's choice, the
    Edit button, and an edit-student dialog whose logic is in a view model, tested without a UI
    thread.
  - `DetailViewModel.addItem` takes quantity and rate; the add row gets the two fields.
- **Docs:** spec 002 gets a "Changed later" section: the switch is removed, who is added when a
  term is opened, and Left students are listed and counted.

## Acceptance criteria
Service and view-model tests run on a temp DB with a fixed clock (today = 2 March 2026). The year
2026-2027 has T1 (Feb–Apr), T2 (May–Aug) and T3 (Sep–Dec). **(by eye)** criteria are checked by
running the app.

**Migration**
- **AC-1** Given a V3 database with students on T1 and T3, a student on no term, a student with
  status LEFT, and P7 and P3 class fees, when it migrates to V4, then:
  - every student and line is Day;
  - each class fee's amount is its Day fee, with a Boarding fee of 0;
  - joined terms are the earliest term each student is on (null for the student on no term);
  - the LEFT student's last term is the latest term they're on;
  - only rows given a joined or last term have a new `updated_at`;
  - the new columns refuse values outside their CHECKs;
  - every other value is unchanged.

**Day and Boarding**
- **AC-2** Given P7's Day fee 300,000, Boarding fee 500,000 and Ream 10,000, set in T1:
  - a Day student's Amount is 300,000 and a Boarding student's is 500,000, in T1, T2 and 2027-2028
    T1;
  - both have Ream 10,000;
  - setting the Boarding fee to 550,000 in T3 changes T3 onwards only, and leaves the Day fee and
    Ream as they were;
  - a class with no Boarding fee ever set gives Boarding students 0.
- **AC-3** Given *Add student* in P3 with Boarding chosen, then the student is Boarding, on this
  term as Boarding, with the Boarding fee. With no choice made, they are Day.
- **AC-4** Given a Day student on T1, T2 and T3, when they're switched to Boarding on T2's page 5:
  - T2 and T3 are Boarding, with the Boarding fee;
  - T1 stays Day, and its Amount, Total and Balance are unchanged;
  - the next term they're added to is Boarding.

  A student with an Amount typed in keeps it after switching.

**Who is on a term**
- **AC-5** Given Ann, who joined T1, and Cara, added on T3's page 5:
  - opening T1 lists Ann only;
  - Cara's T3 Debt stays 0;
  - opening T2 adds Ann and not Cara.
- **AC-6** Given a student from before V4 who is on no term, when T2's page 5 is opened, then they
  are added to T2 and T2 becomes their joined term. Opening T1 afterwards doesn't add them.
- **AC-7** Given Cara, who joined T3:
  - changing Joined to T1 makes opening T1 and T2 add her;
  - her T3 Debt is then carried from T2.

  Given Dan, who joined T1 and is on T1 (no payments) and T2 (a payment):
  - changing Joined to T2 takes him off T1;
  - changing it to T3 is refused with a message naming T2, and nothing changes.

  Joined after a Left student's last term is refused.
- **AC-8** Given Ben, who joined T1 and is on T1, T2 (no payments) and T3 (a payment), when he's
  marked Left while viewing T1, then:
  - T1's lines, class and page totals and page-2 Students row are unchanged;
  - T1 shows "Ben Okello (Left)";
  - he is off T2;
  - he stays on T3, tagged Left;
  - opening 2027-2028 T1 doesn't add him.
- **AC-9** Given Ben, Left after T1 and taken off T2, when he's marked Active while viewing T2,
  then he is on T2 again under his current class and Day/Boarding, T1 no longer shows the tag, and
  2027-2028 T1 adds him when opened.
- **AC-10** Given a term with two Active students and one Left student (with a balance), then page
  5 lists all three, its class and page totals include the Left student, and its page totals equal
  page 2's Students row (Expected and each month). The view model has no "show students who left"
  property.
- **AC-11** Given T1 with class fees, two students, payments and a typed Debt, and T1's lines,
  totals and page-2 Students row noted, when on T2 and T3:
  - each class fee (Day, Boarding, Ream) is changed;
  - a student is added;
  - a student is switched to Boarding;
  - a student's Amount, Debt, Ream, payment and remark are changed;
  - a student is removed from T3;
  - a student is marked Left and then Active again;

  then T1's lines, totals and page-2 Students row are unchanged.

**Edit student**
- **AC-12** Given Ben (P5, National), when Edit student saves first name " Benjamin ", last name
  "Okello" and Refugee, then he is Benjamin Okello, Refugee, on every term he's on. An empty first
  or last name is refused with 001's message (*"First name can't be empty."*), and nothing in that
  save is applied. Cancel changes nothing.

**Add item with quantity and rate**
- **AC-13** Given page 3 on month 2, when "Maize", "kg", quantity `12.5` and rate `3,500` are
  added to Feeding, then:
  - Maize exists in every term and month of the year;
  - its month-2 entry is 12.5 × 3,500 = 43,750, and months 1 and 3 are blank;
  - Feeding's month and term totals and page 2's Feeding row include 43,750.

  With quantity and rate blank, the item is added blank, as before. With only a quantity, the
  quantity is stored and the amount is blank. The same holds on page 4.
- **AC-14** Given an invalid quantity (`-1`, `1.234`, `abc`) or rate (`-5`, `12.5`, `abc`), when
  Add item is pressed, then no item and no entry are created, the message names the quantity or
  rate, and the add row keeps what was typed.

**Totals follow edits**
- **AC-15** Given P7 with Ann and Ben, when a payment, Amount, Debt, Ream or Day/Boarding is
  changed for Ann, then by the time P7's rows report the change, its totals row already holds the
  new totals. Ann's Total and Balance and the page totals are also new. The same holds after a
  class fee change, Add student, Edit student and Remove from this term.
- **AC-16** Given page 3 or 4, when a quantity, rate or Expected/Budgeted is changed, then the
  item's amount, Total this month and Term total are new at once, and page 2 shows the new figures
  when it is opened again. This is a regression test for behaviour that works today.

**By eye**
- **AC-17 (by eye, Mac)** On page 5 at 1366×768:
  - leaving a payment cell with Enter, with Tab and with a click elsewhere updates the student's
    Total and Balance, the class totals row and the page totals at once;
  - the table fits with the Day/Boarding column and the Edit button;
  - the class heading shows Day fee, Boarding fee and Ream;
  - the edit dialog works, and "(Left)" shows.

  On page 3, Add item with quantity and rate fills the selected month.
- **AC-18 (by eye, Windows)** In the next test round, the installer upgrades the installed app (the
  footer shows the new version). V4 migrates the school's data, and AC-17 holds.

## Decisions (from the interview)
1. **Boarding changes the fee:** each class has a Day fee and a Boarding fee; Ream stays one per
   class.
2. **Day/Boarding shows as a column on page 5**, set when adding a student. It is not split on
   pages 2/3, not counted per class, and not filterable.
3. **Feature point 3 (base fee per class) was already built in 002.** In its place, totals that
   don't follow edits are fixed; only page 5's class totals row was found to be wrong.
4. **Add item's quantity and rate go into the selected month.**
5. **Day/Boarding is recorded per term**; a change applies to that term and later ones.
6. **Edit student is in this spec:** names, National/Refugee, joined term, Active/Left. Not class.
7. **The Boarding fee starts at 0** until it is set.
8. **Left students stay listed**, tagged Left, and always count; the "Show students who left"
   switch is removed.
9. **Earlier terms are never changed by later actions.** Students record the term they joined
   (editable, for entering history) and, when Left, their last term (the term being viewed).

Recommendations accepted at approval:
10. **Moving Joined later past a term with payments is refused.** A student can't have paid for a
    term before they joined.
11. **Edit and Remove are two buttons on each line.** *Remove from this term* moves into the Edit
    dialog only if AC-17 finds the table too wide at 1366.
12. **A fee corrected in an earlier term keeps carrying forward** into later terms, including
    later years, until a term with its own fee, as in 002. Giving each new school year its own copy
    of the fees would be a later spec.
13. **A student who comes back after a gap** (Left after T1, Active again in T3) is added to T2 if
    T2's page 5 is first opened after they're Active again. They can be removed from T2 by hand;
    recording gaps is out of scope.
14. **Day/Boarding is changed only in its column**, not also in the Edit dialog.

## Found during implementation
- **`StudentService.setStatus` is gone.** Left needs a last term, so it is only set through
  `StudentAccountService.editStudent`. Its 001 test and 002's Left-switch service test were
  removed (AC-8 to AC-10 replace them); three other tests now mark Left through `editStudent`.
  The 4-argument `setClassFee` and `lines(term, includeLeft)` were replaced, and the tests that
  used them updated.
- **Day/Boarding stays out of `StudentDetails`**, so saving a student's details can never
  overwrite it; it is only changed by `setBoarding` (the column) and `addStudent`.
- **Saving Edit student as Left for a student who is already Left keeps their last term.** Only
  the change from Active to Left sets it to the term being viewed.
- **002's V2 → V3 migration test now migrates to V3 only**, since it compares whole student
  rows and V4 adds columns.
- **Flyway prints a small table for each `ADD COLUMN` with a CHECK** while V4 runs (SQLite's own
  check of the existing rows, "No rows returned"). Harmless, and only during the migration.
- **The Edit student dialog grows when its message appears**; found by eye, where the note above
  it was cut off.
- **Verified by eye (AC-17), Mac only**, using a throwaway program (not committed) that drove the
  real pages at 1366×728 on a migrated copy of Finn's database and saved screenshots:
  - page 5 fits sideways with the Day/Boarding column and both buttons;
  - a payment left with Enter and another by moving to a different cell updated the student,
    the class totals row and the page totals at once;
  - setting a Boarding fee and switching a student to Boarding updated the student and the
    totals;
  - the dialog opened filled in, refused an empty first name and stayed open with the message;
  - marking Left showed "(Left)", and the student still counted;
  - on page 3, an unreadable quantity turned the field red and kept the typed text, and a valid
    add filled the selected month and cleared the row.

  Typing into cells by hand was not clicked through.
- **Real database:** backed up to
  `~/AngelsCareData/angels-care.db.backup-before-003-20261003-224008`. `./gradlew run` applied V4
  and showed the window:
  - all 17 students have a joined term and are Day;
  - Peter Opio (Left) has 2026-2027 Term 2 as his last term;
  - status and last term agree for every student;
  - integrity and foreign-key checks are clean.

  **16 of the 17 joined 2025-2026 Term 1**, because viewing that term under 002 had put them
  all on it: the bug this spec fixes. As decided, such lines aren't repaired. **On the school's
  PC, look for the same thing**: an earlier term listing students who weren't there. If so, those
  lines carry into later debt. Correct them with *Remove from this term* (when there are no
  payments) and the Joined term in Edit student.

## Out of scope
- Editing a student's class, or the class recorded on a term.
- Promoting a class.
- Admission numbers in the UI.
- Deleting a student entirely.
- Day/Boarding totals or counts, or a Day/Boarding split of the Students row on pages 2/3.
- A ream charge that differs between Day and Boarding.
- Marking which Amounts were typed in.
- An "apply the class fee to everyone" option.
- Recording terms a student missed (gaps), beyond *Remove from this term*.
- Repairing lines the old behaviour put on earlier terms.
- Per-year copies of class fees (decision 12).
- UI-thread (TestFX) tests: page logic stays in view models, and controller behaviour is checked
  by eye.

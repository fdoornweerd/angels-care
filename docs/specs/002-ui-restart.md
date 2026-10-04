# 002: Term accounts (UI restart)

**Status:** Implemented (2026-09-27); AC-25 checked by eye on the Mac from screenshots (cell editing not clicked through), AC-26 awaiting the Windows test round · **Branch:** `feature/002-ui-restart`
<!-- Draft → Approved (date) → Implemented (date). Only Finn moves a spec to Approved. -->

## Goal
Give the bookkeeper one place to track, for each term, the money that comes in and goes out,
month by month, against what was expected or budgeted:
- a list of **school years** with their totals;
- a **term summary** of income, expenses and the surplus or deficit;
- **detailed income and expense sheets**, filled in as quantity × rate per item per month;
- a **students sheet** per class, with each student's fees, debt carried from earlier terms, the
  ream charge, payments per month and remarks.

This replaces the first attempt at setup screens, whose structure did not fit how the school keeps
its accounts. The source is `docs/features/002-feature-restart.md`.

## Current state
- **UI:** the single "Database OK" window from `main`. The first attempt at 002 is kept in
  `git stash` ("002 setup screens: first UI attempt"); some generic pieces are reused (see
  Changes).
- **Backend (001, plus the "Changed later" section of spec 001):** school years and terms (a year
  may run across two calendar years and is named "2026-2027"); students with class, residency and
  status; income and expense **categories and items, which are global** (not tied to a year);
  groups and memberships; fee assignments with the no-overlap rule; monthly expense budgets per
  item per year. Also `CalendarService.defaultTerm`.
- **Nothing records money received or spent yet.**

## Behaviour
Screens are pages in one window. A **breadcrumb** at the top ("School years › 2026-2027 › Term 1 ›
Detailed incomes") and a **Back** button return to earlier pages. The layout is designed for a
1366×768 screen.

**Editing in tables.** Every editable cell saves as soon as the user leaves it (Enter, Tab or a
click elsewhere), and every total on the page updates at once. A value that can't be read turns
the cell red, shows the reason under the table, and is not saved. There are no Save buttons and no
"unsaved changes" questions. Deleting anything asks *"Delete '<name>'?"* first.

**Amounts** are whole shillings. `300000`, `300,000`, `300 000` and `UGX 300,000` are all accepted
and shown as `300,000`. Negative amounts are refused, except for **Debt**, where a minus sign
means credit (the student paid too much before). **Quantities** may have up to 2 decimal places
(`12.5`, `1,200.25`). **Dates** are dd/MM/yyyy.

### 1 · School years (opening screen)
1. A table lists every school year, newest first. Columns: **School year** ("2026-2027"), the
   dates of **Term 1**, **Term 2** and **Term 3**, **Income collected**, **Expenditure**, and
   **Surplus/Deficit**. The totals are for the whole year (all three terms).
2. **New school year** opens a form, as in the first attempt:
   - the year it **starts in**, with the resulting name ("2027-2028") shown next to it;
   - start and end dates for Terms 1–3.

   001's rules apply, as changed later: Term 1 starts in the start year, nothing ends after 31
   December of the following year, and school years may not overlap.
3. If an earlier school year exists, the form offers **"Copy income and expense categories and
   items from 2026-2027"**, ticked by default. This copies from the latest earlier year:
   category names, item names and units. It copies no quantities, rates, expected amounts or
   budgets.
4. A year's term dates can be edited, and a year can be deleted. Deleting is blocked while the
   year still has categories or recorded amounts.
5. Clicking a year opens **2 · Term summary**.
6. With no school years, the table shows *"No school years yet. Click New school year to set one
   up."*

### 2 · Term summary
1. **Term tabs** (Term 1 / Term 2 / Term 3) at the top. The page opens on the term that contains
   today (Africa/Kampala), or the next one. If today isn't in this year, it opens on Term 1.
   Everything on pages 2–5 is for the chosen term.
2. **Income table:**
   - Columns: **Income**, **Expected**, **1st month**, **2nd month**, **3rd month**, **Total**.
   - The first row is always **Students** (from page 5). After it come the year's income
     categories, sorted by name.
   - Month columns show what was received that month, and Total is the sum of the three months.
   - A **totals row** at the bottom sums every column.
3. **Expense table:** the same, with **Budgeted** instead of Expected, and one row per expense
   category.
4. **Summary table**, three rows:
   - **Incomes collected**: the income table's Total;
   - **Expenditures**: the expense table's Total;
   - **Surplus/Deficit**: incomes minus expenditures. A deficit is shown as a negative amount,
     labelled "Deficit".
5. **Moving to the detail pages:**
   - Clicking an income row opens **3 · Detailed incomes**, scrolled so that category's table is
     at the top. The Students row goes to the top of page 3.
   - Clicking an expense row opens **4 · Detailed expenses** at that category.
   - The buttons **Detailed incomes** and **Detailed expenses** open those pages at the top.
6. Nothing is edited on this page.

### 3 · Detailed incomes
1. A **month switch** (1st / 2nd / 3rd month of the chosen term) sets which month the quantity and
   rate cells are for.
2. **At the top**, a one-row Students table:
   - **Amount**: the total owed by all students this term;
   - **Total**: what was paid this term. This matches the Students row on page 2.

   Clicking it opens **5 · Students**.
3. Below it, **one table per income category**, sorted by name:
   - Columns: **Item**, **Units**, **Quantity**, **Rate** (UGX), **Amount** (UGX).
   - Amount = quantity × rate, rounded to the nearest shilling, halves rounded up. It's blank
     until both quantity and rate are filled in.
   - Quantity and rate are for the selected month.
4. Under each table:
   - **Total this month** (equals that category's month column on page 2);
   - **Term total** (equals its Total on page 2);
   - an editable **Expected** amount for the term (equals its Expected on page 2).
5. **Adding and changing:**
   - **Add category** at the top of the page.
   - **Add item** under each table. An item needs a **name** and a **unit** (e.g. "kg", "bags",
     "months"); quantity and rate can be filled in straight away.
   - Item names, units and category names can be edited in place.
   - An item or category belongs to the school year, so it appears in **every term and month of
     that year**, with blank quantities.
6. With no income categories, the page shows the Students row and *"No income categories yet.
   Click Add category."*

### 4 · Detailed expenses
The same as page 3, with **Budgeted** in place of Expected and no Students row.

### 5 · Students
1. Two sections: **Nursery** (Baby, Middle, Top) and **Primary** (P1–P7), with one table per
   class.
2. Each class heading shows that class's **fee** and **ream** charge for the term, both editable.
   A class fee **carries forward**: once set in a term, it applies to that term and every later
   term (into later years too) until it is changed in a later term. With no fee ever set, it is 0.
3. **Columns:**

   | Column | What it is | Editable? |
   |---|---|---|
   | **Name** | the student's name | no |
   | **Amount** | the class fee, unless changed for this student | yes; clearing it goes back to the class fee |
   | **Debt** | the balance left in the student's previous term on this sheet, or 0 | yes |
   | **Ream** | the class ream charge, unless changed for this student | yes |
   | **Total** | Amount + Debt + Ream | no |
   | **1st Month**, **2nd Month**, **3rd Month** | what the student paid in each month | yes |
   | **Balance** | Total − payments | no |
   | **Remarks** | free text, up to 500 characters | yes |

4. Under each class table, and for the whole page, a **totals row**.
5. **Who is listed:** opening this page for a term adds every **Active** student who isn't on the
   term yet, under their current class. The class is recorded for that term, so a later change
   of class doesn't move them in past terms. Students marked Left are not added to new terms.
   A **"Show students who left"** switch reveals Left students already on the term, for example
   to record a late payment.
6. **Add student** in a class table asks for first name, last name, and National or Refugee
   (National by default). It creates the student (Active, in that class) and puts them on this
   term.
7. **Remove from this term** takes a student off the term, after a confirmation. It is for someone
   added by mistake, and it is blocked if they have payments this term.
8. The Students figures on pages 2 and 3:
   - **Expected** = the sum of every listed student's Total (Left students included);
   - **month columns** = the payments in each month;
   - **Total** = all payments this term.

## Changes
- **Schema: `V3__term_accounts.sql`.** No committed migration is edited.
  - `income_category`, `expense_category`: add `school_year_id` (FK). Replace the unique name
    index with one per school year. Categories without a year (none are expected, since there was
    no UI) are ignored.
  - `income_item`, `expense_item`: add `unit TEXT NOT NULL DEFAULT ''`. The service requires a
    unit.
  - `income_entry`, `expense_entry`: `item_id`, `term_id`, `month` (1–3), `quantity_hundredths`
    (INTEGER, may be null), `rate` (INTEGER UGX, may be null). One per item, term and month.
  - `income_plan`, `expense_plan`: `category_id`, `term_id`, `amount` (INTEGER ≥ 0). This is the
    Expected or Budgeted amount for a category in a term.
  - `class_fee`: `school_class`, `term_id` (the term it starts applying from), `amount`, `ream`.
  - `student_term`: `student_id`, `term_id`, `school_class` (recorded when the student is added
    to the term), `amount`, `ream` and `debt` (each null = use the default), `paid_1`, `paid_2`,
    `paid_3`, `remarks`. One per student and term.
  - All tables follow the sync rules: UUID `id`, `created_at`/`updated_at`/`deleted_at`, soft
    deletes.
- **Removed code (Finn's decision):** groups and memberships, fee assignments with their coverage
  rule, and 001's monthly budgets. That means `StudentGroup`, `GroupMembership`, `GroupService`,
  `FeeAssignment`, `FeeTarget`, `BillingFrequency`, `ApplicableFee`, `FeeAssignmentService`,
  `FeeCoverageGuard`, `Coverage`, `StudentChangeGuard`, `ExpenseBudget`, `BudgetLine`,
  `ExpenseBudgetService`, their repositories, and their tests (including the matching parts of
  `NamingRulesTest`, `DeletionRulesTest`, `SoftDeleteTest`, `SyncColumnsTest` and
  `ServiceAdditionsTest`). Their tables stay in the schema, unused.
- **Backend:**
  - **Catalogues** become per school year (`IncomeCatalogService` and `ExpenseCatalogService`
    take a year), plus `copyFromYear`.
  - **Entries and plans:** a shared implementation for income and expense, parameterised by
    tables, so the two sides don't duplicate code.
  - **`StudentAccountService`:** the term register, class fees carrying forward, the debt chain,
    and totals.
  - **`TermAccountsService`:** the page-2 figures and the year totals.
  - **`Quantity`** value type in `common/`: stored as hundredths, parsed and formatted, and
    multiplied by `Ugx` with rounding.
- **UI:** view models under `<feature>/ui/`, in plain Java with JavaFX properties, tested without a
  UI thread. FXML views with thin controllers. A page navigator with the breadcrumb and Back.
  From the stash, reuse `UgxField`, `DateFormats`, `ErrorMessages`, `ConfirmDialogs` and
  `JavaFxDialogs`, and parts of `Fx`. Nothing else from the first attempt.
- **Other:** CLAUDE.md gets one line on view models, and spec 001's "Changed later" section
  records the removals.

## Acceptance criteria
Service and view-model tests run on a temp DB with a fixed clock (today = 2 March 2026 unless a
test says otherwise). **(by eye)** criteria are checked by running the app, and on Windows in the
next test round.

**Schema and catalogue**
- **AC-1** Given a database at V2, when it migrates, then the V3 tables and columns exist, every
  new table has the sync columns, and existing rows are unchanged.
- **AC-2** Given two school years, then each has its own categories. "Feeding" may exist in both,
  but not twice in one year, ignoring case. An item needs a name and a unit (trimmed, at most 100
  characters). A missing unit is refused.
- **AC-3** Given 2026-2027 with categories, items and entries, when 2027-2028 is created with
  *copy*, then it has the same category and item names and units, and no entries or plans.
  Without *copy*, or with no earlier year, it has none.
- **AC-4** Given an item created while viewing Term 2, month 3, then it appears, blank, in every
  term and month of that school year and in no other year.

**Quantities and entries**
- **AC-5**
  - `12.5`, `1,200.25`, `0` and `3` are valid quantities.
  - `-1`, `1.234`, `abc` and an empty value are refused with a message.
  - Amounts round to the nearest shilling, halves up: 12.5 × 3,500 = 43,750; 0.33 × 10 = 3;
    0.35 × 10 = 4.
- **AC-6** Given an item, when a quantity or rate is entered for a term and month, then it is
  stored at once. With only one of the two filled in, the amount is blank and counts as 0.
  Clearing both removes the entry (a soft delete). A rate must be a whole, non-negative amount.
- **AC-7** Given entries in months 1 and 3, then the category's month totals and term total add
  up. An Expected or Budgeted amount can be set (0 or more), changed, and cleared.

**Term summary (page 2)**
- **AC-8** Given income categories A and B and expense category C with entries, then:
  - the income rows are Students, A, B in that order, with Expected, three month columns and
    Total;
  - the expense rows show Budgeted and the same columns;
  - each table's totals row equals the sum of its rows.
- **AC-9** Given incomes of 1,000,000 and expenses of 1,250,000 in a term, then the summary shows
  incomes collected 1,000,000, expenditures 1,250,000, and a deficit of −250,000. Equal amounts
  show 0.
- **AC-10** Given a new year with no categories and no students, then the income table has only
  the Students row, at zero, the expense table shows its hint, and the summary is all zeros.
- **AC-11** Given today (Kampala) in 2026-2027's Term 2, when the year is opened, then the page
  opens on Term 2. For a year that doesn't contain today, it opens on Term 1.

**Detail pages (3 and 4)**
- **AC-12** Given the detail page and month 2, when a quantity and rate are entered for an item,
  then the item's amount, the category's month and term totals, and page 2's figures all update.
  Page 3's Students row matches page 2's Students row.
- **AC-13** Given an invalid quantity or rate in a cell, then that cell is marked, a message names
  the item, and nothing is saved.
- **AC-14** Given the page opened from a category row on page 2, then that category's table is
  the one to scroll to. Opened from the Students row or the Detailed button, the page opens at the
  top.
- **AC-15** Given a category or item with recorded entries or a plan, when it is deleted, then
  the delete is blocked with a message. Without them, it is soft-deleted after confirmation.

**Students (page 5)**
- **AC-16** Given 3 Active students and 1 Left student, when page 5 is opened for a term, then
  the 3 Active students are added under their classes and the Left one is not. Opening it again
  adds nobody twice. "Show students who left" shows Left students already on the term.
- **AC-17** Class fees carry forward:
  - Given P7 fee 300,000 and ream 10,000 set in 2026-2027 Term 1, then P7 students in Term 1,
    Term 2 and 2027-2028 Term 1 get those defaults.
  - Changing the fee to 320,000 in Term 3 affects Term 3 onwards only.
  - A class with no fee ever set gets 0.
- **AC-18** Given a P7 student in Term 1 with Amount 300,000, Debt 0 and Ream 10,000 who paid
  100,000 and 50,000:
  - their Total is 310,000 and their Balance is 160,000;
  - in Term 2 their Debt defaults to 160,000;
  - typing a Debt overrides it;
  - an overpayment carries as a negative debt (a credit);
  - clearing an Amount override returns it to the class fee.
- **AC-19** Given a student added via "Add student" in P3, then they exist as an Active P3
  National student and are on this term with the class defaults. The student is on the term
  once, even when the page is opened again.
- **AC-20** Given a student moved from P3 to P4 after Term 1, then they stay under P3 in Term 1
  and are added under P4 in a later term.
- **AC-21** Given students with payments, then page 2's Students row shows Expected = Σ Totals
  and each month = Σ payments that month. A student marked Left keeps their rows, still counts,
  and is not added to later terms.
- **AC-22** Given a student with payments this term, then *Remove from this term* is refused.
  Without payments, it removes them after confirmation.

**School years (page 1)**
- **AC-23** Given two years with recorded money, then the list is newest first and each year's
  totals are the sum of its three terms. With no years, the hint is shown. Year-form validation
  shows 001's messages, and the copy option is offered only when an earlier year exists.

**Navigation**
- **AC-24** Given the navigator:
  - the pages open in the order 1 → 2 → 3 → 5;
  - the breadcrumb reads "School years › 2026-2027 › Term 1 › Detailed incomes › Students";
  - Back returns step by step;
  - the chosen term is kept when going back to page 2.

**By eye**
- **AC-25 (by eye)** At 1366×768, pages 2–5 are readable. Page 2's three tables fit without
  scrolling sideways. Page 5 scrolls down through the classes. Cells save when you leave them, and
  totals update at once. Opening page 3 from a category row scrolls it to the top.
- **AC-26 (by eye, Windows)** The installer's app shows the school-year list. A year, a category,
  an item with quantity and rate, and a student payment can be entered and are still there after
  restarting.

## Decisions (resolved at approval)
From the interview:
1. **Page 2 is per term**, with Term 1/2/3 tabs; pages 3–5 follow the chosen term.
2. **Amounts are entered per month** on pages 3/4 (a month switch; quantity × rate per item).
3. **Categories and items belong to a school year**, can be copied from the previous year, and an
   item created anywhere in a year exists in every term and month of that year.
4. **All five pages are in this spec.**
5. **Page 5:** Ream is a charge; Total = Amount + Debt + Ream; a Balance column is added. Debt is
   carried over and editable. Amount and Ream come from a per-class fee, can be changed per
   student, and carry forward until changed.
6. **Quantities** may have up to 2 decimals.
7. **Page 2 rows are categories**; page 1 shows each year's totals.
8. **Groups, fee assignments and 001's monthly budgets are removed** from the code (tables stay).
9. **Cells save when you leave them**, with no Save buttons.
10. **Page 5 lists Active students**, with a switch to show students who left.

Recommendations accepted at approval:
11. Opening page 5 for a term adds every Active student not yet on it; their class is recorded
    per term; *Remove from this term* corrects mistakes and is blocked when there are payments.
12. Students' Expected is the sum of every listed student's Total (Left students included).
13. Overpayment carries as negative debt (credit); Debt is the only field that may be negative.
14. quantity × rate is rounded to the nearest shilling, halves up.
15. Page 3's Students row shows whole-term figures, whatever month is selected.
16. Deleting a category or item is blocked while it has entries or a plan; deleting a school year
    is blocked while it has categories.
17. "Add student" asks only for first name, last name, and National or Refugee (default National).

## Found during implementation
- **Your database already had categories.** Finn's Mac database held a 2026 school year, an income
  category "Students" (items Tuition, Housing), an expense category "Salary", a student, a group
  and a fee assignment, entered while trying the first attempt. V3 therefore gives categories from
  before V3 the **latest school year** (and a new `updated_at`), rather than leaving them hidden;
  a database with no school year leaves them without one. Test:
  `FinanceMigrationTest.attachesOldCategoriesToLatestYear`. The group and fee assignment stay in
  their unused tables. Items from before V3 have a blank unit, which can be typed in on page 3.
- **V3 has run on Finn's real database**, so it must not be edited again even before it is
  committed; any further schema change goes in V4.
- **A user category may be called "Students"**, which then shows next to the built-in Students
  row (it happened on Finn's data). Left as is; worth a rule later if it confuses people.
- **Opening another term while month 2 or 3 was selected** kept the previous term's tables
  (`DetailViewModel.show` only switched the month). Caught by
  `DetailViewModelTest.itemsBelongToTheYear`; `show` now always rebuilds.
- **Editing a cell updates its line and totals in place** on pages 3 and 5 instead of rebuilding
  the page, so moving from cell to cell with Tab isn't interrupted. Renaming, adding and deleting
  rebuild.
- **Removed students stay removed:** `StudentTermRepository.everOnTerm` is the one read that also
  sees soft-deleted rows, on purpose, so *Remove from this term* isn't undone when the page is
  opened again.
- **The debt chain is computed in memory** (`student/service/Register`) from one read of the term
  lines and class fees, instead of one query per student and term.
- **Spring wiring:** the two ledger sheets and the page view models are made in
  `accounts/service/AccountsConfig` and `shell/ui/PagesConfig`.
- **Verified by eye (AC-25), Mac only:** a throwaway program (not committed) filled a scratch
  database and walked pages 1 → 2 → 3 → 5 → 4 at 1366×728 and 1024×600, saving screenshots.
  Page 2's tables fit without scrolling sideways at both sizes; page 4 opened from a category
  scrolls towards it; the figures match hand calculations (e.g. Feeding month 1 = 200 × 3,500 +
  12.5 × 5,000 = 762,500). Two fixes came from this: right-aligned totals in page 5's editable
  columns, and "National"/"Refugee" in the add-student picker. Typing into cells was not clicked
  through: that is covered by the view-model tests, and Finn should try it once.
- **Real database:** backed up to
  `~/AngelsCareData/angels-care.db.backup-before-002-restart-20260927-183807`. `./gradlew run`
  applied V3 (categories joined 2026-2027, `app_meta` unchanged, integrity and foreign-key checks
  clean), and the `jpackageImage` app starts on it. The pages were walked on a copy of it.

## Changed later
**Spec 003 (student updates, 2026-10-03)** changed page 5:
- **The "Show students who left" switch is gone.** Left students are always listed on the terms
  they're on, "(Left)" on their last term, and always count, so page 5's totals equal page 2's
  Students row (AC-16's switch test was removed; spec 003's AC-10 replaces it).
- **Opening a term adds only the students who belong to it** (joined by then, not left before
  it), not every Active student: viewing an earlier term used to put students who joined later
  onto it, with that term's fee carried into their debt.
- Each class has a **Day fee and a Boarding fee** (plus Ream), and students are Day or Boarding,
  recorded per term like class.
- The class totals row is built by the view model (`ClassSection.rows()`); it used to lag one
  edit behind.

## Out of scope
Student photos. Promoting a whole class to the next class. Printing, reports and exports. Receipts
and exact payment dates (only which month). Groups and fee assignments (their code is removed;
their tables stay unused). Actual-versus-budget reports beyond page 2. Importing students or
amounts. Several people editing the same cell at once (cloud sync is later). Changing a student's
recorded class within a term.

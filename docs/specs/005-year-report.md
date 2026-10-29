# 005: Printable year report

**Status:** Implemented (2026-10-28); AC-1 and AC-17 checked by eye on the Mac with the real database, the button clicked through by Finn, AC-18 awaiting the Windows test round · **Branch:** `feature/005-year-report`
<!-- Draft → Approved (date) → Implemented (date). Only Finn moves a spec to Approved. -->

## Goal
From the School years page, the bookkeeper clicks one button on a year and gets a PDF with
everything recorded for that year: the term summaries, the detailed incomes and expenses, and
every class's student sheet. They print it from the PC's PDF viewer, so the school keeps a paper
copy of its accounts (for the head teacher, the board, or a filing cabinet), not only the copy on
the PC.

## Current state
- **Page 1 · School years** (`calendar/ui/SchoolYearsController`, `school-years.fxml`) lists the
  years with their totals. Clicking a row opens the term summary. No year has any action other
  than edit and delete.
- The data the report needs is all readable through existing services:
  - `TermAccountsService.yearTotals` / `summary`: page 2's income, expense and summary tables;
  - `LedgerSheetService.ledger`, `monthEntries`, `categoryTotals`, `plan`: pages 3–4;
  - `StudentAccountService.lines`, `classFee`, `totals`: page 5.
- Nothing in the app writes PDFs or opens files yet. There is no PDF library among the
  dependencies.

## Behaviour

### The button
1. Each row on the School years page has a **PDF report** button. Clicking it does not open the
   year (the row click still does).
2. A **Save** dialog opens, suggesting the file name `Angels Care 2026-2027 report.pdf` in the
   user's **Documents** folder. Only `.pdf` is offered; a name typed without `.pdf` gets it added.
   Choosing an existing file asks to replace it (the system dialog's own question).
3. **Cancel** in the dialog does nothing.
4. After saving, the PDF **opens in the PC's default PDF viewer**, where it is printed. If it was
   saved but can't be opened (no PDF viewer), the app says *"The report was saved to <path>, but
   it could not be opened. Open it from that folder to print it."*
5. If it can't be written (folder not writable, file open in another program), the app shows
   *"The report could not be saved: <reason>"* and nothing else changes.
6. Making the report **changes nothing** in the database. In particular, it does not add students
   to a term the way opening page 5 does: each term prints the students already on it.

### The document
A4 **landscape**. Every page has a header, *"Angels Care School · Report 2026-2027"*, and a footer,
*"Printed 28/10/2026 14:05 · Page 3 of 17"* (Africa/Kampala time). Amounts are whole shillings
with thousands separators (`300,000`), as in the app; a deficit is shown as a negative amount,
labelled "Deficit". Dates are dd/MM/yyyy. Tables that run over a page repeat their column
headings on the next page.

It is ordered **section by section**:

1. **Year overview** (first page): the year name, the dates of Terms 1–3, and the year's
   **Income collected**, **Expenditure** and **Surplus/Deficit**, the same as the year's row on
   page 1.
2. **Term summaries**: for Term 1, 2 and 3 in turn, page 2's three tables (Income with Expected,
   the three months and Total, Students first; Expense with Budgeted; Summary). Each term starts on
   a new page.
3. **Detailed incomes**: for each term, the Students row (Amount, Total), then one table per
   income category, sorted by name, with **one row per item**: Item, Units, then for each of the
   1st, 2nd and 3rd month its Quantity, Rate and Amount, then the item's term total.
   Under each table: the category's total per month, its term total, and its Expected amount.
   Blank quantities and rates stay blank, as on screen.
4. **Detailed expenses**: the same, with Budgeted in place of Expected and no Students row.
5. **Students**: for each term, Nursery then Primary, one table per class with the class's Ream in
   the heading. Columns: Name, Day/Boarding, Amount, Debt, Ream, Total, 1st Month, 2nd Month,
   3rd Month, Balance, Remarks. Students who left are included, with *"(Left)"* after their name.
   A totals row under each class, and a totals row for the whole term.

Classes with no students on a term are left out of that term's student section. Categories with
no items still print their heading with *"No items."*

### Terms with nothing recorded
A term has **nothing recorded** when no student is on it and no item has a quantity or a rate in
any of its months, and it has no Expected or Budgeted amounts. Such a term still gets its
**term summary** (all zeros), with the note *"Nothing recorded for this term yet."* under it, and
**no** detailed-income, detailed-expense or students pages. If no term has anything recorded, the
report is the overview and three summaries.

### Layout check (by eye)
The widest table (the detail tables: Item, Units, 3 × Quantity/Rate/Amount, Total = 12 columns)
must fit on A4 landscape at a readable size (at least 7 pt). Long item names and remarks wrap in
their cell rather than being cut off.

## Changes
- **Schema:** none. The report only reads.
- **Code:**
  - New feature package `report/`:
    - `report/service/YearReportService`: gathers one year's data from the existing services into
      an immutable `report/model/YearReport` (no JavaFX, no PDF). It decides which terms have
      nothing recorded.
    - `report/service/YearReportPdf`: writes a `YearReport` to an `OutputStream` as PDF.
  - `SchoolYearsController` / `SchoolYearsViewModel`: the **PDF report** button column, the Save
    dialog (`FileChooser`), opening the saved file (JavaFX `HostServices.showDocument`), and the
    error messages.
- **Dependency:** **OpenPDF** (`com.github.librepdf:openpdf`, LGPL/MPL): tables that split across
  pages with repeated headings, landscape A4, and page events for "Page x of y", in plain Java on
  the classpath. Added to `gradle/libs.versions.toml`.
- **Packaging:** the new jar must be in the Windows installer, and opening a PDF from the app
  must work on Windows. Both need the next Windows test round.
- **Other:** README's project layout lists `report/`.

## Acceptance criteria
- **AC-1** Given the School years page with two years, when it is shown, then each row has a
  **PDF report** button, and clicking the button does not open the year.
- **AC-2** Given 2026-2027, when **PDF report** is clicked, then a Save dialog opens in Documents
  with the name `Angels Care 2026-2027 report.pdf`.
- **AC-3** Given the Save dialog, when Cancel is clicked, then no file is written and nothing
  else happens.
- **AC-4** Given a name typed without `.pdf`, when saved, then the file gets `.pdf` added.
- **AC-5** Given a saved report, when writing finishes, then it opens in the default PDF viewer;
  and given it can't be opened, then the "was saved to <path>, but could not be opened" message is
  shown.
- **AC-6** Given a file that can't be written (e.g. open in another program), when saving, then
  "The report could not be saved: …" is shown and the app carries on.
- **AC-7** Given a year with recorded amounts, students and expected/budgeted amounts, when the
  report is made, then the database is unchanged: same rows, same `updated_at`, and no student is
  added to a term (an Active student who is on no term of the year stays off it).
- **AC-8** Given any report, then it is A4 landscape, and every page has the header with the
  year and the footer with the print time (Africa/Kampala) and "Page x of y".
- **AC-9** Given a year, then the overview's Income collected, Expenditure and Surplus/Deficit
  equal that year's row on page 1, and each term summary's tables equal page 2 for that term,
  cell for cell; a deficit shows as negative and labelled "Deficit".
- **AC-10** Given a term with categories and items, then the detailed incomes and expenses list
  every category by name and every item, with each month's quantity, rate and amount as on pages
  3–4, blanks staying blank, and the category totals and Expected/Budgeted as on screen.
- **AC-11** Given a term with students in several classes, including one who left, then each
  class with students has a table with its Ream in the heading and the 11 columns of page 5, the
  Left student appears as "Name (Left)", and the class and term totals equal page 5's.
- **AC-12** Given a class with no students on a term, then it is not printed for that term; and
  given a category with no items, then its heading is printed with "No items."
- **AC-13** Given a year where Term 1 has data and Terms 2 and 3 have nothing recorded, then
  Terms 2 and 3 get only a zero summary with "Nothing recorded for this term yet." and appear in
  no detail or student section.
- **AC-14** Given a year with nothing recorded in any term, then the report is the overview and
  three zero summaries, and it is still made without errors.
- **AC-15** Given zero amounts, a student with a credit (negative Debt), and amounts of
  10,000,000 or more, then they print as `0`, `-20,000` and `10,000,000`, with no decimals.
- **AC-16** Given the report sections, then they are in the order overview, term summaries,
  detailed incomes, detailed expenses, students; each term's summary starts on a new page; and
  tables split over pages repeat their headings.
- **AC-17** *(by eye)* Given a real year printed on A4 landscape, then the 12-column detail
  tables and the 11-column student tables fit the page at 7 pt or larger, and long names and
  remarks wrap instead of being cut.
- **AC-18** *(Windows round)* Given the installed app on Windows, when a report is saved, then it
  opens in the PC's PDF viewer and prints.

## Notes from implementing
- The page is a true landscape A4 (842 × 595 points), not a portrait page marked "rotate 90°":
  some printers and previews ignore that mark and cut the page off.
- A table that fits on one page is moved whole to the next page instead of splitting, and each
  class's and category's heading is part of its table, so a heading is never left alone at the
  bottom of a page.
- An income or expense side with no categories at all prints "No income categories." /
  "No expense categories." for that term.

## Decisions
1. **Branch.** `feature/005-year-report` was cut from `feature/004-update-students`, thought to
   be unmerged; 004 had in fact been merged into `main` (PR 6), so no rebase was needed.
2. **PDF library: OpenPDF**, over Apache PDFBox, which only draws text and lines and would need
   its own table layout. Its licence allows use in a closed app without changes.
3. **One file per year, not per term.** A per-term button can be added later if the bookkeeper
   wants to print just one term.
4. Asked while specifying: contents are term summaries, detailed incomes/expenses and every
   student; Save as… then open; A4 landscape; Left students included and marked; section by
   section; terms with nothing recorded get only their summary and a note.

## Out of scope
- Printing straight from the app without a PDF (use the PDF viewer's Print).
- Reports for a single term, a single class, or a date range; per-student statements or receipts.
- The school's logo, address or signature lines on the report.
- Choosing which sections to include, or portrait paper.
- Excel/CSV export.

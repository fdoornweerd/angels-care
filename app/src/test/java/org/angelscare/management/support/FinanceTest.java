package org.angelscare.management.support;

import java.time.Instant;
import java.time.LocalDate;
import org.angelscare.management.accounts.service.LedgerSheetService;
import org.angelscare.management.calendar.model.SchoolYear;
import org.angelscare.management.calendar.model.TermDates;
import org.angelscare.management.calendar.model.TermRef;
import org.angelscare.management.common.Quantity;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.expense.model.ExpenseCategory;
import org.angelscare.management.expense.model.ExpenseItem;
import org.angelscare.management.income.model.IncomeCategory;
import org.angelscare.management.income.model.IncomeItem;
import org.angelscare.management.student.model.Residency;
import org.angelscare.management.student.model.SchoolClass;
import org.angelscare.management.student.model.Student;
import org.angelscare.management.student.model.StudentDetails;
import org.junit.jupiter.api.BeforeEach;

/**
 * A {@link DatabaseTest} with every finance service wired up and a clock fixed at {@link #T0}
 * (2 March 2026), plus shortcuts for the data most tests need.
 */
public abstract class FinanceTest extends DatabaseTest {

    protected static final Instant T0 = Instant.parse("2026-03-02T08:00:00Z");

    protected MutableClock clock;
    protected Finance finance;

    @BeforeEach
    void wireFinance() {
        clock = new MutableClock(T0);
        finance = new Finance(jdbc, clock);
    }

    protected static TermRef t(int year, int number) {
        return TermRef.of(year, number);
    }

    protected static TermDates dates(String start, String end) {
        return TermDates.of(LocalDate.parse(start), LocalDate.parse(end));
    }

    /** A February-to-December school year. */
    protected SchoolYear createYear(int year) {
        return finance.calendar.createYear(year,
                dates(year + "-02-02", year + "-04-24"),
                dates(year + "-05-18", year + "-08-14"),
                dates(year + "-09-07", year + "-12-04"));
    }

    /** A September-to-July school year, e.g. 2025-2026. */
    protected SchoolYear createSpanningYear(int startYear) {
        int next = startYear + 1;
        return finance.calendar.createYear(startYear,
                dates(startYear + "-09-07", startYear + "-12-04"),
                dates(next + "-01-11", next + "-04-02"),
                dates(next + "-04-26", next + "-07-30"));
    }

    protected Student createStudent(String firstName, String lastName, SchoolClass schoolClass) {
        return finance.students.create(
                StudentDetails.of(firstName, lastName, schoolClass, Residency.NATIONAL));
    }

    protected IncomeCategory incomeCategory(int year, String name) {
        return finance.income.listCategories(year).stream()
                .filter(c -> c.name().equals(name))
                .findFirst()
                .orElseGet(() -> finance.income.createCategory(year, name));
    }

    protected IncomeItem incomeItem(int year, String category, String item, String unit) {
        return finance.income.createItem(incomeCategory(year, category).id(), item, unit);
    }

    protected ExpenseCategory expenseCategory(int year, String name) {
        return finance.expenses.listCategories(year).stream()
                .filter(c -> c.name().equals(name))
                .findFirst()
                .orElseGet(() -> finance.expenses.createCategory(year, name));
    }

    protected ExpenseItem expenseItem(int year, String category, String item, String unit) {
        return finance.expenses.createItem(expenseCategory(year, category).id(), item, unit);
    }

    /** Records quantity × rate for an item in one month of a term. */
    protected void record(LedgerSheetService sheet, String itemId, TermRef term, int month,
            String quantity, long rate) {
        sheet.setQuantity(itemId, term, month, Quantity.parse(quantity));
        sheet.setRate(itemId, term, month, Ugx.of(rate));
    }

    protected Instant timestampOf(String table, String column, String id) {
        return Instant.parse(jdbc.queryForObject(
                "SELECT " + column + " FROM " + table + " WHERE id = ?", String.class, id));
    }

    protected boolean isSoftDeleted(String table, String id) {
        return jdbc.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM " + table + " WHERE id = ?", Boolean.class, id);
    }

    protected int liveRows(String table) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE deleted_at IS NULL", Integer.class);
    }

    /** One row in every table still in use, all linked together, in 2026 Term 1. */
    protected record Everything(
            SchoolYear year,
            Student student,
            IncomeCategory incomeCategory,
            IncomeItem incomeItem,
            ExpenseCategory expenseCategory,
            ExpenseItem expenseItem) {
    }

    protected Everything createOneOfEverything() {
        SchoolYear year = createYear(2026);
        Student student = createStudent("Amina", "Nakato", SchoolClass.P4);
        IncomeCategory donations = finance.income.createCategory(2026, "Donations");
        IncomeItem church = finance.income.createItem(donations.id(), "Church support", "months");
        ExpenseCategory feeding = finance.expenses.createCategory(2026, "Feeding");
        ExpenseItem maize = finance.expenses.createItem(feeding.id(), "Maize flour", "kg");
        record(finance.incomeSheet, church.id(), t(2026, 1), 1, "1", 200_000);
        record(finance.expenseSheet, maize.id(), t(2026, 1), 1, "50", 3_500);
        finance.incomeSheet.setPlan(donations.id(), t(2026, 1), Ugx.of(600_000));
        finance.expenseSheet.setPlan(feeding.id(), t(2026, 1), Ugx.of(500_000));
        finance.studentAccounts.setClassFee(SchoolClass.P4, t(2026, 1), Ugx.of(250_000),
                Ugx.of(10_000));
        finance.studentAccounts.openTerm(t(2026, 1));
        finance.studentAccounts.setPayment(student.id(), t(2026, 1), 1, Ugx.of(100_000));
        return new Everything(year, student, donations, church, feeding, maize);
    }
}

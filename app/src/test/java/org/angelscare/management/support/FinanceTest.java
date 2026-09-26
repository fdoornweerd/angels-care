package org.angelscare.management.support;

import java.time.Instant;
import java.time.LocalDate;
import org.angelscare.management.calendar.model.SchoolYear;
import org.angelscare.management.calendar.model.TermDates;
import org.angelscare.management.calendar.model.TermRange;
import org.angelscare.management.calendar.model.TermRef;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.expense.model.ExpenseBudget;
import org.angelscare.management.expense.model.ExpenseCategory;
import org.angelscare.management.expense.model.ExpenseItem;
import org.angelscare.management.income.model.BillingFrequency;
import org.angelscare.management.income.model.FeeAssignment;
import org.angelscare.management.income.model.FeeTarget;
import org.angelscare.management.income.model.IncomeCategory;
import org.angelscare.management.income.model.IncomeItem;
import org.angelscare.management.student.model.GroupMembership;
import org.angelscare.management.student.model.Residency;
import org.angelscare.management.student.model.SchoolClass;
import org.angelscare.management.student.model.Student;
import org.angelscare.management.student.model.StudentDetails;
import org.angelscare.management.student.model.StudentGroup;
import org.junit.jupiter.api.BeforeEach;

/**
 * A {@link DatabaseTest} with every finance service wired up and a clock fixed at {@link #T0},
 * plus shortcuts for the data most tests need.
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

    /** A year with typical Ugandan term dates. */
    protected SchoolYear createYear(int year) {
        return finance.calendar.createYear(year,
                dates(year + "-02-02", year + "-04-24"),
                dates(year + "-05-18", year + "-08-14"),
                dates(year + "-09-07", year + "-12-04"));
    }

    protected Student createStudent(String firstName, String lastName, SchoolClass schoolClass) {
        return finance.students.create(
                StudentDetails.of(firstName, lastName, schoolClass, Residency.NATIONAL));
    }

    protected IncomeItem incomeItem(String categoryName, String itemName) {
        IncomeCategory category = finance.income.listCategories().stream()
                .filter(c -> c.name().equals(categoryName))
                .findFirst()
                .orElseGet(() -> finance.income.createCategory(categoryName));
        return finance.income.createItem(category.id(), itemName);
    }

    protected FeeAssignment assign(IncomeItem item, FeeTarget target, long amount,
            TermRange terms) {
        return finance.fees.assign(item.id(), target, Ugx.of(amount), BillingFrequency.PER_TERM,
                terms);
    }

    protected Instant timestampOf(String table, String column, String id) {
        return Instant.parse(jdbc.queryForObject(
                "SELECT " + column + " FROM " + table + " WHERE id = ?", String.class, id));
    }

    protected boolean isSoftDeleted(String table, String id) {
        return jdbc.queryForObject(
                "SELECT deleted_at IS NOT NULL FROM " + table + " WHERE id = ?", Boolean.class, id);
    }

    /** One row in every finance table, all linked together. */
    protected record Everything(
            SchoolYear year,
            Student student,
            StudentGroup group,
            GroupMembership membership,
            IncomeCategory incomeCategory,
            IncomeItem incomeItem,
            FeeAssignment fee,
            ExpenseCategory expenseCategory,
            ExpenseItem expenseItem,
            ExpenseBudget budget) {
    }

    protected Everything createOneOfEverything() {
        SchoolYear year = createYear(2026);
        Student student = createStudent("Amina", "Nakato", SchoolClass.P4);
        StudentGroup group = finance.groups.create("Boarders", "Sleep at school");
        GroupMembership membership =
                finance.groups.addMember(group.id(), student.id(), TermRange.from(t(2026, 1)));
        IncomeCategory incomeCategory = finance.income.createCategory("Student Fees");
        IncomeItem incomeItem = finance.income.createItem(incomeCategory.id(), "Boarding");
        FeeAssignment fee = assign(incomeItem, FeeTarget.group(group.id()), 250_000,
                TermRange.from(t(2026, 1)));
        ExpenseCategory expenseCategory = finance.expenses.createCategory("Administrative Costs");
        ExpenseItem expenseItem = finance.expenses.createItem(expenseCategory.id(), "Airtime");
        ExpenseBudget budget =
                finance.budgets.setMonthlyBudget(expenseItem.id(), 2026, Ugx.of(50_000));
        return new Everything(year, student, group, membership, incomeCategory, incomeItem, fee,
                expenseCategory, expenseItem, budget);
    }
}

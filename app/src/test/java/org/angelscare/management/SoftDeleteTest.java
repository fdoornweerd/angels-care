package org.angelscare.management;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.angelscare.management.student.model.StudentFilter;
import org.angelscare.management.support.FinanceTables;
import org.angelscare.management.support.FinanceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Spec 001, AC-25: deleting only ever marks rows, and marked rows vanish from every read. */
class SoftDeleteTest extends FinanceTest {

    @Test
    @DisplayName("AC-25: deleting everything keeps every row, marked deleted, and hides it from every list")
    void deletesAreSoft() {
        Everything e = createOneOfEverything();
        Map<String, Integer> rowsBefore = rowCounts();

        finance.fees.delete(e.fee().id());
        finance.groups.removeMembership(e.membership().id());
        finance.budgets.deleteBudget(e.budget().id());
        finance.income.deleteItem(e.incomeItem().id());
        finance.income.deleteCategory(e.incomeCategory().id());
        finance.expenses.deleteItem(e.expenseItem().id());
        finance.expenses.deleteCategory(e.expenseCategory().id());
        finance.groups.delete(e.group().id());
        finance.students.delete(e.student().id());
        finance.calendar.deleteYear(e.year().id());

        assertThat(rowCounts()).isEqualTo(rowsBefore);
        for (String table : FinanceTables.ALL) {
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + table + " WHERE deleted_at IS NULL", Integer.class))
                    .as(table).isZero();
        }

        assertThat(finance.calendar.listYears()).isEmpty();
        assertThat(finance.calendar.findYear(2026)).isEmpty();
        assertThat(finance.students.list(StudentFilter.ALL)).isEmpty();
        assertThat(finance.students.find(e.student().id())).isEmpty();
        assertThat(finance.groups.list()).isEmpty();
        assertThat(finance.groups.memberships(e.group().id())).isEmpty();
        assertThat(finance.income.listCategories()).isEmpty();
        assertThat(finance.income.listItems(e.incomeCategory().id())).isEmpty();
        assertThat(finance.fees.listForItem(e.incomeItem().id())).isEmpty();
        assertThat(finance.expenses.listCategories()).isEmpty();
        assertThat(finance.expenses.listItems(e.expenseCategory().id())).isEmpty();
        assertThat(finance.budgets.budgetsFor(2026)).isEmpty();
    }

    @Test
    @DisplayName("AC-25: deleted_at is stamped with the time of the delete")
    void deletedAtIsNow() {
        Everything e = createOneOfEverything();
        clock.advance(java.time.Duration.ofDays(1));

        finance.budgets.deleteBudget(e.budget().id());

        assertThat(timestampOf("expense_budget", "deleted_at", e.budget().id()))
                .isEqualTo(clock.instant());
        assertThat(timestampOf("expense_budget", "updated_at", e.budget().id()))
                .isEqualTo(clock.instant());
    }

    private Map<String, Integer> rowCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String table : FinanceTables.ALL) {
            counts.put(table, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
        }
        return counts;
    }
}

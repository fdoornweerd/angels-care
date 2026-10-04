package org.angelscare.management;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.angelscare.management.student.model.StudentFilter;
import org.angelscare.management.support.FinanceTables;
import org.angelscare.management.support.FinanceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Spec 001 AC-25: deleting only ever marks rows, and marked rows vanish from every read. */
class SoftDeleteTest extends FinanceTest {

    @Test
    @DisplayName("AC-25 (001): clearing and deleting keeps every row, marked deleted, and hides it")
    void deletesAreSoft() {
        Everything e = createOneOfEverything();
        Map<String, Integer> rowsBefore = rowCounts();

        finance.incomeSheet.setQuantity(e.incomeItem().id(), t(2026, 1), 1, null);
        finance.incomeSheet.setRate(e.incomeItem().id(), t(2026, 1), 1, null);
        finance.expenseSheet.setQuantity(e.expenseItem().id(), t(2026, 1), 1, null);
        finance.expenseSheet.setRate(e.expenseItem().id(), t(2026, 1), 1, null);
        finance.incomeSheet.setPlan(e.incomeCategory().id(), t(2026, 1), null);
        finance.expenseSheet.setPlan(e.expenseCategory().id(), t(2026, 1), null);
        finance.studentAccounts.setPayment(e.student().id(), t(2026, 1), 1, null);
        finance.studentAccounts.removeFromTerm(e.student().id(), t(2026, 1));
        finance.income.deleteItem(e.incomeItem().id());
        finance.income.deleteCategory(e.incomeCategory().id());
        finance.expenses.deleteItem(e.expenseItem().id());
        finance.expenses.deleteCategory(e.expenseCategory().id());
        finance.students.delete(e.student().id());

        assertThat(rowCounts()).isEqualTo(rowsBefore);
        for (String table : List.of("income_entry", "expense_entry", "income_plan", "expense_plan",
                "student_term", "income_item", "income_category", "expense_item",
                "expense_category", "student")) {
            assertThat(liveRows(table)).as(table).isZero();
        }
        assertThat(finance.students.list(StudentFilter.ALL)).isEmpty();
        assertThat(finance.income.listCategories(2026)).isEmpty();
        assertThat(finance.expenses.listCategories(2026)).isEmpty();
        assertThat(finance.incomeSheet.monthEntries(t(2026, 1), 1)).isEmpty();
        assertThat(finance.incomeSheet.plan(e.incomeCategory().id(), t(2026, 1))).isEmpty();
        assertThat(finance.studentAccounts.lines(t(2026, 1))).isEmpty();
    }

    @Test
    @DisplayName("AC-25 (001): deleted_at is stamped with the time of the delete")
    void deletedAtIsNow() {
        Everything e = createOneOfEverything();
        clock.advance(Duration.ofDays(1));

        finance.expenses.deleteItem(finance.expenses.createItem(e.expenseCategory().id(), "Beans",
                "kg").id());

        String id = jdbc.queryForObject(
                "SELECT id FROM expense_item WHERE name = 'Beans'", String.class);
        assertThat(timestampOf("expense_item", "deleted_at", id)).isEqualTo(clock.instant());
        assertThat(timestampOf("expense_item", "updated_at", id)).isEqualTo(clock.instant());
    }

    private Map<String, Integer> rowCounts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String table : FinanceTables.ALL) {
            counts.put(table, jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class));
        }
        return counts;
    }
}

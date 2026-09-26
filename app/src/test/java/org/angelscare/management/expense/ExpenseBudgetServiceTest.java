package org.angelscare.management.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import org.angelscare.management.common.Ugx;
import org.angelscare.management.common.ValidationException;
import org.angelscare.management.expense.model.BudgetLine;
import org.angelscare.management.expense.model.ExpenseBudget;
import org.angelscare.management.expense.model.ExpenseCategory;
import org.angelscare.management.expense.model.ExpenseItem;
import org.angelscare.management.support.FinanceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExpenseBudgetServiceTest extends FinanceTest {

    @Test
    @DisplayName("AC-22: a zero monthly budget is stored as zero")
    void zeroBudget() {
        createYear(2026);
        ExpenseItem airtime = expenseItem("Administrative Costs", "Airtime");

        ExpenseBudget budget = finance.budgets.setMonthlyBudget(airtime.id(), 2026, Ugx.ZERO);

        assertThat(budget.monthlyAmount()).isEqualTo(Ugx.ZERO);
        assertThat(finance.budgets.budgetsFor(2026)).extracting(BudgetLine::monthlyAmount)
                .containsExactly(Ugx.ZERO);
    }

    @Test
    @DisplayName("AC-22: a negative monthly budget is rejected")
    void negativeBudget() {
        createYear(2026);
        ExpenseItem airtime = expenseItem("Administrative Costs", "Airtime");

        assertThatThrownBy(() -> finance.budgets.setMonthlyBudget(airtime.id(), 2026, Ugx.of(-1)))
                .isInstanceOf(ValidationException.class);
        assertThat(finance.budgets.budgetsFor(2026)).isEmpty();
    }

    @Test
    @DisplayName("AC-22: setting a budget again replaces it rather than adding a second one")
    void replacesBudget() {
        createYear(2026);
        ExpenseItem airtime = expenseItem("Administrative Costs", "Airtime");
        ExpenseBudget first = finance.budgets.setMonthlyBudget(airtime.id(), 2026, Ugx.of(50_000));

        ExpenseBudget second = finance.budgets.setMonthlyBudget(airtime.id(), 2026, Ugx.of(60_000));

        assertThat(second.id()).isEqualTo(first.id());
        assertThat(finance.budgets.budgetsFor(2026)).extracting(BudgetLine::monthlyAmount)
                .containsExactly(Ugx.of(60_000));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM expense_budget", Integer.class))
                .isEqualTo(1);
    }

    @Test
    @DisplayName("AC-22: each school year has its own budget")
    void budgetsPerYear() {
        createYear(2026);
        createYear(2027);
        ExpenseItem airtime = expenseItem("Administrative Costs", "Airtime");

        finance.budgets.setMonthlyBudget(airtime.id(), 2026, Ugx.of(50_000));
        finance.budgets.setMonthlyBudget(airtime.id(), 2027, Ugx.of(70_000));

        assertThat(finance.budgets.budgetsFor(2026)).extracting(BudgetLine::monthlyAmount)
                .containsExactly(Ugx.of(50_000));
        assertThat(finance.budgets.budgetsFor(2027)).extracting(BudgetLine::monthlyAmount)
                .containsExactly(Ugx.of(70_000));
    }

    @Test
    @DisplayName("AC-22: a budget needs an existing school year and expense item")
    void needsYearAndItem() {
        ExpenseItem airtime = expenseItem("Administrative Costs", "Airtime");

        assertThatThrownBy(() -> finance.budgets.setMonthlyBudget(airtime.id(), 2026, Ugx.of(1)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("2026");

        createYear(2026);
        assertThatThrownBy(() -> finance.budgets.setMonthlyBudget("no-such-item", 2026, Ugx.of(1)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("AC-23: a year with no budgets lists nothing, and items without a budget are not shown as zero")
    void onlyBudgetedItemsAreListed() {
        createYear(2026);
        assertThat(finance.budgets.budgetsFor(2026)).isEmpty();
        assertThat(finance.budgets.budgetsFor(2030)).isEmpty();

        ExpenseItem airtime = expenseItem("Administrative Costs", "Airtime");
        expenseItem("Administrative Costs", "Data bundles");
        finance.budgets.setMonthlyBudget(airtime.id(), 2026, Ugx.of(50_000));

        assertThat(finance.budgets.budgetsFor(2026)).extracting(line -> line.item().name())
                .containsExactly("Airtime");
    }

    @Test
    @DisplayName("AC-23: budgets are listed by category, then item name")
    void listsSorted() {
        createYear(2026);
        ExpenseItem data = expenseItem("Administrative Costs", "Data bundles");
        ExpenseItem teachers = expenseItem("Staff Salaries", "Teachers");
        ExpenseItem airtime = expenseItem("Administrative Costs", "Airtime");
        finance.budgets.setMonthlyBudget(teachers.id(), 2026, Ugx.of(4_000_000));
        finance.budgets.setMonthlyBudget(data.id(), 2026, Ugx.of(80_000));
        finance.budgets.setMonthlyBudget(airtime.id(), 2026, Ugx.of(50_000));

        assertThat(finance.budgets.budgetsFor(2026))
                .extracting(line -> line.category().name(), line -> line.item().name(),
                        BudgetLine::monthlyAmount)
                .containsExactly(
                        tuple("Administrative Costs", "Airtime", Ugx.of(50_000)),
                        tuple("Administrative Costs", "Data bundles", Ugx.of(80_000)),
                        tuple("Staff Salaries", "Teachers", Ugx.of(4_000_000)));
    }

    @Test
    @DisplayName("AC-25: a deleted budget is no longer listed, and the item can be budgeted again")
    void deletedBudget() {
        createYear(2026);
        ExpenseItem airtime = expenseItem("Administrative Costs", "Airtime");
        ExpenseBudget budget = finance.budgets.setMonthlyBudget(airtime.id(), 2026, Ugx.of(50_000));

        finance.budgets.deleteBudget(budget.id());

        assertThat(finance.budgets.budgetsFor(2026)).isEmpty();
        assertThat(isSoftDeleted("expense_budget", budget.id())).isTrue();
        assertThat(finance.budgets.setMonthlyBudget(airtime.id(), 2026, Ugx.of(40_000)).id())
                .isNotEqualTo(budget.id());
    }

    private ExpenseItem expenseItem(String categoryName, String itemName) {
        ExpenseCategory category = finance.expenses.listCategories().stream()
                .filter(c -> c.name().equals(categoryName))
                .findFirst()
                .orElseGet(() -> finance.expenses.createCategory(categoryName));
        return finance.expenses.createItem(category.id(), itemName);
    }
}

package org.angelscare.management.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import org.angelscare.management.calendar.model.SchoolYear;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.common.ValidationException;
import org.angelscare.management.expense.model.ExpenseCategory;
import org.angelscare.management.expense.model.ExpenseItem;
import org.angelscare.management.income.model.IncomeCategory;
import org.angelscare.management.income.model.IncomeItem;
import org.angelscare.management.support.FinanceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Spec 002: categories and items belong to a school year, and a new year can copy them. */
class CatalogPerYearTest extends FinanceTest {

    @Test
    @DisplayName("AC-2: each school year has its own categories; names are unique within a year")
    void categoriesPerYear() {
        SchoolYear y2026 = createYear(2026);
        createYear(2027);

        ExpenseCategory feeding2026 = finance.expenses.createCategory(2026, "Feeding");
        ExpenseCategory feeding2027 = finance.expenses.createCategory(2027, "Feeding");

        assertThat(feeding2026.schoolYearId()).isEqualTo(y2026.id());
        assertThat(feeding2027.id()).isNotEqualTo(feeding2026.id());
        assertThat(finance.expenses.listCategories(2026)).containsExactly(feeding2026);
        assertThatThrownBy(() -> finance.expenses.createCategory(2026, " feeding "))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("already exists");
        assertThat(finance.income.createCategory(2027, "Donations").name()).isEqualTo("Donations");
    }

    @Test
    @DisplayName("AC-2: a category needs an existing school year")
    void needsSchoolYear() {
        assertThatThrownBy(() -> finance.income.createCategory(2026, "Donations"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("school year");
        assertThat(finance.income.listCategories(2026)).isEmpty();
    }

    @Test
    @DisplayName("AC-2: an item needs a name and a unit; both are trimmed and can be changed")
    void itemNeedsUnit() {
        createYear(2026);
        IncomeCategory donations = finance.income.createCategory(2026, "Donations");

        assertThatThrownBy(() -> finance.income.createItem(donations.id(), "Church support", null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Unit");
        assertThatThrownBy(() -> finance.income.createItem(donations.id(), "Church support", " "))
                .isInstanceOf(ValidationException.class);

        IncomeItem church = finance.income.createItem(donations.id(), " Church support ", " months ");
        assertThat(church.name()).isEqualTo("Church support");
        assertThat(church.unit()).isEqualTo("months");

        IncomeItem changed = finance.income.updateItem(church.id(), "Church gift", "terms");
        assertThat(changed).isEqualTo(new IncomeItem(church.id(), donations.id(), "Church gift",
                "terms"));
        assertThat(finance.income.listItems(donations.id())).containsExactly(changed);
        assertThatThrownBy(() -> finance.income.updateItem(church.id(), "Church gift", ""))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("AC-3: a new year copies the latest earlier year's category and item names and units")
    void copiesFromPreviousYear() {
        createYear(2025);
        finance.expenses.createCategory(2025, "Old category");
        createYear(2026);
        var donations = finance.income.createCategory(2026, "Donations");
        var church = finance.income.createItem(donations.id(), "Church support", "months");
        var feeding = finance.expenses.createCategory(2026, "Feeding");
        var maize = finance.expenses.createItem(feeding.id(), "Maize flour", "kg");
        finance.expenses.createItem(feeding.id(), "Beans", "kg");
        record(finance.incomeSheet, church.id(), t(2026, 1), 1, "1", 200_000);
        record(finance.expenseSheet, maize.id(), t(2026, 2), 3, "12.5", 3_500);
        finance.expenseSheet.setPlan(feeding.id(), t(2026, 1), Ugx.of(500_000));

        assertThat(finance.setup.previousYear(2027)).map(SchoolYear::year).contains(2026);
        finance.setup.createYear(2027, dates("2027-02-01", "2027-04-23"),
                dates("2027-05-17", "2027-08-13"), dates("2027-09-06", "2027-12-03"), true);

        assertThat(finance.income.listCategories(2027)).extracting(IncomeCategory::name)
                .containsExactly("Donations");
        assertThat(finance.expenses.listCategories(2027)).extracting(ExpenseCategory::name)
                .containsExactly("Feeding");
        ExpenseCategory feeding2027 = finance.expenses.listCategories(2027).get(0);
        assertThat(feeding2027.id()).isNotEqualTo(feeding.id());
        assertThat(finance.expenses.listItems(feeding2027.id()))
                .extracting(ExpenseItem::name, ExpenseItem::unit)
                .containsExactly(tuple("Beans", "kg"), tuple("Maize flour", "kg"));
        for (int term = 1; term <= 3; term++) {
            for (int month = 1; month <= 3; month++) {
                assertThat(finance.incomeSheet.monthEntries(t(2027, term), month)).isEmpty();
                assertThat(finance.expenseSheet.monthEntries(t(2027, term), month)).isEmpty();
            }
            assertThat(finance.expenseSheet.plan(feeding2027.id(), t(2027, term))).isEmpty();
        }
        // 2026 itself is untouched.
        assertThat(finance.expenses.listCategories(2026)).containsExactly(feeding);
    }

    @Test
    @DisplayName("AC-3: without copy, or with no earlier year, a new year starts empty")
    void noCopy() {
        assertThat(finance.setup.previousYear(2026)).isEmpty();
        finance.setup.createYear(2026, dates("2026-02-02", "2026-04-24"),
                dates("2026-05-18", "2026-08-14"), dates("2026-09-07", "2026-12-04"), true);
        finance.expenses.createCategory(2026, "Feeding");

        finance.setup.createYear(2027, dates("2027-02-01", "2027-04-23"),
                dates("2027-05-17", "2027-08-13"), dates("2027-09-06", "2027-12-03"), false);

        assertThat(finance.income.listCategories(2026)).isEmpty();
        assertThat(finance.expenses.listCategories(2027)).isEmpty();
    }

    @Test
    @DisplayName("AC-3: deleted categories and items are not copied")
    void deletedAreNotCopied() {
        createYear(2026);
        var feeding = finance.expenses.createCategory(2026, "Feeding");
        var beans = finance.expenses.createItem(feeding.id(), "Beans", "kg");
        finance.expenses.createItem(feeding.id(), "Maize flour", "kg");
        finance.expenses.deleteItem(beans.id());
        var old = finance.expenses.createCategory(2026, "Old");
        finance.expenses.deleteCategory(old.id());

        createYear(2027);

        finance.expenses.copyFromYear(2026, 2027);

        assertThat(finance.expenses.listCategories(2027)).extracting(ExpenseCategory::name)
                .containsExactly("Feeding");
        assertThat(finance.expenses.listItems(finance.expenses.listCategories(2027).get(0).id()))
                .extracting(ExpenseItem::name).containsExactly("Maize flour");
    }

    @Test
    @DisplayName("AC-4: an item belongs to its year: blank in every term and month, absent elsewhere")
    void itemInEveryTermOfItsYear() {
        createYear(2026);
        createYear(2027);
        var maize = expenseItem(2026, "Feeding", "Maize flour", "kg");

        for (int term = 1; term <= 3; term++) {
            for (int month = 1; month <= 3; month++) {
                var entry = finance.expenseSheet.entry(maize.id(), t(2026, term), month);
                assertThat(entry.quantity()).isNull();
                assertThat(entry.rate()).isNull();
                assertThat(entry.amount()).isEmpty();
            }
        }
        assertThat(finance.expenseCatalog.categories(2027)).isEmpty();
        assertThatThrownBy(() -> finance.expenseSheet.entry(maize.id(), t(2027, 1), 1))
                .isInstanceOf(ValidationException.class);
    }
}

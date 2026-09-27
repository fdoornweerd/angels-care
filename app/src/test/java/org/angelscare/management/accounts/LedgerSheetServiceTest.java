package org.angelscare.management.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.function.Function;
import java.util.stream.Stream;
import org.angelscare.management.accounts.model.ItemMonth;
import org.angelscare.management.accounts.service.Catalog;
import org.angelscare.management.accounts.service.LedgerSheetService;
import org.angelscare.management.common.MonthlyAmounts;
import org.angelscare.management.common.Quantity;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.common.ValidationException;
import org.angelscare.management.support.Finance;
import org.angelscare.management.support.FinanceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Spec 002 AC-6/AC-7: one sheet implementation, run for both income and expenses. */
class LedgerSheetServiceTest extends FinanceTest {

    record Side(String label, Function<Finance, Catalog> catalog,
            Function<Finance, LedgerSheetService> sheet, String entryTable, String planTable) {
        @Override
        public String toString() {
            return label;
        }
    }

    static Stream<Side> sides() {
        return Stream.of(
                new Side("income", f -> f.incomeCatalog, f -> f.incomeSheet, "income_entry",
                        "income_plan"),
                new Side("expense", f -> f.expenseCatalog, f -> f.expenseSheet, "expense_entry",
                        "expense_plan"));
    }

    @BeforeEach
    void schoolYears() {
        createYear(2026);
        createYear(2027);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sides")
    @DisplayName("AC-6: quantity and rate are stored at once; the amount needs both")
    void storesEachCellAtOnce(Side side) {
        Catalog catalog = side.catalog().apply(finance);
        LedgerSheetService sheet = side.sheet().apply(finance);
        var category = catalog.createCategory(2026, "Feeding");
        var maize = catalog.createItem(category.id(), "Maize flour", "kg");

        ItemMonth afterQuantity = sheet.setQuantity(maize.id(), t(2026, 1), 2, Quantity.parse("12.5"));

        assertThat(afterQuantity.quantity()).isEqualTo(Quantity.parse("12.5"));
        assertThat(afterQuantity.rate()).isNull();
        assertThat(afterQuantity.amount()).isEmpty();
        assertThat(sheet.categoryTotals(category.id(), t(2026, 1))).isEqualTo(MonthlyAmounts.ZERO);
        assertThat(liveRows(side.entryTable())).isEqualTo(1);

        ItemMonth afterRate = sheet.setRate(maize.id(), t(2026, 1), 2, Ugx.of(3_500));

        assertThat(afterRate.amount()).contains(Ugx.of(43_750));
        assertThat(sheet.entry(maize.id(), t(2026, 1), 2)).isEqualTo(afterRate);
        assertThat(sheet.monthEntries(t(2026, 1), 2)).containsOnlyKeys(maize.id());
        assertThat(sheet.monthEntries(t(2026, 1), 1)).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sides")
    @DisplayName("AC-6: clearing both quantity and rate removes the entry (a soft delete)")
    void clearingRemoves(Side side) {
        Catalog catalog = side.catalog().apply(finance);
        LedgerSheetService sheet = side.sheet().apply(finance);
        var maize = catalog.createItem(catalog.createCategory(2026, "Feeding").id(),
                "Maize flour", "kg");
        record(sheet, maize.id(), t(2026, 1), 1, "2", 1_000);

        sheet.setQuantity(maize.id(), t(2026, 1), 1, null);
        assertThat(liveRows(side.entryTable())).isEqualTo(1);
        sheet.setRate(maize.id(), t(2026, 1), 1, null);

        assertThat(liveRows(side.entryTable())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + side.entryTable(), Integer.class))
                .isEqualTo(1);
        assertThat(sheet.entry(maize.id(), t(2026, 1), 1).quantity()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sides")
    @DisplayName("AC-6: negative rates, months outside 1-3 and items of another year are refused")
    void refusesBadEntries(Side side) {
        Catalog catalog = side.catalog().apply(finance);
        LedgerSheetService sheet = side.sheet().apply(finance);
        var maize = catalog.createItem(catalog.createCategory(2026, "Feeding").id(),
                "Maize flour", "kg");

        assertThatThrownBy(() -> sheet.setRate(maize.id(), t(2026, 1), 1, Ugx.of(-1)))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> sheet.setQuantity(maize.id(), t(2026, 1), 4, Quantity.parse("1")))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> sheet.setQuantity(maize.id(), t(2027, 1), 1, Quantity.parse("1")))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> sheet.setRate("no-such-item", t(2026, 1), 1, Ugx.of(1)))
                .isInstanceOf(ValidationException.class);
        assertThat(liveRows(side.entryTable())).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sides")
    @DisplayName("AC-7: a category's month totals and term total add up its items")
    void categoryTotals(Side side) {
        Catalog catalog = side.catalog().apply(finance);
        LedgerSheetService sheet = side.sheet().apply(finance);
        var feeding = catalog.createCategory(2026, "Feeding");
        var maize = catalog.createItem(feeding.id(), "Maize flour", "kg");
        var beans = catalog.createItem(feeding.id(), "Beans", "kg");
        var other = catalog.createItem(catalog.createCategory(2026, "Other").id(), "Soap", "bars");
        record(sheet, maize.id(), t(2026, 1), 1, "12.5", 3_500);
        record(sheet, beans.id(), t(2026, 1), 3, "2", 10_000);
        record(sheet, maize.id(), t(2026, 1), 3, "1", 1_000);
        record(sheet, maize.id(), t(2026, 2), 1, "100", 100);
        record(sheet, other.id(), t(2026, 1), 1, "1", 999);

        MonthlyAmounts totals = sheet.categoryTotals(feeding.id(), t(2026, 1));

        assertThat(totals).isEqualTo(new MonthlyAmounts(Ugx.of(43_750), Ugx.ZERO, Ugx.of(21_000)));
        assertThat(totals.total()).isEqualTo(Ugx.of(64_750));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sides")
    @DisplayName("AC-7: the expected/budgeted amount can be set to 0 or more, changed and cleared")
    void plans(Side side) {
        Catalog catalog = side.catalog().apply(finance);
        LedgerSheetService sheet = side.sheet().apply(finance);
        var feeding = catalog.createCategory(2026, "Feeding");

        assertThat(sheet.plan(feeding.id(), t(2026, 1))).isEmpty();
        sheet.setPlan(feeding.id(), t(2026, 1), Ugx.ZERO);
        assertThat(sheet.plan(feeding.id(), t(2026, 1))).contains(Ugx.ZERO);
        sheet.setPlan(feeding.id(), t(2026, 1), Ugx.of(500_000));
        assertThat(sheet.plan(feeding.id(), t(2026, 1))).contains(Ugx.of(500_000));
        assertThat(sheet.plan(feeding.id(), t(2026, 2))).isEmpty();
        assertThat(liveRows(side.planTable())).isEqualTo(1);

        sheet.setPlan(feeding.id(), t(2026, 1), null);
        assertThat(sheet.plan(feeding.id(), t(2026, 1))).isEmpty();
        assertThatThrownBy(() -> sheet.setPlan(feeding.id(), t(2026, 1), Ugx.of(-1)))
                .isInstanceOf(ValidationException.class);
    }
}

package org.angelscare.management.accounts.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;
import java.util.stream.Stream;
import org.angelscare.management.accounts.model.SummaryRow;
import org.angelscare.management.accounts.ui.DetailViewModel.CategorySection;
import org.angelscare.management.accounts.ui.DetailViewModel.Cell;
import org.angelscare.management.accounts.ui.DetailViewModel.ItemLine;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.student.model.SchoolClass;
import org.angelscare.management.support.ScreenTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Pages 3 and 4: the same view model, for income and for expenses. */
class DetailViewModelTest extends ScreenTest {

    record Side(String label, Function<DetailViewModelTest, DetailViewModel> page, boolean income,
            String title, String planLabel, String entryWord) {
        @Override
        public String toString() {
            return label;
        }
    }

    static Stream<Side> sides() {
        return Stream.of(
                new Side("income", DetailViewModelTest::incomePage, true, "Detailed incomes",
                        "Expected", "income entry"),
                new Side("expense", DetailViewModelTest::expensePage, false, "Detailed expenses",
                        "Budgeted", "expense entry"));
    }

    @BeforeEach
    void schoolYears() {
        createYear(2026);
        createYear(2027);
    }

    private DetailViewModel open(Side side) {
        DetailViewModel page = side.page().apply(this);
        page.show(t(2026, 1), null);
        return page;
    }

    private static CategorySection section(DetailViewModel page, String name) {
        return page.sections().stream().filter(s -> s.category().name().equals(name)).findFirst()
                .orElseThrow();
    }

    private static ItemLine line(CategorySection section, String name) {
        return section.lines().stream().filter(l -> l.item().name().equals(name)).findFirst()
                .orElseThrow();
    }

    private DetailViewModel withMaize(Side side) {
        DetailViewModel page = open(side);
        assertThat(page.addCategory("Feeding")).as(page.error()).isTrue();
        assertThat(page.addItem(section(page, "Feeding"), "Maize flour", "kg")).as(page.error())
                .isTrue();
        return page;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sides")
    @DisplayName("AC-12: the page's title, plan label and empty hint")
    void labels(Side side) {
        DetailViewModel page = open(side);

        assertThat(page.title()).isEqualTo(side.title());
        assertThat(page.planLabel()).isEqualTo(side.planLabel());
        assertThat(page.sections()).isEmpty();
        assertThat(page.emptyHint()).isEqualTo(side.income()
                ? "No income categories yet. Click Add category."
                : "No expense categories yet. Click Add category.");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sides")
    @DisplayName("AC-12: entering quantity and rate updates the amount, the totals and page 2")
    void editsUpdateTotals(Side side) {
        DetailViewModel page = withMaize(side);
        page.monthProperty().set(2);
        CategorySection feeding = section(page, "Feeding");

        assertThat(page.editQuantity(line(feeding, "Maize flour"), "12.5")).isTrue();
        assertThat(line(section(page, "Feeding"), "Maize flour").amountTextProperty().get())
                .isEmpty();
        assertThat(page.editRate(line(section(page, "Feeding"), "Maize flour"), "3,500")).isTrue();

        feeding = section(page, "Feeding");
        ItemLine maize = line(feeding, "Maize flour");
        assertThat(maize.quantityTextProperty().get()).isEqualTo("12.5");
        assertThat(maize.rateTextProperty().get()).isEqualTo("3,500");
        assertThat(maize.amountTextProperty().get()).isEqualTo("43,750");
        assertThat(feeding.monthTotalProperty().get()).isEqualTo(Ugx.of(43_750));
        assertThat(feeding.termTotalProperty().get()).isEqualTo(Ugx.of(43_750));

        var summary = finance.accounts.summary(t(2026, 1));
        SummaryRow row = (side.income() ? summary.income() : summary.expense()).stream()
                .filter(r -> "Feeding".equals(r.name())).findFirst().orElseThrow();
        assertThat(row.actual().month(2)).isEqualTo(Ugx.of(43_750));

        page.monthProperty().set(1);
        feeding = section(page, "Feeding");
        assertThat(line(feeding, "Maize flour").quantityTextProperty().get()).isEmpty();
        assertThat(line(feeding, "Maize flour").amountTextProperty().get()).isEmpty();
        assertThat(feeding.monthTotalProperty().get()).isEqualTo(Ugx.ZERO);
        assertThat(feeding.termTotalProperty().get()).isEqualTo(Ugx.of(43_750));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sides")
    @DisplayName("AC-12: the expected/budgeted amount can be typed, changed and cleared")
    void plan(Side side) {
        DetailViewModel page = withMaize(side);

        assertThat(page.editPlan(section(page, "Feeding"), "500,000")).isTrue();
        assertThat(section(page, "Feeding").planTextProperty().get()).isEqualTo("500,000");
        assertThat(page.editPlan(section(page, "Feeding"), "-1")).isFalse();
        assertThat(page.editPlan(section(page, "Feeding"), "")).isTrue();
        assertThat(section(page, "Feeding").planTextProperty().get()).isEmpty();
    }

    @Test
    @DisplayName("AC-12: the income page's Students row matches page 2's")
    void studentsRow() {
        var amina = createStudent("Amina", "Nakato", SchoolClass.P7);
        finance.studentAccounts.setClassFee(SchoolClass.P7, t(2026, 1), Ugx.of(300_000),
                Ugx.of(10_000));
        finance.studentAccounts.openTerm(t(2026, 1));
        finance.studentAccounts.setPayment(amina.id(), t(2026, 1), 2, Ugx.of(120_000));

        DetailViewModel page = incomePage();
        page.show(t(2026, 1), null);
        page.monthProperty().set(3);

        var students = page.studentsRowProperty().get();
        SummaryRow onPage2 = finance.accounts.summary(t(2026, 1)).income().get(0);
        assertThat(students.expected()).isEqualTo(onPage2.planned()).isEqualTo(Ugx.of(310_000));
        assertThat(students.total()).isEqualTo(onPage2.total()).isEqualTo(Ugx.of(120_000));
        DetailViewModel expenses = expensePage();
        expenses.show(t(2026, 1), null);
        assertThat(expenses.studentsRowProperty().get()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sides")
    @DisplayName("AC-13: an unreadable cell is marked, the message names the item, nothing is saved")
    void invalidCells(Side side) {
        DetailViewModel page = withMaize(side);

        assertThat(page.editQuantity(line(section(page, "Feeding"), "Maize flour"), "1.234"))
                .isFalse();
        ItemLine maize = line(section(page, "Feeding"), "Maize flour");
        assertThat(maize.invalidCellProperty().get()).isEqualTo(Cell.QUANTITY);
        assertThat(page.error())
                .isEqualTo("Maize flour: Enter a quantity with up to 2 decimals, e.g. 12.5.");

        assertThat(page.editRate(maize, "-5")).isFalse();
        assertThat(line(section(page, "Feeding"), "Maize flour").invalidCellProperty().get())
                .isEqualTo(Cell.RATE);
        assertThat(page.error())
                .isEqualTo("Maize flour: Enter a whole amount in shillings, e.g. 300,000.");
        String itemId = maize.item().id();
        var sheet = side.income() ? finance.incomeSheet : finance.expenseSheet;
        assertThat(sheet.entry(itemId, t(2026, 1), 1).quantity()).isNull();
        assertThat(sheet.entry(itemId, t(2026, 1), 1).rate()).isNull();

        assertThat(page.editRate(line(section(page, "Feeding"), "Maize flour"), "3500")).isTrue();
        assertThat(line(section(page, "Feeding"), "Maize flour").invalidCellProperty().get())
                .isNull();
        assertThat(page.error()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sides")
    @DisplayName("AC-14: opened from a category row, that category is the one to scroll to")
    void focus(Side side) {
        DetailViewModel page = withMaize(side);
        String feedingId = section(page, "Feeding").category().id();

        page.show(t(2026, 1), feedingId);
        assertThat(page.focusCategoryId()).isEqualTo(feedingId);

        page.show(t(2026, 1), null);
        assertThat(page.focusCategoryId()).isNull();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sides")
    @DisplayName("AC-15: deleting asks first; an item with entries can't be deleted, one without can")
    void deleteItems(Side side) {
        DetailViewModel page = withMaize(side);
        assertThat(page.addItem(section(page, "Feeding"), "Beans", "kg")).isTrue();
        page.editQuantity(line(section(page, "Feeding"), "Maize flour"), "1");
        dialogs.answerConfirm(true).answerConfirm(false).answerConfirm(true);

        assertThat(page.deleteItem(line(section(page, "Feeding"), "Maize flour"))).isFalse();
        assertThat(page.error()).contains(side.entryWord());
        assertThat(page.deleteItem(line(section(page, "Feeding"), "Beans"))).isFalse();
        assertThat(section(page, "Feeding").lines()).hasSize(2);
        assertThat(page.deleteItem(line(section(page, "Feeding"), "Beans"))).isTrue();

        assertThat(dialogs.asked).containsExactly("Delete 'Maize flour'?", "Delete 'Beans'?",
                "Delete 'Beans'?");
        assertThat(section(page, "Feeding").lines()).extracting(l -> l.item().name())
                .containsExactly("Maize flour");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sides")
    @DisplayName("AC-15: a category with a plan can't be deleted; an empty one can")
    void deleteCategories(Side side) {
        DetailViewModel page = open(side);
        page.addCategory("Feeding");
        page.addCategory("Transport");
        page.editPlan(section(page, "Feeding"), "0");
        dialogs.answerConfirm(true).answerConfirm(true);

        assertThat(page.deleteCategory(section(page, "Feeding"))).isFalse();
        assertThat(page.deleteCategory(section(page, "Transport"))).isTrue();

        assertThat(page.sections()).extracting(s -> s.category().name()).containsExactly("Feeding");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sides")
    @DisplayName("AC-4: an item added in Term 2, month 3 is blank in every term and month of its year")
    void itemsBelongToTheYear(Side side) {
        DetailViewModel page = side.page().apply(this);
        page.show(t(2026, 2), null);
        page.monthProperty().set(3);
        page.addCategory("Feeding");

        assertThat(page.addItem(section(page, "Feeding"), "Maize flour", "kg")).isTrue();

        for (int term = 1; term <= 3; term++) {
            page.show(t(2026, term), null);
            for (int month = 1; month <= 3; month++) {
                page.monthProperty().set(month);
                ItemLine maize = line(section(page, "Feeding"), "Maize flour");
                assertThat(maize.item().unit()).isEqualTo("kg");
                assertThat(maize.quantityTextProperty().get()).isEmpty();
                assertThat(maize.rateTextProperty().get()).isEmpty();
            }
        }
        page.show(t(2027, 1), null);
        assertThat(page.sections()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("sides")
    @DisplayName("AC-2: categories and items are added, renamed and checked on the page")
    void addAndRename(Side side) {
        DetailViewModel page = withMaize(side);

        assertThat(page.addCategory("feeding")).isFalse();
        assertThat(page.error()).contains("already exists");
        assertThat(page.addItem(section(page, "Feeding"), "Beans", " ")).isFalse();
        assertThat(page.error()).contains("Unit");

        assertThat(page.addCategory("Administrative Costs")).isTrue();
        assertThat(page.sections()).extracting(s -> s.category().name())
                .containsExactly("Administrative Costs", "Feeding");
        assertThat(page.renameCategory(section(page, "Feeding"), "Food")).isTrue();
        assertThat(page.editItem(line(section(page, "Food"), "Maize flour"), "Maize", "bags"))
                .isTrue();
        assertThat(line(section(page, "Food"), "Maize").item().unit()).isEqualTo("bags");
    }
}

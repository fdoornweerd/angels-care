package org.angelscare.management.calendar.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.angelscare.management.accounts.model.YearTotals;
import org.angelscare.management.calendar.model.SchoolYear;
import org.angelscare.management.calendar.ui.SchoolYearsViewModel.YearRow;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.support.ScreenTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SchoolYearsViewModelTest extends ScreenTest {

    private SchoolYearsViewModel openPage() {
        SchoolYearsViewModel page = schoolYearsPage();
        page.refresh();
        return page;
    }

    private static void fillDates(SchoolYearsViewModel page, int year) {
        page.termStart(1).set(LocalDate.of(year, 2, 2));
        page.termEnd(1).set(LocalDate.of(year, 4, 24));
        page.termStart(2).set(LocalDate.of(year, 5, 18));
        page.termEnd(2).set(LocalDate.of(year, 8, 14));
        page.termStart(3).set(LocalDate.of(year, 9, 7));
        page.termEnd(3).set(LocalDate.of(year, 12, 4));
    }

    @Test
    @DisplayName("AC-23: with no school years the list is empty and shows the hint")
    void empty() {
        SchoolYearsViewModel page = openPage();

        assertThat(page.rows()).isEmpty();
        assertThat(page.emptyHint())
                .isEqualTo("No school years yet. Click New school year to set one up.");
    }

    @Test
    @DisplayName("AC-23: years newest first, each with its totals over its three terms")
    void listWithTotals() {
        createYear(2025);
        createYear(2026);
        record(finance.incomeSheet, incomeItem(2026, "Donations", "Church", "gifts").id(),
                t(2026, 1), 1, "1", 400_000);
        record(finance.expenseSheet, expenseItem(2026, "Feeding", "Maize", "kg").id(),
                t(2026, 3), 2, "100", 5_000);

        SchoolYearsViewModel page = openPage();

        assertThat(page.rows()).extracting(row -> row.year().label())
                .containsExactly("2026-2027", "2025-2026");
        YearRow y2026 = page.rows().get(0);
        assertThat(y2026.totals()).isEqualTo(new YearTotals(Ugx.of(400_000), Ugx.of(500_000)));
        assertThat(y2026.totals().surplus()).isEqualTo(Ugx.of(-100_000));
        assertThat(page.rows().get(1).totals()).isEqualTo(new YearTotals(Ugx.ZERO, Ugx.ZERO));
    }

    @Test
    @DisplayName("AC-23: the first year: no copy offer; saving creates it")
    void firstYear() {
        SchoolYearsViewModel page = openPage();

        page.newYear();
        assertThat(page.editingProperty().get()).isTrue();
        assertThat(page.startYearProperty().get()).isEqualTo(2026);
        assertThat(page.yearNameProperty().get()).isEqualTo("2026-2027");
        assertThat(page.copyOfferedProperty().get()).isFalse();
        fillDates(page, 2026);

        assertThat(page.save()).as(page.error()).isTrue();

        assertThat(page.editingProperty().get()).isFalse();
        assertThat(page.rows()).extracting(row -> row.year().year()).containsExactly(2026);
    }

    @Test
    @DisplayName("AC-23/AC-3: a later year offers to copy the latest earlier year, ticked")
    void copyOffer() {
        createYear(2026);
        finance.expenses.createCategory(2026, "Feeding");
        SchoolYearsViewModel page = openPage();

        page.newYear();

        assertThat(page.startYearProperty().get()).isEqualTo(2027);
        assertThat(page.copyOfferedProperty().get()).isTrue();
        assertThat(page.copyLabelProperty().get())
                .isEqualTo("Copy income and expense categories and items from 2026-2027");
        assertThat(page.copyFromPreviousProperty().get()).isTrue();

        fillDates(page, 2027);
        assertThat(page.save()).as(page.error()).isTrue();
        assertThat(finance.expenses.listCategories(2027)).hasSize(1);
    }

    @Test
    @DisplayName("AC-23: the copy offer follows the typed start year")
    void copyOfferFollowsYear() {
        createYear(2026);
        SchoolYearsViewModel page = openPage();
        page.newYear();

        page.startYearProperty().set(2025);

        assertThat(page.copyOfferedProperty().get()).isFalse();
        assertThat(page.yearNameProperty().get()).isEqualTo("2025-2026");
    }

    @Test
    @DisplayName("AC-23: invalid dates show 001's message and create nothing")
    void invalidDates() {
        SchoolYearsViewModel page = openPage();
        page.newYear();
        fillDates(page, 2026);
        page.termStart(2).set(LocalDate.of(2026, 4, 1));

        assertThat(page.save()).isFalse();

        assertThat(page.error()).isEqualTo("Term 2 must start after Term 1 ends.");
        assertThat(page.editingProperty().get()).isTrue();
        assertThat(finance.calendar.listYears()).isEmpty();
    }

    @Test
    @DisplayName("AC-23: a year's term dates can be edited")
    void editDates() {
        SchoolYear year = createYear(2026);
        SchoolYearsViewModel page = openPage();

        page.edit(year);
        assertThat(page.copyOfferedProperty().get()).isFalse();
        page.termEnd(2).set(LocalDate.of(2026, 8, 21));

        assertThat(page.save()).as(page.error()).isTrue();
        assertThat(finance.calendar.requireTerm(t(2026, 2)).dates().end())
                .isEqualTo(LocalDate.of(2026, 8, 21));
    }

    @Test
    @DisplayName("AC-23: deleting asks first and is blocked while the year has categories")
    void delete() {
        SchoolYear year = createYear(2026);
        finance.expenses.createCategory(2026, "Feeding");
        SchoolYearsViewModel page = openPage();
        dialogs.answerConfirm(true);

        assertThat(page.delete(year)).isFalse();

        assertThat(dialogs.asked).containsExactly("Delete 'School year 2026-2027'?");
        assertThat(page.error()).contains("expense category");
        assertThat(page.rows()).hasSize(1);
    }
}

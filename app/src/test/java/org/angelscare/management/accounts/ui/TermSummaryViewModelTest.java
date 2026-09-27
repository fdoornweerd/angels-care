package org.angelscare.management.accounts.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.angelscare.management.accounts.model.SummaryRow;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.support.ScreenTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TermSummaryViewModelTest extends ScreenTest {

    @Test
    @DisplayName("AC-10: an empty term: the Students row only, the expense hint, zeros")
    void emptyTerm() {
        createYear(2026);
        TermSummaryViewModel page = termSummaryPage();

        page.show(t(2026, 1));

        assertThat(page.incomeRows()).extracting(SummaryRow::name).containsExactly("Students");
        assertThat(page.expenseRows()).isEmpty();
        assertThat(page.expenseHint())
                .isEqualTo("No expense categories yet. Add them on Detailed expenses.");
        assertThat(page.summaryProperty().get().surplus()).isEqualTo(Ugx.ZERO);
        assertThat(page.surplusLabel()).isEqualTo("Surplus");
    }

    @Test
    @DisplayName("AC-9: a negative result is labelled Deficit")
    void deficit() {
        createYear(2026);
        record(finance.expenseSheet, expenseItem(2026, "Feeding", "Maize", "kg").id(),
                t(2026, 1), 1, "1", 1_000);
        TermSummaryViewModel page = termSummaryPage();

        page.show(t(2026, 1));

        assertThat(page.summaryProperty().get().surplus()).isEqualTo(Ugx.of(-1_000));
        assertThat(page.surplusLabel()).isEqualTo("Deficit");
        assertThat(page.expenseRows()).extracting(SummaryRow::name).containsExactly("Feeding");
    }
}

package org.angelscare.management.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MonthlyAmountsTest {

    private final MonthlyAmounts amounts =
            new MonthlyAmounts(Ugx.of(100), Ugx.of(0), Ugx.of(250));

    @Test
    @DisplayName("AC-7: months 1-3 are read by number and add up to the term total")
    void monthsAndTotal() {
        assertThat(amounts.month(1)).isEqualTo(Ugx.of(100));
        assertThat(amounts.month(3)).isEqualTo(Ugx.of(250));
        assertThat(amounts.total()).isEqualTo(Ugx.of(350));
        assertThat(MonthlyAmounts.ZERO.total()).isEqualTo(Ugx.ZERO);
        assertThatThrownBy(() -> amounts.month(4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> amounts.month(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("AC-7: months are replaced and added month by month")
    void withAndPlus() {
        assertThat(amounts.with(2, Ugx.of(50)))
                .isEqualTo(new MonthlyAmounts(Ugx.of(100), Ugx.of(50), Ugx.of(250)));
        assertThat(amounts.plus(amounts))
                .isEqualTo(new MonthlyAmounts(Ugx.of(200), Ugx.ZERO, Ugx.of(500)));
    }
}

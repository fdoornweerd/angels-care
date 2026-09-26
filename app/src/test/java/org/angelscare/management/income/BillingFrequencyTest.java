package org.angelscare.management.income;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.angelscare.management.common.Ugx;
import org.angelscare.management.income.model.BillingFrequency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BillingFrequencyTest {

    @Test
    @DisplayName("AC-19: a per-term fee totals its amount; a per-month fee totals three months")
    void termTotals() {
        assertThat(BillingFrequency.PER_TERM.termTotal(Ugx.of(250_000))).isEqualTo(Ugx.of(250_000));
        assertThat(BillingFrequency.PER_MONTH.termTotal(Ugx.of(100_000))).isEqualTo(Ugx.of(300_000));
        assertThat(BillingFrequency.PER_MONTH.termTotal(Ugx.of(1))).isEqualTo(Ugx.of(3));
    }

    @Test
    @DisplayName("AC-19: a term total too large for a long throws instead of wrapping around")
    void termTotalOverflow() {
        assertThatThrownBy(() -> BillingFrequency.PER_MONTH.termTotal(Ugx.of(Long.MAX_VALUE / 2)))
                .isInstanceOf(ArithmeticException.class);
    }
}

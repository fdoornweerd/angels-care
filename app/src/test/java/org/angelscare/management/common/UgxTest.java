package org.angelscare.management.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UgxTest {

    @Test
    @DisplayName("AC-4: amounts format with thousands separators and no decimals")
    void formatsWholeShillings() {
        assertThat(Ugx.of(1_250_000).format()).isEqualTo("UGX 1,250,000");
        assertThat(Ugx.of(999).format()).isEqualTo("UGX 999");
    }

    @Test
    @DisplayName("AC-4: zero formats as UGX 0")
    void formatsZero() {
        assertThat(Ugx.of(0).format()).isEqualTo("UGX 0");
        assertThat(Ugx.ZERO.format()).isEqualTo("UGX 0");
    }

    @Test
    @DisplayName("AC-4: negative amounts format with a leading minus")
    void formatsNegative() {
        assertThat(Ugx.of(-5_000).format()).isEqualTo("-UGX 5,000");
    }

    @Test
    void addsAndSubtracts() {
        Ugx fee = Ugx.of(450_000);
        Ugx paid = Ugx.of(500_000);

        assertThat(fee.plus(paid)).isEqualTo(Ugx.of(950_000));
        assertThat(fee.minus(paid)).isEqualTo(Ugx.of(-50_000));
        assertThat(fee.minus(paid).isNegative()).isTrue();
        assertThat(Ugx.ZERO.isNegative()).isFalse();
    }

    @Test
    void multiplies() {
        assertThat(Ugx.of(100_000).times(3)).isEqualTo(Ugx.of(300_000));
        assertThat(Ugx.of(100_000).times(0)).isEqualTo(Ugx.ZERO);
        assertThatThrownBy(() -> Ugx.of(Long.MAX_VALUE).times(2))
                .isInstanceOf(ArithmeticException.class);
    }

    @Test
    void refusesToOverflowSilently() {
        // A wrapped-around long would turn a huge credit into a huge debt without any error.
        assertThatThrownBy(() -> Ugx.of(Long.MAX_VALUE).plus(Ugx.of(1)))
                .isInstanceOf(ArithmeticException.class);
    }
}

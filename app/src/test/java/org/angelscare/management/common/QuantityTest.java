package org.angelscare.management.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class QuantityTest {

    @ParameterizedTest(name = "\"{0}\" = {1} hundredths")
    @CsvSource(delimiter = '|', value = {
            "12.5|1250", "1,200.25|120025", "0|0", "3|300", " 12.50 |1250", "0.33|33", "0.05|5"})
    @DisplayName("AC-5: quantities with up to two decimals are read")
    void parses(String text, long hundredths) {
        assertThat(Quantity.parse(text)).isEqualTo(Quantity.ofHundredths(hundredths));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @NullSource
    @ValueSource(strings = {"", "  ", "-1", "1.234", "abc", "1.2.3", ",", ".", "12a"})
    @DisplayName("AC-5: negatives, three decimals, letters and blanks are refused with one message")
    void refuses(String text) {
        assertThatThrownBy(() -> Quantity.parse(text))
                .isInstanceOf(ValidationException.class)
                .hasMessage(Quantity.INVALID_MESSAGE);
    }

    @ParameterizedTest(name = "{0} hundredths -> \"{1}\"")
    @CsvSource(delimiter = '|', value = {
            "1250|12.5", "120025|1,200.25", "300|3", "33|0.33", "1200|12", "5|0.05", "0|0"})
    @DisplayName("AC-5: quantities are shown without trailing zeros")
    void formats(long hundredths, String text) {
        assertThat(Quantity.ofHundredths(hundredths).format()).isEqualTo(text);
    }

    @Test
    @DisplayName("AC-5: quantity × rate is rounded to the nearest shilling, halves up")
    void timesRounds() {
        assertThat(Quantity.parse("12.5").times(Ugx.of(3_500))).isEqualTo(Ugx.of(43_750));
        assertThat(Quantity.parse("0.33").times(Ugx.of(10))).isEqualTo(Ugx.of(3));
        assertThat(Quantity.parse("0.35").times(Ugx.of(10))).isEqualTo(Ugx.of(4));
        assertThat(Quantity.parse("0.05").times(Ugx.of(10))).isEqualTo(Ugx.of(1));
        assertThat(Quantity.parse("0").times(Ugx.of(3_500))).isEqualTo(Ugx.ZERO);
        assertThat(Quantity.parse("3").times(Ugx.ZERO)).isEqualTo(Ugx.ZERO);
    }

    @Test
    @DisplayName("AC-5: a product too large for a long throws instead of wrapping around")
    void timesOverflow() {
        assertThatThrownBy(() -> Quantity.ofHundredths(Long.MAX_VALUE / 10).times(Ugx.of(1_000)))
                .isInstanceOf(ArithmeticException.class);
    }
}

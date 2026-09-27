package org.angelscare.management.common.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.angelscare.management.common.Ugx;
import org.angelscare.management.common.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class UgxFieldTest {

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"300000", "300,000", "300 000", " UGX 300,000 ", "ugx300000"})
    @DisplayName("AC-7: whole shillings are read with or without separators and UGX")
    void parsesWholeShillings(String text) {
        assertThat(UgxField.parse(text)).isEqualTo(Ugx.of(300_000));
    }

    @Test
    @DisplayName("AC-7: zero is a valid amount")
    void parsesZero() {
        assertThat(UgxField.parse("0")).isEqualTo(Ugx.ZERO);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @NullSource
    @ValueSource(strings = {"", "   ", "1.5", "300,000.00", "-5", "12a", "UGX", ",",
            "1,00,0000,000,000,000,000,000"})
    @DisplayName("AC-7: anything but a whole, non-negative amount is refused with one message")
    void refusesOtherText(String text) {
        assertThatThrownBy(() -> UgxField.parse(text))
                .isInstanceOf(ValidationException.class)
                .hasMessage(UgxField.INVALID_MESSAGE);
    }

    @Test
    @DisplayName("AC-7: amounts are shown grouped, without currency or decimals")
    void formats() {
        assertThat(UgxField.format(Ugx.of(300_000))).isEqualTo("300,000");
        assertThat(UgxField.format(Ugx.ZERO)).isEqualTo("0");
        assertThat(UgxField.format(Ugx.of(1_250_000))).isEqualTo("1,250,000");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"-5,000", " -5000 ", "-UGX 5,000", "UGX -5,000"})
    @DisplayName("AC-18 (002): a debt may be negative (a credit)")
    void parsesSigned(String text) {
        assertThat(UgxField.parseSigned(text)).isEqualTo(Ugx.of(-5_000));
        assertThat(UgxField.parseSigned("5,000")).isEqualTo(Ugx.of(5_000));
        assertThat(UgxField.parseSigned("-0")).isEqualTo(Ugx.ZERO);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "--5", "5-", "- ", "abc", "-1.5"})
    @DisplayName("AC-18 (002): a signed amount is still a whole number of shillings")
    void refusesBadSigned(String text) {
        assertThatThrownBy(() -> UgxField.parseSigned(text))
                .isInstanceOf(ValidationException.class)
                .hasMessage(UgxField.INVALID_MESSAGE);
    }
}

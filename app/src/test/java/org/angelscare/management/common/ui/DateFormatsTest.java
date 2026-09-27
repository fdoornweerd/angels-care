package org.angelscare.management.common.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import org.angelscare.management.common.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DateFormatsTest {

    @Test
    @DisplayName("AC-8: dates are shown and read as dd/MM/yyyy")
    void roundTrips() {
        assertThat(DateFormats.format(LocalDate.of(2026, 2, 2))).isEqualTo("02/02/2026");
        assertThat(DateFormats.parse("02/02/2026")).isEqualTo(LocalDate.of(2026, 2, 2));
        assertThat(DateFormats.parse(" 31/12/2026 ")).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(DateFormats.parse("29/02/2028")).isEqualTo(LocalDate.of(2028, 2, 29));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"31/02/2026", "29/02/2026", "2026-02-02", "02-02-2026", "2/2/26", ""})
    @DisplayName("AC-8: impossible dates and other formats are refused")
    void refusesOtherFormats(String text) {
        assertThatThrownBy(() -> DateFormats.parse(text))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("dd/mm/yyyy");
    }
}

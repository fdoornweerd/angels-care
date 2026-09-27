package org.angelscare.management.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.angelscare.management.support.FinanceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** CalendarService.defaultTerm. 2026-2027's terms in createYear: 02/02-24/04, 18/05-14/08, 07/09-04/12. */
class DefaultTermTest extends FinanceTest {

    @Test
    @DisplayName("with no school years there is no default term")
    void noYears() {
        assertThat(finance.calendar.defaultTerm(LocalDate.of(2026, 3, 1))).isEmpty();
    }

    @ParameterizedTest(name = "{0} -> 2026-2027 Term {1}")
    @CsvSource({
            "2026-06-10, 2",   // inside Term 2
            "2026-05-18, 2",   // Term 2's first day
            "2026-08-14, 2",   // Term 2's last day
            "2026-04-25, 2",   // the day after Term 1 ends: between terms, so the next one
            "2026-05-17, 2",   // the day before Term 2 starts
            "2026-12-05, 3",   // after the last term, with no 2027: the last term
            "2026-01-01, 1"})  // before the first term
    @DisplayName("the default term follows today")
    void followsToday(String today, int expectedTerm) {
        createYear(2026);

        assertThat(finance.calendar.defaultTerm(LocalDate.parse(today))).contains(t(2026, expectedTerm));
    }

    @Test
    @DisplayName("after the last term of one year, the next year's first term is next")
    void acrossYears() {
        createYear(2026);
        createYear(2027);

        assertThat(finance.calendar.defaultTerm(LocalDate.of(2026, 12, 20))).contains(t(2027, 1));
    }

    @Test
    @DisplayName("the default term follows today through a September-to-July year")
    void spanningYear() {
        finance.calendar.createYear(2026, dates("2026-09-07", "2026-12-04"),
                dates("2027-01-11", "2027-04-02"), dates("2027-04-26", "2027-07-30"));
        finance.calendar.createYear(2027, dates("2027-09-06", "2027-12-03"),
                dates("2028-01-10", "2028-03-31"), dates("2028-04-24", "2028-07-28"));

        assertThat(finance.calendar.defaultTerm(LocalDate.of(2027, 2, 1))).contains(t(2026, 2));
        assertThat(finance.calendar.defaultTerm(LocalDate.of(2027, 8, 15))).contains(t(2027, 1));
        assertThat(finance.calendar.defaultTerm(LocalDate.of(2026, 12, 20))).contains(t(2026, 2));
    }
}

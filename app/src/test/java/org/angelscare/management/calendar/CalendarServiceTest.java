package org.angelscare.management.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.stream.Stream;
import org.angelscare.management.calendar.model.SchoolYear;
import org.angelscare.management.calendar.model.Term;
import org.angelscare.management.calendar.model.TermDates;
import org.angelscare.management.common.ValidationException;
import org.angelscare.management.support.FinanceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class CalendarServiceTest extends FinanceTest {

    @Test
    @DisplayName("AC-6: creating a school year creates Terms 1-3 with the given dates")
    void createsYearWithThreeTerms() {
        SchoolYear year = createYear(2026);

        assertThat(year.year()).isEqualTo(2026);
        assertThat(year.terms()).extracting(term -> term.ref().number()).containsExactly(1, 2, 3);
        assertThat(year.terms()).extracting(term -> term.ref().year()).containsOnly(2026);
        assertThat(year.terms().get(1).dates())
                .isEqualTo(dates("2026-05-18", "2026-08-14"));
        assertThat(finance.calendar.findYear(2026)).contains(year);
        assertThat(finance.calendar.requireTerm(t(2026, 3)).dates())
                .isEqualTo(dates("2026-09-07", "2026-12-04"));
    }

    @Test
    @DisplayName("AC-6: with no school years, the list is empty and nothing is found")
    void emptyCalendar() {
        assertThat(finance.calendar.listYears()).isEmpty();
        assertThat(finance.calendar.findYear(2026)).isEmpty();
    }

    @Test
    @DisplayName("AC-6: years are listed in order")
    void listsYearsInOrder() {
        createYear(2027);
        createYear(2026);

        assertThat(finance.calendar.listYears()).extracting(SchoolYear::year)
                .containsExactly(2026, 2027);
    }

    @Test
    @DisplayName("AC-7: the same year cannot be created twice")
    void rejectsDuplicateYear() {
        createYear(2026);

        assertThatThrownBy(() -> createYear(2026))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("2026");
        assertThat(finance.calendar.listYears()).hasSize(1);
    }

    static Stream<Arguments> invalidTermDates() {
        return Stream.of(
                Arguments.of("term 1 starts after it ends",
                        d("2026-04-24", "2026-02-02"), d("2026-05-18", "2026-08-14"),
                        d("2026-09-07", "2026-12-04")),
                Arguments.of("term 2 starts on term 1's last day",
                        d("2026-02-02", "2026-04-24"), d("2026-04-24", "2026-08-14"),
                        d("2026-09-07", "2026-12-04")),
                Arguments.of("terms 2 and 3 overlap",
                        d("2026-02-02", "2026-04-24"), d("2026-05-18", "2026-09-10"),
                        d("2026-09-07", "2026-12-04")),
                Arguments.of("term 2 comes before term 1",
                        d("2026-05-18", "2026-08-14"), d("2026-02-02", "2026-04-24"),
                        d("2026-09-07", "2026-12-04")),
                Arguments.of("term 1 starts in the previous year",
                        d("2025-12-30", "2026-04-24"), d("2026-05-18", "2026-08-14"),
                        d("2026-09-07", "2026-12-04")),
                Arguments.of("term 3 ends after the end of the following year",
                        d("2026-09-07", "2026-12-04"), d("2027-01-11", "2027-04-02"),
                        d("2027-04-26", "2028-01-05")),
                Arguments.of("term 1 starts in the following year",
                        d("2027-01-11", "2027-04-02"), d("2027-04-26", "2027-07-30"),
                        d("2027-09-06", "2027-12-03")),
                Arguments.of("a date is missing",
                        d("2026-02-02", "2026-04-24"), new TermDates(LocalDate.parse("2026-05-18"), null),
                        d("2026-09-07", "2026-12-04")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTermDates")
    @DisplayName("AC-8: invalid term dates are rejected and no year is created")
    void rejectsInvalidTermDates(String description, TermDates t1, TermDates t2, TermDates t3) {
        assertThatThrownBy(() -> finance.calendar.createYear(2026, t1, t2, t3))
                .isInstanceOf(ValidationException.class);
        assertThat(finance.calendar.findYear(2026)).isEmpty();
    }

    @Test
    @DisplayName("AC-8: adjacent terms, one-day terms and the year's first and last days are accepted")
    void acceptsBoundaryDates() {
        SchoolYear year = finance.calendar.createYear(2026,
                dates("2026-01-01", "2026-04-30"),
                dates("2026-05-01", "2026-05-01"),
                dates("2026-05-02", "2026-12-31"));

        assertThat(year.terms()).hasSize(3);
    }

    @Test
    @DisplayName("AC-8: term dates can be edited under the same rules")
    void editsTermDates() {
        createYear(2026);

        Term edited = finance.calendar.updateTermDates(t(2026, 2), dates("2026-05-11", "2026-08-21"));

        assertThat(edited.dates()).isEqualTo(dates("2026-05-11", "2026-08-21"));
        assertThat(finance.calendar.requireTerm(t(2026, 2)).dates())
                .isEqualTo(dates("2026-05-11", "2026-08-21"));
    }

    @Test
    @DisplayName("AC-8: an edit that makes terms overlap is rejected and changes nothing")
    void rejectsOverlappingEdit() {
        createYear(2026);

        assertThatThrownBy(() -> finance.calendar.updateTermDates(t(2026, 2),
                dates("2026-05-18", "2026-09-07")))
                .isInstanceOf(ValidationException.class);
        assertThat(finance.calendar.requireTerm(t(2026, 2)).dates())
                .isEqualTo(dates("2026-05-18", "2026-08-14"));
    }

    @Test
    @DisplayName("AC-15: asking for a term before any school year exists says to set one up")
    void noSchoolYearYet() {
        assertThatThrownBy(() -> finance.calendar.requireTerm(t(2026, 1)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("school year");
    }

    @Test
    @DisplayName("AC-15: asking for a term in a year that does not exist names the year")
    void unknownSchoolYear() {
        createYear(2026);

        assertThatThrownBy(() -> finance.calendar.requireTerm(t(2027, 1)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("2027");
        assertThatThrownBy(() -> finance.calendar.requireTerm(t(2026, 4)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("AC-26: deleting an unused year soft-deletes it and its three terms")
    void deletesYearWithItsTerms() {
        SchoolYear year = createYear(2026);

        finance.calendar.deleteYear(year.id());

        assertThat(isSoftDeleted("school_year", year.id())).isTrue();
        for (Term term : year.terms()) {
            assertThat(isSoftDeleted("term", term.id())).as(term.ref().toString()).isTrue();
        }
        assertThat(finance.calendar.findYear(2026)).isEmpty();
        assertThat(finance.calendar.listYears()).isEmpty();
        // The year can be set up again from scratch.
        assertThat(createYear(2026).id()).isNotEqualTo(year.id());
    }

    @Test
    @DisplayName("changed later: all three terms can be moved in one edit")
    void movesAllTermsAtOnce() {
        createYear(2026);

        // Every term moves a month later. Moved one at a time, Term 1 would first overlap the old Term 2.
        SchoolYear moved = finance.calendar.updateTermDates(2026,
                dates("2026-03-02", "2026-05-22"),
                dates("2026-06-15", "2026-09-11"),
                dates("2026-10-05", "2026-12-18"));

        assertThat(moved.terms()).extracting(Term::dates).containsExactly(
                dates("2026-03-02", "2026-05-22"),
                dates("2026-06-15", "2026-09-11"),
                dates("2026-10-05", "2026-12-18"));
        assertThat(finance.calendar.requireTerm(t(2026, 3)).dates())
                .isEqualTo(dates("2026-10-05", "2026-12-18"));
    }

    @Test
    @DisplayName("changed later: an invalid three-term edit changes none of them")
    void invalidThreeTermEdit() {
        createYear(2026);

        assertThatThrownBy(() -> finance.calendar.updateTermDates(2026,
                dates("2026-03-02", "2026-05-22"),
                dates("2026-05-01", "2026-09-11"),
                dates("2026-10-05", "2026-12-18")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("Term 2 must start after Term 1 ends.");
        assertThat(finance.calendar.requireTerm(t(2026, 1)).dates())
                .isEqualTo(dates("2026-02-02", "2026-04-24"));
    }

    @Test
    @DisplayName("AC-8 (changed later): a school year may run into the next calendar year")
    void spansTwoCalendarYears() {
        SchoolYear year = createSpanningYear(2026);

        assertThat(year.year()).isEqualTo(2026);
        assertThat(year.label()).isEqualTo("2026-2027");
        assertThat(year.terms().get(2).dates()).isEqualTo(dates("2027-04-26", "2027-07-30"));
        assertThat(finance.calendar.requireTerm(t(2026, 2)).dates().start())
                .isEqualTo(LocalDate.of(2027, 1, 11));
    }

    @Test
    @DisplayName("AC-8 (changed later): the last possible day is 31 December of the following year")
    void latestEnd() {
        SchoolYear year = finance.calendar.createYear(2026,
                dates("2026-12-01", "2026-12-31"), dates("2027-01-01", "2027-06-30"),
                dates("2027-07-01", "2027-12-31"));

        assertThat(year.terms()).hasSize(3);
    }

    @Test
    @DisplayName("AC-8 (changed later): consecutive school years may follow each other directly")
    void consecutiveYears() {
        createSpanningYear(2026);

        SchoolYear next = createSpanningYear(2027);

        assertThat(finance.calendar.listYears()).extracting(SchoolYear::label)
                .containsExactly("2026-2027", "2027-2028");
        assertThat(next.terms().get(0).dates().start()).isEqualTo(LocalDate.of(2027, 9, 7));
    }

    @Test
    @DisplayName("AC-8 (changed later): a school year may not overlap the one before it")
    void overlapWithPreviousYear() {
        createSpanningYear(2026);

        // Term 1 of 2027-2028 would start before 2026-2027's Term 3 (ends 30/07/2027) is over.
        assertThatThrownBy(() -> finance.calendar.createYear(2027,
                dates("2027-07-15", "2027-12-03"), dates("2028-01-10", "2028-03-31"),
                dates("2028-04-24", "2028-07-28")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("2027-2028's Term 1 must start after 2026-2027's Term 3 ends.");
        assertThat(finance.calendar.findYear(2027)).isEmpty();
    }

    @Test
    @DisplayName("AC-8 (changed later): a school year may not overlap the one after it")
    void overlapWithNextYear() {
        createSpanningYear(2027);

        assertThatThrownBy(() -> finance.calendar.createYear(2026,
                dates("2026-09-07", "2026-12-04"), dates("2027-01-11", "2027-04-02"),
                dates("2027-04-26", "2027-09-10")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("2026-2027's Term 3 must end before 2027-2028's Term 1 starts.");
    }

    @Test
    @DisplayName("AC-8 (changed later): editing term dates is checked against the neighbouring years too")
    void editOverlapsNeighbour() {
        createSpanningYear(2026);
        createSpanningYear(2027);

        assertThatThrownBy(() -> finance.calendar.updateTermDates(t(2026, 3),
                dates("2027-04-26", "2027-09-10")))
                .isInstanceOf(ValidationException.class)
                .hasMessage("2026-2027's Term 3 must end before 2027-2028's Term 1 starts.");
        assertThat(finance.calendar.requireTerm(t(2026, 3)).dates().end())
                .isEqualTo(LocalDate.of(2027, 7, 30));
    }

    @Test
    @DisplayName("AC-7/AC-15 (changed later): messages name school years as 2026-2027")
    void messagesUseTheYearName() {
        createYear(2026);

        assertThatThrownBy(() -> createYear(2026)).hasMessage("School year 2026-2027 already exists.");
        assertThatThrownBy(() -> finance.calendar.requireTerm(t(2027, 1)))
                .hasMessage("School year 2027-2028 has not been set up yet.");
    }

    private static TermDates d(String start, String end) {
        return dates(start, end);
    }
}

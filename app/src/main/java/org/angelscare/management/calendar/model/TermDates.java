package org.angelscare.management.calendar.model;

import java.time.LocalDate;

/** First and last day of a term, both inclusive. */
public record TermDates(LocalDate start, LocalDate end) {

    public static TermDates of(LocalDate start, LocalDate end) {
        return new TermDates(start, end);
    }
}

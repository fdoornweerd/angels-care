package org.angelscare.management.calendar.model;

import java.util.Comparator;

/**
 * Names a term: Term {@code number} of the school year that starts in {@code year}. Ordered across
 * years, so 2026-2027 Term 3 comes before 2027-2028 Term 1.
 */
public record TermRef(int year, int number) implements Comparable<TermRef> {

    private static final Comparator<TermRef> ORDER =
            Comparator.comparingInt(TermRef::year).thenComparingInt(TermRef::number);

    public static TermRef of(int year, int number) {
        return new TermRef(year, number);
    }

    @Override
    public int compareTo(TermRef other) {
        return ORDER.compare(this, other);
    }

    /** For screens and messages: "2026-2027 Term 2". */
    public String label() {
        return SchoolYear.label(year) + " Term " + number;
    }
}

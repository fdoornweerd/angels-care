package org.angelscare.management.calendar.model;

import java.util.Comparator;

/**
 * Names a term the way the school does: "2026, Term 2". Ordered across years, so 2026 Term 3
 * comes before 2027 Term 1.
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

    /** For messages: "2026 Term 2". */
    public String label() {
        return year + " Term " + number;
    }
}

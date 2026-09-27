package org.angelscare.management.calendar.model;

import java.util.List;

/**
 * A school year, always with exactly three terms, in order. It starts in calendar year
 * {@code year} and may run into the next one, so it is named after both: "2026-2027".
 */
public record SchoolYear(String id, int year, List<Term> terms) {

    /** "2026-2027". */
    public String label() {
        return label(year);
    }

    /** The name of the school year that starts in {@code startYear}: "2026-2027". */
    public static String label(int startYear) {
        return startYear + "-" + (startYear + 1);
    }
}

package org.angelscare.management.accounts.model;

import org.angelscare.management.common.MonthlyAmounts;
import org.angelscare.management.common.Ugx;

/**
 * A row of page 2: a category, the Students row or a totals row, its expected or budgeted amount
 * ({@code planned}, null when not set) and what came in or went out each month.
 */
public record SummaryRow(String categoryId, String name, Ugx planned, MonthlyAmounts actual) {

    public static final String STUDENTS = "Students";
    public static final String TOTAL = "Total";

    public static SummaryRow students(Ugx expected, MonthlyAmounts paid) {
        return new SummaryRow(null, STUDENTS, expected, paid);
    }

    /** The Students row: it has no category. */
    public boolean isStudents() {
        return categoryId == null && STUDENTS.equals(name);
    }

    /** A table's totals row. */
    public boolean isTotal() {
        return categoryId == null && TOTAL.equals(name);
    }

    public Ugx total() {
        return actual.total();
    }
}

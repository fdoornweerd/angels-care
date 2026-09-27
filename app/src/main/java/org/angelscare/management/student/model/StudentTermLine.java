package org.angelscare.management.student.model;

import org.angelscare.management.common.MonthlyAmounts;
import org.angelscare.management.common.Ugx;

/**
 * One student's line on page 5 for one term. {@code amount}, {@code debt} and {@code ream} are
 * the values in force (the student's own, or the class fee / carried balance); the flags say
 * whether they were typed for this student. Payments are null while blank.
 */
public record StudentTermLine(
        String studentId,
        String name,
        StudentStatus status,
        SchoolClass schoolClass,
        Ugx amount,
        boolean amountOverridden,
        Ugx debt,
        boolean debtOverridden,
        Ugx ream,
        boolean reamOverridden,
        Ugx paid1,
        Ugx paid2,
        Ugx paid3,
        String remarks) {

    /** Amount + Debt + Ream: what is owed this term. */
    public Ugx total() {
        return amount.plus(debt).plus(ream);
    }

    /** The payments per month, blanks as 0. */
    public MonthlyAmounts payments() {
        return new MonthlyAmounts(orZero(paid1), orZero(paid2), orZero(paid3));
    }

    /** Total minus payments; negative means paid too much. */
    public Ugx balance() {
        return total().minus(payments().total());
    }

    private static Ugx orZero(Ugx amount) {
        return amount == null ? Ugx.ZERO : amount;
    }
}

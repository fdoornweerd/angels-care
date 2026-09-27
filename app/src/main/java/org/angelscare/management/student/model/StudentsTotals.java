package org.angelscare.management.student.model;

import org.angelscare.management.common.MonthlyAmounts;
import org.angelscare.management.common.Ugx;

/** The Students row on pages 2 and 3: what is owed this term, and what was paid each month. */
public record StudentsTotals(Ugx expected, MonthlyAmounts paid) {

    public Ugx total() {
        return paid.total();
    }
}

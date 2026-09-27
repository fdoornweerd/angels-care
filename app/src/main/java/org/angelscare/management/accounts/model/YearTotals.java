package org.angelscare.management.accounts.model;

import org.angelscare.management.common.Ugx;

/** A school year's money over its three terms, for page 1. */
public record YearTotals(Ugx incomeCollected, Ugx expenditure) {

    public Ugx surplus() {
        return incomeCollected.minus(expenditure);
    }
}

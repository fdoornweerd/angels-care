package org.angelscare.management.income.model;

import org.angelscare.management.calendar.model.TermMonth;
import org.angelscare.management.common.Ugx;

public enum BillingFrequency {
    /** Charged once per term. */
    PER_TERM(1),
    /** Charged in each of the term's three months. */
    PER_MONTH(TermMonth.PER_TERM);

    private final int chargesPerTerm;

    BillingFrequency(int chargesPerTerm) {
        this.chargesPerTerm = chargesPerTerm;
    }

    /** What {@code amount} at this frequency comes to over one term. */
    public Ugx termTotal(Ugx amount) {
        return amount.times(chargesPerTerm);
    }
}

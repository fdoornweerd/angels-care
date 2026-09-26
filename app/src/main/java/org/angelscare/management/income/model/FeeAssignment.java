package org.angelscare.management.income.model;

import org.angelscare.management.calendar.model.TermRange;
import org.angelscare.management.common.Ugx;

/** "Charge {@code target} {@code amount} {@code frequency} for {@code incomeItemId} during {@code terms}." */
public record FeeAssignment(
        String id,
        String incomeItemId,
        FeeTarget target,
        Ugx amount,
        BillingFrequency frequency,
        TermRange terms) {
}

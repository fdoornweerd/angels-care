package org.angelscare.management.income.model;

import org.angelscare.management.common.Ugx;

/**
 * A fee that applies to one student in one term, and the route it reaches them by: {@code via}
 * is the assignment's target, and {@code viaName} is how to show it ("Student", "Boarders", "P7").
 */
public record ApplicableFee(
        String assignmentId,
        IncomeCategory category,
        IncomeItem item,
        Ugx amount,
        BillingFrequency frequency,
        Ugx termTotal,
        FeeTarget via,
        String viaName) {
}

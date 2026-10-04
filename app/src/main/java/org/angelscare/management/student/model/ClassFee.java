package org.angelscare.management.student.model;

import org.angelscare.management.common.Ugx;

/**
 * A class's fees in a term (carried forward from the term they were set in): {@code amount} for
 * Day students, {@code boardingFee} for Boarding students, and the ream charge for both.
 */
public record ClassFee(SchoolClass schoolClass, Ugx amount, Ugx boardingFee, Ugx ream) {

    /** The Day or the Boarding fee. */
    public Ugx feeFor(Boarding boarding) {
        return boarding == Boarding.BOARDING ? boardingFee : amount;
    }
}

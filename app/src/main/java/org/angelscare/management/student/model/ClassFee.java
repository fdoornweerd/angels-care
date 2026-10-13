package org.angelscare.management.student.model;

import org.angelscare.management.common.Ugx;

/**
 * A class's ream charge in a term (carried forward from the term it was set in), the same for Day
 * and Boarding students. Each student's fee is their own Amount, typed in (spec 004).
 */
public record ClassFee(SchoolClass schoolClass, Ugx ream) {
}

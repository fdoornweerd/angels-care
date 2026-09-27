package org.angelscare.management.student.model;

import org.angelscare.management.common.Ugx;

/** A class's fee and ream charge in a term (carried forward from the term it was set in). */
public record ClassFee(SchoolClass schoolClass, Ugx amount, Ugx ream) {
}

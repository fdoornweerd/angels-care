package org.angelscare.management.accounts.model;

import java.util.Optional;
import org.angelscare.management.calendar.model.TermRef;
import org.angelscare.management.common.Quantity;
import org.angelscare.management.common.Ugx;

/**
 * What was recorded for one item in one month of a term: a quantity and a rate, either of which
 * may still be blank (null).
 */
public record ItemMonth(String itemId, TermRef term, int month, Quantity quantity, Ugx rate) {

    /** quantity × rate, or empty until both are filled in. */
    public Optional<Ugx> amount() {
        return quantity == null || rate == null
                ? Optional.empty()
                : Optional.of(quantity.times(rate));
    }
}

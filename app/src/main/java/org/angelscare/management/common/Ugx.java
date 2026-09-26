package org.angelscare.management.common;

import java.util.Locale;

/**
 * An amount of Ugandan shillings. UGX has no minor unit in practice, so this is a whole number of
 * shillings - never a fraction. Negative amounts are valid (a balance owed, a correction).
 *
 * <p>Arithmetic throws {@link ArithmeticException} on overflow rather than wrapping around, which
 * would silently turn a large credit into a large debt.
 */
public record Ugx(long shillings) {

    public static final Ugx ZERO = new Ugx(0);

    public static Ugx of(long shillings) {
        return new Ugx(shillings);
    }

    public Ugx plus(Ugx other) {
        return new Ugx(Math.addExact(shillings, other.shillings));
    }

    public Ugx minus(Ugx other) {
        return new Ugx(Math.subtractExact(shillings, other.shillings));
    }

    public Ugx times(long factor) {
        return new Ugx(Math.multiplyExact(shillings, factor));
    }

    public boolean isNegative() {
        return shillings < 0;
    }

    /** For display: {@code UGX 1,250,000}, {@code -UGX 5,000}. */
    public String format() {
        // Locale.US for the separator, not the machine's locale: the grouping must not change with
        // whatever region a Windows PC happens to be set to.
        String digits = String.format(Locale.US, "%,d", shillings);
        return isNegative() ? "-UGX " + digits.substring(1) : "UGX " + digits;
    }
}

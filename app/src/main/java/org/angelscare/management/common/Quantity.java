package org.angelscare.management.common;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A quantity of something (kg of beans, bags of maize, months of rent) with up to two decimal
 * places. Stored as a whole number of hundredths, never a double, so sums are exact.
 */
public record Quantity(long hundredths) {

    public static final String INVALID_MESSAGE =
            "Enter a quantity with up to 2 decimals, e.g. 12.5.";

    /** Digits (thousands may be separated by commas), then up to two decimals. */
    private static final Pattern FORMAT = Pattern.compile("(\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.(\\d{1,2}))?");

    public static Quantity ofHundredths(long hundredths) {
        return new Quantity(hundredths);
    }

    /**
     * {@code 12.5}, {@code 1,200.25}, {@code 3}. Anything else (negative, more than two decimals,
     * letters, empty) throws a {@link ValidationException} with {@link #INVALID_MESSAGE}.
     */
    public static Quantity parse(String text) {
        Matcher m = FORMAT.matcher(text == null ? "" : text.strip());
        if (!m.matches()) {
            throw new ValidationException(INVALID_MESSAGE);
        }
        String decimals = m.group(2) == null ? "" : m.group(2);
        try {
            long whole = Long.parseLong(m.group(1).replace(",", ""));
            long fraction = decimals.isEmpty() ? 0 : Long.parseLong((decimals + "0").substring(0, 2));
            return new Quantity(Math.addExact(Math.multiplyExact(whole, 100), fraction));
        } catch (NumberFormatException | ArithmeticException tooLarge) {
            throw new ValidationException(INVALID_MESSAGE);
        }
    }

    /** {@code 12.5}, {@code 1,200.25}, {@code 3}: no trailing zeros. */
    public String format() {
        String whole = String.format(Locale.US, "%,d", hundredths / 100);
        long fraction = hundredths % 100;
        if (fraction == 0) {
            return whole;
        }
        String decimals = String.format("%02d", fraction);
        return whole + "." + (decimals.endsWith("0") ? decimals.substring(0, 1) : decimals);
    }

    /** quantity × rate, rounded to the nearest shilling, halves up. */
    public Ugx times(Ugx rate) {
        long hundredthsOfShillings = Math.multiplyExact(hundredths, rate.shillings());
        return Ugx.of(Math.floorDiv(Math.addExact(hundredthsOfShillings, 50), 100));
    }
}

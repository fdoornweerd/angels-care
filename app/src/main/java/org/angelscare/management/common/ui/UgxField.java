package org.angelscare.management.common.ui;

import java.util.Locale;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.common.ValidationException;

/** Typing and showing amounts: whole shillings, with or without separators or "UGX". */
public final class UgxField {

    public static final String INVALID_MESSAGE = "Enter a whole amount in shillings, e.g. 300,000.";

    private UgxField() {
    }

    /**
     * {@code 300000}, {@code 300,000}, {@code 300 000} and {@code UGX 300,000} all give 300,000.
     * Anything else throws a {@link ValidationException} with {@link #INVALID_MESSAGE}.
     */
    public static Ugx parse(String text) {
        if (text == null) {
            throw new ValidationException(INVALID_MESSAGE);
        }
        String digits = text.strip();
        if (digits.regionMatches(true, 0, "UGX", 0, 3)) {
            digits = digits.substring(3);
        }
        // Thousands separators as people type them: commas, spaces, and the non-breaking space
        // that a copy from Excel can bring along.
        digits = digits.replaceAll("[,\\s\\u00A0]", "");
        if (digits.isEmpty() || !digits.chars().allMatch(c -> c >= '0' && c <= '9')) {
            throw new ValidationException(INVALID_MESSAGE);
        }
        try {
            return Ugx.of(Long.parseLong(digits));
        } catch (NumberFormatException tooLarge) {
            throw new ValidationException(INVALID_MESSAGE);
        }
    }

    /**
     * Like {@link #parse}, but a leading minus is allowed (a debt of {@code -5,000} is a credit).
     */
    public static Ugx parseSigned(String text) {
        String clean = text == null ? "" : text.strip();
        boolean negative = false;
        if (clean.startsWith("-")) {
            negative = true;
            clean = clean.substring(1);
        } else if (clean.regionMatches(true, 0, "UGX", 0, 3)
                && clean.substring(3).strip().startsWith("-")) {
            negative = true;
            clean = clean.substring(3).strip().substring(1);
        }
        Ugx amount = parse(clean);
        return negative ? Ugx.ZERO.minus(amount) : amount;
    }

    /** {@code 300,000}: grouped, no currency, no decimals. */
    public static String format(Ugx amount) {
        return String.format(Locale.US, "%,d", amount.shillings());
    }
}

package org.angelscare.management.common;

/** The one rule for every user-entered name: trimmed, not blank, at most {@link #MAX_LENGTH}. */
public final class Names {

    public static final int MAX_LENGTH = 100;

    private Names() {
    }

    /**
     * Returns {@code value} trimmed, or throws {@link ValidationException} naming {@code what}
     * (e.g. "Category name") if it is null, blank or too long.
     */
    public static String require(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new ValidationException(what + " can't be empty.");
        }
        String trimmed = value.strip();
        if (trimmed.length() > MAX_LENGTH) {
            throw new ValidationException(what + " can be at most " + MAX_LENGTH
                    + " characters; this one has " + trimmed.length() + ".");
        }
        return trimmed;
    }

    /** Like {@link #require} for a field that may be left empty: blank becomes null. */
    public static String optional(String value, String what) {
        return value == null || value.isBlank() ? null : require(value, what);
    }
}

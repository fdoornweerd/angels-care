package org.angelscare.management.common.ui;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import org.angelscare.management.common.ValidationException;

/** Dates on screen are dd/MM/yyyy, the way the school writes them. */
public final class DateFormats {

    /** STRICT, so 31/02/2026 is refused instead of quietly becoming 28/02/2026. */
    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    private DateFormats() {
    }

    public static String format(LocalDate date) {
        return date == null ? "" : FORMAT.format(date);
    }

    /** Parses dd/MM/yyyy strictly; anything else throws a ValidationException. */
    public static LocalDate parse(String text) {
        try {
            return LocalDate.parse(text == null ? "" : text.strip(), FORMAT);
        } catch (DateTimeParseException e) {
            throw new ValidationException("Enter a date as dd/mm/yyyy, e.g. 02/02/2026.");
        }
    }
}

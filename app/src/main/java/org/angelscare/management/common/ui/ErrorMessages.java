package org.angelscare.management.common.ui;

import org.angelscare.management.Diagnostics;
import org.angelscare.management.common.ValidationException;

/**
 * Turns an exception from a save or delete into the line shown above a form. A
 * ValidationException is the user's to fix and is shown as it is; anything else is logged and
 * reported with where to find the details.
 */
public final class ErrorMessages {

    private ErrorMessages() {
    }

    public static String forException(Exception e) {
        if (e instanceof ValidationException) {
            return e.getMessage();
        }
        Diagnostics.log("Unexpected error on a screen", e);
        return "Something went wrong. Details are in " + Diagnostics.logFilePath() + ".";
    }
}

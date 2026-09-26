package org.angelscare.management.common;

/**
 * A rule the user broke (a blank name, a duplicate, a fee charged twice). The message is written
 * for the bookkeeper and is shown unchanged, so it names the thing concerned in plain words.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}

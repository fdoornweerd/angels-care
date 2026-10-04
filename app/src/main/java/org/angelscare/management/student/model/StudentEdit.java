package org.angelscare.management.student.model;

import org.angelscare.management.calendar.model.TermRef;

/** What Edit student saves, all at once or not at all. The class is not edited here. */
public record StudentEdit(
        String firstName,
        String lastName,
        Residency residency,
        TermRef joinedTerm,
        StudentStatus status) {
}

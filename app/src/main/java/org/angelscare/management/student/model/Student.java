package org.angelscare.management.student.model;

import org.angelscare.management.calendar.model.TermRef;

/**
 * {@code admissionNo} is null when the school has not given one yet. {@code boarding} is the
 * student's current Day/Boarding, used when they are put on a further term. {@code joinedTerm} is
 * null only for a student who is on no term yet; {@code lastTerm} is set exactly when they are
 * {@link StudentStatus#LEFT}.
 */
public record Student(
        String id,
        String firstName,
        String lastName,
        String admissionNo,
        SchoolClass schoolClass,
        Residency residency,
        StudentStatus status,
        Boarding boarding,
        TermRef joinedTerm,
        TermRef lastTerm) {

    /** Derived from the class, never stored, so the two cannot disagree. */
    public Level level() {
        return schoolClass.level();
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    /**
     * Whether the student was at the school in {@code term}: joined by then and not left before
     * it. A student with no joined term yet belongs to whichever term they are first put on.
     */
    public boolean belongsTo(TermRef term) {
        if (status == StudentStatus.LEFT && lastTerm == null) {
            return false;
        }
        return (joinedTerm == null || joinedTerm.compareTo(term) <= 0)
                && (lastTerm == null || term.compareTo(lastTerm) <= 0);
    }
}

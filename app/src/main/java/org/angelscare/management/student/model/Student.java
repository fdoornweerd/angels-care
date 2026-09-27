package org.angelscare.management.student.model;

/** {@code admissionNo} is null when the school has not given one yet. */
public record Student(
        String id,
        String firstName,
        String lastName,
        String admissionNo,
        SchoolClass schoolClass,
        Residency residency,
        StudentStatus status) {

    /** Derived from the class, never stored, so the two cannot disagree. */
    public Level level() {
        return schoolClass.level();
    }

    public String fullName() {
        return firstName + " " + lastName;
    }
}

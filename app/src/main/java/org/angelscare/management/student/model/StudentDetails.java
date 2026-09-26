package org.angelscare.management.student.model;

/** What the bookkeeper enters for a student. Status is changed separately. */
public record StudentDetails(
        String firstName,
        String lastName,
        String admissionNo,
        SchoolClass schoolClass,
        Residency residency) {

    public static StudentDetails of(String firstName, String lastName, SchoolClass schoolClass,
            Residency residency) {
        return new StudentDetails(firstName, lastName, null, schoolClass, residency);
    }

    public StudentDetails withAdmissionNo(String admissionNo) {
        return new StudentDetails(firstName, lastName, admissionNo, schoolClass, residency);
    }

    public StudentDetails withClass(SchoolClass schoolClass) {
        return new StudentDetails(firstName, lastName, admissionNo, schoolClass, residency);
    }
}

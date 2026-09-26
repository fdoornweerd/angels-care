package org.angelscare.management.student.model;

/** Which students to list; a null field means "any". */
public record StudentFilter(SchoolClass schoolClass, StudentStatus status) {

    public static final StudentFilter ALL = new StudentFilter(null, null);

    public StudentFilter inClass(SchoolClass schoolClass) {
        return new StudentFilter(schoolClass, status);
    }

    public StudentFilter withStatus(StudentStatus status) {
        return new StudentFilter(schoolClass, status);
    }
}

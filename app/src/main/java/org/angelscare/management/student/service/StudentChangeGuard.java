package org.angelscare.management.student.service;

import org.angelscare.management.calendar.model.TermRange;
import org.angelscare.management.student.model.SchoolClass;
import org.angelscare.management.student.model.Student;

/**
 * Lets a higher feature veto a change to a student before it is saved: fees, for instance, reject
 * a class change that would charge a fee twice. Declared here and implemented above, so the
 * student feature does not depend on the features built on it.
 */
public interface StudentChangeGuard {

    /** Throws a ValidationException if {@code student} may not move to {@code newClass}. */
    void beforeClassChange(Student student, SchoolClass newClass);

    /**
     * Throws a ValidationException if {@code studentId} may not be in {@code groupId} for
     * {@code terms}. {@code replacedMembershipId} is the membership being edited, or null.
     */
    void beforeMembershipChange(String groupId, String studentId, TermRange terms,
            String replacedMembershipId);
}

package org.angelscare.management.income.service;

import java.util.List;
import java.util.Optional;
import org.angelscare.management.calendar.model.TermRange;
import org.angelscare.management.calendar.model.TermRef;
import org.angelscare.management.income.model.FeeAssignment;
import org.angelscare.management.income.model.FeeTarget;
import org.angelscare.management.student.model.GroupMembership;
import org.angelscare.management.student.model.Student;

/** Which terms of a fee assignment actually reach one student. Pure: no database. */
final class Coverage {

    private Coverage() {
    }

    /**
     * The terms in which {@code assignment} applies to {@code student}, given the student's
     * {@code memberships}. Empty if it never does.
     */
    static List<TermRange> of(FeeAssignment assignment, Student student,
            List<GroupMembership> memberships) {
        return switch (assignment.target()) {
            case FeeTarget.OneStudent t -> t.studentId().equals(student.id())
                    ? List.of(assignment.terms()) : List.of();
            case FeeTarget.WholeClass t -> t.schoolClass() == student.schoolClass()
                    ? List.of(assignment.terms()) : List.of();
            case FeeTarget.Group t -> memberships.stream()
                    .filter(m -> m.groupId().equals(t.groupId()) && m.studentId().equals(student.id()))
                    .map(m -> assignment.terms().intersection(m.terms()))
                    .flatMap(Optional::stream)
                    .toList();
        };
    }

    static boolean appliesIn(FeeAssignment assignment, Student student,
            List<GroupMembership> memberships, TermRef term) {
        return of(assignment, student, memberships).stream().anyMatch(r -> r.contains(term));
    }

    /** The first term in which both assignments reach the student, if there is one. */
    static Optional<TermRef> firstSharedTerm(FeeAssignment a, FeeAssignment b, Student student,
            List<GroupMembership> memberships) {
        List<TermRange> bTerms = of(b, student, memberships);
        return of(a, student, memberships).stream()
                .flatMap(ra -> bTerms.stream().map(ra::intersection).flatMap(Optional::stream))
                .map(TermRange::start)
                .min(TermRef::compareTo);
    }
}

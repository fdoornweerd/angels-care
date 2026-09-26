package org.angelscare.management.income.service;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.angelscare.management.calendar.model.TermRange;
import org.angelscare.management.calendar.model.TermRef;
import org.angelscare.management.common.ValidationException;
import org.angelscare.management.income.model.FeeAssignment;
import org.angelscare.management.income.model.FeeTarget;
import org.angelscare.management.income.model.IncomeItem;
import org.angelscare.management.income.repository.FeeAssignmentRepository;
import org.angelscare.management.income.repository.IncomeItemRepository;
import org.angelscare.management.student.model.GroupMembership;
import org.angelscare.management.student.model.SchoolClass;
import org.angelscare.management.student.model.Student;
import org.angelscare.management.student.model.StudentGroup;
import org.angelscare.management.student.repository.GroupMembershipRepository;
import org.angelscare.management.student.repository.StudentGroupRepository;
import org.angelscare.management.student.repository.StudentRepository;
import org.angelscare.management.student.service.StudentChangeGuard;
import org.springframework.stereotype.Component;

/**
 * The "no overlaps" rule: an income item may reach a student through only one fee assignment in
 * any term. Checked when an assignment is created or edited, and, through
 * {@link StudentChangeGuard}, when a student changes class or group.
 *
 * <p>Every non-deleted student counts, LEFT ones included, so a returning student never
 * brings a hidden conflict back with them.
 */
@Component
public class FeeCoverageGuard implements StudentChangeGuard {

    private final FeeAssignmentRepository assignments;
    private final StudentRepository students;
    private final GroupMembershipRepository memberships;
    private final IncomeItemRepository items;
    private final StudentGroupRepository groups;

    public FeeCoverageGuard(FeeAssignmentRepository assignments, StudentRepository students,
            GroupMembershipRepository memberships, IncomeItemRepository items,
            StudentGroupRepository groups) {
        this.assignments = assignments;
        this.students = students;
        this.memberships = memberships;
        this.items = items;
        this.groups = groups;
    }

    /**
     * Throws if {@code candidate} (new, with a null id, or an edited version of an existing one)
     * would make a student pay its item twice in some term.
     */
    public void checkAssignment(FeeAssignment candidate) {
        List<FeeAssignment> others = assignments.findByItem(candidate.incomeItemId()).stream()
                .filter(a -> !Objects.equals(a.id(), candidate.id()))
                .filter(a -> a.terms().overlaps(candidate.terms()))
                .toList();
        if (others.isEmpty()) {
            return;
        }
        for (FeeAssignment other : others) {
            if (other.target().equals(candidate.target())) {
                throw new ValidationException(itemName(candidate) + " is already charged to "
                        + describe(other.target()) + " " + other.terms().label()
                        + ". End that assignment before starting a new one.");
            }
        }
        Map<String, List<GroupMembership>> membershipsByStudent = memberships.findAll().stream()
                .collect(groupingBy(GroupMembership::studentId));
        for (Student student : students.findAll()) {
            List<GroupMembership> ms = membershipsByStudent.getOrDefault(student.id(), List.of());
            for (FeeAssignment other : others) {
                Coverage.firstSharedTerm(candidate, other, student, ms).ifPresent(term -> {
                    throw chargedTwice(student, candidate, other, term);
                });
            }
        }
    }

    @Override
    public void beforeClassChange(Student student, SchoolClass newClass) {
        checkStudent(student.withClass(newClass), memberships.findByStudent(student.id()));
    }

    @Override
    public void beforeMembershipChange(String groupId, String studentId, TermRange terms,
            String replacedMembershipId) {
        Student student = students.findById(studentId)
                .orElseThrow(() -> new ValidationException("That student no longer exists."));
        List<GroupMembership> proposed = new ArrayList<>(memberships.findByStudent(studentId).stream()
                .filter(m -> !Objects.equals(m.id(), replacedMembershipId))
                .toList());
        proposed.add(new GroupMembership(replacedMembershipId, groupId, studentId, terms));
        checkStudent(student, proposed);
    }

    /** Checks every pair of assignments of the same item against one (possibly changed) student. */
    private void checkStudent(Student student, List<GroupMembership> studentMemberships) {
        Map<String, List<FeeAssignment>> byItem = assignments.findAll().stream()
                .collect(groupingBy(FeeAssignment::incomeItemId, LinkedHashMap::new, toList()));
        for (List<FeeAssignment> sameItem : byItem.values()) {
            for (int i = 0; i < sameItem.size(); i++) {
                for (int j = i + 1; j < sameItem.size(); j++) {
                    FeeAssignment a = sameItem.get(i);
                    FeeAssignment b = sameItem.get(j);
                    Coverage.firstSharedTerm(a, b, student, studentMemberships).ifPresent(term -> {
                        throw chargedTwice(student, a, b, term);
                    });
                }
            }
        }
    }

    private ValidationException chargedTwice(Student student, FeeAssignment a, FeeAssignment b,
            TermRef term) {
        return new ValidationException(student.fullName() + " would pay " + itemName(a)
                + " twice from " + term.label() + ": through " + describe(a.target())
                + " and through " + describe(b.target()) + ".");
    }

    private String itemName(FeeAssignment assignment) {
        return items.findById(assignment.incomeItemId()).map(IncomeItem::name).orElse("this fee");
    }

    private String describe(FeeTarget target) {
        return switch (target) {
            case FeeTarget.OneStudent t -> "a fee set just for " + students.findById(t.studentId())
                    .map(Student::fullName).orElse("them");
            case FeeTarget.Group t -> "group " + groups.findById(t.groupId())
                    .map(StudentGroup::name).orElse("(deleted)");
            case FeeTarget.WholeClass t -> "class " + t.schoolClass().label();
        };
    }
}

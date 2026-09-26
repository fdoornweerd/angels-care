package org.angelscare.management.student.service;

import java.util.List;
import java.util.Objects;
import org.angelscare.management.calendar.model.Term;
import org.angelscare.management.calendar.model.TermRange;
import org.angelscare.management.calendar.model.TermRef;
import org.angelscare.management.calendar.service.CalendarService;
import org.angelscare.management.common.DeletionGuard;
import org.angelscare.management.common.Names;
import org.angelscare.management.common.ValidationException;
import org.angelscare.management.student.model.GroupMembership;
import org.angelscare.management.student.model.Student;
import org.angelscare.management.student.model.StudentGroup;
import org.angelscare.management.student.repository.GroupMembershipRepository;
import org.angelscare.management.student.repository.StudentGroupRepository;
import org.angelscare.management.student.repository.StudentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class GroupService {

    private static final int MAX_DESCRIPTION_LENGTH = 500;

    private final StudentGroupRepository groups;
    private final GroupMembershipRepository memberships;
    private final StudentRepository students;
    private final CalendarService calendar;
    private final StudentChangeGuard changeGuard;
    private final DeletionGuard deletionGuard;

    public GroupService(StudentGroupRepository groups, GroupMembershipRepository memberships,
            StudentRepository students, CalendarService calendar, StudentChangeGuard changeGuard,
            DeletionGuard deletionGuard) {
        this.groups = groups;
        this.memberships = memberships;
        this.students = students;
        this.calendar = calendar;
        this.changeGuard = changeGuard;
        this.deletionGuard = deletionGuard;
    }

    public StudentGroup create(String name, String description) {
        String cleanName = validName(name, null);
        return groups.insert(cleanName, validDescription(description));
    }

    public StudentGroup update(String groupId, String name, String description) {
        require(groupId);
        groups.update(groupId, validName(name, groupId), validDescription(description));
        return require(groupId);
    }

    /** Sorted by name. */
    @Transactional(readOnly = true)
    public List<StudentGroup> list() {
        return groups.findAll();
    }

    public void delete(String groupId) {
        StudentGroup group = require(groupId);
        deletionGuard.requireUnused("student_group", groupId, "group '" + group.name() + "'");
        groups.softDelete(groupId);
    }

    public GroupMembership addMember(String groupId, String studentId, TermRange terms) {
        require(groupId);
        students.findById(studentId)
                .orElseThrow(() -> new ValidationException("That student no longer exists."));
        if (terms == null) {
            throw new ValidationException("Choose the first term of the membership.");
        }
        Term start = calendar.requireTerm(terms.start());
        String endTermId = endTermId(terms.end());
        requireNoOverlap(groupId, studentId, terms, null);
        changeGuard.beforeMembershipChange(groupId, studentId, terms, null);
        return memberships.insert(groupId, studentId, start.id(), endTermId);
    }

    /** Sets or clears (null) the last term of a membership. */
    public GroupMembership setEnd(String membershipId, TermRef end) {
        GroupMembership membership = requireMembership(membershipId);
        TermRange terms = end == null
                ? TermRange.from(membership.terms().start())
                : TermRange.between(membership.terms().start(), end);
        String endTermId = endTermId(end);
        requireNoOverlap(membership.groupId(), membership.studentId(), terms, membershipId);
        changeGuard.beforeMembershipChange(membership.groupId(), membership.studentId(), terms,
                membershipId);
        memberships.updateEnd(membershipId, endTermId);
        return requireMembership(membershipId);
    }

    public void removeMembership(String membershipId) {
        requireMembership(membershipId);
        memberships.softDelete(membershipId);
    }

    @Transactional(readOnly = true)
    public List<GroupMembership> memberships(String groupId) {
        return memberships.findByGroup(groupId);
    }

    /** Students whose membership of the group includes {@code term}, sorted by name. */
    @Transactional(readOnly = true)
    public List<Student> membersIn(String groupId, TermRef term) {
        List<String> memberIds = memberships.findByGroup(groupId).stream()
                .filter(m -> m.terms().contains(term))
                .map(GroupMembership::studentId)
                .toList();
        return students.findAll().stream().filter(s -> memberIds.contains(s.id())).toList();
    }

    private void requireNoOverlap(String groupId, String studentId, TermRange terms,
            String replacedMembershipId) {
        memberships.findByStudent(studentId).stream()
                .filter(m -> m.groupId().equals(groupId))
                .filter(m -> !Objects.equals(m.id(), replacedMembershipId))
                .filter(m -> m.terms().overlaps(terms))
                .findFirst()
                .ifPresent(m -> {
                    throw new ValidationException("The student is already in this group "
                            + m.terms().label() + ".");
                });
    }

    private String endTermId(TermRef end) {
        return end == null ? null : calendar.requireTerm(end).id();
    }

    private StudentGroup require(String groupId) {
        return groups.findById(groupId)
                .orElseThrow(() -> new ValidationException("That group no longer exists."));
    }

    private GroupMembership requireMembership(String membershipId) {
        return memberships.findById(membershipId)
                .orElseThrow(() -> new ValidationException("That membership no longer exists."));
    }

    private String validName(String name, String groupId) {
        String clean = Names.require(name, "Group name");
        groups.nameClash(clean, groupId).ifPresent(existing -> {
            throw new ValidationException("A group named '" + existing + "' already exists.");
        });
        return clean;
    }

    private static String validDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String clean = description.strip();
        if (clean.length() > MAX_DESCRIPTION_LENGTH) {
            throw new ValidationException("A group description can be at most "
                    + MAX_DESCRIPTION_LENGTH + " characters.");
        }
        return clean;
    }
}

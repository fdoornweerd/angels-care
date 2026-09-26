package org.angelscare.management.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.angelscare.management.calendar.model.TermRange;
import org.angelscare.management.common.ValidationException;
import org.angelscare.management.student.model.GroupMembership;
import org.angelscare.management.student.model.SchoolClass;
import org.angelscare.management.student.model.Student;
import org.angelscare.management.student.model.StudentGroup;
import org.angelscare.management.support.FinanceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GroupServiceTest extends FinanceTest {

    @Test
    @DisplayName("AC-13: with no groups, the list is empty; groups are listed by name")
    void listsGroups() {
        assertThat(finance.groups.list()).isEmpty();

        finance.groups.create("Soccer", null);
        StudentGroup boarders = finance.groups.create("Boarders", "Sleep at school");

        assertThat(finance.groups.list()).extracting(StudentGroup::name)
                .containsExactly("Boarders", "Soccer");
        assertThat(boarders.description()).isEqualTo("Sleep at school");
    }

    @Test
    @DisplayName("AC-13: a group can be renamed and its description changed or cleared")
    void updatesGroup() {
        StudentGroup group = finance.groups.create("Boarders", "Sleep at school");

        StudentGroup updated = finance.groups.update(group.id(), "Full boarders", null);

        assertThat(updated.name()).isEqualTo("Full boarders");
        assertThat(updated.description()).isNull();
    }

    @Test
    @DisplayName("AC-13: a membership from T1 to T2 covers T1 and T2 but not T3")
    void membershipCoversItsTerms() {
        createYear(2026);
        StudentGroup boarders = finance.groups.create("Boarders", null);
        Student amina = createStudent("Amina", "Nakato", SchoolClass.P4);

        finance.groups.addMember(boarders.id(), amina.id(),
                TermRange.between(t(2026, 1), t(2026, 2)));

        assertThat(finance.groups.membersIn(boarders.id(), t(2026, 1))).containsExactly(amina);
        assertThat(finance.groups.membersIn(boarders.id(), t(2026, 2))).containsExactly(amina);
        assertThat(finance.groups.membersIn(boarders.id(), t(2026, 3))).isEmpty();
    }

    @Test
    @DisplayName("AC-13: an open-ended membership continues into later years")
    void openMembershipContinues() {
        createYear(2026);
        createYear(2027);
        StudentGroup boarders = finance.groups.create("Boarders", null);
        Student amina = createStudent("Amina", "Nakato", SchoolClass.P4);

        finance.groups.addMember(boarders.id(), amina.id(), TermRange.from(t(2026, 3)));

        assertThat(finance.groups.membersIn(boarders.id(), t(2026, 2))).isEmpty();
        assertThat(finance.groups.membersIn(boarders.id(), t(2027, 1))).containsExactly(amina);
    }

    @Test
    @DisplayName("AC-13: an empty group has no members in any term")
    void emptyGroup() {
        createYear(2026);
        StudentGroup boarders = finance.groups.create("Boarders", null);

        assertThat(finance.groups.membersIn(boarders.id(), t(2026, 1))).isEmpty();
        assertThat(finance.groups.memberships(boarders.id())).isEmpty();
    }

    @Test
    @DisplayName("AC-13: a second membership of the same group may not overlap the first")
    void rejectsOverlappingMembership() {
        createYear(2026);
        StudentGroup boarders = finance.groups.create("Boarders", null);
        Student amina = createStudent("Amina", "Nakato", SchoolClass.P4);
        finance.groups.addMember(boarders.id(), amina.id(),
                TermRange.between(t(2026, 1), t(2026, 2)));

        assertThatThrownBy(() -> finance.groups.addMember(boarders.id(), amina.id(),
                TermRange.from(t(2026, 2))))
                .isInstanceOf(ValidationException.class);

        GroupMembership later =
                finance.groups.addMember(boarders.id(), amina.id(), TermRange.from(t(2026, 3)));
        assertThat(later.terms()).isEqualTo(TermRange.from(t(2026, 3)));
        assertThat(finance.groups.memberships(boarders.id())).hasSize(2);
    }

    @Test
    @DisplayName("AC-13: the same student may be in two different groups at once")
    void differentGroupsMayOverlap() {
        createYear(2026);
        StudentGroup boarders = finance.groups.create("Boarders", null);
        StudentGroup soccer = finance.groups.create("Soccer", null);
        Student amina = createStudent("Amina", "Nakato", SchoolClass.P4);

        finance.groups.addMember(boarders.id(), amina.id(), TermRange.from(t(2026, 1)));
        finance.groups.addMember(soccer.id(), amina.id(), TermRange.from(t(2026, 1)));

        assertThat(finance.groups.membersIn(soccer.id(), t(2026, 1))).containsExactly(amina);
    }

    @Test
    @DisplayName("AC-13: a membership can be ended, and ending it before it starts is rejected")
    void endsMembership() {
        createYear(2026);
        StudentGroup boarders = finance.groups.create("Boarders", null);
        Student amina = createStudent("Amina", "Nakato", SchoolClass.P4);
        GroupMembership membership = finance.groups.addMember(boarders.id(), amina.id(),
                TermRange.from(t(2026, 2)));

        assertThatThrownBy(() -> finance.groups.setEnd(membership.id(), t(2026, 1)))
                .isInstanceOf(ValidationException.class);

        GroupMembership ended = finance.groups.setEnd(membership.id(), t(2026, 2));
        assertThat(ended.terms()).isEqualTo(TermRange.between(t(2026, 2), t(2026, 2)));
        assertThat(finance.groups.membersIn(boarders.id(), t(2026, 3))).isEmpty();
    }

    @Test
    @DisplayName("AC-13: extending a membership into another one of the same group is rejected")
    void rejectsExtendingIntoOverlap() {
        createYear(2026);
        StudentGroup boarders = finance.groups.create("Boarders", null);
        Student amina = createStudent("Amina", "Nakato", SchoolClass.P4);
        GroupMembership first = finance.groups.addMember(boarders.id(), amina.id(),
                TermRange.between(t(2026, 1), t(2026, 1)));
        finance.groups.addMember(boarders.id(), amina.id(), TermRange.from(t(2026, 3)));

        assertThatThrownBy(() -> finance.groups.setEnd(first.id(), null))
                .isInstanceOf(ValidationException.class);
        assertThat(finance.groups.setEnd(first.id(), t(2026, 2)).terms())
                .isEqualTo(TermRange.between(t(2026, 1), t(2026, 2)));
    }

    @Test
    @DisplayName("AC-15: a membership needs its terms to exist in a school year")
    void membershipNeedsSchoolYear() {
        StudentGroup boarders = finance.groups.create("Boarders", null);
        Student amina = createStudent("Amina", "Nakato", SchoolClass.P4);

        assertThatThrownBy(() -> finance.groups.addMember(boarders.id(), amina.id(),
                TermRange.from(t(2026, 1))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("school year");

        createYear(2026);
        assertThatThrownBy(() -> finance.groups.addMember(boarders.id(), amina.id(),
                TermRange.between(t(2026, 1), t(2027, 1))))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("2027");
    }

    @Test
    @DisplayName("AC-25: a removed membership no longer counts")
    void removedMembership() {
        createYear(2026);
        StudentGroup boarders = finance.groups.create("Boarders", null);
        Student amina = createStudent("Amina", "Nakato", SchoolClass.P4);
        GroupMembership membership =
                finance.groups.addMember(boarders.id(), amina.id(), TermRange.from(t(2026, 1)));

        finance.groups.removeMembership(membership.id());

        assertThat(finance.groups.membersIn(boarders.id(), t(2026, 1))).isEmpty();
        assertThat(finance.groups.memberships(boarders.id())).isEmpty();
        assertThat(isSoftDeleted("group_membership", membership.id())).isTrue();
        // With the old membership gone, the same terms can be booked again.
        finance.groups.addMember(boarders.id(), amina.id(), TermRange.from(t(2026, 1)));
    }
}

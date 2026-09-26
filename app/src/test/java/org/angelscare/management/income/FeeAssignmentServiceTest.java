package org.angelscare.management.income;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Duration;
import org.angelscare.management.calendar.model.TermRange;
import org.angelscare.management.calendar.model.TermRef;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.common.ValidationException;
import org.angelscare.management.income.model.ApplicableFee;
import org.angelscare.management.income.model.BillingFrequency;
import org.angelscare.management.income.model.FeeAssignment;
import org.angelscare.management.income.model.FeeTarget;
import org.angelscare.management.income.model.IncomeItem;
import org.angelscare.management.student.model.GroupMembership;
import org.angelscare.management.student.model.Residency;
import org.angelscare.management.student.model.SchoolClass;
import org.angelscare.management.student.model.Student;
import org.angelscare.management.student.model.StudentDetails;
import org.angelscare.management.student.model.StudentGroup;
import org.angelscare.management.student.model.StudentStatus;
import org.angelscare.management.support.FinanceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FeeAssignmentServiceTest extends FinanceTest {

    private static final TermRange FROM_2026_T1 = TermRange.from(TermRef.of(2026, 1));

    @Nested
    class Amounts {

        @Test
        @DisplayName("AC-14: an amount of 0 or less is rejected; 1 shilling is accepted")
        void amountMustBePositive() {
            createYear(2026);
            IncomeItem tuition = incomeItem("Student Fees", "Tuition");
            FeeTarget p7 = FeeTarget.schoolClass(SchoolClass.P7);

            assertThatThrownBy(() -> assign(tuition, p7, 0, FROM_2026_T1))
                    .isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> assign(tuition, p7, -1, FROM_2026_T1))
                    .isInstanceOf(ValidationException.class);

            assertThat(assign(tuition, p7, 1, FROM_2026_T1).amount()).isEqualTo(Ugx.of(1));
        }

        @Test
        @DisplayName("AC-14: an assignment needs a frequency, a target and an existing item")
        void requiredFields() {
            createYear(2026);
            IncomeItem tuition = incomeItem("Student Fees", "Tuition");
            FeeTarget p7 = FeeTarget.schoolClass(SchoolClass.P7);

            assertThatThrownBy(() -> finance.fees.assign(tuition.id(), p7, Ugx.of(1_000), null,
                    FROM_2026_T1)).isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> finance.fees.assign(tuition.id(), null, Ugx.of(1_000),
                    BillingFrequency.PER_TERM, FROM_2026_T1))
                    .isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> finance.fees.assign("no-such-item", p7, Ugx.of(1_000),
                    BillingFrequency.PER_TERM, FROM_2026_T1))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("AC-14: a student or group target must exist")
        void targetMustExist() {
            createYear(2026);
            IncomeItem tuition = incomeItem("Student Fees", "Tuition");

            assertThatThrownBy(() -> assign(tuition, FeeTarget.student("no-such-student"), 1_000,
                    FROM_2026_T1)).isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> assign(tuition, FeeTarget.group("no-such-group"), 1_000,
                    FROM_2026_T1)).isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("AC-15: with no school year, a fee cannot be assigned yet")
        void needsSchoolYear() {
            IncomeItem tuition = incomeItem("Student Fees", "Tuition");

            assertThatThrownBy(() -> assign(tuition, FeeTarget.schoolClass(SchoolClass.P7), 300_000,
                    FROM_2026_T1))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("school year");
            assertThat(finance.fees.listForItem(tuition.id())).isEmpty();
        }
    }

    @Nested
    class Overlaps {

        @Test
        @DisplayName("AC-16: a student already charged through their class cannot be charged directly too")
        void studentVersusClass() {
            createYear(2026);
            IncomeItem tuition = incomeItem("Student Fees", "Tuition");
            Student amina = createStudent("Amina", "Nakato", SchoolClass.P7);
            assign(tuition, FeeTarget.schoolClass(SchoolClass.P7), 300_000, FROM_2026_T1);

            assertThatThrownBy(() -> assign(tuition, FeeTarget.student(amina.id()), 150_000,
                    TermRange.from(t(2026, 2))))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Amina")
                    .hasMessageContaining("Nakato")
                    .hasMessageContaining("Tuition");
        }

        @Test
        @DisplayName("AC-16: a group containing a student already charged through their class is rejected")
        void groupVersusClass() {
            createYear(2026);
            IncomeItem tuition = incomeItem("Student Fees", "Tuition");
            Student amina = createStudent("Amina", "Nakato", SchoolClass.P7);
            StudentGroup scholars = finance.groups.create("Scholars", null);
            finance.groups.addMember(scholars.id(), amina.id(), FROM_2026_T1);
            assign(tuition, FeeTarget.schoolClass(SchoolClass.P7), 300_000, FROM_2026_T1);

            assertThatThrownBy(() -> assign(tuition, FeeTarget.group(scholars.id()), 150_000,
                    FROM_2026_T1))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Amina");
        }

        @Test
        @DisplayName("AC-16: once the class assignment has ended, a student assignment may follow it")
        void consecutiveAssignmentsAreFine() {
            createYear(2026);
            IncomeItem tuition = incomeItem("Student Fees", "Tuition");
            Student amina = createStudent("Amina", "Nakato", SchoolClass.P7);
            assign(tuition, FeeTarget.schoolClass(SchoolClass.P7), 300_000,
                    TermRange.between(t(2026, 1), t(2026, 1)));

            FeeAssignment direct = assign(tuition, FeeTarget.student(amina.id()), 150_000,
                    TermRange.from(t(2026, 2)));

            assertThat(direct.target()).isEqualTo(FeeTarget.student(amina.id()));
        }

        @Test
        @DisplayName("AC-16: the same target cannot get the same item twice for overlapping terms")
        void sameTargetTwice() {
            createYear(2026);
            IncomeItem tuition = incomeItem("Student Fees", "Tuition");
            assign(tuition, FeeTarget.schoolClass(SchoolClass.P7), 300_000,
                    TermRange.between(t(2026, 1), t(2026, 2)));

            assertThatThrownBy(() -> assign(tuition, FeeTarget.schoolClass(SchoolClass.P7), 320_000,
                    TermRange.from(t(2026, 2))))
                    .isInstanceOf(ValidationException.class);
            // A price change: the next assignment starts after the old one ends.
            assign(tuition, FeeTarget.schoolClass(SchoolClass.P7), 320_000, TermRange.from(t(2026, 3)));
            assertThat(finance.fees.listForItem(tuition.id())).hasSize(2);
        }

        @Test
        @DisplayName("AC-16: different classes may be given the same item, even with no students yet")
        void differentClasses() {
            createYear(2026);
            IncomeItem tuition = incomeItem("Student Fees", "Tuition");

            assign(tuition, FeeTarget.schoolClass(SchoolClass.P7), 300_000, FROM_2026_T1);
            assign(tuition, FeeTarget.schoolClass(SchoolClass.P6), 280_000, FROM_2026_T1);

            assertThat(finance.fees.listForItem(tuition.id())).hasSize(2);
        }

        @Test
        @DisplayName("AC-16: two groups sharing a member overlap; groups whose memberships never coincide do not")
        void groupVersusGroup() {
            createYear(2026);
            IncomeItem meals = incomeItem("Student Fees", "Meals");
            Student amina = createStudent("Amina", "Nakato", SchoolClass.P4);
            StudentGroup boarders = finance.groups.create("Boarders", null);
            StudentGroup dayScholars = finance.groups.create("Day scholars", null);
            StudentGroup soccer = finance.groups.create("Soccer", null);
            finance.groups.addMember(boarders.id(), amina.id(),
                    TermRange.between(t(2026, 1), t(2026, 1)));
            finance.groups.addMember(dayScholars.id(), amina.id(), TermRange.from(t(2026, 2)));
            finance.groups.addMember(soccer.id(), amina.id(), FROM_2026_T1);
            assign(meals, FeeTarget.group(boarders.id()), 200_000, FROM_2026_T1);

            // Amina is in Boarders only in T1 and in Day scholars only from T2.
            assign(meals, FeeTarget.group(dayScholars.id()), 100_000, FROM_2026_T1);
            // But she is in Soccer throughout.
            assertThatThrownBy(() -> assign(meals, FeeTarget.group(soccer.id()), 50_000,
                    FROM_2026_T1))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Amina");
        }

        @Test
        @DisplayName("AC-17: joining a group that would charge a fee twice is rejected and nothing is saved")
        void joiningGroupCausesOverlap() {
            createYear(2026);
            IncomeItem tuition = incomeItem("Student Fees", "Tuition");
            StudentGroup boarders = finance.groups.create("Boarders", null);
            assign(tuition, FeeTarget.group(boarders.id()), 400_000, FROM_2026_T1);
            assign(tuition, FeeTarget.schoolClass(SchoolClass.P5), 250_000, FROM_2026_T1);
            Student amina = createStudent("Amina", "Nakato", SchoolClass.P5);

            assertThatThrownBy(() -> finance.groups.addMember(boarders.id(), amina.id(),
                    FROM_2026_T1))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Tuition");
            assertThat(finance.groups.memberships(boarders.id())).isEmpty();
        }

        @Test
        @DisplayName("AC-17: moving a group member into a class that would charge a fee twice is rejected")
        void classChangeCausesOverlap() {
            createYear(2026);
            IncomeItem tuition = incomeItem("Student Fees", "Tuition");
            StudentGroup boarders = finance.groups.create("Boarders", null);
            Student amina = createStudent("Amina", "Nakato", SchoolClass.P4);
            finance.groups.addMember(boarders.id(), amina.id(), FROM_2026_T1);
            assign(tuition, FeeTarget.group(boarders.id()), 400_000, FROM_2026_T1);
            assign(tuition, FeeTarget.schoolClass(SchoolClass.P5), 250_000, FROM_2026_T1);

            assertThatThrownBy(() -> finance.students.update(amina.id(),
                    StudentDetails.of("Amina", "Nakato", SchoolClass.P5, Residency.NATIONAL)))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Tuition");
            assertThat(finance.students.find(amina.id()).orElseThrow().schoolClass())
                    .isEqualTo(SchoolClass.P4);
        }

        @Test
        @DisplayName("AC-17: joining a group is fine when its fee ended before the membership starts")
        void membershipAfterGroupFeeEnded() {
            createYear(2026);
            IncomeItem tuition = incomeItem("Student Fees", "Tuition");
            StudentGroup boarders = finance.groups.create("Boarders", null);
            assign(tuition, FeeTarget.group(boarders.id()), 400_000,
                    TermRange.between(t(2026, 1), t(2026, 1)));
            assign(tuition, FeeTarget.schoolClass(SchoolClass.P5), 250_000, FROM_2026_T1);
            Student amina = createStudent("Amina", "Nakato", SchoolClass.P5);

            GroupMembership membership = finance.groups.addMember(boarders.id(), amina.id(),
                    TermRange.from(t(2026, 2)));

            assertThat(membership.studentId()).isEqualTo(amina.id());
        }

        @Test
        @DisplayName("AC-17: extending a membership into a term where the fee would be charged twice is rejected")
        void extendingMembershipCausesOverlap() {
            createYear(2026);
            IncomeItem tuition = incomeItem("Student Fees", "Tuition");
            StudentGroup boarders = finance.groups.create("Boarders", null);
            Student amina = createStudent("Amina", "Nakato", SchoolClass.P5);
            GroupMembership membership = finance.groups.addMember(boarders.id(), amina.id(),
                    TermRange.between(t(2026, 1), t(2026, 1)));
            assign(tuition, FeeTarget.group(boarders.id()), 400_000, FROM_2026_T1);
            assign(tuition, FeeTarget.schoolClass(SchoolClass.P5), 250_000,
                    TermRange.from(t(2026, 2)));

            assertThatThrownBy(() -> finance.groups.setEnd(membership.id(), t(2026, 2)))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("AC-18: different items may reach the same student by different routes")
        void overlapIsPerItem() {
            createYear(2026);
            IncomeItem tuition = incomeItem("Student Fees", "Tuition");
            IncomeItem boarding = incomeItem("Student Fees", "Boarding");
            Student amina = createStudent("Amina", "Nakato", SchoolClass.P7);

            assign(tuition, FeeTarget.schoolClass(SchoolClass.P7), 300_000, FROM_2026_T1);
            assign(boarding, FeeTarget.student(amina.id()), 250_000, FROM_2026_T1);

            assertThat(finance.fees.feesFor(amina.id(), t(2026, 1))).hasSize(2);
        }

        @Test
        @DisplayName("AC-16: LEFT students still count in the overlap check")
        void leftStudentsCount() {
            createYear(2026);
            IncomeItem tuition = incomeItem("Student Fees", "Tuition");
            Student amina = createStudent("Amina", "Nakato", SchoolClass.P7);
            finance.students.setStatus(amina.id(), StudentStatus.LEFT);
            assign(tuition, FeeTarget.student(amina.id()), 150_000, FROM_2026_T1);

            assertThatThrownBy(() -> assign(tuition, FeeTarget.schoolClass(SchoolClass.P7), 300_000,
                    FROM_2026_T1))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Amina");
        }

        @Test
        @DisplayName("AC-25: deleted assignments and memberships are ignored by the overlap check")
        void deletedRowsDoNotOverlap() {
            createYear(2026);
            IncomeItem tuition = incomeItem("Student Fees", "Tuition");
            Student amina = createStudent("Amina", "Nakato", SchoolClass.P7);
            StudentGroup scholars = finance.groups.create("Scholars", null);
            GroupMembership membership =
                    finance.groups.addMember(scholars.id(), amina.id(), FROM_2026_T1);
            FeeAssignment classFee =
                    assign(tuition, FeeTarget.schoolClass(SchoolClass.P7), 300_000, FROM_2026_T1);

            // Would overlap with the class fee, were it not deleted.
            finance.fees.delete(classFee.id());
            assign(tuition, FeeTarget.student(amina.id()), 150_000, FROM_2026_T1);

            // Would overlap with Amina's own fee, were her membership not removed.
            finance.groups.removeMembership(membership.id());
            FeeAssignment groupFee =
                    assign(tuition, FeeTarget.group(scholars.id()), 100_000, FROM_2026_T1);

            assertThat(groupFee.target()).isEqualTo(FeeTarget.group(scholars.id()));
        }
    }

    @Nested
    class FeesForStudent {

        @Test
        @DisplayName("AC-19: fees for a student list each applicable fee with its term total and route")
        void listsApplicableFees() {
            createYear(2026);
            IncomeItem tuition = incomeItem("Student Fees", "Tuition");
            IncomeItem boarding = incomeItem("Student Fees", "Boarding");
            Student amina = createStudent("Amina", "Nakato", SchoolClass.P7);
            StudentGroup boarders = finance.groups.create("Boarders", null);
            finance.groups.addMember(boarders.id(), amina.id(), FROM_2026_T1);
            finance.fees.assign(tuition.id(), FeeTarget.schoolClass(SchoolClass.P7),
                    Ugx.of(100_000), BillingFrequency.PER_MONTH, FROM_2026_T1);
            finance.fees.assign(boarding.id(), FeeTarget.group(boarders.id()),
                    Ugx.of(250_000), BillingFrequency.PER_TERM, FROM_2026_T1);

            assertThat(finance.fees.feesFor(amina.id(), t(2026, 1)))
                    .extracting(fee -> fee.item().name(), fee -> fee.category().name(),
                            ApplicableFee::amount, ApplicableFee::frequency,
                            ApplicableFee::termTotal, ApplicableFee::via, ApplicableFee::viaName)
                    .containsExactlyInAnyOrder(
                            tuple("Tuition", "Student Fees", Ugx.of(100_000),
                                    BillingFrequency.PER_MONTH, Ugx.of(300_000),
                                    FeeTarget.schoolClass(SchoolClass.P7), "P7"),
                            tuple("Boarding", "Student Fees", Ugx.of(250_000),
                                    BillingFrequency.PER_TERM, Ugx.of(250_000),
                                    FeeTarget.group(boarders.id()), "Boarders"));
        }

        @Test
        @DisplayName("AC-19: a student-level fee is listed with the student as its route")
        void studentRoute() {
            createYear(2026);
            IncomeItem medical = incomeItem("Student Fees", "Medical");
            Student amina = createStudent("Amina", "Nakato", SchoolClass.P7);
            assign(medical, FeeTarget.student(amina.id()), 20_000, FROM_2026_T1);

            assertThat(finance.fees.feesFor(amina.id(), t(2026, 1)))
                    .singleElement()
                    .satisfies(fee -> assertThat(fee.via()).isEqualTo(FeeTarget.student(amina.id())));
        }

        @Test
        @DisplayName("AC-19: only assignments whose terms include the asked term are listed")
        void onlyTermsInRange() {
            createYear(2026);
            IncomeItem tuition = incomeItem("Student Fees", "Tuition");
            Student amina = createStudent("Amina", "Nakato", SchoolClass.P7);
            assign(tuition, FeeTarget.schoolClass(SchoolClass.P7), 300_000,
                    TermRange.between(t(2026, 2), t(2026, 2)));

            assertThat(finance.fees.feesFor(amina.id(), t(2026, 1))).isEmpty();
            assertThat(finance.fees.feesFor(amina.id(), t(2026, 2))).hasSize(1);
            assertThat(finance.fees.feesFor(amina.id(), t(2026, 3))).isEmpty();
        }

        @Test
        @DisplayName("AC-20: a student with no applicable fees gets an empty list")
        void noFees() {
            createYear(2026);
            Student amina = createStudent("Amina", "Nakato", SchoolClass.P7);
            assign(incomeItem("Student Fees", "Tuition"), FeeTarget.schoolClass(SchoolClass.P6),
                    280_000, FROM_2026_T1);

            assertThat(finance.fees.feesFor(amina.id(), t(2026, 1))).isEmpty();
        }

        @Test
        @DisplayName("AC-20: a LEFT student has no fees, whatever is assigned")
        void leftStudentHasNoFees() {
            createYear(2026);
            IncomeItem tuition = incomeItem("Student Fees", "Tuition");
            IncomeItem medical = incomeItem("Student Fees", "Medical");
            Student amina = createStudent("Amina", "Nakato", SchoolClass.P7);
            assign(tuition, FeeTarget.schoolClass(SchoolClass.P7), 300_000, FROM_2026_T1);
            assign(medical, FeeTarget.student(amina.id()), 20_000, FROM_2026_T1);

            finance.students.setStatus(amina.id(), StudentStatus.LEFT);

            assertThat(finance.fees.feesFor(amina.id(), t(2026, 1))).isEmpty();
        }

        @Test
        @DisplayName("AC-21: an edited amount is returned, used for fees, and moves updated_at")
        void editsAmount() {
            createYear(2026);
            Student amina = createStudent("Amina", "Nakato", SchoolClass.P7);
            FeeAssignment fee = assign(incomeItem("Student Fees", "Tuition"),
                    FeeTarget.schoolClass(SchoolClass.P7), 300_000, FROM_2026_T1);
            clock.advance(Duration.ofMinutes(5));

            FeeAssignment edited = finance.fees.update(fee.id(), Ugx.of(320_000),
                    BillingFrequency.PER_MONTH, null);

            assertThat(edited.amount()).isEqualTo(Ugx.of(320_000));
            assertThat(edited.frequency()).isEqualTo(BillingFrequency.PER_MONTH);
            assertThat(edited.terms()).isEqualTo(FROM_2026_T1);
            assertThat(finance.fees.feesFor(amina.id(), t(2026, 1)))
                    .extracting(ApplicableFee::termTotal).containsExactly(Ugx.of(960_000));
            assertThat(timestampOf("fee_assignment", "updated_at", fee.id()))
                    .isEqualTo(clock.instant());
        }

        @Test
        @DisplayName("AC-21: an edit is held to the same rules as a new assignment")
        void editIsValidated() {
            createYear(2026);
            FeeAssignment fee = assign(incomeItem("Student Fees", "Tuition"),
                    FeeTarget.schoolClass(SchoolClass.P7), 300_000, TermRange.from(t(2026, 2)));

            assertThatThrownBy(() -> finance.fees.update(fee.id(), Ugx.ZERO,
                    BillingFrequency.PER_TERM, null)).isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> finance.fees.update(fee.id(), Ugx.of(1),
                    BillingFrequency.PER_TERM, t(2026, 1))).isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("AC-21: extending an assignment's end into an overlap is rejected")
        void extendingIntoOverlap() {
            createYear(2026);
            IncomeItem tuition = incomeItem("Student Fees", "Tuition");
            Student amina = createStudent("Amina", "Nakato", SchoolClass.P7);
            FeeAssignment classFee = assign(tuition, FeeTarget.schoolClass(SchoolClass.P7), 300_000,
                    TermRange.between(t(2026, 1), t(2026, 1)));
            assign(tuition, FeeTarget.student(amina.id()), 150_000, TermRange.from(t(2026, 2)));

            assertThatThrownBy(() -> finance.fees.update(classFee.id(), Ugx.of(300_000),
                    BillingFrequency.PER_TERM, t(2026, 2)))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("Amina");
            assertThatThrownBy(() -> finance.fees.update(classFee.id(), Ugx.of(300_000),
                    BillingFrequency.PER_TERM, null))
                    .isInstanceOf(ValidationException.class);
            // Unchanged after the rejected edits.
            assertThat(finance.fees.feesFor(amina.id(), t(2026, 1)))
                    .extracting(ApplicableFee::amount).containsExactly(Ugx.of(300_000));
        }

        @Test
        @DisplayName("AC-25: a deleted assignment no longer applies")
        void deletedAssignment() {
            createYear(2026);
            Student amina = createStudent("Amina", "Nakato", SchoolClass.P7);
            IncomeItem tuition = incomeItem("Student Fees", "Tuition");
            FeeAssignment fee = assign(tuition, FeeTarget.schoolClass(SchoolClass.P7), 300_000,
                    FROM_2026_T1);

            finance.fees.delete(fee.id());

            assertThat(finance.fees.feesFor(amina.id(), t(2026, 1))).isEmpty();
            assertThat(finance.fees.listForItem(tuition.id())).isEmpty();
            assertThat(isSoftDeleted("fee_assignment", fee.id())).isTrue();
        }
    }
}

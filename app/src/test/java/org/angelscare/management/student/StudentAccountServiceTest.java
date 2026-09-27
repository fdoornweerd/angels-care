package org.angelscare.management.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import org.angelscare.management.common.MonthlyAmounts;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.common.ValidationException;
import org.angelscare.management.student.model.ClassFee;
import org.angelscare.management.student.model.Residency;
import org.angelscare.management.student.model.SchoolClass;
import org.angelscare.management.student.model.Student;
import org.angelscare.management.student.model.StudentDetails;
import org.angelscare.management.student.model.StudentStatus;
import org.angelscare.management.student.model.StudentTermLine;
import org.angelscare.management.support.FinanceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Spec 002, page 5: the term register, class fees, debt, payments. */
class StudentAccountServiceTest extends FinanceTest {

    @BeforeEach
    void schoolYears() {
        createYear(2026);
        createYear(2027);
    }

    private StudentTermLine line(Student student, int year, int term) {
        return finance.studentAccounts.line(student.id(), t(year, term));
    }

    private void p7Fees() {
        finance.studentAccounts.setClassFee(SchoolClass.P7, t(2026, 1), Ugx.of(300_000),
                Ugx.of(10_000));
    }

    @Nested
    class Register {

        @Test
        @DisplayName("AC-16: opening a term adds the Active students under their classes, once")
        void addsActiveStudents() {
            Student zed = createStudent("Zed", "Achan", SchoolClass.P7);
            Student amina = createStudent("Amina", "Nakato", SchoolClass.P7);
            Student ruth = createStudent("Ruth", "Akello", SchoolClass.TOP);
            Student left = createStudent("Peter", "Opio", SchoolClass.P5);
            finance.students.setStatus(left.id(), StudentStatus.LEFT);

            finance.studentAccounts.openTerm(t(2026, 1));
            finance.studentAccounts.openTerm(t(2026, 1));

            assertThat(finance.studentAccounts.lines(t(2026, 1), false))
                    .extracting(StudentTermLine::name, StudentTermLine::schoolClass)
                    .containsExactly(
                            tuple("Ruth Akello", SchoolClass.TOP),
                            tuple("Zed Achan", SchoolClass.P7),
                            tuple("Amina Nakato", SchoolClass.P7));
            assertThat(liveRows("student_term")).isEqualTo(3);
            assertThat(finance.studentAccounts.lines(t(2026, 1), true)).hasSize(3);
            assertThat(amina.id()).isNotEqualTo(zed.id());
        }

        @Test
        @DisplayName("AC-16: a student who leaves stays on the term; the Left switch shows them")
        void leftStudentsOnlyWhenAsked() {
            Student amina = createStudent("Amina", "Nakato", SchoolClass.P7);
            finance.studentAccounts.openTerm(t(2026, 1));

            finance.students.setStatus(amina.id(), StudentStatus.LEFT);

            assertThat(finance.studentAccounts.lines(t(2026, 1), false)).isEmpty();
            assertThat(finance.studentAccounts.lines(t(2026, 1), true))
                    .extracting(StudentTermLine::status).containsExactly(StudentStatus.LEFT);
            finance.studentAccounts.openTerm(t(2026, 2));
            assertThat(finance.studentAccounts.lines(t(2026, 2), true)).isEmpty();
        }

        @Test
        @DisplayName("AC-16: a term with no students is an empty register")
        void empty() {
            finance.studentAccounts.openTerm(t(2026, 1));

            assertThat(finance.studentAccounts.lines(t(2026, 1), true)).isEmpty();
            assertThat(finance.studentAccounts.totals(t(2026, 1)).expected()).isEqualTo(Ugx.ZERO);
        }

        @Test
        @DisplayName("AC-19: a student added on the page exists and is on the term once")
        void addStudent() {
            finance.studentAccounts.setClassFee(SchoolClass.P3, t(2026, 1), Ugx.of(200_000),
                    Ugx.of(5_000));

            Student brian = finance.studentAccounts.addStudent(t(2026, 1), "Brian", "Okello",
                    SchoolClass.P3, Residency.NATIONAL);
            finance.studentAccounts.openTerm(t(2026, 1));

            assertThat(finance.students.find(brian.id())).hasValueSatisfying(s -> {
                assertThat(s.status()).isEqualTo(StudentStatus.ACTIVE);
                assertThat(s.schoolClass()).isEqualTo(SchoolClass.P3);
                assertThat(s.residency()).isEqualTo(Residency.NATIONAL);
            });
            assertThat(finance.studentAccounts.lines(t(2026, 1), false)).singleElement()
                    .satisfies(line -> {
                        assertThat(line.amount()).isEqualTo(Ugx.of(200_000));
                        assertThat(line.ream()).isEqualTo(Ugx.of(5_000));
                        assertThat(line.amountOverridden()).isFalse();
                    });
            assertThatThrownBy(() -> finance.studentAccounts.addStudent(t(2026, 1), " ", "Okello",
                    SchoolClass.P3, Residency.NATIONAL)).isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("AC-20: a student who moves class stays under the old class in the old term")
        void classIsRecordedPerTerm() {
            Student amina = createStudent("Amina", "Nakato", SchoolClass.P3);
            finance.studentAccounts.openTerm(t(2026, 1));

            finance.students.update(amina.id(),
                    StudentDetails.of("Amina", "Nakato", SchoolClass.P4, Residency.NATIONAL));
            finance.studentAccounts.openTerm(t(2026, 2));

            assertThat(line(amina, 2026, 1).schoolClass()).isEqualTo(SchoolClass.P3);
            assertThat(line(amina, 2026, 2).schoolClass()).isEqualTo(SchoolClass.P4);
        }

        @Test
        @DisplayName("AC-22: a student with payments can't be removed from the term; without, they can")
        void removeFromTerm() {
            Student amina = createStudent("Amina", "Nakato", SchoolClass.P7);
            finance.studentAccounts.openTerm(t(2026, 1));
            finance.studentAccounts.setPayment(amina.id(), t(2026, 1), 2, Ugx.of(1_000));

            assertThatThrownBy(() -> finance.studentAccounts.removeFromTerm(amina.id(), t(2026, 1)))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("payments");

            finance.studentAccounts.setPayment(amina.id(), t(2026, 1), 2, null);
            finance.studentAccounts.removeFromTerm(amina.id(), t(2026, 1));

            assertThat(finance.studentAccounts.lines(t(2026, 1), true)).isEmpty();
            // Removed on purpose: opening the term again doesn't put them back.
            finance.studentAccounts.openTerm(t(2026, 1));
            assertThat(finance.studentAccounts.lines(t(2026, 1), true)).isEmpty();
        }
    }

    @Nested
    class ClassFees {

        @Test
        @DisplayName("AC-17: a class fee carries forward into later terms and years until changed")
        void carriesForward() {
            p7Fees();

            ClassFee p7 = new ClassFee(SchoolClass.P7, Ugx.of(300_000), Ugx.of(10_000));
            assertThat(finance.studentAccounts.classFee(SchoolClass.P7, t(2026, 1))).isEqualTo(p7);
            assertThat(finance.studentAccounts.classFee(SchoolClass.P7, t(2026, 2))).isEqualTo(p7);
            assertThat(finance.studentAccounts.classFee(SchoolClass.P7, t(2027, 1))).isEqualTo(p7);

            finance.studentAccounts.setClassFee(SchoolClass.P7, t(2026, 3), Ugx.of(320_000),
                    Ugx.of(10_000));

            assertThat(finance.studentAccounts.classFee(SchoolClass.P7, t(2026, 2)).amount())
                    .isEqualTo(Ugx.of(300_000));
            assertThat(finance.studentAccounts.classFee(SchoolClass.P7, t(2026, 3)).amount())
                    .isEqualTo(Ugx.of(320_000));
            assertThat(finance.studentAccounts.classFee(SchoolClass.P7, t(2027, 1)).amount())
                    .isEqualTo(Ugx.of(320_000));
        }

        @Test
        @DisplayName("AC-17: a class with no fee ever set gets 0; negative fees are refused")
        void noFee() {
            assertThat(finance.studentAccounts.classFee(SchoolClass.P1, t(2026, 2)))
                    .isEqualTo(new ClassFee(SchoolClass.P1, Ugx.ZERO, Ugx.ZERO));
            assertThatThrownBy(() -> finance.studentAccounts.setClassFee(SchoolClass.P1, t(2026, 1),
                    Ugx.of(-1), Ugx.ZERO)).isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("AC-17: students' amounts follow the class fee of their term")
        void studentsFollowClassFee() {
            Student amina = createStudent("Amina", "Nakato", SchoolClass.P7);
            p7Fees();
            finance.studentAccounts.openTerm(t(2026, 1));
            finance.studentAccounts.openTerm(t(2026, 3));

            finance.studentAccounts.setClassFee(SchoolClass.P7, t(2026, 3), Ugx.of(320_000),
                    Ugx.of(12_000));

            assertThat(line(amina, 2026, 1).amount()).isEqualTo(Ugx.of(300_000));
            assertThat(line(amina, 2026, 3).amount()).isEqualTo(Ugx.of(320_000));
            assertThat(line(amina, 2026, 3).ream()).isEqualTo(Ugx.of(12_000));
        }
    }

    @Nested
    class Balances {

        @Test
        @DisplayName("AC-18: Total = Amount + Debt + Ream; Balance = Total − payments; debt carries")
        void totalsAndCarriedDebt() {
            Student amina = createStudent("Amina", "Nakato", SchoolClass.P7);
            p7Fees();
            finance.studentAccounts.openTerm(t(2026, 1));
            finance.studentAccounts.setPayment(amina.id(), t(2026, 1), 1, Ugx.of(100_000));
            finance.studentAccounts.setPayment(amina.id(), t(2026, 1), 2, Ugx.of(50_000));

            StudentTermLine term1 = line(amina, 2026, 1);
            assertThat(term1.debt()).isEqualTo(Ugx.ZERO);
            assertThat(term1.total()).isEqualTo(Ugx.of(310_000));
            assertThat(term1.payments()).isEqualTo(new MonthlyAmounts(Ugx.of(100_000),
                    Ugx.of(50_000), Ugx.ZERO));
            assertThat(term1.balance()).isEqualTo(Ugx.of(160_000));

            finance.studentAccounts.openTerm(t(2026, 2));
            StudentTermLine term2 = line(amina, 2026, 2);
            assertThat(term2.debt()).isEqualTo(Ugx.of(160_000));
            assertThat(term2.debtOverridden()).isFalse();
            assertThat(term2.total()).isEqualTo(Ugx.of(470_000));
        }

        @Test
        @DisplayName("AC-18: a typed debt overrides the carried one; clearing it goes back")
        void debtOverride() {
            Student amina = createStudent("Amina", "Nakato", SchoolClass.P7);
            p7Fees();
            finance.studentAccounts.openTerm(t(2026, 1));
            finance.studentAccounts.openTerm(t(2026, 2));

            assertThat(finance.studentAccounts.setDebt(amina.id(), t(2026, 2), Ugx.of(100_000))
                    .total()).isEqualTo(Ugx.of(410_000));
            assertThat(line(amina, 2026, 2).debtOverridden()).isTrue();

            finance.studentAccounts.setDebt(amina.id(), t(2026, 2), null);
            assertThat(line(amina, 2026, 2).debt()).isEqualTo(Ugx.of(310_000));
        }

        @Test
        @DisplayName("AC-18: an overpayment carries as a negative debt (a credit)")
        void creditCarries() {
            Student amina = createStudent("Amina", "Nakato", SchoolClass.P7);
            p7Fees();
            finance.studentAccounts.openTerm(t(2026, 1));
            finance.studentAccounts.setPayment(amina.id(), t(2026, 1), 3, Ugx.of(340_000));
            finance.studentAccounts.openTerm(t(2026, 2));

            assertThat(line(amina, 2026, 1).balance()).isEqualTo(Ugx.of(-30_000));
            assertThat(line(amina, 2026, 2).debt()).isEqualTo(Ugx.of(-30_000));
            assertThat(line(amina, 2026, 2).total()).isEqualTo(Ugx.of(280_000));
            assertThat(finance.studentAccounts.setDebt(amina.id(), t(2026, 2), Ugx.of(-5_000))
                    .debt()).isEqualTo(Ugx.of(-5_000));
        }

        @Test
        @DisplayName("AC-18: a student's own amount and ream; clearing the amount returns the class fee")
        void amountAndReamOverrides() {
            Student amina = createStudent("Amina", "Nakato", SchoolClass.P7);
            p7Fees();
            finance.studentAccounts.openTerm(t(2026, 1));
            finance.studentAccounts.openTerm(t(2026, 2));

            StudentTermLine discounted =
                    finance.studentAccounts.setAmount(amina.id(), t(2026, 1), Ugx.of(250_000));
            assertThat(discounted.amountOverridden()).isTrue();
            assertThat(discounted.total()).isEqualTo(Ugx.of(260_000));
            // Term 2's carried debt follows the change.
            assertThat(line(amina, 2026, 2).debt()).isEqualTo(Ugx.of(260_000));

            finance.studentAccounts.setReam(amina.id(), t(2026, 1), Ugx.ZERO);
            assertThat(line(amina, 2026, 1).ream()).isEqualTo(Ugx.ZERO);
            assertThat(line(amina, 2026, 1).reamOverridden()).isTrue();

            finance.studentAccounts.setAmount(amina.id(), t(2026, 1), null);
            assertThat(line(amina, 2026, 1).amount()).isEqualTo(Ugx.of(300_000));
            assertThat(line(amina, 2026, 1).amountOverridden()).isFalse();
        }

        @Test
        @DisplayName("AC-18: a student new in a later term starts with no debt")
        void newStudentHasNoDebt() {
            p7Fees();
            finance.studentAccounts.openTerm(t(2026, 1));
            Student late = createStudent("Isaac", "Wasswa", SchoolClass.P7);

            finance.studentAccounts.openTerm(t(2026, 2));

            assertThat(line(late, 2026, 2).debt()).isEqualTo(Ugx.ZERO);
        }

        @Test
        @DisplayName("AC-15/AC-18: payments are whole and not negative; months 1-3; remarks up to 500")
        void validation() {
            Student amina = createStudent("Amina", "Nakato", SchoolClass.P7);
            finance.studentAccounts.openTerm(t(2026, 1));

            assertThatThrownBy(() -> finance.studentAccounts.setPayment(amina.id(), t(2026, 1), 1,
                    Ugx.of(-1))).isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> finance.studentAccounts.setPayment(amina.id(), t(2026, 1), 4,
                    Ugx.of(1))).isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> finance.studentAccounts.setAmount(amina.id(), t(2026, 1),
                    Ugx.of(-1))).isInstanceOf(ValidationException.class);
            assertThatThrownBy(() -> finance.studentAccounts.setRemarks(amina.id(), t(2026, 1),
                    "x".repeat(501))).isInstanceOf(ValidationException.class);
            assertThat(finance.studentAccounts.setRemarks(amina.id(), t(2026, 1), "  Pays late  ")
                    .remarks()).isEqualTo("Pays late");
            assertThat(finance.studentAccounts.setRemarks(amina.id(), t(2026, 1), " ").remarks())
                    .isNull();
            assertThatThrownBy(() -> finance.studentAccounts.setPayment(amina.id(), t(2026, 2), 1,
                    Ugx.of(1))).isInstanceOf(ValidationException.class)
                    .hasMessageContaining("not on");
        }
    }
}

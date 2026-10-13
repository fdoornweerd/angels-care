package org.angelscare.management.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;
import java.util.List;
import org.angelscare.management.accounts.model.SummaryRow;
import org.angelscare.management.calendar.model.TermRef;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.common.ValidationException;
import org.angelscare.management.student.model.Boarding;
import org.angelscare.management.student.model.ClassFee;
import org.angelscare.management.student.model.Residency;
import org.angelscare.management.student.model.SchoolClass;
import org.angelscare.management.student.model.Student;
import org.angelscare.management.student.model.StudentEdit;
import org.angelscare.management.student.model.StudentStatus;
import org.angelscare.management.student.model.StudentTermLine;
import org.angelscare.management.student.model.StudentsTotals;
import org.angelscare.management.student.service.StudentAccountService;
import org.angelscare.management.student.ui.StudentsViewModel;
import org.angelscare.management.support.FinanceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Spec 004: each student's Amount is typed in; class headings keep only Ream. */
class StudentAmountsTest extends FinanceTest {

    static final String AMOUNT_REQUIRED = "Type the amount the student pays (0 for none).";

    @BeforeEach
    void schoolYears() {
        createYear(2026);
        createYear(2027);
    }

    private StudentTermLine line(Student student, int year, int term) {
        return finance.studentAccounts.line(student.id(), t(year, term));
    }

    private Student stored(Student student) {
        return finance.students.find(student.id()).orElseThrow();
    }

    /** "Add student" in P7 on {@code year} Term {@code term}, Day, with {@code amount}. */
    private Student add(int year, int term, String firstName, String lastName, long amount) {
        return finance.studentAccounts.addStudent(t(year, term), firstName, lastName,
                SchoolClass.P7, Residency.NATIONAL, Boarding.DAY, Ugx.of(amount));
    }

    private Student edit(Student student, int year, int term, TermRef joined,
            StudentStatus status) {
        Student current = stored(student);
        return finance.studentAccounts.editStudent(student.id(), t(year, term), new StudentEdit(
                current.firstName(), current.lastName(), current.residency(), joined, status));
    }

    /** The amount stored on the student's line, as opposed to the one shown. */
    private Long storedAmount(Student student, int year, int term) {
        return jdbc.queryForObject("SELECT s.amount FROM student_term s"
                + " JOIN term t ON t.id = s.term_id JOIN school_year y ON y.id = t.school_year_id"
                + " WHERE s.student_id = ? AND y.year = ? AND t.number = ?"
                + " AND s.deleted_at IS NULL", Long.class, student.id(), year, term);
    }

    @Nested
    class ClassHeadings {

        @Test
        @DisplayName("AC-2: a class Ream set in Term 1 carries forward; changed in Term 3, from Term 3 on")
        void reamCarriesForward() {
            finance.studentAccounts.setClassReam(SchoolClass.P7, t(2026, 1), Ugx.of(10_000));

            assertThat(finance.studentAccounts.classFee(SchoolClass.P7, t(2026, 1)).ream())
                    .isEqualTo(Ugx.of(10_000));
            assertThat(finance.studentAccounts.classFee(SchoolClass.P7, t(2026, 2)).ream())
                    .isEqualTo(Ugx.of(10_000));
            assertThat(finance.studentAccounts.classFee(SchoolClass.P7, t(2027, 1)).ream())
                    .isEqualTo(Ugx.of(10_000));

            finance.studentAccounts.setClassReam(SchoolClass.P7, t(2026, 3), Ugx.of(12_000));

            assertThat(finance.studentAccounts.classFee(SchoolClass.P7, t(2026, 2)).ream())
                    .isEqualTo(Ugx.of(10_000));
            assertThat(finance.studentAccounts.classFee(SchoolClass.P7, t(2026, 3)).ream())
                    .isEqualTo(Ugx.of(12_000));
            assertThat(finance.studentAccounts.classFee(SchoolClass.P7, t(2027, 1)).ream())
                    .isEqualTo(Ugx.of(12_000));
            assertThatThrownBy(() -> finance.studentAccounts.setClassReam(SchoolClass.P7,
                    t(2026, 1), Ugx.of(-1))).isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("AC-2: a class whose Ream was never set gives 5,000")
        void unsetReamIs5000() {
            Student amina = finance.studentAccounts.addStudent(t(2026, 1), "Amina", "Nakato",
                    SchoolClass.P1, Residency.NATIONAL, Boarding.DAY, Ugx.of(250_000));

            assertThat(finance.studentAccounts.classFee(SchoolClass.P1, t(2026, 1)).ream())
                    .isEqualTo(Ugx.of(5_000));
            assertThat(line(amina, 2026, 1).ream()).isEqualTo(Ugx.of(5_000));
            assertThat(line(amina, 2026, 1).total()).isEqualTo(Ugx.of(255_000));
        }

        @Test
        @DisplayName("AC-3: class fees hold only the Ream; there is no Day or Boarding fee to set")
        void noDayOrBoardingFees() {
            assertThat(Arrays.stream(ClassFee.class.getRecordComponents())
                    .map(c -> c.getName()))
                    .containsExactly("schoolClass", "ream");
            assertThat(Arrays.stream(StudentAccountService.class.getMethods())
                    .map(m -> m.getName()))
                    .doesNotContain("setClassFee");
            assertThat(Arrays.stream(StudentsViewModel.class.getMethods())
                    .map(m -> m.getName()))
                    .doesNotContain("editClassFee", "editClassBoardingFee");
        }
    }

    @Nested
    class Amount {

        @Test
        @DisplayName("AC-4: Add student puts them on the term with the amount typed, Day unless Boarding")
        void addWithAmount() {
            Student brian = finance.studentAccounts.addStudent(t(2026, 1), "Brian", "Okello",
                    SchoolClass.P3, Residency.NATIONAL, Boarding.BOARDING, Ugx.of(250_000));
            Student ruth = finance.studentAccounts.addStudent(t(2026, 1), "Ruth", "Akello",
                    SchoolClass.P3, Residency.NATIONAL, null, Ugx.of(250_000));

            assertThat(line(brian, 2026, 1).amount()).isEqualTo(Ugx.of(250_000));
            assertThat(line(brian, 2026, 1).boarding()).isEqualTo(Boarding.BOARDING);
            assertThat(line(ruth, 2026, 1).amount()).isEqualTo(Ugx.of(250_000));
            assertThat(line(ruth, 2026, 1).boarding()).isEqualTo(Boarding.DAY);
            assertThat(storedAmount(brian, 2026, 1)).isEqualTo(250_000L);
        }

        @Test
        @DisplayName("AC-4: Add student with Amount 0 adds them with 0")
        void addWithZero() {
            Student sam = add(2026, 1, "Sam", "Opio", 0);

            assertThat(line(sam, 2026, 1).amount()).isEqualTo(Ugx.ZERO);
            assertThat(storedAmount(sam, 2026, 1)).isEqualTo(0L);
        }

        @Test
        @DisplayName("AC-5: Add student with no amount or a negative one is refused and creates no student")
        void addWithoutAmount() {
            assertThatThrownBy(() -> finance.studentAccounts.addStudent(t(2026, 1), "Sam", "Opio",
                    SchoolClass.P7, Residency.NATIONAL, Boarding.DAY, null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage(AMOUNT_REQUIRED);
            assertThatThrownBy(() -> add(2026, 1, "Sam", "Opio", -5))
                    .isInstanceOf(ValidationException.class);

            assertThat(liveRows("student")).isZero();
            assertThat(liveRows("student_term")).isZero();
        }

        @Test
        @DisplayName("AC-6: clearing a student's Amount is refused and the amount stays")
        void clearingIsRefused() {
            Student ann = add(2026, 1, "Ann", "Akello", 250_000);

            assertThatThrownBy(() -> finance.studentAccounts.setAmount(ann.id(), t(2026, 1), null))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage(AMOUNT_REQUIRED);

            assertThat(line(ann, 2026, 1).amount()).isEqualTo(Ugx.of(250_000));
        }

        @Test
        @DisplayName("AC-7: a new term copies the amount from the term before, once; each term is then its own")
        void copiedOnce() {
            Student ann = add(2026, 1, "Ann", "Akello", 250_000);

            finance.studentAccounts.openTerm(t(2026, 2));
            assertThat(line(ann, 2026, 2).amount()).isEqualTo(Ugx.of(250_000));
            assertThat(storedAmount(ann, 2026, 2)).isEqualTo(250_000L);

            finance.studentAccounts.setAmount(ann.id(), t(2026, 1), Ugx.of(270_000));
            assertThat(line(ann, 2026, 2).amount()).isEqualTo(Ugx.of(250_000));

            finance.studentAccounts.setAmount(ann.id(), t(2026, 2), Ugx.of(300_000));
            assertThat(line(ann, 2026, 1).amount()).isEqualTo(Ugx.of(270_000));

            finance.studentAccounts.openTerm(t(2026, 3));
            assertThat(line(ann, 2026, 3).amount()).isEqualTo(Ugx.of(300_000));
        }

        @Test
        @DisplayName("AC-8: Joined moved earlier, the earlier term copies the amount from the earliest later term")
        void copiedFromLaterTerm() {
            Student cara = add(2026, 3, "Cara", "Nambi", 300_000);

            edit(cara, 2026, 3, t(2026, 1), StudentStatus.ACTIVE);
            finance.studentAccounts.openTerm(t(2026, 1));

            assertThat(line(cara, 2026, 1).amount()).isEqualTo(Ugx.of(300_000));
            assertThat(storedAmount(cara, 2026, 1)).isEqualTo(300_000L);
        }

        @Test
        @DisplayName("AC-8: a student from before 003 on no term is added with Amount 0")
        void noOtherTermIsZero() {
            Student grace = createStudent("Grace", "Atim", SchoolClass.P5);

            finance.studentAccounts.openTerm(t(2026, 2));

            assertThat(line(grace, 2026, 2).amount()).isEqualTo(Ugx.ZERO);
            assertThat(storedAmount(grace, 2026, 2)).isEqualTo(0L);
        }

        @Test
        @DisplayName("AC-9: marked Active again on Term 2, the student is back with the amount from Term 1")
        void activeAgainCopiesAmount() {
            Student ben = add(2026, 1, "Ben", "Okello", 200_000);
            finance.studentAccounts.openTerm(t(2026, 2));
            edit(ben, 2026, 1, t(2026, 1), StudentStatus.LEFT);

            edit(ben, 2026, 2, t(2026, 1), StudentStatus.ACTIVE);

            assertThat(line(ben, 2026, 2).amount()).isEqualTo(Ugx.of(200_000));
            assertThat(storedAmount(ben, 2026, 2)).isEqualTo(200_000L);
        }

        @Test
        @DisplayName("AC-10: switching to Boarding keeps the amount, and Term 1 is unchanged")
        void boardingKeepsAmount() {
            Student amina = add(2026, 1, "Amina", "Nakato", 250_000);
            finance.studentAccounts.openTerm(t(2026, 2));
            StudentTermLine term1 = line(amina, 2026, 1);

            StudentTermLine term2 = finance.studentAccounts.setBoarding(amina.id(), t(2026, 2),
                    Boarding.BOARDING);

            assertThat(term2.boarding()).isEqualTo(Boarding.BOARDING);
            assertThat(term2.amount()).isEqualTo(Ugx.of(250_000));
            assertThat(line(amina, 2026, 1)).isEqualTo(term1);
        }
    }

    @Nested
    class EarlierTerms {

        /** A term's money figures as pages 2 and 5 show them. */
        private record Figures(List<List<Object>> lines, StudentsTotals totals,
                SummaryRow students, ClassFee p7, ClassFee p3) {
        }

        private Figures figures(TermRef term) {
            return new Figures(
                    finance.studentAccounts.lines(term).stream()
                            .map(l -> Arrays.<Object>asList(l.studentId(), l.name(),
                                    l.schoolClass(), l.boarding(), l.amount(), l.debt(), l.ream(),
                                    l.total(), l.payments(), l.balance(), l.remarks()))
                            .toList(),
                    finance.studentAccounts.totals(term),
                    finance.accounts.summary(term).income().get(0),
                    finance.studentAccounts.classFee(SchoolClass.P7, term),
                    finance.studentAccounts.classFee(SchoolClass.P3, term));
        }

        @Test
        @DisplayName("AC-11: nothing done on Terms 2 and 3 changes Term 1")
        void laterActionsLeaveTerm1Alone() {
            finance.studentAccounts.setClassReam(SchoolClass.P7, t(2026, 1), Ugx.of(10_000));
            Student ann = add(2026, 1, "Ann", "Akello", 300_000);
            Student ben = finance.studentAccounts.addStudent(t(2026, 1), "Ben", "Okello",
                    SchoolClass.P3, Residency.NATIONAL, Boarding.BOARDING, Ugx.of(450_000));
            finance.studentAccounts.setAmount(ann.id(), t(2026, 1), Ugx.of(280_000));
            finance.studentAccounts.setPayment(ann.id(), t(2026, 1), 1, Ugx.of(100_000));
            finance.studentAccounts.setPayment(ben.id(), t(2026, 1), 2, Ugx.of(50_000));
            finance.studentAccounts.openTerm(t(2026, 2));
            finance.studentAccounts.openTerm(t(2026, 3));
            Figures term1 = figures(t(2026, 1));

            finance.studentAccounts.setClassReam(SchoolClass.P7, t(2026, 2), Ugx.of(12_000));
            finance.studentAccounts.setClassReam(SchoolClass.P3, t(2026, 3), Ugx.of(6_000));
            add(2026, 2, "Cara", "Nambi", 320_000);
            finance.studentAccounts.setBoarding(ann.id(), t(2026, 2), Boarding.BOARDING);
            finance.studentAccounts.setAmount(ann.id(), t(2026, 2), Ugx.of(500_000));
            assertThatThrownBy(() -> finance.studentAccounts.setAmount(ann.id(), t(2026, 3), null))
                    .isInstanceOf(ValidationException.class);
            edit(ben, 2026, 2, t(2026, 1), StudentStatus.LEFT);
            edit(ben, 2026, 3, t(2026, 1), StudentStatus.ACTIVE);
            finance.studentAccounts.openTerm(t(2026, 1));

            assertThat(figures(t(2026, 1))).isEqualTo(term1);
        }
    }
}

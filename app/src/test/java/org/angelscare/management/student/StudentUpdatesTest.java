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
import org.angelscare.management.support.FinanceTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Spec 003: Day and Boarding, who is on a term, Edit student, and earlier terms left alone. */
class StudentUpdatesTest extends FinanceTest {

    @BeforeEach
    void schoolYears() {
        createYear(2026);
        createYear(2027);
    }

    private StudentTermLine line(Student student, int year, int term) {
        return finance.studentAccounts.line(student.id(), t(year, term));
    }

    /** Who is on the term, Left students included, in page order. */
    private List<String> names(int year, int term) {
        return finance.studentAccounts.lines(t(year, term)).stream()
                .map(StudentTermLine::name).toList();
    }

    private Student stored(Student student) {
        return finance.students.find(student.id()).orElseThrow();
    }

    /** P7: Day 300,000, Boarding 500,000 and Ream 10,000 from 2026-2027 Term 1. */
    private void p7Fees() {
        finance.studentAccounts.setClassFee(SchoolClass.P7, t(2026, 1), Ugx.of(300_000),
                Ugx.of(500_000), Ugx.of(10_000));
    }

    /** P7 as before 003: a fee of 300,000 and ream of 10,000 from Term 1, no Boarding fee. */
    private void p7DayFee() {
        finance.studentAccounts.setClassFee(SchoolClass.P7, t(2026, 1), Ugx.of(300_000), null,
                Ugx.of(10_000));
    }

    /** "Add student" in P7 on the page of {@code year} Term {@code term}, as in 002. */
    private Student add(int year, int term, String firstName, String lastName) {
        return finance.studentAccounts.addStudent(t(year, term), firstName, lastName,
                SchoolClass.P7, Residency.NATIONAL);
    }

    private Student addAs(Boarding boarding, String firstName, String lastName) {
        return finance.studentAccounts.addStudent(t(2026, 1), firstName, lastName, SchoolClass.P7,
                Residency.NATIONAL, boarding);
    }

    /** Edit student, saved on {@code year} Term {@code term}'s page, changing joined and status. */
    private Student edit(Student student, int year, int term, TermRef joined,
            StudentStatus status) {
        Student current = stored(student);
        return finance.studentAccounts.editStudent(student.id(), t(year, term), new StudentEdit(
                current.firstName(), current.lastName(), current.residency(), joined, status));
    }

    /** A term's money figures as pages 2 and 5 show them, without the Left tag. */
    private record Figures(List<List<Object>> lines, StudentsTotals totals, SummaryRow students) {
    }

    private Figures figures(TermRef term) {
        return new Figures(
                finance.studentAccounts.lines(term).stream()
                        .map(l -> Arrays.<Object>asList(l.studentId(), l.name(), l.schoolClass(),
                                l.amount(), l.debt(), l.ream(), l.total(), l.payments(),
                                l.balance(), l.remarks()))
                        .toList(),
                finance.studentAccounts.totals(term),
                finance.accounts.summary(term).income().get(0));
    }

    @Nested
    class DayAndBoarding {

        @Test
        @DisplayName("AC-2: Day and Boarding students get their class's Day or Boarding fee, carried forward")
        void feesByBoarding() {
            p7Fees();
            Student day = addAs(Boarding.DAY, "Amina", "Nakato");
            Student boarder = addAs(Boarding.BOARDING, "Brian", "Okello");
            finance.studentAccounts.openTerm(t(2026, 2));
            finance.studentAccounts.openTerm(t(2027, 1));

            for (TermRef term : List.of(t(2026, 1), t(2026, 2), t(2027, 1))) {
                StudentTermLine dayLine = finance.studentAccounts.line(day.id(), term);
                StudentTermLine boarderLine = finance.studentAccounts.line(boarder.id(), term);
                assertThat(dayLine.amount()).as(term.label()).isEqualTo(Ugx.of(300_000));
                assertThat(boarderLine.amount()).as(term.label()).isEqualTo(Ugx.of(500_000));
                assertThat(dayLine.ream()).as(term.label()).isEqualTo(Ugx.of(10_000));
                assertThat(boarderLine.ream()).as(term.label()).isEqualTo(Ugx.of(10_000));
            }
        }

        @Test
        @DisplayName("AC-2: a Boarding fee changed in Term 3 applies from Term 3 on; Day fee and Ream stay")
        void boardingFeeCarriesForward() {
            p7Fees();

            finance.studentAccounts.setClassFee(SchoolClass.P7, t(2026, 3), Ugx.of(300_000),
                    Ugx.of(550_000), Ugx.of(10_000));

            ClassFee term3 = finance.studentAccounts.classFee(SchoolClass.P7, t(2026, 3));
            assertThat(finance.studentAccounts.classFee(SchoolClass.P7, t(2026, 2)).boardingFee())
                    .isEqualTo(Ugx.of(500_000));
            assertThat(term3.boardingFee()).isEqualTo(Ugx.of(550_000));
            assertThat(term3.amount()).isEqualTo(Ugx.of(300_000));
            assertThat(term3.ream()).isEqualTo(Ugx.of(10_000));
            assertThat(finance.studentAccounts.classFee(SchoolClass.P7, t(2027, 1)).boardingFee())
                    .isEqualTo(Ugx.of(550_000));
        }

        @Test
        @DisplayName("AC-2: a class with no Boarding fee set charges Boarding students 0; negative is refused")
        void noBoardingFee() {
            finance.studentAccounts.setClassFee(SchoolClass.P7, t(2026, 1), Ugx.of(300_000), null,
                    Ugx.of(10_000));
            Student boarder = addAs(Boarding.BOARDING, "Brian", "Okello");

            assertThat(line(boarder, 2026, 1).amount()).isEqualTo(Ugx.ZERO);
            assertThat(finance.studentAccounts.classFee(SchoolClass.P7, t(2026, 1)).boardingFee())
                    .isEqualTo(Ugx.ZERO);
            assertThat(finance.studentAccounts.classFee(SchoolClass.P1, t(2026, 1)).boardingFee())
                    .isEqualTo(Ugx.ZERO);
            assertThatThrownBy(() -> finance.studentAccounts.setClassFee(SchoolClass.P7,
                    t(2026, 1), Ugx.of(300_000), Ugx.of(-1), Ugx.of(10_000)))
                    .isInstanceOf(ValidationException.class);
        }

        @Test
        @DisplayName("AC-3: Add student with Boarding puts them on the term as Boarding; with no choice, Day")
        void addStudentWithBoarding() {
            finance.studentAccounts.setClassFee(SchoolClass.P3, t(2026, 1), Ugx.of(200_000),
                    Ugx.of(350_000), Ugx.of(5_000));

            Student brian = finance.studentAccounts.addStudent(t(2026, 1), "Brian", "Okello",
                    SchoolClass.P3, Residency.NATIONAL, Boarding.BOARDING);
            Student ruth = finance.studentAccounts.addStudent(t(2026, 1), "Ruth", "Akello",
                    SchoolClass.P3, Residency.NATIONAL, null);

            assertThat(stored(brian).boarding()).isEqualTo(Boarding.BOARDING);
            assertThat(line(brian, 2026, 1).boarding()).isEqualTo(Boarding.BOARDING);
            assertThat(line(brian, 2026, 1).amount()).isEqualTo(Ugx.of(350_000));
            assertThat(stored(ruth).boarding()).isEqualTo(Boarding.DAY);
            assertThat(line(ruth, 2026, 1).boarding()).isEqualTo(Boarding.DAY);
            assertThat(line(ruth, 2026, 1).amount()).isEqualTo(Ugx.of(200_000));
        }

        @Test
        @DisplayName("AC-4: switching to Boarding on Term 2 changes Terms 2 and 3 and later terms, not Term 1")
        void boardingIsPerTerm() {
            p7Fees();
            Student amina = addAs(Boarding.DAY, "Amina", "Nakato");
            finance.studentAccounts.openTerm(t(2026, 2));
            finance.studentAccounts.openTerm(t(2026, 3));
            StudentTermLine term1 = line(amina, 2026, 1);

            StudentTermLine term2 = finance.studentAccounts.setBoarding(amina.id(), t(2026, 2),
                    Boarding.BOARDING);

            assertThat(term2.boarding()).isEqualTo(Boarding.BOARDING);
            assertThat(term2.amount()).isEqualTo(Ugx.of(500_000));
            assertThat(line(amina, 2026, 3).boarding()).isEqualTo(Boarding.BOARDING);
            assertThat(line(amina, 2026, 3).amount()).isEqualTo(Ugx.of(500_000));
            assertThat(line(amina, 2026, 1)).isEqualTo(term1);
            assertThat(line(amina, 2026, 1).boarding()).isEqualTo(Boarding.DAY);
            assertThat(stored(amina).boarding()).isEqualTo(Boarding.BOARDING);
            finance.studentAccounts.openTerm(t(2027, 1));
            assertThat(line(amina, 2027, 1).boarding()).isEqualTo(Boarding.BOARDING);
        }

        @Test
        @DisplayName("AC-4: an Amount typed in for the student stays when they switch")
        void typedAmountStays() {
            p7Fees();
            Student amina = addAs(Boarding.DAY, "Amina", "Nakato");
            finance.studentAccounts.setAmount(amina.id(), t(2026, 1), Ugx.of(250_000));

            StudentTermLine switched = finance.studentAccounts.setBoarding(amina.id(), t(2026, 1),
                    Boarding.BOARDING);

            assertThat(switched.amount()).isEqualTo(Ugx.of(250_000));
            assertThat(switched.amountOverridden()).isTrue();
            assertThat(finance.studentAccounts.setAmount(amina.id(), t(2026, 1), null).amount())
                    .isEqualTo(Ugx.of(500_000));
        }
    }

    @Nested
    class WhoIsOnATerm {

        @Test
        @DisplayName("AC-5: opening an earlier term doesn't add a student who joined later, or give them debt")
        void earlierTermsOnlyListWhoWasThere() {
            p7DayFee();
            add(2026, 1, "Ann", "Akello");
            finance.studentAccounts.openTerm(t(2026, 3));
            Student cara = add(2026, 3, "Cara", "Nambi");

            finance.studentAccounts.openTerm(t(2026, 1));
            finance.studentAccounts.openTerm(t(2026, 2));

            assertThat(names(2026, 1)).containsExactly("Ann Akello");
            assertThat(names(2026, 2)).containsExactly("Ann Akello");
            assertThat(names(2026, 3)).containsExactly("Ann Akello", "Cara Nambi");
            assertThat(line(cara, 2026, 3).debt()).isEqualTo(Ugx.ZERO);
        }

        @Test
        @DisplayName("AC-6: a student on no term (from before V4) joins the first term opened, not an earlier one")
        void noJoinedTermJoinsTheFirstTermOpened() {
            Student grace = createStudent("Grace", "Atim", SchoolClass.P5);

            finance.studentAccounts.openTerm(t(2026, 2));
            finance.studentAccounts.openTerm(t(2026, 1));

            assertThat(names(2026, 2)).containsExactly("Grace Atim");
            assertThat(names(2026, 1)).isEmpty();
            assertThat(stored(grace).joinedTerm()).isEqualTo(t(2026, 2));
        }

        @Test
        @DisplayName("AC-7: moving Joined earlier puts the student on earlier terms; their debt carries from them")
        void joinedEarlier() {
            p7DayFee();
            add(2026, 1, "Ann", "Akello");
            finance.studentAccounts.openTerm(t(2026, 3));
            Student cara = add(2026, 3, "Cara", "Nambi");

            edit(cara, 2026, 3, t(2026, 1), StudentStatus.ACTIVE);
            finance.studentAccounts.openTerm(t(2026, 1));
            finance.studentAccounts.openTerm(t(2026, 2));

            assertThat(names(2026, 1)).contains("Cara Nambi");
            assertThat(names(2026, 2)).contains("Cara Nambi");
            assertThat(stored(cara).joinedTerm()).isEqualTo(t(2026, 1));
            assertThat(line(cara, 2026, 3).debt()).isEqualTo(line(cara, 2026, 2).balance())
                    .isEqualTo(Ugx.of(620_000));
        }

        @Test
        @DisplayName("AC-7: moving Joined later takes them off earlier terms without payments; past a payment it's refused")
        void joinedLater() {
            p7DayFee();
            Student dan = add(2026, 1, "Dan", "Mugisha");
            finance.studentAccounts.openTerm(t(2026, 2));
            finance.studentAccounts.setPayment(dan.id(), t(2026, 2), 1, Ugx.of(50_000));

            edit(dan, 2026, 2, t(2026, 2), StudentStatus.ACTIVE);

            assertThat(names(2026, 1)).isEmpty();
            assertThat(names(2026, 2)).containsExactly("Dan Mugisha");
            assertThatThrownBy(() -> edit(dan, 2026, 2, t(2026, 3), StudentStatus.ACTIVE))
                    .isInstanceOf(ValidationException.class)
                    .hasMessageContaining("2026-2027 Term 2");
            assertThat(names(2026, 2)).containsExactly("Dan Mugisha");
            assertThat(stored(dan).joinedTerm()).isEqualTo(t(2026, 2));
        }

        @Test
        @DisplayName("AC-7: Joined can't be after a Left student's last term")
        void joinedAfterLastTerm() {
            p7DayFee();
            Student eve = add(2026, 1, "Eve", "Nakku");
            edit(eve, 2026, 1, t(2026, 1), StudentStatus.LEFT);

            assertThatThrownBy(() -> edit(eve, 2026, 1, t(2026, 2), StudentStatus.LEFT))
                    .isInstanceOf(ValidationException.class);
            assertThat(stored(eve).joinedTerm()).isEqualTo(t(2026, 1));
        }

        @Test
        @DisplayName("AC-8: marked Left on Term 1, Term 1 is unchanged but tagged; later terms lose him unless he paid there")
        void markingLeft() {
            p7DayFee();
            Student ann = add(2026, 1, "Ann", "Akello");
            Student ben = add(2026, 1, "Ben", "Okello");
            finance.studentAccounts.setPayment(ben.id(), t(2026, 1), 1, Ugx.of(100_000));
            finance.studentAccounts.openTerm(t(2026, 2));
            finance.studentAccounts.openTerm(t(2026, 3));
            finance.studentAccounts.setPayment(ben.id(), t(2026, 3), 2, Ugx.of(20_000));
            Figures term1 = figures(t(2026, 1));

            edit(ben, 2026, 1, t(2026, 1), StudentStatus.LEFT);

            assertThat(figures(t(2026, 1))).isEqualTo(term1);
            assertThat(line(ben, 2026, 1).shownName()).isEqualTo("Ben Okello (Left)");
            assertThat(line(ann, 2026, 1).shownName()).isEqualTo("Ann Akello");
            assertThat(names(2026, 2)).containsExactly("Ann Akello");
            assertThat(line(ben, 2026, 3).left()).isTrue();
            finance.studentAccounts.openTerm(t(2027, 1));
            assertThat(names(2027, 1)).containsExactly("Ann Akello");
            assertThat(stored(ben).status()).isEqualTo(StudentStatus.LEFT);
            assertThat(stored(ben).lastTerm()).isEqualTo(t(2026, 1));
        }

        @Test
        @DisplayName("AC-9: marked Active again on Term 2, the student is back on Term 2 and on later terms")
        void markingActiveAgain() {
            p7DayFee();
            Student ben = add(2026, 1, "Ben", "Okello");
            finance.studentAccounts.openTerm(t(2026, 2));
            edit(ben, 2026, 1, t(2026, 1), StudentStatus.LEFT);
            assertThat(names(2026, 2)).isEmpty();

            edit(ben, 2026, 2, t(2026, 1), StudentStatus.ACTIVE);

            assertThat(line(ben, 2026, 2).schoolClass()).isEqualTo(SchoolClass.P7);
            assertThat(line(ben, 2026, 2).boarding()).isEqualTo(Boarding.DAY);
            assertThat(line(ben, 2026, 1).shownName()).isEqualTo("Ben Okello");
            assertThat(stored(ben).status()).isEqualTo(StudentStatus.ACTIVE);
            assertThat(stored(ben).lastTerm()).isNull();
            finance.studentAccounts.openTerm(t(2027, 1));
            assertThat(names(2027, 1)).containsExactly("Ben Okello");
        }

        @Test
        @DisplayName("AC-10: Left students are listed and counted in the term's totals")
        void leftStudentsAreListedAndCounted() {
            p7DayFee();
            add(2026, 1, "Ann", "Akello");
            add(2026, 1, "Cara", "Nambi");
            Student ben = add(2026, 1, "Ben", "Okello");
            finance.studentAccounts.setPayment(ben.id(), t(2026, 1), 1, Ugx.of(100_000));
            edit(ben, 2026, 1, t(2026, 1), StudentStatus.LEFT);

            assertThat(finance.studentAccounts.lines(t(2026, 1)))
                    .extracting(StudentTermLine::shownName)
                    .containsExactly("Ann Akello", "Cara Nambi", "Ben Okello (Left)");
            assertThat(finance.studentAccounts.totals(t(2026, 1)).expected())
                    .isEqualTo(Ugx.of(930_000));
            assertThat(finance.studentAccounts.totals(t(2026, 1)).total())
                    .isEqualTo(Ugx.of(100_000));
        }
    }

    @Nested
    class EarlierTerms {

        @Test
        @DisplayName("AC-11: nothing done on Terms 2 and 3 changes Term 1")
        void laterActionsLeaveTerm1Alone() {
            p7Fees();
            finance.studentAccounts.setClassFee(SchoolClass.P3, t(2026, 1), Ugx.of(200_000),
                    Ugx.of(350_000), Ugx.of(5_000));
            Student ann = addAs(Boarding.DAY, "Ann", "Akello");
            Student ben = finance.studentAccounts.addStudent(t(2026, 1), "Ben", "Okello",
                    SchoolClass.P3, Residency.NATIONAL, Boarding.BOARDING);
            finance.studentAccounts.setPayment(ann.id(), t(2026, 1), 1, Ugx.of(100_000));
            finance.studentAccounts.setPayment(ben.id(), t(2026, 1), 2, Ugx.of(50_000));
            finance.studentAccounts.setDebt(ann.id(), t(2026, 1), Ugx.of(5_000));
            finance.studentAccounts.setRemarks(ben.id(), t(2026, 1), "Pays late");
            finance.studentAccounts.openTerm(t(2026, 2));
            finance.studentAccounts.openTerm(t(2026, 3));
            List<StudentTermLine> lines = finance.studentAccounts.lines(t(2026, 1));
            StudentsTotals totals = finance.studentAccounts.totals(t(2026, 1));
            SummaryRow students = finance.accounts.summary(t(2026, 1)).income().get(0);
            ClassFee p7 = finance.studentAccounts.classFee(SchoolClass.P7, t(2026, 1));
            ClassFee p3 = finance.studentAccounts.classFee(SchoolClass.P3, t(2026, 1));

            finance.studentAccounts.setClassFee(SchoolClass.P7, t(2026, 2), Ugx.of(320_000),
                    Ugx.of(520_000), Ugx.of(12_000));
            finance.studentAccounts.setClassFee(SchoolClass.P3, t(2026, 3), Ugx.of(210_000),
                    Ugx.of(360_000), Ugx.of(6_000));
            finance.studentAccounts.addStudent(t(2026, 2), "Cara", "Nambi", SchoolClass.P7,
                    Residency.NATIONAL, Boarding.BOARDING);
            finance.studentAccounts.setBoarding(ann.id(), t(2026, 2), Boarding.BOARDING);
            finance.studentAccounts.setAmount(ann.id(), t(2026, 3), Ugx.of(250_000));
            finance.studentAccounts.setDebt(ann.id(), t(2026, 2), Ugx.of(1_000));
            finance.studentAccounts.setReam(ann.id(), t(2026, 2), Ugx.ZERO);
            finance.studentAccounts.setPayment(ann.id(), t(2026, 2), 1, Ugx.of(10_000));
            finance.studentAccounts.setRemarks(ann.id(), t(2026, 2), "Moved to boarding");
            finance.studentAccounts.removeFromTerm(ben.id(), t(2026, 3));
            edit(ben, 2026, 2, t(2026, 1), StudentStatus.LEFT);
            edit(ben, 2026, 2, t(2026, 1), StudentStatus.ACTIVE);
            finance.studentAccounts.openTerm(t(2026, 1));

            assertThat(finance.studentAccounts.lines(t(2026, 1))).isEqualTo(lines);
            assertThat(finance.studentAccounts.totals(t(2026, 1))).isEqualTo(totals);
            assertThat(finance.accounts.summary(t(2026, 1)).income().get(0)).isEqualTo(students);
            assertThat(finance.studentAccounts.classFee(SchoolClass.P7, t(2026, 1))).isEqualTo(p7);
            assertThat(finance.studentAccounts.classFee(SchoolClass.P3, t(2026, 1))).isEqualTo(p3);
        }
    }

    @Nested
    class EditStudent {

        @Test
        @DisplayName("AC-12: Edit student saves trimmed names and Refugee, shown on every term; class stays")
        void savesNames() {
            p7DayFee();
            Student ben = add(2026, 1, "Ben", "Okello");
            finance.studentAccounts.openTerm(t(2026, 2));

            Student saved = finance.studentAccounts.editStudent(ben.id(), t(2026, 2),
                    new StudentEdit(" Benjamin ", "Okello", Residency.REFUGEE, t(2026, 1),
                            StudentStatus.ACTIVE));

            assertThat(saved.firstName()).isEqualTo("Benjamin");
            assertThat(saved.residency()).isEqualTo(Residency.REFUGEE);
            assertThat(saved.schoolClass()).isEqualTo(SchoolClass.P7);
            assertThat(line(ben, 2026, 1).name()).isEqualTo("Benjamin Okello");
            assertThat(line(ben, 2026, 2).name()).isEqualTo("Benjamin Okello");
        }

        @Test
        @DisplayName("AC-12: an empty first name is refused with 001's message and nothing in that save is applied")
        void refusedSaveAppliesNothing() {
            p7DayFee();
            Student ben = add(2026, 1, "Ben", "Okello");

            assertThatThrownBy(() -> finance.studentAccounts.editStudent(ben.id(), t(2026, 1),
                    new StudentEdit(" ", "Opio", Residency.REFUGEE, t(2026, 1),
                            StudentStatus.LEFT)))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("First name can't be empty.");

            assertThat(stored(ben).lastName()).isEqualTo("Okello");
            assertThat(stored(ben).residency()).isEqualTo(Residency.NATIONAL);
            assertThat(stored(ben).status()).isEqualTo(StudentStatus.ACTIVE);
        }
    }
}

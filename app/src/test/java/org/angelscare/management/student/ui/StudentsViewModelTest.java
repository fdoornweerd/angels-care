package org.angelscare.management.student.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javafx.collections.ListChangeListener;
import org.angelscare.management.accounts.model.SummaryRow;
import org.angelscare.management.common.MonthlyAmounts;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.student.model.Boarding;
import org.angelscare.management.student.model.ClassFee;
import org.angelscare.management.student.model.Level;
import org.angelscare.management.student.model.RegisterTotals;
import org.angelscare.management.student.model.Residency;
import org.angelscare.management.student.model.SchoolClass;
import org.angelscare.management.student.model.StudentEdit;
import org.angelscare.management.student.model.StudentStatus;
import org.angelscare.management.student.model.StudentTermLine;
import org.angelscare.management.student.ui.StudentsViewModel.ClassSection;
import org.angelscare.management.support.ScreenTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StudentsViewModelTest extends ScreenTest {

    private StudentsViewModel page;

    @BeforeEach
    void term() {
        createYear(2026);
        finance.studentAccounts.setClassFee(SchoolClass.P7, t(2026, 1), Ugx.of(300_000), null,
                Ugx.of(10_000));
        page = studentsPage();
    }

    private StudentTermLine amina() {
        return student("Amina Nakato");
    }

    private StudentTermLine student(String name) {
        return page.section(SchoolClass.P7).lines().stream()
                .filter(l -> l.name().equals(name)).findFirst().orElseThrow();
    }

    /** P7 with a Boarding fee too: Day 300,000, Boarding 500,000, Ream 10,000. */
    private void p7WithBoardingFee() {
        finance.studentAccounts.setClassFee(SchoolClass.P7, t(2026, 1), Ugx.of(300_000),
                Ugx.of(500_000), Ugx.of(10_000));
    }

    private static <T> T last(List<T> list) {
        return list.get(list.size() - 1);
    }

    /** A class table's totals row holds the totals of {@code students}. */
    private static void assertTotalsRow(StudentTermLine row, List<StudentTermLine> students) {
        RegisterTotals expected = RegisterTotals.of(students);
        assertThat(row.studentId()).as("a totals row has no student").isNull();
        assertThat(row.amount()).as("amount").isEqualTo(expected.amount());
        assertThat(row.debt()).as("debt").isEqualTo(expected.debt());
        assertThat(row.ream()).as("ream").isEqualTo(expected.ream());
        assertThat(row.payments()).as("payments").isEqualTo(expected.paid());
        assertThat(row.balance()).as("balance").isEqualTo(expected.balance());
    }

    @Test
    @DisplayName("AC-16: one section per class, Nursery then Primary, with the term's students")
    void sections() {
        createStudent("Amina", "Nakato", SchoolClass.P7);
        createStudent("Ruth", "Akello", SchoolClass.TOP);

        page.show(t(2026, 1));

        assertThat(page.sections()).extracting(ClassSection::schoolClass)
                .containsExactly(SchoolClass.values());
        assertThat(page.sections().subList(0, 3)).allSatisfy(
                s -> assertThat(s.schoolClass().level()).isEqualTo(Level.NURSERY));
        assertThat(page.section(SchoolClass.TOP).lines()).extracting(StudentTermLine::name)
                .containsExactly("Ruth Akello");
        assertThat(page.section(SchoolClass.P1).lines()).isEmpty();
        assertThat(page.section(SchoolClass.P7).feeProperty().get().amount())
                .isEqualTo(Ugx.of(300_000));
    }

    @Test
    @DisplayName("AC-18: typed amounts, debt (a minus is credit), payments and remarks are saved")
    void edits() {
        createStudent("Amina", "Nakato", SchoolClass.P7);
        page.show(t(2026, 1));

        assertThat(page.editAmount(amina(), "250,000")).isTrue();
        assertThat(amina().amount()).isEqualTo(Ugx.of(250_000));
        assertThat(page.editDebt(amina(), "-5,000")).isTrue();
        assertThat(amina().debt()).isEqualTo(Ugx.of(-5_000));
        assertThat(page.editPayment(amina(), 1, "100,000")).isTrue();
        assertThat(page.editRemarks(amina(), "Pays late")).isTrue();

        StudentTermLine line = amina();
        assertThat(line.total()).isEqualTo(Ugx.of(255_000));
        assertThat(line.balance()).isEqualTo(Ugx.of(155_000));
        assertThat(line.remarks()).isEqualTo("Pays late");
        assertThat(finance.studentAccounts.line(line.studentId(), t(2026, 1))).isEqualTo(line);

        assertThat(page.editAmount(amina(), "")).isTrue();
        assertThat(amina().amount()).isEqualTo(Ugx.of(300_000));
        assertThat(page.editPayment(amina(), 1, "")).isTrue();
        assertThat(amina().paid1()).isNull();
    }

    @Test
    @DisplayName("AC-18: an unreadable amount names the student and saves nothing")
    void invalidEdit() {
        createStudent("Amina", "Nakato", SchoolClass.P7);
        page.show(t(2026, 1));

        assertThat(page.editPayment(amina(), 2, "abc")).isFalse();
        assertThat(page.error())
                .isEqualTo("Amina Nakato: Enter a whole amount in shillings, e.g. 300,000.");
        assertThat(amina().paid2()).isNull();
        assertThat(page.editAmount(amina(), "-1")).isFalse();

        assertThat(page.editPayment(amina(), 2, "5000")).isTrue();
        assertThat(page.error()).isEmpty();
    }

    @Test
    @DisplayName("AC-17/AC-18: class fee edits update the class's students and the totals")
    void classFeeAndTotals() {
        createStudent("Amina", "Nakato", SchoolClass.P7);
        createStudent("Brian", "Okello", SchoolClass.P7);
        page.show(t(2026, 1));

        assertThat(page.editClassFee(page.section(SchoolClass.P7), "320,000")).isTrue();
        assertThat(page.editClassReam(page.section(SchoolClass.P7), "12,000")).isTrue();
        page.editPayment(amina(), 3, "100,000");

        ClassSection p7 = page.section(SchoolClass.P7);
        assertThat(p7.lines()).extracting(StudentTermLine::amount)
                .containsOnly(Ugx.of(320_000));
        var totals = p7.totalsProperty().get();
        assertThat(totals.amount()).isEqualTo(Ugx.of(640_000));
        assertThat(totals.ream()).isEqualTo(Ugx.of(24_000));
        assertThat(totals.total()).isEqualTo(Ugx.of(664_000));
        assertThat(totals.paid()).isEqualTo(new MonthlyAmounts(Ugx.ZERO, Ugx.ZERO,
                Ugx.of(100_000)));
        assertThat(totals.balance()).isEqualTo(Ugx.of(564_000));
        assertThat(page.pageTotalsProperty().get()).isEqualTo(totals);
        assertThat(page.editClassFee(p7, "lots")).isFalse();
    }

    @Test
    @DisplayName("AC-19: a student added in a class appears there with the class defaults")
    void addStudent() {
        page.show(t(2026, 1));

        assertThat(page.addStudent(SchoolClass.P7, "Amina", "Nakato", Residency.REFUGEE)).isTrue();

        assertThat(amina().amount()).isEqualTo(Ugx.of(300_000));
        assertThat(page.addStudent(SchoolClass.P7, "", "Okello", Residency.NATIONAL)).isFalse();
        assertThat(page.error()).contains("First name");
        assertThat(page.section(SchoolClass.P7).lines()).hasSize(1);
    }

    @Test
    @DisplayName("AC-22: removing asks first and is refused while there are payments")
    void removeFromTerm() {
        createStudent("Amina", "Nakato", SchoolClass.P7);
        page.show(t(2026, 1));
        page.editPayment(amina(), 1, "1,000");
        dialogs.answerConfirm(true).answerConfirm(false).answerConfirm(true);

        assertThat(page.removeFromTerm(amina())).isFalse();
        assertThat(page.error()).contains("payments");

        page.editPayment(amina(), 1, "");
        assertThat(page.removeFromTerm(amina())).isFalse();
        assertThat(page.section(SchoolClass.P7).lines()).hasSize(1);
        assertThat(page.removeFromTerm(amina())).isTrue();

        assertThat(dialogs.asked).containsExactly(
                "Remove Amina Nakato from 2026-2027 Term 1?",
                "Remove Amina Nakato from 2026-2027 Term 1?",
                "Remove Amina Nakato from 2026-2027 Term 1?");
        assertThat(page.section(SchoolClass.P7).lines()).isEmpty();
    }

    @Test
    @DisplayName("AC-2 (003): the Boarding fee is edited on its own; Day fee and Ream edits keep it")
    void boardingFeeHeading() {
        page.show(t(2026, 1));

        assertThat(page.editClassBoardingFee(page.section(SchoolClass.P7), "550,000")).isTrue();
        ClassFee fee = page.section(SchoolClass.P7).feeProperty().get();
        assertThat(fee.boardingFee()).isEqualTo(Ugx.of(550_000));
        assertThat(fee.amount()).isEqualTo(Ugx.of(300_000));
        assertThat(fee.ream()).isEqualTo(Ugx.of(10_000));

        assertThat(page.editClassFee(page.section(SchoolClass.P7), "320,000")).isTrue();
        assertThat(page.editClassReam(page.section(SchoolClass.P7), "12,000")).isTrue();
        assertThat(page.section(SchoolClass.P7).feeProperty().get().boardingFee())
                .isEqualTo(Ugx.of(550_000));
        assertThat(page.editClassBoardingFee(page.section(SchoolClass.P7), "-1")).isFalse();
    }

    @Test
    @DisplayName("AC-3/AC-4 (003): Add student as Boarding, then switch them in the Day/Boarding column")
    void boardingColumn() {
        p7WithBoardingFee();
        page.show(t(2026, 1));

        assertThat(page.addStudent(SchoolClass.P7, "Amina", "Nakato", Residency.NATIONAL,
                Boarding.BOARDING)).as(page.error()).isTrue();
        assertThat(amina().boarding()).isEqualTo(Boarding.BOARDING);
        assertThat(amina().amount()).isEqualTo(Ugx.of(500_000));

        assertThat(page.editBoarding(amina(), Boarding.DAY)).as(page.error()).isTrue();

        assertThat(amina().boarding()).isEqualTo(Boarding.DAY);
        assertThat(amina().amount()).isEqualTo(Ugx.of(300_000));
        assertThat(page.section(SchoolClass.P7).totalsProperty().get().amount())
                .isEqualTo(Ugx.of(300_000));
    }

    @Test
    @DisplayName("AC-10 (003): Left students are listed, tagged, and counted; page 5 matches page 2")
    void leftStudentsAreCounted() {
        var amina = createStudent("Amina", "Nakato", SchoolClass.P7);
        createStudent("Brian", "Okello", SchoolClass.P7);
        page.show(t(2026, 1));
        page.editPayment(amina(), 1, "100,000");
        finance.studentAccounts.editStudent(amina.id(), t(2026, 1), new StudentEdit("Amina",
                "Nakato", Residency.NATIONAL, t(2026, 1), StudentStatus.LEFT));

        page.show(t(2026, 1));

        assertThat(page.section(SchoolClass.P7).lines()).extracting(StudentTermLine::shownName)
                .containsExactly("Amina Nakato (Left)", "Brian Okello");
        SummaryRow students = finance.accounts.summary(t(2026, 1)).income().get(0);
        RegisterTotals totals = page.pageTotalsProperty().get();
        assertThat(totals.total()).isEqualTo(students.planned()).isEqualTo(Ugx.of(620_000));
        assertThat(totals.paid()).isEqualTo(students.actual());
        assertThat(Arrays.stream(StudentsViewModel.class.getMethods()).map(Method::getName))
                .doesNotContain("showLeftProperty");
    }

    @Test
    @DisplayName("AC-15: when a student's cell is edited, the class's totals row already has the new totals")
    void totalsRowFollowsEdits() {
        createStudent("Amina", "Nakato", SchoolClass.P7);
        createStudent("Brian", "Okello", SchoolClass.P7);
        page.show(t(2026, 1));
        StudentsViewModel.ClassSection p7 = page.section(SchoolClass.P7);
        List<StudentTermLine> totalsRowWhenNotified = new ArrayList<>();
        p7.rows().addListener((ListChangeListener<StudentTermLine>) change ->
                totalsRowWhenNotified.add(last(p7.rows())));

        page.editPayment(amina(), 1, "100,000");
        assertTotalsRow(last(totalsRowWhenNotified), p7.lines());
        assertThat(last(totalsRowWhenNotified).balance()).isEqualTo(Ugx.of(520_000));
        assertThat(amina().balance()).isEqualTo(Ugx.of(210_000));
        assertThat(page.pageTotalsProperty().get().balance()).isEqualTo(Ugx.of(520_000));

        page.editAmount(amina(), "250,000");
        assertTotalsRow(last(totalsRowWhenNotified), p7.lines());
        page.editDebt(amina(), "5,000");
        assertTotalsRow(last(totalsRowWhenNotified), p7.lines());
        page.editReam(amina(), "0");
        assertTotalsRow(last(totalsRowWhenNotified), p7.lines());
        // P7 has no Boarding fee, so Brian's amount drops to 0.
        page.editBoarding(student("Brian Okello"), Boarding.BOARDING);
        assertTotalsRow(last(totalsRowWhenNotified), p7.lines());
        assertThat(last(totalsRowWhenNotified).amount()).isEqualTo(Ugx.of(250_000));
        assertThat(last(p7.rows())).isEqualTo(last(totalsRowWhenNotified));
    }

    @Test
    @DisplayName("AC-15: after a class fee change, Add student, Edit student and Remove, the totals row is current")
    void totalsRowAfterReloads() {
        createStudent("Amina", "Nakato", SchoolClass.P7);
        createStudent("Brian", "Okello", SchoolClass.P7);
        page.show(t(2026, 1));

        page.editClassFee(page.section(SchoolClass.P7), "320,000");
        assertTotalsRow(last(page.section(SchoolClass.P7).rows()),
                page.section(SchoolClass.P7).lines());
        assertThat(last(page.section(SchoolClass.P7).rows()).amount()).isEqualTo(Ugx.of(640_000));

        page.addStudent(SchoolClass.P7, "Cara", "Nambi", Residency.NATIONAL, Boarding.DAY);
        assertThat(page.section(SchoolClass.P7).rows()).hasSize(4);
        assertTotalsRow(last(page.section(SchoolClass.P7).rows()),
                page.section(SchoolClass.P7).lines());

        EditStudentViewModel editor = page.editor(amina());
        editor.statusProperty().set(StudentStatus.LEFT);
        assertThat(editor.save()).as(editor.error()).isTrue();
        assertTotalsRow(last(page.section(SchoolClass.P7).rows()),
                page.section(SchoolClass.P7).lines());
        assertThat(last(page.section(SchoolClass.P7).rows()).amount()).isEqualTo(Ugx.of(960_000));

        dialogs.answerConfirm(true);
        assertThat(page.removeFromTerm(student("Cara Nambi"))).isTrue();
        assertTotalsRow(last(page.section(SchoolClass.P7).rows()),
                page.section(SchoolClass.P7).lines());
        assertThat(page.section(SchoolClass.P7).rows()).hasSize(3);
        assertThat(page.section(SchoolClass.P1).rows()).as("no students, no totals row").isEmpty();
    }
}

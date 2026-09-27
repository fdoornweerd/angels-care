package org.angelscare.management.student.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.angelscare.management.common.MonthlyAmounts;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.student.model.Level;
import org.angelscare.management.student.model.Residency;
import org.angelscare.management.student.model.SchoolClass;
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
        finance.studentAccounts.setClassFee(SchoolClass.P7, t(2026, 1), Ugx.of(300_000),
                Ugx.of(10_000));
        page = studentsPage();
    }

    private StudentTermLine amina() {
        return page.section(SchoolClass.P7).lines().stream()
                .filter(l -> l.name().equals("Amina Nakato")).findFirst().orElseThrow();
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
    @DisplayName("AC-16: 'Show students who left' reveals Left students already on the term")
    void showLeft() {
        var amina = createStudent("Amina", "Nakato", SchoolClass.P7);
        page.show(t(2026, 1));
        finance.students.setStatus(amina.id(), StudentStatus.LEFT);
        page.show(t(2026, 1));
        assertThat(page.section(SchoolClass.P7).lines()).isEmpty();

        page.showLeftProperty().set(true);

        assertThat(page.section(SchoolClass.P7).lines()).extracting(StudentTermLine::status)
                .containsExactly(StudentStatus.LEFT);
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
}

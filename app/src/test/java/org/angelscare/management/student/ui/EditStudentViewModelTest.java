package org.angelscare.management.student.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.angelscare.management.calendar.model.TermRef;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.student.model.Residency;
import org.angelscare.management.student.model.SchoolClass;
import org.angelscare.management.student.model.Student;
import org.angelscare.management.student.model.StudentStatus;
import org.angelscare.management.student.model.StudentTermLine;
import org.angelscare.management.support.ScreenTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Spec 003: the Edit student dialog, opened from a student's line on page 5. */
class EditStudentViewModelTest extends ScreenTest {

    private StudentsViewModel page;
    private Student ben;

    @BeforeEach
    void benInP5() {
        createYear(2026);
        createYear(2027);
        ben = finance.studentAccounts.addStudent(t(2026, 2), "Ben", "Okello", SchoolClass.P5,
                Residency.NATIONAL);
        page = studentsPage();
        page.show(t(2026, 2));
    }

    private StudentTermLine line(String name) {
        return page.section(SchoolClass.P5).lines().stream()
                .filter(l -> l.name().equals(name)).findFirst().orElseThrow();
    }

    private Student stored() {
        return finance.students.find(ben.id()).orElseThrow();
    }

    @Test
    @DisplayName("AC-12: the dialog opens with the student's details and every term to choose from")
    void opensFilledIn() {
        EditStudentViewModel editor = page.editor(line("Ben Okello"));

        assertThat(editor.title()).isEqualTo("Edit Ben Okello");
        assertThat(editor.firstNameProperty().get()).isEqualTo("Ben");
        assertThat(editor.lastNameProperty().get()).isEqualTo("Okello");
        assertThat(editor.residencyProperty().get()).isEqualTo(Residency.NATIONAL);
        assertThat(editor.joinedProperty().get()).isEqualTo(t(2026, 2));
        assertThat(editor.statusProperty().get()).isEqualTo(StudentStatus.ACTIVE);
        assertThat(editor.joinedChoices()).containsExactly(
                t(2026, 1), t(2026, 2), t(2026, 3), t(2027, 1), t(2027, 2), t(2027, 3));
        assertThat(editor.error()).isEmpty();
    }

    @Test
    @DisplayName("AC-12: Save applies trimmed names and Refugee, and the page shows them")
    void saves() {
        EditStudentViewModel editor = page.editor(line("Ben Okello"));
        editor.firstNameProperty().set(" Benjamin ");
        editor.residencyProperty().set(Residency.REFUGEE);

        assertThat(editor.save()).as(editor.error()).isTrue();

        assertThat(editor.error()).isEmpty();
        assertThat(stored().firstName()).isEqualTo("Benjamin");
        assertThat(stored().residency()).isEqualTo(Residency.REFUGEE);
        assertThat(page.section(SchoolClass.P5).lines()).extracting(StudentTermLine::name)
                .containsExactly("Benjamin Okello");
    }

    @Test
    @DisplayName("AC-12: an empty first name is refused in the dialog and nothing is applied")
    void refused() {
        EditStudentViewModel editor = page.editor(line("Ben Okello"));
        editor.firstNameProperty().set("");
        editor.lastNameProperty().set("Opio");
        editor.residencyProperty().set(Residency.REFUGEE);

        assertThat(editor.save()).isFalse();

        assertThat(editor.error()).isEqualTo("First name can't be empty.");
        assertThat(stored().lastName()).isEqualTo("Okello");
        assertThat(stored().residency()).isEqualTo(Residency.NATIONAL);
    }

    @Test
    @DisplayName("AC-12: Cancel (closing without Save) changes nothing")
    void cancel() {
        EditStudentViewModel editor = page.editor(line("Ben Okello"));
        editor.lastNameProperty().set("Opio");
        editor.statusProperty().set(StudentStatus.LEFT);

        page.show(t(2026, 2));

        assertThat(stored().lastName()).isEqualTo("Okello");
        assertThat(stored().status()).isEqualTo(StudentStatus.ACTIVE);
        assertThat(page.section(SchoolClass.P5).lines()).extracting(StudentTermLine::shownName)
                .containsExactly("Ben Okello");
    }

    @Test
    @DisplayName("AC-8/AC-12: marking Left in the dialog tags the student on this term")
    void markLeft() {
        EditStudentViewModel editor = page.editor(line("Ben Okello"));
        editor.statusProperty().set(StudentStatus.LEFT);

        assertThat(editor.save()).as(editor.error()).isTrue();

        assertThat(page.section(SchoolClass.P5).lines()).extracting(StudentTermLine::shownName)
                .containsExactly("Ben Okello (Left)");
        assertThat(stored().lastTerm()).isEqualTo(t(2026, 2));
    }

    @Test
    @DisplayName("AC-7/AC-12: a Joined term after the term with his payments is refused in the dialog")
    void joinedRefused() {
        finance.studentAccounts.setPayment(ben.id(), t(2026, 2), 1, Ugx.of(10_000));
        EditStudentViewModel editor = page.editor(line("Ben Okello"));
        TermRef term3 = t(2026, 3);
        editor.joinedProperty().set(term3);

        assertThat(editor.save()).isFalse();

        assertThat(editor.error()).contains("2026-2027 Term 2");
        assertThat(stored().joinedTerm()).isEqualTo(t(2026, 2));
    }
}

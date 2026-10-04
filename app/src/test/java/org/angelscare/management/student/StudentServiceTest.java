package org.angelscare.management.student;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.angelscare.management.common.ValidationException;
import org.angelscare.management.student.model.Level;
import org.angelscare.management.student.model.Residency;
import org.angelscare.management.student.model.SchoolClass;
import org.angelscare.management.student.model.Student;
import org.angelscare.management.student.model.StudentDetails;
import org.angelscare.management.student.model.StudentEdit;
import org.angelscare.management.student.model.StudentFilter;
import org.angelscare.management.student.model.StudentStatus;
import org.angelscare.management.support.FinanceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StudentServiceTest extends FinanceTest {

    @Test
    @DisplayName("AC-10: a student's level follows their class, and a new student is ACTIVE")
    void levelAndDefaultStatus() {
        Student p3 = finance.students.create(
                StudentDetails.of("Amina", "Nakato", SchoolClass.P3, Residency.REFUGEE));
        Student top = createStudent("Brian", "Okello", SchoolClass.TOP);

        assertThat(p3.level()).isEqualTo(Level.PRIMARY);
        assertThat(top.level()).isEqualTo(Level.NURSERY);
        assertThat(p3.status()).isEqualTo(StudentStatus.ACTIVE);
        assertThat(finance.students.find(p3.id())).contains(p3);
        assertThat(p3.residency()).isEqualTo(Residency.REFUGEE);
    }

    @Test
    @DisplayName("AC-10: moving a student to another class changes their level")
    void changingClassChangesLevel() {
        Student student = createStudent("Amina", "Nakato", SchoolClass.TOP);

        Student moved = finance.students.update(student.id(),
                StudentDetails.of("Amina", "Nakato", SchoolClass.P1, Residency.NATIONAL));

        assertThat(moved.level()).isEqualTo(Level.PRIMARY);
        assertThat(finance.students.find(student.id()).orElseThrow().schoolClass())
                .isEqualTo(SchoolClass.P1);
    }

    @Test
    @DisplayName("AC-10: a student needs a class and a residency")
    void classAndResidencyAreRequired() {
        assertThatThrownBy(() -> finance.students.create(
                StudentDetails.of("Amina", "Nakato", null, Residency.NATIONAL)))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> finance.students.create(
                StudentDetails.of("Amina", "Nakato", SchoolClass.P1, null)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("AC-11: an admission number already in use is rejected, ignoring case and spaces")
    void rejectsDuplicateAdmissionNumber() {
        finance.students.create(details("Amina", "Nakato").withAdmissionNo("A-12"));

        assertThatThrownBy(() -> finance.students.create(
                details("Brian", "Okello").withAdmissionNo(" a-12 ")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("A-12");
    }

    @Test
    @DisplayName("AC-11: admission numbers are optional; blank means none")
    void admissionNumberIsOptional() {
        Student first = finance.students.create(details("Amina", "Nakato"));
        Student second = finance.students.create(details("Brian", "Okello"));
        Student third = finance.students.create(details("Grace", "Achan").withAdmissionNo("   "));

        assertThat(first.admissionNo()).isNull();
        assertThat(second.admissionNo()).isNull();
        assertThat(third.admissionNo()).isNull();
    }

    @Test
    @DisplayName("AC-11: admission numbers are stored trimmed; a student may keep their own on edit")
    void admissionNumberOnEdit() {
        Student student = finance.students.create(
                details("Amina", "Nakato").withAdmissionNo("  A-12 "));

        Student edited = finance.students.update(student.id(),
                details("Amina", "Nakato-Okello").withAdmissionNo("A-12"));

        assertThat(student.admissionNo()).isEqualTo("A-12");
        assertThat(edited.lastName()).isEqualTo("Nakato-Okello");
    }

    @Test
    @DisplayName("AC-11: a deleted student's admission number can be given to someone else")
    void reusesDeletedAdmissionNumber() {
        Student student = finance.students.create(details("Amina", "Nakato").withAdmissionNo("A-12"));
        finance.students.delete(student.id());

        assertThat(finance.students.create(details("Brian", "Okello").withAdmissionNo("A-12"))
                .admissionNo()).isEqualTo("A-12");
    }

    @Test
    @DisplayName("AC-12: with no students, the list is empty")
    void emptyList() {
        assertThat(finance.students.list(StudentFilter.ALL)).isEmpty();
        assertThat(finance.students.list(StudentFilter.ALL.inClass(SchoolClass.P1))).isEmpty();
    }

    @Test
    @DisplayName("AC-12: students are sorted by last then first name, and filtered by class and status")
    void listsSortedAndFiltered() {
        Student brianOkello = createStudent("Brian", "Okello", SchoolClass.P3);
        Student aminaOkello = createStudent("Amina", "Okello", SchoolClass.P3);
        Student zedAchan = createStudent("Zed", "Achan", SchoolClass.P4);
        createYear(2026);
        finance.studentAccounts.editStudent(zedAchan.id(), t(2026, 1), new StudentEdit("Zed",
                "Achan", Residency.NATIONAL, t(2026, 1), StudentStatus.LEFT));

        assertThat(finance.students.list(StudentFilter.ALL)).extracting(Student::id)
                .containsExactly(zedAchan.id(), aminaOkello.id(), brianOkello.id());
        assertThat(finance.students.list(StudentFilter.ALL.inClass(SchoolClass.P3)))
                .extracting(Student::id).containsExactly(aminaOkello.id(), brianOkello.id());
        assertThat(finance.students.list(StudentFilter.ALL.withStatus(StudentStatus.LEFT)))
                .extracting(Student::id).containsExactly(zedAchan.id());
        assertThat(finance.students.list(
                StudentFilter.ALL.inClass(SchoolClass.P3).withStatus(StudentStatus.LEFT)))
                .isEmpty();
    }

    @Test
    @DisplayName("AC-12: sorting ignores case")
    void sortingIgnoresCase() {
        Student lower = createStudent("amina", "achan", SchoolClass.P1);
        Student upper = createStudent("Brian", "Bosco", SchoolClass.P1);

        assertThat(finance.students.list(StudentFilter.ALL)).extracting(Student::id)
                .containsExactly(lower.id(), upper.id());
    }

    private static StudentDetails details(String firstName, String lastName) {
        return StudentDetails.of(firstName, lastName, SchoolClass.P2, Residency.NATIONAL);
    }
}

package org.angelscare.management.student.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.angelscare.management.common.Ugx;
import org.angelscare.management.common.ui.UgxField;
import org.angelscare.management.student.model.Boarding;
import org.angelscare.management.student.model.Residency;
import org.angelscare.management.student.model.SchoolClass;
import org.angelscare.management.student.model.StudentTermLine;
import org.angelscare.management.support.ScreenTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Spec 004 on page 5: Add student with an amount, and an Amount that can't be cleared. */
class StudentAmountsViewModelTest extends ScreenTest {

    private static final String AMOUNT_REQUIRED = "Type the amount the student pays (0 for none).";

    private StudentsViewModel page;

    @BeforeEach
    void term() {
        createYear(2026);
        page = studentsPage();
        page.show(t(2026, 1));
    }

    private StudentTermLine amina() {
        return page.section(SchoolClass.P7).lines().stream()
                .filter(l -> l.name().equals("Amina Nakato")).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("AC-4: Add student with an amount shows it on the line, the class totals row and the page totals")
    void addWithAmount() {
        assertThat(page.addStudent(SchoolClass.P7, "Amina", "Nakato", Residency.NATIONAL,
                Boarding.BOARDING, "250,000")).as(page.error()).isTrue();

        assertThat(amina().amount()).isEqualTo(Ugx.of(250_000));
        assertThat(amina().boarding()).isEqualTo(Boarding.BOARDING);
        assertThat(page.section(SchoolClass.P7).totalsProperty().get().amount())
                .isEqualTo(Ugx.of(250_000));
        // Ream never set for P7: 5,000.
        assertThat(page.pageTotalsProperty().get().total()).isEqualTo(Ugx.of(255_000));
        assertThat(page.section(SchoolClass.P7).feeProperty().get().ream())
                .isEqualTo(Ugx.of(5_000));
    }

    @Test
    @DisplayName("AC-4: Add student with Amount 0 adds them with 0")
    void addWithZero() {
        assertThat(page.addStudent(SchoolClass.P7, "Amina", "Nakato", Residency.NATIONAL,
                Boarding.DAY, "0")).as(page.error()).isTrue();

        assertThat(amina().amount()).isEqualTo(Ugx.ZERO);
    }

    @Test
    @DisplayName("AC-5: Add student with the amount blank is refused with its message; no student is added")
    void addWithBlankAmount() {
        assertThat(page.addStudent(SchoolClass.P7, "Amina", "Nakato", Residency.NATIONAL,
                Boarding.DAY, " ")).isFalse();

        assertThat(page.error()).isEqualTo(AMOUNT_REQUIRED);
        assertThat(page.section(SchoolClass.P7).lines()).isEmpty();
        assertThat(liveRows("student")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-5", "12.5", "abc"})
    @DisplayName("AC-5: Add student with an unreadable amount is refused; no student is added")
    void addWithInvalidAmount(String amount) {
        assertThat(page.addStudent(SchoolClass.P7, "Amina", "Nakato", Residency.NATIONAL,
                Boarding.DAY, amount)).isFalse();

        assertThat(page.error()).contains(UgxField.INVALID_MESSAGE);
        assertThat(page.section(SchoolClass.P7).lines()).isEmpty();
        assertThat(liveRows("student")).isZero();
    }

    @Test
    @DisplayName("AC-6: clearing the Amount cell is refused with its message, and the amount stays")
    void clearingAmountIsRefused() {
        page.addStudent(SchoolClass.P7, "Amina", "Nakato", Residency.NATIONAL, Boarding.DAY,
                "250,000");

        assertThat(page.editAmount(amina(), "")).isFalse();

        assertThat(page.error()).contains(AMOUNT_REQUIRED);
        assertThat(amina().amount()).isEqualTo(Ugx.of(250_000));
    }
}

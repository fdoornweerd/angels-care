package org.angelscare.management.shell.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.angelscare.management.accounts.model.Ledger;
import org.angelscare.management.shell.ui.Navigator.Page;
import org.angelscare.management.support.MutableClock;
import org.angelscare.management.support.ScreenTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NavigatorTest extends ScreenTest {

    @Test
    @DisplayName("AC-24: pages 1 → 2 → 3 → 5, with the breadcrumb, and Back step by step")
    void pagesAndBreadcrumb() {
        createYear(2026);
        Navigator navigator = navigator();

        navigator.start();
        assertThat(navigator.current()).isEqualTo(new Page.SchoolYears());
        assertThat(navigator.breadcrumb()).containsExactly("School years");
        assertThat(navigator.term()).isNull();

        navigator.openYear(2026);
        assertThat(navigator.current()).isEqualTo(new Page.TermSummary(2026));
        assertThat(navigator.breadcrumb()).containsExactly("School years", "2026-2027", "Term 1");

        navigator.openDetail(Ledger.INCOME, null);
        assertThat(navigator.current()).isEqualTo(new Page.Detail(Ledger.INCOME, 2026, null));

        navigator.openStudents();
        assertThat(navigator.current()).isEqualTo(new Page.Students(2026));
        assertThat(navigator.breadcrumb()).containsExactly(
                "School years", "2026-2027", "Term 1", "Detailed incomes", "Students");

        assertThat(navigator.back()).isTrue();
        assertThat(navigator.current()).isEqualTo(new Page.Detail(Ledger.INCOME, 2026, null));
        assertThat(navigator.back()).isTrue();
        assertThat(navigator.current()).isEqualTo(new Page.TermSummary(2026));
        assertThat(navigator.back()).isTrue();
        assertThat(navigator.current()).isEqualTo(new Page.SchoolYears());
        assertThat(navigator.back()).isFalse();
        assertThat(navigator.current()).isEqualTo(new Page.SchoolYears());
    }

    @Test
    @DisplayName("AC-24: the chosen term is kept when going back to page 2")
    void termIsKept() {
        createYear(2026);
        Navigator navigator = navigator();
        navigator.start();
        navigator.openYear(2026);

        navigator.selectTerm(2);
        navigator.openDetail(Ledger.EXPENSE, "some-category");
        assertThat(navigator.breadcrumb()).containsExactly(
                "School years", "2026-2027", "Term 2", "Detailed expenses");
        assertThat(navigator.current())
                .isEqualTo(new Page.Detail(Ledger.EXPENSE, 2026, "some-category"));
        navigator.back();

        assertThat(navigator.current()).isEqualTo(new Page.TermSummary(2026));
        assertThat(navigator.term()).isEqualTo(t(2026, 2));
    }

    @Test
    @DisplayName("AC-11: a year opens on the term containing today (Kampala), else Term 1")
    void opensOnTodaysTerm() {
        createSpanningYear(2024);
        createSpanningYear(2025); // Term 2 is 11/01/2026 - 02/04/2026: contains 2 March 2026
        createSpanningYear(2026);
        Navigator navigator = navigator();
        navigator.start();

        navigator.openYear(2025);
        assertThat(navigator.term()).isEqualTo(t(2025, 2));

        navigator.back();
        navigator.openYear(2026);
        assertThat(navigator.term()).isEqualTo(t(2026, 1));

        navigator.back();
        navigator.openYear(2024);
        assertThat(navigator.term()).isEqualTo(t(2024, 1));
    }

    @Test
    @DisplayName("AC-11: between two terms of the year, it opens on the next one")
    void betweenTerms() {
        createSpanningYear(2025);
        // 10 April 2026, between Term 2 (ends 02/04) and Term 3 (starts 26/04).
        clock = new MutableClock(Instant.parse("2026-04-10T08:00:00Z"));
        Navigator navigator = navigator();
        navigator.start();

        navigator.openYear(2025);

        assertThat(navigator.term()).isEqualTo(t(2025, 3));
    }
}

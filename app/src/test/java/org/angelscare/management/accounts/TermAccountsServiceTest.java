package org.angelscare.management.accounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import org.angelscare.management.accounts.model.SummaryRow;
import org.angelscare.management.accounts.model.TermSummary;
import org.angelscare.management.accounts.model.YearTotals;
import org.angelscare.management.common.MonthlyAmounts;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.student.model.SchoolClass;
import org.angelscare.management.student.model.StudentStatus;
import org.angelscare.management.support.FinanceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Spec 002: the figures of pages 1 and 2. */
class TermAccountsServiceTest extends FinanceTest {

    private static MonthlyAmounts months(long first, long second, long third) {
        return new MonthlyAmounts(Ugx.of(first), Ugx.of(second), Ugx.of(third));
    }

    @Test
    @DisplayName("AC-8: Students first, then income categories by name; expense rows; totals rows")
    void rowsAndTotals() {
        createYear(2026);
        var uniform = incomeItem(2026, "Uniform sales", "Shirts", "shirts");
        var church = incomeItem(2026, "Donations", "Church support", "months");
        var maize = expenseItem(2026, "Feeding", "Maize flour", "kg");
        record(finance.incomeSheet, uniform.id(), t(2026, 1), 1, "10", 15_000);
        record(finance.incomeSheet, church.id(), t(2026, 1), 2, "1", 200_000);
        record(finance.incomeSheet, church.id(), t(2026, 1), 3, "1", 200_000);
        record(finance.expenseSheet, maize.id(), t(2026, 1), 2, "12.5", 3_500);
        finance.incomeSheet.setPlan(incomeCategory(2026, "Donations").id(), t(2026, 1),
                Ugx.of(600_000));
        finance.expenseSheet.setPlan(expenseCategory(2026, "Feeding").id(), t(2026, 1),
                Ugx.of(100_000));

        TermSummary summary = finance.accounts.summary(t(2026, 1));

        assertThat(summary.income())
                .extracting(SummaryRow::name, SummaryRow::planned, SummaryRow::actual)
                .containsExactly(
                        tuple("Students", Ugx.ZERO, MonthlyAmounts.ZERO),
                        tuple("Donations", Ugx.of(600_000), months(0, 200_000, 200_000)),
                        tuple("Uniform sales", null, months(150_000, 0, 0)));
        assertThat(summary.income().get(0).isStudents()).isTrue();
        assertThat(summary.expense())
                .extracting(SummaryRow::name, SummaryRow::planned, SummaryRow::actual)
                .containsExactly(tuple("Feeding", Ugx.of(100_000), months(0, 43_750, 0)));
        assertThat(summary.incomeTotals().planned()).isEqualTo(Ugx.of(600_000));
        assertThat(summary.incomeTotals().actual()).isEqualTo(months(150_000, 200_000, 200_000));
        assertThat(summary.incomeTotals().total()).isEqualTo(Ugx.of(550_000));
        assertThat(summary.expenseTotals().total()).isEqualTo(Ugx.of(43_750));
    }

    @Test
    @DisplayName("AC-8: only the chosen term's amounts are counted")
    void onlyThisTerm() {
        createYear(2026);
        var maize = expenseItem(2026, "Feeding", "Maize flour", "kg");
        record(finance.expenseSheet, maize.id(), t(2026, 2), 1, "1", 5_000);

        assertThat(finance.accounts.summary(t(2026, 1)).expenditures()).isEqualTo(Ugx.ZERO);
        assertThat(finance.accounts.summary(t(2026, 2)).expenditures()).isEqualTo(Ugx.of(5_000));
    }

    @Test
    @DisplayName("AC-9: incomes collected, expenditures and a deficit")
    void summaryWithDeficit() {
        createYear(2026);
        record(finance.incomeSheet, incomeItem(2026, "Donations", "Church", "gifts").id(),
                t(2026, 1), 1, "1", 1_000_000);
        record(finance.expenseSheet, expenseItem(2026, "Salaries", "Teachers", "teachers").id(),
                t(2026, 1), 1, "5", 250_000);

        TermSummary summary = finance.accounts.summary(t(2026, 1));

        assertThat(summary.incomesCollected()).isEqualTo(Ugx.of(1_000_000));
        assertThat(summary.expenditures()).isEqualTo(Ugx.of(1_250_000));
        assertThat(summary.surplus()).isEqualTo(Ugx.of(-250_000));
    }

    @Test
    @DisplayName("AC-9: equal incomes and expenditures give 0")
    void summaryEven() {
        createYear(2026);
        record(finance.incomeSheet, incomeItem(2026, "Donations", "Church", "gifts").id(),
                t(2026, 1), 1, "1", 700_000);
        record(finance.expenseSheet, expenseItem(2026, "Salaries", "Teachers", "teachers").id(),
                t(2026, 1), 3, "1", 700_000);

        assertThat(finance.accounts.summary(t(2026, 1)).surplus()).isEqualTo(Ugx.ZERO);
    }

    @Test
    @DisplayName("AC-10: a new year with nothing in it has only the Students row, at zero")
    void emptyYear() {
        createYear(2026);

        TermSummary summary = finance.accounts.summary(t(2026, 1));

        assertThat(summary.income()).singleElement().satisfies(row -> {
            assertThat(row.isStudents()).isTrue();
            assertThat(row.planned()).isEqualTo(Ugx.ZERO);
            assertThat(row.actual()).isEqualTo(MonthlyAmounts.ZERO);
        });
        assertThat(summary.expense()).isEmpty();
        assertThat(summary.incomesCollected()).isEqualTo(Ugx.ZERO);
        assertThat(summary.expenditures()).isEqualTo(Ugx.ZERO);
        assertThat(summary.surplus()).isEqualTo(Ugx.ZERO);
        assertThat(summary.expenseTotals().planned()).isEqualTo(Ugx.ZERO);
    }

    @Test
    @DisplayName("AC-21: the Students row is Σ Totals expected and Σ payments per month, Left included")
    void studentsRow() {
        createYear(2026);
        var amina = createStudent("Amina", "Nakato", SchoolClass.P7);
        var brian = createStudent("Brian", "Okello", SchoolClass.P7);
        finance.studentAccounts.setClassFee(SchoolClass.P7, t(2026, 1), Ugx.of(300_000),
                Ugx.of(10_000));
        finance.studentAccounts.openTerm(t(2026, 1));
        finance.studentAccounts.setPayment(amina.id(), t(2026, 1), 1, Ugx.of(100_000));
        finance.studentAccounts.setPayment(amina.id(), t(2026, 1), 2, Ugx.of(50_000));
        finance.studentAccounts.setPayment(brian.id(), t(2026, 1), 2, Ugx.of(310_000));
        finance.students.setStatus(brian.id(), StudentStatus.LEFT);

        SummaryRow students = finance.accounts.summary(t(2026, 1)).income().get(0);

        assertThat(students.planned()).isEqualTo(Ugx.of(620_000));
        assertThat(students.actual()).isEqualTo(months(100_000, 360_000, 0));
        assertThat(finance.accounts.summary(t(2026, 1)).incomesCollected())
                .isEqualTo(Ugx.of(460_000));
        assertThat(finance.studentAccounts.totals(t(2026, 1)).total()).isEqualTo(Ugx.of(460_000));
    }

    @Test
    @DisplayName("AC-23: a year's totals are the sums of its three terms")
    void yearTotals() {
        createYear(2025);
        createYear(2026);
        var church = incomeItem(2026, "Donations", "Church", "gifts");
        var maize = expenseItem(2026, "Feeding", "Maize flour", "kg");
        record(finance.incomeSheet, church.id(), t(2026, 1), 1, "1", 400_000);
        record(finance.incomeSheet, church.id(), t(2026, 3), 2, "1", 100_000);
        record(finance.expenseSheet, maize.id(), t(2026, 2), 3, "100", 3_000);
        var amina = createStudent("Amina", "Nakato", SchoolClass.P4);
        finance.studentAccounts.openTerm(t(2026, 2));
        finance.studentAccounts.setPayment(amina.id(), t(2026, 2), 1, Ugx.of(50_000));

        YearTotals totals = finance.accounts.yearTotals(2026);

        assertThat(totals.incomeCollected()).isEqualTo(Ugx.of(550_000));
        assertThat(totals.expenditure()).isEqualTo(Ugx.of(300_000));
        assertThat(totals.surplus()).isEqualTo(Ugx.of(250_000));
        assertThat(finance.accounts.yearTotals(2025)).isEqualTo(new YearTotals(Ugx.ZERO, Ugx.ZERO));
    }
}

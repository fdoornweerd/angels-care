package org.angelscare.management.accounts.model;

import java.util.List;
import org.angelscare.management.common.MonthlyAmounts;
import org.angelscare.management.common.Ugx;

/** Page 2 for one term: the income rows (Students first), the expense rows, and the summary. */
public record TermSummary(List<SummaryRow> income, List<SummaryRow> expense) {

    /** A "Total" row: every column of the income table summed (a missing plan counts as 0). */
    public SummaryRow incomeTotals() {
        return totals(income);
    }

    public SummaryRow expenseTotals() {
        return totals(expense);
    }

    public Ugx incomesCollected() {
        return incomeTotals().total();
    }

    public Ugx expenditures() {
        return expenseTotals().total();
    }

    /** Incomes minus expenditures; negative is a deficit. */
    public Ugx surplus() {
        return incomesCollected().minus(expenditures());
    }

    private static SummaryRow totals(List<SummaryRow> rows) {
        Ugx planned = Ugx.ZERO;
        MonthlyAmounts actual = MonthlyAmounts.ZERO;
        for (SummaryRow row : rows) {
            planned = planned.plus(row.planned() == null ? Ugx.ZERO : row.planned());
            actual = actual.plus(row.actual());
        }
        return new SummaryRow(null, SummaryRow.TOTAL, planned, actual);
    }
}

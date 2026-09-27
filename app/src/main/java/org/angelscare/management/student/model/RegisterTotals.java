package org.angelscare.management.student.model;

import java.util.List;
import org.angelscare.management.common.MonthlyAmounts;
import org.angelscare.management.common.Ugx;

/** Column totals for a class table or the whole of page 5. */
public record RegisterTotals(Ugx amount, Ugx debt, Ugx ream, Ugx total, MonthlyAmounts paid,
        Ugx balance) {

    public static RegisterTotals of(List<StudentTermLine> lines) {
        Ugx amount = Ugx.ZERO;
        Ugx debt = Ugx.ZERO;
        Ugx ream = Ugx.ZERO;
        MonthlyAmounts paid = MonthlyAmounts.ZERO;
        for (StudentTermLine line : lines) {
            amount = amount.plus(line.amount());
            debt = debt.plus(line.debt());
            ream = ream.plus(line.ream());
            paid = paid.plus(line.payments());
        }
        Ugx total = amount.plus(debt).plus(ream);
        return new RegisterTotals(amount, debt, ream, total, paid, total.minus(paid.total()));
    }
}

package org.angelscare.management.report.model;

import java.util.List;
import org.angelscare.management.common.MonthlyAmounts;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.student.model.SchoolClass;
import org.angelscare.management.student.model.StudentTermLine;

/** One class's table on page 5 for a term: its ream charge and its students, Left included. */
public record ClassSheet(SchoolClass schoolClass, Ugx ream, List<StudentTermLine> lines) {

    /** The class's totals row: each money column summed. */
    public Totals totals() {
        return Totals.of(lines);
    }

    /** The money columns of page 5 summed over some students. */
    public record Totals(Ugx amount, Ugx debt, Ugx ream, Ugx total, MonthlyAmounts paid,
            Ugx balance) {

        public static Totals of(List<StudentTermLine> lines) {
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
            return new Totals(amount, debt, ream, total, paid, total.minus(paid.total()));
        }
    }
}

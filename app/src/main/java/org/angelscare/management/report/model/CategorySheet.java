package org.angelscare.management.report.model;

import java.util.List;
import java.util.Optional;
import org.angelscare.management.accounts.model.ItemMonth;
import org.angelscare.management.common.MonthlyAmounts;
import org.angelscare.management.common.Ugx;

/**
 * One category's table on pages 3 or 4, for a whole term: its items with their three months, the
 * category's total per month, and its expected or budgeted amount ({@code planned}, null when not
 * set).
 */
public record CategorySheet(String name, Ugx planned, MonthlyAmounts totals, List<ItemRow> items) {

    /** An item with its entries for months 1, 2 and 3; blank quantities and rates are null. */
    public record ItemRow(String name, String unit, List<ItemMonth> months) {

        /** The sum of the months that have an amount. */
        public Ugx termTotal() {
            return months.stream().map(ItemMonth::amount).flatMap(Optional::stream)
                    .reduce(Ugx.ZERO, Ugx::plus);
        }
    }
}

package org.angelscare.management.support;

import java.util.List;

/** The tables added by spec 001; all of them follow the sync-column rules. */
public final class FinanceTables {

    public static final List<String> ALL = List.of(
            "school_year", "term", "student", "student_group", "group_membership",
            "income_category", "income_item", "fee_assignment",
            "expense_category", "expense_item", "expense_budget");

    private FinanceTables() {
    }
}

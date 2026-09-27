package org.angelscare.management.support;

import java.util.List;
import java.util.stream.Stream;

/** The finance tables; all of them follow the sync-column rules. */
public final class FinanceTables {

    /** Added by V2 (spec 001). */
    public static final List<String> V2 = List.of(
            "school_year", "term", "student", "student_group", "group_membership",
            "income_category", "income_item", "fee_assignment",
            "expense_category", "expense_item", "expense_budget");

    /** Added by V3 (spec 002). */
    public static final List<String> V3 = List.of(
            "income_entry", "expense_entry", "income_plan", "expense_plan", "class_fee",
            "student_term");

    /** Left in the schema when spec 002 removed groups, fee assignments and 001's budgets. */
    public static final List<String> UNUSED = List.of(
            "student_group", "group_membership", "fee_assignment", "expense_budget");

    public static final List<String> ALL = Stream.concat(V2.stream(), V3.stream()).toList();

    /** The tables the app still writes to. */
    public static final List<String> IN_USE =
            ALL.stream().filter(table -> !UNUSED.contains(table)).toList();

    private FinanceTables() {
    }
}

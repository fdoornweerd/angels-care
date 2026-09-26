package org.angelscare.management.expense.model;

import org.angelscare.management.common.Ugx;

/** One row of a year's budget list. */
public record BudgetLine(String budgetId, ExpenseCategory category, ExpenseItem item,
        Ugx monthlyAmount) {
}

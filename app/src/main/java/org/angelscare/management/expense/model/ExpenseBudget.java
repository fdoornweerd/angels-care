package org.angelscare.management.expense.model;

import org.angelscare.management.common.Ugx;

/** The planned spending on one item per month, for one school year. */
public record ExpenseBudget(String id, String expenseItemId, int year, Ugx monthlyAmount) {
}

package org.angelscare.management.expense.model;

/** A heading for spending in one school year, e.g. "Feeding". */
public record ExpenseCategory(String id, String schoolYearId, String name) {
}

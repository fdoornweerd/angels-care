package org.angelscare.management.expense.model;

/** One kind of spending within a category, e.g. "Airtime bundles". */
public record ExpenseItem(String id, String categoryId, String name) {
}

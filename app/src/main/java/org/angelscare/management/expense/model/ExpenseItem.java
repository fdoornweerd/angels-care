package org.angelscare.management.expense.model;

/** One kind of spending within a category, e.g. "Maize flour", counted in {@code unit} ("kg"). */
public record ExpenseItem(String id, String categoryId, String name, String unit) {
}

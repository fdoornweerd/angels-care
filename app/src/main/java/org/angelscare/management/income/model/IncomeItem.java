package org.angelscare.management.income.model;

/** One kind of income within a category, e.g. "Church support", counted in {@code unit}. */
public record IncomeItem(String id, String categoryId, String name, String unit) {
}

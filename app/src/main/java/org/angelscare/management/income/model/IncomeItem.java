package org.angelscare.management.income.model;

/** One kind of income within a category, e.g. "Tuition". Items can be charged to students. */
public record IncomeItem(String id, String categoryId, String name) {
}

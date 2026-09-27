package org.angelscare.management.income.model;

/** A heading for income in one school year, e.g. "Donations". */
public record IncomeCategory(String id, String schoolYearId, String name) {
}

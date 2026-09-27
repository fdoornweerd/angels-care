package org.angelscare.management.accounts.model;

/**
 * The two sides of the accounts. Income and expenses are kept in separate tables with the same
 * shape, so one implementation serves both, told apart by the table names here.
 */
public enum Ledger {
    INCOME("income_category", "income_item", "income_entry", "income_plan"),
    EXPENSE("expense_category", "expense_item", "expense_entry", "expense_plan");

    private final String categoryTable;
    private final String itemTable;
    private final String entryTable;
    private final String planTable;

    Ledger(String categoryTable, String itemTable, String entryTable, String planTable) {
        this.categoryTable = categoryTable;
        this.itemTable = itemTable;
        this.entryTable = entryTable;
        this.planTable = planTable;
    }

    public String categoryTable() {
        return categoryTable;
    }

    public String itemTable() {
        return itemTable;
    }

    public String entryTable() {
        return entryTable;
    }

    public String planTable() {
        return planTable;
    }
}

package org.angelscare.management.expense.repository;

import static org.angelscare.management.common.SyncedTable.columns;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.angelscare.management.common.SyncedTable;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.expense.model.BudgetLine;
import org.angelscare.management.expense.model.ExpenseBudget;
import org.angelscare.management.expense.model.ExpenseCategory;
import org.angelscare.management.expense.model.ExpenseItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ExpenseBudgetRepository {

    private static final String SELECT = "SELECT b.id, b.expense_item_id, y.year, b.monthly_amount"
            + " FROM expense_budget b JOIN school_year y ON y.id = b.school_year_id"
            + " WHERE b.deleted_at IS NULL";
    private static final RowMapper<ExpenseBudget> MAPPER = (row, n) -> new ExpenseBudget(
            row.getString("id"), row.getString("expense_item_id"), row.getInt("year"),
            Ugx.of(row.getLong("monthly_amount")));

    private final JdbcTemplate jdbc;
    private final SyncedTable table;

    public ExpenseBudgetRepository(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.table = new SyncedTable(jdbc, clock, "expense_budget");
    }

    public ExpenseBudget insert(String expenseItemId, String schoolYearId, Ugx monthlyAmount) {
        return findById(table.insert(columns("expense_item_id", expenseItemId,
                "school_year_id", schoolYearId, "monthly_amount", monthlyAmount.shillings())))
                .orElseThrow();
    }

    public void updateAmount(String id, Ugx monthlyAmount) {
        table.update(id, columns("monthly_amount", monthlyAmount.shillings()));
    }

    public void softDelete(String id) {
        table.softDelete(id);
    }

    public Optional<ExpenseBudget> findById(String id) {
        return jdbc.query(SELECT + " AND b.id = ?", MAPPER, id).stream().findFirst();
    }

    public Optional<ExpenseBudget> find(String expenseItemId, String schoolYearId) {
        return jdbc.query(SELECT + " AND b.expense_item_id = ? AND b.school_year_id = ?",
                MAPPER, expenseItemId, schoolYearId).stream().findFirst();
    }

    /** Sorted by category name, then item name. */
    public List<BudgetLine> linesFor(String schoolYearId) {
        return jdbc.query("SELECT b.id, b.monthly_amount, c.id AS category_id, c.name AS category_name,"
                        + " i.id AS item_id, i.name AS item_name"
                        + " FROM expense_budget b"
                        + " JOIN expense_item i ON i.id = b.expense_item_id"
                        + " JOIN expense_category c ON c.id = i.category_id"
                        + " WHERE b.deleted_at IS NULL AND i.deleted_at IS NULL"
                        + " AND c.deleted_at IS NULL AND b.school_year_id = ?"
                        + " ORDER BY lower(c.name), lower(i.name)",
                (row, n) -> new BudgetLine(
                        row.getString("id"),
                        new ExpenseCategory(row.getString("category_id"), row.getString("category_name")),
                        new ExpenseItem(row.getString("item_id"), row.getString("category_id"),
                                row.getString("item_name")),
                        Ugx.of(row.getLong("monthly_amount"))),
                schoolYearId);
    }
}

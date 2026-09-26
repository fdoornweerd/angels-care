package org.angelscare.management.expense.repository;

import static org.angelscare.management.common.SyncedTable.LIVE;
import static org.angelscare.management.common.SyncedTable.columns;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.angelscare.management.common.SyncedTable;
import org.angelscare.management.expense.model.ExpenseCategory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ExpenseCategoryRepository {

    private static final String SELECT = "SELECT id, name FROM expense_category WHERE " + LIVE;
    private static final RowMapper<ExpenseCategory> MAPPER =
            (row, n) -> new ExpenseCategory(row.getString("id"), row.getString("name"));

    private final JdbcTemplate jdbc;
    private final SyncedTable table;

    public ExpenseCategoryRepository(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.table = new SyncedTable(jdbc, clock, "expense_category");
    }

    public ExpenseCategory insert(String name) {
        return findById(table.insert(columns("name", name))).orElseThrow();
    }

    public void rename(String id, String name) {
        table.update(id, columns("name", name));
    }

    public void softDelete(String id) {
        table.softDelete(id);
    }

    public Optional<ExpenseCategory> findById(String id) {
        return jdbc.query(SELECT + " AND id = ?", MAPPER, id).stream().findFirst();
    }

    public List<ExpenseCategory> findAll() {
        return jdbc.query(SELECT + " ORDER BY lower(name)", MAPPER);
    }

    /** The stored name of another live category with this name (any case). */
    public Optional<String> nameClash(String name, String excludeId) {
        return table.findClash("name", name, excludeId, null, null);
    }
}

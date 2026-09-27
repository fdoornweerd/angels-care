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

    private static final String SELECT =
            "SELECT id, school_year_id, name FROM expense_category WHERE " + LIVE;
    private static final RowMapper<ExpenseCategory> MAPPER = (row, n) -> new ExpenseCategory(
            row.getString("id"), row.getString("school_year_id"), row.getString("name"));

    private final JdbcTemplate jdbc;
    private final SyncedTable table;

    public ExpenseCategoryRepository(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.table = new SyncedTable(jdbc, clock, "expense_category");
    }

    public ExpenseCategory insert(String schoolYearId, String name) {
        return findById(table.insert(columns("school_year_id", schoolYearId, "name", name)))
                .orElseThrow();
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

    /** A school year's categories, by name. */
    public List<ExpenseCategory> findByYear(String schoolYearId) {
        return jdbc.query(SELECT + " AND school_year_id = ? ORDER BY lower(name)", MAPPER,
                schoolYearId);
    }

    /** The stored name of another live category of the same school year with this name. */
    public Optional<String> nameClash(String schoolYearId, String name, String excludeId) {
        return table.findClash("name", name, excludeId, "school_year_id", schoolYearId);
    }
}

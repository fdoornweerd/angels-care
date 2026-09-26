package org.angelscare.management.income.repository;

import static org.angelscare.management.common.SyncedTable.LIVE;
import static org.angelscare.management.common.SyncedTable.columns;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.angelscare.management.common.SyncedTable;
import org.angelscare.management.income.model.IncomeCategory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class IncomeCategoryRepository {

    private static final String SELECT = "SELECT id, name FROM income_category WHERE " + LIVE;
    private static final RowMapper<IncomeCategory> MAPPER =
            (row, n) -> new IncomeCategory(row.getString("id"), row.getString("name"));

    private final JdbcTemplate jdbc;
    private final SyncedTable table;

    public IncomeCategoryRepository(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.table = new SyncedTable(jdbc, clock, "income_category");
    }

    public IncomeCategory insert(String name) {
        return findById(table.insert(columns("name", name))).orElseThrow();
    }

    public void rename(String id, String name) {
        table.update(id, columns("name", name));
    }

    public void softDelete(String id) {
        table.softDelete(id);
    }

    public Optional<IncomeCategory> findById(String id) {
        return jdbc.query(SELECT + " AND id = ?", MAPPER, id).stream().findFirst();
    }

    public List<IncomeCategory> findAll() {
        return jdbc.query(SELECT + " ORDER BY lower(name)", MAPPER);
    }

    /** The stored name of another live category with this name (any case). */
    public Optional<String> nameClash(String name, String excludeId) {
        return table.findClash("name", name, excludeId, null, null);
    }
}

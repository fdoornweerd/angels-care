package org.angelscare.management.income.repository;

import static org.angelscare.management.common.SyncedTable.LIVE;
import static org.angelscare.management.common.SyncedTable.columns;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.angelscare.management.common.SyncedTable;
import org.angelscare.management.income.model.IncomeItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class IncomeItemRepository {

    private static final String SELECT =
            "SELECT id, category_id, name, unit FROM income_item WHERE " + LIVE;
    private static final RowMapper<IncomeItem> MAPPER = (row, n) -> new IncomeItem(
            row.getString("id"), row.getString("category_id"), row.getString("name"),
            row.getString("unit"));

    private final JdbcTemplate jdbc;
    private final SyncedTable table;

    public IncomeItemRepository(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.table = new SyncedTable(jdbc, clock, "income_item");
    }

    public IncomeItem insert(String categoryId, String name, String unit) {
        return findById(table.insert(columns("category_id", categoryId, "name", name, "unit", unit)))
                .orElseThrow();
    }

    public void update(String id, String name, String unit) {
        table.update(id, columns("name", name, "unit", unit));
    }

    public void softDelete(String id) {
        table.softDelete(id);
    }

    public Optional<IncomeItem> findById(String id) {
        return jdbc.query(SELECT + " AND id = ?", MAPPER, id).stream().findFirst();
    }

    public List<IncomeItem> findByCategory(String categoryId) {
        return jdbc.query(SELECT + " AND category_id = ? ORDER BY lower(name)", MAPPER, categoryId);
    }

    /** The stored name of another live item in the same category with this name (any case). */
    public Optional<String> nameClash(String categoryId, String name, String excludeId) {
        return table.findClash("name", name, excludeId, "category_id", categoryId);
    }
}

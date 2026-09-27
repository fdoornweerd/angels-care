package org.angelscare.management.accounts.repository;

import static org.angelscare.management.common.SyncedTable.LIVE;
import static org.angelscare.management.common.SyncedTable.columns;

import java.time.Clock;
import java.util.Optional;
import org.angelscare.management.accounts.model.Ledger;
import org.angelscare.management.common.SyncedTable;
import org.springframework.jdbc.core.JdbcTemplate;

/** One ledger's expected/budgeted amount per category and term. */
public class PlanRepository {

    /** A stored plan. */
    public record Row(String id, long amount) {
    }

    private final Ledger ledger;
    private final JdbcTemplate jdbc;
    private final SyncedTable table;

    public PlanRepository(Ledger ledger, JdbcTemplate jdbc, Clock clock) {
        this.ledger = ledger;
        this.jdbc = jdbc;
        this.table = new SyncedTable(jdbc, clock, ledger.planTable());
    }

    public Optional<Row> find(String categoryId, String termId) {
        return jdbc.query("SELECT id, amount FROM " + ledger.planTable() + " WHERE " + LIVE
                        + " AND category_id = ? AND term_id = ?",
                (row, n) -> new Row(row.getString("id"), row.getLong("amount")),
                categoryId, termId).stream().findFirst();
    }

    /** The school year of a live category, if it exists. */
    public Optional<String> schoolYearOfCategory(String categoryId) {
        return jdbc.queryForList("SELECT school_year_id FROM " + ledger.categoryTable()
                + " WHERE id = ? AND " + LIVE, String.class, categoryId).stream().findFirst();
    }

    public void insert(String categoryId, String termId, long amount) {
        table.insert(columns("category_id", categoryId, "term_id", termId, "amount", amount));
    }

    public void update(String id, long amount) {
        table.update(id, columns("amount", amount));
    }

    public void softDelete(String id) {
        table.softDelete(id);
    }
}

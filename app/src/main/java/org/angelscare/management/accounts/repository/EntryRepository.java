package org.angelscare.management.accounts.repository;

import static org.angelscare.management.common.SyncedTable.LIVE;
import static org.angelscare.management.common.SyncedTable.columns;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.angelscare.management.accounts.model.Ledger;
import org.angelscare.management.common.SyncedTable;
import org.springframework.jdbc.core.JdbcTemplate;

/** One ledger's monthly entries: quantity (in hundredths) and rate per item, term and month. */
public class EntryRepository {

    /** A stored entry; {@code quantityHundredths} and {@code rate} may be null. */
    public record Row(String id, String itemId, String termId, int month, Long quantityHundredths,
            Long rate) {
    }

    private final Ledger ledger;
    private final JdbcTemplate jdbc;
    private final SyncedTable table;
    private final String select;

    public EntryRepository(Ledger ledger, JdbcTemplate jdbc, Clock clock) {
        this.ledger = ledger;
        this.jdbc = jdbc;
        this.table = new SyncedTable(jdbc, clock, ledger.entryTable());
        this.select = "SELECT e.id, e.item_id, e.term_id, e.month, e.quantity_hundredths, e.rate"
                + " FROM " + ledger.entryTable() + " e WHERE e.deleted_at IS NULL";
    }

    public Optional<Row> find(String itemId, String termId, int month) {
        return jdbc.query(select + " AND e.item_id = ? AND e.term_id = ? AND e.month = ?",
                EntryRepository::map, itemId, termId, month).stream().findFirst();
    }

    /** Every entry of the term and month, for items that still exist. */
    public List<Row> findByTermAndMonth(String termId, int month) {
        return jdbc.query(select + " AND e.term_id = ? AND e.month = ? AND EXISTS (SELECT 1 FROM "
                        + ledger.itemTable() + " i WHERE i.id = e.item_id AND i." + LIVE + ")",
                EntryRepository::map, termId, month);
    }

    /** Every entry of the category's live items in the term. */
    public List<Row> findByCategoryAndTerm(String categoryId, String termId) {
        return jdbc.query(select + " AND e.term_id = ? AND e.item_id IN (SELECT i.id FROM "
                        + ledger.itemTable() + " i WHERE i.category_id = ? AND i." + LIVE + ")",
                EntryRepository::map, termId, categoryId);
    }

    /** The school year of a live item (through its category), if the item exists. */
    public Optional<String> schoolYearOfItem(String itemId) {
        return jdbc.queryForList("SELECT c.school_year_id FROM " + ledger.itemTable() + " i JOIN "
                        + ledger.categoryTable() + " c ON c.id = i.category_id"
                        + " WHERE i.id = ? AND i." + LIVE + " AND c." + LIVE,
                String.class, itemId).stream().findFirst();
    }

    public void insert(String itemId, String termId, int month, Long quantityHundredths,
            Long rate) {
        table.insert(columns("item_id", itemId, "term_id", termId, "month", month,
                "quantity_hundredths", quantityHundredths, "rate", rate));
    }

    public void update(String id, Long quantityHundredths, Long rate) {
        table.update(id, columns("quantity_hundredths", quantityHundredths, "rate", rate));
    }

    public void softDelete(String id) {
        table.softDelete(id);
    }

    private static Row map(ResultSet row, int rowNum) throws SQLException {
        return new Row(row.getString("id"), row.getString("item_id"), row.getString("term_id"),
                row.getInt("month"), nullableLong(row, "quantity_hundredths"),
                nullableLong(row, "rate"));
    }

    static Long nullableLong(ResultSet row, String column) throws SQLException {
        long value = row.getLong(column);
        return row.wasNull() ? null : value;
    }
}

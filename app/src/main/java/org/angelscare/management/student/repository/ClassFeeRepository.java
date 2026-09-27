package org.angelscare.management.student.repository;

import static org.angelscare.management.common.SyncedTable.columns;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.angelscare.management.calendar.model.TermRef;
import org.angelscare.management.common.SyncedTable;
import org.angelscare.management.student.model.SchoolClass;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Class fees: each row applies from its term until a later term has a row for the class. */
@Repository
public class ClassFeeRepository {

    /** A stored class fee and the term it applies from. */
    public record Row(String id, SchoolClass schoolClass, String termId, TermRef term, long amount,
            long ream) {
    }

    private static final String SELECT = "SELECT f.id, f.school_class, f.term_id, y.year,"
            + " t.number, f.amount, f.ream FROM class_fee f"
            + " JOIN term t ON t.id = f.term_id JOIN school_year y ON y.id = t.school_year_id"
            + " WHERE f.deleted_at IS NULL AND t.deleted_at IS NULL AND y.deleted_at IS NULL";

    private final JdbcTemplate jdbc;
    private final SyncedTable table;

    public ClassFeeRepository(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.table = new SyncedTable(jdbc, clock, "class_fee");
    }

    /** Every class fee ever set; a small table, read whole. */
    public List<Row> findAll() {
        return jdbc.query(SELECT, (row, n) -> new Row(row.getString("id"),
                SchoolClass.valueOf(row.getString("school_class")), row.getString("term_id"),
                TermRef.of(row.getInt("year"), row.getInt("number")), row.getLong("amount"),
                row.getLong("ream")));
    }

    public Optional<Row> find(SchoolClass schoolClass, String termId) {
        return findAll().stream()
                .filter(row -> row.schoolClass() == schoolClass && row.termId().equals(termId))
                .findFirst();
    }

    public void insert(SchoolClass schoolClass, String termId, long amount, long ream) {
        table.insert(columns("school_class", schoolClass.name(), "term_id", termId,
                "amount", amount, "ream", ream));
    }

    public void update(String id, long amount, long ream) {
        table.update(id, columns("amount", amount, "ream", ream));
    }
}

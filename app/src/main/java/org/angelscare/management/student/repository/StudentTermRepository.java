package org.angelscare.management.student.repository;

import static org.angelscare.management.common.SyncedTable.columns;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.angelscare.management.calendar.model.TermRef;
import org.angelscare.management.common.SyncedTable;
import org.angelscare.management.student.model.Boarding;
import org.angelscare.management.student.model.SchoolClass;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Students' lines per term (page 5). */
@Repository
public class StudentTermRepository {

    /** A stored line; the nullable amounts are null while the default applies or while blank. */
    public record Row(String id, String studentId, String termId, TermRef term,
            SchoolClass schoolClass, Boarding boarding, Long amount, Long ream, Long debt,
            Long paid1, Long paid2, Long paid3, String remarks) {

        /** Whether anything was paid on this line. */
        public boolean hasPayments() {
            return positive(paid1) || positive(paid2) || positive(paid3);
        }

        private static boolean positive(Long amount) {
            return amount != null && amount > 0;
        }
    }

    private static final String SELECT = "SELECT s.id, s.student_id, s.term_id, y.year, t.number,"
            + " s.school_class, s.boarding, s.amount, s.ream, s.debt, s.paid_1, s.paid_2, s.paid_3,"
            + " s.remarks"
            + " FROM student_term s JOIN term t ON t.id = s.term_id"
            + " JOIN school_year y ON y.id = t.school_year_id"
            + " WHERE s.deleted_at IS NULL AND t.deleted_at IS NULL AND y.deleted_at IS NULL";

    private final JdbcTemplate jdbc;
    private final SyncedTable table;

    public StudentTermRepository(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.table = new SyncedTable(jdbc, clock, "student_term");
    }

    /** Every live line, of every term: the debt chain needs a student's earlier terms too. */
    public List<Row> findAll() {
        return jdbc.query(SELECT, StudentTermRepository::map);
    }

    /** The student's live lines, of every term. */
    public List<Row> findByStudent(String studentId) {
        return jdbc.query(SELECT + " AND s.student_id = ?", StudentTermRepository::map, studentId);
    }

    public Optional<Row> find(String studentId, String termId) {
        return jdbc.query(SELECT + " AND s.student_id = ? AND s.term_id = ?",
                StudentTermRepository::map, studentId, termId).stream().findFirst();
    }

    /**
     * Whether the student was ever on the term, <em>including</em> lines removed since. This read
     * deliberately does not skip deleted rows: a student taken off a term on purpose must not be
     * put back the next time the term is opened.
     */
    public boolean everOnTerm(String studentId, String termId) {
        return jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM student_term"
                + " WHERE student_id = ? AND term_id = ?)", Boolean.class, studentId, termId);
    }

    public void insert(String studentId, String termId, SchoolClass schoolClass,
            Boarding boarding) {
        table.insert(columns("student_id", studentId, "term_id", termId,
                "school_class", schoolClass.name(), "boarding", boarding.name()));
    }

    /** Sets one column (boarding, amount, ream, debt, paid_1-3, remarks) of a line. */
    public void update(String id, String column, Object value) {
        table.update(id, columns(column, value));
    }

    public void softDelete(String id) {
        table.softDelete(id);
    }

    private static Row map(ResultSet row, int rowNum) throws SQLException {
        return new Row(row.getString("id"), row.getString("student_id"), row.getString("term_id"),
                TermRef.of(row.getInt("year"), row.getInt("number")),
                SchoolClass.valueOf(row.getString("school_class")),
                Boarding.valueOf(row.getString("boarding")),
                nullable(row, "amount"), nullable(row, "ream"), nullable(row, "debt"),
                nullable(row, "paid_1"), nullable(row, "paid_2"), nullable(row, "paid_3"),
                row.getString("remarks"));
    }

    private static Long nullable(ResultSet row, String column) throws SQLException {
        long value = row.getLong(column);
        return row.wasNull() ? null : value;
    }
}

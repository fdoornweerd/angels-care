package org.angelscare.management.student.repository;

import static org.angelscare.management.common.SyncedTable.columns;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.angelscare.management.calendar.model.TermRef;
import org.angelscare.management.common.SyncedTable;
import org.angelscare.management.student.model.Boarding;
import org.angelscare.management.student.model.Residency;
import org.angelscare.management.student.model.SchoolClass;
import org.angelscare.management.student.model.Student;
import org.angelscare.management.student.model.StudentDetails;
import org.angelscare.management.student.model.StudentFilter;
import org.angelscare.management.student.model.StudentStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StudentRepository {

    /** The joined and last terms come back as year and number, like every other term. */
    private static final String SELECT = "SELECT s.id, s.first_name, s.last_name, s.admission_no,"
            + " s.school_class, s.residency, s.status, s.boarding,"
            + " jy.year AS joined_year, jt.number AS joined_number,"
            + " ly.year AS last_year, lt.number AS last_number FROM student s"
            + " LEFT JOIN term jt ON jt.id = s.joined_term_id"
            + " LEFT JOIN school_year jy ON jy.id = jt.school_year_id"
            + " LEFT JOIN term lt ON lt.id = s.left_term_id"
            + " LEFT JOIN school_year ly ON ly.id = lt.school_year_id"
            + " WHERE s.deleted_at IS NULL";
    private static final String ORDER = " ORDER BY lower(s.last_name), lower(s.first_name), s.id";

    private final JdbcTemplate jdbc;
    private final SyncedTable table;

    public StudentRepository(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.table = new SyncedTable(jdbc, clock, "student");
    }

    public Student insert(StudentDetails details, StudentStatus status) {
        Map<String, Object> values = detailColumns(details);
        values.put("status", status.name());
        return findById(table.insert(values)).orElseThrow();
    }

    public void update(String id, StudentDetails details) {
        table.update(id, detailColumns(details));
    }

    public void updateBoarding(String id, Boarding boarding) {
        table.update(id, columns("boarding", boarding.name()));
    }

    public void updateJoinedTerm(String id, String termId) {
        table.update(id, columns("joined_term_id", termId));
    }

    /** {@code lastTermId} is the Left student's last term, and null for Active. */
    public void updateStatus(String id, StudentStatus status, String lastTermId) {
        table.update(id, columns("status", status.name(), "left_term_id", lastTermId));
    }

    public void softDelete(String id) {
        table.softDelete(id);
    }

    public Optional<Student> findById(String id) {
        return jdbc.query(SELECT + " AND s.id = ?", StudentRepository::map, id).stream()
                .findFirst();
    }

    public List<Student> findAll() {
        return find(StudentFilter.ALL);
    }

    /** Sorted by last name, then first name, ignoring case. */
    public List<Student> find(StudentFilter filter) {
        StringBuilder sql = new StringBuilder(SELECT);
        List<Object> args = new ArrayList<>();
        if (filter.schoolClass() != null) {
            sql.append(" AND s.school_class = ?");
            args.add(filter.schoolClass().name());
        }
        if (filter.status() != null) {
            sql.append(" AND s.status = ?");
            args.add(filter.status().name());
        }
        return jdbc.query(sql + ORDER, StudentRepository::map, args.toArray());
    }

    /** Another live student already holding this admission number (any case). */
    public Optional<Student> findByAdmissionNo(String admissionNo, String excludeId) {
        return table.findClash("admission_no", admissionNo, excludeId, null, null)
                .flatMap(stored -> jdbc.query(SELECT + " AND s.admission_no = ?",
                        StudentRepository::map, stored).stream().findFirst());
    }

    private static Map<String, Object> detailColumns(StudentDetails details) {
        return columns(
                "first_name", details.firstName(),
                "last_name", details.lastName(),
                "admission_no", details.admissionNo(),
                "school_class", details.schoolClass().name(),
                "residency", details.residency().name());
    }

    private static Student map(ResultSet row, int rowNum) throws SQLException {
        return new Student(
                row.getString("id"),
                row.getString("first_name"),
                row.getString("last_name"),
                row.getString("admission_no"),
                SchoolClass.valueOf(row.getString("school_class")),
                Residency.valueOf(row.getString("residency")),
                StudentStatus.valueOf(row.getString("status")),
                Boarding.valueOf(row.getString("boarding")),
                term(row, "joined_year", "joined_number"),
                term(row, "last_year", "last_number"));
    }

    private static TermRef term(ResultSet row, String year, String number) throws SQLException {
        int y = row.getInt(year);
        return row.wasNull() ? null : TermRef.of(y, row.getInt(number));
    }
}

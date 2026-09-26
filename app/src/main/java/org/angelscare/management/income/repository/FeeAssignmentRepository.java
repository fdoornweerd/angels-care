package org.angelscare.management.income.repository;

import static org.angelscare.management.common.SyncedTable.columns;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.angelscare.management.calendar.repository.TermRangeColumns;
import org.angelscare.management.common.SyncedTable;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.income.model.BillingFrequency;
import org.angelscare.management.income.model.FeeAssignment;
import org.angelscare.management.income.model.FeeTarget;
import org.angelscare.management.student.model.SchoolClass;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FeeAssignmentRepository {

    private static final String SELECT = "SELECT a.id, a.income_item_id, a.target_type,"
            + " a.student_id, a.group_id, a.school_class, a.amount, a.frequency, "
            + TermRangeColumns.SELECT + " FROM fee_assignment a" + TermRangeColumns.joins("a")
            + " WHERE a.deleted_at IS NULL";
    private static final String ORDER = " ORDER BY sy.year, st.number, a.created_at";

    private final JdbcTemplate jdbc;
    private final SyncedTable table;

    public FeeAssignmentRepository(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.table = new SyncedTable(jdbc, clock, "fee_assignment");
    }

    public FeeAssignment insert(String incomeItemId, FeeTarget target, Ugx amount,
            BillingFrequency frequency, String startTermId, String endTermId) {
        Map<String, Object> values = columns("income_item_id", incomeItemId);
        values.putAll(targetColumns(target));
        values.putAll(columns("amount", amount.shillings(), "frequency", frequency.name(),
                "start_term_id", startTermId, "end_term_id", endTermId));
        return findById(table.insert(values)).orElseThrow();
    }

    public void update(String id, Ugx amount, BillingFrequency frequency, String endTermId) {
        table.update(id, columns("amount", amount.shillings(), "frequency", frequency.name(),
                "end_term_id", endTermId));
    }

    public void softDelete(String id) {
        table.softDelete(id);
    }

    public Optional<FeeAssignment> findById(String id) {
        return jdbc.query(SELECT + " AND a.id = ?", FeeAssignmentRepository::map, id)
                .stream().findFirst();
    }

    public List<FeeAssignment> findByItem(String incomeItemId) {
        return jdbc.query(SELECT + " AND a.income_item_id = ?" + ORDER,
                FeeAssignmentRepository::map, incomeItemId);
    }

    public List<FeeAssignment> findAll() {
        return jdbc.query(SELECT + ORDER, FeeAssignmentRepository::map);
    }

    private static Map<String, Object> targetColumns(FeeTarget target) {
        return switch (target) {
            case FeeTarget.OneStudent t -> columns("target_type", "STUDENT",
                    "student_id", t.studentId(), "group_id", null, "school_class", null);
            case FeeTarget.Group t -> columns("target_type", "GROUP",
                    "student_id", null, "group_id", t.groupId(), "school_class", null);
            case FeeTarget.WholeClass t -> columns("target_type", "CLASS",
                    "student_id", null, "group_id", null, "school_class", t.schoolClass().name());
        };
    }

    private static FeeAssignment map(ResultSet row, int rowNum) throws SQLException {
        FeeTarget target = switch (row.getString("target_type")) {
            case "STUDENT" -> FeeTarget.student(row.getString("student_id"));
            case "GROUP" -> FeeTarget.group(row.getString("group_id"));
            case "CLASS" -> FeeTarget.schoolClass(SchoolClass.valueOf(row.getString("school_class")));
            default -> throw new IllegalStateException(
                    "Unknown target_type " + row.getString("target_type"));
        };
        return new FeeAssignment(
                row.getString("id"),
                row.getString("income_item_id"),
                target,
                Ugx.of(row.getLong("amount")),
                BillingFrequency.valueOf(row.getString("frequency")),
                TermRangeColumns.read(row));
    }
}

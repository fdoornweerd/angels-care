package org.angelscare.management.student.repository;

import static org.angelscare.management.common.SyncedTable.columns;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.angelscare.management.calendar.repository.TermRangeColumns;
import org.angelscare.management.common.SyncedTable;
import org.angelscare.management.student.model.GroupMembership;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class GroupMembershipRepository {

    private static final String SELECT = "SELECT m.id, m.group_id, m.student_id, "
            + TermRangeColumns.SELECT + " FROM group_membership m" + TermRangeColumns.joins("m")
            + " WHERE m.deleted_at IS NULL";
    private static final String ORDER = " ORDER BY sy.year, st.number";
    private static final RowMapper<GroupMembership> MAPPER = (row, n) -> new GroupMembership(
            row.getString("id"), row.getString("group_id"), row.getString("student_id"),
            TermRangeColumns.read(row));

    private final JdbcTemplate jdbc;
    private final SyncedTable table;

    public GroupMembershipRepository(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.table = new SyncedTable(jdbc, clock, "group_membership");
    }

    public GroupMembership insert(String groupId, String studentId, String startTermId,
            String endTermId) {
        return findById(table.insert(columns("group_id", groupId, "student_id", studentId,
                "start_term_id", startTermId, "end_term_id", endTermId))).orElseThrow();
    }

    public void updateEnd(String id, String endTermId) {
        table.update(id, columns("end_term_id", endTermId));
    }

    public void softDelete(String id) {
        table.softDelete(id);
    }

    public Optional<GroupMembership> findById(String id) {
        return jdbc.query(SELECT + " AND m.id = ?", MAPPER, id).stream().findFirst();
    }

    public List<GroupMembership> findByGroup(String groupId) {
        return jdbc.query(SELECT + " AND m.group_id = ?" + ORDER, MAPPER, groupId);
    }

    public List<GroupMembership> findByStudent(String studentId) {
        return jdbc.query(SELECT + " AND m.student_id = ?" + ORDER, MAPPER, studentId);
    }

    public List<GroupMembership> findAll() {
        return jdbc.query(SELECT + ORDER, MAPPER);
    }
}

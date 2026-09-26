package org.angelscare.management.student.repository;

import static org.angelscare.management.common.SyncedTable.LIVE;
import static org.angelscare.management.common.SyncedTable.columns;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.angelscare.management.common.SyncedTable;
import org.angelscare.management.student.model.StudentGroup;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class StudentGroupRepository {

    private static final String SELECT = "SELECT id, name, description FROM student_group WHERE " + LIVE;
    private static final RowMapper<StudentGroup> MAPPER = (row, n) ->
            new StudentGroup(row.getString("id"), row.getString("name"), row.getString("description"));

    private final JdbcTemplate jdbc;
    private final SyncedTable table;

    public StudentGroupRepository(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.table = new SyncedTable(jdbc, clock, "student_group");
    }

    public StudentGroup insert(String name, String description) {
        return findById(table.insert(columns("name", name, "description", description))).orElseThrow();
    }

    public void update(String id, String name, String description) {
        table.update(id, columns("name", name, "description", description));
    }

    public void softDelete(String id) {
        table.softDelete(id);
    }

    public Optional<StudentGroup> findById(String id) {
        return jdbc.query(SELECT + " AND id = ?", MAPPER, id).stream().findFirst();
    }

    public List<StudentGroup> findAll() {
        return jdbc.query(SELECT + " ORDER BY lower(name)", MAPPER);
    }

    /** The stored name of another live group with this name (any case). */
    public Optional<String> nameClash(String name, String excludeId) {
        return table.findClash("name", name, excludeId, null, null);
    }
}

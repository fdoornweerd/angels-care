package org.angelscare.management.common;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The sync rules from CLAUDE.md for one table, in one place: new rows get a UUID and
 * {@code created_at}/{@code updated_at}; every write moves {@code updated_at}; deletes only set
 * {@code deleted_at}. Repositories write through this instead of repeating those rules.
 *
 * <p>Table and column names come from code, never from the user; values are always bound.
 */
public final class SyncedTable {

    /** Add to every read: rows marked deleted are invisible. */
    public static final String LIVE = "deleted_at IS NULL";

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final String table;

    public SyncedTable(JdbcTemplate jdbc, Clock clock, String table) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.table = table;
    }

    /** Column values in order, allowing nulls (which {@link Map#of} does not). */
    public static Map<String, Object> columns(Object... namesAndValues) {
        Map<String, Object> columns = new LinkedHashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            columns.put((String) namesAndValues[i], namesAndValues[i + 1]);
        }
        return columns;
    }

    /** Inserts a row with a new id; returns the id. */
    public String insert(Map<String, Object> values) {
        String id = Ids.newId();
        String now = now();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.putAll(values);
        row.put("created_at", now);
        row.put("updated_at", now);
        jdbc.update("INSERT INTO " + table + " (" + String.join(", ", row.keySet()) + ") VALUES ("
                + String.join(", ", Collections.nCopies(row.size(), "?")) + ")",
                row.values().toArray());
        return id;
    }

    /** Updates a live row and stamps {@code updated_at}; false if there is no such row. */
    public boolean update(String id, Map<String, Object> values) {
        List<String> assignments = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        values.forEach((column, value) -> {
            assignments.add(column + " = ?");
            args.add(value);
        });
        assignments.add("updated_at = ?");
        args.add(now());
        args.add(id);
        return jdbc.update("UPDATE " + table + " SET " + String.join(", ", assignments)
                + " WHERE id = ? AND " + LIVE, args.toArray()) == 1;
    }

    /** Marks a live row deleted; false if there is no such row. Never issues a SQL DELETE. */
    public boolean softDelete(String id) {
        String now = now();
        return jdbc.update("UPDATE " + table + " SET deleted_at = ?, updated_at = ? WHERE id = ? AND "
                + LIVE, now, now, id) == 1;
    }

    /**
     * The stored value of {@code column} on another live row that equals {@code value}, ignoring
     * case, if there is one. {@code excludeId} (may be null) is the row being edited;
     * {@code scopeColumn} (may be null) limits the search, e.g. to one category's items.
     */
    public Optional<String> findClash(String column, String value, String excludeId,
            String scopeColumn, Object scopeValue) {
        String sql = "SELECT " + column + " FROM " + table + " WHERE " + LIVE
                + " AND lower(" + column + ") = lower(?) AND id <> ?"
                + (scopeColumn == null ? "" : " AND " + scopeColumn + " = ?") + " LIMIT 1";
        Object[] args = scopeColumn == null
                ? new Object[] {value, excludeId == null ? "" : excludeId}
                : new Object[] {value, excludeId == null ? "" : excludeId, scopeValue};
        return jdbc.queryForList(sql, String.class, args).stream().findFirst();
    }

    private String now() {
        // Millisecond precision, like V1's strftime('%f'), so stamps from different sources compare.
        return clock.instant().truncatedTo(ChronoUnit.MILLIS).toString();
    }
}

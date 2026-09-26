package org.angelscare.management.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Blocks a soft delete while other non-deleted rows still refer to the row. It finds the
 * referring tables from the schema's foreign keys, so a new table is protected without any code
 * here changing.
 */
@Component
public class DeletionGuard {

    private final JdbcTemplate jdbc;
    /** Referenced table → (referring table → its referring columns). Read lazily: Flyway may not have run yet when this bean is built. */
    private volatile Map<String, Map<String, List<String>>> references;

    public DeletionGuard(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Throws {@link ValidationException} naming {@code displayName} and what still uses it, if any
     * non-deleted row outside {@code ignoredTables} refers to {@code table.id}.
     */
    public void requireUnused(String table, String id, String displayName, String... ignoredTables) {
        Set<String> ignored = Set.of(ignoredTables);
        List<String> usages = new ArrayList<>();
        references().getOrDefault(table, Map.of()).forEach((child, columns) -> {
            if (ignored.contains(child)) {
                return;
            }
            String matches = String.join(" OR ", columns.stream().map(c -> c + " = ?").toList());
            int count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + child + " WHERE " + SyncedTable.LIVE
                            + " AND (" + matches + ")",
                    Integer.class, columns.stream().map(c -> id).toArray());
            if (count > 0) {
                usages.add(count + " " + child.replace('_', ' ') + (count == 1 ? "" : "s"));
            }
        });
        if (!usages.isEmpty()) {
            throw new ValidationException("Can't delete " + displayName + ": it is still used by "
                    + String.join(" and ", usages) + ". Delete those first.");
        }
    }

    private Map<String, Map<String, List<String>>> references() {
        if (references == null) {
            Map<String, Map<String, List<String>>> found = new LinkedHashMap<>();
            List<String> tables = jdbc.queryForList(
                    "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'"
                            + " ORDER BY name", String.class);
            for (String child : tables) {
                jdbc.query("SELECT \"table\" AS parent, \"from\" AS col FROM pragma_foreign_key_list(?)",
                        row -> {
                            found.computeIfAbsent(row.getString("parent"), k -> new LinkedHashMap<>())
                                    .computeIfAbsent(child, k -> new ArrayList<>())
                                    .add(row.getString("col"));
                        }, child);
            }
            references = found;
        }
        return references;
    }
}

package org.angelscare.management;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.angelscare.management.db.SqliteDataSources;
import org.angelscare.management.support.FinanceTables;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

/** Spec 001: the finance migration runs on the databases that already exist. */
class FinanceMigrationTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("AC-1: a database at V1 gets every finance table, and app_meta is untouched")
    void upgradesFromV1() throws Exception {
        DataSource dataSource = SqliteDataSources.create(tempDir.resolve("v1.db"));
        try {
            Flyway.configure().dataSource(dataSource).target("1").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.update("INSERT INTO app_meta (key, value) VALUES ('device_id', 'device-1')");
            List<Map<String, Object>> appMetaBefore =
                    jdbc.queryForList("SELECT * FROM app_meta ORDER BY key");

            Flyway.configure().dataSource(dataSource).load().migrate();

            assertThat(tableNames(jdbc)).containsAll(FinanceTables.ALL);
            assertThat(jdbc.queryForList("SELECT * FROM app_meta ORDER BY key"))
                    .isEqualTo(appMetaBefore);
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }

    @Test
    @DisplayName("AC-1: a baselined pre-Flyway database also gets every finance table")
    void upgradesPreFlywayDatabase() throws Exception {
        DataSource dataSource = SqliteDataSources.create(tempDir.resolve("old.db"));
        try {
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("CREATE TABLE students (id INTEGER PRIMARY KEY, name TEXT)");

            // The same baseline settings as application.properties.
            Flyway.configure().dataSource(dataSource)
                    .baselineOnMigrate(true).baselineVersion("0")
                    .load().migrate();

            assertThat(tableNames(jdbc)).containsAll(FinanceTables.ALL);
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }

    private static List<String> tableNames(JdbcTemplate jdbc) {
        return jdbc.queryForList("SELECT name FROM sqlite_master WHERE type = 'table'", String.class);
    }
}

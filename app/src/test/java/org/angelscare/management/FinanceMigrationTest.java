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

/** The finance migrations run on the databases that already exist. */
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

    @Test
    @DisplayName("AC-1 (002): a database at V2 gets the V3 tables and columns; its rows are unchanged")
    void upgradesFromV2() throws Exception {
        DataSource dataSource = SqliteDataSources.create(tempDir.resolve("v2.db"));
        try {
            Flyway.configure().dataSource(dataSource).target("2").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.update("INSERT INTO student (id, first_name, last_name, school_class, residency,"
                    + " status, created_at, updated_at) VALUES ('s1', 'Amina', 'Nakato', 'P4',"
                    + " 'NATIONAL', 'ACTIVE', 't', 't')");
            jdbc.update("INSERT INTO income_category (id, name, created_at, updated_at)"
                    + " VALUES ('c1', 'Donations', 't', 't')");
            List<Map<String, Object>> studentsBefore = jdbc.queryForList("SELECT * FROM student");

            Flyway.configure().dataSource(dataSource).load().migrate();

            assertThat(tableNames(jdbc)).containsAll(FinanceTables.V3);
            assertThat(columnNames(jdbc, "income_category")).contains("school_year_id");
            assertThat(columnNames(jdbc, "expense_category")).contains("school_year_id");
            assertThat(columnNames(jdbc, "income_item")).contains("unit");
            assertThat(columnNames(jdbc, "expense_item")).contains("unit");
            assertThat(jdbc.queryForList("SELECT * FROM student")).isEqualTo(studentsBefore);
            // A category from before V3 has no school year, so no page shows it.
            assertThat(jdbc.queryForObject(
                    "SELECT school_year_id FROM income_category WHERE id = 'c1'", String.class))
                    .isNull();
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }

    @Test
    @DisplayName("AC-1 (002, found in implementation): categories from before V3 join the latest school year")
    void attachesOldCategoriesToLatestYear() throws Exception {
        DataSource dataSource = SqliteDataSources.create(tempDir.resolve("v2-with-data.db"));
        try {
            Flyway.configure().dataSource(dataSource).target("2").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.update("INSERT INTO school_year (id, year, created_at, updated_at)"
                    + " VALUES ('y2025', 2025, 't', 't'), ('y2026', 2026, 't', 't')");
            jdbc.update("INSERT INTO school_year (id, year, created_at, updated_at, deleted_at)"
                    + " VALUES ('y2027', 2027, 't', 't', 'gone')");
            jdbc.update("INSERT INTO income_category (id, name, created_at, updated_at)"
                    + " VALUES ('c1', 'Students', 't', 't')");
            jdbc.update("INSERT INTO expense_category (id, name, created_at, updated_at)"
                    + " VALUES ('e1', 'Salary', 't', 't')");

            Flyway.configure().dataSource(dataSource).load().migrate();

            assertThat(jdbc.queryForObject(
                    "SELECT school_year_id FROM income_category WHERE id = 'c1'", String.class))
                    .isEqualTo("y2026");
            assertThat(jdbc.queryForObject(
                    "SELECT school_year_id FROM expense_category WHERE id = 'e1'", String.class))
                    .isEqualTo("y2026");
            // A changed row is stamped, so sync will pick it up.
            assertThat(jdbc.queryForObject(
                    "SELECT updated_at FROM expense_category WHERE id = 'e1'", String.class))
                    .isNotEqualTo("t");
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }

    private static List<String> columnNames(JdbcTemplate jdbc, String table) {
        return jdbc.queryForList("SELECT name FROM pragma_table_info(?)", String.class, table);
    }

    private static List<String> tableNames(JdbcTemplate jdbc) {
        return jdbc.queryForList("SELECT name FROM sqlite_master WHERE type = 'table'", String.class);
    }
}

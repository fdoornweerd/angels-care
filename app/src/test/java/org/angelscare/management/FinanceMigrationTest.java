package org.angelscare.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.dao.DataAccessException;
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

            // To V3 only: V4 (spec 003) adds student columns, which AC-1 (003) checks.
            Flyway.configure().dataSource(dataSource).target("3").load().migrate();

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

    /** The columns V3 gave these tables, which V4 must leave as they were. */
    private static final String STUDENT_V3 = "SELECT id, first_name, last_name, admission_no,"
            + " school_class, residency, status, created_at, deleted_at FROM student ORDER BY id";
    private static final String STUDENT_TERM_V3 = "SELECT id, student_id, term_id, school_class,"
            + " amount, ream, debt, paid_1, paid_2, paid_3, remarks, created_at, updated_at,"
            + " deleted_at FROM student_term ORDER BY id";
    private static final String CLASS_FEE_V3 = "SELECT id, school_class, term_id, amount, ream,"
            + " created_at, updated_at, deleted_at FROM class_fee ORDER BY id";

    @Test
    @DisplayName("AC-1 (003): V4 makes everyone Day, keeps fees as Day fees, and records joined and last terms")
    void upgradesFromV3() throws Exception {
        DataSource dataSource = SqliteDataSources.create(tempDir.resolve("v3.db"));
        try {
            Flyway.configure().dataSource(dataSource).target("3").load().migrate();
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.update("INSERT INTO school_year (id, year, created_at, updated_at)"
                    + " VALUES ('y', 2026, 't', 't')");
            jdbc.update("INSERT INTO term (id, school_year_id, number, start_date, end_date,"
                    + " created_at, updated_at) VALUES"
                    + " ('t1', 'y', 1, '2026-02-02', '2026-04-24', 't', 't'),"
                    + " ('t2', 'y', 2, '2026-05-18', '2026-08-14', 't', 't'),"
                    + " ('t3', 'y', 3, '2026-09-07', '2026-12-04', 't', 't')");
            jdbc.update("INSERT INTO student (id, first_name, last_name, school_class, residency,"
                    + " status, created_at, updated_at) VALUES"
                    + " ('ann', 'Ann', 'Akello', 'P7', 'NATIONAL', 'ACTIVE', 't', 't'),"
                    + " ('cara', 'Cara', 'Nambi', 'P7', 'REFUGEE', 'ACTIVE', 't', 't'),"
                    + " ('grace', 'Grace', 'Atim', 'P5', 'NATIONAL', 'ACTIVE', 't', 't'),"
                    + " ('ben', 'Ben', 'Okello', 'P3', 'NATIONAL', 'LEFT', 't', 't')");
            // Cara's Term 1 line was removed, so she joined in Term 3.
            jdbc.update("INSERT INTO student_term (id, student_id, term_id, school_class, paid_1,"
                    + " created_at, updated_at, deleted_at) VALUES"
                    + " ('l1', 'ann', 't1', 'P7', 100000, 't', 't', NULL),"
                    + " ('l2', 'ann', 't3', 'P7', NULL, 't', 't', NULL),"
                    + " ('l3', 'cara', 't1', 'P7', NULL, 't', 't', 'removed'),"
                    + " ('l4', 'cara', 't3', 'P7', NULL, 't', 't', NULL),"
                    + " ('l5', 'ben', 't1', 'P3', NULL, 't', 't', NULL),"
                    + " ('l6', 'ben', 't2', 'P3', 5000, 't', 't', NULL)");
            jdbc.update("INSERT INTO class_fee (id, school_class, term_id, amount, ream,"
                    + " created_at, updated_at) VALUES"
                    + " ('f7', 'P7', 't1', 300000, 10000, 't', 't'),"
                    + " ('f3', 'P3', 't3', 200000, 5000, 't', 't')");
            List<Map<String, Object>> students = jdbc.queryForList(STUDENT_V3);
            List<Map<String, Object>> lines = jdbc.queryForList(STUDENT_TERM_V3);
            List<Map<String, Object>> fees = jdbc.queryForList(CLASS_FEE_V3);

            Flyway.configure().dataSource(dataSource).load().migrate();

            assertThat(columnNames(jdbc, "student"))
                    .contains("boarding", "joined_term_id", "left_term_id");
            assertThat(columnNames(jdbc, "student_term")).contains("boarding");
            assertThat(columnNames(jdbc, "class_fee")).contains("boarding_amount");
            assertThat(jdbc.queryForList("SELECT DISTINCT boarding FROM student", String.class))
                    .containsExactly("DAY");
            assertThat(jdbc.queryForList("SELECT DISTINCT boarding FROM student_term",
                    String.class)).containsExactly("DAY");
            assertThat(jdbc.queryForList("SELECT boarding_amount FROM class_fee", Long.class))
                    .containsExactly(0L, 0L);
            assertThat(studentColumn(jdbc, "joined_term_id", "ann")).isEqualTo("t1");
            assertThat(studentColumn(jdbc, "joined_term_id", "cara")).isEqualTo("t3");
            assertThat(studentColumn(jdbc, "joined_term_id", "grace")).isNull();
            assertThat(studentColumn(jdbc, "joined_term_id", "ben")).isEqualTo("t1");
            assertThat(studentColumn(jdbc, "left_term_id", "ben")).isEqualTo("t2");
            assertThat(studentColumn(jdbc, "left_term_id", "ann")).isNull();
            // Rows given a joined or last term are stamped; rows only given defaults are not.
            assertThat(studentColumn(jdbc, "updated_at", "ann")).isNotEqualTo("t");
            assertThat(studentColumn(jdbc, "updated_at", "cara")).isNotEqualTo("t");
            assertThat(studentColumn(jdbc, "updated_at", "ben")).isNotEqualTo("t");
            assertThat(studentColumn(jdbc, "updated_at", "grace")).isEqualTo("t");
            assertThat(jdbc.queryForList(STUDENT_V3)).isEqualTo(students);
            assertThat(jdbc.queryForList(STUDENT_TERM_V3)).isEqualTo(lines);
            assertThat(jdbc.queryForList(CLASS_FEE_V3)).isEqualTo(fees);
            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE student SET boarding = 'WEEKLY' WHERE id = 'ann'"))
                    .isInstanceOf(DataAccessException.class).hasMessageContaining("CHECK");
            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE student_term SET boarding = 'WEEKLY' WHERE id = 'l1'"))
                    .isInstanceOf(DataAccessException.class).hasMessageContaining("CHECK");
            assertThatThrownBy(() -> jdbc.update(
                    "UPDATE class_fee SET boarding_amount = -1 WHERE id = 'f7'"))
                    .isInstanceOf(DataAccessException.class).hasMessageContaining("CHECK");
        } finally {
            ((AutoCloseable) dataSource).close();
        }
    }

    private static String studentColumn(JdbcTemplate jdbc, String column, String id) {
        return jdbc.queryForObject("SELECT " + column + " FROM student WHERE id = ?", String.class,
                id);
    }

    private static List<String> columnNames(JdbcTemplate jdbc, String table) {
        return jdbc.queryForList("SELECT name FROM pragma_table_info(?)", String.class, table);
    }

    private static List<String> tableNames(JdbcTemplate jdbc) {
        return jdbc.queryForList("SELECT name FROM sqlite_master WHERE type = 'table'", String.class);
    }
}

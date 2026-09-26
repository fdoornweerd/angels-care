package org.angelscare.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

/** Starts the real Spring context exactly as the application does, against a temp data folder. */
class BootstrapTest {

    @TempDir
    Path tempDir;

    Path dataDir;

    @BeforeEach
    void setUp() {
        dataDir = tempDir.resolve("AngelsCareData");
        // Keep the start-up log out of the real ~/AngelsCareData.
        Diagnostics.startLogFile(tempDir.resolve("logs"));
    }

    @Test
    @DisplayName("AC-1: a fresh data folder gets the schema and a device_id")
    void freshDataFolder() {
        try (ConfigurableApplicationContext context = startOrFail(dataDir)) {
            String deviceId = deviceId(context);

            assertThat(Files.exists(dataDir.resolve("angels-care.db"))).isTrue();
            assertThat(deviceId).isNotBlank();
            assertThat(UUID.fromString(deviceId).toString()).isEqualTo(deviceId);
        }
    }

    @Test
    @DisplayName("AC-2: restarting keeps the device_id and re-runs no migrations")
    void restartIsStable() {
        String firstId;
        int firstMigrations;
        try (ConfigurableApplicationContext context = startOrFail(dataDir)) {
            firstId = deviceId(context);
            firstMigrations = migrationCount(context);
        }

        try (ConfigurableApplicationContext context = startOrFail(dataDir)) {
            assertThat(deviceId(context)).isEqualTo(firstId);
            assertThat(migrationCount(context)).isEqualTo(firstMigrations);
        }
    }

    @Test
    @DisplayName("a database from before Flyway is upgraded, and its test-only students table dropped")
    void preFlywayDatabase() throws Exception {
        // What the pre-scaffold app left on every machine it ran on (including the Windows PC).
        Files.createDirectories(dataDir);
        try (var conn = java.sql.DriverManager.getConnection(
                     "jdbc:sqlite:" + dataDir.resolve("angels-care.db"));
             var stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE students (id INTEGER PRIMARY KEY, name TEXT)");
            stmt.execute("INSERT INTO students (name) VALUES ('Test Student')");
        }

        try (ConfigurableApplicationContext context = startOrFail(dataDir)) {
            assertThat(deviceId(context)).isNotBlank();
            assertThat(context.getBean(JdbcTemplate.class).queryForObject(
                    "SELECT COUNT(*) FROM sqlite_master WHERE name = 'students'", Integer.class))
                    .isZero();
        }
    }

    @Test
    @DisplayName("AC-5: an unusable data folder is reported, not thrown, and the cause is logged")
    void unusableDataFolder() throws IOException {
        // A plain file where the data folder should be: no database can be created inside it.
        Path blocked = Files.writeString(tempDir.resolve("blocked"), "not a folder");

        StartupResult result = Bootstrap.start(blocked);

        assertThat(result).isInstanceOf(StartupResult.Failed.class);
        StartupResult.Failed failed = (StartupResult.Failed) result;
        assertThat(failed.message()).contains(Diagnostics.logFilePath().toString());
        assertThat(Files.readString(Diagnostics.logFilePath()))
                .contains(failed.cause().getClass().getName());
    }

    private static ConfigurableApplicationContext startOrFail(Path dataDir) {
        StartupResult result = Bootstrap.start(dataDir);
        if (result instanceof StartupResult.Failed failed) {
            fail("start-up failed: " + failed.message(), failed.cause());
        }
        return ((StartupResult.Started) result).context();
    }

    private static String deviceId(ConfigurableApplicationContext context) {
        return context.getBean(JdbcTemplate.class)
                .queryForObject("SELECT value FROM app_meta WHERE key = 'device_id'", String.class);
    }

    private static int migrationCount(ConfigurableApplicationContext context) {
        return context.getBean(JdbcTemplate.class)
                .queryForObject("SELECT COUNT(*) FROM flyway_schema_history", Integer.class);
    }
}

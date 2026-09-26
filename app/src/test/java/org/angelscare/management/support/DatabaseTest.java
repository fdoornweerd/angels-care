package org.angelscare.management.support;

import java.nio.file.Path;
import javax.sql.DataSource;
import org.angelscare.management.db.SqliteDataSources;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Base class for repository and service tests: each test gets its own SQLite file with every
 * Flyway migration applied, built with the same settings the application uses. No Spring context,
 * so it is fast - construct the classes under test by hand from {@link #jdbc}.
 */
public abstract class DatabaseTest {

    @TempDir
    protected Path tempDir;

    protected DataSource dataSource;
    protected JdbcTemplate jdbc;

    @BeforeEach
    void createDatabase() {
        dataSource = SqliteDataSources.create(tempDir.resolve("test.db"));
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterEach
    void closeDatabase() throws Exception {
        if (dataSource instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }
}

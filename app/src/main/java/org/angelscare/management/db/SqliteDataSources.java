package org.angelscare.management.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Path;
import javax.sql.DataSource;

/**
 * The one place a SQLite {@link DataSource} is built, so that the application and the tests get
 * identical connection settings.
 */
public final class SqliteDataSources {

    private SqliteDataSources() {
    }

    public static DataSource create(Path dbFile) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("angels-care-sqlite");
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.toAbsolutePath());
        // SQLite allows one writer at a time; a single shared connection means callers queue in
        // the pool instead of failing with SQLITE_BUSY.
        config.setMaximumPoolSize(1);
        // sqlite-jdbc applies these to every connection it opens. Foreign keys in particular are
        // OFF by default in SQLite and are a per-connection setting, not a property of the file.
        config.addDataSourceProperty("foreign_keys", "true");
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("busy_timeout", "5000");
        return new HikariDataSource(config);
    }
}

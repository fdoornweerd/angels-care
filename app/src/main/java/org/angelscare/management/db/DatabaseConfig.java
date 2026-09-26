package org.angelscare.management.db;

import java.nio.file.Path;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class DatabaseConfig {

    /** Flyway and JdbcTemplate are auto-configured on top of this bean. */
    @Bean
    DataSource dataSource(@Value("${angelscare.data-dir}") Path dataDir) {
        return SqliteDataSources.create(dataDir.resolve("angels-care.db"));
    }
}

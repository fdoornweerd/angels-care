package org.angelscare.management.db;

import static org.assertj.core.api.Assertions.assertThat;

import org.angelscare.management.support.DatabaseTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SqliteDataSourcesTest extends DatabaseTest {

    @Test
    @DisplayName("AC-3: foreign keys are enforced on every connection")
    void foreignKeysAreOn() {
        Integer foreignKeys = jdbc.queryForObject("PRAGMA foreign_keys", Integer.class);

        assertThat(foreignKeys).isEqualTo(1);
    }
}

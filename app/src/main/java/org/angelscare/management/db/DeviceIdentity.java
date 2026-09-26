package org.angelscare.management.db;

import org.angelscare.management.common.Ids;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * A random ID for this database file, created on first start and never changed. Sync will use it
 * to tell which computer made a change.
 */
@Component
public class DeviceIdentity {

    private final String deviceId;

    DeviceIdentity(JdbcTemplate jdbc) {
        jdbc.update("INSERT OR IGNORE INTO app_meta (key, value) VALUES ('device_id', ?)", Ids.newId());
        this.deviceId = jdbc.queryForObject(
                "SELECT value FROM app_meta WHERE key = 'device_id'", String.class);
    }

    public String deviceId() {
        return deviceId;
    }
}

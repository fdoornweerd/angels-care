package org.angelscare.management.common;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/** The school's time zone: "today" is today in Uganda, whatever the PC is set to. */
public final class SchoolTime {

    public static final ZoneId ZONE = ZoneId.of("Africa/Kampala");

    private SchoolTime() {
    }

    public static LocalDate today(Clock clock) {
        return LocalDate.ofInstant(clock.instant(), ZONE);
    }
}

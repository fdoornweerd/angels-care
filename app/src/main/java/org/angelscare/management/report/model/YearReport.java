package org.angelscare.management.report.model;

import java.time.ZonedDateTime;
import java.util.List;
import org.angelscare.management.accounts.model.YearTotals;
import org.angelscare.management.calendar.model.SchoolYear;

/**
 * Everything one school year's printed report shows (spec 005), gathered before any PDF is drawn.
 * {@code printedAt} is in Africa/Kampala time.
 */
public record YearReport(SchoolYear year, YearTotals totals, List<TermReport> terms,
        ZonedDateTime printedAt) {
}

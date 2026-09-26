package org.angelscare.management.calendar.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import org.angelscare.management.calendar.model.TermRange;
import org.angelscare.management.calendar.model.TermRef;

/**
 * Reads a {@link TermRange} stored as {@code start_term_id}/{@code end_term_id} columns, for any
 * table that has them (memberships, fee assignments). Joins through to the school year so the
 * range comes back as "2026 Term 1", not as ids.
 */
public final class TermRangeColumns {

    public static final String SELECT =
            "sy.year AS start_year, st.number AS start_number, ey.year AS end_year, et.number AS end_number";

    private TermRangeColumns() {
    }

    /** Joins for the row aliased {@code alias}. */
    public static String joins(String alias) {
        return " JOIN term st ON st.id = " + alias + ".start_term_id"
                + " JOIN school_year sy ON sy.id = st.school_year_id"
                + " LEFT JOIN term et ON et.id = " + alias + ".end_term_id"
                + " LEFT JOIN school_year ey ON ey.id = et.school_year_id";
    }

    public static TermRange read(ResultSet row) throws SQLException {
        TermRef start = TermRef.of(row.getInt("start_year"), row.getInt("start_number"));
        int endYear = row.getInt("end_year");
        if (row.wasNull()) {
            return TermRange.from(start);
        }
        return new TermRange(start, TermRef.of(endYear, row.getInt("end_number")));
    }
}

package org.angelscare.management.calendar.repository;

import static org.angelscare.management.common.SyncedTable.LIVE;
import static org.angelscare.management.common.SyncedTable.columns;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.angelscare.management.calendar.model.SchoolYear;
import org.angelscare.management.calendar.model.Term;
import org.angelscare.management.calendar.model.TermDates;
import org.angelscare.management.calendar.model.TermRef;
import org.angelscare.management.common.SyncedTable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** School years together with their terms: a term never exists without its year. */
@Repository
public class SchoolYearRepository {

    private final JdbcTemplate jdbc;
    private final SyncedTable years;
    private final SyncedTable terms;

    public SchoolYearRepository(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.years = new SyncedTable(jdbc, clock, "school_year");
        this.terms = new SyncedTable(jdbc, clock, "term");
    }

    public SchoolYear insert(int year, List<TermDates> termDates) {
        String yearId = years.insert(columns("year", year));
        for (int i = 0; i < termDates.size(); i++) {
            TermDates dates = termDates.get(i);
            terms.insert(columns("school_year_id", yearId, "number", i + 1,
                    "start_date", dates.start().toString(), "end_date", dates.end().toString()));
        }
        return findById(yearId).orElseThrow();
    }

    public Optional<SchoolYear> findById(String id) {
        return findYears("id = ?", id).stream().findFirst();
    }

    public Optional<SchoolYear> findByYear(int year) {
        return findYears("year = ?", year).stream().findFirst();
    }

    /** Oldest first. */
    public List<SchoolYear> findAll() {
        return findYears("1 = 1");
    }

    public boolean anyExists() {
        return jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM school_year WHERE " + LIVE + ")",
                Boolean.class);
    }

    public void updateTermDates(String termId, TermDates dates) {
        terms.update(termId, columns(
                "start_date", dates.start().toString(), "end_date", dates.end().toString()));
    }

    public void softDelete(SchoolYear year) {
        year.terms().forEach(term -> terms.softDelete(term.id()));
        years.softDelete(year.id());
    }

    private List<SchoolYear> findYears(String condition, Object... args) {
        List<Object[]> rows = jdbc.query(
                "SELECT id, year FROM school_year WHERE " + LIVE + " AND " + condition
                        + " ORDER BY year",
                (row, n) -> new Object[] {row.getString("id"), row.getInt("year")}, args);
        return rows.stream()
                .map(row -> new SchoolYear((String) row[0], (int) row[1],
                        termsOf((String) row[0], (int) row[1])))
                .toList();
    }

    private List<Term> termsOf(String yearId, int year) {
        return jdbc.query(
                "SELECT id, number, start_date, end_date FROM term"
                        + " WHERE school_year_id = ? AND " + LIVE + " ORDER BY number",
                (row, n) -> new Term(row.getString("id"),
                        TermRef.of(year, row.getInt("number")),
                        TermDates.of(LocalDate.parse(row.getString("start_date")),
                                LocalDate.parse(row.getString("end_date")))),
                yearId);
    }
}

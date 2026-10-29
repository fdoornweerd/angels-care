package org.angelscare.management.report.model;

import java.util.List;
import org.angelscare.management.accounts.model.TermSummary;
import org.angelscare.management.calendar.model.Term;
import org.angelscare.management.student.model.StudentsTotals;

/**
 * One term of the report: page 2's summary, pages 3 and 4 per category, and page 5 per class
 * (only classes with students on the term). A term with {@code nothingRecorded} prints only its
 * summary.
 */
public record TermReport(
        Term term,
        TermSummary summary,
        boolean nothingRecorded,
        StudentsTotals students,
        List<CategorySheet> incomes,
        List<CategorySheet> expenses,
        List<ClassSheet> classes) {
}

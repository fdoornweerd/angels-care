package org.angelscare.management.accounts.service;

import java.util.Comparator;
import java.util.Optional;
import org.angelscare.management.calendar.model.SchoolYear;
import org.angelscare.management.calendar.model.TermDates;
import org.angelscare.management.calendar.service.CalendarService;
import org.angelscare.management.expense.service.ExpenseCatalogService;
import org.angelscare.management.income.service.IncomeCatalogService;
import org.springframework.transaction.annotation.Transactional;

/** Creating a school year, optionally with the previous year's categories and items. */
@Transactional
public class SchoolYearSetupService {

    private final CalendarService calendar;
    private final IncomeCatalogService income;
    private final ExpenseCatalogService expenses;

    public SchoolYearSetupService(CalendarService calendar, IncomeCatalogService income,
            ExpenseCatalogService expenses) {
        this.calendar = calendar;
        this.income = income;
        this.expenses = expenses;
    }

    /** The latest school year that starts before {@code year}: the one a new year copies. */
    @Transactional(readOnly = true)
    public Optional<SchoolYear> previousYear(int year) {
        return calendar.listYears().stream()
                .filter(existing -> existing.year() < year)
                .max(Comparator.comparingInt(SchoolYear::year));
    }

    /** Creates the year and, if asked and there is one, copies the previous year's catalogue. */
    public SchoolYear createYear(int year, TermDates term1, TermDates term2, TermDates term3,
            boolean copyFromPrevious) {
        Optional<SchoolYear> previous = previousYear(year);
        SchoolYear created = calendar.createYear(year, term1, term2, term3);
        if (copyFromPrevious && previous.isPresent()) {
            income.copyFromYear(previous.get().year(), year);
            expenses.copyFromYear(previous.get().year(), year);
        }
        return created;
    }
}

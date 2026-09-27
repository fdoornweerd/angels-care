package org.angelscare.management.shell.ui;

import java.time.Clock;
import org.angelscare.management.accounts.service.ExpenseCatalog;
import org.angelscare.management.accounts.service.IncomeCatalog;
import org.angelscare.management.accounts.service.LedgerSheetService;
import org.angelscare.management.accounts.service.SchoolYearSetupService;
import org.angelscare.management.accounts.service.TermAccountsService;
import org.angelscare.management.accounts.ui.DetailViewModel;
import org.angelscare.management.accounts.ui.TermSummaryViewModel;
import org.angelscare.management.calendar.service.CalendarService;
import org.angelscare.management.calendar.ui.SchoolYearsViewModel;
import org.angelscare.management.common.ui.ConfirmDialogs;
import org.angelscare.management.common.ui.fx.JavaFxDialogs;
import org.angelscare.management.student.service.StudentAccountService;
import org.angelscare.management.student.ui.StudentsViewModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The navigator and one view model per page, for the one main window. They are plain classes
 * (tested without Spring), so they are made here rather than annotated.
 */
@Configuration
class PagesConfig {

    @Bean
    ConfirmDialogs confirmDialogs() {
        return new JavaFxDialogs();
    }

    @Bean
    Navigator navigator(CalendarService calendar, Clock clock) {
        return new Navigator(calendar, clock);
    }

    @Bean
    SchoolYearsViewModel schoolYearsViewModel(SchoolYearSetupService setup,
            CalendarService calendar, TermAccountsService accounts, ConfirmDialogs dialogs,
            Clock clock) {
        return new SchoolYearsViewModel(setup, calendar, accounts, dialogs, clock);
    }

    @Bean
    TermSummaryViewModel termSummaryViewModel(TermAccountsService accounts) {
        return new TermSummaryViewModel(accounts);
    }

    @Bean
    DetailViewModel incomeDetailViewModel(IncomeCatalog catalog,
            @Qualifier("incomeSheet") LedgerSheetService sheet, StudentAccountService students,
            ConfirmDialogs dialogs) {
        return new DetailViewModel(catalog, sheet, students, dialogs);
    }

    @Bean
    DetailViewModel expenseDetailViewModel(ExpenseCatalog catalog,
            @Qualifier("expenseSheet") LedgerSheetService sheet, ConfirmDialogs dialogs) {
        return new DetailViewModel(catalog, sheet, null, dialogs);
    }

    @Bean
    StudentsViewModel studentsViewModel(StudentAccountService accounts, ConfirmDialogs dialogs) {
        return new StudentsViewModel(accounts, dialogs);
    }
}

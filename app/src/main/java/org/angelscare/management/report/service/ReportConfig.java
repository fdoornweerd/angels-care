package org.angelscare.management.report.service;

import java.time.Clock;
import org.angelscare.management.accounts.service.ExpenseCatalog;
import org.angelscare.management.accounts.service.IncomeCatalog;
import org.angelscare.management.accounts.service.LedgerSheetService;
import org.angelscare.management.accounts.service.TermAccountsService;
import org.angelscare.management.calendar.service.CalendarService;
import org.angelscare.management.student.service.StudentAccountService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The year report: its data, gathered from both ledgers, and the PDF it is drawn as. */
@Configuration
class ReportConfig {

    @Bean
    YearReportService yearReportService(CalendarService calendar, TermAccountsService accounts,
            IncomeCatalog incomeCatalog, ExpenseCatalog expenseCatalog,
            @Qualifier("incomeSheet") LedgerSheetService incomeSheet,
            @Qualifier("expenseSheet") LedgerSheetService expenseSheet,
            StudentAccountService students, Clock clock) {
        return new YearReportService(calendar, accounts, incomeCatalog, expenseCatalog,
                incomeSheet, expenseSheet, students, clock);
    }

    @Bean
    YearReportPdf yearReportPdf() {
        return new YearReportPdf();
    }
}

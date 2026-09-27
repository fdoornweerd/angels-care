package org.angelscare.management.accounts.service;

import java.time.Clock;
import org.angelscare.management.accounts.model.Ledger;
import org.angelscare.management.accounts.repository.EntryRepository;
import org.angelscare.management.accounts.repository.PlanRepository;
import org.angelscare.management.calendar.service.CalendarService;
import org.angelscare.management.expense.service.ExpenseCatalogService;
import org.angelscare.management.income.service.IncomeCatalogService;
import org.angelscare.management.student.service.StudentAccountService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/** The accounts services: one ledger sheet per ledger, built from the same classes. */
@Configuration
class AccountsConfig {

    @Bean
    LedgerSheetService incomeSheet(JdbcTemplate jdbc, Clock clock, CalendarService calendar) {
        return sheet(Ledger.INCOME, jdbc, clock, calendar);
    }

    @Bean
    LedgerSheetService expenseSheet(JdbcTemplate jdbc, Clock clock, CalendarService calendar) {
        return sheet(Ledger.EXPENSE, jdbc, clock, calendar);
    }

    @Bean
    TermAccountsService termAccountsService(IncomeCatalog income, ExpenseCatalog expenses,
            @Qualifier("incomeSheet") LedgerSheetService incomeSheet,
            @Qualifier("expenseSheet") LedgerSheetService expenseSheet,
            StudentAccountService students, CalendarService calendar) {
        return new TermAccountsService(income, expenses, incomeSheet, expenseSheet, students,
                calendar);
    }

    @Bean
    SchoolYearSetupService schoolYearSetupService(CalendarService calendar,
            IncomeCatalogService income, ExpenseCatalogService expenses) {
        return new SchoolYearSetupService(calendar, income, expenses);
    }

    private static LedgerSheetService sheet(Ledger ledger, JdbcTemplate jdbc, Clock clock,
            CalendarService calendar) {
        return new LedgerSheetService(ledger, new EntryRepository(ledger, jdbc, clock),
                new PlanRepository(ledger, jdbc, clock), calendar);
    }
}

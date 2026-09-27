package org.angelscare.management.support;

import java.time.Clock;
import org.angelscare.management.accounts.model.Ledger;
import org.angelscare.management.accounts.repository.EntryRepository;
import org.angelscare.management.accounts.repository.PlanRepository;
import org.angelscare.management.accounts.service.Catalog;
import org.angelscare.management.accounts.service.ExpenseCatalog;
import org.angelscare.management.accounts.service.IncomeCatalog;
import org.angelscare.management.accounts.service.LedgerSheetService;
import org.angelscare.management.accounts.service.SchoolYearSetupService;
import org.angelscare.management.accounts.service.TermAccountsService;
import org.angelscare.management.calendar.repository.SchoolYearRepository;
import org.angelscare.management.calendar.service.CalendarService;
import org.angelscare.management.common.DeletionGuard;
import org.angelscare.management.expense.repository.ExpenseCategoryRepository;
import org.angelscare.management.expense.repository.ExpenseItemRepository;
import org.angelscare.management.expense.service.ExpenseCatalogService;
import org.angelscare.management.income.repository.IncomeCategoryRepository;
import org.angelscare.management.income.repository.IncomeItemRepository;
import org.angelscare.management.income.service.IncomeCatalogService;
import org.angelscare.management.student.repository.ClassFeeRepository;
import org.angelscare.management.student.repository.StudentRepository;
import org.angelscare.management.student.repository.StudentTermRepository;
import org.angelscare.management.student.service.StudentAccountService;
import org.angelscare.management.student.service.StudentService;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Every finance service, wired by hand the way Spring wires them, on one test database. Faster
 * than starting a Spring context for each test.
 */
public final class Finance {

    public final CalendarService calendar;
    public final StudentService students;
    public final IncomeCatalogService income;
    public final ExpenseCatalogService expenses;
    public final Catalog incomeCatalog;
    public final Catalog expenseCatalog;
    public final LedgerSheetService incomeSheet;
    public final LedgerSheetService expenseSheet;
    public final StudentAccountService studentAccounts;
    public final TermAccountsService accounts;
    public final SchoolYearSetupService setup;

    public Finance(JdbcTemplate jdbc, Clock clock) {
        DeletionGuard deletionGuard = new DeletionGuard(jdbc);
        calendar = new CalendarService(new SchoolYearRepository(jdbc, clock), deletionGuard);

        StudentRepository studentRepo = new StudentRepository(jdbc, clock);
        students = new StudentService(studentRepo, deletionGuard);

        income = new IncomeCatalogService(new IncomeCategoryRepository(jdbc, clock),
                new IncomeItemRepository(jdbc, clock), calendar, deletionGuard);
        expenses = new ExpenseCatalogService(new ExpenseCategoryRepository(jdbc, clock),
                new ExpenseItemRepository(jdbc, clock), calendar, deletionGuard);
        incomeCatalog = new IncomeCatalog(income);
        expenseCatalog = new ExpenseCatalog(expenses);
        incomeSheet = new LedgerSheetService(Ledger.INCOME,
                new EntryRepository(Ledger.INCOME, jdbc, clock),
                new PlanRepository(Ledger.INCOME, jdbc, clock), calendar);
        expenseSheet = new LedgerSheetService(Ledger.EXPENSE,
                new EntryRepository(Ledger.EXPENSE, jdbc, clock),
                new PlanRepository(Ledger.EXPENSE, jdbc, clock), calendar);

        studentAccounts = new StudentAccountService(new StudentTermRepository(jdbc, clock),
                new ClassFeeRepository(jdbc, clock), studentRepo, students, calendar);
        accounts = new TermAccountsService(incomeCatalog, expenseCatalog, incomeSheet,
                expenseSheet, studentAccounts, calendar);
        setup = new SchoolYearSetupService(calendar, income, expenses);
    }
}

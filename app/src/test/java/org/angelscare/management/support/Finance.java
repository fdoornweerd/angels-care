package org.angelscare.management.support;

import java.time.Clock;
import org.angelscare.management.calendar.repository.SchoolYearRepository;
import org.angelscare.management.calendar.service.CalendarService;
import org.angelscare.management.common.DeletionGuard;
import org.angelscare.management.expense.repository.ExpenseBudgetRepository;
import org.angelscare.management.expense.repository.ExpenseCategoryRepository;
import org.angelscare.management.expense.repository.ExpenseItemRepository;
import org.angelscare.management.expense.service.ExpenseBudgetService;
import org.angelscare.management.expense.service.ExpenseCatalogService;
import org.angelscare.management.income.repository.FeeAssignmentRepository;
import org.angelscare.management.income.repository.IncomeCategoryRepository;
import org.angelscare.management.income.repository.IncomeItemRepository;
import org.angelscare.management.income.service.FeeAssignmentService;
import org.angelscare.management.income.service.FeeCoverageGuard;
import org.angelscare.management.income.service.IncomeCatalogService;
import org.angelscare.management.student.repository.GroupMembershipRepository;
import org.angelscare.management.student.repository.StudentGroupRepository;
import org.angelscare.management.student.repository.StudentRepository;
import org.angelscare.management.student.service.GroupService;
import org.angelscare.management.student.service.StudentService;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Every finance service, wired by hand the way Spring wires them, on one test database. Faster
 * than starting a Spring context for each test.
 */
public final class Finance {

    public final CalendarService calendar;
    public final StudentService students;
    public final GroupService groups;
    public final IncomeCatalogService income;
    public final FeeAssignmentService fees;
    public final ExpenseCatalogService expenses;
    public final ExpenseBudgetService budgets;

    public Finance(JdbcTemplate jdbc, Clock clock) {
        DeletionGuard deletionGuard = new DeletionGuard(jdbc);

        SchoolYearRepository schoolYears = new SchoolYearRepository(jdbc, clock);
        calendar = new CalendarService(schoolYears, deletionGuard);

        StudentRepository studentRepo = new StudentRepository(jdbc, clock);
        StudentGroupRepository groupRepo = new StudentGroupRepository(jdbc, clock);
        GroupMembershipRepository membershipRepo = new GroupMembershipRepository(jdbc, clock);

        IncomeCategoryRepository incomeCategories = new IncomeCategoryRepository(jdbc, clock);
        IncomeItemRepository incomeItems = new IncomeItemRepository(jdbc, clock);
        FeeAssignmentRepository assignments = new FeeAssignmentRepository(jdbc, clock);
        FeeCoverageGuard coverageGuard = new FeeCoverageGuard(assignments, studentRepo,
                membershipRepo, incomeItems, groupRepo);

        students = new StudentService(studentRepo, coverageGuard, deletionGuard);
        groups = new GroupService(groupRepo, membershipRepo, studentRepo, calendar, coverageGuard,
                deletionGuard);
        income = new IncomeCatalogService(incomeCategories, incomeItems, deletionGuard);
        fees = new FeeAssignmentService(assignments, incomeItems, incomeCategories, studentRepo,
                groupRepo, membershipRepo, calendar, coverageGuard);

        ExpenseCategoryRepository expenseCategories = new ExpenseCategoryRepository(jdbc, clock);
        ExpenseItemRepository expenseItems = new ExpenseItemRepository(jdbc, clock);
        expenses = new ExpenseCatalogService(expenseCategories, expenseItems, deletionGuard);
        budgets = new ExpenseBudgetService(new ExpenseBudgetRepository(jdbc, clock), expenseItems,
                calendar);
    }
}

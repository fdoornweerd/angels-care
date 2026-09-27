package org.angelscare.management.accounts.service;

import java.util.ArrayList;
import java.util.List;
import org.angelscare.management.accounts.model.SummaryRow;
import org.angelscare.management.accounts.model.TermSummary;
import org.angelscare.management.accounts.model.YearTotals;
import org.angelscare.management.calendar.model.SchoolYear;
import org.angelscare.management.calendar.model.Term;
import org.angelscare.management.calendar.model.TermRef;
import org.angelscare.management.calendar.service.CalendarService;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.student.model.StudentsTotals;
import org.angelscare.management.student.service.StudentAccountService;
import org.springframework.transaction.annotation.Transactional;

/** The figures of pages 1 and 2. */
@Transactional(readOnly = true)
public class TermAccountsService {

    private final Catalog income;
    private final Catalog expenses;
    private final LedgerSheetService incomeSheet;
    private final LedgerSheetService expenseSheet;
    private final StudentAccountService students;
    private final CalendarService calendar;

    public TermAccountsService(Catalog income, Catalog expenses, LedgerSheetService incomeSheet,
            LedgerSheetService expenseSheet, StudentAccountService students,
            CalendarService calendar) {
        this.income = income;
        this.expenses = expenses;
        this.incomeSheet = incomeSheet;
        this.expenseSheet = expenseSheet;
        this.students = students;
        this.calendar = calendar;
    }

    /** Page 2: Students and the income categories, the expense categories, and their totals. */
    public TermSummary summary(TermRef term) {
        calendar.requireTerm(term);
        StudentsTotals studentTotals = students.totals(term);
        List<SummaryRow> incomeRows = new ArrayList<>();
        incomeRows.add(SummaryRow.students(studentTotals.expected(), studentTotals.paid()));
        incomeRows.addAll(categoryRows(income, incomeSheet, term));
        return new TermSummary(incomeRows, categoryRows(expenses, expenseSheet, term));
    }

    /** Page 1: a school year's totals over its three terms. */
    public YearTotals yearTotals(int year) {
        SchoolYear schoolYear = calendar.requireYear(year);
        Ugx collected = Ugx.ZERO;
        Ugx spent = Ugx.ZERO;
        for (Term term : schoolYear.terms()) {
            TermSummary summary = summary(term.ref());
            collected = collected.plus(summary.incomesCollected());
            spent = spent.plus(summary.expenditures());
        }
        return new YearTotals(collected, spent);
    }

    private static List<SummaryRow> categoryRows(Catalog catalog, LedgerSheetService sheet,
            TermRef term) {
        return catalog.categories(term.year()).stream()
                .map(category -> new SummaryRow(category.id(), category.name(),
                        sheet.plan(category.id(), term).orElse(null),
                        sheet.categoryTotals(category.id(), term)))
                .toList();
    }
}

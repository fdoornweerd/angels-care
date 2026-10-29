package org.angelscare.management.report.service;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.angelscare.management.accounts.model.ItemMonth;
import org.angelscare.management.accounts.service.Catalog;
import org.angelscare.management.accounts.service.LedgerSheetService;
import org.angelscare.management.accounts.service.TermAccountsService;
import org.angelscare.management.calendar.model.SchoolYear;
import org.angelscare.management.calendar.model.Term;
import org.angelscare.management.calendar.model.TermRef;
import org.angelscare.management.calendar.service.CalendarService;
import org.angelscare.management.common.SchoolTime;
import org.angelscare.management.report.model.CategorySheet;
import org.angelscare.management.report.model.CategorySheet.ItemRow;
import org.angelscare.management.report.model.ClassSheet;
import org.angelscare.management.report.model.TermReport;
import org.angelscare.management.report.model.YearReport;
import org.angelscare.management.student.model.SchoolClass;
import org.angelscare.management.student.model.StudentTermLine;
import org.angelscare.management.student.service.StudentAccountService;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gathers a school year's printed report (spec 005) from the same services the pages use. It only
 * reads: in particular it never opens a term for students the way page 5 does, so each term shows
 * the students already on it.
 */
@Transactional(readOnly = true)
public class YearReportService {

    private static final int MONTHS = 3;

    private final CalendarService calendar;
    private final TermAccountsService accounts;
    private final Catalog incomeCatalog;
    private final Catalog expenseCatalog;
    private final LedgerSheetService incomeSheet;
    private final LedgerSheetService expenseSheet;
    private final StudentAccountService students;
    private final Clock clock;

    public YearReportService(CalendarService calendar, TermAccountsService accounts,
            Catalog incomeCatalog, Catalog expenseCatalog, LedgerSheetService incomeSheet,
            LedgerSheetService expenseSheet, StudentAccountService students, Clock clock) {
        this.calendar = calendar;
        this.accounts = accounts;
        this.incomeCatalog = incomeCatalog;
        this.expenseCatalog = expenseCatalog;
        this.incomeSheet = incomeSheet;
        this.expenseSheet = expenseSheet;
        this.students = students;
        this.clock = clock;
    }

    /** The report of the school year starting in {@code year}, as printed now. */
    public YearReport report(int year) {
        SchoolYear schoolYear = calendar.requireYear(year);
        List<TermReport> terms = schoolYear.terms().stream().map(this::term).toList();
        return new YearReport(schoolYear, accounts.yearTotals(year), terms,
                ZonedDateTime.ofInstant(clock.instant(), SchoolTime.ZONE));
    }

    private TermReport term(Term term) {
        TermRef ref = term.ref();
        List<CategorySheet> incomes = sheets(incomeCatalog, incomeSheet, ref);
        List<CategorySheet> expenses = sheets(expenseCatalog, expenseSheet, ref);
        List<StudentTermLine> lines = students.lines(ref);
        boolean nothingRecorded = lines.isEmpty() && nothingIn(incomes) && nothingIn(expenses);
        return new TermReport(term, accounts.summary(ref), nothingRecorded, students.totals(ref),
                incomes, expenses, classes(lines, ref));
    }

    private static List<CategorySheet> sheets(Catalog catalog, LedgerSheetService sheet,
            TermRef term) {
        List<Map<String, ItemMonth>> months = new ArrayList<>();
        for (int month = 1; month <= MONTHS; month++) {
            months.add(sheet.monthEntries(term, month));
        }
        return catalog.categories(term.year()).stream()
                .map(category -> new CategorySheet(category.name(),
                        sheet.plan(category.id(), term).orElse(null),
                        sheet.categoryTotals(category.id(), term),
                        catalog.items(category.id()).stream()
                                .map(item -> itemRow(item, term, months))
                                .toList()))
                .toList();
    }

    private static ItemRow itemRow(Catalog.Item item, TermRef term,
            List<Map<String, ItemMonth>> months) {
        List<ItemMonth> entries = new ArrayList<>();
        for (int month = 1; month <= MONTHS; month++) {
            entries.add(months.get(month - 1).getOrDefault(item.id(),
                    new ItemMonth(item.id(), term, month, null, null)));
        }
        return new ItemRow(item.name(), item.unit(), entries);
    }

    /** No expected/budgeted amount, and no quantity or rate in any month. */
    private static boolean nothingIn(List<CategorySheet> sheets) {
        return sheets.stream().allMatch(sheet -> sheet.planned() == null
                && sheet.items().stream().flatMap(item -> item.months().stream())
                        .allMatch(month -> month.quantity() == null && month.rate() == null));
    }

    /** The classes with students on the term, youngest first, each with its ream in force. */
    private List<ClassSheet> classes(List<StudentTermLine> lines, TermRef term) {
        Map<SchoolClass, List<StudentTermLine>> byClass = new EnumMap<>(SchoolClass.class);
        for (StudentTermLine line : lines) {
            byClass.computeIfAbsent(line.schoolClass(), c -> new ArrayList<>()).add(line);
        }
        return byClass.entrySet().stream()
                .map(e -> new ClassSheet(e.getKey(),
                        students.classFee(e.getKey(), term).ream(), e.getValue()))
                .toList();
    }
}

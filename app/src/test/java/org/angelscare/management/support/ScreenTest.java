package org.angelscare.management.support;

import org.angelscare.management.accounts.ui.DetailViewModel;
import org.angelscare.management.accounts.ui.TermSummaryViewModel;
import org.angelscare.management.calendar.ui.SchoolYearsViewModel;
import org.angelscare.management.shell.ui.Navigator;
import org.angelscare.management.student.ui.StudentsViewModel;
import org.junit.jupiter.api.BeforeEach;

/**
 * A {@link FinanceTest} for view models: the real services on a temp database, a fixed clock and
 * scripted dialogs. Each factory builds a page's view model the way the app does.
 */
public abstract class ScreenTest extends FinanceTest {

    protected FakeDialogs dialogs;

    @BeforeEach
    void createDialogs() {
        dialogs = new FakeDialogs();
    }

    protected Navigator navigator() {
        return new Navigator(finance.calendar, clock);
    }

    protected SchoolYearsViewModel schoolYearsPage() {
        return new SchoolYearsViewModel(finance.setup, finance.calendar, finance.accounts, dialogs,
                clock);
    }

    protected TermSummaryViewModel termSummaryPage() {
        return new TermSummaryViewModel(finance.accounts);
    }

    protected DetailViewModel incomePage() {
        return new DetailViewModel(finance.incomeCatalog, finance.incomeSheet,
                finance.studentAccounts, dialogs);
    }

    protected DetailViewModel expensePage() {
        return new DetailViewModel(finance.expenseCatalog, finance.expenseSheet, null, dialogs);
    }

    protected StudentsViewModel studentsPage() {
        return new StudentsViewModel(finance.studentAccounts, dialogs);
    }
}

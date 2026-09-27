package org.angelscare.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import java.nio.file.Path;
import java.time.LocalDate;
import org.angelscare.management.accounts.service.SchoolYearSetupService;
import org.angelscare.management.accounts.service.TermAccountsService;
import org.angelscare.management.calendar.model.TermDates;
import org.angelscare.management.calendar.model.TermRef;
import org.angelscare.management.common.Quantity;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.common.ValidationException;
import org.angelscare.management.expense.service.ExpenseCatalogService;
import org.angelscare.management.shell.ui.Navigator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * The other tests wire the services by hand. This one goes through the real Spring context, so the
 * bean wiring (two ledger sheets, one per ledger) and the {@code @Transactional} proxies on SQLite
 * are exercised too.
 */
class FinanceWiringTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("the accounts services and pages' navigator work through the Spring context")
    void servicesWorkThroughSpring() {
        Diagnostics.startLogFile(tempDir.resolve("logs"));
        StartupResult result = Bootstrap.start(tempDir.resolve("AngelsCareData"));
        if (result instanceof StartupResult.Failed failed) {
            fail("start-up failed: " + failed.message(), failed.cause());
        }
        try (ConfigurableApplicationContext context = ((StartupResult.Started) result).context()) {
            SchoolYearSetupService setup = context.getBean(SchoolYearSetupService.class);
            ExpenseCatalogService expenses = context.getBean(ExpenseCatalogService.class);
            TermAccountsService accounts = context.getBean(TermAccountsService.class);
            var expenseSheet = context.getBean("expenseSheet",
                    org.angelscare.management.accounts.service.LedgerSheetService.class);

            setup.createYear(2026, dates("2026-02-02", "2026-04-24"),
                    dates("2026-05-18", "2026-08-14"), dates("2026-09-07", "2026-12-04"), false);
            var feeding = expenses.createCategory(2026, "Feeding");
            var maize = expenses.createItem(feeding.id(), "Maize flour", "kg");
            expenseSheet.setQuantity(maize.id(), TermRef.of(2026, 1), 1, Quantity.parse("12.5"));
            expenseSheet.setRate(maize.id(), TermRef.of(2026, 1), 1, Ugx.of(3_500));

            assertThat(accounts.summary(TermRef.of(2026, 1)).expenditures()).isEqualTo(Ugx.of(43_750));
            assertThat(context.getBean(Navigator.class)).isNotNull();
            // A rejected write leaves the context usable.
            assertThatThrownBy(() -> expenses.createCategory(2026, "feeding"))
                    .isInstanceOf(ValidationException.class);
            assertThat(expenses.listCategories(2026)).hasSize(1);
        }
    }

    private static TermDates dates(String start, String end) {
        return TermDates.of(LocalDate.parse(start), LocalDate.parse(end));
    }
}

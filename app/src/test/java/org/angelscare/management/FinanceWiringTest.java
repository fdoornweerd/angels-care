package org.angelscare.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import java.nio.file.Path;
import java.time.LocalDate;
import org.angelscare.management.calendar.model.TermDates;
import org.angelscare.management.calendar.model.TermRange;
import org.angelscare.management.calendar.model.TermRef;
import org.angelscare.management.calendar.service.CalendarService;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.common.ValidationException;
import org.angelscare.management.income.model.BillingFrequency;
import org.angelscare.management.income.model.FeeTarget;
import org.angelscare.management.income.service.FeeAssignmentService;
import org.angelscare.management.income.service.IncomeCatalogService;
import org.angelscare.management.student.model.Residency;
import org.angelscare.management.student.model.SchoolClass;
import org.angelscare.management.student.model.StudentDetails;
import org.angelscare.management.student.service.StudentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * The other finance tests wire the services by hand. This one goes through the real Spring context,
 * so the bean wiring and the {@code @Transactional} proxies (including read-only ones on SQLite)
 * are exercised too.
 */
class FinanceWiringTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("the finance services work through the Spring context")
    void servicesWorkThroughSpring() {
        Diagnostics.startLogFile(tempDir.resolve("logs"));
        StartupResult result = Bootstrap.start(tempDir.resolve("AngelsCareData"));
        if (result instanceof StartupResult.Failed failed) {
            fail("start-up failed: " + failed.message(), failed.cause());
        }
        try (ConfigurableApplicationContext context = ((StartupResult.Started) result).context()) {
            CalendarService calendar = context.getBean(CalendarService.class);
            StudentService students = context.getBean(StudentService.class);
            IncomeCatalogService income = context.getBean(IncomeCatalogService.class);
            FeeAssignmentService fees = context.getBean(FeeAssignmentService.class);

            calendar.createYear(2026, dates("2026-02-02", "2026-04-24"),
                    dates("2026-05-18", "2026-08-14"), dates("2026-09-07", "2026-12-04"));
            var amina = students.create(
                    StudentDetails.of("Amina", "Nakato", SchoolClass.P7, Residency.NATIONAL));
            var tuition = income.createItem(income.createCategory("Student Fees").id(), "Tuition");
            fees.assign(tuition.id(), FeeTarget.schoolClass(SchoolClass.P7), Ugx.of(300_000),
                    BillingFrequency.PER_TERM, TermRange.from(TermRef.of(2026, 1)));

            assertThat(fees.feesFor(amina.id(), TermRef.of(2026, 1))).hasSize(1);
            assertThat(calendar.listYears()).hasSize(1);
            // A rejected write leaves the context usable.
            assertThatThrownBy(() -> income.createCategory("student fees"))
                    .isInstanceOf(ValidationException.class);
            assertThat(income.listCategories()).hasSize(1);
        }
    }

    private static TermDates dates(String start, String end) {
        return TermDates.of(LocalDate.parse(start), LocalDate.parse(end));
    }
}

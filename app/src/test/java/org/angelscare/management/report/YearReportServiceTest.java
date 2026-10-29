package org.angelscare.management.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

import java.io.OutputStream;
import java.time.Duration;
import java.util.List;
import org.angelscare.management.calendar.ui.SchoolYearsViewModel;
import org.angelscare.management.common.MonthlyAmounts;
import org.angelscare.management.common.Quantity;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.report.model.CategorySheet;
import org.angelscare.management.report.model.ClassSheet;
import org.angelscare.management.report.model.TermReport;
import org.angelscare.management.report.model.YearReport;
import org.angelscare.management.report.service.YearReportPdf;
import org.angelscare.management.student.model.SchoolClass;
import org.angelscare.management.student.model.StudentTermLine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class YearReportServiceTest extends YearReportFixture {

    @Test
    @DisplayName("AC-7: making the report changes nothing, and adds no student to a term")
    void readOnly() {
        createFullYear();
        var before = snapshot();
        clock.advance(Duration.ofHours(1));

        new YearReportPdf()
                .write(yearReports().report(2026), OutputStream.nullOutputStream());

        assertThat(snapshot()).isEqualTo(before);
        assertThat(finance.studentAccounts.lines(t(2026, 2))).isEmpty();
        assertThat(finance.studentAccounts.lines(t(2026, 1)))
                .extracting(StudentTermLine::studentId).doesNotContain(dan.id());
    }

    @Test
    @DisplayName("AC-9: the overview and term summaries equal pages 1 and 2")
    void summariesMatchPages() {
        createFullYear();
        SchoolYearsViewModel page1 = schoolYearsPage();
        page1.refresh();

        YearReport report = yearReports().report(2026);

        assertThat(report.totals()).isEqualTo(page1.rows().getFirst().totals());
        assertThat(report.totals().incomeCollected()).isEqualTo(Ugx.of(302_500));
        assertThat(report.totals().expenditure()).isEqualTo(Ugx.of(175_000));
        for (TermReport term : report.terms()) {
            assertThat(term.summary()).isEqualTo(finance.accounts.summary(term.term().ref()));
        }
        assertThat(report.terms()).extracting(term -> term.term().ref().number())
                .containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("AC-10: every category and item, with each month's quantity, rate and amount")
    void detailedSheets() {
        createFullYear();

        TermReport t1 = yearReports().report(2026).terms().getFirst();

        assertThat(t1.incomes()).extracting(CategorySheet::name)
                .containsExactly("Donations", "Sales");
        CategorySheet donations = t1.incomes().getFirst();
        assertThat(donations.planned()).isEqualTo(Ugx.of(600_000));
        assertThat(donations.totals())
                .isEqualTo(new MonthlyAmounts(Ugx.of(200_000), Ugx.ZERO, Ugx.of(2_500)));
        CategorySheet.ItemRow church = donations.items().getFirst();
        assertThat(church.name()).isEqualTo("Church support");
        assertThat(church.unit()).isEqualTo("months");
        assertThat(church.months()).extracting(m -> m.quantity(), m -> m.rate())
                .containsExactly(
                        tuple(Quantity.parse("1"), Ugx.of(200_000)),
                        tuple(null, null),
                        tuple(Quantity.parse("2.5"), Ugx.of(1_000)));
        assertThat(church.months().get(1).amount()).isEmpty();
        assertThat(church.termTotal()).isEqualTo(Ugx.of(202_500));

        CategorySheet feeding = t1.expenses().getFirst();
        assertThat(feeding.planned()).isEqualTo(Ugx.of(500_000));
        assertThat(feeding.totals().total()).isEqualTo(Ugx.of(175_000));
        assertThat(t1.students()).isEqualTo(finance.studentAccounts.totals(t(2026, 1)));
    }

    @Test
    @DisplayName("AC-11: classes with their ream and students, Left included, totals as on page 5")
    void studentSheets() {
        createFullYear();

        TermReport t1 = yearReports().report(2026).terms().getFirst();

        assertThat(t1.classes()).extracting(ClassSheet::schoolClass)
                .containsExactly(SchoolClass.BABY, SchoolClass.P4);
        ClassSheet p4 = t1.classes().get(1);
        assertThat(p4.ream()).isEqualTo(Ugx.of(10_000));
        assertThat(p4.lines()).extracting(StudentTermLine::shownName)
                .containsExactly("Amina Nakato", "Ben Okello (Left)");
        ClassSheet.Totals totals = p4.totals();
        assertThat(totals.amount()).isEqualTo(Ugx.of(10_250_000));
        assertThat(totals.ream()).isEqualTo(Ugx.of(20_000));
        assertThat(totals.total()).isEqualTo(Ugx.of(10_270_000));
        assertThat(totals.paid().total()).isEqualTo(Ugx.of(100_000));
        assertThat(totals.balance()).isEqualTo(Ugx.of(10_170_000));

        List<StudentTermLine> everyone = t1.classes().stream()
                .flatMap(c -> c.lines().stream()).toList();
        ClassSheet.Totals term = ClassSheet.Totals.of(everyone);
        assertThat(term.total()).isEqualTo(t1.students().expected());
        assertThat(term.paid()).isEqualTo(t1.students().paid());
    }

    @Test
    @DisplayName("AC-12: a class with no students is left out; a category without items is kept")
    void emptyClassesAndCategories() {
        createFullYear();

        TermReport t1 = yearReports().report(2026).terms().getFirst();

        assertThat(t1.classes()).extracting(ClassSheet::schoolClass)
                .doesNotContain(SchoolClass.P1, SchoolClass.MIDDLE);
        assertThat(t1.incomes().get(1).name()).isEqualTo("Sales");
        assertThat(t1.incomes().get(1).items()).isEmpty();
    }

    @Test
    @DisplayName("AC-13: only terms without students, entries or plans count as nothing recorded")
    void nothingRecorded() {
        createFullYear();

        YearReport report = yearReports().report(2026);

        assertThat(report.terms()).extracting(TermReport::nothingRecorded)
                .containsExactly(false, true, true);
    }

    @Test
    @DisplayName("AC-13: a term with only a budget, or only a quantity, has something recorded")
    void planOrQuantityAlone() {
        createYear(2026);
        finance.expenseSheet.setPlan(expenseCategory(2026, "Feeding").id(), t(2026, 2), Ugx.ZERO);
        String maize = expenseItem(2026, "Feeding", "Maize flour", "kg").id();
        finance.expenseSheet.setQuantity(maize, t(2026, 3), 2, Quantity.parse("3"));

        YearReport report = yearReports().report(2026);

        assertThat(report.terms()).extracting(TermReport::nothingRecorded)
                .containsExactly(true, false, false);
    }

    @Test
    @DisplayName("AC-14: a year with nothing at all still makes a report")
    void emptyYear() {
        createYear(2026);

        YearReport report = yearReports().report(2026);

        assertThat(report.terms()).allMatch(TermReport::nothingRecorded);
        assertThat(report.totals().surplus()).isEqualTo(Ugx.ZERO);
    }
}

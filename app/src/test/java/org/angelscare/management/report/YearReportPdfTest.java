package org.angelscare.management.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.lowagie.text.PageSize;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.angelscare.management.report.service.YearReportPdf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class YearReportPdfTest extends YearReportFixture {

    /** The text of each page, in order. */
    private List<String> pages(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            List<String> pages = new ArrayList<>();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                pages.add(extractor.getTextFromPage(page));
            }
            return pages;
        } finally {
            reader.close();
        }
    }

    /**
     * All pages' text without whitespace: the text extractor runs table cells together or splits
     * them over lines, so expectations are compared without spaces too (see {@link #squash}).
     */
    private String text(byte[] pdf) throws Exception {
        return squash(String.join("", pages(pdf)));
    }

    private static String squash(String text) {
        return text.replaceAll("\\s+", "");
    }

    private byte[] pdf(int year) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new YearReportPdf().write(yearReports().report(year), out);
        return out.toByteArray();
    }

    /** The first page whose text contains {@code text}, counting from 1; 0 if none. */
    private static int pageOf(List<String> pages, String text) {
        for (int i = 0; i < pages.size(); i++) {
            if (pages.get(i).contains(text)) {
                return i + 1;
            }
        }
        return 0;
    }

    @Test
    @DisplayName("AC-8: A4 landscape, with the year on top and print time and page x of y below")
    void pageLayout() throws Exception {
        createFullYear();

        byte[] pdf = pdf(2026);

        PdfReader reader = new PdfReader(pdf);
        Rectangle size = reader.getPageSizeWithRotation(1);
        int rotation = reader.getPageRotation(1);
        reader.close();
        assertThat(rotation).isZero();
        assertThat(size.getWidth()).isEqualTo(PageSize.A4.getHeight());
        assertThat(size.getHeight()).isEqualTo(PageSize.A4.getWidth());
        List<String> pages = pages(pdf);
        // T0 is 08:00 UTC, 11:00 in Kampala.
        for (int page = 1; page <= pages.size(); page++) {
            assertThat(squash(pages.get(page - 1)))
                    .contains(squash("Angels Care School · Report 2026-2027"))
                    .contains(squash("Printed 02/03/2026 11:00 · Page " + page + " of " + pages.size()));
        }
    }

    @Test
    @DisplayName("AC-16: overview, term summaries, detailed incomes, detailed expenses, students")
    void sectionOrder() throws Exception {
        createFullYear();

        List<String> pages = pages(pdf(2026)).stream().map(YearReportPdfTest::squash).toList();

        int overview = pageOf(pages, squash("Report for school year 2026-2027"));
        int summaries = pageOf(pages, squash("Term summaries"));
        int term2 = pageOf(pages, squash("2026-2027 · Term 2"));
        int term3 = pageOf(pages, squash("2026-2027 · Term 3"));
        int incomes = pageOf(pages, squash("Detailed incomes"));
        int expenses = pageOf(pages, squash("Detailed expenses"));
        int students = pageOf(pages, "Nursery");
        assertThat(overview).isEqualTo(1);
        assertThat(List.of(overview, summaries, term2, term3, incomes, expenses, students))
                .isSorted().doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("AC-9/AC-15: amounts as on screen: separators, no decimals, 0, credit, deficit")
    void amounts() throws Exception {
        createFullYear();

        String text = text(pdf(2026));

        assertThat(text).contains(squash("Income collected 302,500"))
                .contains(squash("Expenditure 175,000"))
                .contains(squash("Surplus 127,500"))
                .contains("10,000,000")
                .contains(squash("Cara Akello Day 0 -20,000"))
                .doesNotContain(".00");
        // Term 2's income table: the Students row and the totals row are all zeros.
        assertThat(text).contains(squash("Students 0 0 0 0 0 Donations"));
    }

    @Test
    @DisplayName("AC-9: a deficit is negative and labelled Deficit")
    void deficit() throws Exception {
        createYear(2026);
        record(finance.expenseSheet, expenseItem(2026, "Feeding", "Maize", "kg").id(),
                t(2026, 1), 1, "100", 5_000);

        assertThat(text(pdf(2026))).contains(squash("Deficit -500,000"));
    }

    @Test
    @DisplayName("AC-10/AC-12: items with each month, blanks blank, and No items. for an empty category")
    void detailTables() throws Exception {
        createFullYear();

        assertThat(text(pdf(2026)))
                .contains(squash("Church support months 1 200,000 200,000 2.5 1,000 2,500 202,500"))
                .contains(squash("Total 200,000 0 2,500 202,500"))
                .contains(squash("Expected: 600,000"))
                .contains(squash("Budgeted: 500,000"))
                .contains(squash("Sales No items."));
    }

    @Test
    @DisplayName("AC-11: students by class with ream, the Left student marked, and totals rows")
    void studentTables() throws Exception {
        createFullYear();

        assertThat(text(pdf(2026)))
                .contains(squash("P4 · Ream 10,000"))
                .contains(squash("Ben Okello (Left) Day 250,000 0 10,000 260,000 260,000 Moved to Gulu"))
                .contains(squash("Total P4 10,250,000 0 20,000 10,270,000 100,000 0 0 10,170,000"))
                .contains(squash("Whole term 10,250,000 -20,000 25,000 10,255,000"))
                .doesNotContain("DanMugisha");
    }

    @Test
    @DisplayName("AC-13: terms with nothing recorded get only their summary and the note")
    void emptyTerms() throws Exception {
        createFullYear();

        List<String> pages = pages(pdf(2026)).stream().map(YearReportPdfTest::squash).toList();
        String text = String.join("", pages);

        assertThat(pages.stream().filter(p -> p.contains(squash(YearReportPdf.NOTHING_RECORDED))))
                .hasSize(2);
        // Term 2 and 3 headings appear once each: on their summary page only.
        assertThat(text.split(squash("2026-2027 · Term 2"), -1)).hasSize(2);
        assertThat(text.split(squash("2026-2027 · Term 3"), -1)).hasSize(2);
    }

    @Test
    @DisplayName("AC-14: a year with nothing recorded is the overview and three summaries")
    void emptyYear() throws Exception {
        createYear(2026);

        List<String> pages = pages(pdf(2026));

        assertThat(pages).hasSize(4);
        assertThat(squash(String.join("", pages))).doesNotContain("Detailedincomes")
                .doesNotContain("Nursery");
    }
}

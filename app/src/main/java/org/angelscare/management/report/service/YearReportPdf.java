package org.angelscare.management.report.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfTemplate;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.OutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import org.angelscare.management.accounts.model.ItemMonth;
import org.angelscare.management.accounts.model.SummaryRow;
import org.angelscare.management.accounts.model.TermSummary;
import org.angelscare.management.calendar.model.Term;
import org.angelscare.management.common.Ugx;
import org.angelscare.management.report.model.CategorySheet;
import org.angelscare.management.report.model.CategorySheet.ItemRow;
import org.angelscare.management.report.model.ClassSheet;
import org.angelscare.management.report.model.TermReport;
import org.angelscare.management.report.model.YearReport;
import org.angelscare.management.student.model.Boarding;
import org.angelscare.management.student.model.Level;
import org.angelscare.management.student.model.StudentTermLine;

/**
 * Draws a {@link YearReport} as an A4 landscape PDF (spec 005), section by section: the year
 * overview, the term summaries, the detailed incomes, the detailed expenses, and the students.
 * Amounts are whole shillings with thousands separators, as on screen.
 */
public class YearReportPdf {

    public static final String NOTHING_RECORDED = "Nothing recorded for this term yet.";

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final DateTimeFormatter PRINTED = DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm");
    private static final Color HEADING_FILL = new Color(0xE8, 0xEC, 0xF2);
    private static final Color TOTAL_FILL = new Color(0xF4, 0xF4, 0xF4);
    private static final float MARGIN = 36;
    /**
     * A4 landscape as a wide page, not {@code PageSize.A4.rotate()}: that writes a portrait page
     * with a /Rotate flag, which some printers and previews ignore, cutting the page off.
     */
    private static final Rectangle A4_LANDSCAPE =
            new Rectangle(PageSize.A4.getHeight(), PageSize.A4.getWidth());

    private static final Font TITLE = new Font(Font.HELVETICA, 18, Font.BOLD);
    private static final Font SECTION = new Font(Font.HELVETICA, 14, Font.BOLD);
    private static final Font SUBSECTION = new Font(Font.HELVETICA, 11, Font.BOLD);
    private static final Font TEXT = new Font(Font.HELVETICA, 9);
    private static final Font NOTE = new Font(Font.HELVETICA, 9, Font.ITALIC);
    private static final Font CELL = new Font(Font.HELVETICA, 8);
    private static final Font CELL_BOLD = new Font(Font.HELVETICA, 8, Font.BOLD);

    /** Writes the report to {@code out}; the stream is left open. */
    public void write(YearReport report, OutputStream out) {
        Document document = new Document(A4_LANDSCAPE, MARGIN, MARGIN, MARGIN + 14, MARGIN + 10);
        try {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            writer.setCloseStream(false);
            writer.setPageEvent(new HeaderAndFooter(report));
            document.open();
            overview(document, report);
            termSummaries(document, report);
            details(document, report, "Detailed incomes", "Expected", true);
            details(document, report, "Detailed expenses", "Budgeted", false);
            students(document, report);
            document.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("The report could not be drawn", e);
        }
    }

    // ---- sections ----

    private static void overview(Document document, YearReport report) {
        add(document, new Paragraph("Angels Care School", TITLE));
        add(document, spaced(new Paragraph("Report for school year " + report.year().label(), SECTION), 12));

        PdfPTable terms = table(new float[] {2, 3, 3});
        headings(terms, "Term", "Starts", "Ends");
        for (Term term : report.year().terms()) {
            text(terms, "Term " + term.ref().number());
            text(terms, DATE.format(term.dates().start()));
            text(terms, DATE.format(term.dates().end()));
        }
        terms.setWidthPercentage(50);
        terms.setHorizontalAlignment(Element.ALIGN_LEFT);
        add(document, terms);

        PdfPTable totals = table(new float[] {3, 2});
        headings(totals, "The whole year", "UGX");
        text(totals, "Income collected");
        money(totals, report.totals().incomeCollected());
        text(totals, "Expenditure");
        money(totals, report.totals().expenditure());
        Ugx surplus = report.totals().surplus();
        totalText(totals, surplusLabel(surplus));
        totalMoney(totals, surplus);
        totals.setWidthPercentage(50);
        totals.setHorizontalAlignment(Element.ALIGN_LEFT);
        totals.setSpacingBefore(16);
        add(document, totals);
    }

    private static void termSummaries(Document document, YearReport report) {
        for (TermReport term : report.terms()) {
            document.newPage();
            if (term == report.terms().getFirst()) {
                add(document, new Paragraph("Term summaries", SECTION));
            }
            add(document, termHeading(report, term));
            TermSummary summary = term.summary();
            add(document, summaryTable("Income", "Expected", summary.income(), summary.incomeTotals()));
            add(document, summaryTable("Expense", "Budgeted", summary.expense(), summary.expenseTotals()));

            PdfPTable result = table(new float[] {3, 2});
            headings(result, "Summary", "UGX");
            text(result, "Incomes collected");
            money(result, summary.incomesCollected());
            text(result, "Expenditures");
            money(result, summary.expenditures());
            totalText(result, surplusLabel(summary.surplus()));
            totalMoney(result, summary.surplus());
            result.setWidthPercentage(40);
            result.setHorizontalAlignment(Element.ALIGN_LEFT);
            add(document, result);
            if (term.nothingRecorded()) {
                add(document, spaced(new Paragraph(NOTHING_RECORDED, NOTE), 6));
            }
        }
    }

    private static void details(Document document, YearReport report, String title,
            String plannedLabel, boolean income) {
        boolean first = true;
        for (TermReport term : report.terms()) {
            if (term.nothingRecorded()) {
                continue;
            }
            document.newPage();
            if (first) {
                add(document, new Paragraph(title, SECTION));
                first = false;
            }
            add(document, termHeading(report, term));
            if (income) {
                PdfPTable students = table(new float[] {4, 2, 2});
                headings(students, "Students", "Amount", "Total");
                text(students, "Students");
                money(students, term.students().expected());
                money(students, term.students().total());
                students.setWidthPercentage(50);
                students.setHorizontalAlignment(Element.ALIGN_LEFT);
                add(document, students);
            }
            List<CategorySheet> sheets = income ? term.incomes() : term.expenses();
            if (sheets.isEmpty()) {
                add(document, new Paragraph("No " + (income ? "income" : "expense")
                        + " categories.", NOTE));
            }
            for (CategorySheet sheet : sheets) {
                if (sheet.items().isEmpty()) {
                    add(document, spaced(new Paragraph(sheet.name(), SUBSECTION), 8));
                    add(document, new Paragraph("No items.", NOTE));
                } else {
                    add(document, itemTable(sheet));
                }
                add(document, new Paragraph(plannedLabel + ": "
                        + (sheet.planned() == null ? "not set" : format(sheet.planned())), TEXT));
            }
        }
    }

    private static void students(Document document, YearReport report) {
        boolean first = true;
        for (TermReport term : report.terms()) {
            if (term.nothingRecorded()) {
                continue;
            }
            document.newPage();
            if (first) {
                add(document, new Paragraph("Students", SECTION));
                first = false;
            }
            add(document, termHeading(report, term));
            if (term.classes().isEmpty()) {
                add(document, new Paragraph("No students on this term.", NOTE));
                continue;
            }
            for (Level level : Level.values()) {
                List<ClassSheet> classes = term.classes().stream()
                        .filter(c -> c.schoolClass().level() == level).toList();
                if (classes.isEmpty()) {
                    continue;
                }
                add(document, spaced(new Paragraph(level == Level.NURSERY ? "Nursery" : "Primary",
                        SUBSECTION), 8));
                for (ClassSheet sheet : classes) {
                    add(document, classTable(sheet));
                }
            }
            List<StudentTermLine> everyone = term.classes().stream()
                    .flatMap(c -> c.lines().stream()).toList();
            PdfPTable totals = studentColumns();
            totalsRow(totals, "Whole term", ClassSheet.Totals.of(everyone));
            totals.setSpacingBefore(10);
            add(document, totals);
        }
    }

    // ---- tables ----

    private static PdfPTable summaryTable(String name, String plannedLabel, List<SummaryRow> rows,
            SummaryRow totals) {
        PdfPTable table = table(new float[] {4, 2, 2, 2, 2, 2});
        headings(table, name, plannedLabel, "1st month", "2nd month", "3rd month", "Total");
        table.setHeaderRows(1);
        for (SummaryRow row : rows) {
            text(table, row.name());
            money(table, row.planned());
            for (int month = 1; month <= 3; month++) {
                money(table, row.actual().month(month));
            }
            money(table, row.total());
        }
        totalText(table, "Total");
        totalMoney(table, totals.planned());
        for (int month = 1; month <= 3; month++) {
            totalMoney(table, totals.actual().month(month));
        }
        totalMoney(table, totals.total());
        return table;
    }

    /** Item, Units, then Quantity, Rate and Amount for each month, then the item's term total. */
    private static PdfPTable itemTable(CategorySheet sheet) {
        PdfPTable table = table(new float[] {4.5f, 1.6f, 1.3f, 1.6f, 1.8f, 1.3f, 1.6f, 1.8f,
                1.3f, 1.6f, 1.8f, 2});
        title(table, sheet.name(), SUBSECTION);
        table.addCell(heading("Item", 1, 2));
        table.addCell(heading("Units", 1, 2));
        for (String month : List.of("1st month", "2nd month", "3rd month")) {
            table.addCell(heading(month, 3, 1));
        }
        table.addCell(heading("Term total", 1, 2));
        for (int month = 1; month <= 3; month++) {
            headings(table, "Quantity", "Rate", "Amount");
        }
        table.setHeaderRows(3);
        for (ItemRow item : sheet.items()) {
            text(table, item.name());
            text(table, item.unit());
            for (ItemMonth month : item.months()) {
                number(table, month.quantity() == null ? "" : month.quantity().format(), CELL, null);
                money(table, month.rate());
                money(table, month.amount().orElse(null));
            }
            money(table, item.termTotal());
        }
        totalText(table, "Total");
        totalText(table, "");
        for (int month = 1; month <= 3; month++) {
            totalText(table, "");
            totalText(table, "");
            totalMoney(table, sheet.totals().month(month));
        }
        totalMoney(table, sheet.totals().total());
        return table;
    }

    private static PdfPTable classTable(ClassSheet sheet) {
        PdfPTable table = studentColumns();
        title(table, sheet.schoolClass().label() + " · Ream " + format(sheet.ream()), TEXT);
        headings(table, "Name", "Day/Boarding", "Amount", "Debt", "Ream", "Total", "1st Month",
                "2nd Month", "3rd Month", "Balance", "Remarks");
        table.setHeaderRows(2);
        for (StudentTermLine line : sheet.lines()) {
            text(table, line.shownName());
            text(table, line.boarding() == Boarding.BOARDING ? "Boarding" : "Day");
            money(table, line.amount());
            money(table, line.debt());
            money(table, line.ream());
            money(table, line.total());
            money(table, line.paid1());
            money(table, line.paid2());
            money(table, line.paid3());
            money(table, line.balance());
            text(table, line.remarks() == null ? "" : line.remarks());
        }
        totalsRow(table, "Total " + sheet.schoolClass().label(), sheet.totals());
        return table;
    }

    private static PdfPTable studentColumns() {
        return table(new float[] {4, 2.3f, 1.8f, 1.6f, 1.4f, 1.8f, 1.6f, 1.6f, 1.6f, 1.8f, 4.5f});
    }

    private static void totalsRow(PdfPTable table, String label, ClassSheet.Totals totals) {
        totalText(table, label);
        totalText(table, "");
        for (Ugx amount : List.of(totals.amount(), totals.debt(), totals.ream(), totals.total(),
                totals.paid().first(), totals.paid().second(), totals.paid().third(),
                totals.balance())) {
            totalMoney(table, amount);
        }
        totalText(table, "");
    }

    // ---- cells ----

    private static PdfPTable table(float[] widths) {
        PdfPTable table = new PdfPTable(widths);
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);
        table.setSpacingAfter(8);
        table.setSplitLate(false);
        // A table that fits on a page starts on the next one rather than splitting; a longer one
        // still splits, with its headings repeated.
        table.setKeepTogether(true);
        return table;
    }

    /**
     * A heading across the whole table, as its first header row: it moves with the table to a new
     * page and is repeated when the table continues on the next one.
     */
    private static void title(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setColspan(table.getNumberOfColumns());
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPaddingLeft(0);
        cell.setPaddingTop(6);
        cell.setPaddingBottom(4);
        table.addCell(cell);
    }

    private static void headings(PdfPTable table, String... names) {
        for (String name : names) {
            table.addCell(heading(name, 1, 1));
        }
    }

    private static PdfPCell heading(String name, int colspan, int rowspan) {
        PdfPCell cell = cell(name, CELL_BOLD, HEADING_FILL);
        cell.setColspan(colspan);
        cell.setRowspan(rowspan);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    private static void text(PdfPTable table, String text) {
        table.addCell(cell(text, CELL, null));
    }

    private static void totalText(PdfPTable table, String text) {
        table.addCell(cell(text, CELL_BOLD, TOTAL_FILL));
    }

    private static void money(PdfPTable table, Ugx amount) {
        number(table, amount == null ? "" : format(amount), CELL, null);
    }

    private static void totalMoney(PdfPTable table, Ugx amount) {
        number(table, amount == null ? "" : format(amount), CELL_BOLD, TOTAL_FILL);
    }

    private static void number(PdfPTable table, String text, Font font, Color fill) {
        PdfPCell cell = cell(text, font, fill);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(cell);
    }

    private static PdfPCell cell(String text, Font font, Color fill) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(3);
        cell.setBorderColor(Color.GRAY);
        if (fill != null) {
            cell.setBackgroundColor(fill);
        }
        return cell;
    }

    // ---- text ----

    /** {@code 1,250,000} or {@code -20,000}: whole shillings, separators fixed to commas. */
    static String format(Ugx amount) {
        return String.format(Locale.US, "%,d", amount.shillings());
    }

    private static String surplusLabel(Ugx surplus) {
        return surplus.isNegative() ? "Deficit" : "Surplus";
    }

    private static Paragraph termHeading(YearReport report, TermReport term) {
        Term t = term.term();
        return spaced(new Paragraph(report.year().label() + " · Term " + t.ref().number() + "  ("
                + DATE.format(t.dates().start()) + " – " + DATE.format(t.dates().end()) + ")",
                SUBSECTION), 6);
    }

    private static Paragraph spaced(Paragraph paragraph, float before) {
        paragraph.setSpacingBefore(before);
        paragraph.setSpacingAfter(2);
        return paragraph;
    }

    private static void add(Document document, Element element) {
        try {
            document.add(element);
        } catch (DocumentException e) {
            throw new IllegalStateException("The report could not be drawn", e);
        }
    }

    /** "Angels Care School · Report 2026-2027" on top; "Printed … · Page x of y" at the bottom. */
    private static final class HeaderAndFooter extends PdfPageEventHelper {

        private static final float FOOTER_SIZE = 8;

        private final String header;
        private final String printed;
        private PdfTemplate pageCount;
        private BaseFont font;

        HeaderAndFooter(YearReport report) {
            header = "Angels Care School · Report " + report.year().label();
            printed = "Printed " + PRINTED.format(report.printedAt()) + " · Page ";
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            pageCount = writer.getDirectContent().createTemplate(40, 12);
            try {
                font = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (Exception e) {
                throw new IllegalStateException("No Helvetica for the footer", e);
            }
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte canvas = writer.getDirectContent();
            Rectangle page = document.getPageSize();
            ColumnText.showTextAligned(canvas, Element.ALIGN_LEFT,
                    new Phrase(header, new Font(Font.HELVETICA, FOOTER_SIZE, Font.BOLD)),
                    MARGIN, page.getHeight() - MARGIN + 6, 0);

            String footer = printed + writer.getPageNumber() + " of ";
            float width = font.getWidthPoint(footer, FOOTER_SIZE);
            float x = page.getWidth() - MARGIN - width - font.getWidthPoint("000", FOOTER_SIZE);
            float y = MARGIN - 14;
            canvas.beginText();
            canvas.setFontAndSize(font, FOOTER_SIZE);
            canvas.setTextMatrix(x, y);
            canvas.showText(footer);
            canvas.endText();
            canvas.addTemplate(pageCount, x + width, y);
        }

        @Override
        public void onCloseDocument(PdfWriter writer, Document document) {
            pageCount.beginText();
            pageCount.setFontAndSize(font, FOOTER_SIZE);
            pageCount.setTextMatrix(0, 0);
            pageCount.showText(String.valueOf(writer.getPageNumber() - 1));
            pageCount.endText();
        }
    }
}

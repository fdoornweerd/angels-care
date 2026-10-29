package org.angelscare.management.calendar.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.angelscare.management.calendar.model.SchoolYear;
import org.angelscare.management.support.FakeFileDialogs.SaveAsked;
import org.angelscare.management.support.ScreenTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The PDF report button on page 1 (spec 005). */
class SaveReportTest extends ScreenTest {

    private SchoolYearsViewModel openPage() {
        SchoolYearsViewModel page = schoolYearsPage();
        page.refresh();
        return page;
    }

    private static boolean isPdf(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        return bytes.length > 4 && new String(bytes, 0, 5).equals("%PDF-");
    }

    @Test
    @DisplayName("AC-2: the Save dialog opens in Documents with the year's file name")
    void suggestedFile() throws Exception {
        SchoolYear year = createYear(2026);
        Path chosen = tempDir.resolve("Angels Care 2026-2027 report.pdf");
        files.answerSave(chosen);

        assertThat(openPage().saveReport(year)).isTrue();

        assertThat(files.asked).containsExactly(
                new SaveAsked(tempDir.resolve("Documents"), "Angels Care 2026-2027 report.pdf"));
        assertThat(isPdf(chosen)).isTrue();
    }

    @Test
    @DisplayName("AC-3: Cancel writes nothing and opens nothing")
    void cancel() {
        SchoolYear year = createYear(2026);
        files.answerCancel();
        SchoolYearsViewModel page = openPage();

        assertThat(page.saveReport(year)).isFalse();

        assertThat(files.opened).isEmpty();
        assertThat(page.error()).isEmpty();
    }

    @Test
    @DisplayName("AC-4: a name without .pdf gets it added")
    void addsExtension() throws Exception {
        SchoolYear year = createYear(2026);
        files.answerSave(tempDir.resolve("year report"));

        openPage().saveReport(year);

        assertThat(isPdf(tempDir.resolve("year report.pdf"))).isTrue();
        assertThat(tempDir.resolve("year report")).doesNotExist();
    }

    @Test
    @DisplayName("AC-5: the saved report is opened")
    void opens() {
        SchoolYear year = createYear(2026);
        Path chosen = tempDir.resolve("report.PDF");
        files.answerSave(chosen);
        SchoolYearsViewModel page = openPage();

        page.saveReport(year);

        assertThat(files.opened).containsExactly(chosen);
        assertThat(page.error()).isEmpty();
    }

    @Test
    @DisplayName("AC-5: a report that can't be opened says where it was saved")
    void cannotOpen() throws Exception {
        SchoolYear year = createYear(2026);
        Path chosen = tempDir.resolve("report.pdf");
        files.answerSave(chosen).failOpen(new IllegalStateException("no PDF viewer"));
        SchoolYearsViewModel page = openPage();

        assertThat(page.saveReport(year)).isTrue();

        assertThat(isPdf(chosen)).isTrue();
        assertThat(page.error()).isEqualTo("The report was saved to " + chosen
                + ", but it could not be opened. Open it from that folder to print it.");
    }

    @Test
    @DisplayName("AC-6: a file that can't be written shows why, and the page carries on")
    void cannotWrite() throws Exception {
        SchoolYear year = createYear(2026);
        Path folder = Files.createDirectory(tempDir.resolve("taken.pdf"));
        files.answerSave(folder);
        SchoolYearsViewModel page = openPage();

        assertThat(page.saveReport(year)).isFalse();

        assertThat(page.error()).startsWith("The report could not be saved: ");
        assertThat(files.opened).isEmpty();
        assertThat(page.rows()).hasSize(1);
    }
}

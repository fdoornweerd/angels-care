package org.angelscare.management.support;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.angelscare.management.common.ui.FileDialogs;

/**
 * Answers the Save dialog from a script and records what was opened. A Save dialog with no
 * scripted answer fails the test, so an unexpected dialog can't go unnoticed.
 */
public final class FakeFileDialogs implements FileDialogs {

    /** What the Save dialog was shown with. */
    public record SaveAsked(Path folder, String suggestedName) {
    }

    private final Path documents;
    private Optional<Path> saveAnswer;
    private Exception openFailure;
    public final List<SaveAsked> asked = new ArrayList<>();
    public final List<Path> opened = new ArrayList<>();

    public FakeFileDialogs(Path documents) {
        this.documents = documents;
    }

    /** The Save dialog returns this file. */
    public FakeFileDialogs answerSave(Path file) {
        saveAnswer = Optional.of(file);
        return this;
    }

    /** The Save dialog is cancelled. */
    public FakeFileDialogs answerCancel() {
        saveAnswer = Optional.empty();
        return this;
    }

    /** Opening a file throws this, as when the PC has no PDF viewer. */
    public FakeFileDialogs failOpen(Exception failure) {
        openFailure = failure;
        return this;
    }

    @Override
    public Path documentsFolder() {
        return documents;
    }

    @Override
    public Optional<Path> choosePdfToSave(Path folder, String suggestedName) {
        asked.add(new SaveAsked(folder, suggestedName));
        if (saveAnswer == null) {
            throw new AssertionError("unexpected Save dialog: " + suggestedName);
        }
        return saveAnswer;
    }

    @Override
    public void open(Path file) throws Exception {
        if (openFailure != null) {
            throw openFailure;
        }
        opened.add(file);
    }
}

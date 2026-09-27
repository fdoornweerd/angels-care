package org.angelscare.management.support;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.angelscare.management.common.ui.ConfirmDialogs;

/**
 * Answers the screens' questions from a script. A question with no scripted answer fails the
 * test, so an unexpected dialog can't go unnoticed.
 */
public final class FakeDialogs implements ConfirmDialogs {

    private final Deque<UnsavedChoice> unsavedAnswers = new ArrayDeque<>();
    private final Deque<Boolean> confirmAnswers = new ArrayDeque<>();
    /** Every question asked, in order: "unsaved changes" or the confirm question's text. */
    public final List<String> asked = new ArrayList<>();

    public FakeDialogs answerUnsaved(UnsavedChoice choice) {
        unsavedAnswers.add(choice);
        return this;
    }

    public FakeDialogs answerConfirm(boolean yes) {
        confirmAnswers.add(yes);
        return this;
    }

    @Override
    public UnsavedChoice askUnsavedChanges() {
        asked.add("unsaved changes");
        if (unsavedAnswers.isEmpty()) {
            throw new AssertionError("unexpected question: unsaved changes");
        }
        return unsavedAnswers.poll();
    }

    @Override
    public boolean confirm(String question) {
        asked.add(question);
        if (confirmAnswers.isEmpty()) {
            throw new AssertionError("unexpected question: " + question);
        }
        return confirmAnswers.poll();
    }
}
